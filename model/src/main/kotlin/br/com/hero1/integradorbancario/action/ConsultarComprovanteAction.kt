package br.com.hero1.integradorbancario.action

import br.com.hero1.integradorbancario.integracao.IntegracaoBancaria
import br.com.hero1.integradorbancario.integracao.IntegracaoBancariaException
import br.com.hero1.integradorbancario.integracao.LogHelper
import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.studio.annotations.hooks.ActionButton
import br.com.sankhya.studio.annotations.hooks.TransactionType
import br.com.sankhya.ws.ServiceContext
import com.sankhya.util.SessionFile
import com.sankhya.util.UIDGenerator
import java.math.BigDecimal

/**
 * Botao "Consultar Comprovante DDA" na tela do Financeiro (instancia nativa
 * `Financeiro` / TGFFIN).
 *
 * So VISUALIZA o comprovante de um pagamento/agendamento ja feito - busca o
 * comprovante no banco, gera o PDF ([AnexoFinanceiro.gerarPdf]) e devolve um
 * link pra abrir. Sem efeito colateral: nao faz baixa, nao anexa em TSIANX,
 * nao altera PROCESSADO/IDPAGAMENTO - so leitura + arquivo temporario de
 * sessao (`SessionFile` + `visualizadorArquivos.mge`, mesmo padrao de
 * `ImprimeAnexoAcao` nas personalizacoes existentes).
 *
 * So aceita 1 linha por vez - o retorno e um unico link.
 *
 * TODO: definir `resourceId` da tela de contas a pagar para o botao nao
 * aparecer em toda tela que usa a instancia `Financeiro`.
 */
@ActionButton(
    description = "Consultar Comprovante DDA",
    instanceName = "Financeiro",
    transactionType = TransactionType.AUTOMATIC,
)
class ConsultarComprovanteAction : AcaoRotinaJava {

    override fun doAction(contexto: ContextoAcao) {
        val linhas = contexto.linhas
        if (linhas.size != 1) {
            throw IntegracaoBancariaException("Selecione apenas um financeiro para consultar o comprovante.")
        }
        val linha = linhas[0]
        val nufin = linha.getCampo("NUFIN") as? BigDecimal
            ?: throw IntegracaoBancariaException("Linha sem NUFIN.")
        val logBco = LogHelper((linha.getCampo("CODEMP") as? BigDecimal)?.toInt())

        try {
            val pdf = IntegracaoBancaria.pagarDdaService.gerarPdfComprovante(nufin)
            contexto.setMensagemRetorno(linkParaArquivo(nufin, pdf))
        } catch (e: Exception) {
            logBco.registrarAsync(
                LogHelper.Status.ERROR,
                "Falha ao gerar comprovante do NUFIN ${nufin.toPlainString()}",
                e,
                ORIGEM,
            )
            throw e
        }
    }

    private fun linkParaArquivo(nufin: BigDecimal, pdf: ByteArray): String {
        val chave = "pdf_" + UIDGenerator.getNextID()
        val nome = "Comprovante_${nufin.toPlainString()}.pdf"
        val sessionFile = SessionFile.createSessionFile(nome, "application/pdf", pdf)
        ServiceContext.getCurrent().putHttpSessionAttribute(chave, sessionFile)
        val link = "/mge/visualizadorArquivos.mge?chaveArquivo=$chave"
        return "<a href=\"$link\" target=\"_blank\">$nome</a>"
    }

    private companion object {
        const val ORIGEM = "ConsultarComprovanteAction"
    }
}
