package br.com.hero1.integradorbancario.integracao

/**
 * Converte o codigo de barras de um boleto bancario (44 digitos) para a linha
 * digitavel (47 digitos) pelo padrao FEBRABAN.
 *
 * Trata apenas boleto bancario (arrecadacao / convenio - codigo iniciando com
 * "8", 48 digitos - devolve null; nesse caso o chamador grava so o codigo de
 * barras). O calculo e deterministico, entao dispensa consultar o banco.
 */
internal object LinhaDigitavel {

    /**
     * @return linha digitavel de 47 digitos, ou null se [codigoBarras] nao for
     * um codigo de barras de boleto bancario valido.
     */
    fun deCodigoBarras(codigoBarras: String?): String? {
        val cb = codigoBarras?.filter(Char::isDigit) ?: return null
        if (cb.length != 44 || cb.startsWith("8")) return null

        val bancoMoeda = cb.substring(0, 4)      // posicoes 1-4
        val dvGeral = cb.substring(4, 5)         // posicao 5
        val fatorValor = cb.substring(5, 19)     // posicoes 6-19 (fator venc. + valor)
        val campoLivre = cb.substring(19, 44)    // posicoes 20-44

        val campo1 = bancoMoeda + campoLivre.substring(0, 5)
        val campo2 = campoLivre.substring(5, 15)
        val campo3 = campoLivre.substring(15, 25)

        return buildString {
            append(campo1).append(dvMod10(campo1))
            append(campo2).append(dvMod10(campo2))
            append(campo3).append(dvMod10(campo3))
            append(dvGeral)
            append(fatorValor)
        }
    }

    /** Digito verificador modulo 10 (pesos 2,1,2,1... da direita; soma dos digitos quando > 9). */
    private fun dvMod10(campo: String): Int {
        var soma = 0
        var peso = 2
        for (c in campo.reversed()) {
            var produto = (c - '0') * peso
            if (produto > 9) produto = produto / 10 + produto % 10
            soma += produto
            peso = if (peso == 2) 1 else 2
        }
        val resto = soma % 10
        return if (resto == 0) 0 else 10 - resto
    }
}
