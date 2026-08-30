package br.com.hero1.integradorbancario.integracao.sicoob

import br.com.hero1.integradorbancario.integracao.dominio.Dda
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobBoletoDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Converte o boleto cru do Sicoob para o DDA normalizado. */
class SicoobMapper {

    /** @return null quando o boleto nao tem identificador utilizavel como PK. */
    fun paraDda(dto: SicoobBoletoDto): Dda? {
        val idFinanceiro = dto.nossoNumero ?: dto.seuNumero ?: return null
        return Dda(
            idFinanceiro = idFinanceiro,
            codigoBanco = SicoobConfig.CODIGO_COMPENSACAO,
            cnpjBeneficiario = dto.beneficiario?.numeroCpfCnpj?.let(::apenasDigitos),
            dataVencimento = parseData(dto.dataVencimento),
            valor = parseValor(dto.valor),
            dataNegociacao = parseData(dto.dataEmissao),
            nossoNumero = dto.nossoNumero,
        )
    }

    private fun parseData(texto: String?): LocalDate? {
        if (texto.isNullOrBlank()) return null
        return try {
            LocalDate.parse(texto)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun parseValor(texto: String?): BigDecimal? {
        if (texto.isNullOrBlank()) return null
        return try {
            BigDecimal(texto.trim().replace(",", "."))
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun apenasDigitos(texto: String): String = texto.filter(Char::isDigit)
}
