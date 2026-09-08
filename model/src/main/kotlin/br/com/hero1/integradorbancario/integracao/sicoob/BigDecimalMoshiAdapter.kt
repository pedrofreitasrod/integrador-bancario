package br.com.hero1.integradorbancario.integracao.sicoob

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import java.math.BigDecimal

/**
 * Adaptador Moshi para [BigDecimal] - o Moshi nao traz suporte nativo.
 *
 * Valores monetarios das APIs dos bancos chegam como numero JSON (ex.: 152.3);
 * ler direto em `Double` perderia precisao. `reader.nextString()` devolve o
 * literal do numero sem conversao, entao o `BigDecimal` e montado exato.
 * Aceita tambem string ("152.30") e trata `null`/vazio.
 */
internal class BigDecimalMoshiAdapter {

    @FromJson
    fun fromJson(reader: JsonReader): BigDecimal? {
        if (reader.peek() == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        val texto = reader.nextString().trim()
        return if (texto.isEmpty()) null else BigDecimal(texto)
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: BigDecimal?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }
}
