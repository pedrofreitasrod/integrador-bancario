package br.com.hero1.integradorbancario.integracao.sicoob.dto

import com.squareup.moshi.Json
import java.math.BigDecimal

/**
 * Resposta do pagamento e da consulta de comprovante
 * (`POST .../boletos/pagamentos/{codigoBarras}` e
 * `GET .../boletos/pagamentos/{idPagamento}/comprovantes`).
 * Envelope `{ "resultado": { ... } }`, modelo `ComprovantePagamento`.
 *
 * Campos confirmados contra retorno real da API (consulta de comprovante de
 * um pagamento cancelado, 2026-09-15) - so os que o addon consome estao
 * mapeados.
 */
data class SicoobComprovanteResponse(
    val resultado: SicoobComprovanteDto? = null,
)

data class SicoobComprovanteDto(
    @Json(name = "idPagamento") val idPagamento: Long? = null,
    @Json(name = "numeroAutenticacaoPagamento") val numeroAutenticacaoPagamento: String? = null,
    @Json(name = "situacaoPagamento") val situacaoPagamento: String? = null,
    @Json(name = "descricaoDetalheSituacao") val descricaoDetalheSituacao: String? = null,
    @Json(name = "descricaoTituloComprovante") val descricaoTituloComprovante: String? = null,
    @Json(name = "dataHoraCadastro") val dataHoraCadastro: String? = null,
    @Json(name = "dataVencimento") val dataVencimento: String? = null,
    @Json(name = "dataPagamento") val dataPagamento: String? = null,
    @Json(name = "valorBoleto") val valorBoleto: BigDecimal? = null,
    @Json(name = "valorAbatimentoDesconto") val valorAbatimentoDesconto: BigDecimal? = null,
    @Json(name = "valorMultaMora") val valorMultaMora: BigDecimal? = null,
    @Json(name = "valorPagamento") val valorPagamento: BigDecimal? = null,
    @Json(name = "numeroLinhaDigitavel") val numeroLinhaDigitavel: String? = null,
    @Json(name = "nossoNumero") val nossoNumero: String? = null,
    @Json(name = "numeroDocumento") val numeroDocumento: String? = null,
    @Json(name = "descricaoObservacao") val descricaoObservacao: String? = null,
    @Json(name = "descricaoOuvidoria") val descricaoOuvidoria: String? = null,
    @Json(name = "numeroCpfCnpjBeneficiario") val numeroCpfCnpjBeneficiario: String? = null,
    @Json(name = "nomeRazaoSocialBeneficiario") val nomeRazaoSocialBeneficiario: String? = null,
    @Json(name = "numeroInstituicaoEmissora") val numeroInstituicaoEmissora: Int? = null,
    @Json(name = "nomeInstituicaoEmissora") val nomeInstituicaoEmissora: String? = null,
    @Json(name = "numeroCpfCnpjPagador") val numeroCpfCnpjPagador: String? = null,
    @Json(name = "nomeRazaoSocialPagador") val nomeRazaoSocialPagador: String? = null,
    @Json(name = "numeroAgencia") val numeroAgencia: String? = null,
    @Json(name = "nomeAgencia") val nomeAgencia: String? = null,
    @Json(name = "numeroConta") val numeroConta: Long? = null,
    @Json(name = "nomeProprietarioContaCorrente") val nomeProprietarioContaCorrente: String? = null,
)
