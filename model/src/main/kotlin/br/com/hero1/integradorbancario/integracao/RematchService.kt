package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.entity.BcoRespBanco
import br.com.hero1.integradorbancario.entity.BcoRespBancoId
import br.com.hero1.integradorbancario.entity.TipoRespostaEnum
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.core.JapeSession.SessionHandle
import br.com.sankhya.jape.vo.DynamicVO
import java.math.BigDecimal
import java.time.LocalDate

/** Filtro da tela de rematch: vencimento e empresa obrigatorios, parceiro opcional. */
data class RematchFiltro(
    val codEmp: Int,
    val vencIni: LocalDate,
    val vencFim: LocalDate,
    val codParc: Int? = null,
)

data class RematchPendencias(
    val ddas: List<RematchDdaItem>,
    val titulos: List<RematchTituloItem>,
)

data class RematchDdaItem(
    val idFinanceiro: String,
    val idBanco: Int,
    val codEmp: Int,
    val tipoResp: String,
    val cnpjBeneficiario: String?,
    val nomeBeneficiario: String?,
    val dataVencimento: String?,
    val valor: BigDecimal?,
    val nossoNumero: String?,
    val temCodigoBarras: Boolean,
)

data class RematchTituloItem(
    val nufin: BigDecimal,
    val codParc: Int?,
    val nomeParceiro: String?,
    val cnpjParceiro: String?,
    val numeroNota: String?,
    val dataVencimento: String?,
    val valor: BigDecimal?,
    val historico: String?,
)

/** Comando de confirmacao: PK do DDA + NUFIN do titulo + flag de confirmacao das divergencias. */
data class RematchComando(
    val idFinanceiro: String,
    val idBanco: Int,
    val codEmp: Int,
    val tipoResp: String,
    val nufin: BigDecimal,
    val confirmado: Boolean,
)

data class RematchResultado(
    val divergencias: List<String>,
    val aplicado: Boolean,
)

/**
 * Rematch manual DDA <-> financeiro (TGFFIN). Alimenta a tela de rematch: lista
 * os DDAs sem vinculo e os titulos a pagar sem vinculo de um periodo/empresa, e
 * efetiva o match escolhido pelo operador.
 *
 * Classe plana (sem Guice). A transacao e a do chamador (o `@Controller`). A
 * validacao dura das invariantes fica em [BancoDao.aplicarMatch]; aqui monta so
 * os avisos de divergencia.
 */
class RematchService(private val dao: BancoDao) {

    fun listar(filtro: RematchFiltro): RematchPendencias {
        var hnd: SessionHandle? = null
        try {
            hnd = JapeSession.open()
            val codParcBd = filtro.codParc?.let { BigDecimal.valueOf(it.toLong()) }
            val cnpjParc = codParcBd?.let { dao.parceiro(it)?.cnpj }

            val ddas = dao.ddasSemMatch(filtro.codEmp, filtro.vencIni, filtro.vencFim, cnpjParc)
                .map(::toDdaItem)
            val titulos = dao.titulosSemMatch(filtro.codEmp, filtro.vencIni, filtro.vencFim, codParcBd)
                .map(::toTituloItem)
            return RematchPendencias(ddas, titulos)
        }catch (e:Exception){
            throw e
        }finally {
            JapeSession.close(hnd)
        }

    }

    fun confirmar(cmd: RematchComando): RematchResultado {
        val pk = BcoRespBancoId(cmd.idFinanceiro, cmd.idBanco, cmd.codEmp, cmd.tipoResp)
        val dda = dao.respostaPorPk(pk)
            ?: throw IntegracaoBancariaException("DDA ${cmd.idFinanceiro} nao encontrado.")
        val finVO = dao.financeiroVO(cmd.nufin)
            ?: throw IntegracaoBancariaException("Financeiro ${cmd.nufin.toPlainString()} nao encontrado.")

        val divergencias = divergencias(dda, finVO)
        val codBarras = codigoBarrasDe(dda.codigoBarras, pk.idFinanceiro)
        if (codBarras == null) {
            divergencias.add(
                "DDA sem codigo de barras - o titulo sera vinculado, mas CODIGOBARRA/LINHADIGITAVEL nao serao preenchidos.",
            )
        }

        if (divergencias.isNotEmpty() && !cmd.confirmado) {
            return RematchResultado(divergencias, aplicado = false)
        }

        dao.aplicarMatch(pk, cmd.nufin, codBarras, codBarras?.let(LinhaDigitavel::deCodigoBarras))

        LogHelper(cmd.codEmp).registrar(
            LogHelper.Status.INFO,
            "Match manual: DDA ${cmd.idFinanceiro} vinculado ao financeiro ${cmd.nufin.toPlainString()}" +
                if (divergencias.isEmpty()) "." else " (divergencias confirmadas: ${divergencias.joinToString(" | ")}).",
            origem = ORIGEM,
        )
        return RematchResultado(emptyList(), aplicado = true)
    }

    // --- helpers ---------------------------------------------------------------

    private fun divergencias(dda: BcoRespBanco, finVO: DynamicVO): MutableList<String> {
        val avisos = mutableListOf<String>()

        val cnpjDda = dda.cnpjBeneficiario?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }
        val cnpjTitulo = finVO.asBigDecimal("CODPARC")?.let { dao.parceiro(it)?.cnpj }
        if (cnpjDda != null && cnpjTitulo != null && cnpjDda != cnpjTitulo) {
            avisos.add("Beneficiario do DDA (CNPJ $cnpjDda) diferente do parceiro do titulo (CNPJ $cnpjTitulo).")
        }

        val valorDda = dda.valor
        val valorTitulo = finVO.asBigDecimal("VLRDESDOB")
        if (valorDda != null && valorTitulo != null && valorDda.compareTo(valorTitulo) != 0) {
            avisos.add("Valor do DDA (${valorDda.toPlainString()}) diferente do titulo (${valorTitulo.toPlainString()}).")
        }

        val vencDda = dda.dataVencimento?.toLocalDateTime()?.toLocalDate()
        val vencTitulo = finVO.asTimestamp("DTVENC")?.toLocalDateTime()?.toLocalDate()
        if (vencDda != null && vencTitulo != null && vencDda != vencTitulo) {
            avisos.add("Vencimento do DDA ($vencDda) diferente do titulo ($vencTitulo).")
        }
        return avisos
    }

    /** Codigo de barras do DDA: campo dedicado, ou o IDFINANCEIRO quando ele "parece" um codigo de barras (44 digitos). */
    private fun codigoBarrasDe(codBarrasCampo: String?, idFinanceiro: String?): String? {
        codBarrasCampo?.filter(Char::isDigit)?.takeIf { it.length == 44 }?.let { return it }
        return idFinanceiro?.filter(Char::isDigit)?.takeIf { it.length == 44 }
    }

    private fun toDdaItem(vo: DynamicVO): RematchDdaItem {
        val cnpj = vo.asString("CNPJBENEF")?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }
        val nome = cnpj?.let { dao.parceiroPorCnpj(it) }?.let { dao.parceiro(it)?.nome }
        return RematchDdaItem(
            idFinanceiro = vo.asString("IDFINANCEIRO").orEmpty(),
            idBanco = vo.asBigDecimal("IDBANCO")?.toInt() ?: 0,
            codEmp = vo.asBigDecimal("CODEMP")?.toInt() ?: 0,
            tipoResp = vo.asString("TIPORESP") ?: TipoRespostaEnum.DDA.value,
            cnpjBeneficiario = cnpj,
            nomeBeneficiario = nome,
            dataVencimento = vo.asTimestamp("DTVENCIMENTO")?.toLocalDateTime()?.toLocalDate()?.toString(),
            valor = vo.asBigDecimal("VALOR"),
            nossoNumero = vo.asString("NOSSONUMERO"),
            temCodigoBarras = codigoBarrasDe(vo.asString("CODBARRAS"), vo.asString("IDFINANCEIRO")) != null,
        )
    }

    private fun toTituloItem(vo: DynamicVO): RematchTituloItem {
        val codParc = vo.asBigDecimal("CODPARC")
        val parc = codParc?.let { dao.parceiro(it) }
        return RematchTituloItem(
            nufin = vo.asBigDecimal("NUFIN") ?: BigDecimal.ZERO,
            codParc = codParc?.toInt(),
            nomeParceiro = parc?.nome,
            cnpjParceiro = parc?.cnpj,
            numeroNota = vo.asBigDecimal("NUMNOTA")?.toPlainString(),
            dataVencimento = vo.asTimestamp("DTVENC")?.toLocalDateTime()?.toLocalDate()?.toString(),
            valor = vo.asBigDecimal("VLRDESDOB"),
            historico = vo.asString("HISTORICO"),
        )
    }

    private companion object {
        const val ORIGEM = "RematchService"
    }
}
