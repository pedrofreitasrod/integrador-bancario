package br.com.hero1.integradorbancario.integracao.sankhya

import br.com.hero1.integradorbancario.integracao.IntegracaoBancariaException
import br.com.hero1.integradorbancario.integracao.dominio.BoletoParaPagar
import br.com.hero1.integradorbancario.integracao.dominio.ComprovantePagamento
import br.com.sankhya.dwf.helper.AnexoHelper
import br.com.sankhya.jape.sql.NativeSql
import br.com.sankhya.modelcore.util.EntityFacadeFactory
import com.sankhya.util.UIDGenerator
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.sql.Timestamp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.logging.Logger

/**
 * Gera o comprovante de pagamento em PDF, grava no repositorio de anexos do
 * Sankhya e registra o vinculo em TSIANX (Central de Anexos), apontando para o
 * registro do financeiro (instancia `Financeiro`, PK `<NUFIN>_Financeiro`).
 *
 * Padrao do `salvaArquivo` das personalizacoes existentes (BTG): arquivo fisico
 * em `AnexoHelper.getAnexosDir(data)` nomeado pela CHAVEARQUIVO.
 */
class AnexoFinanceiro {

    private val log: Logger = Logger.getLogger(AnexoFinanceiro::class.java.name)

    /** @return a CHAVEARQUIVO gerada (chave do arquivo no repositorio). */
    fun anexarComprovante(
        nufin: BigDecimal,
        codUsu: BigDecimal,
        comprovante: ComprovantePagamento,
        boleto: BoletoParaPagar,
    ): String {
        val agora = Timestamp(System.currentTimeMillis())
        val chaveArquivo = "pdf_" + UIDGenerator.getNextID()
        val nomeArquivo = "Comprovante_${nufin.toPlainString()}.pdf"

        val pdf = gerarPdf(nufin, comprovante, boleto.codigoBarras)

        val diretorio: File = AnexoHelper.getAnexosDir(agora)
        if (!diretorio.exists()) diretorio.mkdirs()
        val destino = File(diretorio, chaveArquivo)
        try {
            Files.write(destino.toPath(), pdf)
        } catch (e: Exception) {
            throw IntegracaoBancariaException("Falha ao gravar o comprovante em ${destino.absolutePath}", e)
        }

        inserirTsianx(chaveArquivo, nomeArquivo, nufin, codUsu, agora)
        log.info("Comprovante anexado ao NUFIN $nufin (chave $chaveArquivo).")
        return chaveArquivo
    }

    /**
     * So gera os bytes do PDF - sem gravar nada (nem arquivo em disco, nem
     * TSIANX). Uso: botao "Consultar Comprovante" (so visualizar, sem
     * efeito colateral) via `SessionFile`.
     *
     * Layout "extrato bancario": pagina estreita, banner colorido pela
     * situacao (verde/vermelho/cinza), campos rotulo/valor, divisores. Os
     * campos de beneficiario/pagador/conta/valores vem do comprovante ([c])
     * - e a versao final, confirmada pelo banco no momento do pagamento. So o
     * codigo de barras nao vem no comprovante (a PK do DDA e a fonte).
     */
    fun gerarPdf(nufin: BigDecimal, c: ComprovantePagamento, codigoBarras: String): ByteArray {
        val situacao = c.situacao?.trim()
        val efetivado = situacao.equals("Efetivado", ignoreCase = true)
        val falhou = situacao.equals("Rejeitado", ignoreCase = true) || situacao.equals("Cancelado", ignoreCase = true)
        val (corFundoBanner, corBanner) = when {
            efetivado -> CorPdf.VERDE_FUNDO to CorPdf.VERDE
            falhou -> CorPdf.VERMELHO_FUNDO to CorPdf.VERMELHO
            else -> CorPdf.CINZA_FUNDO to CorPdf.CINZA_TEXTO
        }
        val agencia = listOfNotNull(c.numeroAgencia, c.nomeAgencia).joinToString(" ").ifBlank { null }
        val contaDebitada = listOfNotNull(agencia, c.numeroConta, c.nomeProprietarioConta)
            .joinToString("/").ifBlank { "-" }

        val builder = ComprovantePdf.Builder()
            .banner(c.tituloComprovante ?: (situacao?.uppercase() ?: "COMPROVANTE DE PAGAMENTO"), corFundoBanner, corBanner)
            .espaco(6.0)
            .valorGrande(formatarMoeda(c.valorPagamento ?: c.valorBoleto))
            .subtitulo(
                c.dataPagamento?.let { "Pagamento em ${formatarData(it)}" } ?: "Situacao: ${situacao ?: "-"}",
            )
        (c.observacao ?: "Pagamento DDA - NUFIN ${nufin.toPlainString()}").let { builder.subtitulo(it) }

        builder
            .espaco(10.0)
            .tituloSecao("Comprovante de pagamento")
            .caixaInfo(
                "Comprovante para simples conferencia - gerado em ${formatarDataHora(LocalDateTime.now())}",
                CorPdf.AZUL_FUNDO,
                CorPdf.AZUL_TEXTO,
            )
            .espaco(6.0)
            .tituloSecao("Beneficiario")
            .campo("Nome/Razao social", c.nomeBeneficiario ?: "-")
            .campo("CPF/CNPJ", c.cnpjBeneficiario ?: "-")
            .campo("Instituicao", c.instituicaoBeneficiaria ?: "-")
            .divisor()
            .tituloSecao("Pagador")
            .campo("Nome/Razao social", c.nomePagador ?: "-")
            .campo("CPF/CNPJ", c.cnpjPagador ?: "-")
            .campo("Conta debitada", contaDebitada)
            .divisor()
            .campo("Financeiro (NUFIN)", nufin.toPlainString())
            .campo("Numero do documento", c.numeroDocumento ?: "-")
            .campo("Nosso numero", c.nossoNumero ?: "-")
            .campo("Numero do agendamento", c.idPagamento.toString())
            .campo("Data de cadastro no banco", formatarData(c.dataCadastro))
            .campo("Data do vencimento", formatarData(c.dataVencimento))
            .campo("Data do pagamento", formatarData(c.dataPagamento))
            .campo("Outros encargos", formatarMoeda(c.valorMulta ?: BigDecimal.ZERO))
            .campo("Valor do desconto", formatarMoeda(c.valorDesconto ?: BigDecimal.ZERO))
            .campo("Valor total pago", formatarMoeda(c.valorPagamento))
            .campo("Situacao", situacao ?: "-")
            .divisor()
            .campo("Codigo de barras", codigoBarras)
            .campo("Linha digitavel", c.linhaDigitavel ?: "-")
            .divisor()
            .caixaInfo("Autenticacao: ${c.autenticacao ?: "-"}", CorPdf.CINZA_FUNDO, CorPdf.CINZA_TEXTO)

        c.detalheSituacao?.let { builder.espaco(4.0).subtitulo(it, tamanho = 7) }
        c.ouvidoria?.let { builder.espaco(4.0).subtitulo(it, tamanho = 7) }

        return builder.gerar()
    }

    // DecimalFormat nao e thread-safe (diferente de DateTimeFormatter) - instancia
    // nova a cada chamada, custo desprezivel pra um PDF gerado poucas vezes.
    private fun formatarMoeda(v: BigDecimal?): String =
        if (v == null) "-" else "R$ ${DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("pt", "BR"))).format(v)}"

    private fun formatarData(d: LocalDate?): String = d?.format(FORMATO_DATA) ?: "-"

    private fun formatarDataHora(d: LocalDateTime): String = d.format(FORMATO_DATA_HORA)

    private fun inserirTsianx(
        chaveArquivo: String,
        nomeArquivo: String,
        nufin: BigDecimal,
        codUsu: BigDecimal,
        agora: Timestamp,
    ) {
        val jdbc = EntityFacadeFactory.getDWFFacade().jdbcWrapper
        val proximo = (NativeSql.getBigDecimal("MAX(NUATTACH)", "TSIANX", "1=1") ?: BigDecimal.ZERO)
            .add(BigDecimal.ONE)
        val sql = NativeSql(jdbc)
        sql.appendSql(
            "INSERT INTO TSIANX (CHAVEARQUIVO, CODUSU, CODUSUALT, DESCRICAO, DHALTER, DHCAD, " +
                "LINK, NOMEARQUIVO, NOMEINSTANCIA, NUATTACH, PKREGISTRO, RESOURCEID, TIPOACESSO, TIPOAPRES) " +
                "VALUES (:CHAVE, :CODUSU, :CODUSU, :DESCRICAO, :DH, :DH, NULL, :NOME, :INSTANCIA, " +
                ":NUATTACH, :PK, :RESOURCEID, 'ALL', 'LOC')",
        )
        sql.setNamedParameter("CHAVE", chaveArquivo)
        sql.setNamedParameter("CODUSU", codUsu)
        sql.setNamedParameter("DESCRICAO", "Comprovante de pagamento DDA - NUFIN ${nufin.toPlainString()}")
        sql.setNamedParameter("DH", agora)
        sql.setNamedParameter("NOME", nomeArquivo)
        sql.setNamedParameter("INSTANCIA", INSTANCIA_FINANCEIRO)
        sql.setNamedParameter("NUATTACH", proximo)
        sql.setNamedParameter("PK", "${nufin.toPlainString()}_$INSTANCIA_FINANCEIRO")
        sql.setNamedParameter("RESOURCEID", RESOURCE_ID_FINANCEIRO)
        sql.executeUpdate()
    }

    private companion object {
        const val INSTANCIA_FINANCEIRO = "Financeiro"
        const val RESOURCE_ID_FINANCEIRO = "br.com.sankhya.fin.cad.movimentacaoFinanceira"

        // DateTimeFormatter e imutavel/thread-safe - pode ficar compartilhado.
        val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val FORMATO_DATA_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm:ss")
    }
}
