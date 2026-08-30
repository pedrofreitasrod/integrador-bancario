package br.com.hero1.integradorbancario.integracao.sicoob

import br.com.hero1.integradorbancario.entity.BcoCadCredencial
import br.com.hero1.integradorbancario.entity.BcoCadCredencialId
import br.com.hero1.integradorbancario.integracao.AutenticacaoBancariaException
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobTokenResponse
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Obtem e cacheia o access token do Sicoob por credencial (banco+empresa).
 *
 * Sandbox: token publico fixo. Producao: OAuth2 client_credentials via mTLS,
 * com cache respeitando o expires_in (margem de 30s).
 */
class SicoobTokenProvider(
    private val http: SicoobHttpClient,
) {

    private data class TokenCache(val token: String, val expiraEm: Instant)

    private val cache = ConcurrentHashMap<BcoCadCredencialId, TokenCache>()

    fun bearer(credencial: BcoCadCredencial, sandbox: Boolean): String {
        if (sandbox) return SicoobConfig.SANDBOX_ACCESS_TOKEN

        val id = credencial.id
            ?: throw AutenticacaoBancariaException("Credencial Sicoob sem chave (banco/empresa)")

        val atual = cache[id]
        if (atual != null && Instant.now().isBefore(atual.expiraEm)) return atual.token

        val novo = solicitarToken(credencial)
        cache[id] = novo
        return novo.token
    }

    private fun solicitarToken(credencial: BcoCadCredencial): TokenCache {
        val clientId = credencial.clientId
            ?: throw AutenticacaoBancariaException("Credencial Sicoob sem CLIENTID")
        val scope = credencial.scopes?.takeIf { it.isNotBlank() } ?: SicoobConfig.SCOPE_PADRAO_DDA
        val certArquivo = credencial.certArquivo
            ?: throw AutenticacaoBancariaException("Credencial Sicoob sem CERTARQUIVO para producao")

        val url = SicoobConfig.AUTH_BASE_PRODUCAO + SicoobConfig.PATH_TOKEN
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

        val segundos = corpo.expiresIn ?: 300L
        val validade = (segundos - 30).coerceAtLeast(30)
        return TokenCache(token, Instant.now().plusSeconds(validade))
    }
}
