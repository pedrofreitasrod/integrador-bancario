package br.com.hero1.integradorbancario.integracao.sicoob

import br.com.hero1.integradorbancario.entity.BcoParamBanco
import br.com.hero1.integradorbancario.integracao.AutenticacaoBancariaException
import br.com.hero1.integradorbancario.integracao.BancoDao
import br.com.hero1.integradorbancario.integracao.dominio.ResultadoAutenticacao
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobTokenResponse
import java.sql.Timestamp
import java.time.Instant
import java.util.logging.Logger

/**
 * Obtem e reaproveita o access token do Sicoob por credencial (banco+empresa).
 *
 * O token fica persistido em BCO_PARAMBANCO (ACESSTOKEN / EXPIRES /
 * DHAUTENTIQUE): antes de cada chamada, [bearer] olha esses campos e so
 * reautentica se o token expirou (ou vai expirar dentro da margem de
 * seguranca). O botao "Autenticar" da tela chama [renovar] para forcar.
 *
 * Sandbox: token publico fixo do Sicoob.
 * Producao: OAuth2 client_credentials via mTLS no Keycloak do Sicoob.
 */
class SicoobTokenProvider(
    private val http: SicoobHttpClient,
    private val dao: BancoDao,
) {

    private val log: Logger = Logger.getLogger(SicoobTokenProvider::class.java.name)

    /** Bearer para as chamadas de API - reautentica so quando necessario. */
    fun bearer(credencial: BcoParamBanco, sandbox: Boolean): String {
        if (sandbox) return SicoobConfig.SANDBOX_ACCESS_TOKEN
        return tokenReaproveitavel(credencial) ?: renovar(credencial, sandbox).token
    }

    /**
     * Forca uma nova autenticacao e persiste o token nos parametros da empresa.
     * Tambem atualiza o objeto [credencial] em memoria para as proximas
     * chamadas da mesma operacao nao reautenticarem.
     */
    fun renovar(credencial: BcoParamBanco, sandbox: Boolean): ResultadoAutenticacao {
        val id = credencial.id
            ?: throw AutenticacaoBancariaException("Parametros Sicoob sem chave (banco/empresa)")

        val resultado =
            if (sandbox) {
                ResultadoAutenticacao(
                    token = SicoobConfig.SANDBOX_ACCESS_TOKEN,
                    expiraEmSegundos = SicoobConfig.SANDBOX_EXPIRES_SEGUNDOS,
                    autenticadoEm = Instant.now(),
                    escopo = credencial.scopes,
                )
            } else {
                solicitarToken(credencial)
            }

        dao.salvarToken(id, resultado.token, resultado.expiraEmSegundos, Timestamp.from(resultado.autenticadoEm))
        credencial.acessToken = resultado.token
        credencial.expiresIn = resultado.expiraEmSegundos
        credencial.dhAutentique = Timestamp.from(resultado.autenticadoEm)

        log.info(
            "Token Sicoob renovado para banco ${id.idBanco} / empresa ${id.codEmp} " +
                "(expira_em=${resultado.expiraEmSegundos}s sandbox=$sandbox)",
        )
        return resultado
    }

    /** Token persistido ainda utilizavel (com folga de [MARGEM_SEGUNDOS]), ou null. */
    private fun tokenReaproveitavel(credencial: BcoParamBanco): String? {
        val token = credencial.acessToken?.takeIf { it.isNotBlank() } ?: return null
        val expiraEm = credencial.tokenExpiraEm() ?: return null
        return token.takeIf { Instant.now().plusSeconds(MARGEM_SEGUNDOS).isBefore(expiraEm) }
    }

    private fun solicitarToken(credencial: BcoParamBanco): ResultadoAutenticacao {
        val clientId = credencial.clientId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw AutenticacaoBancariaException("Credencial Sicoob sem CLIENTID")
        val scope = credencial.scopes?.takeIf { it.isNotBlank() } ?: SicoobConfig.SCOPE_PADRAO
        val certArquivo = credencial.certArquivo?.takeIf { it.isNotEmpty() }
            ?: throw AutenticacaoBancariaException("Credencial Sicoob sem CERTARQUIVO para producao")

        val url = SicoobConfig.AUTH_BASE_PRODUCAO + SicoobConfig.PATH_TOKEN
        val autenticadoEm = Instant.now()
        val resposta = http.postForm(
            url = url,
            campos = linkedMapOf(
                "grant_type" to SicoobConfig.GRANT_TYPE,
                "client_id" to clientId,
                "scope" to scope,
            ),
            headers = mapOf("Accept" to "application/json"),
            mtls = SicoobHttpClient.Mtls(certArquivo, credencial.certSenha.orEmpty()),
        )

        if (!resposta.ok) {
            throw AutenticacaoBancariaException(
                "Sicoob recusou a autenticacao (HTTP ${resposta.status})",
            )
        }
        val corpo = http.ler(resposta.corpo, SicoobTokenResponse::class.java)
            ?: throw AutenticacaoBancariaException("Resposta de token vazia do Sicoob")
        val token = corpo.accessToken
            ?: throw AutenticacaoBancariaException("Sicoob nao retornou access_token")

        return ResultadoAutenticacao(
            token = token,
            expiraEmSegundos = (corpo.expiresIn ?: 300L).toInt(),
            autenticadoEm = autenticadoEm,
            escopo = corpo.scope ?: scope,
        )
    }

    private companion object {
        /** Reautentica se faltar menos que isto para o token expirar. */
        const val MARGEM_SEGUNDOS = 60L
    }
}
