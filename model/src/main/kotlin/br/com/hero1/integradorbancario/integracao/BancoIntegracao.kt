package br.com.hero1.integradorbancario.integracao

/**
 * Bancos com integracao implementada no addon.
 *
 * A URL base de cada banco (homologacao / producao) mora aqui, junto do codigo
 * que a consome, e nao na tabela BCO_CADBANCO. A tabela guarda apenas o cadastro
 * (nome, codigo de compensacao e o flag de ambiente).
 *
 * Adicionar um banco novo = uma constante aqui + o client/interceptor/mapper dele.
 */
enum class BancoIntegracao(
    val codigoCompensacao: Int,
    val urlBaseSandbox: String,
    val urlBaseProducao: String,
) {

    SICOOB(
        codigoCompensacao = 756,
        urlBaseSandbox = "https://sandbox.sicoob.com.br/sicoob/sandbox",
        urlBaseProducao = "https://api.sicoob.com.br",
    );

    /**
     * URL base da API do banco para o ambiente informado.
     * `sandbox == true` -> homologacao; caso contrario (inclusive `null`) -> producao.
     */
    fun urlBase(sandbox: Boolean?): String =
        if (sandbox == true) urlBaseSandbox else urlBaseProducao

    companion object {
        @JvmStatic
        fun fromCodigo(codigo: Int?): BancoIntegracao {
            if (codigo == null) {
                throw BancoNaoSuportadoException("Codigo de compensacao nao informado.")
            }
            return entries.firstOrNull { it.codigoCompensacao == codigo }
                ?: throw BancoNaoSuportadoException("Banco sem integracao implementada: $codigo")
        }
    }
}
