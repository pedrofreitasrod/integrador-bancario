package br.com.hero1.integradorbancario.integracao.sicoob

/**
 * Constantes da integracao Sicoob. Valores especificos do contrato (paths,
 * escopos) ficam aqui para nao espalhar pelo codigo - e para o proximo banco
 * ter um lugar analogo.
 *
 * Fonte: portal developers.sicoob.com.br (API Cobranca Bancaria Pagamentos v3).
 */
internal object SicoobConfig {

    const val CODIGO_COMPENSACAO = 756

    /**
     * Sufixo da API de Cobranca Bancaria Pagamentos v3 (DDA + consulta e
     * pagamento de boletos), concatenado a urlBase do banco.
     *
     * ATENCAO: o path muda entre os ambientes.
     * - Producao : {host}/pagamentos/v3
     * - Sandbox  : {host}/cobranca-bancaria-pagamentos/v3
     */
    const val PATH_PAGAMENTOS_V3_PRODUCAO = "/pagamentos/v3"
    const val PATH_PAGAMENTOS_V3_SANDBOX = "/cobranca-bancaria-pagamentos/v3"

    fun pathPagamentosV3(sandbox: Boolean): String =
        if (sandbox) PATH_PAGAMENTOS_V3_SANDBOX else PATH_PAGAMENTOS_V3_PRODUCAO

    /** Servidor de autenticacao (Keycloak) de producao. Sandbox usa token fixo. */
    const val AUTH_BASE_PRODUCAO = "https://auth.sicoob.com.br/"

    /** Path do endpoint de token, concatenado a AUTH_BASE_PRODUCAO. */
    const val PATH_TOKEN = "auth/realms/cooperado/protocol/openid-connect/token"

    const val GRANT_TYPE = "client_credentials"

    /**
     * Escopos da API Cobranca Bancaria Pagamentos. A credencial (campo SCOPES)
     * deve listar todos os que ela usa, separados por espaco - ex.:
     * "pagamentos_consulta pagamentos_inclusao". [SCOPE_PADRAO] e o fallback.
     */
    const val SCOPE_PAGAMENTOS_CONSULTA = "pagamentos_consulta"
    const val SCOPE_PAGAMENTOS_INCLUSAO = "pagamentos_inclusao"
    const val SCOPE_PAGAMENTOS_ALTERACAO = "pagamentos_alteracao"
    const val SCOPE_PADRAO = SCOPE_PAGAMENTOS_CONSULTA

    /** Situacao do boleto DDA na consulta: 1=Em aberto, 2=Agendado, 3=Liquidado, 4=Baixado. */
    const val DDA_SITUACAO_EM_ABERTO = 1

    /** tipoData da consulta DDA: 1=Vencimento, 2=Emissao, 3=Inclusao. */
    const val DDA_TIPO_DATA_VENCIMENTO = 1

    /** accountType do debtorAccount no pagamento: 0=Conta Corrente. */
    const val CONTA_TIPO_CORRENTE = 0

    /**
     * personType do debtorAccount: 0=Pessoa Fisica, 1=Pessoa Juridica.
     * A conta debitada e sempre a da Empresa (CODEMP) do Sankhya -> sempre PJ.
     */
    const val CONTA_PESSOA_JURIDICA = 1

    // Valores publicos do ambiente sandbox do Sicoob (documentacao oficial).
    const val SANDBOX_CLIENT_ID = "9b5e603e428cc477a2841e2683c92d21"
    const val SANDBOX_ACCESS_TOKEN = "1301865f-c6bc-38f3-9f49-666dbcfc59c3"

    /** Sandbox usa token fixo; guardamos uma validade longa so para nao reautenticar a toa. */
    const val SANDBOX_EXPIRES_SEGUNDOS = 86_400
}
