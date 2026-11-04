package com.orcamento.core

import java.time.LocalDate
import java.time.YearMonth

/** Percentual a partir do qual um orçamento passa a "atenção". */
const val LIMIAR_ATENCAO = 80

enum class Nivel(val rotulo: String) {
    SEM_LIMITE("Sem limite definido"),
    DENTRO("Dentro do orçamento"),
    ATENCAO("Perto do limite"),
    ACIMA("Acima do limite"),
}

/**
 * Um orçamento com limite (lazer, avulsos, um item de lazer).
 *   disponível = limite + cobertura da reserva
 *   saldo      = disponível − gasto            (negativo quando estoura)
 *   excesso    = max(0, gasto − disponível)
 */
data class Envelope(
    val limite: Dinheiro,
    val gasto: Dinheiro,
    val coberturaReserva: Dinheiro = Dinheiro.ZERO,
) {
    val disponivel: Dinheiro get() = limite + coberturaReserva
    val saldo: Dinheiro get() = disponivel - gasto
    val restante: Dinheiro get() = saldo.minimoZero()
    val excesso: Dinheiro get() = (gasto - disponivel).minimoZero()
    val percentualUsado: Int? get() = Percentual.de(gasto, disponivel)
    val nivel: Nivel
        get() = when {
            !disponivel.ehPositivo -> if (gasto.ehPositivo) Nivel.ACIMA else Nivel.SEM_LIMITE
            gasto > disponivel -> Nivel.ACIMA
            (percentualUsado ?: 0) >= LIMIAR_ATENCAO -> Nivel.ATENCAO
            else -> Nivel.DENTRO
        }
}

data class ResumoItemLazer(val item: ItemLazer, val envelope: Envelope)

data class ResumoInvestimento(val meta: Dinheiro, val aportado: Dinheiro) {
    val falta: Dinheiro get() = (meta - aportado).minimoZero()
    val acimaDaMeta: Dinheiro get() = (aportado - meta).minimoZero()
    /** Null quando a meta é zero. */
    val percentual: Int? get() = Percentual.de(aportado, meta)
    val metaAtingida: Boolean get() = meta.ehPositivo && aportado >= meta
}

/** Uma fatura com o total calculado a partir do que foi lançado no cartão. */
data class FaturaCalculada(
    val cartao: Cartao,
    val mesVencimento: YearMonth,
    val registro: Fatura?,
    val compras: List<Lancamento>,
    val compromissos: List<Compromisso>,
) {
    val totalCompras: Dinheiro get() = compras.somarDinheiro { it.valor }
    val totalCompromissos: Dinheiro get() = compromissos.somarDinheiro { it.valorEfetivo }
    val ajuste: Dinheiro get() = registro?.ajuste ?: Dinheiro.ZERO
    val totalCalculado: Dinheiro get() = totalCompras + totalCompromissos + ajuste
    val pago: Boolean get() = registro?.pago == true
    /** O que sai (ou saiu) da conta para quitar a fatura. */
    val valorDevido: Dinheiro get() = if (pago) (registro?.valorPago ?: totalCalculado) else totalCalculado
    val vencimento: LocalDate get() = CicloCartao.vencimentoDaFatura(cartao, mesVencimento)
    /** Diferença entre o que foi pago e o calculado (ex.: compra lançada depois do pagamento). */
    val diferencaAposPagamento: Dinheiro get() = if (pago) totalCalculado - valorDevido else Dinheiro.ZERO
    val temConteudo: Boolean get() = compras.isNotEmpty() || compromissos.isNotEmpty() || !ajuste.ehZero || pago
}

enum class TipoConta { COMPROMISSO, FATURA }

/** Algo que precisa sair da conta bancária no mês (visão de caixa). */
data class ItemConta(
    val tipo: TipoConta,
    val nome: String,
    val valor: Dinheiro,
    val vencimento: LocalDate?,
    val situacao: Situacao,
    val compromisso: Compromisso? = null,
    val fatura: FaturaCalculada? = null,
)

data class ResumoContas(val itens: List<ItemConta>) {
    val total: Dinheiro get() = itens.somarDinheiro { it.valor }
    val pago: Dinheiro get() = itens.filter { it.situacao == Situacao.PAGO }.somarDinheiro { it.valor }
    val pendente: Dinheiro get() = total - pago
    val percentualQuitado: Int? get() = Percentual.de(pago, total)
    val vencidos: List<ItemConta> get() = itens.filter { it.situacao == Situacao.VENCIDO }
    val proximos: List<ItemConta> get() = itens.filter { it.situacao == Situacao.PROXIMO }
    val pendentes: List<ItemConta> get() = itens.filter { it.situacao != Situacao.PAGO }
}

data class ResumoMes(
    val plano: PlanoMes,
    /** Compromissos do mês (competência), incluindo os cobrados no cartão. */
    val compromissos: List<Compromisso>,
    val lazer: Envelope,
    val itensLazer: List<ResumoItemLazer>,
    val avulsos: Envelope,
    val investimento: ResumoInvestimento,
    val contas: ResumoContas,
) {
    val mes: YearMonth get() = plano.mes
    val renda: Dinheiro get() = plano.renda

    val totalEssenciais: Dinheiro get() = totalDoGrupo(Grupo.ESSENCIAL)
    val totalNaoEssenciais: Dinheiro get() = totalDoGrupo(Grupo.NAO_ESSENCIAL)
    val totalOutros: Dinheiro get() = totalDoGrupo(Grupo.OUTRO)
    val totalCompromissos: Dinheiro get() = compromissos.somarDinheiro { it.valorEfetivo }
    val compromissosPlanejados: Dinheiro get() = compromissos.somarDinheiro { it.valorPlanejado }

    fun totalDoGrupo(g: Grupo): Dinheiro = compromissos.filter { it.grupo == g }.somarDinheiro { it.valorEfetivo }

    /** Tudo o que o planejamento reserva da renda do mês. */
    val totalAlocado: Dinheiro
        get() = totalCompromissos + lazer.limite + avulsos.limite + investimento.meta

    /**
     * Sobra do planejamento (o "Valor Restante" da planilha):
     *   renda − compromissos − orçamento de lazer − limite de avulsos − meta de investimento
     */
    val sobraPlanejada: Dinheiro get() = renda - totalAlocado

    /**
     * Sobra considerando o que já aconteceu: estouros e aportes acima da meta saem da sobra.
     * O que foi coberto pela reserva NÃO sai da renda do mês (já foi separado em meses anteriores).
     */
    val sobraAtual: Dinheiro
        get() = sobraPlanejada - lazer.excesso - avulsos.excesso - investimento.acimaDaMeta
}

object Calculadora {
    fun resumir(
        plano: PlanoMes,
        compromissos: List<Compromisso>,
        itensLazer: List<ItemLazer>,
        lancamentosDoMes: List<Lancamento>,
        aportesDoMes: List<Aporte>,
        movimentosReserva: List<MovimentoReserva>,
        faturas: List<FaturaCalculada>,
        hoje: LocalDate,
    ): ResumoMes {
        val mes = plano.mes
        val doMes = lancamentosDoMes.filter { it.mes == mes }
        val coberturas = movimentosReserva.filter { it.tipo == TipoMovimentoReserva.SAIDA_COBERTURA && it.mesReferencia == mes }

        val gastosLazer = doMes.filter { it.destino == Destino.LAZER }
        val itens = itensLazer.filter { it.mes == mes }.map { item ->
            ResumoItemLazer(item, Envelope(item.limite, gastosLazer.filter { it.itemLazerId == item.id }.somarDinheiro { it.valor }))
        }
        val lazer = Envelope(
            limite = itens.somarDinheiro { it.item.limite },
            gasto = gastosLazer.somarDinheiro { it.valor },
            coberturaReserva = coberturas.filter { it.envelope == Destino.LAZER }.somarDinheiro { it.valor },
        )
        val avulsos = Envelope(
            limite = plano.limiteAvulsos,
            gasto = doMes.filter { it.destino == Destino.AVULSO }.somarDinheiro { it.valor },
            coberturaReserva = coberturas.filter { it.envelope == Destino.AVULSO }.somarDinheiro { it.valor },
        )
        val investimento = ResumoInvestimento(
            plano.metaInvestimento,
            aportesDoMes.filter { it.mes == mes }.somarDinheiro { it.valor },
        )
        val compDoMes = compromissos.filter { it.mes == mes }
        return ResumoMes(
            plano = plano,
            compromissos = compDoMes,
            lazer = lazer,
            itensLazer = itens,
            avulsos = avulsos,
            investimento = investimento,
            contas = contasDoMes(compDoMes, faturas.filter { it.mesVencimento == mes }, hoje),
        )
    }

    /**
     * Visão de caixa: compromissos pagos direto da conta + faturas que vencem no mês.
     * Compromissos cobrados no cartão NÃO entram aqui separadamente — eles estão dentro da fatura.
     */
    fun contasDoMes(compromissos: List<Compromisso>, faturas: List<FaturaCalculada>, hoje: LocalDate): ResumoContas {
        val diretos = compromissos.filter { !it.noCartao }.map {
            ItemConta(TipoConta.COMPROMISSO, it.nome, it.valorEfetivo, it.vencimento, it.situacao(hoje), compromisso = it)
        }
        val itensFatura = faturas.filter { it.temConteudo }.map {
            ItemConta(
                TipoConta.FATURA, "Fatura ${it.cartao.nome}", it.valorDevido, it.vencimento,
                Datas.situacao(it.pago, it.vencimento, hoje), fatura = it,
            )
        }
        return ResumoContas((diretos + itensFatura).sortedWith(compareBy({ it.vencimento ?: LocalDate.MAX }, { it.nome })))
    }
}
