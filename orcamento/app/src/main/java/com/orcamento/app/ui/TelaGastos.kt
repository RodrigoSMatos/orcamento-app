package com.orcamento.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orcamento.core.Destino
import com.orcamento.core.Lancamento
import com.orcamento.core.somarDinheiro
import java.time.format.TextStyle

private enum class Filtro(val rotulo: String) { TODOS("Todos"), AVULSOS("Avulsos"), LAZER("Lazer"), CARTAO("No cartão") }

@Composable
fun TelaGastos(e: EstadoMes, vm: PrincipalViewModel, abrirGasto: (Lancamento?) -> Unit) {
    val busca by vm.busca.collectAsState()
    val resultados by vm.resultadosBusca.collectAsState()
    var filtro by remember { mutableStateOf(Filtro.TODOS) }
    var categoria by remember { mutableStateOf<String?>(null) }
    val buscando = busca.trim().length >= 2

    LazyColumn {
        item {
            OutlinedTextField(
                value = busca,
                onValueChange = vm::buscar,
                placeholder = { Text("Buscar em todos os meses") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (busca.isNotEmpty()) IconButton(onClick = { vm.buscar("") }) { Icon(Icons.Filled.Clear, contentDescription = "Limpar busca") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (buscando) {
            item {
                Text("${resultados.size} resultado(s) · total ${resultados.somarDinheiro { it.valor }.formatar()}",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
            }
            if (resultados.isEmpty()) item { EstadoVazio("Nenhum gasto encontrado para \"${busca.trim()}\".") }
            items(resultados, key = { it.id }) { l ->
                // Na busca, abrir só gastos do mês exibido (os nomes de item/cartão vêm do mês carregado).
                Column {
                    LinhaGasto(l, e, Modifier.padding(horizontal = 16.dp).clickable {
                        if (l.mes == e.mes) abrirGasto(l) else vm.irPara(l.mes)
                    }, mostrarAno = true)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
            return@LazyColumn
        }

        item { CabecalhoMes(e, vm) }

        val categoriasDoMes = e.lancamentos.mapNotNull { it.categoria }.distinct().sorted()
        val filtrados = e.lancamentos.filter { l ->
            when (filtro) {
                Filtro.TODOS -> true
                Filtro.AVULSOS -> l.destino == Destino.AVULSO
                Filtro.LAZER -> l.destino == Destino.LAZER
                Filtro.CARTAO -> l.cartaoId != null
            } && (categoria == null || l.categoria == categoria)
        }

        item {
            Column {
                Escolha(Filtro.entries, filtro, { it.rotulo }, { filtro = it }, Modifier.padding(horizontal = 16.dp))
                if (categoriasDoMes.isNotEmpty()) {
                    Escolha(listOf<String?>(null) + categoriasDoMes, categoria, { it ?: "Todas as categorias" }, { categoria = it },
                        Modifier.padding(horizontal = 16.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("${filtrados.size} gasto(s)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("Total ${filtrados.somarDinheiro { it.valor }.formatar()}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (filtrados.isEmpty()) {
            item {
                EstadoVazio(
                    if (e.lancamentos.isEmpty()) "Nenhum gasto neste mês.\nRegistre compras do dia a dia (lanche, roupa, padaria) no botão \"Gasto\"."
                    else "Nenhum gasto com esse filtro.",
                )
            }
        }

        filtrados.groupBy { it.data }.forEach { (dia, lista) ->
            item(key = "dia-$dia") {
                val semana = dia.dayOfWeek.getDisplayName(TextStyle.FULL, com.orcamento.core.Datas.PT_BR)
                    .replaceFirstChar { it.titlecase(com.orcamento.core.Datas.PT_BR) }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    Text("$semana, ${com.orcamento.core.Datas.formatarDiaMes(dia)}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text(lista.somarDinheiro { it.valor }.formatar(), style = MaterialTheme.typography.labelLarge, color = Cores.neutra())
                }
            }
            items(lista, key = { it.id }) { l ->
                Column {
                    LinhaGasto(l, e, Modifier.padding(horizontal = 16.dp).clickable(enabled = !e.fechado) { abrirGasto(l) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}
