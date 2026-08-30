package br.com.hero1.integradorbancario.integracao.sicoob

import br.com.hero1.integradorbancario.integracao.AutenticacaoBancariaException
import br.com.hero1.integradorbancario.integracao.IntegracaoBancariaException
import com.squareup.moshi.Moshi
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Chamadas HTTP para as APIs dos bancos via `HttpURLConnection` (JDK).
 * Sem Retrofit/OkHttp - ver override em CLAUDE.md.
 *
 * mTLS: cada request de producao usa o SSLSocketFactory montado a partir do
 * .pfx da credencial (cacheado por caminho de certificado). Moshi so para o
 * parse JSON de `String` -> objeto.
 */
class SicoobHttpClient(
    private val moshi: Moshi,
) {

    private val fabricasSsl = ConcurrentHashMap<String, SSLSocketFactory>()

    data class Mtls(val caminhoPfx: String, val senha: String)

    data class Resposta(val status: Int, val corpo: String) {
        val ok: Boolean get() = status in 200..299
    }

    fun get(url: String, headers: Map<String, String>, mtls: Mtls?): Resposta =
        executar("GET", url, headers, null, mtls)

    fun postForm(
        url: String,
        campos: Map<String, String>,
        headers: Map<String, String>,
        mtls: Mtls?,
    ): Resposta {
        val corpo = campos.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val h = HashMap(headers)
        h["Content-Type"] = "application/x-www-form-urlencoded"
        return executar("POST", url, h, corpo, mtls)
    }

    fun <T> ler(corpo: String, tipo: Class<T>): T? =
        try {
            moshi.adapter(tipo).fromJson(corpo)
        } catch (e: IOException) {
            throw IntegracaoBancariaException("Resposta do banco em formato inesperado", e)
        }

    private fun executar(
        metodo: String,
        url: String,
        headers: Map<String, String>,
        corpo: String?,
        mtls: Mtls?,
    ): Resposta {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw IntegracaoBancariaException("Nao foi possivel abrir conexao com $url", e)
        }
        try {
            conn.requestMethod = metodo
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            if (conn is HttpsURLConnection && mtls != null) {
                conn.sslSocketFactory = fabricaSsl(mtls)
            }
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            if (corpo != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }
            }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val texto = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Resposta(status, texto)
        } catch (e: IOException) {
            throw IntegracaoBancariaException("Falha de rede em $metodo $url", e)
        } finally {
            conn.disconnect()
        }
    }

    private fun fabricaSsl(mtls: Mtls): SSLSocketFactory =
        fabricasSsl.getOrPut(mtls.caminhoPfx) { montarSsl(mtls.caminhoPfx, mtls.senha) }

    private fun montarSsl(caminhoPfx: String, senha: String): SSLSocketFactory {
        val senhaChars = senha.toCharArray()
        val keyStore = try {
            KeyStore.getInstance("PKCS12").apply {
                Files.newInputStream(Paths.get(caminhoPfx)).use { load(it, senhaChars) }
            }
        } catch (e: Exception) {
            throw AutenticacaoBancariaException("Falha ao ler o certificado .pfx: $caminhoPfx", e)
        }

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, senhaChars)

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val trustManager = tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: throw AutenticacaoBancariaException("TrustManager X509 nao encontrado")

        val ssl = SSLContext.getInstance("TLS")
        ssl.init(kmf.keyManagers, arrayOf<TrustManager>(trustManager), null)
        return ssl.socketFactory
    }

    private fun enc(valor: String): String = URLEncoder.encode(valor, "UTF-8")

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
