package com.orcamento.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orcamento.core.Aporte
import com.orcamento.core.Datas
import com.orcamento.core.Destino
import com.orcamento.core.Dinheiro
import com.orcamento.core.ItemLazer
import com.orcamento.core.MovimentoReserva
import com.orcamento.core.ResumoItemLazer
import com.orcamento.core.TipoMovimentoReserva

private enum class AcaoReserva(val titulo: String) { USAR("Usar a reserva neste mês"), DEPOSITAR("Depositar na reserva"), RETIRAR("Retirar da reserva") }

@Composable
fun TelaPlanejar(e: EstadoMes, vm: PrincipalViewModel) {
    val r = e.resumo
    var editarPlano by remember { mutableStateOf(false) }
    var itemEditado by remember { mutableStateOf<ItemLazer?>(null) }
    var novoItem by remember { mutableStateOf(false) }
    var aporteEditado by remember { mutableStateOf<Aporte?>(null) }
    var novoAporte by remember { mutableStateOf(false) }
    var acaoReserva by remember { mutableStateOf<AcaoReserva?>(null) }
    var movimentoParaExcluir by remember { mutableStateOf<MovimentoReserva?>(null) }
    var verExtratoTodo by remember { mutableStateOf(false) }
    var fechar by remember { mutableStateOf(false) }
    var reabrir by remember { mutableStateOf(false) }
    val aberto = !e.fechado

    LazyColumn {
        item { CabecalhoMes(e, vm) }

        item {
            Secao(
                "Orçamento do mês",
                subtitulo = "Valores só de ${Datas.formatarMes(e.mes)}",
                acao = { if (aberto) IconButton(onClick = { editarPlano = true }) { Icon(Icons.Filled.Edit, contentDescription = "Editar orçamento do mês") } },
            ) {
                LinhaValor("Renda", r.renda)
                LinhaValor("Limite de gastos avulsos", r.avulsos.limite)
                LinhaValor("Meta de investimento", r.investimento.meta)
                LinhaValor("Orçamento de lazer (soma dos itens)", r.lazer.limite)
            }
        }

        item {
            Secao("Planejado × realizado") {
                Row(Modifier.fillMaxWidth()) {
                    Text("", Modifier.weight(1.3f))
                    Text("Planejado", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, color = Cores.neutra())
                    Text("Realizado", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, color = Cores.neutra())
                }
                LinhaComparativo("Compromissos", r.compromissosPlanejados, r.compromissos.filter { it.pago }.fold(Dinheiro.ZERO) { a, c -> a + c.valorEfetivo }, menorEhMelhor = true)
                LinhaComparativo("Lazer", r.lazer.disponivel, r.lazer.gasto, menorEhMelhor = true)
                LinhaComparativo("Avulsos", r.avulsos.disponivel, r.avulsos.gasto, menorEhMelhor = true)
                LinhaComparativo("Investimento", r.investimento.meta, r.investimento.aportado, menorEhMelhor = false)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                LinhaComparativo("Sobra", r.sobraPlanejada, r.sobraAtual, menorEhMelhor = false)
                Text("Compromissos realizados = pagos (ou conferidos no cartão) até agora.",
                    style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(top = 4.dp))
            }
        }

        item {
            Secao(
                "Lazer do mês",
                subtitulo = "Limites planejados por item",
                acao = { if (aberto) TextButton(onClick = { novoItem = true }) { Icon(Icons.Filled.Add, contentDescription = null); Text("Item") } },
            ) {
                ResumoEnvelope(r.lazer)
                if (r.itensLazer.isEmpty()) {
                    Text("Nenhum item. Cadastre itens padrão em Ajustes (valem todo mês) ou adicione um só para este mês.",
                        style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(top = 8.dp))
                }
                r.itensLazer.forEach { ri -> LinhaItemLazer(ri, aberto) { itemEditado = ri.item } }
                if (aberto && e.saldoReserva.ehPositivo) {
                    OutlinedButton(onClick = { acaoReserva = AcaoReserva.USAR }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Usar reserva (${e.saldoReserva.formatar()} disponível)")
                    }
                }
            }
        }

        item {
            val inv = r.investimento
            Secao(
                "Investimentos",
                subtitulo = "Total investido desde o início: ${e.totalInvestido.formatar()}",
                acao = { if (aberto) TextButton(onClick = { novoAporte = true }) { Icon(Icons.Filled.Add, contentDescription = null); Text("Aporte") } },
            ) {
                LinhaValor("Meta do mês", inv.meta)
                LinhaValor("Investido no mês", inv.aportado, cor = if (inv.metaAtingida) Cores.ok() else null)
                LinhaValor(if (inv.acimaDaMeta.ehPositivo) "Acima da meta" else "Falta para a meta", if (inv.acimaDaMeta.ehPositivo) inv.acimaDaMeta else inv.falta)
                if (inv.meta.ehPositivo) {
                    Spacer(Modifier.height(4.dp))
                    Barra(fracao(inv.aportado, inv.meta), MaterialTheme.colorScheme.primary)
                }
                Text("Definir a meta não registra aporte. Registre cada aporte quando ele acontecer.",
                    style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(top = 6.dp))
                e.aportes.forEach { a ->
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable(enabled = aberto) { aporteEditado = a }.padding(vertical = 8.dp)) {
                        Text("${Datas.formatarDiaMes(a.data)}  ${a.descricao ?: "Aporte"}", Modifier.weight(1f))
                        Text(a.valor.formatar(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Secao("Reserva de lazer", subtitulo = "Sobras de lazer guardadas para meses futuros") {
                Text(e.saldoReserva.formatar(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Não faz parte da sobra do mês nem do orçamento de lazer até você usar.", style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (aberto && e.saldoReserva.ehPositivo) OutlinedButton(onClick = { acaoReserva = AcaoReserva.USAR }) { Text("Usar") }
                    OutlinedButton(onClick = { acaoReserva = AcaoReserva.DEPOSITAR }) { Text("Depositar") }
                    if (e.saldoReserva.ehPositivo) OutlinedButton(onClick = { acaoReserva = AcaoReserva.RETIRAR }) { Text("Retirar") }
                }
                if (e.extratoReserva.isNotEmpty()) Rotulo("Movimentações (toque para excluir)")
                val lista = if (verExtratoTodo) e.extratoReserva else e.extratoReserva.take(6)
                lista.forEach { m ->
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable { movimentoParaExcluir = m }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.descricao, style = MaterialTheme.typography.bodyMedium)
                            Text("${Datas.formatar(m.data)} · ${m.tipo.rotulo}" + (m.motivo?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
                        }
                        Text(
                            (if (m.tipo.entrada) "+ " else "− ") + m.valor.formatar(),
                            color = if (m.tipo.entrada) Cores.ok() else Color.Unspecified, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (e.extratoReserva.size > 6) {
                    TextButton(onClick = { verExtratoTodo = !verExtratoTodo }) { Text(if (verExtratoTodo) "Mostrar menos" else "Ver todas (${e.extratoReserva.size})") }
                }
            }
        }

        item {
            Secao("Fechamento do mês") {
                when {
                    e.fechado -> {
                        Text("Fechado em ${e.resumo.plano.fechadoEm?.let(Datas::formatar) ?: "-"}. Lançamentos, pagamentos e valores deste mês estão travados.",
                            style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { reabrir = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Reabrir mês") }
                    }
                    e.mes > java.time.YearMonth.from(e.hoje) -> Text("Este mês ainda não começou.", color = Cores.neutra())
                    else -> {
                        Text("Ao fechar, você decide quanto da sobra de lazer vai para a reserva. Nada é transferido sem sua confirmação.",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Sobra de lazer agora: ${r.lazer.restante.formatar()}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                        Button(onClick = { fechar = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Fechar ${Datas.formatarMes(e.mes)}") }
                    }
                }
            }
        }

        item {
            Secao("Histórico", subtitulo = "Toque em um mês para abri-lo") {
                if (e.historico.isEmpty()) Text("Ainda não há meses registrados.", color = Cores.neutra())
                e.historico.forEachIndexed { i, h ->
                    if (i > 0) HorizontalDivider()
                    val hr = h.resumo
                    Column(Modifier.fillMaxWidth().clickable { vm.irPara(hr.mes) }.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Datas.formatarMes(hr.mes), fontWeight = if (hr.mes == e.mes) FontWeight.Bold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(if (hr.plano.fechado) "Fechado" else "Aberto", style = MaterialTheme.typography.labelSmall, color = Cores.neutra())
                        }
                        Text(
                            "Sobra ${hr.sobraAtual.formatar()} · Avulsos ${hr.avulsos.gasto.formatar()}/${hr.avulsos.disponivel.formatar()} · " +
                                "Lazer ${hr.lazer.gasto.formatar()}/${hr.lazer.disponivel.formatar()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Investido ${hr.investimento.aportado.formatar()} · Contas pendentes ${hr.contas.pendente.formatar()} · Reserva no fim ${h.saldoReservaNoFim.formatar()}",
                            style = MaterialTheme.typography.bodySmall, color = Cores.neutra(),
                        )
                        val estouros = listOfNotNull(
                            hr.avulsos.excesso.takeIf { it.ehPositivo }?.let { "avulsos +${it.formatar()}" },
                            hr.lazer.excesso.takeIf { it.ehPositivo }?.let { "lazer +${it.formatar()}" },
                        )
                        if (estouros.isNotEmpty()) Text("Acima do limite: ${estouros.joinToString()}", style = MaterialTheme.typography.bodySmall, color = Cores.acima())
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (editarPlano) DialogPlanoMes(e, vm) { editarPlano = false }
    if (novoItem) DialogItemLazer(e, vm, null) { novoItem = false }
    itemEditado?.let { DialogItemLazer(e, vm, it) { itemEditado = null } }
    if (novoAporte) DialogAporte(e, vm, null) { novoAporte = false }
    aporteEditado?.let { DialogAporte(e, vm, it) { aporteEditado = null } }
    acaoReserva?.let { DialogReserva(e, vm, it) { acaoReserva = null } }
    if (fechar) DialogFechamento(e, vm) { fechar = false }
    if (reabrir) {
        ConfirmarDialog(
            "Reabrir ${Datas.formatarMes(e.mes)}?",
            "O mês volta a aceitar alterações e a sobra transferida para a reserva no fechamento é devolvida ao mês. " +
                "Se esse dinheiro já foi usado, não será possível reabrir.",
            confirmar = "Reabrir",
            aoConfirmar = { vm.executar("Mês reaberto.") { reabrirMes(e.mes) } },
            aoCancelar = { reabrir = false },
        )
    }
    movimentoParaExcluir?.let { m ->
        ConfirmarDialog(
            "Excluir movimentação?",
            "${m.descricao} (${m.valor.formatar()}). O saldo da reserva será recalculado.",
            aoConfirmar = { vm.executar("Movimentação excluída.") { excluirMovimentoReserva(m.id) } },
            aoCancelar = { movimentoParaExcluir = null },
        )
    }
}

@Composable
private fun LinhaComparativo(rotulo: String, planejado: Dinheiro, realizado: Dinheiro, menorEhMelhor: Boolean) {
    val ruim = if (menorEhMelhor) realizado > planejado else realizado < planejado
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(rotulo, Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium)
        Text(planejado.formatar(), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
        Text(
            realizado.formatar(), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold, color = if (ruim && menorEhMelhor) Cores.acima() else Color.Unspecified,
        )
    }
}

@Composable
private fun LinhaItemLazer(ri: ResumoItemLazer, aberto: Boolean, aoTocar: () -> Unit) {
    val env = ri.envelope
    Column(Modifier.fillMaxWidth().clickable(enabled = aberto, onClick = aoTocar).padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ri.item.nome, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("${env.gasto.formatar()} / ${env.limite.formatar()}", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Barra(fracao(env.gasto, env.limite), Cores.doNivel(env.nivel))
        Text(
            if (env.excesso.ehPositivo) "Passou ${env.excesso.formatar()}" else "Resta ${env.restante.formatar()}",
            style = MaterialTheme.typography.bodySmall, color = Cores.doNivel(env.nivel),
        )
    }
}

@Composable
private fun DialogPlanoMes(e: EstadoMes, vm: PrincipalViewModel, aoFechar: () -> Unit) {
    val p = e.resumo.plano
    var renda by remember { mutableStateOf(p.renda.paraCampo()) }
    var limite by remember { mutableStateOf(p.limiteAvulsos.paraCampo()) }
    var meta by remember { mutableStateOf(p.metaInvestimento.paraCampo()) }
    var comoPadrao by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    FormularioDialog("Orçamento de ${Datas.formatarMes(e.mes)}", erro, aoFechar, {
        val vr = valorOuZero(renda); val vl = valorOuZero(limite); val vmeta = valorOuZero(meta)
        if (vr == null || vl == null || vmeta == null) erro = "Confira os valores."
        else vm.executar("Orçamento atualizado.", aoErro = { erro = it }, aoConcluir = aoFechar) {
            atualizarPlanoMes(e.mes, vr, vl, vmeta, comoPadrao)
        }
    }) {
        CampoDinheiro(renda, { renda = it }, "Renda do mês")
        CampoDinheiro(limite, { limite = it }, "Limite para gastos avulsos")
        CampoDinheiro(meta, { meta = it }, "Meta de investimento")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { comoPadrao = !comoPadrao }) {
            Checkbox(checked = comoPadrao, onCheckedChange = { comoPadrao = it })
            Text("Usar também nos próximos meses", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            if (comoPadrao) "Os meses seguintes que ainda usam os valores padrão antigos também serão atualizados. Meses passados não mudam."
            else "Só ${Datas.formatarMes(e.mes)} muda.",
            style = MaterialTheme.typography.bodySmall, color = Cores.neutra(),
        )
    }
}

private fun valorOuZero(t: String): Dinheiro? = if (t.isBlank()) Dinheiro.ZERO else Dinheiro.interpretar(t)

@Composable
private fun DialogItemLazer(e: EstadoMes, vm: PrincipalViewModel, original: ItemLazer?, aoFechar: () -> Unit) {
    var nome by remember { mutableStateOf(original?.nome ?: "") }
    var limite by remember { mutableStateOf(original?.limite?.paraCampo() ?: "") }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf(false) }

    FormularioDialog(
        if (original == null) "Novo item de lazer" else "Item de lazer", erro, aoFechar,
        aoConfirmar = {
            val v = valorOuZero(limite)
            if (v == null) erro = "Confira o valor."
            else {
                val item = (original ?: ItemLazer(mes = e.mes, nome = "", limite = v)).copy(nome = nome, limite = v)
                vm.executar("Item salvo.", aoErro = { erro = it }, aoConcluir = aoFechar) { salvarItemLazer(item) }
            }
        },
        aoExcluir = original?.let { { confirmar = true } },
    ) {
        Text("Vale só para ${Datas.formatarMes(e.mes)}. Para todo mês, use Ajustes → Itens de lazer padrão.",
            style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(bottom = 8.dp))
        CampoTexto(nome, { nome = it }, "Nome (ex.: Comida fora, Cinema)")
        CampoDinheiro(limite, { limite = it }, "Limite no mês")
    }
    if (confirmar && original != null) {
        ConfirmarDialog("Excluir item?", "\"${original.nome}\" sai do lazer de ${Datas.formatarMes(e.mes)}.",
            aoConfirmar = { vm.executar("Item excluído.", aoConcluir = aoFechar) { excluirItemLazer(original.id) } },
            aoCancelar = { confirmar = false })
    }
}

@Composable
private fun DialogAporte(e: EstadoMes, vm: PrincipalViewModel, original: Aporte?, aoFechar: () -> Unit) {
    var valor by remember { mutableStateOf(original?.valor?.paraCampo() ?: "") }
    var descricao by remember { mutableStateOf(original?.descricao ?: "") }
    var data by remember { mutableStateOf(original?.data ?: Datas.dataPadraoNoMes(e.mes, e.hoje)) }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf(false) }

    FormularioDialog(
        if (original == null) "Registrar aporte" else "Editar aporte", erro, aoFechar,
        aoConfirmar = {
            val v = Dinheiro.interpretar(valor)
            if (v == null) erro = "Informe o valor."
            else {
                val a = Aporte(id = original?.id ?: 0, data = data, valor = v, descricao = descricao.ifBlank { null })
                vm.executar("Aporte salvo.", aoErro = { erro = it }, aoConcluir = aoFechar) { salvarAporte(a) }
            }
        },
        aoExcluir = original?.let { { confirmar = true } },
    ) {
        CampoDinheiro(valor, { valor = it }, "Valor investido")
        CampoTexto(descricao, { descricao = it }, "Descrição (opcional, ex.: Tesouro Selic)")
        CampoData("Data", data, e.hoje) { data = it }
    }
    if (confirmar && original != null) {
        ConfirmarDialog("Excluir aporte?", "${original.valor.formatar()} em ${Datas.formatar(original.data)}.",
            aoConfirmar = { vm.executar("Aporte excluído.", aoConcluir = aoFechar) { excluirAporte(original.id) } },
            aoCancelar = { confirmar = false })
    }
}

@Composable
private fun DialogReserva(e: EstadoMes, vm: PrincipalViewModel, acao: AcaoReserva, aoFechar: () -> Unit) {
    var valor by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf("") }
    var envelope by remember { mutableStateOf(Destino.LAZER) }
    var erro by remember { mutableStateOf<String?>(null) }

    FormularioDialog(acao.titulo, erro, aoFechar, textoConfirmar = "Confirmar", aoConfirmar = {
        val v = Dinheiro.interpretar(valor)
        if (v == null) erro = "Informe o valor."
        else {
            val msg = "Reserva atualizada."
            when (acao) {
                AcaoReserva.USAR -> vm.executar(msg, aoErro = { erro = it }, aoConcluir = aoFechar) { usarReservaNoMes(e.mes, envelope, v, motivo) }
                AcaoReserva.DEPOSITAR -> vm.executar(msg, aoErro = { erro = it }, aoConcluir = aoFechar) { depositarNaReserva(v, descricao, motivo) }
                AcaoReserva.RETIRAR -> vm.executar(msg, aoErro = { erro = it }, aoConcluir = aoFechar) { retirarDaReserva(v, descricao, motivo) }
            }
        }
    }) {
        Text("Saldo atual: ${e.saldoReserva.formatar()}", fontWeight = FontWeight.SemiBold)
        Text(
            when (acao) {
                AcaoReserva.USAR -> "O valor aumenta o orçamento escolhido em ${Datas.formatarMes(e.mes)}. O gasto continua contado uma vez só, no orçamento."
                AcaoReserva.DEPOSITAR -> "Use para lançar o saldo inicial ou guardar dinheiro extra para lazer."
                AcaoReserva.RETIRAR -> "O dinheiro sai da reserva para outra finalidade, sem aumentar nenhum orçamento do mês."
            },
            style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(vertical = 6.dp),
        )
        CampoDinheiro(valor, { valor = it }, "Valor")
        if (acao == AcaoReserva.USAR) {
            Rotulo("Aumentar o orçamento de")
            Escolha(Destino.entries, envelope, { if (it == Destino.LAZER) "Lazer" else "Gastos avulsos" }, { envelope = it })
        } else {
            CampoTexto(descricao, { descricao = it }, "Descrição")
        }
        CampoTexto(motivo, { motivo = it }, "Motivo (opcional)")
    }
}

@Composable
private fun DialogFechamento(e: EstadoMes, vm: PrincipalViewModel, aoFechar: () -> Unit) {
    val sobra = e.resumo.lazer.restante
    val pendentes = e.resumo.contas.pendentes
    var valor by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }

    FormularioDialog("Fechar ${Datas.formatarMes(e.mes)}", erro, aoFechar, textoConfirmar = "Fechar mês", aoConfirmar = {
        val v = valorOuZero(valor)
        if (v == null) erro = "Confira o valor."
        else vm.executar("Mês fechado.", aoErro = { erro = it }, aoConcluir = aoFechar) { fecharMes(e.mes, v) }
    }) {
        LinhaValor("Orçamento de lazer", e.resumo.lazer.disponivel)
        LinhaValor("Gasto em lazer", e.resumo.lazer.gasto)
        LinhaValor("Sobra de lazer", sobra, destaque = true, cor = Cores.ok())
        Spacer(Modifier.height(8.dp))
        if (sobra.ehPositivo) {
            Text("Quanto da sobra vai para a reserva?", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                TextButton(onClick = { valor = "" }) { Text("Nada") }
                TextButton(onClick = { valor = Dinheiro(sobra.centavos / 2).paraCampo() }) { Text("Metade") }
                TextButton(onClick = { valor = sobra.paraCampo() }) { Text("Tudo") }
            }
            CampoDinheiro(valor, { valor = it }, "Transferir para a reserva", suporte = "Máximo ${sobra.formatar()}. Vazio = nada.")
        } else {
            Text("Não houve sobra de lazer, então nada vai para a reserva.", style = MaterialTheme.typography.bodyMedium)
        }
        if (pendentes.isNotEmpty()) {
            Text(
                "Atenção: ${pendentes.size} conta(s) ainda pendente(s) (${pendentes.fold(Dinheiro.ZERO) { a, c -> a + c.valor }.formatar()}). " +
                    "Fechar mesmo assim trava o mês; você pode reabrir depois.",
                color = Cores.atencao(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.width(1.dp))
    }
}
