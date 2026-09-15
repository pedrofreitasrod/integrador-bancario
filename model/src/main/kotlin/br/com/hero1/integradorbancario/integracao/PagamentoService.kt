package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.entity.BcoCadBanco
import br.com.hero1.integradorbancario.entity.BcoParamBanco
import br.com.hero1.integradorbancario.integracao.dominio.BoletoParaPagar
import br.com.hero1.integradorbancario.integracao.dominio.ComandoCancelamento
import br.com.hero1.integradorbancario.integracao.dominio.ComandoPagamento
import br.com.hero1.integradorbancario.integracao.dominio.ComprovantePagamento
import br.com.hero1.integradorbancario.integracao.dominio.ConsultaBoleto
import br.com.hero1.integradorbancario.integracao.dominio.ConsultaComprovante
import java.math.BigDecimal
import java.time.LocalDate
import java.util.logging.Logger

/**
 * Orquestra consulta e pagamento de boletos: resolve a credencial e o conector
 * do banco a partir de (idBanco, empresa) e delega ao [ConectorBancario].
 *
 * Classe plana (sem Guice). A transacao e a do chamador (o `@Controller` ou o
 * botao/job que vier a usar). Nao grava nada por si - a persistencia do
 * comprovante fica com o chamador.
 */
class PagamentoService(
    private val dao: BancoDao,
    private val registry: ConectorBancarioRegistry,
) {

    private val log: Logger = Logger.getLogger(PagamentoService::class.java.name)

    /** Consulta um boleto (valor atualizado, bloqueios) antes de pagar. */
    fun consultarBoleto(
        idBanco: Int,
        codEmp: Int,
        codigoBarras: String,
        dataPagamento: LocalDate? = null,
    ): BoletoParaPagar {
        val alvo = resolver(idBanco, codEmp)
        return alvo.conector.consultarBoleto(
            ConsultaBoleto(
                credencial = alvo.credencial,
                sandbox = alvo.sandbox,
                codigoBarras = codigoBarras.trim(),
                dataPagamento = dataPagamento,
            ),
        )
    }

    /**
     * Efetua/agenda o pagamento de um boleto ja consultado.
     *
     * `valorPagamento` nulo assume o valor calculado pelo banco na consulta
     * ([BoletoParaPagar.valorPagamento]) ou, na falta, o valor nominal.
     * Recusa boleto com bloqueio de pagamento.
     */
    fun pagarBoleto(
        idBanco: Int,
        codEmp: Int,
        boleto: BoletoParaPagar,
        cpfCnpjPortador: String,
        nomePortador: String,
        dataPagamento: LocalDate? = null,
        valorPagamento: BigDecimal? = null,
        valorDescontoAbatimento: BigDecimal = BigDecimal.ZERO,
        valorMultaMora: BigDecimal = BigDecimal.ZERO,
        aceitaValorDivergente: Boolean = false,
        observacao: String? = null,
    ): ComprovantePagamento {
        if (boleto.pagamentoBloqueado) {
            throw PagamentoBancarioException(
                "Boleto com bloqueio de pagamento: ${boleto.mensagemBloqueio ?: "sem detalhe"}",
            )
        }
        val valorBoleto = boleto.valorBoleto
            ?: throw PagamentoBancarioException("Boleto sem valor nominal - consulte o boleto antes de pagar")
        val valorFinal = valorPagamento
            ?: boleto.valorPagamento
            ?: valorBoleto

        val alvo = resolver(idBanco, codEmp)
        val comprovante = alvo.conector.pagarBoleto(
            ComandoPagamento(
                credencial = alvo.credencial,
                sandbox = alvo.sandbox,
                codigoBarras = boleto.codigoBarras,
                identificadorConsulta = boleto.identificadorConsulta,
                valorBoleto = valorBoleto,
                valorPagamento = valorFinal,
                valorDescontoAbatimento = valorDescontoAbatimento,
                valorMultaMora = valorMultaMora,
                dataPagamento = dataPagamento,
                aceitaValorDivergente = aceitaValorDivergente,
                cpfCnpjPortador = cpfCnpjPortador,
                nomePortador = nomePortador,
                observacao = observacao,
            ),
        )
        log.info(
            "Pagamento banco=$idBanco empresa=$codEmp boleto=${boleto.codigoBarras}: " +
                "id=${comprovante.idPagamento} situacao=${comprovante.situacao}",
        )
        return comprovante
    }

    /** Reconsulta o comprovante de um pagamento ja efetuado/agendado. */
    fun consultarComprovante(idBanco: Int, codEmp: Int, idPagamento: Long): ComprovantePagamento {
        val alvo = resolver(idBanco, codEmp)
        return alvo.conector.consultarComprovante(
            ConsultaComprovante(alvo.credencial, alvo.sandbox, idPagamento),
        )
    }

    /** Cancela um pagamento agendado (ainda nao efetivado). */
    fun cancelarAgendamento(idBanco: Int, codEmp: Int, idPagamento: Long) {
        val alvo = resolver(idBanco, codEmp)
        alvo.conector.cancelarAgendamento(
            ComandoCancelamento(alvo.credencial, alvo.sandbox, idPagamento),
        )
    }

    private data class Alvo(
        val conector: ConectorBancario,
        val credencial: BcoParamBanco,
        val sandbox: Boolean,
    )

    private fun resolver(idBanco: Int, codEmp: Int): Alvo {
        val credencial = dao.credencial(idBanco, codEmp)
            ?: throw IntegracaoBancariaException(
                "Empresa $codEmp nao tem credencial para o banco $idBanco em BCO_PARAMBANCO",
            )
        if (!credencial.estaAtiva()) {
            throw IntegracaoBancariaException("Credencial do banco $idBanco / empresa $codEmp esta inativa")
        }
        val banco: BcoCadBanco = dao.bancoPorId(idBanco)
            ?: throw IntegracaoBancariaException("Banco $idBanco nao esta em BCO_CADBANCO")
        val codigoCompensacao = banco.codigoDoBanco
            ?: throw IntegracaoBancariaException("Banco $idBanco sem codigo de compensacao")

        return Alvo(registry.para(codigoCompensacao), credencial, banco.sandbox == true)
    }
}
