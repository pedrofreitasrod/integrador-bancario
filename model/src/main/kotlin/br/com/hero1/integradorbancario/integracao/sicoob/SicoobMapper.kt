package br.com.hero1.integradorbancario.integracao.sicoob

import br.com.hero1.integradorbancario.integracao.PagamentoBancarioException
import br.com.hero1.integradorbancario.integracao.dominio.BoletoParaPagar
import br.com.hero1.integradorbancario.integracao.dominio.ComprovantePagamento
import br.com.hero1.integradorbancario.integracao.dominio.Dda
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobBoletoConsultaDto
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobBoletoDdaDto
import br.com.hero1.integradorbancario.integracao.sicoob.dto.SicoobComprovanteDto
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Converte os payloads crus do Sicoob para os modelos neutros da integracao. */
class SicoobMapper {

    /** @return null quando o boleto DDA nao tem identificador utilizavel como PK. */
    fun paraDda(dto: SicoobBoletoDdaDto): Dda? {
        val codigoBarras = dto.numeroCodigoBarras?.trim()?.takeIf { it.isNotEmpty() }
        val nossoNumero = dto.numeroNossoNumero?.trim()?.takeIf { it.isNotEmpty() }
        // Preferimos o codigo de barras: e globalmente unico e e o que a rotina
        // de pagamento precisa. Nosso numero e o fallback.
        val idFinanceiro = codigoBarras ?: nossoNumero ?: return null

        return Dda(
            idFinanceiro = idFinanceiro,
            codigoBanco = SicoobConfig.CODIGO_COMPENSACAO,
            cnpjBeneficiario = dto.numeroCpfCnpjBeneficiario?.let(::apenasDigitos),
            dataVencimento = parseData(dto.dataVencimentoBoleto),
            valor = dto.valorBoleto,
            dataNegociacao = parseData(dto.dataEmissao),
            nossoNumero = nossoNumero,
            codigoBarras = codigoBarras,
        )
    }

    fun paraBoletoParaPagar(dto: SicoobBoletoConsultaDto, codigoBarras: String): BoletoParaPagar =
        BoletoParaPagar(
            codigoBarras = dto.codigoBarras?.takeIf { it.isNotBlank() } ?: codigoBarras,
            identificadorConsulta = dto.identificadorConsulta,
            linhaDigitavel = dto.numeroLinhaDigitavel,
            dataVencimento = parseData(dto.dataVencimentoBoleto),
            dataLimitePagamento = parseData(dto.dataLimitePagamentoBoleto),
            valorBoleto = dto.valorBoleto,
            valorPagamento = dto.valorPagamento,
            permiteAlterarValor = dto.permiteAlterarValor == true,
            pagamentoBloqueado = dto.bloquearPagamento == true,
            mensagemBloqueio = dto.mensagemBloqueioPagamento,
            cnpjBeneficiario = dto.numeroCpfCnpjBeneficiario?.let(::apenasDigitos),
            nomeBeneficiario = dto.nomeRazaoSocialBeneficiario,
            situacao = dto.codigoSituacaoBoletoPagamento,
        )

    fun paraComprovante(dto: SicoobComprovanteDto): ComprovantePagamento {
        val id = dto.idPagamento
            ?: throw PagamentoBancarioException("Sicoob nao retornou idPagamento no comprovante")
        return ComprovantePagamento(
            idPagamento = id,
            autenticacao = dto.numeroAutenticacaoPagamento,
            situacao = dto.situacaoPagamento,
            detalheSituacao = dto.descricaoDetalheSituacao,
            dataPagamento = parseData(dto.dataPagamento),
            valorPagamento = dto.valorPagamento,
            linhaDigitavel = dto.numeroLinhaDigitavel,
        )
    }

    private fun parseData(texto: String?): LocalDate? {
        if (texto.isNullOrBlank()) return null
        return try {
            LocalDate.parse(texto.trim().take(10))
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun apenasDigitos(texto: String): String = texto.filter(Char::isDigit)
}
