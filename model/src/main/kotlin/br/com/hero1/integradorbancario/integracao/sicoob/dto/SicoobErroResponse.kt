package br.com.hero1.integradorbancario.integracao.sicoob.dto

import com.squareup.moshi.Json

/**
 * Corpo de erro padrao das APIs do Sicoob (HTTP 400/406/500).
 * `{ "mensagens": [ { "mensagem": "...", "codigo": "..." } ] }`
 */
data class SicoobErroResponse(
    val mensagens: List<SicoobMensagemErro>? = null,
) {
    fun resumo(): String? =
        mensagens
            ?.mapNotNull { m -> m.mensagem?.let { t -> m.codigo?.let { "[$it] $t" } ?: t } }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("; ")
}

data class SicoobMensagemErro(
    @Json(name = "mensagem") val mensagem: String? = null,
    @Json(name = "codigo") val codigo: String? = null,
)
