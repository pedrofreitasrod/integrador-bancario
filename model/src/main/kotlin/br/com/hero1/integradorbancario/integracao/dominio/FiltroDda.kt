package br.com.hero1.integradorbancario.integracao.dominio

import br.com.hero1.integradorbancario.entity.BcoCadCredencial
import java.time.LocalDate

/**
 * Parametros da consulta de DDA: empresa (via credencial) + periodo de vencimento.
 * O mesmo objeto serve para o job agendado e para o botao da tela.
 */
data class FiltroDda(
    val credencial: BcoCadCredencial,
    val sandbox: Boolean,
    val dataInicio: LocalDate,
    val dataFim: LocalDate,
) {
    val codEmp: Int
        get() = credencial.codEmp() ?: error("Credencial sem CODEMP")
}
