package com.orcamento.core

import java.time.LocalDate
import java.time.YearMonth

/** Repositório para testes, com transação de verdade (desfaz tudo se der erro). */
class RepositorioEmMemoria : Repositorio {
    private class Dados(
        var padrao: PlanoPadrao = PlanoPadrao(),
        val planos: MutableMap<YearMonth, PlanoMes> = mutableMapOf(),
        val cartoes: MutableMap<Long, Cartao> = mutableMapOf(),
        val modelos: MutableMap<Long, ModeloRecorrente> = mutableMapOf(),
        val modelosLazer: MutableMap<Long, ModeloLazer> = mutableMapOf(),
        val compromissos: MutableMap<Long, Compromisso> = mutableMapOf(),
        val itens: MutableMap<Long, ItemLazer> = mutableMapOf(),
        val lancamentos: MutableMap<Long, Lancamento> = mutableMapOf(),
        val aportes: MutableMap<Long, Aporte> = mutableMapOf(),
        val movimentos: MutableMap<Long, MovimentoReserva> = mutableMapOf(),
        val faturas: MutableMap<Long, Fatura> = mutableMapOf(),
        var proximoId: Long = 1,
    ) {
        fun copia() = Dados(
            padrao, planos.toMutableMap(), cartoes.toMutableMap(), modelos.toMutableMap(), modelosLazer.toMutableMap(),
            compromissos.toMutableMap(), itens.toMutableMap(), lancamentos.toMutableMap(), aportes.toMutableMap(),
            movimentos.toMutableMap(), faturas.toMutableMap(), proximoId,
        )
    }

    private var d = Dados()
    private var profundidade = 0

    override fun <T> transacao(bloco: () -> T): T {
        val antes = if (profundidade == 0) d.copia() else null
        profundidade++
        try {
            return bloco()
        } catch (e: Throwable) {
            if (antes != null) d = antes
            throw e
        } finally {
            profundidade--
        }
    }

    private fun novoId() = d.proximoId++
    private fun idOuNovo(id: Long) = if (id == 0L) novoId() else id

    override fun planoPadrao() = d.padrao
    override fun salvarPlanoPadrao(p: PlanoPadrao) { d.padrao = p }
    override fun planoMes(mes: YearMonth) = d.planos[mes]
    override fun salvarPlanoMes(p: PlanoMes) { d.planos[p.mes] = p }
    override fun planosMes() = d.planos.values.toList()

    override fun cartoes() = d.cartoes.values.toList()
    override fun cartao(id: Long) = d.cartoes[id]
    override fun salvarCartao(c: Cartao): Long { val id = idOuNovo(c.id); d.cartoes[id] = c.copy(id = id); return id }
    override fun excluirCartao(id: Long) { d.cartoes.remove(id) }

    override fun modelos() = d.modelos.values.toList()
    override fun modelo(id: Long) = d.modelos[id]
    override fun salvarModelo(m: ModeloRecorrente): Long { val id = idOuNovo(m.id); d.modelos[id] = m.copy(id = id); return id }
    override fun excluirModelo(id: Long) { d.modelos.remove(id) }

    override fun modelosLazer() = d.modelosLazer.values.toList()
    override fun modeloLazer(id: Long) = d.modelosLazer[id]
    override fun salvarModeloLazer(m: ModeloLazer): Long { val id = idOuNovo(m.id); d.modelosLazer[id] = m.copy(id = id); return id }
    override fun excluirModeloLazer(id: Long) { d.modelosLazer.remove(id) }

    override fun compromissos(mes: YearMonth) = d.compromissos.values.filter { it.mes == mes }
    override fun compromissosEntre(de: YearMonth, ate: YearMonth) = d.compromissos.values.filter { it.mes in de..ate }
    override fun compromissosDoModelo(modeloId: Long) = d.compromissos.values.filter { it.modeloId == modeloId }
    override fun compromisso(id: Long) = d.compromissos[id]
    override fun salvarCompromisso(c: Compromisso): Long { val id = idOuNovo(c.id); d.compromissos[id] = c.copy(id = id); return id }
    override fun excluirCompromisso(id: Long) { d.compromissos.remove(id) }

    override fun itensLazer(mes: YearMonth) = d.itens.values.filter { it.mes == mes }
    override fun itensLazerDoModelo(modeloId: Long) = d.itens.values.filter { it.modeloId == modeloId }
    override fun itemLazer(id: Long) = d.itens[id]
    override fun salvarItemLazer(i: ItemLazer): Long { val id = idOuNovo(i.id); d.itens[id] = i.copy(id = id); return id }
    override fun excluirItemLazer(id: Long) { d.itens.remove(id) }

    override fun lancamentos(de: LocalDate, ate: LocalDate) = d.lancamentos.values.filter { !it.data.isBefore(de) && !it.data.isAfter(ate) }
    override fun lancamento(id: Long) = d.lancamentos[id]
    override fun salvarLancamento(l: Lancamento): Long { val id = idOuNovo(l.id); d.lancamentos[id] = l.copy(id = id); return id }
    override fun excluirLancamento(id: Long) { d.lancamentos.remove(id) }
    override fun lancamentosDaCompra(compraId: Long) = d.lancamentos.values.filter { it.compraId == compraId }.sortedBy { it.parcela }
    override fun contarLancamentosDoItem(itemLazerId: Long) = d.lancamentos.values.count { it.itemLazerId == itemLazerId }
    override fun contarUsosDoCartao(cartaoId: Long) =
        d.lancamentos.values.count { it.cartaoId == cartaoId } + d.compromissos.values.count { it.cartaoId == cartaoId } +
            d.modelos.values.count { it.cartaoId == cartaoId } + d.faturas.values.count { it.cartaoId == cartaoId }

    override fun aportes(de: LocalDate, ate: LocalDate) = d.aportes.values.filter { !it.data.isBefore(de) && !it.data.isAfter(ate) }
    override fun aporte(id: Long) = d.aportes[id]
    override fun salvarAporte(a: Aporte): Long { val id = idOuNovo(a.id); d.aportes[id] = a.copy(id = id); return id }
    override fun excluirAporte(id: Long) { d.aportes.remove(id) }
    override fun totalAportado() = d.aportes.values.somarDinheiro { it.valor }

    override fun movimentosReserva() = d.movimentos.values.toList()
    override fun movimentoReserva(id: Long) = d.movimentos[id]
    override fun salvarMovimentoReserva(m: MovimentoReserva): Long { val id = idOuNovo(m.id); d.movimentos[id] = m.copy(id = id); return id }
    override fun excluirMovimentoReserva(id: Long) { d.movimentos.remove(id) }

    override fun fatura(cartaoId: Long, mesVencimento: YearMonth) = d.faturas.values.firstOrNull { it.cartaoId == cartaoId && it.mesVencimento == mesVencimento }
    override fun salvarFatura(f: Fatura): Long { val id = idOuNovo(f.id); d.faturas[id] = f.copy(id = id); return id }
}
