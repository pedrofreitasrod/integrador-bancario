package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.entity.BcoCadBanco
import br.com.hero1.integradorbancario.entity.BcoCadCredencial
import br.com.hero1.integradorbancario.entity.BcoCadCredencialId
import br.com.hero1.integradorbancario.entity.BcoRespBanco
import br.com.hero1.integradorbancario.entity.BcoRespBancoId
import br.com.sankhya.jape.vo.DynamicVO
import br.com.sankhya.jape.wrapper.JapeFactory
import java.math.BigDecimal

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

    fun bancoPorId(idBanco: Int): BcoCadBanco? {
        val vo = dao(ENT_BANCO).findOne("this.ID = ?", BigDecimal.valueOf(idBanco.toLong())) ?: return null
        return BcoCadBanco().apply {
            id = vo.asBigDecimalOrZero("ID").toInt()
            nomeBanco = vo.asString("NOMEBANCO")
            codigoDoBanco = vo.asBigDecimalOrZero("CODIGODOBANCO").toInt()
            sandbox = vo.asBoolean("SANDBOX")
        }
    }

    fun credenciaisAtivas(): List<BcoCadCredencial> =
        dao(ENT_CRED).find("this.ATIVO = ?", "S").map(::toCredencial)

    fun credenciaisAtivasPorEmpresa(codEmp: Int): List<BcoCadCredencial> =
        dao(ENT_CRED)
            .find("this.ATIVO = ? and this.CODEMP = ?", "S", BigDecimal.valueOf(codEmp.toLong()))
            .map(::toCredencial)

    fun credencial(idBanco: Int, codEmp: Int): BcoCadCredencial? {
        val vo = dao(ENT_CRED).findOne(
            "this.IDBANCO = ? and this.CODEMP = ?",
            BigDecimal.valueOf(idBanco.toLong()),
            BigDecimal.valueOf(codEmp.toLong()),
        ) ?: return null
        return toCredencial(vo)
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
            .set("PROCESSADO", if (r.processado == true) "S" else "N")
            .save()
    }

    private fun toCredencial(vo: DynamicVO): BcoCadCredencial = BcoCadCredencial().apply {
        id = BcoCadCredencialId(
            vo.asBigDecimalOrZero("IDBANCO").toInt(),
            vo.asBigDecimalOrZero("CODEMP").toInt(),
        )
        clientId = vo.asString("CLIENTID")
        cooperativa = vo.asString("COOPERATIVA")
        numConta = vo.asString("NUMCONTA")
        numContrato = vo.asString("NUMCONTRATO")
        certArquivo = vo.asString("CERTARQUIVO")
        certSenha = vo.asString("CERTSENHA")
        scopes = vo.asString("SCOPES")
        ativo = vo.asBoolean("ATIVO")
    }

    private fun dao(entidade: String) = JapeFactory.dao(entidade)

    private companion object {
        const val ENT_BANCO = "BcoCadBanco"
        const val ENT_CRED = "BcoCadCredencial"
        const val ENT_RESP = "BcoRespBanco"
    }
}
