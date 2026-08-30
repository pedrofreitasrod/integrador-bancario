package br.com.hero1.integradorbancario.entity

import br.com.sankhya.studio.persistence.Column
import br.com.sankhya.studio.persistence.Id
import br.com.sankhya.studio.persistence.JapeEntity

/**
 * Credenciais de acesso a API de um banco para uma empresa (BCO_CADCREDENCIAL).
 *
 * Uma linha por (banco, empresa). O conector do banco le esta linha para
 * autenticar. PK manual (sequenceType="M") - composta so de FKs.
 *
 * ATENCAO: CERTSENHA guarda a senha do .pfx em texto. Se o ambiente exigir,
 * trocar por referencia a um cofre / arquivo protegido.
 *
 * Sem construtor primario -> Kotlin gera o no-arg exigido pelo JAPE.
 */
@JapeEntity(entity = "BcoCadCredencial", table = "BCO_CADCREDENCIAL")
class BcoCadCredencial {

    @Id
    var id: BcoCadCredencialId? = null

    @Column(name = "CLIENTID")
    var clientId: String? = null

    @Column(name = "COOPERATIVA")
    var cooperativa: String? = null

    @Column(name = "NUMCONTA")
    var numConta: String? = null

    @Column(name = "NUMCONTRATO")
    var numContrato: String? = null

    @Column(name = "CERTARQUIVO")
    var certArquivo: String? = null

    @Column(name = "CERTSENHA")
    var certSenha: String? = null

    @Column(name = "SCOPES")
    var scopes: String? = null

    @Column(name = "ATIVO")
    var ativo: Boolean? = null

    fun idBanco(): Int? = id?.idBanco

    fun codEmp(): Int? = id?.codEmp

    fun estaAtiva(): Boolean = ativo == true
}
