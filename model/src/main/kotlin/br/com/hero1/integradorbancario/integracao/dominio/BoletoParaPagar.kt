package br.com.hero1.integradorbancario.integracao.dominio

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Boleto consultado - o formato neutro que todo conector devolve na consulta,
 * independente do payload de cada banco. Traz o que a rotina precisa para
 * decidir e efetuar o pagamento.
 */
data class BoletoParaPagar(
    val codigoBarras: String,
    /** Hash devolvido pelo banco na consulta, exigido de volta no pagamento (Sicoob). */
    val identificadorConsulta: String? = null,
    val linhaDigitavel: String? = null,
    val dataVencimento: LocalDate? = null,
    val dataLimitePagamento: LocalDate? = null,
    /** Valor nominal do boleto. */
    val valorBoleto: BigDecimal? = null,
    /** Valor a pagar ja com juros/multa/desconto para a data consultada. */
    val valorPagamento: BigDecimal? = null,
    val permiteAlterarValor: Boolean = false,
    val pagamentoBloqueado: Boolean = false,
    val mensagemBloqueio: String? = null,
    val cnpjBeneficiario: String? = null,
    val nomeBeneficiario: String? = null,
    val situacao: String? = null,
)
