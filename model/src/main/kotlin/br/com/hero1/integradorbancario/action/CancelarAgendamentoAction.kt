package br.com.hero1.integradorbancario.action

import br.com.hero1.integradorbancario.integracao.IntegracaoBancaria
import br.com.hero1.integradorbancario.integracao.IntegracaoBancariaException
import br.com.hero1.integradorbancario.integracao.LogHelper
import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.studio.annotations.hooks.ActionButton
import br.com.sankhya.studio.annotations.hooks.TransactionType
import java.math.BigDecimal

/**
 * Botao "Cancelar Agendamento DDA" na tela do Financeiro (instancia nativa
 * `Financeiro` / TGFFIN).
 *
 * Cancela no banco um pagamento agendado (data futura) ainda nao efetivado.
 * O DDA fica liberado - PROCESSADO volta 'N' e os dados do pagamento
 * cancelado sao limpos - pra uma nova tentativa de "Pagar DDA" depois.
 *
 * Para cada titulo marcado na grade, pega o DDA vinculado (BCO_RESPBANCO.NUFIN)
 * que ja tem um pagamento agendado. Cada linha e isolada por try/catch.
 *
 * TODO: definir `resourceId` da tela de contas a pagar para o botao nao
 * aparecer em toda tela que usa a instancia `Financeiro`.
 */
@ActionButton(
    description = "Cancelar Agendamento DDA",
    instanceName = "Financeiro",
    transactionType = TransactionType.AUTOMATIC,
)
class CancelarAgendamentoAction : AcaoRotinaJava {

    override fun doAction(contexto: ContextoAcao) {
        val linhas = contexto.linhas
        if (linhas.isEmpty()) {
            throw IntegracaoBancariaException("Marque ao menos um financeiro para cancelar o agendamento.")
        }
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
                service.cancelarAgendamento(nufin)
                ok.append("NUFIN ${nufin.toPlainString()}; ")
                logBco.registrarAsync(
                    LogHelper.Status.INFO,
                    "Agendamento de DDA cancelado - NUFIN ${nufin.toPlainString()}",
                    origem = ORIGEM,
                )
            } catch (e: Exception) {
                logBco.registrarAsync(
                    LogHelper.Status.ERROR,
                    "Falha ao cancelar agendamento do NUFIN ${nufin.toPlainString()}",
                    e,
                    ORIGEM,
                )
                falhas.append("NUFIN ${nufin.toPlainString()}: ${e.message}; ")
            }
        }

        val mensagem = buildString {
            if (ok.isNotEmpty()) append("Cancelados: ").append(ok)
            if (falhas.isNotEmpty()) append("Falhas: ").append(falhas)
            if (isEmpty()) append("Nada processado.")
        }
        contexto.setMensagemRetorno(mensagem)
    }

    private companion object {
        const val ORIGEM = "CancelarAgendamentoAction"
    }
}
