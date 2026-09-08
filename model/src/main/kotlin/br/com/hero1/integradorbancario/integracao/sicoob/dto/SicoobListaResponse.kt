package br.com.hero1.integradorbancario.integracao.sicoob.dto

/**
 * Envelope das respostas de lista do Sicoob: `{ "resultado": [ ... ] }`.
 *
 * Alguns endpoints devolvem o array cru e outros embrulham em `resultado`;
 * `SicoobHttpClient.lerLista` aceita as duas formas.
 */
data class SicoobListaResponse<T>(
    val resultado: List<T>? = null,
)
