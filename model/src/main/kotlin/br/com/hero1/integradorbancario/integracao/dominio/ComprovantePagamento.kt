package br.com.hero1.integradorbancario.integracao.dominio

import java.math.BigDecimal
import java.time.LocalDate

/** Comprovante de um pagamento efetuado ou agendado - formato neutro. */
data class ComprovantePagamento(
    val idPagamento: Long,
    val autenticacao: String? = null,
    /** Ex.: "Efetivado", "Agendado", "Rejeitado". */
    val situacao: String? = null,
    val detalheSituacao: String? = null,
    val dataPagamento: LocalDate? = null,
    val valorPagamento: BigDecimal? = null,
    val linhaDigitavel: String? = null,
)
