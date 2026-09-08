package br.com.hero1.integradorbancario.integracao.dominio

import br.com.hero1.integradorbancario.entity.BcoParamBanco
import java.time.LocalDate

/**
 * Parametros da consulta de DDA: empresa (via credencial) + periodo de vencimento.
 * O mesmo objeto serve para o job agendado e para o botao da tela.
 */
data class FiltroDda(
    val credencial: BcoParamBanco,
    val sandbox: Boolean,
    val dataInicio: LocalDate,
    val dataFim: LocalDate,
) {
    val codEmp: Int
        get() = credencial.codEmp() ?: error("Credencial sem CODEMP")
}
