package br.com.hero1.integradorbancario.entity

import br.com.sankhya.studio.persistence.Column
import br.com.sankhya.studio.persistence.Id
import br.com.sankhya.studio.persistence.JapeEntity
import lombok.Data
import lombok.NoArgsConstructor
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
@Data
@NoArgsConstructor
@JapeEntity(entity = "BcoRespBanco", table = "BCO_RESPBANCO")
open class BcoRespBanco {

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

    /**
     * Codigo de barras do boleto do DDA, quando o banco fornece. Persistido a
     * parte porque [BcoRespBancoId.idFinanceiro] pode carregar o nosso numero
     * (fallback do mapper). O rematch usa este campo para preencher a TGFFIN.
     */
    @Column(name = "CODBARRAS")
    var codigoBarras: String? = null

    /** Numero do documento do boleto, usado como criterio adicional de match automatico com TGFFIN.NUMNOTA. */
    @Column(name = "NUMERODOC")
    var numeroDoc: Int? = null

    /** Id do pagamento no banco (retorno da API), para reconsulta de comprovante. */
    @Column(name = "IDPAGAMENTO")
    var idPagamento: String? = null

    @Column(name = "AUTENTICACAO")
    var autenticacao: String? = null

    /** Situacao do pagamento (ex.: "Efetivado", "Agendado", "Rejeitado"). */
    @Column(name = "SITUACAOPGTO")
    var situacaoPagamento: String? = null

    @Column(name = "VLRPAGO")
    var valorPago: BigDecimal? = null

    @Column(name = "DTPAGAMENTO")
    var dataPagamento: Timestamp? = null

    /** CHAVEARQUIVO (TSIANX) do PDF do comprovante ja anexado - evita reanexar a cada reconsulta. */
    @Column(name = "CHAVEANEXO")
    var chaveAnexo: String? = null

    /** Tipo de resposta (discriminador), que mora na PK ([BcoRespBancoId.tipoResposta]). */
    fun tipoResposta(): TipoRespostaEnum? = id?.tipoRespostaEnum()
}
