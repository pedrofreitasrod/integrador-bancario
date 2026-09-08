package br.com.hero1.integradorbancario.integracao.dominio

import br.com.hero1.integradorbancario.entity.BcoParamBanco

/** Parametros para reconsultar o comprovante de um pagamento ja efetuado/agendado. */
data class ConsultaComprovante(
    val credencial: BcoParamBanco,
    val sandbox: Boolean,
    /** Id do pagamento devolvido em [ComprovantePagamento.idPagamento]. */
    val idPagamento: Long,
)
