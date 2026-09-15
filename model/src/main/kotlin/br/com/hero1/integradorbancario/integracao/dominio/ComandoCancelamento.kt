package br.com.hero1.integradorbancario.integracao.dominio

import br.com.hero1.integradorbancario.entity.BcoParamBanco

/** Parametros para cancelar um pagamento de boleto ja agendado (nao efetivado). */
data class ComandoCancelamento(
    val credencial: BcoParamBanco,
    val sandbox: Boolean,
    /** Id do pagamento devolvido em [ComprovantePagamento.idPagamento] (numero do agendamento). */
    val idPagamento: Long,
)
