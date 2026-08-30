package br.com.hero1.integradorbancario.integracao

/** Resultado da busca de DDA de uma empresa/banco. */
data class ResultadoBuscaDda(
    val codEmp: Int,
    val idBanco: Int,
    val quantidadeConsultada: Int,
    val quantidadeGravada: Int,
    val erro: String? = null,
) {
    val sucesso: Boolean get() = erro == null
}
