package br.com.hero1.integradorbancario.integracao.sicoob.dto

import com.squareup.moshi.Json
import java.math.BigDecimal

/**
 * Corpo do `POST {pagamentos-v3}/boletos/pagamentos/{codigoBarras}` (Pagar
 * Boleto). Modelo `BoletoPagamento` da API Cobranca Bancaria Pagamentos v3.
 */
data class SicoobPagamentoRequest(
    @Json(name = "identificadorConsulta") val identificadorConsulta: String? = null,
    @Json(name = "valorBoleto") val valorBoleto: BigDecimal,
    @Json(name = "valorDescontoAbatimento") val valorDescontoAbatimento: BigDecimal,
    @Json(name = "valorMultaMora") val valorMultaMora: BigDecimal,
    @Json(name = "descricaoObservacao") val descricaoObservacao: String? = null,
    @Json(name = "aceitaValorDivergente") val aceitaValorDivergente: Boolean,
    @Json(name = "numeroCpfCnpjPortador") val numeroCpfCnpjPortador: String,
    @Json(name = "nomePortador") val nomePortador: String,
    /** Valor efetivo de pagamento. */
    @Json(name = "amount") val amount: BigDecimal,
    /**
     * Data de pagamento "yyyy-MM-dd". Obrigatorio na pratica: apesar da doc do
     * Sicoob dizer "opcional", a API rejeita null/ausente com HTTP 400
     * ("date: nao pode estar nulo") - `SicoobConector.pagarBoleto` sempre
     * resolve pra hoje quando o chamador nao especifica.
     */
    @Json(name = "date") val date: String,
    @Json(name = "debtorAccount") val debtorAccount: SicoobDebtorAccount,
)

/** Conta do associado que sera debitada (`debtorAccount`). */
data class SicoobDebtorAccount(
    /** Numero da cooperativa. */
    @Json(name = "issuer") val issuer: Int,
    /** Numero da conta habilitada para pagamentos via API. */
    @Json(name = "number") val number: Long,
    /** 0 = Conta Corrente. */
    @Json(name = "accountType") val accountType: Int,
    /** 0 = Pessoa Fisica, 1 = Pessoa Juridica. */
    @Json(name = "personType") val personType: Int,
)
