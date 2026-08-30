package br.com.hero1.integradorbancario.entity

import br.com.sankhya.studio.persistence.Column
import br.com.sankhya.studio.persistence.Id
import br.com.sankhya.studio.persistence.JapeEntity
import java.math.BigDecimal
import java.sql.Timestamp

/**
 * Respostas recebidas das APIs dos bancos (ex.: DDA do Sicoob), consumidas
 * depois pela rotina que faz o match com o financeiro (TGFFIN) do Sankhya.
 *
 * Tabela generica: campos de negocio ficam nulos ate serem preenchidos pelo
 * tipo de resposta correspondente. PK manual (sequenceType="M") porque
 * IDFINANCEIRO vem de sistema externo.
 *
 * Sem construtor primario -> Kotlin gera o no-arg exigido pelo JAPE.
 */
@JapeEntity(entity = "BcoRespBanco", table = "BCO_RESPBANCO")
class BcoRespBanco {

    @Id
    var id: BcoRespBancoId? = null

    @Column(name = "CNPJBENEF")
    var cnpjBeneficiario: String? = null

    @Column(name = "DTVENCIMENTO")
    var dataVencimento: Timestamp? = null

    @Column(name = "VALOR")
    var valor: BigDecimal? = null

    @Column(name = "DTNEGOCIACAO")
    var dataNegociacao: Timestamp? = null

    @Column(name = "NOSSONUMERO")
    var nossoNumero: String? = null

    @Column(name = "DTINSERCAO")
    var dataInsercao: Timestamp? = null

    @Column(name = "DTPROCESSAMENTO")
    var dataProcessamento: Timestamp? = null

    @Column(name = "PROCESSADO")
    var processado: Boolean? = null

    @Column(name = "NUFIN")
    var nufin: BigDecimal? = null

    /** Tipo de resposta (discriminador), que mora na PK ([BcoRespBancoId.tipoResposta]). */
    fun tipoResposta(): TipoRespostaEnum? = id?.tipoRespostaEnum()
}
