package br.com.hero1.integradorbancario.integracao.sicoob

/**
 * Constantes da integracao Sicoob. Valores especificos do contrato (paths,
 * escopos) ficam aqui para nao espalhar pelo codigo - e para o proximo banco
 * ter um lugar analogo.
 */
internal object SicoobConfig {

    const val CODIGO_COMPENSACAO = 756

    /** Sufixo da API de Cobranca Bancaria V3, concatenado a urlBase do banco. */
    const val PATH_COBRANCA_V3 = "/cobranca-bancaria/v3/"

    /** Servidor de autenticacao (Keycloak) de producao. Sandbox usa token fixo. */
    const val AUTH_BASE_PRODUCAO = "https://auth.sicoob.com.br/"

    /** Path do endpoint de token, concatenado a AUTH_BASE_PRODUCAO. */
    const val PATH_TOKEN = "auth/realms/cooperado/protocol/openid-connect/token"

    const val GRANT_TYPE = "client_credentials"

    /** Escopo default da consulta de boletos. Ajustar conforme liberacao no portal. */
    const val SCOPE_PADRAO_DDA = "cobranca_boletos_consultar"

    // Valores publicos do ambiente sandbox do Sicoob (documentacao oficial).
    const val SANDBOX_CLIENT_ID = "9b5e603e428cc477a2841e2683c92d21"
    const val SANDBOX_ACCESS_TOKEN = "1301865f-c6bc-38f3-9f49-666dbcfc59c3"
}
