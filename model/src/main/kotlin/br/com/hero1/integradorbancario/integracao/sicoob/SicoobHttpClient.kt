package br.com.hero1.integradorbancario.integracao.sicoob

import br.com.hero1.integradorbancario.integracao.AutenticacaoBancariaException
import br.com.hero1.integradorbancario.integracao.IntegracaoBancariaException
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobListaResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
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
 * conteudo do .pfx da credencial (bytes vindos direto da coluna CERTARQUIVO),
 * cacheado por hash do conteudo. Moshi so para o parse JSON de `String` -> objeto.
 */
class SicoobHttpClient(
    private val moshi: Moshi,
) {

    private val log: Logger = Logger.getLogger(SicoobHttpClient::class.java.name)

    private val fabricasSsl = ConcurrentHashMap<String, SSLSocketFactory>()

    /** [pfxBytes] = valor bruto da coluna CERTARQUIVO (o .pfx, com o cabecalho de anexo do Sankhya). */
    class Mtls(val pfxBytes: ByteArray, val senha: String)

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

    /** POST com corpo JSON serializado de [payload] (tipo [tipoPayload]). */
    fun <T> postJson(
        url: String,
        payload: T,
        tipoPayload: Class<T>,
        headers: Map<String, String>,
        mtls: Mtls?,
    ): Resposta {
        val h = HashMap(headers)
        h["Content-Type"] = "application/json"
        return executar("POST", url, h, escrever(payload, tipoPayload), mtls)
    }

    /** DELETE com corpo JSON opcional. */
    fun <T> delete(
        url: String,
        payload: T?,
        tipoPayload: Class<T>,
        headers: Map<String, String>,
        mtls: Mtls?,
    ): Resposta {
        val h = HashMap(headers)
        val corpo = payload?.let {
            h["Content-Type"] = "application/json"
            escrever(it, tipoPayload)
        }
        return executar("DELETE", url, h, corpo, mtls)
    }

    fun <T> ler(corpo: String, tipo: Class<T>): T? =
        try {
            moshi.adapter(tipo).fromJson(corpo)
        } catch (e: Exception) {
            throw formatoInesperado(corpo, e)
        }

    /**
     * Le uma resposta de lista de [tipo]. Aceita as duas formas que o Sicoob
     * usa: array JSON cru (`[ ... ]`) e envelope (`{ "resultado": [ ... ] }`).
     */
    fun <T> lerLista(corpo: String, tipo: Class<T>): List<T> =
        try {
            if (corpo.trimStart().startsWith("[")) {
                val tipoLista = Types.newParameterizedType(List::class.java, tipo)
                moshi.adapter<List<T>>(tipoLista).fromJson(corpo).orEmpty()
            } else {
                val tipoEnvelope = Types.newParameterizedType(SicoobListaResponse::class.java, tipo)
                // resultado ausente (null) != lista vazia: no primeiro caso o
                // envelope nao e o esperado e devolver "0 registros" esconderia o erro.
                moshi.adapter<SicoobListaResponse<T>>(tipoEnvelope).fromJson(corpo)?.resultado
                    ?: throw IntegracaoBancariaException(
                        "Resposta do banco sem a lista esperada (campo 'resultado'). Corpo: ${corpo.take(500)}",
                    )
            }
        } catch (e: Exception) {
            throw formatoInesperado(corpo, e)
        }

    /** Erro de parse com um trecho do corpo - sem isso o diagnostico fica cego. */
    private fun formatoInesperado(corpo: String, e: Exception): IntegracaoBancariaException {
        if (e is IntegracaoBancariaException) return e
        return IntegracaoBancariaException(
            "Resposta do banco em formato inesperado (${e.message}). Corpo: ${corpo.take(500)}",
            e,
        )
    }

    private fun <T> escrever(payload: T, tipoPayload: Class<T>): String =
        moshi.adapter(tipoPayload).toJson(payload)

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
        fabricasSsl.getOrPut(hash(mtls.pfxBytes)) { montarSsl(mtls.pfxBytes, mtls.senha) }

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /**
     * O conteudo de CERTARQUIVO (campo `ARQUIVO` do Sankhya) chega com um
     * cabecalho antes dos bytes do arquivo:
     *
     *     __start_fileinformation__{"name":"...","size":9113,...}__end_fileinformation__<bytes>
     *
     * Este metodo remove esse cabecalho, usa o `size` do JSON para cortar
     * sobras no fim e devolve os bytes PKCS#12 crus (DER). Se ainda vier em
     * Base64, decodifica. Se nao der pra reconhecer, falha com mensagem clara.
     */
    private fun normalizarPfx(bruto: ByteArray): ByteArray {
        var bytes = removerCabecalhoAnexo(bruto)

        if (bytes.isNotEmpty() && bytes[0] == DER_SEQUENCE) return bytes

        val texto = String(bytes, Charsets.US_ASCII).trim()
            .substringAfterLast("base64,")
            .filter { !it.isWhitespace() }
        val base64 = runCatching { Base64.getDecoder().decode(texto) }
            .recoverCatching { Base64.getUrlDecoder().decode(texto) }
            .getOrNull()
        if (base64 != null && base64.isNotEmpty() && base64[0] == DER_SEQUENCE) {
            log.fine("CERTARQUIVO decodificado de Base64 (${base64.size} bytes DER)")
            return base64
        }

        val amostra = bytes.take(24).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
        throw AutenticacaoBancariaException(
            "CERTARQUIVO nao contem um .pfx (PKCS#12) reconhecivel apos remover o cabecalho de anexo. " +
                "Primeiros bytes: [$amostra]. Reenvie o arquivo .pfx no campo Certificado.",
        )
    }

    /** Tira o envelope `__start_fileinformation__{json}__end_fileinformation__` do campo ARQUIVO. */
    private fun removerCabecalhoAnexo(bruto: ByteArray): ByteArray {
        val prefixo = String(bruto, 0, minOf(bruto.size, CABECALHO_MAX), Charsets.ISO_8859_1)
        if (!prefixo.startsWith(MARCADOR_INICIO)) return bruto

        val fimMarcador = prefixo.indexOf(MARCADOR_FIM)
        if (fimMarcador < 0) {
            throw AutenticacaoBancariaException(
                "CERTARQUIVO comeca com cabecalho de anexo mas sem '$MARCADOR_FIM' - arquivo truncado?",
            )
        }
        val inicioConteudo = fimMarcador + MARCADOR_FIM.length
        if (inicioConteudo >= bruto.size) {
            throw AutenticacaoBancariaException("CERTARQUIVO so tem o cabecalho de anexo, sem o conteudo do .pfx")
        }
        val json = prefixo.substring(MARCADOR_INICIO.length, fimMarcador)
        val tamanho = Regex("\"size\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()

        var conteudo = bruto.copyOfRange(inicioConteudo, bruto.size)
        if (tamanho != null && tamanho in 1..conteudo.size) {
            conteudo = conteudo.copyOf(tamanho)
        }
        log.fine("CERTARQUIVO: cabecalho de anexo removido, conteudo com ${conteudo.size} bytes")
        return conteudo
    }

    private fun montarSsl(pfxBruto: ByteArray, senha: String): SSLSocketFactory {
        val senhaChars = senha.toCharArray()
        val pfx = normalizarPfx(pfxBruto)
        val keyStore = try {
            KeyStore.getInstance("PKCS12").apply {
                ByteArrayInputStream(pfx).use { load(it, senhaChars) }
            }
        } catch (e: Exception) {
            throw AutenticacaoBancariaException(
                "Falha ao abrir o certificado .pfx da credencial (${pfx.size} bytes) - " +
                    "confira o conteudo de CERTARQUIVO e a CERTSENHA",
                e,
            )
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

        /** Primeiro byte de um DER SEQUENCE (envelope do PKCS#12). */
        const val DER_SEQUENCE: Byte = 0x30

        // Envelope que o campo ARQUIVO do Sankhya poe antes dos bytes do arquivo.
        const val MARCADOR_INICIO = "__start_fileinformation__"
        const val MARCADOR_FIM = "__end_fileinformation__"
        const val CABECALHO_MAX = 8192
    }
}
