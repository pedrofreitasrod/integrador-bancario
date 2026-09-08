package br.com.hero1.integradorbancario.integracao

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.studio.annotations.hooks.ActionButton
import br.com.sankhya.studio.annotations.hooks.TransactionType
import java.math.BigDecimal

/**
 * Botao "Autenticar" na tela de Parametros por Empresa (instancia `BcoParamBanco`).
 *
 * Para cada linha marcada, dispara a autenticacao no banco e grava o access
 * token (ACESSTOKEN / EXPIRES / DHAUTENTIQUE) na propria linha. As chamadas de
 * consulta/pagamento reaproveitam esse token ate ele expirar.
 *
 * Serve tambem como "testar credenciais": se o certificado / client id / conta
 * estiverem errados, a autenticacao falha aqui, antes de qualquer pagamento.
 * O resultado de cada linha vai para BCO_LOG via `LogHelper`.
 */
@ActionButton(
    description = "Autenticar",
    instanceName = "BcoParamBanco",
    transactionType = TransactionType.AUTOMATIC,
)
class AutenticarBancoAction : AcaoRotinaJava {

    override fun doAction(contexto: ContextoAcao) {
        val linhas = contexto.linhas
        if (linhas.isEmpty()) {
            throw IntegracaoBancariaException("Marque ao menos uma linha para autenticar.")
        }
        val service = IntegracaoBancaria.autenticacaoService

        val ok = StringBuilder()
        val falhas = StringBuilder()
        for (linha in linhas) {
            val idBanco = (linha.getCampo("IDBANCO") as? BigDecimal)?.toInt()
            val codEmp = (linha.getCampo("CODEMP") as? BigDecimal)?.toInt()
            val logBco = LogHelper(codEmp)
            if (idBanco == null || codEmp == null) {
                falhas.append("(linha sem IDBANCO/CODEMP) ")
                continue
            }
            try {
                val r = service.autenticar(idBanco, codEmp)
                ok.append("banco $idBanco / empresa $codEmp: token valido por ${r.expiraEmSegundos}s; ")
                logBco.registrar(
                    LogHelper.Status.INFO,
                    "Autenticacao OK no banco $idBanco (token valido por ${r.expiraEmSegundos}s)",
                    origem = ORIGEM,
                )
            } catch (e: Exception) {
                logBco.registrarAsync(
                    LogHelper.Status.ERROR,
                    "Falha ao autenticar no banco $idBanco / empresa $codEmp",
                    e,
                    ORIGEM,
                )
                falhas.append("banco $idBanco / empresa $codEmp: ${e.message}; ")
            }
        }

        val mensagem = buildString {
            if (ok.isNotEmpty()) append("Autenticados: ").append(ok)
            if (falhas.isNotEmpty()) append("Falhas: ").append(falhas)
            if (isEmpty()) append("Nada processado.")
        }
        contexto.setMensagemRetorno(mensagem)
    }

    private companion object {
        const val ORIGEM = "AutenticarBancoAction"
    }
}
