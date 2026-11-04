package com.orcamento.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Grupo organizacional de um compromisso. Não muda nenhum cálculo, só a leitura. */
enum class Grupo(val rotulo: String) {
    ESSENCIAL("Essencial"),
    NAO_ESSENCIAL("Não essencial"),
    OUTRO("Outro"),
}

enum class Frequencia(val intervaloMeses: Int, val rotulo: String) {
    MENSAL(1, "Mensal"),
    BIMESTRAL(2, "Bimestral"),
    TRIMESTRAL(3, "Trimestral"),
    SEMESTRAL(6, "Semestral"),
    ANUAL(12, "Anual"),
}

enum class Situacao(val rotulo: String) {
    PENDENTE("Pendente"),
    PROXIMO("Vence em breve"),
    VENCIDO("Vencido"),
    PAGO("Pago"),
}

/** Para onde um gasto do dia a dia é contabilizado. */
enum class Destino(val rotulo: String) {
    AVULSO("Avulso"),
    LAZER("Lazer"),
}

/** Quantos dias antes do vencimento um compromisso passa a "vence em breve". */
const val DIAS_AVISO_VENCIMENTO = 3L

data class Cartao(
    val id: Long = 0,
    val nome: String,
    /** Compras feitas neste dia ou depois entram na fatura seguinte. */
    val diaFechamento: Int,
    val diaVencimento: Int,
    val ativo: Boolean = true,
)

/** Valores usados para criar cada mês novo. Mudar aqui não altera meses já criados (ver ServicoFinanceiro). */
data class PlanoPadrao(
    val renda: Dinheiro = Dinheiro.ZERO,
    val limiteAvulsos: Dinheiro = Dinheiro.ZERO,
    val metaInvestimento: Dinheiro = Dinheiro.ZERO,
)

/** Cópia do planejamento de um mês específico. Cada mês tem a sua. */
data class PlanoMes(
    val mes: YearMonth,
    val renda: Dinheiro,
    val limiteAvulsos: Dinheiro,
    val metaInvestimento: Dinheiro,
    val fechado: Boolean = false,
    val fechadoEm: LocalDate? = null,
)

/** Despesa recorrente cadastrada uma vez e gerada automaticamente nos meses em que se aplica. */
data class ModeloRecorrente(
    val id: Long = 0,
    val nome: String,
    val valor: Dinheiro,
    val grupo: Grupo,
    val categoria: String? = null,
    val diaVencimento: Int? = null,
    val frequencia: Frequencia = Frequencia.MENSAL,
    val mesInicio: YearMonth,
    /** Último mês em que a despesa existe (ex.: fim da faculdade). Null = sem fim. */
    val mesFim: YearMonth? = null,
    /** Se for cobrada no cartão, entra na fatura desse cartão. */
    val cartaoId: Long? = null,
    val ativo: Boolean = true,
    val observacoes: String? = null,
) {
    fun aplicaEm(mes: YearMonth): Boolean {
        if (!ativo || mes < mesInicio) return false
        if (mesFim != null && mes > mesFim) return false
        val distancia = ChronoUnit.MONTHS.between(mesInicio, mes)
        return distancia % frequencia.intervaloMeses == 0L
    }

    fun gerarCompromisso(mes: YearMonth) = Compromisso(
        mes = mes,
        nome = nome,
        valorPlanejado = valor,
        grupo = grupo,
        categoria = categoria,
        vencimento = diaVencimento?.let { Datas.diaNoMes(mes, it) },
        cartaoId = cartaoId,
        modeloId = id,
        observacoes = observacoes,
    )

    /** Atualiza um compromisso já gerado com os dados atuais do modelo (mantém pagamento e id). */
    fun atualizar(c: Compromisso) = c.copy(
        nome = nome,
        valorPlanejado = valor,
        grupo = grupo,
        categoria = categoria,
        vencimento = diaVencimento?.let { Datas.diaNoMes(c.mes, it) },
        cartaoId = cartaoId,
        observacoes = observacoes,
    )
}

/** Uma obrigação de um mês específico (gerada de um modelo ou cadastrada à mão). */
data class Compromisso(
    val id: Long = 0,
    val mes: YearMonth,
    val nome: String,
    val valorPlanejado: Dinheiro,
    val grupo: Grupo,
    val categoria: String? = null,
    val vencimento: LocalDate? = null,
    val cartaoId: Long? = null,
    val modeloId: Long? = null,
    val pago: Boolean = false,
    val dataPagamento: LocalDate? = null,
    val valorPago: Dinheiro? = null,
    /** Marcado quando a pessoa altera este mês à mão; o modelo deixa de sobrescrever. */
    val editadoManualmente: Boolean = false,
    val observacoes: String? = null,
) {
    /** Valor real se já pago; senão, o planejado. */
    val valorEfetivo: Dinheiro get() = if (pago) (valorPago ?: valorPlanejado) else valorPlanejado

    val noCartao: Boolean get() = cartaoId != null

    /** Data em que a cobrança cai no cartão (vencimento, ou dia 1 se não houver). */
    val dataCobranca: LocalDate get() = vencimento ?: mes.atDay(1)

    fun situacao(hoje: LocalDate): Situacao = Datas.situacao(pago, vencimento, hoje)
}

/** Item de lazer padrão: gera um item com este limite em cada mês novo. */
data class ModeloLazer(
    val id: Long = 0,
    val nome: String,
    val valor: Dinheiro,
    val ativo: Boolean = true,
)

/** Limite de lazer de um mês específico (ex.: "Comida fora" R$ 150,00 em outubro). */
data class ItemLazer(
    val id: Long = 0,
    val mes: YearMonth,
    val nome: String,
    val limite: Dinheiro,
    val modeloId: Long? = null,
    val editadoManualmente: Boolean = false,
)

/** Um gasto do dia a dia. Pertence ao mês da sua data. */
data class Lancamento(
    val id: Long = 0,
    val data: LocalDate,
    val descricao: String,
    val valor: Dinheiro,
    val destino: Destino = Destino.AVULSO,
    /** Obrigatório quando destino = LAZER. */
    val itemLazerId: Long? = null,
    val categoria: String? = null,
    /** Null = pago na hora (Pix, débito, dinheiro). Preenchido = compra no cartão. */
    val cartaoId: Long? = null,
    val observacoes: String? = null,
) {
    val mes: YearMonth get() = YearMonth.from(data)
}

/** Dinheiro efetivamente investido. Não é gasto de consumo. */
data class Aporte(
    val id: Long = 0,
    val data: LocalDate,
    val valor: Dinheiro,
    val descricao: String? = null,
) {
    val mes: YearMonth get() = YearMonth.from(data)
}

enum class TipoMovimentoReserva(val entrada: Boolean, val rotulo: String) {
    /** Sobra de lazer transferida no fechamento de um mês. */
    ENTRADA_FECHAMENTO(true, "Sobra de mês fechado"),
    /** Depósito manual (saldo inicial, ajuste). */
    ENTRADA_MANUAL(true, "Depósito"),
    /** Dinheiro da reserva usado para aumentar o orçamento de lazer ou de avulsos de um mês. */
    SAIDA_COBERTURA(false, "Uso no mês"),
    /** Retirada da reserva para outra finalidade. */
    SAIDA_RETIRADA(false, "Retirada"),
}

data class MovimentoReserva(
    val id: Long = 0,
    val data: LocalDate,
    val tipo: TipoMovimentoReserva,
    /** Sempre positivo; o tipo diz se entra ou sai. */
    val valor: Dinheiro,
    val descricao: String,
    val motivo: String? = null,
    /** Mês fechado (entrada) ou mês coberto (saída por cobertura). */
    val mesReferencia: YearMonth? = null,
    /** Qual orçamento a cobertura aumenta. Só para SAIDA_COBERTURA. */
    val envelope: Destino? = null,
) {
    val valorComSinal: Dinheiro get() = if (tipo.entrada) valor else -valor
}

/** Registro do pagamento/ajuste de uma fatura. O total da fatura é calculado, não digitado. */
data class Fatura(
    val id: Long = 0,
    val cartaoId: Long,
    val mesVencimento: YearMonth,
    /** Valores que estão na fatura mas não foram lançados no app (anuidade, parcela antiga…). */
    val ajuste: Dinheiro = Dinheiro.ZERO,
    val pago: Boolean = false,
    val dataPagamento: LocalDate? = null,
    val valorPago: Dinheiro? = null,
    val observacoes: String? = null,
)

class RegraViolada(mensagem: String) : Exception(mensagem)
