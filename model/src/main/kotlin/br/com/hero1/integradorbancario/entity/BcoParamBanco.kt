package br.com.hero1.integradorbancario.entity

import br.com.sankhya.studio.persistence.Column
import br.com.sankhya.studio.persistence.Id
import br.com.sankhya.studio.persistence.JapeEntity
import java.sql.Timestamp
import java.time.Instant

/**
 * Parametros do integrador bancario por empresa e banco (BCO_PARAMBANCO).
 *
 * Uma linha por (banco, empresa): guarda as credenciais de acesso a API do
 * banco, os parametros operacionais (conta e tipo de operacao usados na baixa
 * dos titulos pagos) e o ultimo access token obtido (para reaproveitar entre
 * chamadas em vez de reautenticar toda vez). O conector do banco le esta linha
 * para autenticar; a rotina de pagamento le os parametros para baixar o
 * financeiro.
 *
 * PK manual (sequenceType="M") - composta so de FKs.
 *
 * ATENCAO: CERTSENHA guarda a senha do .pfx em texto. Se o ambiente exigir,
 * trocar por referencia a um cofre / arquivo protegido.
 *
 * Sem construtor primario -> Kotlin gera o no-arg exigido pelo JAPE.
 */
@JapeEntity(entity = "BcoParamBanco", table = "BCO_PARAMBANCO")
class BcoParamBanco {

    @Id
    var id: BcoParamBancoId? = null

    @Column(name = "CLIENTID")
    var clientId: String? = null

    @Column(name = "COOPERATIVA")
    var cooperativa: String? = null

    @Column(name = "NUMCONTA")
    var numConta: String? = null

    @Column(name = "NUMCONTRATO")
    var numContrato: String? = null

    /** Conteudo do arquivo .pfx (PKCS#12) para o mTLS. Bytes direto da coluna. */
    @Column(name = "CERTARQUIVO")
    var certArquivo: ByteArray? = null

    @Column(name = "CERTSENHA")
    var certSenha: String? = null

    @Column(name = "SCOPES")
    var scopes: String? = null

    @Column(name = "ATIVO")
    var ativo: Boolean? = null

    /** Conta bancaria (TGFCTA) usada na baixa e no movimento bancario do pagamento. */
    @Column(name = "CODCTA")
    var codCta: Int? = null

    /** Tipo de operacao do movimento bancario gerado na baixa. Nulo = default da baixa. */
    @Column(name = "CODTIPOPER")
    var codTipoOper: Int? = null

    /** Ultimo access token obtido na autenticacao com o banco. Gerenciado pelo addon. */
    @Column(name = "ACESSTOKEN")
    var acessToken: String? = null

    /** `expires_in` (segundos) retornado pelo banco junto do [acessToken]. */
    @Column(name = "EXPIRES")
    var expiresIn: Int? = null

    /** Instante em que o [acessToken] foi obtido. Base para calcular a validade. */
    @Column(name = "DHAUTENTIQUE")
    var dhAutentique: Timestamp? = null

    fun idBanco(): Int? = id?.idBanco

    fun codEmp(): Int? = id?.codEmp

    fun estaAtiva(): Boolean = ativo == true

    /**
     * Instante em que o access token persistido expira (dhAutentique +
     * expires_in). `null` quando ainda nao houve autenticacao ou faltam dados.
     */
    fun tokenExpiraEm(): Instant? {
        val obtidoEm = dhAutentique ?: return null
        val segundos = expiresIn ?: return null
        return obtidoEm.toInstant().plusSeconds(segundos.toLong())
    }
}
