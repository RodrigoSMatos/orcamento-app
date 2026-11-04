package com.orcamento.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * Onde os dados ficam guardados. O app implementa com Room (SQLite no celular);
 * os testes usam uma versão em memória. Métodos `salvar*` inserem quando id = 0
 * e atualizam caso contrário, devolvendo o id.
 */
interface Repositorio {
    /** Executa tudo ou nada: se algo falhar no meio, nenhuma alteração fica gravada. */
    fun <T> transacao(bloco: () -> T): T

    fun planoPadrao(): PlanoPadrao
    fun salvarPlanoPadrao(p: PlanoPadrao)

    fun planoMes(mes: YearMonth): PlanoMes?
    fun salvarPlanoMes(p: PlanoMes)
    fun planosMes(): List<PlanoMes>

    fun cartoes(): List<Cartao>
    fun cartao(id: Long): Cartao?
    fun salvarCartao(c: Cartao): Long
    fun excluirCartao(id: Long)

    fun modelos(): List<ModeloRecorrente>
    fun modelo(id: Long): ModeloRecorrente?
    fun salvarModelo(m: ModeloRecorrente): Long
    fun excluirModelo(id: Long)

    fun modelosLazer(): List<ModeloLazer>
    fun modeloLazer(id: Long): ModeloLazer?
    fun salvarModeloLazer(m: ModeloLazer): Long
    fun excluirModeloLazer(id: Long)

    fun compromissos(mes: YearMonth): List<Compromisso>
    fun compromissosEntre(de: YearMonth, ate: YearMonth): List<Compromisso>
    fun compromissosDoModelo(modeloId: Long): List<Compromisso>
    fun compromisso(id: Long): Compromisso?
    fun salvarCompromisso(c: Compromisso): Long
    fun excluirCompromisso(id: Long)

    fun itensLazer(mes: YearMonth): List<ItemLazer>
    fun itensLazerDoModelo(modeloId: Long): List<ItemLazer>
    fun itemLazer(id: Long): ItemLazer?
    fun salvarItemLazer(i: ItemLazer): Long
    fun excluirItemLazer(id: Long)

    fun lancamentos(de: LocalDate, ate: LocalDate): List<Lancamento>
    fun lancamento(id: Long): Lancamento?
    fun salvarLancamento(l: Lancamento): Long
    fun excluirLancamento(id: Long)
    fun contarLancamentosDoItem(itemLazerId: Long): Int
    fun contarUsosDoCartao(cartaoId: Long): Int

    fun aportes(de: LocalDate, ate: LocalDate): List<Aporte>
    fun aporte(id: Long): Aporte?
    fun salvarAporte(a: Aporte): Long
    fun excluirAporte(id: Long)
    fun totalAportado(): Dinheiro

    fun movimentosReserva(): List<MovimentoReserva>
    fun movimentoReserva(id: Long): MovimentoReserva?
    fun salvarMovimentoReserva(m: MovimentoReserva): Long
    fun excluirMovimentoReserva(id: Long)

    fun fatura(cartaoId: Long, mesVencimento: YearMonth): Fatura?
    fun salvarFatura(f: Fatura): Long
}
