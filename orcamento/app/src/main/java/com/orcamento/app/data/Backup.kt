package com.orcamento.app.data

import android.content.Context
import android.net.Uri
import com.orcamento.core.Datas
import com.orcamento.core.Dinheiro
import java.io.File

/**
 * Backup = cópia do arquivo do banco (inclui tudo: planos, recorrentes, gastos, reserva…).
 * Exportação CSV = só os gastos, para abrir em Excel/Planilhas.
 */
object Backup {
    private val CABECALHO_SQLITE = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun arquivoDoBanco(context: Context): File = context.getDatabasePath(BancoDeDados.NOME)

    /** Copia o banco para o destino escolhido pela pessoa. O banco precisa estar fechado ou ocioso. */
    fun exportar(context: Context, destino: Uri) {
        val origem = arquivoDoBanco(context)
        context.contentResolver.openOutputStream(destino, "w")?.use { saida ->
            origem.inputStream().use { it.copyTo(saida) }
        } ?: error("Não foi possível abrir o arquivo de destino.")
    }

    /** Valida e copia o arquivo escolhido por cima do banco atual. Chame com o banco FECHADO. */
    fun substituirBanco(context: Context, origem: Uri) {
        val temporario = File(context.cacheDir, "restauracao.db")
        context.contentResolver.openInputStream(origem)?.use { entrada ->
            temporario.outputStream().use { entrada.copyTo(it) }
        } ?: error("Não foi possível ler o arquivo escolhido.")
        val cabecalho = temporario.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        if (!cabecalho.contentEquals(CABECALHO_SQLITE)) {
            temporario.delete()
            throw IllegalArgumentException("O arquivo escolhido não é um backup deste app.")
        }
        val destino = arquivoDoBanco(context)
        File(destino.path + "-journal").delete()
        File(destino.path + "-wal").delete()
        File(destino.path + "-shm").delete()
        temporario.copyTo(destino, overwrite = true)
        temporario.delete()
    }

    /** Gera o CSV (separado por ponto e vírgula, padrão do Excel em português). */
    fun csvDeGastos(dao: FinancasDao): String {
        val cartoes = dao.cartoes().associate { it.id to it.nome }
        val itens = mutableMapOf<Long, String>()
        val linhas = StringBuilder("Data;Descrição;Valor;Destino;Item de lazer;Categoria;Pagamento;Observações\n")
        for (e in dao.todosLancamentos()) {
            val l = e.paraDominio()
            val item = l.itemLazerId?.let { id -> itens.getOrPut(id) { dao.itemLazer(id)?.nome ?: "" } } ?: ""
            linhas.append(
                listOf(
                    Datas.formatar(l.data), l.descricao, Dinheiro(l.valor.centavos).paraCampo(), l.destino.rotulo, item,
                    l.categoria ?: "", l.cartaoId?.let { cartoes[it] ?: "Cartão" } ?: "Pix/débito/dinheiro", l.observacoes ?: "",
                ).joinToString(";") { campo -> "\"" + campo.replace("\"", "\"\"") + "\"" },
            ).append('\n')
        }
        return linhas.toString()
    }

    fun escreverTexto(context: Context, destino: Uri, texto: String) {
        context.contentResolver.openOutputStream(destino, "w")?.use { saida ->
            // BOM para o Excel reconhecer acentos
            saida.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            saida.write(texto.toByteArray(Charsets.UTF_8))
        } ?: error("Não foi possível abrir o arquivo de destino.")
    }
}
