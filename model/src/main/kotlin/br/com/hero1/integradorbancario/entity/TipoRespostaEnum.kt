package br.com.hero1.integradorbancario.entity

/**
 * Discriminador da tabela BCO_RESPBANCO: qual tipo de resposta de API cada
 * linha representa. Cada constante corresponde a um <option> da lista TIPORESP
 * no dicionario (datadictionary/BCO_RESPBANCO.xml).
 *
 * Adicionar um novo tipo de resposta = uma constante aqui + um <option> no XML.
 */
enum class TipoRespostaEnum(val value: String) {

    /** Debito Direto Autorizado. */
    DDA("DDA");

    companion object {
        @JvmStatic
        fun fromValue(value: String?): TipoRespostaEnum? =
            entries.firstOrNull { it.value == value }
    }
}
