package br.com.hero1.integradorbancario.integracao.sicoob.dto

import com.squareup.moshi.Json

/**
 * Corpo do `DELETE {pagamentos-v3}/boletos/pagamentos/agendamentos/{idPagamento}`
 * (Cancelar um agendamento de pagamento). Modelo `Cancelamento` da API
 * Cobranca Bancaria Pagamentos v3 - o `idPagamento` vai no path, so a conta
 * vai no corpo.
 */
data class SicoobCancelamentoRequest(
    @Json(name = "numeroConta") val numeroConta: Long,
)
