package br.com.hero1.integradorbancario.integracao.sicoob.dto

import com.squareup.moshi.Json

/**
 * Boleto retornado pela API de Cobranca Bancaria V3 do Sicoob.
 *
 * TODO(sicoob): alinhar os campos ao contrato REAL do endpoint de DDA/boletos
 * quando a documentacao estiver em maos. Os nomes abaixo seguem o padrao da
 * V3 de cobranca; datas chegam como String "yyyy-MM-dd".
 */
data class SicoobBoletosResponse(
    val resultado: List<SicoobBoletoDto>? = null,
)

data class SicoobBoletoDto(
    @Json(name = "nossoNumero") val nossoNumero: String? = null,
    @Json(name = "seuNumero") val seuNumero: String? = null,
    @Json(name = "identificacaoBoletoEmpresa") val identificacaoEmpresa: String? = null,
    /** Valor como String para nao perder precisao no parse JSON; convertido no mapper. */
    val valor: String? = null,
    @Json(name = "dataVencimento") val dataVencimento: String? = null,
    @Json(name = "dataEmissao") val dataEmissao: String? = null,
    val beneficiario: SicoobParteDto? = null,
    val pagador: SicoobParteDto? = null,
)

data class SicoobParteDto(
    @Json(name = "numeroCpfCnpj") val numeroCpfCnpj: String? = null,
    val nome: String? = null,
)
