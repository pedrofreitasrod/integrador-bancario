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
            .set("CODBARRAS", r.codigoBarras)
            .set("NUMERODOC", r.numeroDoc?.let { BigDecimal.valueOf(it.toLong()) })
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

    /** DDA localizado pela PK completa (para o rematch). */
    fun respostaPorPk(pk: BcoRespBancoId): BcoRespBanco? {
        val vo = dao(ENT_RESP).findOne(
            "this.IDFINANCEIRO = ? and this.IDBANCO = ? and this.CODEMP = ? and this.TIPORESP = ?",
            pk.idFinanceiro,
            BigDecimal.valueOf((pk.idBanco ?: 0).toLong()),
            BigDecimal.valueOf((pk.codEmp ?: 0).toLong()),
            pk.tipoResposta,
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
        val vo = respostaPorPkOuFalha(pk, "marcar como pago")
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

    /** Grava a chave do anexo (TSIANX) do comprovante ja gerado - evita reanexar a cada reconsulta. */
    fun marcarAnexoComprovante(pk: BcoRespBancoId, chaveAnexo: String) {
        val vo = respostaPorPkOuFalha(pk, "gravar o anexo do comprovante")
        dao(ENT_RESP).prepareToUpdate(vo).set("CHAVEANEXO", chaveAnexo).update()
    }

    /**
     * Fecha o ciclo do DDA (pagamento efetivado + baixa concluida) depois que
     * [marcarPago] ja gravou os dados do pagamento. Separado de `marcarPago`
     * de proposito: o IDPAGAMENTO precisa ficar salvo mesmo que a baixa falhe
     * depois - sem isso, um pagamento que o banco ja efetivou (irreversivel)
     * ficaria sem nenhum rastro no Sankhya se a baixa der erro.
     */
    fun marcarProcessado(pk: BcoRespBancoId) {
        val vo = respostaPorPkOuFalha(pk, "marcar como processado")
        dao(ENT_RESP).prepareToUpdate(vo)
            .set("PROCESSADO", "S")
            .set("DTPROCESSAMENTO", Timestamp(System.currentTimeMillis()))
            .update()
    }

    private fun respostaPorPkOuFalha(pk: BcoRespBancoId, acao: String): DynamicVO =
        dao(ENT_RESP).findOne(
            "this.IDFINANCEIRO = ? and this.IDBANCO = ? and this.CODEMP = ? and this.TIPORESP = ?",
            pk.idFinanceiro,
            BigDecimal.valueOf((pk.idBanco ?: 0).toLong()),
            BigDecimal.valueOf((pk.codEmp ?: 0).toLong()),
            pk.tipoResposta,
        ) ?: throw IntegracaoBancariaException("DDA ${pk.idFinanceiro} nao encontrado para $acao")

    /**
     * Procura um titulo a pagar em aberto que corresponda ao DDA (matching
     * automatico na busca). Criterio: empresa + parceiro (por CNPJ) + valor +
     * vencimento + NUMERODOC do DDA batendo com NUMNOTA do titulo, nao
     * baixado, nao provisao, ainda nao integrado por outro DDA (BCO_INTEGRADO).
     * O criterio de NUMNOTA e obrigatorio (AND, nao fallback): DDA sem numero
     * de documento nao casa automaticamente.
     * @return NUFIN ou null.
     */
    fun acharNufinAberto(
        codEmp: Int,
        cnpjBeneficiario: String?,
        valor: BigDecimal?,
        vencimento: LocalDate?,
        numeroDocumento: Int?,
    ): BigDecimal? {
        val cnpj = cnpjBeneficiario?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() } ?: return null
        if (valor == null || vencimento == null || numeroDocumento == null) return null

        val codParc = parceiroPorCnpj(cnpj) ?: return null

        val de = Timestamp.valueOf(vencimento.atStartOfDay())
        val ate = Timestamp.valueOf(vencimento.plusDays(1).atStartOfDay())
        val candidatos = dao(ENT_FIN).find(
            "this.CODEMP = ? and this.CODPARC = ? and this.PROVISAO = 'N' and this.DHBAIXA is null " +
                "and this.VLRDESDOB = ? and this.DTVENC >= ? and this.DTVENC < ? and this.NUMNOTA = ? " +
                "and (this.BCO_INTEGRADO is null or this.BCO_INTEGRADO <> 'S')",
            BigDecimal.valueOf(codEmp.toLong()),
            codParc,
            valor,
            de,
            ate,
            BigDecimal.valueOf(numeroDocumento.toLong()),
        )
        val lista = candidatos.toList()
        if (lista.isEmpty()) return null
        if (lista.size > 1) {
            log.warning(
                "Matching de DDA ambiguo (empresa=$codEmp parceiro=$codParc valor=$valor venc=$vencimento " +
                    "numeroDoc=$numeroDocumento): ${lista.size} titulos - usando o primeiro.",
            )
        }
        return lista.first().asBigDecimalOrZero("NUFIN")
    }

    /**
     * Preenche os dados do boleto no titulo (TGFFIN) e marca BCO_INTEGRADO ao
     * vincular um DDA cujo match foi automatico (na busca) - sem as validacoes
     * de conflito do rematch manual ([aplicarMatch]), que so fazem sentido
     * quando um usuario confirma na tela.
     */
    fun marcarTituloIntegrado(
        nufin: BigDecimal,
        codigoBarras: String?,
        linhaDigitavel: String?,
        nossoNumero: String?,
    ) {
        val finVO = dao(ENT_FIN).findOne("this.NUFIN = ?", nufin) ?: return
        preencherTituloVinculado(finVO, codigoBarras, linhaDigitavel, nossoNumero)
    }

    /** VO do titulo financeiro (TGFFIN) pelo NUFIN - para valores da baixa. */
    fun financeiroVO(nufin: BigDecimal): DynamicVO? =
        dao(ENT_FIN).findOne("this.NUFIN = ?", nufin)

    /** CODPARC do parceiro cujo CNPJ/CPF (sem pontuacao) bate com [cnpj]. */
    fun parceiroPorCnpj(cnpj: String): BigDecimal? {
        val digitos = cnpj.filter(Char::isDigit).takeIf { it.isNotEmpty() } ?: return null
        return NativeSql.getBigDecimal(
            "CODPARC",
            "TGFPAR",
            "REPLACE(REPLACE(REPLACE(CGC_CPF, '.', ''), '/', ''), '-', '') = ?",
            digitos,
        )
    }

    data class DadosParceiro(val codParc: BigDecimal, val cnpj: String?, val nome: String?)

    fun parceiro(codParc: BigDecimal): DadosParceiro? {
        val vo = dao(ENT_PARC).findOne("this.CODPARC = ?", codParc) ?: return null
        return DadosParceiro(
            codParc = codParc,
            cnpj = vo.asString("CGC_CPF")?.filter(Char::isDigit),
            nome = vo.asString("NOMEPARC") ?: vo.asString("RAZAOSOCIAL"),
        )
    }

    /** DDAs sem vinculo (NUFIN nulo, nao processados) da empresa e periodo de vencimento. */
    fun ddasSemMatch(
        codEmp: Int,
        vencIni: LocalDate,
        vencFim: LocalDate,
        cnpjBeneficiario: String? = null,
    ): List<DynamicVO> {
        val de = Timestamp.valueOf(vencIni.atStartOfDay())
        val ate = Timestamp.valueOf(vencFim.plusDays(1).atStartOfDay())
        val criterio = StringBuilder(
            "this.CODEMP = ? and this.TIPORESP = ? and this.NUFIN is null and this.PROCESSADO = 'N' " +
                "and this.DTVENCIMENTO >= ? and this.DTVENCIMENTO < ?",
        )
        val params = mutableListOf<Any>(
            BigDecimal.valueOf(codEmp.toLong()),
            TipoRespostaEnum.DDA.value,
            de,
            ate,
        )
        cnpjBeneficiario?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }?.let {
            criterio.append(" and this.CNPJBENEF = ?")
            params.add(it)
        }
        return dao(ENT_RESP).find(criterio.toString(), *params.toTypedArray()).toList()
    }

    /**
     * Titulos a pagar em aberto (despesa, nao provisao, nao baixado) da empresa e
     * periodo, sem codigo de barras e sem nenhum DDA apontando o NUFIN.
     */
    fun titulosSemMatch(
        codEmp: Int,
        vencIni: LocalDate,
        vencFim: LocalDate,
        codParc: BigDecimal? = null,
    ): List<DynamicVO> {
        val de = Timestamp.valueOf(vencIni.atStartOfDay())
        val ate = Timestamp.valueOf(vencFim.plusDays(1).atStartOfDay())
        val criterio = StringBuilder(
            "this.CODEMP = ? and this.RECDESP = ? and this.PROVISAO = 'N' and this.DHBAIXA is null " +
                "and this.CODIGOBARRA is null and this.DTVENC >= ? and this.DTVENC < ?",
        )
        val params = mutableListOf<Any>(
            BigDecimal.valueOf(codEmp.toLong()),
            BigDecimal.valueOf(-1L),
            de,
            ate,
        )
        codParc?.let {
            criterio.append(" and this.CODPARC = ?")
            params.add(it)
        }

        val vinculados = dao(ENT_RESP)
            .find("this.CODEMP = ? and this.NUFIN is not null", BigDecimal.valueOf(codEmp.toLong()))
            .mapNotNull { it.asBigDecimal("NUFIN") }
            .toHashSet()

        return dao(ENT_FIN)
            .find(criterio.toString(), *params.toTypedArray())
            .filter { it.asString("CODIGOBARRA").isNullOrBlank() }
            .filter { (it.asBigDecimal("NUFIN") ?: BigDecimal.ZERO) !in vinculados }
    }

    /**
     * Efetiva o match: grava NUFIN no DDA e codigo de barras / linha digitavel no
     * titulo. Revalida as invariantes no servidor - lanca se alguma foi violada
     * entre a listagem e a confirmacao (ex.: outro usuario ja casou o titulo).
     */
    fun aplicarMatch(
        pk: BcoRespBancoId,
        nufin: BigDecimal,
        codigoBarras: String?,
        linhaDigitavel: String?,
    ) {
        val respVO = dao(ENT_RESP).findOne(
            "this.IDFINANCEIRO = ? and this.IDBANCO = ? and this.CODEMP = ? and this.TIPORESP = ?",
            pk.idFinanceiro,
            BigDecimal.valueOf((pk.idBanco ?: 0).toLong()),
            BigDecimal.valueOf((pk.codEmp ?: 0).toLong()),
            pk.tipoResposta,
        ) ?: throw IntegracaoBancariaException("DDA ${pk.idFinanceiro} nao encontrado para o match.")

        if (respVO.asBigDecimal("NUFIN") != null) {
            throw IntegracaoBancariaException(
                "Este DDA ja foi vinculado ao financeiro ${respVO.asBigDecimal("NUFIN")?.toPlainString()}.",
            )
        }
        if (respVO.asString("PROCESSADO") == "S") {
            throw IntegracaoBancariaException("Este DDA ja foi processado.")
        }

        val finVO = dao(ENT_FIN).findOne("this.NUFIN = ?", nufin)
            ?: throw IntegracaoBancariaException("Financeiro ${nufin.toPlainString()} nao encontrado.")
        if (finVO.asTimestamp("DHBAIXA") != null) {
            throw IntegracaoBancariaException("Financeiro ${nufin.toPlainString()} ja esta baixado.")
        }
        if (!finVO.asString("CODIGOBARRA").isNullOrBlank()) {
            throw IntegracaoBancariaException(
                "Financeiro ${nufin.toPlainString()} ja tem codigo de barras preenchido.",
            )
        }
        if (dao(ENT_RESP).findOne("this.NUFIN = ? and this.TIPORESP = ?", nufin, TipoRespostaEnum.DDA.value) != null) {
            throw IntegracaoBancariaException(
                "O financeiro ${nufin.toPlainString()} ja esta vinculado a outro DDA.",
            )
        }

        dao(ENT_RESP).prepareToUpdate(respVO).set("NUFIN", nufin).update()
        preencherTituloVinculado(finVO, codigoBarras, linhaDigitavel, respVO.asString("NOSSONUMERO"))
    }

    /** Grava CODIGOBARRA/LINHADIGITAVEL/BCO_NOSSONUM (quando informados) e marca BCO_INTEGRADO no titulo. */
    private fun preencherTituloVinculado(
        finVO: DynamicVO,
        codigoBarras: String?,
        linhaDigitavel: String?,
        nossoNumero: String?,
    ) {
        val up = dao(ENT_FIN).prepareToUpdate(finVO).set("BCO_INTEGRADO", "S")
        if (!codigoBarras.isNullOrBlank()) up.set("CODIGOBARRA", codigoBarras)
        if (!linhaDigitavel.isNullOrBlank()) up.set("LINHADIGITAVEL", linhaDigitavel)
        if (!nossoNumero.isNullOrBlank()) up.set("BCO_NOSSONUM", nossoNumero)
        up.update()
    }

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
        codigoBarras = vo.asString("CODBARRAS")
        numeroDoc = vo.asBigDecimal("NUMERODOC")?.toInt()
        nufin = vo.asBigDecimal("NUFIN")
        processado = vo.asBoolean("PROCESSADO")
        idPagamento = vo.asString("IDPAGAMENTO")
        chaveAnexo = vo.asString("CHAVEANEXO")
    }

    private fun dao(entidade: String) = JapeFactory.dao(entidade)

    private companion object {
        const val ENT_BANCO = "BcoCadBanco"
        const val ENT_CRED = "BcoParamBanco"
        const val ENT_RESP = "BcoRespBanco"
        const val ENT_FIN = "Financeiro"
        const val ENT_EMP = "Empresa"
        const val ENT_PARC = "Parceiro"
    }
}
