package com.orcamento.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orcamento.core.Lancamento

enum class Aba(val rotulo: String, val icone: ImageVector) {
    INICIO("Início", Icons.Filled.Home),
    GASTOS("Gastos", Icons.AutoMirrored.Filled.List),
    CONTAS("Contas", Icons.Filled.CheckCircle),
    PLANEJAR("Planejar", Icons.Filled.DateRange),
    AJUSTES("Ajustes", Icons.Filled.Settings),
}

/** Formulário de gasto aberto: novo (lancamento = null) ou edição. */
data class EdicaoGasto(val lancamento: Lancamento?)

@Composable
fun AppRaiz(vm: PrincipalViewModel = viewModel()) {
    val estado by vm.estado.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var aba by rememberSaveable { mutableStateOf(Aba.INICIO) }
    var edicaoGasto by remember { mutableStateOf<EdicaoGasto?>(null) }

    LaunchedEffect(Unit) { vm.mensagens.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = aba != Aba.INICIO) { aba = Aba.INICIO }

    val e = estado
    val podeLancar = e != null && !e.fechado && aba in setOf(Aba.INICIO, Aba.GASTOS)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Aba.entries.forEach { a ->
                    NavigationBarItem(
                        selected = aba == a,
                        onClick = { aba = a },
                        icon = { Icon(a.icone, contentDescription = null) },
                        label = { Text(a.rotulo) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (podeLancar) {
                ExtendedFloatingActionButton(
                    onClick = { edicaoGasto = EdicaoGasto(null) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Gasto") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (e == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val abrirGasto: (Lancamento?) -> Unit = { edicaoGasto = EdicaoGasto(it) }
                when (aba) {
                    Aba.INICIO -> TelaInicio(e, vm, abrirGasto) { aba = it }
                    Aba.GASTOS -> TelaGastos(e, vm, abrirGasto)
                    Aba.CONTAS -> TelaContas(e, vm)
                    Aba.PLANEJAR -> TelaPlanejar(e, vm)
                    Aba.AJUSTES -> TelaAjustes(e, vm)
                }
            }
        }
    }

    val edicao = edicaoGasto
    if (edicao != null && e != null) {
        FormLancamento(e, vm, edicao.lancamento) { edicaoGasto = null }
    }
}

/** Cabeçalho de mês usado em todas as abas que dependem do mês. */
@Composable
fun CabecalhoMes(e: EstadoMes, vm: PrincipalViewModel) {
    SeletorMes(
        mes = e.mes, fechado = e.fechado,
        aoAnterior = { vm.mudarMes(-1) }, aoProximo = { vm.mudarMes(1) }, aoHoje = { vm.irParaHoje() },
        ehMesAtual = e.ehMesAtual,
    )
}
