package br.com.hero1.integradorbancario.integracao.dominio

import br.com.hero1.integradorbancario.entity.BcoParamBanco

/**
 * Parametros para forcar uma nova autenticacao no banco (botao "Autenticar" da
 * tela de parametrizacao por empresa). Empresa e ambiente vem da credencial.
 */
data class ComandoAutenticacao(
    val credencial: BcoParamBanco,
    val sandbox: Boolean,
)
