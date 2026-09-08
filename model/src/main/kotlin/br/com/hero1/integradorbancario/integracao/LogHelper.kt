package br.com.hero1.integradorbancario.integracao

import br.com.sankhya.jape.wrapper.JapeFactory
import br.com.sankhya.modelcore.util.AsyncAction
import com.sankhya.util.TimeUtils
import java.io.PrintWriter
import java.io.StringWriter
import java.math.BigDecimal
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Grava eventos do integrador bancario na tabela BCO_LOG (consultavel por tela)
 * e espelha no `java.util.logging` padrao.
 *
 * Baseado no `LogHelper` das personalizacoes existentes (BTG). Classe plana,
 * sem Guice - instanciar com `new`, passando a empresa (ou nada).
 *
 * - [registrar]: grava sincrono, dentro da transacao do chamador. Se o chamador
 *   der rollback, o log tambem some.
 * - [registrarAsync]: grava numa `AsyncTask` propria - o log **sobrevive** ao
 *   rollback da transacao do chamador (job / @ActionButton). Use para erros.
 */
class LogHelper(private val codEmp: BigDecimal? = null) {

    constructor(codEmp: Int?) : this(codEmp?.let { BigDecimal.valueOf(it.toLong()) })

    private val jul: Logger = Logger.getLogger(LogHelper::class.java.name)

    enum class Status { INFO, WARNING, ERROR }

    fun info(mensagem: String, origem: String? = null) =
        registrar(Status.INFO, mensagem, null, origem)

    fun alerta(mensagem: String, erro: Throwable? = null, origem: String? = null) =
        registrar(Status.WARNING, mensagem, erro, origem)

    fun erro(mensagem: String, erro: Throwable? = null, origem: String? = null) =
        registrar(Status.ERROR, mensagem, erro, origem)

    /** Grava sincrono (mesma transacao do chamador). */
    fun registrar(status: Status, mensagem: String, erro: Throwable? = null, origem: String? = null) {
        espelharNoJul(status, mensagem, erro, origem)
        val linha = Linha(codEmp, status, resumo(mensagem, erro), erro?.let(::stackTrace), origem)
        try {
            gravar(linha)
        } catch (falhaLog: Exception) {
            jul.log(Level.WARNING, "Falha ao gravar BCO_LOG: ${falhaLog.message}", falhaLog)
        }
    }

    /**
     * Grava numa `AsyncTask` propria - resiste ao rollback do chamador.
     * Os dados sao capturados agora; a gravacao roda depois, fora desta transacao.
     */
    fun registrarAsync(status: Status, mensagem: String, erro: Throwable? = null, origem: String? = null) {
        espelharNoJul(status, mensagem, erro, origem)
        val linha = Linha(codEmp, status, resumo(mensagem, erro), erro?.let(::stackTrace), origem)
        val task = AsyncAction.AsyncTask(TAREFA, true, true)
        task.setTaskBody {
            try {
                gravar(linha)
            } catch (falhaLog: Exception) {
                jul.log(Level.WARNING, "Falha ao gravar BCO_LOG (async): ${falhaLog.message}", falhaLog)
            }
        }
        try {
            AsyncAction.addTask(task)
        } catch (e: Exception) {
            jul.log(Level.WARNING, "Falha ao enfileirar task de BCO_LOG: ${e.message}", e)
        }
    }

    private data class Linha(
        val codEmp: BigDecimal?,
        val status: Status,
        val mensagem: String,
        val detalhamento: String?,
        val origem: String?,
    )

    private fun gravar(l: Linha) {
        JapeFactory.dao(ENT_LOG).create()
            .set("CODEMP", l.codEmp)
            .set("STATUS", l.status.name)
            .set("DATA", TimeUtils.getNow())
            .set("ORIGEM", l.origem?.take(MAX_ORIGEM))
            .set("MENSAGEM", l.mensagem.take(MAX_MENSAGEM))
            .set("DETALHAMENTO", l.detalhamento?.take(MAX_DETALHE)?.toCharArray())
            .save()
    }

    private fun resumo(mensagem: String, erro: Throwable?): String =
        if (erro == null) mensagem
        else "$mensagem: ${erro.message ?: erro.javaClass.simpleName}"

    private fun stackTrace(t: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { t.printStackTrace(it) }
        return sw.toString()
    }

    private fun espelharNoJul(status: Status, mensagem: String, erro: Throwable?, origem: String?) {
        val nivel = when (status) {
            Status.ERROR -> Level.SEVERE
            Status.WARNING -> Level.WARNING
            Status.INFO -> Level.INFO
        }
        val texto = origem?.let { "[$it] $mensagem" } ?: mensagem
        if (erro != null) jul.log(nivel, texto, erro) else jul.log(nivel, texto)
    }

    private companion object {
        const val ENT_LOG = "BcoLog"
        const val TAREFA = "IntegradorBancario-Log"
        const val MAX_MENSAGEM = 500
        const val MAX_ORIGEM = 120
        const val MAX_DETALHE = 4000
    }
}
