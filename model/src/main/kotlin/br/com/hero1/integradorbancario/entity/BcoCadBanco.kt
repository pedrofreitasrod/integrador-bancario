package br.com.hero1.integradorbancario.entity

import br.com.sankhya.studio.persistence.Column
import br.com.sankhya.studio.persistence.Id
import br.com.sankhya.studio.persistence.JapeEntity

/**
 * Cadastro de bancos (BCO_CADBANCO).
 *
 * Classe sem construtor primario declarado -> Kotlin gera o construtor sem-args
 * que o JAPE exige, sem precisar do plugin kotlin-noarg. Propriedades `var`
 * nullable para o framework popular via reflexao.
 */
@JapeEntity(entity = "BcoCadBanco", table = "BCO_CADBANCO")
class BcoCadBanco {

    @Id
    @Column(name = "ID")
    var id: Int? = null

    @Column(name = "NOMEBANCO")
    var nomeBanco: String? = null

    @Column(name = "CODIGODOBANCO")
    var codigoDoBanco: Int? = null

    @Column(name = "SANDBOX")
    var sandbox: Boolean? = null
}
