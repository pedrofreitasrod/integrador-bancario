package br.com.hero1.integradorbancario.integracao

import br.com.hero1.integradorbancario.integracao.sicoob.SicoobConector
import br.com.hero1.integradorbancario.integracao.sicoob.SicoobHttpClient
import br.com.hero1.integradorbancario.integracao.sicoob.SicoobMapper
import br.com.hero1.integradorbancario.integracao.sicoob.SicoobTokenProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Composition root da integracao bancaria. Sem Guice - este projeto roda com
 * `isSdkEnabled=false`, entao o job/controller (instanciados com `new`) pegam
 * o grafo pronto daqui.
 *
 * Tudo `by lazy` para viver como singleton (o `SicoobHttpClient` cacheia
 * SSLSocketFactory por certificado, o `SicoobTokenProvider` cacheia token).
 *
 * Adicionar um banco = mais um conector na lista de [registry].
 */
object IntegracaoBancaria {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val http: SicoobHttpClient by lazy { SicoobHttpClient(moshi) }

    private val registry: ConectorBancarioRegistry by lazy {
        ConectorBancarioRegistry(
            listOf(
                SicoobConector(http, SicoobTokenProvider(http), SicoobMapper()),
            ),
        )
    }

    val buscarDdaService: BuscarDdaService by lazy {
        BuscarDdaService(BancoDao(), registry)
    }
}
