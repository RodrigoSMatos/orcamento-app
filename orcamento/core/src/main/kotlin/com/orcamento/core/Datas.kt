package com.orcamento.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Datas {
    val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
    private val FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR)
    private val FMT_DIA_MES = DateTimeFormatter.ofPattern("dd/MM", PT_BR)
    private val FMT_MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR)
    private val FMT_MES_CURTO = DateTimeFormatter.ofPattern("MMM/yy", PT_BR)

    /** Dia [dia] do mês, ajustado para o último dia quando o mês é mais curto (31 → 30/28). */
    fun diaNoMes(mes: YearMonth, dia: Int): LocalDate = mes.atDay(dia.coerceIn(1, mes.lengthOfMonth()))

    fun formatar(data: LocalDate): String = data.format(FMT_DATA)
    fun formatarDiaMes(data: LocalDate): String = data.format(FMT_DIA_MES)
    fun formatarMes(mes: YearMonth): String = mes.format(FMT_MES).replaceFirstChar { it.titlecase(PT_BR) }
    fun formatarMesCurto(mes: YearMonth): String = mes.format(FMT_MES_CURTO).replace(".", "")

    /** Data dentro do mês: hoje, se hoje estiver nele; senão o último dia do mês (passado) ou o primeiro (futuro). */
    fun dataPadraoNoMes(mes: YearMonth, hoje: LocalDate): LocalDate = when {
        YearMonth.from(hoje) == mes -> hoje
        mes < YearMonth.from(hoje) -> mes.atEndOfMonth()
        else -> mes.atDay(1)
    }

    fun situacao(pago: Boolean, vencimento: LocalDate?, hoje: LocalDate): Situacao = when {
        pago -> Situacao.PAGO
        vencimento == null -> Situacao.PENDENTE
        vencimento.isBefore(hoje) -> Situacao.VENCIDO
        !vencimento.isAfter(hoje.plusDays(DIAS_AVISO_VENCIMENTO)) -> Situacao.PROXIMO
        else -> Situacao.PENDENTE
    }
}

/**
 * Regras do ciclo do cartão de crédito.
 * - Compra feita ANTES do dia de fechamento entra no ciclo que fecha nesse mês.
 * - Compra no dia do fechamento ou depois entra no ciclo seguinte.
 * - A fatura vence no primeiro "dia de vencimento" posterior ao fechamento.
 * A fatura é identificada pelo mês em que vence.
 */
object CicloCartao {
    fun fechamentoDoCiclo(cartao: Cartao, compra: LocalDate): LocalDate {
        val mes = YearMonth.from(compra)
        val fechamento = Datas.diaNoMes(mes, cartao.diaFechamento)
        return if (compra.isBefore(fechamento)) fechamento else Datas.diaNoMes(mes.plusMonths(1), cartao.diaFechamento)
    }

    fun vencimentoDaCompra(cartao: Cartao, compra: LocalDate): LocalDate {
        val fechamento = fechamentoDoCiclo(cartao, compra)
        val mes = YearMonth.from(fechamento)
        val vencimento = Datas.diaNoMes(mes, cartao.diaVencimento)
        return if (vencimento.isAfter(fechamento)) vencimento else Datas.diaNoMes(mes.plusMonths(1), cartao.diaVencimento)
    }

    fun mesDaFatura(cartao: Cartao, compra: LocalDate): YearMonth = YearMonth.from(vencimentoDaCompra(cartao, compra))

    fun vencimentoDaFatura(cartao: Cartao, mesVencimento: YearMonth): LocalDate =
        Datas.diaNoMes(mesVencimento, cartao.diaVencimento)

    /** Janela segura de datas de compra que podem cair na fatura de [mesVencimento] (filtrar depois com mesDaFatura). */
    fun janelaDeBusca(mesVencimento: YearMonth): Pair<LocalDate, LocalDate> =
        mesVencimento.minusMonths(2).atDay(1) to mesVencimento.atEndOfMonth()
}
