package com.orcamento.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orcamento.core.CicloCartao
import com.orcamento.core.Compromisso
import com.orcamento.core.Datas
import com.orcamento.core.Dinheiro
import com.orcamento.core.FaturaCalculada
import com.orcamento.core.Grupo
import com.orcamento.core.ItemConta
import com.orcamento.core.Situacao
import com.orcamento.core.TipoConta

@Composable
fun TelaContas(e: EstadoMes, vm: PrincipalViewModel) {
    val r = e.resumo
    val c = r.contas
    var editando by remember { mutableStateOf<Compromisso?>(null) }
    var novo by remember { mutableStateOf(false) }
    var faturaAberta by remember { mutableStateOf<FaturaCalculada?>(null) }
    val noCartao = r.compromissos.filter { it.noCartao }

    LazyColumn {
        item { CabecalhoMes(e, vm) }

        item {
            Secao("Resumo das contas", subtitulo = "Compromissos pagos direto da conta + faturas que vencem no mês") {
                LinhaValor("Total do mês", c.total, destaque = true)
                LinhaValor("Já pago", c.pago, cor = Cores.ok())
                LinhaValor("Pendente", c.pendente, cor = if (c.vencidos.isNotEmpty()) Cores.acima() else null)
                Spacer(Modifier.height(6.dp))
                Barra(fracao(c.pago, c.total), Cores.ok())
                Text("${c.percentualQuitado ?: 0}% quitado", style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(top = 4.dp))
                if (c.vencidos.isNotEmpty()) Text("${c.vencidos.size} vencido(s)", color = Cores.acima(), style = MaterialTheme.typography.bodyMedium)
                if (c.proximos.isNotEmpty()) Text("${c.proximos.size} vence(m) nos próximos dias", color = Cores.atencao(), style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            Secao(
                "A pagar neste mês",
                subtitulo = if (e.fechado) "Mês fechado: reabra em Planejar para alterar" else "Toque na caixa para marcar como pago",
                acao = {
                    if (!e.fechado) TextButton(onClick = { novo = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null); Text("Adicionar")
                    }
                },
            ) {
                if (c.itens.isEmpty()) {
                    Text("Nada a pagar. As despesas recorrentes cadastradas em Ajustes aparecem aqui todo mês.",
                        color = Cores.neutra(), style = MaterialTheme.typography.bodyMedium)
                }
                c.itens.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider()
                    LinhaConta(
                        item, e.fechado,
                        aoAlternar = { marcar ->
                            when (item.tipo) {
                                TipoConta.COMPROMISSO -> {
                                    val id = item.compromisso!!.id
                                    if (marcar) vm.executar("${item.nome} marcado como pago.") { marcarPago(id) }
                                    else vm.executar("Pagamento desfeito.") { desmarcarPago(id) }
                                }
                                TipoConta.FATURA -> {
                                    val f = item.fatura!!
                                    if (marcar) vm.executar("Fatura marcada como paga.") { pagarFatura(f.cartao.id, f.mesVencimento) }
                                    else vm.executar("Pagamento da fatura desfeito.") { desfazerPagamentoFatura(f.cartao.id, f.mesVencimento) }
                                }
                            }
                        },
                        aoTocar = {
                            when (item.tipo) {
                                TipoConta.COMPROMISSO -> editando = item.compromisso
                                TipoConta.FATURA -> faturaAberta = item.fatura
                            }
                        },
                    )
                }
            }
        }

        if (noCartao.isNotEmpty()) {
            item {
                Secao("Cobrados no cartão", subtitulo = "Já estão somados na fatura. Marque quando conferir a cobrança.") {
                    noCartao.forEachIndexed { i, comp ->
                        if (i > 0) HorizontalDivider()
                        val cartao = e.cartoes.firstOrNull { it.id == comp.cartaoId }
                        val mesFatura = cartao?.let { CicloCartao.mesDaFatura(it, comp.dataCobranca) }
                        Row(Modifier.fillMaxWidth().clickable { editando = comp }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = comp.pago, enabled = !e.fechado,
                                onCheckedChange = { marcar ->
                                    if (marcar) vm.executar { marcarPago(comp.id) } else vm.executar { desmarcarPago(comp.id) }
                                },
                                modifier = Modifier.semantics { contentDescription = "Conferido: ${comp.nome}" },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(comp.nome, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Fatura ${cartao?.nome ?: ""}" + (mesFatura?.let { " de ${Datas.formatarMesCurto(it)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = Cores.neutra(),
                                )
                            }
                            Text(comp.valorEfetivo.formatar(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            Secao("Compromissos por grupo", subtitulo = "Tudo o que é do mês, inclusive o que vai no cartão") {
                LinhaValor("Essenciais", r.totalEssenciais)
                LinhaValor("Não essenciais", r.totalNaoEssenciais)
                if (r.totalOutros.ehPositivo) LinhaValor("Outros", r.totalOutros)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                LinhaValor("Total", r.totalCompromissos, destaque = true)
                if (r.compromissosPlanejados != r.totalCompromissos) {
                    Text("Planejado: ${r.compromissosPlanejados.formatar()} (diferença por valores pagos diferentes do previsto)",
                        style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (novo) FormCompromisso(e, vm, null) { novo = false }
    editando?.let { comp -> FormCompromisso(e, vm, comp) { editando = null } }
    faturaAberta?.let { f ->
        // Usa a versão mais recente da fatura (o estado pode ter mudado com o diálogo aberto)
        val atual = e.resumo.contas.itens.firstOrNull { it.fatura?.cartao?.id == f.cartao.id }?.fatura ?: f
        DialogFatura(e, vm, atual) { faturaAberta = null }
    }
}

@Composable
private fun LinhaConta(item: ItemConta, fechado: Boolean, aoAlternar: (Boolean) -> Unit, aoTocar: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = item.situacao == Situacao.PAGO,
            onCheckedChange = aoAlternar,
            enabled = !fechado,
            modifier = Modifier.semantics { contentDescription = "Pago: ${item.nome}" },
        )
        Column(Modifier.weight(1f)) {
            Text(item.nome, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.vencimento?.let { "Vence ${Datas.formatarDiaMes(it)}" } ?: "Sem vencimento", style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
                Spacer(Modifier.width(6.dp))
                EtiquetaSituacao(item.situacao)
            }
        }
        Text(item.valor.formatar(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FormCompromisso(e: EstadoMes, vm: PrincipalViewModel, original: Compromisso?, aoFechar: () -> Unit) {
    var nome by remember { mutableStateOf(original?.nome ?: "") }
    var valor by remember { mutableStateOf(original?.valorPlanejado?.paraCampo() ?: "") }
    var grupo by remember { mutableStateOf(original?.grupo ?: Grupo.ESSENCIAL) }
    var categoria by remember { mutableStateOf(original?.categoria ?: "") }
    var temVencimento by remember { mutableStateOf(original?.vencimento != null || original == null) }
    var vencimento by remember { mutableStateOf(original?.vencimento ?: Datas.dataPadraoNoMes(e.mes, e.hoje)) }
    var cartaoId by remember { mutableStateOf(original?.cartaoId) }
    var pago by remember { mutableStateOf(original?.pago ?: false) }
    var valorPago by remember { mutableStateOf((original?.valorPago ?: original?.valorPlanejado)?.paraCampo() ?: "") }
    var dataPagamento by remember { mutableStateOf(original?.dataPagamento ?: e.hoje) }
    var observacoes by remember { mutableStateOf(original?.observacoes ?: "") }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember { mutableStateOf(false) }

    FormularioDialog(
        titulo = if (original == null) "Novo compromisso" else "Editar compromisso",
        erro = erro,
        aoCancelar = aoFechar,
        aoExcluir = if (original != null && !e.fechado) ({ confirmarExclusao = true }) else null,
        aoConfirmar = {
            val v = Dinheiro.interpretar(valor)
            val vp = if (pago) Dinheiro.interpretar(valorPago) else null
            when {
                v == null -> erro = "Informe o valor planejado."
                pago && vp == null -> erro = "Informe o valor pago."
                else -> {
                    val c = (original ?: Compromisso(mes = e.mes, nome = "", valorPlanejado = v, grupo = grupo)).copy(
                        nome = nome, valorPlanejado = v, grupo = grupo, categoria = categoria.ifBlank { null },
                        vencimento = if (temVencimento) vencimento else null, cartaoId = cartaoId,
                        pago = pago, valorPago = vp, dataPagamento = if (pago) dataPagamento else null,
                        observacoes = observacoes.ifBlank { null },
                    )
                    vm.executar("Compromisso salvo.", aoErro = { erro = it }, aoConcluir = aoFechar) { salvarCompromisso(c) }
                }
            }
        },
    ) {
        if (original?.modeloId != null) {
            Text("Gerado de uma despesa recorrente. Mudanças aqui valem só para ${Datas.formatarMes(original.mes)}.",
                style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(bottom = 8.dp))
        }
        CampoTexto(nome, { nome = it }, "Nome")
        CampoDinheiro(valor, { valor = it }, "Valor planejado")
        Rotulo("Grupo")
        Escolha(Grupo.entries, grupo, { it.rotulo }, { grupo = it })
        CampoTexto(categoria, { categoria = it }, "Categoria (opcional)")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Tem vencimento", Modifier.weight(1f))
            Switch(checked = temVencimento, onCheckedChange = { temVencimento = it })
        }
        if (temVencimento) CampoData("Vencimento (dentro de ${Datas.formatarMes(e.mes)})", vencimento, e.hoje) { vencimento = it }
        if (e.cartoesAtivos.isNotEmpty() || cartaoId != null) {
            Rotulo("Forma de pagamento")
            Escolha(listOf<Long?>(null) + e.cartoes.filter { it.ativo || it.id == cartaoId }.map { it.id }, cartaoId,
                { id -> if (id == null) "Direto da conta" else "Cartão ${e.nomeCartao(id)}" }, { cartaoId = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (cartaoId != null) "Cobrança conferida" else "Pago", Modifier.weight(1f))
            Switch(checked = pago, onCheckedChange = { pago = it })
        }
        if (pago) {
            CampoDinheiro(valorPago, { valorPago = it }, "Valor efetivamente pago")
            CampoData("Data do pagamento", dataPagamento, e.hoje) { dataPagamento = it }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(observacoes, { observacoes = it }, label = { Text("Observações (opcional)") }, modifier = Modifier.fillMaxWidth())
    }

    if (confirmarExclusao && original != null) {
        ConfirmarDialog(
            "Excluir compromisso?",
            "\"${original.nome}\" será removido de ${Datas.formatarMes(original.mes)}." +
                if (original.modeloId != null) " A despesa recorrente continua valendo para os outros meses." else "",
            aoConfirmar = { vm.executar("Compromisso excluído.", aoConcluir = aoFechar) { excluirCompromisso(original.id) } },
            aoCancelar = { confirmarExclusao = false },
        )
    }
}

@Composable
private fun DialogFatura(e: EstadoMes, vm: PrincipalViewModel, f: FaturaCalculada, aoFechar: () -> Unit) {
    var descontar by remember(f.registro?.id) { mutableStateOf(f.ajuste.ehNegativo) }
    var ajuste by remember(f.registro?.id) { mutableStateOf(if (f.ajuste.ehZero) "" else f.ajuste.paraCampo()) }
    var obs by remember(f.registro?.id) { mutableStateOf(f.registro?.observacoes ?: "") }
    var valorPago by remember(f.totalCalculado) { mutableStateOf(f.totalCalculado.minimoZero().paraCampo()) }
    var dataPago by remember { mutableStateOf(e.hoje) }
    var erro by remember { mutableStateOf<String?>(null) }
    val cartaoId = f.cartao.id
    val mes = f.mesVencimento

    FormularioDialog(
        titulo = "Fatura ${f.cartao.nome} · ${Datas.formatarMesCurto(mes)}",
        erro = erro,
        aoCancelar = aoFechar,
        textoConfirmar = "Fechar",
        aoConfirmar = aoFechar,
    ) {
        Text("Vence em ${Datas.formatar(f.vencimento)}", style = MaterialTheme.typography.bodyMedium)
        Text("Pagar a fatura não é um gasto novo: cada compra já foi contada quando foi registrada.",
            style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(vertical = 6.dp))

        if (f.compras.isNotEmpty()) Rotulo("Compras (${f.compras.size})")
        f.compras.forEach { l -> LinhaValor("${Datas.formatarDiaMes(l.data)} ${l.descricao}", l.valor) }
        if (f.compromissos.isNotEmpty()) Rotulo("Compromissos no cartão")
        f.compromissos.forEach { c -> LinhaValor("${Datas.formatarDiaMes(c.dataCobranca)} ${c.nome}", c.valorEfetivo) }
        if (!f.ajuste.ehZero) LinhaValor("Ajuste${f.registro?.observacoes?.let { " ($it)" } ?: ""}", f.ajuste)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        LinhaValor("Total calculado", f.totalCalculado, destaque = true)
        Text("Sem integração com o banco: o total é a soma do que foi registrado aqui. Use o ajuste para o que não foi lançado (anuidade, parcelas antigas, estornos).",
            style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(top = 4.dp))

        if (!e.fechado) {
            Rotulo("Ajuste")
            Escolha(listOf(false, true), descontar, { if (it) "Descontar" else "Acrescentar" }, { descontar = it })
            CampoDinheiro(ajuste, { ajuste = it }, "Valor do ajuste")
            CampoTexto(obs, { obs = it }, "Motivo do ajuste")
            OutlinedButton(onClick = {
                val v = if (ajuste.isBlank()) Dinheiro.ZERO else Dinheiro.interpretar(ajuste)
                if (v == null) {
                    erro = "Valor do ajuste inválido."
                } else {
                    vm.executar("Ajuste salvo.", aoErro = { erro = it }) { ajustarFatura(cartaoId, mes, if (descontar) -v else v, obs) }
                }
            }, modifier = Modifier.padding(top = 4.dp)) { Text("Salvar ajuste") }
        }

        Rotulo("Pagamento")
        if (f.pago) {
            Text("Paga em ${f.registro?.dataPagamento?.let(Datas::formatar) ?: "-"}: ${f.valorDevido.formatar()}", color = Cores.ok())
            if (!f.diferencaAposPagamento.ehZero) {
                Text("Atenção: o total calculado mudou ${f.diferencaAposPagamento.formatar()} depois do pagamento.",
                    color = Cores.atencao(), style = MaterialTheme.typography.bodySmall)
            }
            if (!e.fechado) {
                TextButton(onClick = { vm.executar("Pagamento desfeito.", aoErro = { erro = it }) { desfazerPagamentoFatura(cartaoId, mes) } }) {
                    Text("Desfazer pagamento")
                }
            }
        } else if (e.fechado) {
            Text("Não paga. Reabra o mês para registrar o pagamento.", color = Cores.atencao())
        } else {
            CampoDinheiro(valorPago, { valorPago = it }, "Valor pago")
            CampoData("Data do pagamento", dataPago, e.hoje) { dataPago = it }
            OutlinedButton(onClick = {
                val v = Dinheiro.interpretar(valorPago)
                if (v == null || !v.ehPositivo) {
                    erro = "Informe o valor pago."
                } else {
                    vm.executar("Fatura marcada como paga.", aoErro = { erro = it }) { pagarFatura(cartaoId, mes, dataPago, v) }
                }
            }) { Text("Marcar como paga") }
        }
    }
}
