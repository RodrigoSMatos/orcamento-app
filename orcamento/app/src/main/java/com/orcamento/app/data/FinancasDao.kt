package com.orcamento.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Acesso ao banco. Métodos síncronos de propósito: são chamados só fora da thread principal
 * (ver PrincipalViewModel) e dentro de transações do Room.
 * Inserções usam ABORT: uma violação de índice único gera erro e desfaz a transação inteira
 * em vez de apagar silenciosamente uma linha existente.
 */
@Dao
interface FinancasDao {
    // Plano padrão e planos por mês
    @Query("SELECT * FROM plano_padrao WHERE id = 1") fun planoPadrao(): PlanoPadraoEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun salvarPlanoPadrao(p: PlanoPadraoEntity)
    @Query("SELECT * FROM planos_mes WHERE mes = :mes") fun planoMes(mes: Int): PlanoMesEntity?
    @Query("SELECT * FROM planos_mes ORDER BY mes") fun planosMes(): List<PlanoMesEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun salvarPlanoMes(p: PlanoMesEntity)

    // Cartões
    @Query("SELECT * FROM cartoes ORDER BY nome") fun cartoes(): List<CartaoEntity>
    @Query("SELECT * FROM cartoes WHERE id = :id") fun cartao(id: Long): CartaoEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun inserirCartao(c: CartaoEntity): Long
    @Update fun atualizarCartao(c: CartaoEntity)
    @Query("DELETE FROM cartoes WHERE id = :id") fun excluirCartao(id: Long)

    // Modelos recorrentes
    @Query("SELECT * FROM modelos ORDER BY nome") fun modelos(): List<ModeloEntity>
    @Query("SELECT * FROM modelos WHERE id = :id") fun modelo(id: Long): ModeloEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun inserirModelo(m: ModeloEntity): Long
    @Update fun atualizarModelo(m: ModeloEntity)
    @Query("DELETE FROM modelos WHERE id = :id") fun excluirModelo(id: Long)

    @Query("SELECT * FROM modelos_lazer ORDER BY nome") fun modelosLazer(): List<ModeloLazerEntity>
    @Query("SELECT * FROM modelos_lazer WHERE id = :id") fun modeloLazer(id: Long): ModeloLazerEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun inserirModeloLazer(m: ModeloLazerEntity): Long
    @Update fun atualizarModeloLazer(m: ModeloLazerEntity)
    @Query("DELETE FROM modelos_lazer WHERE id = :id") fun excluirModeloLazer(id: Long)

    // Compromissos
    @Query("SELECT * FROM compromissos WHERE mes = :mes ORDER BY vencimento, nome") fun compromissos(mes: Int): List<CompromissoEntity>
    @Query("SELECT * FROM compromissos WHERE mes BETWEEN :de AND :ate") fun compromissosEntre(de: Int, ate: Int): List<CompromissoEntity>
    @Query("SELECT * FROM compromissos WHERE modeloId = :modeloId") fun compromissosDoModelo(modeloId: Long): List<CompromissoEntity>
    @Query("SELECT * FROM compromissos WHERE id = :id") fun compromisso(id: Long): CompromissoEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun inserirCompromisso(c: CompromissoEntity): Long
    @Update fun atualizarCompromisso(c: CompromissoEntity)
    @Query("DELETE FROM compromissos WHERE id = :id") fun excluirCompromisso(id: Long)

    // Itens de lazer
    @Query("SELECT * FROM itens_lazer WHERE mes = :mes ORDER BY nome") fun itensLazer(mes: Int): List<ItemLazerEntity>
    @Query("SELECT * FROM itens_lazer WHERE modeloId = :modeloId") fun itensLazerDoModelo(modeloId: Long): List<ItemLazerEntity>
    @Query("SELECT * FROM itens_lazer WHERE id = :id") fun itemLazer(id: Long): ItemLazerEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun inserirItemLazer(i: ItemLazerEntity): Long
    @Update fun atualizarItemLazer(i: ItemLazerEntity)
    @Query("DELETE FROM itens_lazer WHERE id = :id") fun excluirItemLazer(id: Long)

    // Lançamentos
    @Query("SELECT * FROM lancamentos WHERE data BETWEEN :de AND :ate ORDER BY data DESC, id DESC")
    fun lancamentos(de: Long, ate: Long): List<LancamentoEntity>
    @Query("SELECT * FROM lancamentos WHERE id = :id") fun lancamento(id: Long): LancamentoEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun inserirLancamento(l: LancamentoEntity): Long
    @Update fun atualizarLancamento(l: LancamentoEntity)
    @Query("DELETE FROM lancamentos WHERE id = :id") fun excluirLancamento(id: Long)
    @Query("SELECT COUNT(*) FROM lancamentos WHERE itemLazerId = :itemId") fun contarLancamentosDoItem(itemId: Long): Int
    @Query("SELECT * FROM lancamentos WHERE compraId = :compraId ORDER BY parcela") fun lancamentosDaCompra(compraId: Long): List<LancamentoEntity>

    @Query(
        "SELECT * FROM lancamentos WHERE descricao LIKE '%' || :texto || '%' OR categoria LIKE '%' || :texto || '%' " +
            "ORDER BY data DESC, id DESC LIMIT 300",
    )
    fun buscarLancamentos(texto: String): List<LancamentoEntity>

    @Query("SELECT descricao FROM lancamentos GROUP BY lower(descricao) ORDER BY COUNT(*) DESC, MAX(data) DESC LIMIT :limite")
    fun descricoesFrequentes(limite: Int): List<String>

    @Query(
        "SELECT categoria FROM lancamentos WHERE categoria IS NOT NULL AND categoria != '' " +
            "GROUP BY lower(categoria) ORDER BY COUNT(*) DESC LIMIT :limite",
    )
    fun categoriasFrequentes(limite: Int): List<String>

    @Query("SELECT * FROM lancamentos WHERE lower(descricao) = lower(:descricao) ORDER BY data DESC, id DESC LIMIT 1")
    fun ultimoComDescricao(descricao: String): LancamentoEntity?

    @Query("SELECT * FROM lancamentos ORDER BY id DESC LIMIT 1") fun ultimoLancamento(): LancamentoEntity?

    @Query("SELECT * FROM lancamentos ORDER BY data, id") fun todosLancamentos(): List<LancamentoEntity>

    @Query(
        "SELECT (SELECT COUNT(*) FROM lancamentos WHERE cartaoId = :id) + (SELECT COUNT(*) FROM compromissos WHERE cartaoId = :id) + " +
            "(SELECT COUNT(*) FROM modelos WHERE cartaoId = :id) + (SELECT COUNT(*) FROM faturas WHERE cartaoId = :id)",
    )
    fun contarUsosDoCartao(id: Long): Int

    // Aportes
    @Query("SELECT * FROM aportes WHERE data BETWEEN :de AND :ate ORDER BY data DESC, id DESC") fun aportes(de: Long, ate: Long): List<AporteEntity>
    @Query("SELECT * FROM aportes WHERE id = :id") fun aporte(id: Long): AporteEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun inserirAporte(a: AporteEntity): Long
    @Update fun atualizarAporte(a: AporteEntity)
    @Query("DELETE FROM aportes WHERE id = :id") fun excluirAporte(id: Long)
    @Query("SELECT COALESCE(SUM(valor), 0) FROM aportes") fun totalAportado(): Long

    // Reserva
    @Query("SELECT * FROM movimentos_reserva ORDER BY data, id") fun movimentosReserva(): List<MovimentoReservaEntity>
    @Query("SELECT * FROM movimentos_reserva WHERE id = :id") fun movimentoReserva(id: Long): MovimentoReservaEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun inserirMovimento(m: MovimentoReservaEntity): Long
    @Update fun atualizarMovimento(m: MovimentoReservaEntity)
    @Query("DELETE FROM movimentos_reserva WHERE id = :id") fun excluirMovimento(id: Long)

    // Faturas
    @Query("SELECT * FROM faturas WHERE cartaoId = :cartaoId AND mesVencimento = :mes") fun fatura(cartaoId: Long, mes: Int): FaturaEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun inserirFatura(f: FaturaEntity): Long
    @Update fun atualizarFatura(f: FaturaEntity)
}
