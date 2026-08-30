package br.com.hero1.integradorbancario.integracao.dominio

import java.math.BigDecimal
import java.time.LocalDate

/**
 * DDA normalizado - o formato neutro que TODO conector de banco devolve,
 * independente do payload cru de cada API. O mapper de cada banco converte
 * a resposta especifica para este modelo; a rotina de gravacao so conhece
 * este tipo.
 *
 * Adicionar um banco novo nao muda este modelo - se um banco trouxer um campo
 * que aqui nao existe e for relevante, o campo entra aqui (e em BCO_RESPBANCO).
 */
data class Dda(
    /** Identificador do titulo no banco (vira IDFINANCEIRO na PK de BCO_RESPBANCO). */
    val idFinanceiro: String,
    /** Codigo de compensacao do banco de origem. */
    val codigoBanco: Int,
    val cnpjBeneficiario: String? = null,
    val dataVencimento: LocalDate? = null,
    val valor: BigDecimal? = null,
    val dataNegociacao: LocalDate? = null,
    val nossoNumero: String? = null,
)
