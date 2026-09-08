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
import java.text.SimpleDateFormat
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

        val pdf = ComprovantePdf.gerar(
            titulo = "Comprovante de Pagamento - DDA",
            linhas = linhasComprovante(nufin, comprovante, boleto),
        )

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

    private fun linhasComprovante(
        nufin: BigDecimal,
        c: ComprovantePagamento,
        b: BoletoParaPagar,
    ): List<String> {
        val data = SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date())
        return listOf(
            "Emitido em: $data",
            "",
            "Financeiro (NUFIN): ${nufin.toPlainString()}",
            "Codigo de barras: ${b.codigoBarras}",
            "Linha digitavel: ${b.linhaDigitavel ?: "-"}",
            "Beneficiario: ${b.nomeBeneficiario ?: "-"} (${b.cnpjBeneficiario ?: "-"})",
            "Vencimento: ${b.dataVencimento?.toString() ?: "-"}",
            "",
            "Id do pagamento: ${c.idPagamento}",
            "Autenticacao: ${c.autenticacao ?: "-"}",
            "Situacao: ${c.situacao ?: "-"}",
            "Detalhe: ${c.detalheSituacao ?: "-"}",
            "Data do pagamento: ${c.dataPagamento?.toString() ?: "-"}",
            "Valor pago: R$ ${c.valorPagamento?.toPlainString() ?: "-"}",
        )
    }

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
    }
}
