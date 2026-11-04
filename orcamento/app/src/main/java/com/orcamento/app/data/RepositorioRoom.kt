package com.orcamento.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.orcamento.core.Aporte
import com.orcamento.core.Cartao
import com.orcamento.core.Compromisso
import com.orcamento.core.Destino
import com.orcamento.core.Dinheiro
import com.orcamento.core.Fatura
import com.orcamento.core.Frequencia
import com.orcamento.core.Grupo
import com.orcamento.core.ItemLazer
import com.orcamento.core.Lancamento
import com.orcamento.core.ModeloLazer
import com.orcamento.core.ModeloRecorrente
import com.orcamento.core.MovimentoReserva
import com.orcamento.core.PlanoMes
import com.orcamento.core.PlanoPadrao
import com.orcamento.core.Repositorio
import com.orcamento.core.TipoMovimentoReserva
import java.time.LocalDate
import java.time.YearMonth

@Database(
    entities = [
        PlanoPadraoEntity::class, PlanoMesEntity::class, CartaoEntity::class, ModeloEntity::class,
        ModeloLazerEntity::class, CompromissoEntity::class, ItemLazerEntity::class, LancamentoEntity::class,
        AporteEntity::class, MovimentoReservaEntity::class, FaturaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BancoDeDados : RoomDatabase() {
    abstract fun dao(): FinancasDao

    companion object {
        const val NOME = "orcamento.db"

        fun abrir(context: Context): BancoDeDados =
            Room.databaseBuilder(context, BancoDeDados::class.java, NOME)
                // Sem WAL: o arquivo .db fica sempre completo, o que torna o backup por cópia seguro.
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
    }
}

// ───────── Conversões ─────────

internal fun YearMonth.paraInt(): Int = year * 100 + monthValue
internal fun Int.paraMes(): YearMonth = YearMonth.of(this / 100, this % 100)
internal fun LocalDate.paraLong(): Long = toEpochDay()
internal fun Long.paraData(): LocalDate = LocalDate.ofEpochDay(this)
private fun Long.din() = Dinheiro(this)

/** Implementação do repositório do núcleo usando Room. */
class RepositorioRoom(private val banco: BancoDeDados) : Repositorio {
    private val dao = banco.dao()

    override fun <T> transacao(bloco: () -> T): T = banco.runInTransaction<T> { bloco() }

    override fun planoPadrao(): PlanoPadrao =
        dao.planoPadrao()?.let { PlanoPadrao(it.renda.din(), it.limiteAvulsos.din(), it.metaInvestimento.din()) } ?: PlanoPadrao()

    override fun salvarPlanoPadrao(p: PlanoPadrao) =
        dao.salvarPlanoPadrao(PlanoPadraoEntity(1, p.renda.centavos, p.limiteAvulsos.centavos, p.metaInvestimento.centavos))

    override fun planoMes(mes: YearMonth) = dao.planoMes(mes.paraInt())?.paraDominio()
    override fun salvarPlanoMes(p: PlanoMes) = dao.salvarPlanoMes(
        PlanoMesEntity(p.mes.paraInt(), p.renda.centavos, p.limiteAvulsos.centavos, p.metaInvestimento.centavos, p.fechado, p.fechadoEm?.paraLong()),
    )
    override fun planosMes() = dao.planosMes().map { it.paraDominio() }

    override fun cartoes() = dao.cartoes().map { it.paraDominio() }
    override fun cartao(id: Long) = dao.cartao(id)?.paraDominio()
    override fun salvarCartao(c: Cartao): Long {
        val e = CartaoEntity(c.id, c.nome, c.diaFechamento, c.diaVencimento, c.ativo)
        return if (c.id == 0L) dao.inserirCartao(e) else { dao.atualizarCartao(e); c.id }
    }
    override fun excluirCartao(id: Long) = dao.excluirCartao(id)

    override fun modelos() = dao.modelos().map { it.paraDominio() }
    override fun modelo(id: Long) = dao.modelo(id)?.paraDominio()
    override fun salvarModelo(m: ModeloRecorrente): Long {
        val e = ModeloEntity(
            m.id, m.nome, m.valor.centavos, m.grupo.name, m.categoria, m.diaVencimento, m.frequencia.name,
            m.mesInicio.paraInt(), m.mesFim?.paraInt(), m.cartaoId, m.ativo, m.observacoes,
        )
        return if (m.id == 0L) dao.inserirModelo(e) else { dao.atualizarModelo(e); m.id }
    }
    override fun excluirModelo(id: Long) = dao.excluirModelo(id)

    override fun modelosLazer() = dao.modelosLazer().map { it.paraDominio() }
    override fun modeloLazer(id: Long) = dao.modeloLazer(id)?.paraDominio()
    override fun salvarModeloLazer(m: ModeloLazer): Long {
        val e = ModeloLazerEntity(m.id, m.nome, m.valor.centavos, m.ativo)
        return if (m.id == 0L) dao.inserirModeloLazer(e) else { dao.atualizarModeloLazer(e); m.id }
    }
    override fun excluirModeloLazer(id: Long) = dao.excluirModeloLazer(id)

    override fun compromissos(mes: YearMonth) = dao.compromissos(mes.paraInt()).map { it.paraDominio() }
    override fun compromissosEntre(de: YearMonth, ate: YearMonth) = dao.compromissosEntre(de.paraInt(), ate.paraInt()).map { it.paraDominio() }
    override fun compromissosDoModelo(modeloId: Long) = dao.compromissosDoModelo(modeloId).map { it.paraDominio() }
    override fun compromisso(id: Long) = dao.compromisso(id)?.paraDominio()
    override fun salvarCompromisso(c: Compromisso): Long {
        val e = CompromissoEntity(
            c.id, c.mes.paraInt(), c.nome, c.valorPlanejado.centavos, c.grupo.name, c.categoria, c.vencimento?.paraLong(),
            c.cartaoId, c.modeloId, c.pago, c.dataPagamento?.paraLong(), c.valorPago?.centavos, c.editadoManualmente, c.observacoes,
        )
        return if (c.id == 0L) dao.inserirCompromisso(e) else { dao.atualizarCompromisso(e); c.id }
    }
    override fun excluirCompromisso(id: Long) = dao.excluirCompromisso(id)

    override fun itensLazer(mes: YearMonth) = dao.itensLazer(mes.paraInt()).map { it.paraDominio() }
    override fun itensLazerDoModelo(modeloId: Long) = dao.itensLazerDoModelo(modeloId).map { it.paraDominio() }
    override fun itemLazer(id: Long) = dao.itemLazer(id)?.paraDominio()
    override fun salvarItemLazer(i: ItemLazer): Long {
        val e = ItemLazerEntity(i.id, i.mes.paraInt(), i.nome, i.limite.centavos, i.modeloId, i.editadoManualmente)
        return if (i.id == 0L) dao.inserirItemLazer(e) else { dao.atualizarItemLazer(e); i.id }
    }
    override fun excluirItemLazer(id: Long) = dao.excluirItemLazer(id)

    override fun lancamentos(de: LocalDate, ate: LocalDate) = dao.lancamentos(de.paraLong(), ate.paraLong()).map { it.paraDominio() }
    override fun lancamento(id: Long) = dao.lancamento(id)?.paraDominio()
    override fun salvarLancamento(l: Lancamento): Long {
        val e = LancamentoEntity(
            l.id, l.data.paraLong(), l.descricao, l.valor.centavos, l.destino.name, l.itemLazerId, l.categoria, l.cartaoId, l.observacoes,
        )
        return if (l.id == 0L) dao.inserirLancamento(e) else { dao.atualizarLancamento(e); l.id }
    }
    override fun excluirLancamento(id: Long) = dao.excluirLancamento(id)
    override fun contarLancamentosDoItem(itemLazerId: Long) = dao.contarLancamentosDoItem(itemLazerId)
    override fun contarUsosDoCartao(cartaoId: Long) = dao.contarUsosDoCartao(cartaoId)

    override fun aportes(de: LocalDate, ate: LocalDate) = dao.aportes(de.paraLong(), ate.paraLong()).map { it.paraDominio() }
    override fun aporte(id: Long) = dao.aporte(id)?.paraDominio()
    override fun salvarAporte(a: Aporte): Long {
        val e = AporteEntity(a.id, a.data.paraLong(), a.valor.centavos, a.descricao)
        return if (a.id == 0L) dao.inserirAporte(e) else { dao.atualizarAporte(e); a.id }
    }
    override fun excluirAporte(id: Long) = dao.excluirAporte(id)
    override fun totalAportado() = Dinheiro(dao.totalAportado())

    override fun movimentosReserva() = dao.movimentosReserva().map { it.paraDominio() }
    override fun movimentoReserva(id: Long) = dao.movimentoReserva(id)?.paraDominio()
    override fun salvarMovimentoReserva(m: MovimentoReserva): Long {
        val e = MovimentoReservaEntity(
            m.id, m.data.paraLong(), m.tipo.name, m.valor.centavos, m.descricao, m.motivo, m.mesReferencia?.paraInt(), m.envelope?.name,
        )
        return if (m.id == 0L) dao.inserirMovimento(e) else { dao.atualizarMovimento(e); m.id }
    }
    override fun excluirMovimentoReserva(id: Long) = dao.excluirMovimento(id)

    override fun fatura(cartaoId: Long, mesVencimento: YearMonth) = dao.fatura(cartaoId, mesVencimento.paraInt())?.paraDominio()
    override fun salvarFatura(f: Fatura): Long {
        val e = FaturaEntity(
            f.id, f.cartaoId, f.mesVencimento.paraInt(), f.ajuste.centavos, f.pago, f.dataPagamento?.paraLong(), f.valorPago?.centavos, f.observacoes,
        )
        return if (f.id == 0L) dao.inserirFatura(e) else { dao.atualizarFatura(e); f.id }
    }
}

// ───────── Entidade → domínio ─────────

private fun PlanoMesEntity.paraDominio() =
    PlanoMes(mes.paraMes(), renda.din(), limiteAvulsos.din(), metaInvestimento.din(), fechado, fechadoEm?.paraData())

internal fun CartaoEntity.paraDominio() = Cartao(id, nome, diaFechamento, diaVencimento, ativo)

private fun ModeloEntity.paraDominio() = ModeloRecorrente(
    id, nome, valor.din(), Grupo.valueOf(grupo), categoria, diaVencimento, Frequencia.valueOf(frequencia),
    mesInicio.paraMes(), mesFim?.paraMes(), cartaoId, ativo, observacoes,
)

private fun ModeloLazerEntity.paraDominio() = ModeloLazer(id, nome, valor.din(), ativo)

private fun CompromissoEntity.paraDominio() = Compromisso(
    id, mes.paraMes(), nome, valorPlanejado.din(), Grupo.valueOf(grupo), categoria, vencimento?.paraData(),
    cartaoId, modeloId, pago, dataPagamento?.paraData(), valorPago?.din(), editadoManualmente, observacoes,
)

private fun ItemLazerEntity.paraDominio() = ItemLazer(id, mes.paraMes(), nome, limite.din(), modeloId, editadoManualmente)

internal fun LancamentoEntity.paraDominio() = Lancamento(
    id, data.paraData(), descricao, valor.din(), Destino.valueOf(destino), itemLazerId, categoria, cartaoId, observacoes,
)

private fun AporteEntity.paraDominio() = Aporte(id, data.paraData(), valor.din(), descricao)

private fun MovimentoReservaEntity.paraDominio() = MovimentoReserva(
    id, data.paraData(), TipoMovimentoReserva.valueOf(tipo), valor.din(), descricao, motivo,
    mesReferencia?.paraMes(), envelope?.let { Destino.valueOf(it) },
)

private fun FaturaEntity.paraDominio() = Fatura(
    id, cartaoId, mesVencimento.paraMes(), ajuste.din(), pago, dataPagamento?.paraData(), valorPago?.din(), observacoes,
)
