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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orcamento.core.Datas
import com.orcamento.core.Destino
import com.orcamento.core.Lancamento

@Composable
fun TelaInicio(e: EstadoMes, vm: PrincipalViewModel, abrirGasto: (Lancamento?) -> Unit, irPara: (Aba) -> Unit) {
    val r = e.resumo
    LazyColumn {
        item { CabecalhoMes(e, vm) }

        item {
            Secao("Sobra do mês", subtitulo = "Livre depois de compromissos, orçamentos e investimento") {
                if (!r.renda.ehPositivo) {
                    Text("Cadastre sua renda mensal em Planejar para ver quanto sobra.", color = Cores.atencao(),
                        modifier = Modifier.clickable { irPara(Aba.PLANEJAR) })
                } else {
                    val cor = if (r.sobraAtual.ehNegativo) Cores.acima() else Cores.ok()
                    Text(r.sobraAtual.formatar(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = cor)
                    if (r.sobraAtual != r.sobraPlanejada) {
                        Text("Planejado: ${r.sobraPlanejada.formatar()} · estouros e aportes extras reduziram a sobra",
                            style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
                    }
                    if (r.sobraAtual.ehNegativo) {
                        Text("O planejamento usa mais do que a renda.", style = MaterialTheme.typography.bodySmall, color = Cores.acima())
                    }
                    Spacer(Modifier.height(8.dp))
                    LinhaValor("Renda", r.renda)
                    LinhaValor("Compromissos fixos", r.totalCompromissos, prefixo = "− ")
                    LinhaValor("Orçamento de lazer", r.lazer.limite, prefixo = "− ")
                    LinhaValor("Limite de avulsos", r.avulsos.limite, prefixo = "− ")
                    LinhaValor("Meta de investimento", r.investimento.meta, prefixo = "− ")
                }
            }
        }

        item {
            Secao("Gastos avulsos", subtitulo = "Compras do dia a dia", aoTocar = { irPara(Aba.GASTOS) }) {
                ResumoEnvelope(r.avulsos)
            }
        }

        item {
            Secao("Lazer", subtitulo = if (r.itensLazer.isEmpty()) "Nenhum item de lazer planejado" else null, aoTocar = { irPara(Aba.PLANEJAR) }) {
                ResumoEnvelope(r.lazer)
            }
        }

        item {
            val c = r.contas
            Secao("Contas do mês", subtitulo = "O que precisa sair da conta, incluindo faturas", aoTocar = { irPara(Aba.CONTAS) }) {
                if (c.itens.isEmpty()) {
                    Text("Nenhuma conta neste mês. Cadastre despesas recorrentes em Ajustes.", color = Cores.neutra(), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(c.pendente.formatar(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(" pendente", style = MaterialTheme.typography.bodyMedium, color = Cores.neutra(), modifier = Modifier.padding(bottom = 2.dp))
                        Spacer(Modifier.weight(1f))
                        Text("${c.percentualQuitado ?: 0}% pago", style = MaterialTheme.typography.labelLarge, color = Cores.ok())
                    }
                    Spacer(Modifier.height(6.dp))
                    Barra(fracao(c.pago, c.total), Cores.ok())
                    Spacer(Modifier.height(6.dp))
                    Text("${c.pago.formatar()} pagos de ${c.total.formatar()}", style = MaterialTheme.typography.bodyMedium)
                    if (c.vencidos.isNotEmpty()) {
                        Text("${c.vencidos.size} vencido(s): ${c.vencidos.joinToString { it.nome }}", color = Cores.acima(), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (c.proximos.isNotEmpty()) {
                        Text("Vence em breve: ${c.proximos.joinToString { "${it.nome} (${it.vencimento?.let(Datas::formatarDiaMes)})" }}",
                            color = Cores.atencao(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            val inv = r.investimento
            Secao("Investimento", aoTocar = { irPara(Aba.PLANEJAR) }) {
                if (!inv.meta.ehPositivo && !inv.aportado.ehPositivo) {
                    Text("Sem meta definida para este mês.", color = Cores.neutra(), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(inv.aportado.formatar(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(" de ${inv.meta.formatar()}", style = MaterialTheme.typography.bodyMedium, color = Cores.neutra(), modifier = Modifier.padding(bottom = 2.dp))
                        Spacer(Modifier.weight(1f))
                        inv.percentual?.let { Text("$it%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Barra(fracao(inv.aportado, inv.meta), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (inv.metaAtingida) "Meta atingida" else "Faltam ${inv.falta.formatar()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (inv.metaAtingida) Cores.ok() else Cores.neutra(),
                    )
                }
            }
        }

        item {
            Secao("Reserva de lazer", subtitulo = "Acumulada de meses anteriores · separada da sobra do mês", aoTocar = { irPara(Aba.PLANEJAR) }) {
                Text(e.saldoReserva.formatar(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Secao("Gastos recentes", aoTocar = { irPara(Aba.GASTOS) }) {
                if (e.lancamentos.isEmpty()) {
                    Text("Nenhum gasto em ${Datas.formatarMes(e.mes)}. Toque em \"Gasto\" para registrar.", color = Cores.neutra(), style = MaterialTheme.typography.bodyMedium)
                } else {
                    e.lancamentos.take(5).forEachIndexed { i, l ->
                        if (i > 0) HorizontalDivider()
                        LinhaGasto(l, e, Modifier.clickable(enabled = !e.fechado) { abrirGasto(l) })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
fun LinhaGasto(l: Lancamento, e: EstadoMes, modifier: Modifier = Modifier, mostrarAno: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(l.descricao, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (mostrarAno) Datas.formatar(l.data) else Datas.formatarDiaMes(l.data), style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
                Spacer(Modifier.width(6.dp))
                Etiqueta(if (l.destino == Destino.LAZER) "Lazer" + (e.nomeItemLazer(l.itemLazerId)?.let { " · $it" } ?: "") else "Avulso")
                l.cartaoId?.let { Spacer(Modifier.width(4.dp)); Etiqueta("Cartão ${e.nomeCartao(it) ?: ""}") }
                l.categoria?.let { Spacer(Modifier.width(4.dp)); Etiqueta(it) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(l.valor.formatar(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
