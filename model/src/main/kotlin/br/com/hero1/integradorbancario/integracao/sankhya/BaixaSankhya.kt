package br.com.hero1.integradorbancario.integracao.sankhya

import br.com.hero1.integradorbancario.integracao.IntegracaoBancariaException
import br.com.sankhya.jape.util.JapeSessionContext
import br.com.sankhya.modelcore.financeiro.helper.BaixaHelper
import com.sankhya.util.TimeUtils
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.logging.Logger

/**
 * Baixa de titulo financeiro (TGFFIN) via engine nativa do Sankhya, ja
 * conciliada, com movimento bancario.
 *
 * Wrapper fino sobre `BaixaHelper` / `DadosBaixa` (`br.com.sankhya.modelcore.
 * financeiro`). Mesmo padrao usado nas personalizacoes existentes (BTG). Roda
 * dentro da transacao do chamador (o @ActionButton).
 */
class BaixaSankhya {

    private val log: Logger = Logger.getLogger(BaixaSankhya::class.java.name)

    data class Parametros(
        val nufin: BigDecimal,
        val codUsu: BigDecimal,
        val codEmp: BigDecimal,
        /** Conta bancaria (TGFCTA) do debito. */
        val codConta: BigDecimal,
        /** Tipo de operacao do movimento bancario; nulo = default da baixa. */
        val codTipoOperacao: BigDecimal?,
        val dataBaixa: Timestamp,
        val valorPago: BigDecimal,
        val valorJuros: BigDecimal = BigDecimal.ZERO,
        val valorMulta: BigDecimal = BigDecimal.ZERO,
        val valorDesconto: BigDecimal = BigDecimal.ZERO,
    )

    /** @throws IntegracaoBancariaException se a baixa devolver mensagem de erro. */
    fun baixar(p: Parametros) {
        val helper = BaixaHelper(p.nufin, p.codUsu)
        helper.setIncluiAcertoFrete(false)
        helper.setIncluirMovimentoBancario(true)

        val dados = helper.montaDadosBaixa(Timestamp(TimeUtils.getToday()), false)
        dados.dataBaixa = p.dataBaixa

        val valores = dados.valoresBaixa
        valores.vlrBaixa = p.valorPago.toDouble()
        valores.vlrTotal = p.valorPago.toDouble()
        valores.vlrJuros = p.valorJuros.toDouble()
        valores.vlrMulta = p.valorMulta.toDouble()
        valores.vlrDesconto = p.valorDesconto.toDouble()

        dados.dadosAdicionais.codEmpresa = p.codEmp
        p.codTipoOperacao?.let { dados.dadosAdicionais.codTipoOperacao = it }

        val bancarios = dados.dadosBancarios
        bancarios.codConta = p.codConta
        bancarios.conciliado = "S"
        bancarios.dataConciliacao = Timestamp(System.currentTimeMillis())

        JapeSessionContext.putProperty("mov.financeiro.ignoraValidacao", false)
        try {
            helper.baixar(dados)
        } finally {
            JapeSessionContext.putProperty("mov.financeiro.ignoraValidacao", true)
        }

        val mensagens = helper.mensagens
        if (mensagens != null && !mensagens.isEmpty()) {
            val texto = mensagens.joinToString("; ") { it.toString() }
            log.warning("Baixa do NUFIN ${p.nufin} retornou mensagens: $texto")
            throw IntegracaoBancariaException("Falha na baixa do financeiro ${p.nufin}: $texto")
        }
        log.info("NUFIN ${p.nufin} baixado e conciliado (conta ${p.codConta}).")
    }
}
