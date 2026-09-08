package br.com.hero1.integradorbancario.integracao.sicoob.dto

import com.squareup.moshi.Json
import java.math.BigDecimal

/**
 * Item do array retornado por `GET {pagamentos-v3}/boletos` (Movimentacoes DDA
 * da API Cobranca Bancaria Pagamentos v3 do Sicoob).
 *
 * A resposta e um array JSON cru (sem envelope). So os campos que o addon
 * consome estao mapeados; datas chegam como String "yyyy-MM-dd", valores como
 * numero JSON (lidos em BigDecimal via BigDecimalMoshiAdapter).
 */
data class SicoobBoletoDdaDto(
    @Json(name = "numeroCodigoBarras") val numeroCodigoBarras: String? = null,
    @Json(name = "numeroNossoNumero") val numeroNossoNumero: String? = null,
    @Json(name = "numeroDocumento") val numeroDocumento: String? = null,
    @Json(name = "numeroCpfCnpjBeneficiario") val numeroCpfCnpjBeneficiario: String? = null,
    @Json(name = "nomeRazaoSocialBeneficiario") val nomeRazaoSocialBeneficiario: String? = null,
    @Json(name = "numeroCpfCnpjPagador") val numeroCpfCnpjPagador: String? = null,
    @Json(name = "valorBoleto") val valorBoleto: BigDecimal? = null,
    @Json(name = "dataVencimentoBoleto") val dataVencimentoBoleto: String? = null,
    @Json(name = "dataEmissao") val dataEmissao: String? = null,
    @Json(name = "codigoTipoSituacaoBoleto") val codigoTipoSituacaoBoleto: Int? = null,
    @Json(name = "descricaoSituacaoBoleto") val descricaoSituacaoBoleto: String? = null,
    @Json(name = "dataPagamento") val dataPagamento: String? = null,
    @Json(name = "valorPagamento") val valorPagamento: BigDecimal? = null,
)
