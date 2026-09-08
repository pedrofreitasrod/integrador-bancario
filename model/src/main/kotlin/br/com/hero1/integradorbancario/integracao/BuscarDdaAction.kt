package br.com.hero1.integradorbancario.integracao

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.studio.annotations.hooks.ActionButton
import br.com.sankhya.studio.annotations.hooks.Field
import br.com.sankhya.studio.annotations.hooks.FieldType
import br.com.sankhya.studio.annotations.hooks.Form
import br.com.sankhya.studio.annotations.hooks.TransactionType
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate

private const val PARAM_DATA_INICIO = "DTINICIO"
private const val PARAM_DATA_FIM = "DTFIM"
private const val PARAM_EMPRESA = "CODEMP"

/**
 * Botao "Buscar DDA" na tela do Financeiro (instancia nativa `Financeiro` / TGFFIN).
 *
 * Pede data inicial, data final e empresa; busca os DDAs de todos os bancos com
 * credencial ativa para essa empresa e grava os novos em BCO_RESPBANCO.
 *
 * Classe plana (construtor sem-args) - pega o service pronto de
 * [IntegracaoBancaria]. Mesmo padrao do [BuscarDdaJob].
 *
 * TODO: definir `resourceId` da tela de movimentacao financeira para o botao
 * nao aparecer em toda tela que usa a instancia `Financeiro`.
 */
@ActionButton(
    description = "Buscar DDA",
    instanceName = "Financeiro",
    transactionType = TransactionType.AUTOMATIC,
    form = Form(
        fields = [
            Field(
                name = PARAM_DATA_INICIO,
                label = "Vencimento - de",
                type = FieldType.DATE,
                required = true,
                saveLast = true
            ),
            Field(
                name = PARAM_DATA_FIM,
                label = "Vencimento - ate",
                type = FieldType.DATE,
                required = true,
                saveLast = true
            ),
            Field(
                name = PARAM_EMPRESA,
                label = "Empresa",
                type = FieldType.SEARCH,
                instance = "Empresa",
                required = true,
                saveLast = true
            ),
        ],
    ),
)
class BuscarDdaAction : AcaoRotinaJava {

    override fun doAction(contexto: ContextoAcao) {
        val inicio = dataParam(contexto, PARAM_DATA_INICIO)
        val fim = dataParam(contexto, PARAM_DATA_FIM)
        var codEmp = contexto.getParam(PARAM_EMPRESA)
        if(codEmp==null){
            throw IntegracaoBancariaException("Informe a empresa.")
        }
        codEmp = BigDecimal(codEmp.toString()).toInt()


        if (fim.isBefore(inicio)) {
            throw IntegracaoBancariaException("A data final nao pode ser anterior a inicial.")
        }

        val resultados = IntegracaoBancaria.buscarDdaService.buscarParaEmpresa(codEmp.toInt(), inicio, fim)
        val novos = resultados.sumOf { it.quantidadeGravada }
        val falhas = resultados.filter { !it.sucesso }

        val mensagem = buildString {
            append("Busca de DDA concluida: $novos registro(s) novo(s) em ${resultados.size} banco(s).")
            if (falhas.isNotEmpty()) {
                append(" Falha(s): ")
                append(falhas.joinToString("; ") { "banco ${it.idBanco} - ${it.erro}" })
            }
        }
        contexto.setMensagemRetorno(mensagem)
    }

    private fun dataParam(contexto: ContextoAcao, nome: String): LocalDate {
        val valor = contexto.getParam(nome) as? Timestamp
            ?: throw IntegracaoBancariaException("Informe a data '$nome'.")
        return valor.toLocalDateTime().toLocalDate()
    }
}
