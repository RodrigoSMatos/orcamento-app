package com.orcamento.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.orcamento.core.Datas
import com.orcamento.core.Destino
import com.orcamento.core.Dinheiro
import com.orcamento.core.Lancamento
import kotlinx.coroutines.delay
import java.time.YearMonth

/**
 * Cadastro rápido: valor → descrição → Salvar. Data = hoje (alterável).
 * Destino, pagamento, categoria e observações já vêm preenchidos e são opcionais.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormLancamento(e: EstadoMes, vm: PrincipalViewModel, original: Lancamento?, aoFechar: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var valor by remember { mutableStateOf(original?.valor?.paraCampo() ?: "") }
    var descricao by remember { mutableStateOf(original?.descricao ?: "") }
    var data by remember { mutableStateOf(original?.data ?: if (e.ehMesAtual) e.hoje else Datas.dataPadraoNoMes(e.mes, e.hoje)) }
    var destino by remember { mutableStateOf(original?.destino ?: Destino.AVULSO) }
    var itemLazerId by remember { mutableStateOf(original?.itemLazerId ?: e.itensLazer.singleOrNull()?.id) }
    var cartaoId by remember {
        mutableStateOf(original?.cartaoId ?: e.ultimoCartaoId?.takeIf { id -> e.cartoesAtivos.any { it.id == id } })
    }
    var categoria by remember { mutableStateOf(original?.categoria ?: "") }
    var observacoes by remember { mutableStateOf(original?.observacoes ?: "") }
    var maisOpcoes by remember { mutableStateOf(original?.categoria != null || original?.observacoes != null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember { mutableStateOf(false) }
    val focoValor = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (original == null) {
            delay(200)
            runCatching { focoValor.requestFocus() }
        }
    }

    fun salvar() {
        val v = Dinheiro.interpretar(valor)
        if (v == null || !v.ehPositivo) {
            erro = "Informe um valor válido, por exemplo 12,50."
            return
        }
        val l = Lancamento(
            id = original?.id ?: 0, data = data, descricao = descricao, valor = v, destino = destino,
            itemLazerId = if (destino == Destino.LAZER) itemLazerId else null,
            categoria = categoria.ifBlank { null }, cartaoId = cartaoId, observacoes = observacoes.ifBlank { null },
        )
        val msg = (if (original == null) "Gasto registrado." else "Gasto atualizado.") + avisoDeExcesso(e, original, l)
        vm.executar(msg, aoErro = { erro = it }, aoConcluir = aoFechar) { salvarLancamento(l) }
    }

    ModalBottomSheet(onDismissRequest = aoFechar, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .navigationBarsPadding().imePadding(),
        ) {
            Text(if (original == null) "Novo gasto" else "Editar gasto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            CampoDinheiro(valor, { valor = it; erro = null }, "Valor", focusRequester = focoValor)
            Spacer(Modifier.height(8.dp))
            CampoTexto(descricao, { descricao = it; erro = null }, "Descrição", imeAction = ImeAction.Done, aoConcluir = { salvar() })

            val sugestoes = e.sugestoesDescricao
                .filter { descricao.isBlank() || (it.contains(descricao.trim(), ignoreCase = true) && !it.equals(descricao.trim(), ignoreCase = true)) }
                .take(8)
            if (sugestoes.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sugestoes.forEach { s ->
                        AssistChip(onClick = {
                            descricao = s
                            vm.buscarUltimoComDescricao(s) { anterior ->
                                if (valor.isBlank()) valor = anterior.valor.paraCampo()
                                if (categoria.isBlank() && anterior.categoria != null) categoria = anterior.categoria!!
                                destino = anterior.destino
                                if (anterior.cartaoId == null || e.cartoesAtivos.any { it.id == anterior.cartaoId }) cartaoId = anterior.cartaoId
                            }
                        }, label = { Text(s) })
                    }
                }
            }

            Rotulo("Conta em")
            Escolha(Destino.entries, destino, { if (it == Destino.AVULSO) "Gastos avulsos" else "Lazer" }, { destino = it; erro = null })
            if (destino == Destino.LAZER) {
                Rotulo("Item de lazer")
                if (e.itensLazer.isEmpty()) {
                    Text("Nenhum item de lazer em ${Datas.formatarMes(e.mes)}. Cadastre em Planejar → Lazer.",
                        style = MaterialTheme.typography.bodySmall, color = Cores.atencao())
                } else {
                    Escolha(e.itensLazer.map { it.id }, itemLazerId, { id -> e.nomeItemLazer(id) ?: "" }, { itemLazerId = it; erro = null })
                }
            }

            Rotulo("Pagamento")
            Escolha(listOf<Long?>(null) + e.cartoesAtivos.map { it.id }, cartaoId,
                { id -> if (id == null) "Pix/débito/dinheiro" else "Cartão ${e.nomeCartao(id)}" }, { cartaoId = it })

            CampoData("Data", data, e.hoje) { data = it; erro = null }

            TextButton(onClick = { maisOpcoes = !maisOpcoes }) { Text(if (maisOpcoes) "Menos opções" else "Mais opções (categoria, observações)") }
            if (maisOpcoes) {
                CampoTexto(categoria, { categoria = it }, "Categoria (opcional)")
                if (e.categorias.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        e.categorias.forEach { c -> AssistChip(onClick = { categoria = c }, label = { Text(c) }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(observacoes, { observacoes = it }, label = { Text("Observações (opcional)") }, modifier = Modifier.fillMaxWidth())
            }

            if (erro != null) {
                Text(erro!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (original != null) {
                    TextButton(onClick = { confirmarExclusao = true }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = aoFechar) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { salvar() }) { Text("Salvar") }
            }
        }
    }

    if (confirmarExclusao && original != null) {
        ConfirmarDialog(
            "Excluir gasto?",
            "\"${original.descricao}\" de ${original.valor.formatar()} em ${Datas.formatar(original.data)} será removido e os totais recalculados.",
            aoConfirmar = { vm.executar("Gasto excluído.", aoConcluir = aoFechar) { excluirLancamento(original.id) } },
            aoCancelar = { confirmarExclusao = false },
        )
    }
}

/** Texto extra para o aviso quando o gasto faz o orçamento do mês exibido passar do limite. */
private fun avisoDeExcesso(e: EstadoMes, original: Lancamento?, novo: Lancamento): String {
    if (YearMonth.from(novo.data) != e.mes) return ""
    val env = if (novo.destino == Destino.AVULSO) e.resumo.avulsos else e.resumo.lazer
    val jaContado = if (original != null && original.mes == e.mes && original.destino == novo.destino) original.valor else Dinheiro.ZERO
    val gasto = env.gasto - jaContado + novo.valor
    if (!env.disponivel.ehPositivo || gasto <= env.disponivel) return ""
    val nome = if (novo.destino == Destino.AVULSO) "Avulsos" else "Lazer"
    return " $nome passou ${(gasto - env.disponivel).formatar()} do limite."
}
