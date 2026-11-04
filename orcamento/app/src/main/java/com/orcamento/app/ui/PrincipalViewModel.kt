package com.orcamento.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orcamento.app.OrcamentoApp
import com.orcamento.app.data.Backup
import com.orcamento.app.data.paraDominio
import com.orcamento.core.Aporte
import com.orcamento.core.Cartao
import com.orcamento.core.CompraParcelada
import com.orcamento.core.Dinheiro
import com.orcamento.core.ItemLazer
import com.orcamento.core.Lancamento
import com.orcamento.core.ModeloLazer
import com.orcamento.core.ModeloRecorrente
import com.orcamento.core.MovimentoReserva
import com.orcamento.core.PlanoPadrao
import com.orcamento.core.RegraViolada
import com.orcamento.core.ResumoHistorico
import com.orcamento.core.ResumoMes
import com.orcamento.core.ServicoFinanceiro
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/** Tudo o que as telas mostram para o mês selecionado. Sempre recalculado a partir do banco. */
data class EstadoMes(
    val hoje: LocalDate,
    val resumo: ResumoMes,
    val lancamentos: List<Lancamento>,
    val itensLazer: List<ItemLazer>,
    val aportes: List<Aporte>,
    val saldoReserva: Dinheiro,
    val extratoReserva: List<MovimentoReserva>,
    val totalInvestido: Dinheiro,
    val cartoes: List<Cartao>,
    val modelos: List<ModeloRecorrente>,
    val modelosLazer: List<ModeloLazer>,
    val padrao: PlanoPadrao,
    val historico: List<ResumoHistorico>,
    val sugestoesDescricao: List<String>,
    val categorias: List<String>,
    val ultimoCartaoId: Long?,
) {
    val mes: YearMonth get() = resumo.mes
    val fechado: Boolean get() = resumo.plano.fechado
    val ehMesAtual: Boolean get() = mes == YearMonth.from(hoje)
    val cartoesAtivos: List<Cartao> get() = cartoes.filter { it.ativo }
    fun nomeCartao(id: Long?): String? = id?.let { i -> cartoes.firstOrNull { it.id == i }?.nome }
    fun nomeItemLazer(id: Long?): String? = id?.let { i -> itensLazer.firstOrNull { it.id == i }?.nome }
}

class PrincipalViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OrcamentoApp
    private val versao = MutableStateFlow(0)
    private val _mes = MutableStateFlow(YearMonth.now())
    val mes: StateFlow<YearMonth> = _mes.asStateFlow()

    private val _mensagens = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val mensagens: SharedFlow<String> = _mensagens.asSharedFlow()

    private var ultimoEstado: EstadoMes? = null

    val estado: StateFlow<EstadoMes?> = combine(_mes, versao) { m, _ -> m }
        .map { m ->
            try {
                carregar(m).also { ultimoEstado = it }
            } catch (e: Exception) {
                _mensagens.tryEmit("Erro ao carregar os dados: ${e.message}")
                ultimoEstado
            }
        }
        .flowOn(app.dispatcherBanco)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _busca = MutableStateFlow("")
    val busca: StateFlow<String> = _busca.asStateFlow()

    val resultadosBusca: StateFlow<List<Lancamento>> = combine(_busca, versao) { t, _ -> t.trim() }
        .map { t -> if (t.length < 2) emptyList() else app.dao.buscarLancamentos(t).map { it.paraDominio() } }
        .flowOn(app.dispatcherBanco)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun carregar(m: YearMonth): EstadoMes {
        val s = app.servico
        val dao = app.dao
        val resumo = s.resumo(m)
        return EstadoMes(
            hoje = LocalDate.now(),
            resumo = resumo,
            lancamentos = s.lancamentos(m),
            itensLazer = s.itensLazer(m),
            aportes = s.aportes(m),
            saldoReserva = s.saldoReserva(),
            extratoReserva = s.extratoReserva(),
            totalInvestido = s.totalInvestido(),
            cartoes = s.cartoes(),
            modelos = s.modelos(),
            modelosLazer = s.modelosLazer(),
            padrao = s.planoPadrao(),
            historico = s.historico(),
            sugestoesDescricao = dao.descricoesFrequentes(12),
            categorias = dao.categoriasFrequentes(12),
            ultimoCartaoId = dao.ultimoLancamento()?.cartaoId,
        )
    }

    fun mudarMes(delta: Long) { _mes.value = _mes.value.plusMonths(delta) }
    fun irPara(m: YearMonth) { _mes.value = m }
    fun irParaHoje() { _mes.value = YearMonth.now() }
    fun buscar(texto: String) { _busca.value = texto }

    /**
     * Executa uma regra do núcleo fora da thread principal. Em sucesso, recarrega tudo e chama [aoConcluir];
     * em erro, entrega a mensagem para [aoErro] (formulários) ou para o snackbar.
     */
    fun executar(
        sucesso: String? = null,
        aoErro: ((String) -> Unit)? = null,
        aoConcluir: () -> Unit = {},
        acao: ServicoFinanceiro.() -> Unit,
    ) {
        viewModelScope.launch {
            val erro = withContext(app.dispatcherBanco) {
                try {
                    app.servico.acao()
                    null
                } catch (e: RegraViolada) {
                    e.message ?: "Não foi possível concluir."
                } catch (e: Exception) {
                    "Erro inesperado: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            if (erro == null) {
                versao.value++
                aoConcluir()
                sucesso?.let { _mensagens.emit(it) }
            } else if (aoErro != null) {
                aoErro(erro)
            } else {
                _mensagens.emit(erro)
            }
        }
    }

    /** Carrega uma compra parcelada e os itens de lazer do mês da compra (para editar a partir de qualquer parcela). */
    fun carregarCompra(compraId: Long, aoCarregar: (CompraParcelada, List<ItemLazer>) -> Unit) {
        viewModelScope.launch {
            val r = withContext(app.dispatcherBanco) {
                runCatching {
                    val c = app.servico.compraParcelada(compraId) ?: return@runCatching null
                    c to app.servico.itensLazer(YearMonth.from(c.dataCompra))
                }.getOrNull()
            }
            if (r != null) aoCarregar(r.first, r.second) else _mensagens.emit("Não foi possível carregar a compra parcelada.")
        }
    }

    /** Último gasto com a mesma descrição, para pré-preencher o formulário. */
    fun buscarUltimoComDescricao(descricao: String, aoEncontrar: (Lancamento) -> Unit) {
        viewModelScope.launch {
            val l = withContext(app.dispatcherBanco) { app.dao.ultimoComDescricao(descricao.trim())?.paraDominio() }
            if (l != null) aoEncontrar(l)
        }
    }

    fun exportarBackup(destino: Uri) = arquivo("Backup salvo.") { app.exportarBackup(destino) }

    fun restaurarBackup(origem: Uri) = arquivo("Backup restaurado.") { app.restaurarBackup(origem) }

    fun exportarCsv(destino: Uri) = arquivo("Gastos exportados.") { Backup.escreverTexto(app, destino, Backup.csvDeGastos(app.dao)) }

    private fun arquivo(sucesso: String, bloco: () -> Unit) {
        viewModelScope.launch {
            val erro = withContext(app.dispatcherBanco) {
                try { bloco(); null } catch (e: Exception) { e.message ?: "Não foi possível concluir." }
            }
            versao.value++
            _mensagens.emit(erro ?: sucesso)
        }
    }
}
