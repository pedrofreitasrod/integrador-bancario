package br.com.hero1.integradorbancario.integracao.dominio

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Comprovante de um pagamento efetuado/agendado/cancelado - formato neutro.
 *
 * Campos de beneficiario/pagador/conta/valores sao os que a API do banco
 * devolve JUNTO com o comprovante (mais confiaveis que a consulta previa do
 * boleto, feita antes de pagar) - usados para montar o PDF anexado ao titulo.
 */
data class ComprovantePagamento(
    val idPagamento: Long,
    val autenticacao: String? = null,
    /** Ex.: "Efetivado", "Agendado", "Rejeitado", "Cancelado". */
    val situacao: String? = null,
    val detalheSituacao: String? = null,
    /** Ex.: "PAGAMENTO EFETIVADO", "PAGAMENTO CANCELADO". */
    val tituloComprovante: String? = null,
    /** Data de cadastro do pagamento no Sicoob (`dataHoraCadastro`) - NAO e a data de agendamento. */
    val dataCadastro: LocalDate? = null,
    val dataVencimento: LocalDate? = null,
    val dataPagamento: LocalDate? = null,
    val valorBoleto: BigDecimal? = null,
    val valorDesconto: BigDecimal? = null,
    val valorMulta: BigDecimal? = null,
    val valorPagamento: BigDecimal? = null,
    val linhaDigitavel: String? = null,
    val nossoNumero: String? = null,
    val numeroDocumento: String? = null,
    val observacao: String? = null,
    val ouvidoria: String? = null,
    val cnpjBeneficiario: String? = null,
    val nomeBeneficiario: String? = null,
    /** Ex.: "33-BCO SANTANDER (BRASIL) S.A.". */
    val instituicaoBeneficiaria: String? = null,
    val cnpjPagador: String? = null,
    val nomePagador: String? = null,
    val numeroAgencia: String? = null,
    val nomeAgencia: String? = null,
    val numeroConta: String? = null,
    val nomeProprietarioConta: String? = null,
)
