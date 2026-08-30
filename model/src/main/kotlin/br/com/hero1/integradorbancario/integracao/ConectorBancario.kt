package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.integracao.dominio.Dda
import br.com.hero1.integradorbancario.integracao.dominio.FiltroDda

/**
 * Contrato de integracao de UM banco. Cada banco tem uma implementacao;
 * a rotina de negocio depende so desta interface.
 *
 * A autenticacao e responsabilidade interna do conector (token/certificado
 * resolvidos a partir da credencial do filtro) - nao ha metodo de auth exposto.
 *
 * Registro: cada implementacao entra na lista passada ao
 * [ConectorBancarioRegistry], montada em [IntegracaoBancaria].
 */
interface ConectorBancario {

    /** Codigo de compensacao do banco que este conector atende (ex.: 756 = Sicoob). */
    val codigoCompensacao: Int

    /** Consulta os DDAs do periodo/empresa e devolve ja normalizados. */
    fun buscarDdas(filtro: FiltroDda): List<Dda>
}
