package br.com.hero1.integradorbancario.integracao

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.studio.annotations.hooks.ActionButton
import br.com.sankhya.studio.annotations.hooks.TransactionType
import java.math.BigDecimal

/**
 * Botao "Pagar DDA" na tela do Financeiro (instancia nativa `Financeiro` / TGFFIN).
 *
 * Para cada titulo marcado na grade, pega o DDA vinculado (BCO_RESPBANCO.NUFIN),
 * consulta e paga o boleto no banco, baixa/concilia o titulo e anexa o
 * comprovante. Cada linha e isolada por try/catch.
 *
 * ATENCAO: pagamento e efeito externo (dinheiro sai). Em lote, uma linha que
 * falhe apos o pagamento ter sido efetuado no banco fica com o titulo NAO
 * baixado - a mensagem de retorno lista o que deu certo e o que falhou, e o
 * `LogHelper` grava cada resultado em BCO_LOG (async - resiste a rollback).
 *
 * TODO: definir `resourceId` da tela de contas a pagar para o botao nao
 * aparecer em toda tela que usa a instancia `Financeiro`.
 */
@ActionButton(
    description = "Pagar DDA",
    instanceName = "Financeiro",
    transactionType = TransactionType.AUTOMATIC,
)
class PagarDdaAction : AcaoRotinaJava {

    override fun doAction(contexto: ContextoAcao) {
        val linhas = contexto.linhas
        if (linhas.isEmpty()) {
            throw IntegracaoBancariaException("Marque ao menos um financeiro para pagar.")
        }
        val codUsu = contexto.usuarioLogado ?: BigDecimal.ZERO
        val service = IntegracaoBancaria.pagarDdaService

        val ok = StringBuilder()
        val falhas = StringBuilder()
        for (linha in linhas) {
            val nufin = linha.getCampo("NUFIN") as? BigDecimal
            val logBco = LogHelper((linha.getCampo("CODEMP") as? BigDecimal)?.toInt())
            if (nufin == null) {
                falhas.append("(linha sem NUFIN) ")
                continue
            }
            try {
                val r = service.pagarPorNufin(nufin, codUsu)
                ok.append(
                    "NUFIN ${nufin.toPlainString()}: ${r.situacao ?: "?"} (id ${r.idPagamento})" +
                        (if (r.baixado) " - baixado" else "") + "; ",
                )
                logBco.registrarAsync(
                    LogHelper.Status.INFO,
                    "DDA pago - NUFIN ${nufin.toPlainString()}: ${r.situacao ?: "?"} " +
                        "(pagamento ${r.idPagamento}${if (r.baixado) ", baixado" else ""})",
                    origem = ORIGEM,
                )
            } catch (e: Exception) {
                logBco.registrarAsync(
                    LogHelper.Status.ERROR,
                    "Falha ao pagar DDA do NUFIN ${nufin.toPlainString()}",
                    e,
                    ORIGEM,
                )
                falhas.append("NUFIN ${nufin.toPlainString()}: ${e.message}; ")
            }
        }

        val mensagem = buildString {
            if (ok.isNotEmpty()) append("Pagos: ").append(ok)
            if (falhas.isNotEmpty()) append("Falhas: ").append(falhas)
            if (isEmpty()) append("Nada processado.")
        }
        contexto.setMensagemRetorno(mensagem)
    }

    private companion object {
        const val ORIGEM = "PagarDdaAction"
    }
}
