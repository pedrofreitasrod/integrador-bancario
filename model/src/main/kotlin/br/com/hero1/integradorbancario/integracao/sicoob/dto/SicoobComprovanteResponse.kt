package br.com.hero1.integradorbancario.integracao.sicoob.dto

import com.squareup.moshi.Json
import java.math.BigDecimal

/**
 * Resposta do pagamento e da consulta de comprovante
 * (`POST .../boletos/pagamentos/{codigoBarras}` e
 * `GET .../boletos/pagamentos/{idPagamento}/comprovantes`).
 * Envelope `{ "resultado": { ... } }`, modelo `ComprovantePagamento`.
 */
data class SicoobComprovanteResponse(
    val resultado: SicoobComprovanteDto? = null,
)

data class SicoobComprovanteDto(
    @Json(name = "idPagamento") val idPagamento: Long? = null,
    @Json(name = "numeroAutenticacaoPagamento") val numeroAutenticacaoPagamento: String? = null,
    @Json(name = "situacaoPagamento") val situacaoPagamento: String? = null,
    @Json(name = "descricaoDetalheSituacao") val descricaoDetalheSituacao: String? = null,
    @Json(name = "dataPagamento") val dataPagamento: String? = null,
    @Json(name = "valorPagamento") val valorPagamento: BigDecimal? = null,
    @Json(name = "numeroLinhaDigitavel") val numeroLinhaDigitavel: String? = null,
)
