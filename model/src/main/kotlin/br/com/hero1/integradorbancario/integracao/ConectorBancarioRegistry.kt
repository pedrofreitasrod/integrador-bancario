package br.com.hero1.integradorbancario.integracao

/**
 * Resolve o [ConectorBancario] pelo codigo de compensacao do banco.
 *
 * Classe plana - recebe a lista de conectores no construtor (montada em
 * [IntegracaoBancaria]). Adicionar um banco = mais um conector nessa lista.
 */
class ConectorBancarioRegistry(conectores: List<ConectorBancario>) {

    private val porCodigo: Map<Int, ConectorBancario> =
        conectores.associateBy { it.codigoCompensacao }

    /** @throws BancoNaoSuportadoException se nao ha conector para o codigo. */
    fun para(codigoCompensacao: Int): ConectorBancario =
        porCodigo[codigoCompensacao]
            ?: throw BancoNaoSuportadoException(
                "Banco sem conector de integracao implementado: $codigoCompensacao",
            )

    fun suporta(codigoCompensacao: Int): Boolean = porCodigo.containsKey(codigoCompensacao)

    fun codigosSuportados(): Set<Int> = porCodigo.keys
}
