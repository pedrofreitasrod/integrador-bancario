package br.com.hero1.integradorbancario.entity

import br.com.sankhya.studio.persistence.Column
import br.com.sankhya.studio.persistence.Embeddable
import java.util.Objects

/**
 * Chave primaria composta de [BcoRespBanco]:
 * id do registro no banco (retorno da API) + cadastro do banco + empresa +
 * tipo de resposta.
 *
 * TIPORESP entra na PK porque o mesmo IDFINANCEIRO pode chegar em respostas de
 * tipos diferentes do mesmo banco/empresa. Guardado como String (nao como
 * [TipoRespostaEnum]) para nao arriscar o mapeamento de enum dentro do
 * @Embeddable; use [tipoRespostaEnum] para a versao tipada.
 *
 * Sem construtor primario -> Kotlin gera o no-arg exigido pelo JAPE.
 * equals/hashCode manuais porque o framework compara instancias de PK.
 */
@Embeddable
class BcoRespBancoId {

    @Column(name = "IDFINANCEIRO")
    var idFinanceiro: String? = null

    @Column(name = "IDBANCO")
    var idBanco: Int? = null

    @Column(name = "CODEMP")
    var codEmp: Int? = null

    @Column(name = "TIPORESP")
    var tipoResposta: String? = null

    constructor()

    constructor(idFinanceiro: String?, idBanco: Int?, codEmp: Int?, tipoResposta: String?) {
        this.idFinanceiro = idFinanceiro
        this.idBanco = idBanco
        this.codEmp = codEmp
        this.tipoResposta = tipoResposta
    }

    constructor(idFinanceiro: String?, idBanco: Int?, codEmp: Int?, tipoResposta: TipoRespostaEnum?)
        : this(idFinanceiro, idBanco, codEmp, tipoResposta?.value)

    fun tipoRespostaEnum(): TipoRespostaEnum? = TipoRespostaEnum.fromValue(tipoResposta)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BcoRespBancoId) return false
        return idFinanceiro == other.idFinanceiro &&
            idBanco == other.idBanco &&
            codEmp == other.codEmp &&
            tipoResposta == other.tipoResposta
    }

    override fun hashCode(): Int = Objects.hash(idFinanceiro, idBanco, codEmp, tipoResposta)

    override fun toString(): String =
        "BcoRespBancoId(idFinanceiro=$idFinanceiro, idBanco=$idBanco, codEmp=$codEmp, tipoResposta=$tipoResposta)"
}
