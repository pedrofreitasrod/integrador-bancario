package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.integracao.dominio.BoletoParaPagar
import br.com.hero1.integradorbancario.integracao.dominio.ComandoAutenticacao
import br.com.hero1.integradorbancario.integracao.dominio.ComandoCancelamento
import br.com.hero1.integradorbancario.integracao.dominio.ComandoPagamento
import br.com.hero1.integradorbancario.integracao.dominio.ComprovantePagamento
import br.com.hero1.integradorbancario.integracao.dominio.ConsultaBoleto
import br.com.hero1.integradorbancario.integracao.dominio.ConsultaComprovante
import br.com.hero1.integradorbancario.integracao.dominio.Dda
import br.com.hero1.integradorbancario.integracao.dominio.FiltroDda
import br.com.hero1.integradorbancario.integracao.dominio.ResultadoAutenticacao

/**
 * Contrato de integracao de UM banco. Cada banco tem uma implementacao;
 * a rotina de negocio depende so desta interface.
 *
 * Nas consultas/pagamentos a autenticacao e resolvida internamente pelo
 * conector (token reaproveitado dos parametros da empresa, renovado quando
 * expira). [autenticar] existe so para forcar a renovacao pela tela.
 *
 * Registro: cada implementacao entra na lista passada ao
 * [ConectorBancarioRegistry], montada em [IntegracaoBancaria].
 */
interface ConectorBancario {

    /** Codigo de compensacao do banco que este conector atende (ex.: 756 = Sicoob). */
    val codigoCompensacao: Int

    /**
     * Forca uma nova autenticacao no banco e persiste o token nos parametros
     * da empresa (BCO_PARAMBANCO). Usado pelo botao "Autenticar" da tela de
     * parametrizacao - as demais operacoes nao precisam chamar isto.
     */
    fun autenticar(comando: ComandoAutenticacao): ResultadoAutenticacao

    /** Consulta os DDAs do periodo/empresa e devolve ja normalizados. */
    fun buscarDdas(filtro: FiltroDda): List<Dda>

    /** Consulta um boleto (dados, valor atualizado, bloqueios) antes de pagar. */
    fun consultarBoleto(consulta: ConsultaBoleto): BoletoParaPagar

    /** Efetua ou agenda o pagamento de um boleto e devolve o comprovante. */
    fun pagarBoleto(comando: ComandoPagamento): ComprovantePagamento

    /** Reconsulta o comprovante de um pagamento ja efetuado/agendado. */
    fun consultarComprovante(consulta: ConsultaComprovante): ComprovantePagamento

    /**
     * Cancela um pagamento agendado (ainda nao efetivado). Lanca
     * [PagamentoBancarioException] se o banco recusar (ex.: pagamento ja
     * efetivado, nao pode mais ser cancelado).
     */
    fun cancelarAgendamento(comando: ComandoCancelamento)
}
