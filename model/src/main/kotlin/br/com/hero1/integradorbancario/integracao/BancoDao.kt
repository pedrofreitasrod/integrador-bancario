package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.entity.BcoCadBanco
import br.com.hero1.integradorbancario.entity.BcoParamBanco
import br.com.hero1.integradorbancario.entity.BcoParamBancoId
import br.com.hero1.integradorbancario.entity.BcoRespBanco
import br.com.hero1.integradorbancario.entity.BcoRespBancoId
import br.com.hero1.integradorbancario.entity.TipoRespostaEnum
import br.com.sankhya.jape.sql.NativeSql
import br.com.sankhya.jape.vo.DynamicVO
import br.com.sankhya.jape.wrapper.JapeFactory
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate
import java.util.logging.Logger

/**
 * Acesso a dados da integracao bancaria via JAPE (`JapeFactory.dao`).
 *
 * Substitui as interfaces `@Repository` - este projeto roda sem Guice
 * (`isSdkEnabled=false`), entao classes instanciadas com `new` (job/controller)
 * nao conseguem injetar um repository. As classes `@JapeEntity` continuam
 * existindo so para o dicionario/DWF/autoDDL; aqui trabalhamos com `DynamicVO`.
 *
 * Roda dentro da transacao do chamador (o `onSchedule` do job / o `@Controller`).
 */
class BancoDao {

    private val log: Logger = Logger.getLogger(BancoDao::class.java.name)

    fun bancoPorId(idBanco: Int): BcoCadBanco? {
        val vo = dao(ENT_BANCO).findOne("this.ID = ?", BigDecimal.valueOf(idBanco.toLong())) ?: return null
        return BcoCadBanco().apply {
            id = vo.asBigDecimalOrZero("ID").toInt()
            nomeBanco = vo.asString("NOMEBANCO")
            codigoDoBanco = vo.asBigDecimalOrZero("CODIGODOBANCO").toInt()
            sandbox = vo.asBoolean("SANDBOX")
        }
    }

    fun credenciaisAtivas(): List<BcoParamBanco> =
        dao(ENT_CRED).find("this.ATIVO = ?", "S").map(::toCredencial)

    fun credenciaisAtivasPorEmpresa(codEmp: Int): List<BcoParamBanco> =
        dao(ENT_CRED)
            .find("this.ATIVO = ? and this.CODEMP = ?", "S", BigDecimal.valueOf(codEmp.toLong()))
            .map(::toCredencial)

    fun credencial(idBanco: Int, codEmp: Int): BcoParamBanco? {
        val vo = dao(ENT_CRED).findOne(
            "this.IDBANCO = ? and this.CODEMP = ?",
            BigDecimal.valueOf(idBanco.toLong()),
            BigDecimal.valueOf(codEmp.toLong()),
        ) ?: return null
        return toCredencial(vo)
    }

    /** Persiste o access token obtido na autenticacao nos parametros da empresa. */
    fun salvarToken(id: BcoParamBancoId, acessToken: String, expiraEmSegundos: Int, autenticadoEm: Timestamp) {
        val vo = dao(ENT_CRED).findOne(
            "this.IDBANCO = ? and this.CODEMP = ?",
            BigDecimal.valueOf((id.idBanco ?: 0).toLong()),
            BigDecimal.valueOf((id.codEmp ?: 0).toLong()),
        ) ?: throw IntegracaoBancariaException(
            "Parametros do banco ${id.idBanco} / empresa ${id.codEmp} nao encontrados em BCO_PARAMBANCO",
        )
        dao(ENT_CRED).prepareToUpdate(vo)
            .set("ACESSTOKEN", acessToken.toCharArray())
            .set("EXPIRES", BigDecimal.valueOf(expiraEmSegundos.toLong()))
            .set("DHAUTENTIQUE", autenticadoEm)
            .update()
    }

    fun respostaExiste(pk: BcoRespBancoId): Boolean =
        dao(ENT_RESP).findOne(
            "this.IDFINANCEIRO = ? and this.IDBANCO = ? and this.CODEMP = ? and this.TIPORESP = ?",
            pk.idFinanceiro,
            BigDecimal.valueOf((pk.idBanco ?: 0).toLong()),
            BigDecimal.valueOf((pk.codEmp ?: 0).toLong()),
            pk.tipoResposta,
        ) != null

    fun inserirResposta(r: BcoRespBanco) {
        val pk = r.id ?: error("BcoRespBanco sem PK")
        dao(ENT_RESP).create()
            .set("IDFINANCEIRO", pk.idFinanceiro)
            .set("IDBANCO", BigDecimal.valueOf((pk.idBanco ?: 0).toLong()))
            .set("CODEMP", BigDecimal.valueOf((pk.codEmp ?: 0).toLong()))
            .set("TIPORESP", pk.tipoResposta)
            .set("CNPJBENEF", r.cnpjBeneficiario)
            .set("DTVENCIMENTO", r.dataVencimento)
            .set("VALOR", r.valor)
            .set("DTNEGOCIACAO", r.dataNegociacao)
            .set("NOSSONUMERO", r.nossoNumero)
            .set("DTINSERCAO", r.dataInsercao)
            .set("NUFIN", r.nufin)
            .set("PROCESSADO", if (r.processado == true) "S" else "N")
            .save()
    }

    /** DDA gravado, localizado pelo NUFIN vinculado (para o botao de pagamento). */
    fun ddaPorNufin(nufin: BigDecimal): BcoRespBanco? {
        val vo = dao(ENT_RESP).findOne(
            "this.NUFIN = ? and this.TIPORESP = ?",
            nufin,
            TipoRespostaEnum.DDA.value,
        ) ?: return null
        return toResposta(vo)
    }

    /**
     * Grava o resultado do pagamento no DDA. `processado` = true apenas quando
     * o ciclo completou (pagamento efetivado + baixa); se o pagamento ficou
     * agendado/pendente, grava os dados mas nao marca processado.
     */
    fun marcarPago(
        pk: BcoRespBancoId,
        idPagamento: String?,
        autenticacao: String?,
        situacao: String?,
        valorPago: BigDecimal?,
        dataPagamento: Timestamp?,
        processado: Boolean,
    ) {
        val vo = dao(ENT_RESP).findOne(
            "this.IDFINANCEIRO = ? and this.IDBANCO = ? and this.CODEMP = ? and this.TIPORESP = ?",
            pk.idFinanceiro,
            BigDecimal.valueOf((pk.idBanco ?: 0).toLong()),
            BigDecimal.valueOf((pk.codEmp ?: 0).toLong()),
            pk.tipoResposta,
        ) ?: throw IntegracaoBancariaException("DDA ${pk.idFinanceiro} nao encontrado para marcar como pago")

        dao(ENT_RESP).prepareToUpdate(vo)
            .set("IDPAGAMENTO", idPagamento)
            .set("AUTENTICACAO", autenticacao)
            .set("SITUACAOPGTO", situacao)
            .set("VLRPAGO", valorPago)
            .set("DTPAGAMENTO", dataPagamento)
            .set("PROCESSADO", if (processado) "S" else "N")
            .set("DTPROCESSAMENTO", if (processado) Timestamp(System.currentTimeMillis()) else null)
            .update()
    }

    /**
     * Procura um titulo a pagar em aberto que corresponda ao DDA (matching
     * automatico na busca). Criterio: empresa + parceiro (por CNPJ) + valor +
     * vencimento, nao baixado, nao provisao. @return NUFIN ou null.
     */
    fun acharNufinAberto(
        codEmp: Int,
        cnpjBeneficiario: String?,
        valor: BigDecimal?,
        vencimento: LocalDate?,
    ): BigDecimal? {
        val cnpj = cnpjBeneficiario?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() } ?: return null
        if (valor == null || vencimento == null) return null

        val codParc = NativeSql.getBigDecimal(
            "CODPARC",
            "TGFPAR",
            "REPLACE(REPLACE(REPLACE(CGC_CPF, '.', ''), '/', ''), '-', '') = ?",
            cnpj,
        ) ?: return null

        val de = Timestamp.valueOf(vencimento.atStartOfDay())
        val ate = Timestamp.valueOf(vencimento.plusDays(1).atStartOfDay())
        val candidatos = dao(ENT_FIN).find(
            "this.CODEMP = ? and this.CODPARC = ? and this.PROVISAO = 'N' and this.DHBAIXA is null " +
                "and this.VLRDESDOB = ? and this.DTVENC >= ? and this.DTVENC < ?",
            BigDecimal.valueOf(codEmp.toLong()),
            codParc,
            valor,
            de,
            ate,
        )
        val lista = candidatos.toList()
        if (lista.isEmpty()) return null
        if (lista.size > 1) {
            log.warning(
                "Matching de DDA ambiguo (empresa=$codEmp parceiro=$codParc valor=$valor venc=$vencimento): " +
                    "${lista.size} titulos - usando o primeiro.",
            )
        }
        return lista.first().asBigDecimalOrZero("NUFIN")
    }

    /** VO do titulo financeiro (TGFFIN) pelo NUFIN - para valores da baixa. */
    fun financeiroVO(nufin: BigDecimal): DynamicVO? =
        dao(ENT_FIN).findOne("this.NUFIN = ?", nufin)

    data class DadosEmpresa(val cnpj: String, val nome: String)

    /** CNPJ e razao social da empresa (para o portador do pagamento). */
    fun empresa(codEmp: Int): DadosEmpresa? {
        val vo = dao(ENT_EMP).findOne("this.CODEMP = ?", BigDecimal.valueOf(codEmp.toLong())) ?: return null
        return DadosEmpresa(
            cnpj = vo.asString("CGC").orEmpty(),
            nome = (vo.asString("RAZAOSOCIAL") ?: vo.asString("NOMEFANTASIA")).orEmpty(),
        )
    }

    private fun toCredencial(vo: DynamicVO): BcoParamBanco = BcoParamBanco().apply {
        id = BcoParamBancoId(
            vo.asBigDecimalOrZero("IDBANCO").toInt(),
            vo.asBigDecimalOrZero("CODEMP").toInt(),
        )
        clientId = vo.asString("CLIENTID")
        cooperativa = vo.asString("COOPERATIVA")
        numConta = vo.asString("NUMCONTA")
        numContrato = vo.asString("NUMCONTRATO")
        certArquivo = vo.asBlob("CERTARQUIVO")
        certSenha = vo.asString("CERTSENHA")
        scopes = vo.asString("SCOPES")
        ativo = vo.asBoolean("ATIVO")
        codCta = vo.asBigDecimal("CODCTA")?.toInt()
        codTipoOper = vo.asBigDecimal("CODTIPOPER")?.toInt()
        acessToken = vo.asString("ACESSTOKEN")
        expiresIn = vo.asBigDecimal("EXPIRES")?.toInt()
        dhAutentique = vo.asTimestamp("DHAUTENTIQUE")
    }

    private fun toResposta(vo: DynamicVO): BcoRespBanco = BcoRespBanco().apply {
        id = BcoRespBancoId(
            vo.asString("IDFINANCEIRO"),
            vo.asBigDecimalOrZero("IDBANCO").toInt(),
            vo.asBigDecimalOrZero("CODEMP").toInt(),
            vo.asString("TIPORESP"),
        )
        cnpjBeneficiario = vo.asString("CNPJBENEF")
        dataVencimento = vo.asTimestamp("DTVENCIMENTO")
        valor = vo.asBigDecimal("VALOR")
        nossoNumero = vo.asString("NOSSONUMERO")
        nufin = vo.asBigDecimal("NUFIN")
        processado = vo.asBoolean("PROCESSADO")
        idPagamento = vo.asString("IDPAGAMENTO")
    }

    private fun dao(entidade: String) = JapeFactory.dao(entidade)

    private companion object {
        const val ENT_BANCO = "BcoCadBanco"
        const val ENT_CRED = "BcoParamBanco"
        const val ENT_RESP = "BcoRespBanco"
        const val ENT_FIN = "Financeiro"
        const val ENT_EMP = "Empresa"
    }
}
