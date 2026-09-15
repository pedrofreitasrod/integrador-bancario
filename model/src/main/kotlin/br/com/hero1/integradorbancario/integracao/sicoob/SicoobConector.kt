package br.com.hero1.integradorbancario.integracao.sicoob

import br.com.hero1.integradorbancario.entity.BcoParamBanco
import br.com.hero1.integradorbancario.integracao.BancoIntegracao
import br.com.hero1.integradorbancario.integracao.ConectorBancario
import br.com.hero1.integradorbancario.integracao.ConsultaBancariaException
import br.com.hero1.integradorbancario.integracao.PagamentoBancarioException
import br.com.hero1.integradorbancario.integracao.dominio.BoletoParaPagar
import br.com.hero1.integradorbancario.integracao.dominio.ComandoAutenticacao
import br.com.hero1.integradorbancario.integracao.dominio.ComandoCancelamento
import br.com.hero1.integradorbancario.integracao.dominio.ComandoPagamento
import br.com.hero1.integradorbancario.integracao.dominio.ComprovantePagamento
import br.com.hero1.integradorbancario.integracao.dominio.ConsultaBoleto
import br.com.hero1.integradorbancario.integracao.dominio.ConsultaComprovante
import br.com.hero1.integradorbancario.integracao.dominio.Dda
import br.com.hero1.integradorbancario.integracao.dominio.FiltroDda
import br.com.hero1.integradorbancario.integracao.dominio.ResultadoAutenticacao
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobBoletoConsultaResponse
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobBoletoDdaDto
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobCancelamentoRequest
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobComprovanteResponse
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobDebtorAccount
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobErroResponse
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobPagamentoRequest
import java.net.URLEncoder
import java.time.LocalDate
import java.util.UUID

/**
 * Conector do Sicoob. Classe plana - montada em `IntegracaoBancaria`.
 *
 * Usa a API Cobranca Bancaria Pagamentos v3:
 * - Producao: https://api.sicoob.com.br/pagamentos/v3
 * - Sandbox : https://sandbox.sicoob.com.br/sicoob/sandbox/cobranca-bancaria-pagamentos/v3
 *
 * DDA = `GET /boletos` (secao "Movimentacoes DDA"). NAO confundir com o
 * `GET /cobranca-bancaria/v3/boletos` da API de Cobranca, que lista os boletos
 * emitidos pela empresa como beneficiaria.
 */
class SicoobConector(
    private val http: SicoobHttpClient,
    private val tokenProvider: SicoobTokenProvider,
    private val mapper: SicoobMapper,
) : ConectorBancario {

    override val codigoCompensacao: Int = SicoobConfig.CODIGO_COMPENSACAO

    override fun autenticar(comando: ComandoAutenticacao): ResultadoAutenticacao =
        tokenProvider.renovar(comando.credencial, comando.sandbox)

    override fun buscarDdas(filtro: FiltroDda): List<Dda> {
        val credencial = filtro.credencial
        val numeroConta = contaEmDigitos(credencial)
        val ctx = contexto(credencial, filtro.sandbox)

        val url = basePagamentos(filtro.sandbox) + "/boletos" +
            "?numeroConta=" + enc(numeroConta) +
            "&dataInicial=" + filtro.dataInicio +
            "&dataFinal=" + filtro.dataFim +
            "&situacao=" + SicoobConfig.DDA_SITUACAO_EM_ABERTO +
            "&tipoData=" + SicoobConfig.DDA_TIPO_DATA_VENCIMENTO

        val resposta = http.get(url, ctx.headers, ctx.mtls)
        if (!resposta.ok) {
            throw ConsultaBancariaException(
                "Sicoob recusou a consulta de DDA (HTTP ${resposta.status}): ${mensagemErro(resposta.corpo)}",
            )
        }

        return http.lerLista(resposta.corpo, SicoobBoletoDdaDto::class.java)
            .mapNotNull(mapper::paraDda)
    }

    override fun consultarBoleto(consulta: ConsultaBoleto): BoletoParaPagar {
        val credencial = consulta.credencial
        val numeroConta = contaEmDigitos(credencial)
        val ctx = contexto(credencial, consulta.sandbox)

        val url = basePagamentos(consulta.sandbox) + "/boletos/" + enc(consulta.codigoBarras) +
            "?numeroConta=" + enc(numeroConta) +
            (consulta.dataPagamento?.let { "&dataPagamento=$it" } ?: "")

        val resposta = http.get(url, ctx.headers, ctx.mtls)
        if (!resposta.ok) {
            throw ConsultaBancariaException(
                "Sicoob recusou a consulta do boleto (HTTP ${resposta.status}): ${mensagemErro(resposta.corpo)}",
            )
        }

        val dto = http.ler(resposta.corpo, SicoobBoletoConsultaResponse::class.java)?.resultado
            ?: throw ConsultaBancariaException("Sicoob nao retornou dados do boleto")
        return mapper.paraBoletoParaPagar(dto, consulta.codigoBarras)
    }

    override fun pagarBoleto(comando: ComandoPagamento): ComprovantePagamento {
        val credencial = comando.credencial
        val numeroConta = contaEmDigitos(credencial)
        val ctx = contexto(credencial, comando.sandbox)

        val url = basePagamentos(comando.sandbox) +
            "/boletos/pagamentos/" + enc(comando.codigoBarras)

        val corpo = SicoobPagamentoRequest(
            identificadorConsulta = comando.identificadorConsulta,
            valorBoleto = comando.valorBoleto,
            valorDescontoAbatimento = comando.valorDescontoAbatimento,
            valorMultaMora = comando.valorMultaMora,
            descricaoObservacao = comando.observacao,
            aceitaValorDivergente = comando.aceitaValorDivergente,
            numeroCpfCnpjPortador = comando.cpfCnpjPortador.filter(Char::isDigit),
            nomePortador = comando.nomePortador,
            amount = comando.valorPagamento,
            // Sicoob rejeita o campo ausente/null (HTTP 400 "date: nao pode estar
            // nulo"), apesar da doc oficial dizer que e opcional. O contrato do
            // dominio (ComandoPagamento.dataPagamento null = "paga hoje") fica
            // igual pros outros bancos; so aqui resolvemos a data concreta.
            date = (comando.dataPagamento ?: LocalDate.now()).toString(),
            debtorAccount = SicoobDebtorAccount(
                issuer = cooperativaEmDigitos(credencial).toInt(),
                number = numeroConta.toLong(),
                accountType = SicoobConfig.CONTA_TIPO_CORRENTE,
                personType = SicoobConfig.CONTA_PESSOA_JURIDICA,
            ),
        )

        val headers = HashMap(ctx.headers)
        headers["x-idempotency-key"] = chaveIdempotencia(credencial, comando)

        val resposta = http.postJson(
            url = url,
            payload = corpo,
            tipoPayload = SicoobPagamentoRequest::class.java,
            headers = headers,
            mtls = ctx.mtls,
        )
        if (!resposta.ok) {
            throw PagamentoBancarioException(
                "Sicoob recusou o pagamento (HTTP ${resposta.status}): ${mensagemErro(resposta.corpo)}",
            )
        }

        val dto = http.ler(resposta.corpo, SicoobComprovanteResponse::class.java)?.resultado
            ?: throw PagamentoBancarioException("Sicoob nao retornou o comprovante do pagamento")
        return mapper.paraComprovante(dto)
    }

    override fun consultarComprovante(consulta: ConsultaComprovante): ComprovantePagamento {
        val credencial = consulta.credencial
        val numeroConta = contaEmDigitos(credencial)
        val ctx = contexto(credencial, consulta.sandbox)

        val url = basePagamentos(consulta.sandbox) +
            "/boletos/pagamentos/" + consulta.idPagamento + "/comprovantes" +
            "?numeroConta=" + enc(numeroConta)

        val resposta = http.get(url, ctx.headers, ctx.mtls)
        if (!resposta.ok) {
            throw ConsultaBancariaException(
                "Sicoob recusou a consulta do comprovante (HTTP ${resposta.status}): ${mensagemErro(resposta.corpo)}",
            )
        }

        val dto = http.ler(resposta.corpo, SicoobComprovanteResponse::class.java)?.resultado
            ?: throw ConsultaBancariaException("Sicoob nao retornou o comprovante")
        return mapper.paraComprovante(dto)
    }

    /** `DELETE {pagamentos-v3}/boletos/pagamentos/agendamentos/{idPagamento}` (Cancelar um agendamento de pagamento). */
    override fun cancelarAgendamento(comando: ComandoCancelamento) {
        val credencial = comando.credencial
        val numeroConta = contaEmDigitos(credencial)
        val ctx = contexto(credencial, comando.sandbox)

        val url = basePagamentos(comando.sandbox) +
            "/boletos/pagamentos/agendamentos/" + comando.idPagamento

        val corpo = SicoobCancelamentoRequest(numeroConta = numeroConta.toLong())

        val resposta = http.delete(url, corpo, SicoobCancelamentoRequest::class.java, ctx.headers, ctx.mtls)
        if (!resposta.ok) {
            throw PagamentoBancarioException(
                "Sicoob recusou o cancelamento do agendamento (HTTP ${resposta.status}): ${mensagemErro(resposta.corpo)}",
            )
        }
    }

    // --- helpers ----------------------------------------------------------

    private data class Contexto(val headers: Map<String, String>, val mtls: SicoobHttpClient.Mtls?)

    private fun contexto(credencial: BcoParamBanco, sandbox: Boolean): Contexto {
        val bearer = tokenProvider.bearer(credencial, sandbox)
        val clientId = clientId(credencial, sandbox)
        return Contexto(headersPadrao(bearer, clientId), mtlsDe(credencial, sandbox))
    }

    private fun basePagamentos(sandbox: Boolean): String =
        BancoIntegracao.SICOOB.urlBase(sandbox).trimEnd('/') +
            SicoobConfig.pathPagamentosV3(sandbox)

    private fun headersPadrao(bearer: String, clientId: String): Map<String, String> =
        mapOf(
            "Authorization" to "Bearer $bearer",
            "client_id" to clientId,
            "Accept" to "application/json",
        )

    private fun clientId(credencial: BcoParamBanco, sandbox: Boolean): String =
        if (sandbox) SicoobConfig.SANDBOX_CLIENT_ID
        else credencial.clientId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ConsultaBancariaException("Credencial Sicoob sem CLIENTID")

    private fun mtlsDe(credencial: BcoParamBanco, sandbox: Boolean): SicoobHttpClient.Mtls? =
        if (sandbox) null
        else SicoobHttpClient.Mtls(
            credencial.certArquivo?.takeIf { it.isNotEmpty() }
                ?: throw ConsultaBancariaException("Credencial Sicoob sem CERTARQUIVO"),
            credencial.certSenha.orEmpty(),
        )

    private fun contaEmDigitos(credencial: BcoParamBanco): String =
        credencial.numConta?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }
            ?: throw ConsultaBancariaException("Credencial Sicoob sem NUMCONTA")

    private fun cooperativaEmDigitos(credencial: BcoParamBanco): String =
        credencial.cooperativa?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }
            ?: throw ConsultaBancariaException("Credencial Sicoob sem COOPERATIVA")

    /**
     * `x-idempotency-key`: `<coop ate 4>-<conta ate 14>-<UUID>`. UUID derivado
     * de conta + codigo de barras + data => reenviar o mesmo pagamento e
     * idempotente; pagar outro boleto gera chave diferente.
     */
    private fun chaveIdempotencia(credencial: BcoParamBanco, comando: ComandoPagamento): String {
        val coop = cooperativaEmDigitos(credencial).takeLast(4)
        val conta = contaEmDigitos(credencial).takeLast(14)
        val semente = "$conta|${comando.codigoBarras}|${comando.dataPagamento ?: "hoje"}"
        val uuid = UUID.nameUUIDFromBytes(semente.toByteArray(Charsets.UTF_8))
        return "$coop-$conta-$uuid"
    }

    private fun mensagemErro(corpo: String): String {
        val resumo = try {
            http.ler(corpo, SicoobErroResponse::class.java)?.resumo()
        } catch (e: Exception) {
            null
        }
        return resumo ?: corpo.take(500)
    }

    private fun enc(valor: String): String = URLEncoder.encode(valor, "UTF-8")
}
