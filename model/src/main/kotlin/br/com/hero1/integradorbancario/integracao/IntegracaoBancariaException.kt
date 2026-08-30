package br.com.hero1.integradorbancario.integracao

/** Base das falhas da integracao bancaria. Mensagem voltada ao usuario de negocio. */
open class IntegracaoBancariaException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Falha ao autenticar na API do banco (token, certificado, credencial invalida). */
class AutenticacaoBancariaException(message: String, cause: Throwable? = null) :
    IntegracaoBancariaException(message, cause)

/** Falha ao consultar dados na API do banco (HTTP, rede, payload inesperado). */
class ConsultaBancariaException(message: String, cause: Throwable? = null) :
    IntegracaoBancariaException(message, cause)
