package com.orcamento.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Tabelas do SQLite. Convenções:
 *  - dinheiro em centavos (Long)
 *  - mês como inteiro AAAAMM (ex.: 202610)
 *  - data como dias desde 01/01/1970 (LocalDate.toEpochDay)
 *  - enums pelo nome (String)
 */

@Entity(tableName = "plano_padrao")
data class PlanoPadraoEntity(
    @PrimaryKey val id: Int = 1,
    val renda: Long,
    val limiteAvulsos: Long,
    val metaInvestimento: Long,
)

@Entity(tableName = "planos_mes")
data class PlanoMesEntity(
    @PrimaryKey val mes: Int,
    val renda: Long,
    val limiteAvulsos: Long,
    val metaInvestimento: Long,
    val fechado: Boolean,
    val fechadoEm: Long?,
)

@Entity(tableName = "cartoes")
data class CartaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val diaFechamento: Int,
    val diaVencimento: Int,
    val ativo: Boolean,
)

@Entity(tableName = "modelos")
data class ModeloEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val valor: Long,
    val grupo: String,
    val categoria: String?,
    val diaVencimento: Int?,
    val frequencia: String,
    val mesInicio: Int,
    val mesFim: Int?,
    val cartaoId: Long?,
    val ativo: Boolean,
    val observacoes: String?,
)

@Entity(tableName = "modelos_lazer")
data class ModeloLazerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val valor: Long,
    val ativo: Boolean,
)

/** O índice único (mes, modeloId) impede, no próprio banco, gerar a mesma recorrente duas vezes no mês. */
@Entity(
    tableName = "compromissos",
    indices = [Index(value = ["mes", "modeloId"], unique = true), Index("mes")],
)
data class CompromissoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mes: Int,
    val nome: String,
    val valorPlanejado: Long,
    val grupo: String,
    val categoria: String?,
    val vencimento: Long?,
    val cartaoId: Long?,
    val modeloId: Long?,
    val pago: Boolean,
    val dataPagamento: Long?,
    val valorPago: Long?,
    val editadoManualmente: Boolean,
    val observacoes: String?,
)

@Entity(
    tableName = "itens_lazer",
    indices = [Index(value = ["mes", "modeloId"], unique = true), Index("mes")],
)
data class ItemLazerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mes: Int,
    val nome: String,
    val limite: Long,
    val modeloId: Long?,
    val editadoManualmente: Boolean,
)

@Entity(tableName = "lancamentos", indices = [Index("data"), Index("itemLazerId")])
data class LancamentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,
    val descricao: String,
    val valor: Long,
    val destino: String,
    val itemLazerId: Long?,
    val categoria: String?,
    val cartaoId: Long?,
    val observacoes: String?,
    // Versão 2 do banco: compras parceladas
    val compraId: Long? = null,
    val parcela: Int? = null,
    val totalParcelas: Int? = null,
    val mesFatura: Int? = null,
)

@Entity(tableName = "aportes", indices = [Index("data")])
data class AporteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,
    val valor: Long,
    val descricao: String?,
)

@Entity(tableName = "movimentos_reserva")
data class MovimentoReservaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,
    val tipo: String,
    val valor: Long,
    val descricao: String,
    val motivo: String?,
    val mesReferencia: Int?,
    val envelope: String?,
)

@Entity(tableName = "faturas", indices = [Index(value = ["cartaoId", "mesVencimento"], unique = true)])
data class FaturaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cartaoId: Long,
    val mesVencimento: Int,
    val ajuste: Long,
    val pago: Boolean,
    val dataPagamento: Long?,
    val valorPago: Long?,
    val observacoes: String?,
)
