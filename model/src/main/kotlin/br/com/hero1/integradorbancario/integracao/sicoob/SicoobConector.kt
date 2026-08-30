package br.com.hero1.integradorbancario.integracao.sicoob

import br.com.hero1.integradorbancario.integracao.BancoIntegracao
import br.com.hero1.integradorbancario.integracao.ConectorBancario
import br.com.hero1.integradorbancario.integracao.ConsultaBancariaException
import br.com.hero1.integradorbancario.integracao.dominio.Dda
import br.com.hero1.integradorbancario.integracao.dominio.FiltroDda
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobBoletosResponse
import java.net.URLEncoder

/**
 * Conector do Sicoob. Classe plana - montada em [IntegracaoBancaria].
 *
 * TODO(sicoob): confirmar path/params reais da consulta de DDA. Segue o padrao
 * da consulta de boletos por contrato + periodo da API de Cobranca V3.
 */
class SicoobConector(
    private val http: SicoobHttpClient,
    private val tokenProvider: SicoobTokenProvider,
    private val mapper: SicoobMapper,
) : ConectorBancario {

    override val codigoCompensacao: Int = SicoobConfig.CODIGO_COMPENSACAO

    override fun buscarDdas(filtro: FiltroDda): List<Dda> {
        val credencial = filtro.credencial
        val numeroContrato = credencial.numContrato
            ?: throw ConsultaBancariaException("Credencial Sicoob sem NUMCONTRATO")

        val clientId =
            if (filtro.sandbox) SicoobConfig.SANDBOX_CLIENT_ID
            else credencial.clientId
                ?: throw ConsultaBancariaException("Credencial Sicoob sem CLIENTID")

        val bearer = tokenProvider.bearer(credencial, filtro.sandbox)

        val mtls =
            if (filtro.sandbox) null
            else SicoobHttpClient.Mtls(
                credencial.certArquivo
                    ?: throw ConsultaBancariaException("Credencial Sicoob sem CERTARQUIVO"),
                credencial.certSenha.orEmpty(),
            )

        val base = BancoIntegracao.SICOOB.urlBase(filtro.sandbox).trimEnd('/') +
            SicoobConfig.PATH_COBRANCA_V3 + "boletos"
        val url = base +
            "?numeroContrato=" + enc(numeroContrato) +
            "&modalidade=1" +
            "&dataInicio=" + filtro.dataInicio +
            "&dataFim=" + filtro.dataFim

        val resposta = http.get(
            url = url,
            headers = mapOf(
                "Authorization" to "Bearer $bearer",
                "client_id" to clientId,
                "Accept" to "application/json",
            ),
            mtls = mtls,
        )

        if (!resposta.ok) {
            throw ConsultaBancariaException(
                "Sicoob recusou a consulta de DDA (HTTP ${resposta.status})",
            )
        }

        val corpo = http.ler(resposta.corpo, SicoobBoletosResponse::class.java)
        return corpo?.resultado.orEmpty().mapNotNull(mapper::paraDda)
    }

    private fun enc(valor: String): String = URLEncoder.encode(valor, "UTF-8")
}
