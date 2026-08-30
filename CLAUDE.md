@docs/ADDON.md

## Overrides do projeto

Regras específicas deste projeto que **prevalecem sobre as skills do plugin** (conforme `docs/ADDON.md` → "Conflito regra-do-projeto × skill").

### HTTP de saída: `HttpURLConnection`, não Retrofit

A camada de integração com APIs de bancos usa **`java.net.URL` + `java.net.HttpURLConnection`** diretamente, com **Moshi** apenas para o parse JSON (`String` → objeto).

- Não usar Retrofit nem OkHttp. A skill `retrofit` **não se aplica** aqui.
- mTLS: `conn as HttpsURLConnection` + `conn.sslSocketFactory = ...` por request, com o `SSLContext` montado a partir do `.pfx` da credencial (`SicoobHttpClient`).
- Decisão do dev por familiaridade. Padrão vale para todos os bancos, não só o Sicoob.

### Sem Guice / sem `@Repository` na camada de integração

Este projeto roda o KSP com `isSdkEnabled=false` — `@Job`/`@Controller` são instanciados com `new`, sem injeção de dependência. Então a feature de integração bancária **não usa Guice**:

- Sem `@Component` / `@Inject` / `@CustomModule` / `@Value`. Classes planas, `new` explícito.
- Composition root manual: `IntegracaoBancaria` (object) monta o grafo.
- Acesso a dados por **`br.com.sankhya.jape.wrapper.JapeFactory.dao("<Instance>")`** (retorna `JapeWrapper` / `DynamicVO`), não por interface `@Repository`. A skill `repository` **não se aplica**. As classes `@JapeEntity` continuam existindo só para o dicionário/DWF/autoDDL.
- Parâmetros: `MGECoreParameter.getParameterAsBoolean(...)` estático, não `@Value`.
- Entidades seguem Kotlin (classe sem construtor primário).
