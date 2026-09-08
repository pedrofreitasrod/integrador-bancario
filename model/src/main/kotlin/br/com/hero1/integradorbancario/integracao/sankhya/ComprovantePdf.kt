package br.com.hero1.integradorbancario.integracao.sankhya

import java.io.ByteArrayOutputStream

/**
 * Gerador minimo de PDF: uma pagina A4, texto Helvetica. Sem dependencia
 * externa - o comprovante de pagamento e so texto e o projeto evita libs
 * (ver override no CLAUDE.md). Estrutura padrao de PDF 1.4 com 5 objetos.
 */
internal object ComprovantePdf {

    private const val CHARSET = "ISO-8859-1"
    private const val TOPO_Y = 790
    private const val ALTURA_LINHA = 15

    fun gerar(titulo: String, linhas: List<String>): ByteArray {
        val texto = StringBuilder()
        texto.append("BT\n/F1 14 Tf\n50 $TOPO_Y Td\n(").append(escape(titulo)).append(") Tj\n")
        texto.append("/F1 9 Tf\n")
        texto.append("0 -").append(ALTURA_LINHA * 2).append(" Td\n")
        for (linha in linhas) {
            texto.append("(").append(escape(linha)).append(") Tj\n0 -").append(ALTURA_LINHA).append(" Td\n")
        }
        texto.append("ET")
        val stream = texto.toString().toByteArray(charset(CHARSET))

        val objetos = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
        )

        val out = ByteArrayOutputStream()
        val offsets = IntArray(6)
        fun escreve(s: String) = out.write(s.toByteArray(charset(CHARSET)))

        escreve("%PDF-1.4\n")
        for (i in objetos.indices) {
            offsets[i + 1] = out.size()
            escreve("${i + 1} 0 obj\n${objetos[i]}\nendobj\n")
        }
        offsets[5] = out.size()
        escreve("5 0 obj\n<< /Length ${stream.size} >>\nstream\n")
        out.write(stream)
        escreve("\nendstream\nendobj\n")

        val xref = out.size()
        val trailer = StringBuilder("xref\n0 6\n0000000000 65535 f \n")
        for (i in 1..5) trailer.append(String.format("%010d 00000 n \n", offsets[i]))
        trailer.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF")
        escreve(trailer.toString())

        return out.toByteArray()
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\r", "").replace("\n", " ")
}
