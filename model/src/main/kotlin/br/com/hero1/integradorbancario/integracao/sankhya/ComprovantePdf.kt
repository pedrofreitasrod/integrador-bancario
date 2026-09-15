package br.com.hero1.integradorbancario.integracao.sankhya

import java.io.ByteArrayOutputStream
import java.util.Locale

/** Cor RGB (0.0-1.0) para preenchimento/traco no content stream do PDF. */
internal data class Cor(val r: Double, val g: Double, val b: Double)

/**
 * Paleta usada no comprovante - aproximada do layout de extrato bancario
 * (banner de situacao, caixa de aviso, campos rotulo/valor, divisores).
 */
internal object CorPdf {
    val VERDE = Cor(0.13, 0.55, 0.30)
    val VERDE_FUNDO = Cor(0.90, 0.96, 0.91)
    val VERMELHO = Cor(0.75, 0.18, 0.18)
    val VERMELHO_FUNDO = Cor(0.98, 0.92, 0.92)
    val CINZA_TEXTO = Cor(0.35, 0.35, 0.35)
    val CINZA_FUNDO = Cor(0.94, 0.94, 0.94)
    val AZUL_FUNDO = Cor(0.90, 0.95, 1.00)
    val AZUL_TEXTO = Cor(0.20, 0.38, 0.58)
    val PRETO = Cor(0.10, 0.10, 0.10)
    val LABEL = Cor(0.55, 0.55, 0.55)
    val DIVISOR = Cor(0.88, 0.88, 0.88)
}

/**
 * Gerador minimo de PDF em formato "extrato bancario": pagina estreita,
 * banner colorido por situacao, campos rotulo/valor, divisores e caixas de
 * aviso. Sem dependencia externa (o projeto evita libs - ver override no
 * CLAUDE.md); PDF 1.4 cru, montado a mao (Catalog/Pages/Page/2 fontes/Contents).
 *
 * Uso: `ComprovantePdf.Builder().banner(...).campo(...)....gerar()`.
 * A altura da pagina e calculada a partir do conteudo (soma das alturas dos
 * comandos), entao nunca sobra nem falta espaco em branco.
 */
internal object ComprovantePdf {

    private const val CHARSET = "ISO-8859-1"
    const val LARGURA_PADRAO = 320.0
    private const val MARGEM = 16.0
    private const val LARGURA_CONTEUDO = LARGURA_PADRAO - MARGEM * 2

    class Builder {
        private val comandos = mutableListOf<Comando>()

        fun espaco(altura: Double): Builder {
            comandos += Comando.Espaco(altura)
            return this
        }

        fun banner(texto: String, corFundo: Cor, corTexto: Cor, tamanho: Int = 13): Builder {
            val linhas = quebrar(texto, tamanho, negrito = true, larguraDisp = LARGURA_PADRAO - 40)
            val altura = 26 + linhas.size * (tamanho + 6) + 18
            comandos += Comando.Banner(altura.toDouble(), linhas, corFundo, corTexto, tamanho)
            return this
        }

        fun valorGrande(texto: String, tamanho: Int = 24, cor: Cor = CorPdf.PRETO): Builder {
            comandos += Comando.Centralizado((tamanho + 6).toDouble(), texto, tamanho, true, cor)
            return this
        }

        fun subtitulo(texto: String, tamanho: Int = 9, cor: Cor = CorPdf.LABEL): Builder {
            val linhas = quebrar(texto, tamanho, negrito = false, larguraDisp = LARGURA_CONTEUDO)
            comandos += Comando.CentralizadoMulti((linhas.size * (tamanho + 3)).toDouble(), linhas, tamanho, cor)
            return this
        }

        fun tituloSecao(texto: String, tamanho: Int = 11): Builder {
            comandos += Comando.TituloSecao((tamanho + 6).toDouble(), texto, tamanho)
            return this
        }

        fun caixaInfo(texto: String, corFundo: Cor, corTexto: Cor, tamanho: Int = 8): Builder {
            val linhas = quebrar(texto, tamanho, negrito = false, larguraDisp = LARGURA_CONTEUDO - 20)
            val altura = linhas.size * (tamanho + 3) + 18
            comandos += Comando.Caixa(altura.toDouble(), linhas, corFundo, corTexto, tamanho)
            return this
        }

        fun campo(label: String, valor: String, tamanho: Int = 10): Builder {
            val linhasValor = quebrar(valor, tamanho, negrito = true, larguraDisp = LARGURA_CONTEUDO)
            val altura = (tamanho - 1 + 4) + linhasValor.size * (tamanho + 3) + 8
            comandos += Comando.Campo(altura.toDouble(), label, linhasValor, tamanho)
            return this
        }

        fun divisor(): Builder {
            comandos += Comando.Divisor
            return this
        }

        fun gerar(): ByteArray {
            val alturaTotal = comandos.sumOf { it.altura } + MARGEM * 2
            var y = alturaTotal - MARGEM
            val ops = StringBuilder()

            for (cmd in comandos) {
                when (cmd) {
                    is Comando.Espaco -> {
                        y -= cmd.altura
                    }
                    is Comando.Banner -> {
                        ops.appendLine(retangulo(0.0, y - cmd.altura, LARGURA_PADRAO, cmd.altura, cmd.corFundo))
                        var yy = y - 26
                        for (linha in cmd.linhas) {
                            ops.appendLine(textoCentralizado(linha, cmd.tamanho, true, cmd.corTexto, yy))
                            yy -= cmd.tamanho + 6
                        }
                        y -= cmd.altura
                    }
                    is Comando.Centralizado -> {
                        ops.appendLine(textoCentralizado(cmd.texto, cmd.tamanho, cmd.negrito, cmd.cor, y - cmd.tamanho))
                        y -= cmd.altura
                    }
                    is Comando.CentralizadoMulti -> {
                        var yy = y - cmd.tamanho
                        for (linha in cmd.linhas) {
                            ops.appendLine(textoCentralizado(linha, cmd.tamanho, false, cmd.cor, yy))
                            yy -= cmd.tamanho + 3
                        }
                        y -= cmd.altura
                    }
                    is Comando.TituloSecao -> {
                        ops.appendLine(desenharTexto(MARGEM, y - cmd.tamanho, cmd.tamanho, true, CorPdf.PRETO, cmd.texto))
                        y -= cmd.altura
                    }
                    is Comando.Caixa -> {
                        ops.appendLine(retangulo(MARGEM, y - cmd.altura, LARGURA_CONTEUDO, cmd.altura, cmd.corFundo))
                        var yy = y - 12 - cmd.tamanho
                        for (linha in cmd.linhas) {
                            ops.appendLine(desenharTexto(MARGEM + 10, yy, cmd.tamanho, false, cmd.corTexto, linha))
                            yy -= cmd.tamanho + 3
                        }
                        y -= cmd.altura
                    }
                    is Comando.Campo -> {
                        ops.appendLine(
                            desenharTexto(MARGEM, y - (cmd.tamanho - 1), cmd.tamanho - 1, false, CorPdf.LABEL, cmd.label),
                        )
                        var yy = y - (cmd.tamanho - 1) - 4 - cmd.tamanho
                        for (linha in cmd.linhasValor) {
                            ops.appendLine(desenharTexto(MARGEM, yy, cmd.tamanho, true, CorPdf.PRETO, linha))
                            yy -= cmd.tamanho + 3
                        }
                        y -= cmd.altura
                    }
                    is Comando.Divisor -> {
                        val yy = y - 8
                        ops.appendLine(linha(MARGEM, yy, LARGURA_PADRAO - MARGEM, yy, CorPdf.DIVISOR))
                        y -= cmd.altura
                    }
                }
            }

            return montarPdf(LARGURA_PADRAO, alturaTotal, ops.toString())
        }
    }

    private sealed class Comando(val altura: Double) {
        class Espaco(altura: Double) : Comando(altura)
        class Banner(altura: Double, val linhas: List<String>, val corFundo: Cor, val corTexto: Cor, val tamanho: Int) :
            Comando(altura)
        class Centralizado(altura: Double, val texto: String, val tamanho: Int, val negrito: Boolean, val cor: Cor) :
            Comando(altura)
        class CentralizadoMulti(altura: Double, val linhas: List<String>, val tamanho: Int, val cor: Cor) :
            Comando(altura)
        class TituloSecao(altura: Double, val texto: String, val tamanho: Int) : Comando(altura)
        class Caixa(altura: Double, val linhas: List<String>, val corFundo: Cor, val corTexto: Cor, val tamanho: Int) :
            Comando(altura)
        class Campo(altura: Double, val label: String, val linhasValor: List<String>, val tamanho: Int) : Comando(altura)
        object Divisor : Comando(16.0)
    }

    // --- desenho -----------------------------------------------------------

    private fun retangulo(x: Double, y: Double, w: Double, h: Double, cor: Cor): String =
        "${f(cor.r)} ${f(cor.g)} ${f(cor.b)} rg\n${f(x)} ${f(y)} ${f(w)} ${f(h)} re f"

    private fun linha(x1: Double, y1: Double, x2: Double, y2: Double, cor: Cor): String =
        "${f(cor.r)} ${f(cor.g)} ${f(cor.b)} RG\n0.75 w\n${f(x1)} ${f(y1)} m\n${f(x2)} ${f(y2)} l\nS"

    private fun desenharTexto(x: Double, y: Double, tamanho: Int, negrito: Boolean, cor: Cor, texto: String): String {
        val fonte = if (negrito) "/F2" else "/F1"
        return "BT\n$fonte $tamanho Tf\n${f(cor.r)} ${f(cor.g)} ${f(cor.b)} rg\n" +
            "1 0 0 1 ${f(x)} ${f(y)} Tm\n(${escape(texto)}) Tj\nET"
    }

    private fun textoCentralizado(texto: String, tamanho: Int, negrito: Boolean, cor: Cor, y: Double): String {
        val larguraTexto = texto.length * larguraMediaChar(tamanho, negrito)
        val x = maxOf((LARGURA_PADRAO - larguraTexto) / 2, MARGEM)
        return desenharTexto(x, y, tamanho, negrito, cor, texto)
    }

    // --- quebra de linha (sem metrica real de fonte - estimativa por char) -

    private fun larguraMediaChar(tamanho: Int, negrito: Boolean): Double = tamanho * (if (negrito) 0.58 else 0.50)

    private fun quebrar(texto: String, tamanho: Int, negrito: Boolean, larguraDisp: Double): List<String> {
        val maxChars = (larguraDisp / larguraMediaChar(tamanho, negrito)).toInt().coerceAtLeast(8)
        val linhas = mutableListOf<String>()
        for (paragrafo in texto.split("\n")) {
            if (paragrafo.length <= maxChars) {
                linhas += paragrafo
                continue
            }
            var atual = StringBuilder()
            for (palavraOriginal in paragrafo.split(" ")) {
                // Palavra sem espaco (token de autenticacao, codigo de barras etc.) maior
                // que a linha inteira nunca quebraria sozinha - forca corte no meio dela,
                // senao o texto "vaza" da pagina sem erro nem log.
                var palavra = palavraOriginal
                while (palavra.length > maxChars) {
                    if (atual.isNotEmpty()) {
                        linhas += atual.toString()
                        atual = StringBuilder()
                    }
                    linhas += palavra.substring(0, maxChars)
                    palavra = palavra.substring(maxChars)
                }
                val tentativa = if (atual.isEmpty()) palavra else "$atual $palavra"
                if (tentativa.length > maxChars && atual.isNotEmpty()) {
                    linhas += atual.toString()
                    atual = StringBuilder(palavra)
                } else {
                    atual = StringBuilder(tentativa)
                }
            }
            if (atual.isNotEmpty()) linhas += atual.toString()
        }
        return linhas.ifEmpty { listOf("") }
    }

    /** Locale.ROOT sempre - PDF exige ponto decimal nas coordenadas, o locale do servidor pode usar virgula. */
    private fun f(v: Double): String = String.format(Locale.ROOT, "%.2f", v)

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\r", "").replace("\n", " ")

    // --- estrutura do arquivo PDF --------------------------------------------

    private fun montarPdf(largura: Double, altura: Double, streamOps: String): ByteArray {
        val stream = streamOps.toByteArray(charset(CHARSET))
        val objetos = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(largura)} ${f(altura)}] " +
                "/Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>",
        )

        val out = ByteArrayOutputStream()
        val offsets = IntArray(7)
        fun escreve(s: String) = out.write(s.toByteArray(charset(CHARSET)))

        escreve("%PDF-1.4\n")
        for (i in objetos.indices) {
            offsets[i + 1] = out.size()
            escreve("${i + 1} 0 obj\n${objetos[i]}\nendobj\n")
        }
        offsets[6] = out.size()
        escreve("6 0 obj\n<< /Length ${stream.size} >>\nstream\n")
        out.write(stream)
        escreve("\nendstream\nendobj\n")

        val xref = out.size()
        val trailer = StringBuilder("xref\n0 7\n0000000000 65535 f \n")
        for (i in 1..6) trailer.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]))
        trailer.append("trailer\n<< /Size 7 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF")
        escreve(trailer.toString())

        return out.toByteArray()
    }
}
