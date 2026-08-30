package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.entity.BcoCadCredencial
import br.com.hero1.integradorbancario.entity.BcoRespBanco
import br.com.hero1.integradorbancario.entity.BcoRespBancoId
import br.com.hero1.integradorbancario.entity.TipoRespostaEnum
import br.com.hero1.integradorbancario.integracao.dominio.Dda
import br.com.hero1.integradorbancario.integracao.dominio.FiltroDda
import java.sql.Timestamp
import java.time.LocalDate
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Orquestra a busca de DDA: resolve o conector do banco a partir da credencial,
 * consulta a API e grava os DDAs novos em BCO_RESPBANCO.
 *
 * Classe plana (sem Guice). A transacao e a do chamador (o `onSchedule` do job
 * ou o `@Controller`). Cada credencial e isolada por try/catch - um erro de
 * uma empresa nao interrompe as demais; erro de rede/HTTP acontece antes de
 * qualquer gravacao.
 */
class BuscarDdaService(
    private val dao: BancoDao,
    private val registry: ConectorBancarioRegistry,
) {

    private val log: Logger = Logger.getLogger(BuscarDdaService::class.java.name)

    /** Uso: job agendado. Roda todas as credenciais ativas. */
    fun buscarTodasAtivas(dataInicio: LocalDate, dataFim: LocalDate): List<ResultadoBuscaDda> {
        val ativas = dao.credenciaisAtivas()
        if (ativas.isEmpty()) {
            log.info("Nenhuma credencial de banco ativa para buscar DDA.")
            return emptyList()
        }
        return ativas.map { credencial -> executarSeguro(credencial, dataInicio, dataFim) }
    }

    /**
     * Uso: botao da tela do Financeiro. Roda todos os bancos com credencial
     * ativa para a empresa informada.
     */
    fun buscarParaEmpresa(
        codEmp: Int,
        dataInicio: LocalDate,
        dataFim: LocalDate,
    ): List<ResultadoBuscaDda> {
        val credenciais = dao.credenciaisAtivasPorEmpresa(codEmp)
        if (credenciais.isEmpty()) {
            throw IntegracaoBancariaException(
                "Empresa $codEmp nao tem credencial de banco ativa em BCO_CADCREDENCIAL",
            )
        }
        return credenciais.map { executarSeguro(it, dataInicio, dataFim) }
    }

    private fun executarSeguro(
        credencial: BcoCadCredencial,
        dataInicio: LocalDate,
        dataFim: LocalDate,
    ): ResultadoBuscaDda =
        try {
            executar(credencial, dataInicio, dataFim)
        } catch (e: Exception) {
            log.log(Level.SEVERE, "Falha ao buscar DDA (credencial ${credencial.id}): ${e.message}", e)
            ResultadoBuscaDda(
                codEmp = credencial.codEmp() ?: 0,
                idBanco = credencial.idBanco() ?: 0,
                quantidadeConsultada = 0,
                quantidadeGravada = 0,
                erro = e.message,
            )
        }

    private fun executar(
        credencial: BcoCadCredencial,
        dataInicio: LocalDate,
        dataFim: LocalDate,
    ): ResultadoBuscaDda {
        val idBanco = credencial.idBanco()
            ?: throw IntegracaoBancariaException("Credencial sem IDBANCO")
        val codEmp = credencial.codEmp()
            ?: throw IntegracaoBancariaException("Credencial sem CODEMP")

        val banco = dao.bancoPorId(idBanco)
            ?: throw IntegracaoBancariaException("Banco $idBanco nao esta em BCO_CADBANCO")
        val codigoCompensacao = banco.codigoDoBanco
            ?: throw IntegracaoBancariaException("Banco $idBanco sem codigo de compensacao")

        val conector = registry.para(codigoCompensacao)
        val filtro = FiltroDda(
            credencial = credencial,
            sandbox = banco.sandbox == true,
            dataInicio = dataInicio,
            dataFim = dataFim,
        )

        val ddas = conector.buscarDdas(filtro)
        var gravados = 0
        for (dda in ddas) {
            if (armazenar(dda, idBanco, codEmp)) gravados++
        }

        log.info("DDA banco=$codigoCompensacao empresa=$codEmp: ${ddas.size} consultados, $gravados novos.")
        return ResultadoBuscaDda(codEmp, idBanco, ddas.size, gravados)
    }

    /** @return true se inseriu; false se ja existia (nao sobrescreve registro processado). */
    private fun armazenar(dda: Dda, idBanco: Int, codEmp: Int): Boolean {
        val pk = BcoRespBancoId(dda.idFinanceiro, idBanco, codEmp, TipoRespostaEnum.DDA.value)
        if (dao.respostaExiste(pk)) return false

        val registro = BcoRespBanco().apply {
            id = pk
            cnpjBeneficiario = dda.cnpjBeneficiario
            dataVencimento = dda.dataVencimento?.let { Timestamp.valueOf(it.atStartOfDay()) }
            valor = dda.valor
            dataNegociacao = dda.dataNegociacao?.let { Timestamp.valueOf(it.atStartOfDay()) }
            nossoNumero = dda.nossoNumero
            dataInsercao = Timestamp(System.currentTimeMillis())
            processado = false
        }
        dao.inserirResposta(registro)
        return true
    }
}
