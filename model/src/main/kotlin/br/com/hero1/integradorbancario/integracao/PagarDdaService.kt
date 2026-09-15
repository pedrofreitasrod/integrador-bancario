package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.entity.BcoParamBanco
import br.com.hero1.integradorbancario.entity.BcoRespBanco
import br.com.hero1.integradorbancario.entity.BcoRespBancoId
import br.com.hero1.integradorbancario.integracao.dominio.BoletoParaPagar
import br.com.hero1.integradorbancario.integracao.dominio.ComprovantePagamento
import br.com.hero1.integradorbancario.integracao.sankhya.AnexoFinanceiro
import br.com.hero1.integradorbancario.integracao.sankhya.BaixaSankhya
import br.com.sankhya.jape.vo.DynamicVO
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate

/**
 * Orquestra o pagamento de um DDA a partir de um titulo do Financeiro:
 * consulta e paga o boleto no banco, baixa/concilia o titulo no Sankhya, gera
 * o comprovante em PDF (anexo + TSIANX) e marca o DDA como processado.
 *
 * Classe plana (sem Guice). Roda dentro da transacao do @ActionButton.
 * Anomalias (pagamento nao efetivado, falha ao anexar) vao para BCO_LOG via
 * `LogHelper` async - o sucesso e logado pelo chamador (`PagarDdaAction` /
 * `ConsultarComprovanteAction`).
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

    /** @param dataPagamento null = paga hoje; data futura = agenda o pagamento no banco. */
    fun pagarPorNufin(nufin: BigDecimal, codUsu: BigDecimal, dataPagamento: LocalDate? = null): Resultado {
        val ctx = carregarContexto(nufin)
        // PROCESSADO='N' nao significa "nunca tentou pagar" - um pagamento que
        // efetivou mas cuja baixa falhou fica assim de proposito (ver
        // finalizarComprovante), pra continuar reconsultavel. Pagar de novo
        // aqui geraria um SEGUNDO pagamento real no banco - so "Consultar
        // Comprovante" pode retomar um DDA que ja tem IDPAGAMENTO.
        if (!ctx.dda.idPagamento.isNullOrBlank()) {
            throw IntegracaoBancariaException(
                "DDA do financeiro $nufin ja tem um pagamento registrado (id ${ctx.dda.idPagamento}) - " +
                    "use 'Consultar Comprovante' para retomar, nao pague de novo.",
            )
        }

        val empresa = dao.empresa(ctx.codEmp)
            ?: throw IntegracaoBancariaException("Empresa ${ctx.codEmp} nao encontrada.")

        val boleto = pagamentos.consultarBoleto(ctx.idBanco, ctx.codEmp, ctx.codigoBarras, dataPagamento)
        val comprovante = pagamentos.pagarBoleto(
            idBanco = ctx.idBanco,
            codEmp = ctx.codEmp,
            boleto = boleto,
            cpfCnpjPortador = empresa.cnpj,
            nomePortador = empresa.nome,
            dataPagamento = dataPagamento,
            observacao = "Pagamento DDA - NUFIN ${nufin.toPlainString()}",
        )

        val efetivado = finalizarComprovante(nufin, codUsu, ctx, comprovante) { boleto }
        return Resultado(nufin, comprovante.idPagamento, comprovante.situacao, comprovante.valorPagamento, efetivado)
    }

    /**
     * Reconsulta a situacao no banco e, se virou "Efetivado", tenta a baixa de
     * novo (recuperacao de um pagamento que efetivou mas cuja baixa falhou -
     * ver nota em [finalizarComprovante]).
     *
     * TODO: sem botao/job que chame isto no momento - `ConsultarComprovanteAction`
     * foi trocado para so visualizar o PDF ([gerarPdfComprovante], sem side
     * effect). Fica disponivel aqui pra quando decidirem onde disparar a
     * retentativa (outro botao, ou o job noturno).
     */
    fun reconsultarComprovante(nufin: BigDecimal, codUsu: BigDecimal): Resultado {
        val ctx = carregarContexto(nufin)
        val idPagamento = ctx.dda.idPagamento?.toLongOrNull()
            ?: throw IntegracaoBancariaException(
                "DDA do financeiro $nufin ainda nao tem pagamento registrado - use 'Pagar DDA' primeiro.",
            )

        val comprovante = pagamentos.consultarComprovante(ctx.idBanco, ctx.codEmp, idPagamento)
        // So consulta o boleto (chamada extra ao banco) se realmente precisar dele
        // pra baixa/anexo - evita gasto de API em toda reconsulta ainda pendente.
        val efetivado = finalizarComprovante(nufin, codUsu, ctx, comprovante) {
            pagamentos.consultarBoleto(ctx.idBanco, ctx.codEmp, ctx.codigoBarras)
        }
        return Resultado(nufin, comprovante.idPagamento, comprovante.situacao, comprovante.valorPagamento, efetivado)
    }

    /**
     * Cancela no banco um pagamento agendado (ainda nao efetivado) e libera o
     * DDA para uma nova tentativa: limpa os dados do pagamento cancelado
     * (IDPAGAMENTO/AUTENTICACAO/VLRPAGO/DTPAGAMENTO), mantem PROCESSADO='N' e
     * o vinculo com o NUFIN. TGFFIN nao e tocado - um agendamento nunca chega
     * "Efetivado" ([finalizarComprovante] so grava la nesse caso), entao nao
     * ha nada pra reverter no titulo.
     */
    fun cancelarAgendamento(nufin: BigDecimal) {
        val ctx = carregarContexto(nufin)
        val idPagamento = ctx.dda.idPagamento?.toLongOrNull()
            ?: throw IntegracaoBancariaException("DDA do financeiro $nufin ainda nao tem pagamento agendado.")

        pagamentos.cancelarAgendamento(ctx.idBanco, ctx.codEmp, idPagamento)

        dao.marcarPago(
            pk = ctx.pk,
            idPagamento = null,
            autenticacao = null,
            situacao = "Cancelado pelo usuario",
            valorPago = null,
            dataPagamento = null,
            processado = false,
        )
    }

    /**
     * So gera o PDF do comprovante de um DDA que ja tem pagamento registrado -
     * sem efeito colateral nenhum (nao faz baixa, nao anexa em TSIANX, nao
     * altera PROCESSADO/IDPAGAMENTO). Uso: botao "Consultar Comprovante DDA",
     * so pra visualizar. Ao contrario de [carregarContexto], nao bloqueia DDA
     * ja processado - ver o comprovante de algo ja concluido e o caso normal.
     */
    fun gerarPdfComprovante(nufin: BigDecimal): ByteArray {
        val dda = dao.ddaPorNufin(nufin)
            ?: throw IntegracaoBancariaException(
                "Nenhum DDA vinculado ao financeiro $nufin. Rode a busca de DDA e confira o vinculo (campo NUFIN) antes.",
            )
        val pk = dda.id ?: throw IntegracaoBancariaException("DDA do financeiro $nufin sem PK.")
        val idBanco = pk.idBanco ?: throw IntegracaoBancariaException("DDA do financeiro $nufin sem IDBANCO.")
        val codEmp = pk.codEmp ?: throw IntegracaoBancariaException("DDA do financeiro $nufin sem CODEMP.")
        val codigoBarras = pk.idFinanceiro?.takeIf { it.isNotBlank() }
            ?: throw IntegracaoBancariaException("DDA do financeiro $nufin sem codigo de barras.")
        val idPagamento = dda.idPagamento?.toLongOrNull()
            ?: throw IntegracaoBancariaException(
                "DDA do financeiro $nufin ainda nao tem pagamento registrado - use 'Pagar DDA' primeiro.",
            )

        val comprovante = pagamentos.consultarComprovante(idBanco, codEmp, idPagamento)
        return anexo.gerarPdf(nufin, comprovante, codigoBarras)
    }

    // --- helpers ---------------------------------------------------------------

    private data class ContextoPagamento(
        val dda: BcoRespBanco,
        val pk: BcoRespBancoId,
        val idBanco: Int,
        val codEmp: Int,
        val codigoBarras: String,
        val param: BcoParamBanco,
        val finVO: DynamicVO,
        val logBco: LogHelper,
    )

    /** Carrega e valida o DDA/titulo de [nufin] - comum a pagamento novo e reconsulta. */
    private fun carregarContexto(nufin: BigDecimal): ContextoPagamento {
        val dda = dao.ddaPorNufin(nufin)
            ?: throw IntegracaoBancariaException(
                "Nenhum DDA vinculado ao financeiro $nufin. Rode a busca de DDA e confira o vinculo (campo NUFIN) antes.",
            )
        if (dda.processado == true) {
            throw IntegracaoBancariaException(
                "O DDA do financeiro $nufin ja foi processado (pagamento ${dda.idPagamento}).",
            )
        }

        val pk = dda.id ?: throw IntegracaoBancariaException("DDA do financeiro $nufin sem PK.")
        val idBanco = pk.idBanco ?: throw IntegracaoBancariaException("DDA do financeiro $nufin sem IDBANCO.")
        val codEmp = pk.codEmp ?: throw IntegracaoBancariaException("DDA do financeiro $nufin sem CODEMP.")
        val codigoBarras = pk.idFinanceiro?.takeIf { it.isNotBlank() }
            ?: throw IntegracaoBancariaException("DDA do financeiro $nufin sem codigo de barras.")

        val param = dao.credencial(idBanco, codEmp)
            ?: throw IntegracaoBancariaException("Empresa $codEmp sem parametros para o banco $idBanco.")
        val finVO = dao.financeiroVO(nufin)
            ?: throw IntegracaoBancariaException("Financeiro $nufin nao encontrado.")
        if (finVO.asTimestamp("DHBAIXA") != null) {
            throw IntegracaoBancariaException("Financeiro $nufin ja esta baixado.")
        }

        return ContextoPagamento(dda, pk, idBanco, codEmp, codigoBarras, param, finVO, LogHelper(codEmp))
    }

    /**
     * Se [comprovante] veio "Efetivado", baixa o titulo e anexa o PDF; se veio
     * numa situacao terminal de falha (rejeitado/cancelado), so avisa e encerra
     * o DDA (sem baixa); qualquer outra situacao (ex. "Agendado") fica pendente
     * pra proxima reconsulta - sem gerar anexo novo a cada tentativa.
     * [boleto] so e avaliado (chamada ao banco) quando efetivado, pra nao gastar
     * API em reconsultas que ainda vao continuar pendentes.
     *
     * IMPORTANTE: o `IDPAGAMENTO` e gravado JA, antes de tentar a baixa - se
     * [comprovante] chegou ate aqui, o pagamento no banco ja aconteceu e e
     * irreversivel. Se a baixa falhar depois (conta mal configurada, erro do
     * Sankhya etc.), o DDA nao pode ficar "pago no banco e sem rastro nenhum"
     * no Sankhya - `PROCESSADO` so vira 'S' quando a baixa realmente terminar
     * ([BancoDao.marcarProcessado]); ate la o DDA continua elegivel pra
     * `reconsultarComprovante` tentar a baixa de novo.
     * @return true se baixou (== `comprovante` efetivado).
     */
    private fun finalizarComprovante(
        nufin: BigDecimal,
        codUsu: BigDecimal,
        ctx: ContextoPagamento,
        comprovante: ComprovantePagamento,
        boleto: () -> BoletoParaPagar,
    ): Boolean {
        val situacao = comprovante.situacao?.trim()
        val efetivado = situacao.equals("Efetivado", ignoreCase = true)
        val falhaTerminal = !efetivado && SITUACOES_TERMINAIS_FALHA.any { it.equals(situacao, ignoreCase = true) }

        dao.marcarPago(
            pk = ctx.pk,
            idPagamento = comprovante.idPagamento.toString(),
            autenticacao = comprovante.autenticacao,
            situacao = comprovante.situacao,
            valorPago = comprovante.valorPagamento,
            dataPagamento = comprovante.dataPagamento?.let { Timestamp.valueOf(it.atStartOfDay()) },
            processado = falhaTerminal,
        )

        if (efetivado) {
            val boletoResolvido = boleto()
            val codConta = (ctx.param.codCta ?: ctx.finVO.asBigDecimal("CODCTABCOINT")?.toInt())
                ?: throw IntegracaoBancariaException(
                    "Pagamento ${comprovante.idPagamento} ja efetivado no banco, mas sem conta bancaria pra " +
                        "baixa: preencha CODCTA nos parametros ou a conta no titulo e use 'Consultar Comprovante' " +
                        "para tentar a baixa de novo - o pagamento nao sera refeito.",
                )
            baixa.baixar(
                BaixaSankhya.Parametros(
                    nufin = nufin,
                    codUsu = codUsu,
                    codEmp = BigDecimal.valueOf(ctx.codEmp.toLong()),
                    codConta = BigDecimal.valueOf(codConta.toLong()),
                    codTipoOperacao = ctx.param.codTipoOper?.let { BigDecimal.valueOf(it.toLong()) },
                    dataBaixa = comprovante.dataPagamento?.let { Timestamp.valueOf(it.atStartOfDay()) }
                        ?: Timestamp(System.currentTimeMillis()),
                    valorPago = comprovante.valorPagamento
                        ?: boletoResolvido.valorPagamento
                        ?: boletoResolvido.valorBoleto
                        ?: BigDecimal.ZERO,
                ),
            )
            dao.marcarProcessado(ctx.pk)

            if (ctx.dda.chaveAnexo.isNullOrBlank()) {
                try {
                    val chave = anexo.anexarComprovante(nufin, codUsu, comprovante, boletoResolvido)
                    dao.marcarAnexoComprovante(ctx.pk, chave)
                } catch (e: Exception) {
                    ctx.logBco.registrarAsync(
                        LogHelper.Status.WARNING,
                        "Pagamento efetuado mas falhou ao anexar o comprovante (NUFIN $nufin)",
                        e,
                        ORIGEM,
                    )
                }
            } else {
                ctx.logBco.registrarAsync(
                    LogHelper.Status.INFO,
                    "Comprovante do NUFIN $nufin ja anexado (chave ${ctx.dda.chaveAnexo}) - " +
                        "nao gerado de novo, consulte nos anexos do titulo.",
                    origem = ORIGEM,
                )
            }
        } else if (falhaTerminal) {
            ctx.logBco.registrarAsync(
                LogHelper.Status.WARNING,
                "Pagamento do NUFIN $nufin foi $situacao - DDA encerrado sem baixa (nao sera mais reconsultado).",
                origem = ORIGEM,
            )
        } else {
            ctx.logBco.registrarAsync(
                LogHelper.Status.WARNING,
                "Pagamento do NUFIN $nufin retornou situacao '$situacao' - baixa nao executada, ainda reconsultavel.",
                origem = ORIGEM,
            )
        }

        return efetivado
    }

    private companion object {
        const val ORIGEM = "PagarDdaService"
        val SITUACOES_TERMINAIS_FALHA = listOf("Rejeitado", "Cancelado")
    }
}
