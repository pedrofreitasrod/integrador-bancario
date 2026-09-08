package br.com.hero1.integradorbancario.integracao.sicoob.dto

import com.squareup.moshi.Json
import java.math.BigDecimal

/**
 * Resposta de `GET {pagamentos-v3}/boletos/{codigoBarras}` (Consultar Boleto).
 * Envelope `{ "resultado": { ... } }`. So os campos consumidos estao mapeados.
 */
data class SicoobBoletoConsultaResponse(
    val resultado: SicoobBoletoConsultaDto? = null,
)

data class SicoobBoletoConsultaDto(
    @Json(name = "identificadorConsulta") val identificadorConsulta: String? = null,
    @Json(name = "codigoBarras") val codigoBarras: String? = null,
    @Json(name = "numeroLinhaDigitavel") val numeroLinhaDigitavel: String? = null,
    @Json(name = "dataVencimentoBoleto") val dataVencimentoBoleto: String? = null,
    @Json(name = "dataLimitePagamentoBoleto") val dataLimitePagamentoBoleto: String? = null,
    @Json(name = "valorBoleto") val valorBoleto: BigDecimal? = null,
    @Json(name = "valorPagamento") val valorPagamento: BigDecimal? = null,
    @Json(name = "permiteAlterarValor") val permiteAlterarValor: Boolean? = null,
    @Json(name = "bloquearPagamento") val bloquearPagamento: Boolean? = null,
    @Json(name = "mensagemBloqueioPagamento") val mensagemBloqueioPagamento: String? = null,
    @Json(name = "codigoSituacaoBoletoPagamento") val codigoSituacaoBoletoPagamento: String? = null,
    @Json(name = "numeroCpfCnpjBeneficiario") val numeroCpfCnpjBeneficiario: String? = null,
    @Json(name = "nomeRazaoSocialBeneficiario") val nomeRazaoSocialBeneficiario: String? = null,
)
