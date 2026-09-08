package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.integracao.sankhya.AnexoFinanceiro
import br.com.hero1.integradorbancario.integracao.sankhya.BaixaSankhya
import java.math.BigDecimal
import java.sql.Timestamp

/**
 * Orquestra o pagamento de um DDA a partir de um titulo do Financeiro:
 * consulta e paga o boleto no banco, baixa/concilia o titulo no Sankhya, gera
 * o comprovante em PDF (anexo + TSIANX) e marca o DDA como processado.
 *
 * Classe plana (sem Guice). Roda dentro da transacao do @ActionButton.
 * Anomalias (pagamento nao efetivado, falha ao anexar) vao para BCO_LOG via
 * `LogHelper` async - o sucesso e logado pelo `PagarDdaAction`.
 */
class PagarDdaService(
    private val dao: BancoDao,
    private val pagamentos: PagamentoService,
    private val baixa: BaixaSankhya,
    private val anexo: AnexoFinanceiro,
) {

    data class Resultado(
        val nufin: BigDecimal,
        val idPagamento: Long,
        val situacao: String?,
        val valorPago: BigDecimal?,
        val baixado: Boolean,
    )

    fun pagarPorNufin(nufin: BigDecimal, codUsu: BigDecimal): Resultado {
        val dda = dao.ddaPorNufin(nufin)
            ?: throw IntegracaoBancariaException(
                "Nenhum DDA vinculado ao financeiro $nufin. Rode a busca de DDA e confira o vinculo (campo NUFIN) antes.",
            )
        if (dda.processado == true) {
            throw IntegracaoBancariaException(
                "O DDA do financeiro $nufin ja foi processado (pagamento ${dda.idPagamento}).",
            )
        }

        val pk = dda.id ?: error("DDA sem PK")
        val idBanco = pk.idBanco ?: error("DDA sem IDBANCO")
        val codEmp = pk.codEmp ?: error("DDA sem CODEMP")
        val logBco = LogHelper(codEmp)
        val codigoBarras = pk.idFinanceiro?.takeIf { it.isNotBlank() }
            ?: throw IntegracaoBancariaException("DDA do financeiro $nufin sem codigo de barras.")

        val param = dao.credencial(idBanco, codEmp)
            ?: throw IntegracaoBancariaException("Empresa $codEmp sem parametros para o banco $idBanco.")
        val empresa = dao.empresa(codEmp)
            ?: throw IntegracaoBancariaException("Empresa $codEmp nao encontrada.")
        val finVO = dao.financeiroVO(nufin)
            ?: throw IntegracaoBancariaException("Financeiro $nufin nao encontrado.")
        if (finVO.asTimestamp("DHBAIXA") != null) {
            throw IntegracaoBancariaException("Financeiro $nufin ja esta baixado.")
        }

        val boleto = pagamentos.consultarBoleto(idBanco, codEmp, codigoBarras)
        val comprovante = pagamentos.pagarBoleto(
            idBanco = idBanco,
            codEmp = codEmp,
            boleto = boleto,
            cpfCnpjPortador = empresa.cnpj,
            nomePortador = empresa.nome,
            observacao = "Pagamento DDA - NUFIN ${nufin.toPlainString()}",
        )

        val efetivado = comprovante.situacao?.trim().equals("Efetivado", ignoreCase = true)
        if (efetivado) {
            val codConta = (param.codCta ?: finVO.asBigDecimal("CODCTABCOINT")?.toInt())
                ?: throw IntegracaoBancariaException(
                    "Sem conta bancaria para a baixa: preencha CODCTA nos parametros ou a conta no titulo.",
                )
            baixa.baixar(
                BaixaSankhya.Parametros(
                    nufin = nufin,
                    codUsu = codUsu,
                    codEmp = BigDecimal.valueOf(codEmp.toLong()),
                    codConta = BigDecimal.valueOf(codConta.toLong()),
                    codTipoOperacao = param.codTipoOper?.let { BigDecimal.valueOf(it.toLong()) },
                    dataBaixa = comprovante.dataPagamento?.let { Timestamp.valueOf(it.atStartOfDay()) }
                        ?: Timestamp(System.currentTimeMillis()),
                    valorPago = comprovante.valorPagamento
                        ?: boleto.valorPagamento
                        ?: boleto.valorBoleto
                        ?: BigDecimal.ZERO,
                ),
            )
        } else {
            logBco.registrarAsync(
                LogHelper.Status.WARNING,
                "Pagamento do NUFIN $nufin retornou situacao '${comprovante.situacao}' - baixa nao executada.",
                origem = ORIGEM,
            )
        }

        try {
            anexo.anexarComprovante(nufin, codUsu, comprovante, boleto)
        } catch (e: Exception) {
            logBco.registrarAsync(
                LogHelper.Status.WARNING,
                "Pagamento efetuado mas falhou ao anexar o comprovante (NUFIN $nufin)",
                e,
                ORIGEM,
            )
        }

        dao.marcarPago(
            pk = pk,
            idPagamento = comprovante.idPagamento.toString(),
            autenticacao = comprovante.autenticacao,
            situacao = comprovante.situacao,
            valorPago = comprovante.valorPagamento,
            dataPagamento = comprovante.dataPagamento?.let { Timestamp.valueOf(it.atStartOfDay()) },
            processado = efetivado,
        )

        return Resultado(nufin, comprovante.idPagamento, comprovante.situacao, comprovante.valorPagamento, efetivado)
    }

    private companion object {
        const val ORIGEM = "PagarDdaService"
    }
}
