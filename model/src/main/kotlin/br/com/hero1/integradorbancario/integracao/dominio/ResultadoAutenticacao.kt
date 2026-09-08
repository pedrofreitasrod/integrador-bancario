package br.com.hero1.integradorbancario.integracao.dominio

import java.time.Instant

/**
 * Resultado de uma autenticacao no banco - formato neutro.
 *
 * O conector ja persiste o token nos parametros da empresa (BCO_PARAMBANCO);
 * este objeto e so o retorno para a tela/rotina que disparou a autenticacao.
 */
data class ResultadoAutenticacao(
    val token: String,
    /** `expires_in` (segundos) informado pelo banco. */
    val expiraEmSegundos: Int,
    /** Instante em que o token foi obtido. */
    val autenticadoEm: Instant,
    /** Escopos concedidos, quando o banco devolve. */
    val escopo: String? = null,
)
