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
import java.util.logging.Logger

/** Converte os payloads crus do Sicoob para os modelos neutros da integracao. */
class SicoobMapper {

    private val log: Logger = Logger.getLogger(SicoobMapper::class.java.name)

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
            numeroDocumento = parseNumeroDocumento(dto.numeroDocumento, idFinanceiro),
        )
    }

    /**
     * numeroDocumento nao numerico ou maior que Int estoura em silencio via
     * toIntOrNull(); loga para nao mascarar por que o match automatico (que
     * agora exige este campo) deixou de casar um DDA especifico.
     */
    private fun parseNumeroDocumento(numeroDocumento: String?, idFinanceiro: String): Int? {
        val bruto = numeroDocumento?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val valor = bruto.toIntOrNull()
        if (valor == null) {
            log.warning(
                "numeroDocumento Sicoob nao numerico/overflow (DDA $idFinanceiro): '$bruto' - " +
                    "match automatico por NUMNOTA desabilitado para este DDA.",
            )
        }
        return valor
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
            tituloComprovante = dto.descricaoTituloComprovante,
            // dataHoraCadastro vem em ISO 8601 ("2026-09-15T18:41:41.136Z") - parseData
            // pega so os 10 primeiros chars (yyyy-MM-dd), entao serve pra ela tambem.
            // NAO e data de agendamento - e quando o pagamento foi cadastrado no Sicoob.
            dataCadastro = parseData(dto.dataHoraCadastro),
            dataVencimento = parseData(dto.dataVencimento),
            dataPagamento = parseData(dto.dataPagamento),
            valorBoleto = dto.valorBoleto,
            valorDesconto = dto.valorAbatimentoDesconto,
            valorMulta = dto.valorMultaMora,
            valorPagamento = dto.valorPagamento,
            linhaDigitavel = dto.numeroLinhaDigitavel,
            nossoNumero = dto.nossoNumero,
            numeroDocumento = dto.numeroDocumento,
            observacao = dto.descricaoObservacao,
            ouvidoria = dto.descricaoOuvidoria,
            cnpjBeneficiario = dto.numeroCpfCnpjBeneficiario?.let(::apenasDigitos),
            nomeBeneficiario = dto.nomeRazaoSocialBeneficiario,
            instituicaoBeneficiaria = formatarInstituicao(dto.numeroInstituicaoEmissora, dto.nomeInstituicaoEmissora),
            cnpjPagador = dto.numeroCpfCnpjPagador?.let(::apenasDigitos),
            nomePagador = dto.nomeRazaoSocialPagador,
            numeroAgencia = dto.numeroAgencia,
            nomeAgencia = dto.nomeAgencia,
            numeroConta = dto.numeroConta?.toString(),
            nomeProprietarioConta = dto.nomeProprietarioContaCorrente,
        )
    }

    private fun formatarInstituicao(numero: Int?, nome: String?): String? {
        if (numero == null && nome == null) return null
        return listOfNotNull(numero?.toString(), nome).joinToString("-")
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
