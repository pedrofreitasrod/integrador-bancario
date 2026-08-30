package br.com.hero1.integradorbancario.integracao.sicoob.dto

import com.squareup.moshi.Json

/** Resposta do endpoint OAuth2 de token do Sicoob (Keycloak). */
data class SicoobTokenResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "scope") val scope: String? = null,
)
