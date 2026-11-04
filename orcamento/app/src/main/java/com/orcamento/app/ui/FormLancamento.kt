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
import androidx.compose.material3.Switch
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
import com.orcamento.core.CicloCartao
import com.orcamento.core.CompraParcelada
import com.orcamento.core.Datas
import com.orcamento.core.Destino
import com.orcamento.core.Dinheiro
import com.orcamento.core.ItemLazer
import com.orcamento.core.MAX_PARCELAS
import com.orcamento.core.MIN_PARCELAS
import com.orcamento.core.Lancamento
import kotlinx.coroutines.delay
import java.time.LocalDate
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

    // Compra parcelada: só para compras novas no cartão, ou ao editar uma parcela (edita a compra inteira)
    val editandoParcela = original?.compraId != null
    var parcelado by remember { mutableStateOf(editandoParcela) }
    var nParcelas by remember { mutableStateOf(original?.totalParcelas?.toString() ?: "") }
    var itensDaCompra by remember { mutableStateOf<List<ItemLazer>?>(null) }
    var carregando by remember { mutableStateOf(editandoParcela) }
    val podeParcelar = (original == null || editandoParcela) && cartaoId != null
    val itensLazer = itensDaCompra ?: e.itensLazer

    LaunchedEffect(original?.compraId) {
        val compraId = original?.compraId ?: return@LaunchedEffect
        vm.carregarCompra(compraId) { c, itens ->
            valor = c.valorTotal.paraCampo()
            descricao = c.descricao
            data = c.dataCompra
            destino = c.destino
            itemLazerId = c.itemLazerId
            cartaoId = c.cartaoId
            categoria = c.categoria ?: ""
            observacoes = c.observacoes ?: ""
            nParcelas = c.parcelas.toString()
            itensDaCompra = itens
            carregando = false
        }
    }

    LaunchedEffect(Unit) {
        if (original == null) {
            delay(200)
            runCatching { focoValor.requestFocus() }
        }
    }

    fun salvar() {
        if (carregando) return
        val v = Dinheiro.interpretar(valor)
        if (v == null || !v.ehPositivo) {
            erro = "Informe um valor válido, por exemplo 12,50."
            return
        }
        if (parcelado && podeParcelar) {
            val n = nParcelas.toIntOrNull()
            if (n == null || n !in MIN_PARCELAS..MAX_PARCELAS) {
                erro = "Informe o número de parcelas (de $MIN_PARCELAS a $MAX_PARCELAS)."
                return
            }
            val compra = CompraParcelada(
                compraId = original?.compraId, dataCompra = data, descricao = descricao, valorTotal = v, parcelas = n,
                cartaoId = cartaoId!!, destino = destino, itemLazerId = if (destino == Destino.LAZER) itemLazerId else null,
                categoria = categoria.ifBlank { null }, observacoes = observacoes.ifBlank { null },
            )
            val msg = if (editandoParcela) "Compra parcelada atualizada." else "Compra registrada em ${n}x."
            vm.executar(msg, aoErro = { erro = it }, aoConcluir = aoFechar) { salvarCompraParcelada(compra) }
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
            Text(if (original == null) "Novo gasto" else if (editandoParcela) "Editar compra parcelada" else "Editar gasto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            if (editandoParcela) {
                Text("Mudanças valem para todas as ${original?.totalParcelas ?: ""} parcelas.",
                    style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(bottom = 8.dp))
            }
            CampoDinheiro(valor, { valor = it; erro = null }, if (parcelado && podeParcelar) "Valor total da compra" else "Valor", focusRequester = focoValor)
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
                if (itensLazer.isEmpty()) {
                    Text("Nenhum item de lazer em ${Datas.formatarMes(e.mes)}. Cadastre em Planejar → Lazer.",
                        style = MaterialTheme.typography.bodySmall, color = Cores.atencao())
                } else {
                    Escolha(itensLazer.map { it.id }, itemLazerId, { id -> itensLazer.firstOrNull { it.id == id }?.nome ?: "" }, { itemLazerId = it; erro = null })
                }
            }

            Rotulo("Pagamento")
            Escolha((if (editandoParcela) emptyList() else listOf<Long?>(null)) + e.cartoes.filter { it.ativo || it.id == cartaoId }.map { it.id }, cartaoId,
                { id -> if (id == null) "Pix/débito/dinheiro" else "Cartão ${e.nomeCartao(id)}" }, { cartaoId = it; erro = null })

            if (podeParcelar) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Parcelado", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = parcelado, onCheckedChange = { parcelado = it; erro = null }, enabled = !editandoParcela)
                }
                if (parcelado) {
                    CampoTexto(nParcelas, { nParcelas = it.filter(Char::isDigit).take(2); erro = null }, "Número de parcelas", numerico = true)
                    PreviaParcelas(e, valor, nParcelas, cartaoId, data)
                }
            }

            CampoData(if (parcelado && podeParcelar) "Data da compra" else "Data", data, e.hoje) { data = it; erro = null }

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
                    TextButton(onClick = { confirmarExclusao = true }, enabled = !carregando) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = aoFechar) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { salvar() }, enabled = !carregando) { Text("Salvar") }
            }
        }
    }

    if (confirmarExclusao && original != null && original.compraId != null) {
        ConfirmarDialog(
            "Excluir compra parcelada?",
            "\"${original.descricao}\" e todas as ${original.totalParcelas ?: ""} parcelas serão removidas dos meses e das faturas.",
            aoConfirmar = { vm.executar("Compra excluída.", aoConcluir = aoFechar) { excluirCompraParcelada(original.compraId!!) } },
            aoCancelar = { confirmarExclusao = false },
        )
    } else if (confirmarExclusao && original != null) {
        ConfirmarDialog(
            "Excluir gasto?",
            "\"${original.descricao}\" de ${original.valor.formatar()} em ${Datas.formatar(original.data)} será removido e os totais recalculados.",
            aoConfirmar = { vm.executar("Gasto excluído.", aoConcluir = aoFechar) { excluirLancamento(original.id) } },
            aoCancelar = { confirmarExclusao = false },
        )
    }
}

/** "3x de R$ 100,00 · faturas de nov/26 a jan/27" */
@Composable
private fun PreviaParcelas(e: EstadoMes, valor: String, nParcelas: String, cartaoId: Long?, data: LocalDate) {
    val v = Dinheiro.interpretar(valor)
    val n = nParcelas.toIntOrNull()
    val cartao = e.cartoes.firstOrNull { it.id == cartaoId }
    if (v == null || n == null || n !in MIN_PARCELAS..MAX_PARCELAS || cartao == null) return
    val compra = CompraParcelada(dataCompra = data, descricao = "", valorTotal = v, parcelas = n, cartaoId = cartao.id)
    val valores = compra.valoresDasParcelas()
    val primeira = CicloCartao.mesDaFatura(cartao, data)
    val texto = buildString {
        append("${n}x de ${valores.last().formatar()}")
        if (valores.first() != valores.last()) append(" (1ª de ${valores.first().formatar()})")
        append(" · faturas de ${Datas.formatarMesCurto(primeira)} a ${Datas.formatarMesCurto(primeira.plusMonths((n - 1).toLong()))}")
    }
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
    Text("Cada parcela conta no orçamento do mês em que é cobrada, não tudo no mês da compra.",
        style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
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
