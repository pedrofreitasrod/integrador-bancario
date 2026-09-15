package br.com.hero1.integradorbancario.job

import br.com.hero1.integradorbancario.integracao.IntegracaoBancaria
import br.com.hero1.integradorbancario.integracao.LogHelper
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.core.JapeSession.SessionHandle
import br.com.sankhya.modelcore.util.MGECoreParameter
import br.com.sankhya.studio.annotations.Job
import br.com.sankhya.studio.annotations.enums.EJBTransactionType
import br.com.sankhya.studio.stereotypes.IJob
import java.time.LocalDate

/**
 * Busca DDA de todas as credenciais ativas a cada 30 minutos.
 *
 * Job "normal" (construtor sem-args) - pega o service pronto de
 * [IntegracaoBancaria]. `transactionType = Required` para as gravacoes; cada
 * credencial e isolada por try/catch dentro do service (falha de rede/HTTP
 * ocorre antes de qualquer gravacao).
 *
 * Bypass: parametro Sankhya `PBCODDAJOB` (boolean, `cacheable="false"`). Setar
 * "Nao" nas Preferencias desliga o job sem redeploy.
 */
@Job(
    serviceName = "BuscarDdaJobSP",
    frequency = "0 0/30 * * * ?",
    transactionType = EJBTransactionType.Required,
)
class BuscarDdaJob : IJob() {

    override fun onSchedule() {
        var hnd:SessionHandle?=null
        try {
            hnd = JapeSession.open()
            if (!jobAtivo()) {
                LogHelper().info("Job de DDA desligado pelo parametro $PARAM_BYPASS - execucao ignorada.", ORIGEM)
                return
            }

            val inicio = LocalDate.now()
            val fim = inicio.plusDays(DIAS_JANELA)

            val resultados = IntegracaoBancaria.buscarDdaService.buscarTodasAtivas(inicio, fim)
            val novos = resultados.sumOf { it.quantidadeGravada }
            val falhas = resultados.count { !it.sucesso }
            LogHelper().info(
                "Job de DDA: ${resultados.size} credenciais, $novos registros novos, $falhas falha(s).",
                ORIGEM,
            )

        } catch (e: Exception) {
            LogHelper().registrarAsync(LogHelper.Status.ERROR, "Job de DDA: falha geral", e, ORIGEM)
        }finally {
            JapeSession.close(hnd)
        }
    }

    private fun jobAtivo(): Boolean =
        try {
            MGECoreParameter.getParameterAsBoolean( PARAM_BYPASS)
        } catch (e: Exception) {
            // Fora do contexto do ERP a chamada lanca - default: executar.
            true
        }

    private companion object {
        const val ORIGEM = "BuscarDdaJob"
        const val PARAM_BYPASS = "br.com.parameter.heroone.integrador"
        const val DIAS_JANELA = 30L
    }
}
