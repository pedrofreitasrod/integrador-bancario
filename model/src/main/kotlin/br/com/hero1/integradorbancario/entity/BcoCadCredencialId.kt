package br.com.hero1.integradorbancario.entity

import br.com.sankhya.studio.persistence.Column
import br.com.sankhya.studio.persistence.Embeddable
import java.util.Objects

/**
 * Chave primaria composta de [BcoCadCredencial]: cadastro do banco + empresa.
 *
 * Sem construtor primario -> Kotlin gera o no-arg exigido pelo JAPE.
 * equals/hashCode manuais porque o framework compara instancias de PK.
 */
@Embeddable
class BcoCadCredencialId {

    @Column(name = "IDBANCO")
    var idBanco: Int? = null

    @Column(name = "CODEMP")
    var codEmp: Int? = null

    constructor()

    constructor(idBanco: Int?, codEmp: Int?) {
        this.idBanco = idBanco
        this.codEmp = codEmp
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BcoCadCredencialId) return false
        return idBanco == other.idBanco && codEmp == other.codEmp
    }

    override fun hashCode(): Int = Objects.hash(idBanco, codEmp)

    override fun toString(): String = "BcoCadCredencialId(idBanco=$idBanco, codEmp=$codEmp)"
}
