package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.entity.TipoRespostaEnum
import br.com.sankhya.studio.annotations.Controller
import br.com.sankhya.studio.persistence.Transactional
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Servico consumido pela tela de rematch (`html5/BcoRematch`) via `ServiceProxy`
 * (`integrador-bancario@RematchControllerSP.<metodo>`).
 *
 * Orquestra apenas - a logica esta em [RematchService], montado em
 * [IntegracaoBancaria]. Sem Guice (override do projeto): construtor sem-args.
 * Nao captura excecao: `IntegracaoBancariaException` sobe e vira erro na tela.
 */
@Controller(serviceName = "RematchControllerSP")
class RematchController {

    private val service: RematchService = IntegracaoBancaria.rematchService

    /** Lista DDAs sem vinculo e titulos a pagar sem vinculo do periodo/empresa. */

    fun listarPendencias(request: ListarPendenciasRequest): RematchPendencias =
        service.listar(
            RematchFiltro(
                codEmp = request.exigirCodEmp(),
                vencIni = request.exigirData(request.dtVencIni, "vencimento inicial"),
                vencFim = request.exigirData(request.dtVencFim, "vencimento final"),
                codParc = request.codParc,
            ),
        )

    /**
     * Confirma o match de um DDA com um titulo. Se houver divergencia e
     * `confirmado = false`, devolve as divergencias sem gravar (a tela pede
     * confirmacao e chama de novo com `confirmado = true`).
     */
    @Transactional
    fun confirmarMatch(request: ConfirmarMatchRequest): RematchResultado =
        service.confirmar(
            RematchComando(
                idFinanceiro = request.exigirTexto(request.idFinanceiro, "idFinanceiro"),
                idBanco = request.exigirInt(request.idBanco, "idBanco"),
                codEmp = request.exigirInt(request.codEmp, "codEmp"),
                tipoResp = request.tipoResp ?: TipoRespostaEnum.DDA.value,
                nufin = request.exigirDecimal(request.nufin, "nufin"),
                confirmado = request.confirmado == true,
            ),
        )
}

/**
 * DTOs de entrada como classes sem construtor primario (o projeto roda o KSP com
 * `isSdkEnabled=false` e sem kotlin-noarg; assim o framework consegue instanciar
 * via reflexao antes de popular os campos do JSON).
 */
class ListarPendenciasRequest {
    var codEmp: Int? = null
    var dtVencIni: String? = null
    var dtVencFim: String? = null
    var codParc: Int? = null

    fun exigirCodEmp(): Int = codEmp ?: erro("Informe a empresa.")
    fun exigirData(valor: String?, campo: String): LocalDate =
        try {
            LocalDate.parse((valor ?: erro("Informe a data de $campo.")).take(10))
        } catch (e: Exception) {
            erro("Data de $campo invalida: $valor")
        }
}

class ConfirmarMatchRequest {
    var idFinanceiro: String? = null
    var idBanco: Int? = null
    var codEmp: Int? = null
    var tipoResp: String? = null
    var nufin: String? = null
    var confirmado: Boolean? = null

    fun exigirTexto(valor: String?, campo: String): String =
        valor?.trim()?.takeIf { it.isNotEmpty() } ?: erro("Campo obrigatorio ausente: $campo")

    fun exigirInt(valor: Int?, campo: String): Int = valor ?: erro("Campo obrigatorio ausente: $campo")

    fun exigirDecimal(valor: String?, campo: String): BigDecimal =
        try {
            BigDecimal(valor ?: erro("Campo obrigatorio ausente: $campo"))
        } catch (e: NumberFormatException) {
            erro("Campo $campo invalido: $valor")
        }
}

private fun erro(mensagem: String): Nothing = throw IntegracaoBancariaException(mensagem)
