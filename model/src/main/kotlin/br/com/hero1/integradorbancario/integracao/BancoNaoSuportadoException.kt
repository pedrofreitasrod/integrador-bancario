package br.com.hero1.integradorbancario.integracao

/**
 * Lancada quando um codigo de compensacao nao possui integracao implementada
 * no addon (sem entrada correspondente em [BancoIntegracao]).
 */
class BancoNaoSuportadoException(message: String) : RuntimeException(message)
