package com.orcamento.app

import android.app.Application
import android.net.Uri
import com.orcamento.app.data.Backup
import com.orcamento.app.data.BancoDeDados
import com.orcamento.app.data.FinancasDao
import com.orcamento.app.data.RepositorioRoom
import com.orcamento.core.ServicoFinanceiro
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors

/**
 * Ponto único de acesso aos dados. Tudo que toca o banco roda em [dispatcherBanco],
 * uma única thread: operações nunca se cruzam e o backup sempre copia um arquivo consistente.
 */
class OrcamentoApp : Application() {
    val dispatcherBanco = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private lateinit var banco: BancoDeDados
    lateinit var dao: FinancasDao
        private set
    lateinit var servico: ServicoFinanceiro
        private set

    override fun onCreate() {
        super.onCreate()
        abrirBanco()
    }

    private fun abrirBanco() {
        banco = BancoDeDados.abrir(this)
        dao = banco.dao()
        servico = ServicoFinanceiro(RepositorioRoom(banco))
    }

    /** Chamar dentro de [dispatcherBanco]. */
    fun exportarBackup(destino: Uri) {
        dao.planosMes() // garante que o arquivo exista
        banco.close()
        try {
            Backup.exportar(this, destino)
        } finally {
            abrirBanco()
        }
    }

    /** Troca o banco pelo backup. Se o arquivo não abrir, volta o banco anterior. Chamar dentro de [dispatcherBanco]. */
    fun restaurarBackup(origem: Uri) {
        val atual = Backup.arquivoDoBanco(this)
        val seguranca = File(cacheDir, "antes_da_restauracao.db")
        dao.planosMes() // garante que o arquivo atual exista
        banco.close()
        atual.copyTo(seguranca, overwrite = true)
        try {
            Backup.substituirBanco(this, origem)
            abrirBanco()
            dao.planosMes() // força abrir e validar o esquema
        } catch (e: Exception) {
            runCatching { banco.close() }
            seguranca.copyTo(atual, overwrite = true)
            abrirBanco()
            throw IllegalArgumentException(e.message ?: "Backup inválido.")
        } finally {
            seguranca.delete()
        }
    }
}
