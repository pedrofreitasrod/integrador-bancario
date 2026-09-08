package br.com.hero1.integradorbancario.integracao.sicoob.dto

import com.squareup.moshi.Json

/**
 * Item do array retornado por `GET {pagamentos-v3}/boletos` (Movimentacoes DDA
 * da API Cobranca Bancaria Pagamentos v3 do Sicoob).
 *
 * A resposta e um array JSON cru (sem envelope). So os campos que o addon
 * consome estao mapeados; datas chegam como String "yyyy-MM-dd", valores como
 * numero JSON (lidos em BigDecimal via BigDecimalMoshiAdapter).
 */
data class ResultadoSicoobBoletoDdaDto(
    @Json(name = "resultado") val numeroCodigoBarras: List<SicoobBoletoDdaDto>? = null,

    )
