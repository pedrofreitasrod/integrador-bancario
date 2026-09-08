package br.com.hero1.integradorbancario.entity

import br.com.sankhya.studio.persistence.Column
import br.com.sankhya.studio.persistence.Id
import br.com.sankhya.studio.persistence.JapeEntity
import java.sql.Timestamp

/**
 * Log do integrador bancario (BCO_LOG) - eventos gravados por `LogHelper`.
 *
 * PK sequencial gerada pelo framework (sequenceType="A"). Existe so para o
 * dicionario / DWF / autoDDL; o acesso e por `JapeFactory.dao("BcoLog")`.
 *
 * Sem construtor primario -> Kotlin gera o no-arg exigido pelo JAPE.
 */
@JapeEntity(entity = "BcoLog", table = "BCO_LOG")
class BcoLog {

    @Id
    @Column(name = "NULOG")
    var nuLog: Long? = null

    @Column(name = "DATA")
    var data: Timestamp? = null

    /** INFO, WARNING ou ERROR (ver [LogHelper.Status]). */
    @Column(name = "STATUS")
    var status: String? = null

    @Column(name = "CODEMP")
    var codEmp: Int? = null

    /** Rotina que gerou o log (classe / botao / job). */
    @Column(name = "ORIGEM")
    var origem: String? = null

    @Column(name = "MENSAGEM")
    var mensagem: String? = null

    /** Stack trace / detalhes. Pode vir truncado ao tamanho da coluna. */
    @Column(name = "DETALHAMENTO")
    var detalhamento: String? = null
}
