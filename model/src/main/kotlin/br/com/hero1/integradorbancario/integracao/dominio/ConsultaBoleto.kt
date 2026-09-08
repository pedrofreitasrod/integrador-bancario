package br.com.hero1.integradorbancario.integracao.dominio

import br.com.hero1.integradorbancario.entity.BcoParamBanco
import java.time.LocalDate

/**
 * Parametros para consultar um boleto antes de pagar. Empresa e ambiente vem
 * da credencial; o codigo de barras identifica o titulo.
 */
data class ConsultaBoleto(
    val credencial: BcoParamBanco,
    val sandbox: Boolean,
    /** Codigo de barras (44 posicoes) ou linha digitavel (47) do boleto. */
    val codigoBarras: String,
    /** Data pretendida de pagamento; null = hoje. Afeta juros/multa/desconto calculados. */
    val dataPagamento: LocalDate? = null,
)
