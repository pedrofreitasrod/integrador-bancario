package br.com.hero1.integradorbancario.integracao.dominio

import br.com.hero1.integradorbancario.entity.BcoParamBanco
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Comando para efetuar (ou agendar) o pagamento de um boleto. Data futura em
 * [dataPagamento] = agendamento.
 *
 * Normalmente montado a partir de um [BoletoParaPagar] recem-consultado
 * (reaproveitando `identificadorConsulta`, `valorBoleto` e `valorPagamento`).
 */
data class ComandoPagamento(
    val credencial: BcoParamBanco,
    val sandbox: Boolean,
    val codigoBarras: String,
    val identificadorConsulta: String? = null,
    val valorBoleto: BigDecimal,
    /** Valor a debitar. Igual a [valorBoleto] quando nao ha divergencia. */
    val valorPagamento: BigDecimal,
    val valorDescontoAbatimento: BigDecimal = BigDecimal.ZERO,
    val valorMultaMora: BigDecimal = BigDecimal.ZERO,
    /** Data de pagamento; null = hoje. */
    val dataPagamento: LocalDate? = null,
    val aceitaValorDivergente: Boolean = false,
    /** CPF/CNPJ do responsavel pelo pagamento (portador). */
    val cpfCnpjPortador: String,
    val nomePortador: String,
    val observacao: String? = null,
)
