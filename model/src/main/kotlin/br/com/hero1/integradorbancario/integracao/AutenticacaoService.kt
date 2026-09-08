package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.entity.BcoCadBanco
import br.com.hero1.integradorbancario.integracao.dominio.ComandoAutenticacao
import br.com.hero1.integradorbancario.integracao.dominio.ResultadoAutenticacao
import java.util.logging.Logger

/**
 * Dispara a autenticacao de uma empresa num banco (botao "Autenticar" da tela
 * de parametrizacao). Resolve o conector por (idBanco, empresa) e delega -
 * o conector obtem o token e ja o persiste em BCO_PARAMBANCO.
 *
 * Classe plana (sem Guice). Roda dentro da transacao do @ActionButton.
 */
class AutenticacaoService(
    private val dao: BancoDao,
    private val registry: ConectorBancarioRegistry,
) {

    private val log: Logger = Logger.getLogger(AutenticacaoService::class.java.name)

    fun autenticar(idBanco: Int, codEmp: Int): ResultadoAutenticacao {
        val credencial = dao.credencial(idBanco, codEmp)
            ?: throw IntegracaoBancariaException(
                "Empresa $codEmp nao tem parametros para o banco $idBanco em BCO_PARAMBANCO",
            )
        val banco: BcoCadBanco = dao.bancoPorId(idBanco)
            ?: throw IntegracaoBancariaException("Banco $idBanco nao esta em BCO_CADBANCO")
        val codigoCompensacao = banco.codigoDoBanco
            ?: throw IntegracaoBancariaException("Banco $idBanco sem codigo de compensacao")

        val resultado = registry.para(codigoCompensacao)
            .autenticar(ComandoAutenticacao(credencial, banco.sandbox == true))

        log.info("Autenticacao OK: banco=$idBanco empresa=$codEmp expira_em=${resultado.expiraEmSegundos}s")
        return resultado
    }
}
