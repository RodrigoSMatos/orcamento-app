package com.orcamento.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.orcamento.core.Cartao
import com.orcamento.core.Datas
import com.orcamento.core.Dinheiro
import com.orcamento.core.Frequencia
import com.orcamento.core.Grupo
import com.orcamento.core.ModeloLazer
import com.orcamento.core.ModeloRecorrente
import com.orcamento.core.PlanoPadrao
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun TelaAjustes(e: EstadoMes, vm: PrincipalViewModel) {
    var editarPadrao by remember { mutableStateOf(false) }
    var modelo by remember { mutableStateOf<ModeloRecorrente?>(null) }
    var novoModeloGrupo by remember { mutableStateOf<Grupo?>(null) }
    var modeloLazer by remember { mutableStateOf<ModeloLazer?>(null) }
    var novoModeloLazer by remember { mutableStateOf(false) }
    var cartao by remember { mutableStateOf<Cartao?>(null) }
    var novoCartao by remember { mutableStateOf(false) }
    var confirmarRestauracao by remember { mutableStateOf(false) }

    val hojeTexto = LocalDate.now().toString()
    val exportarBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { vm.exportarBackup(it) }
    }
    val restaurarBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.restaurarBackup(it) }
    }
    val exportarCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportarCsv(it) }
    }

    LazyColumn {
        item {
            Secao(
                "Valores padrão para meses novos",
                subtitulo = "Cada mês guarda a própria cópia; mudar aqui não altera meses já criados",
                acao = { IconButton(onClick = { editarPadrao = true }) { Icon(Icons.Filled.Edit, contentDescription = "Editar valores padrão") } },
            ) {
                LinhaValor("Renda mensal", e.padrao.renda)
                LinhaValor("Limite de gastos avulsos", e.padrao.limiteAvulsos)
                LinhaValor("Meta de investimento", e.padrao.metaInvestimento)
            }
        }

        Grupo.entries.forEach { g ->
            item(key = "grupo-${g.name}") {
                val lista = e.modelos.filter { it.grupo == g }
                Secao(
                    "Recorrentes: ${g.rotulo.lowercase()}",
                    subtitulo = if (g == Grupo.ESSENCIAL) "Conta de celular, ajuda em casa, faculdade…" else if (g == Grupo.NAO_ESSENCIAL) "Assinaturas, serviços digitais…" else null,
                    acao = { TextButton(onClick = { novoModeloGrupo = g }) { Icon(Icons.Filled.Add, contentDescription = null); Text("Adicionar") } },
                ) {
                    if (lista.isEmpty()) Text("Nenhuma despesa cadastrada.", color = Cores.neutra(), style = MaterialTheme.typography.bodyMedium)
                    lista.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().clickable { modelo = m }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.nome, style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (m.ativo) null else TextDecoration.LineThrough)
                                Text(descricaoModelo(m, e), style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
                            }
                            Text(m.valor.formatar(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (lista.isNotEmpty()) {
                        HorizontalDivider()
                        LinhaValor("Total mensal ativo", lista.filter { it.ativo && it.frequencia == Frequencia.MENSAL }.fold(Dinheiro.ZERO) { a, m -> a + m.valor }, destaque = true)
                    }
                }
            }
        }

        item {
            Secao(
                "Itens de lazer padrão",
                subtitulo = "Viram o orçamento de lazer de cada mês novo",
                acao = { TextButton(onClick = { novoModeloLazer = true }) { Icon(Icons.Filled.Add, contentDescription = null); Text("Adicionar") } },
            ) {
                if (e.modelosLazer.isEmpty()) Text("Ex.: Comida fora R$ 150, Rolês R$ 100, Cinema R$ 50.", color = Cores.neutra(), style = MaterialTheme.typography.bodyMedium)
                e.modelosLazer.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable { modeloLazer = m }.padding(vertical = 8.dp)) {
                        Text(m.nome + if (m.ativo) "" else " (inativo)", Modifier.weight(1f))
                        Text(m.valor.formatar(), fontWeight = FontWeight.SemiBold)
                    }
                }
                if (e.modelosLazer.isNotEmpty()) {
                    HorizontalDivider()
                    LinhaValor("Total de lazer por mês", e.modelosLazer.filter { it.ativo }.fold(Dinheiro.ZERO) { a, m -> a + m.valor }, destaque = true)
                }
            }
        }

        item {
            Secao(
                "Cartões de crédito",
                subtitulo = "Usados para montar a fatura de cada mês",
                acao = { TextButton(onClick = { novoCartao = true }) { Icon(Icons.Filled.Add, contentDescription = null); Text("Adicionar") } },
            ) {
                if (e.cartoes.isEmpty()) Text("Nenhum cartão. Cadastre para separar compras no cartão das pagas na hora.", color = Cores.neutra(), style = MaterialTheme.typography.bodyMedium)
                e.cartoes.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable { cartao = c }.padding(vertical = 8.dp)) {
                        Text(c.nome + if (c.ativo) "" else " (inativo)", Modifier.weight(1f))
                        Text("Fecha dia ${c.diaFechamento} · vence dia ${c.diaVencimento}", style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
                    }
                }
            }
        }

        item {
            Secao("Backup e exportação", subtitulo = "Os dados ficam só neste celular") {
                Text("Faça backup de vez em quando e guarde o arquivo no Drive ou no computador. " +
                    "Se trocar de celular, instale o app e restaure o backup.",
                    style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
                OutlinedButton(onClick = { exportarBackup.launch("orcamento-backup-$hojeTexto.db") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Salvar backup")
                }
                OutlinedButton(onClick = { confirmarRestauracao = true }, modifier = Modifier.fillMaxWidth()) { Text("Restaurar backup") }
                OutlinedButton(onClick = { exportarCsv.launch("gastos-$hojeTexto.csv") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Exportar gastos (CSV para planilha)")
                }
            }
        }

        item {
            Secao("Sobre") {
                Text(
                    "Tudo é registrado por você: o app não se conecta a bancos nem a corretoras. " +
                        "Faturas são calculadas a partir das compras lançadas no cartão.",
                    style = MaterialTheme.typography.bodySmall, color = Cores.neutra(),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (editarPadrao) DialogPadrao(e.padrao, vm) { editarPadrao = false }
    novoModeloGrupo?.let { g -> FormModelo(e, vm, null, g) { novoModeloGrupo = null } }
    modelo?.let { m -> FormModelo(e, vm, m, m.grupo) { modelo = null } }
    if (novoModeloLazer) FormModeloLazer(vm, null) { novoModeloLazer = false }
    modeloLazer?.let { m -> FormModeloLazer(vm, m) { modeloLazer = null } }
    if (novoCartao) FormCartao(vm, null) { novoCartao = false }
    cartao?.let { c -> FormCartao(vm, c) { cartao = null } }
    if (confirmarRestauracao) {
        ConfirmarDialog(
            "Restaurar backup?",
            "Todos os dados atuais deste celular serão substituídos pelos do arquivo escolhido. Se tiver dúvida, salve um backup antes.",
            confirmar = "Escolher arquivo",
            aoConfirmar = { restaurarBackup.launch(arrayOf("*/*")) },
            aoCancelar = { confirmarRestauracao = false },
        )
    }
}

private fun descricaoModelo(m: ModeloRecorrente, e: EstadoMes): String = listOfNotNull(
    m.frequencia.rotulo,
    m.diaVencimento?.let { "vence dia $it" },
    m.cartaoId?.let { "cartão ${e.nomeCartao(it) ?: ""}" },
    "desde ${Datas.formatarMesCurto(m.mesInicio)}",
    m.mesFim?.let { "até ${Datas.formatarMesCurto(it)}" },
    if (!m.ativo) "inativo" else null,
).joinToString(" · ")

@Composable
private fun DialogPadrao(p: PlanoPadrao, vm: PrincipalViewModel, aoFechar: () -> Unit) {
    var renda by remember { mutableStateOf(p.renda.paraCampo()) }
    var limite by remember { mutableStateOf(p.limiteAvulsos.paraCampo()) }
    var meta by remember { mutableStateOf(p.metaInvestimento.paraCampo()) }
    var erro by remember { mutableStateOf<String?>(null) }
    FormularioDialog("Valores padrão", erro, aoFechar, {
        val vr = zeroSeVazio(renda); val vl = zeroSeVazio(limite); val vmeta = zeroSeVazio(meta)
        if (vr == null || vl == null || vmeta == null) erro = "Confira os valores."
        else vm.executar("Valores padrão salvos.", aoErro = { erro = it }, aoConcluir = aoFechar) { salvarPlanoPadrao(PlanoPadrao(vr, vl, vmeta)) }
    }) {
        Text("Usados ao criar um mês novo. Para mudar o mês atual, use Planejar → Orçamento do mês.",
            style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(bottom = 8.dp))
        CampoDinheiro(renda, { renda = it }, "Renda mensal")
        CampoDinheiro(limite, { limite = it }, "Limite de gastos avulsos")
        CampoDinheiro(meta, { meta = it }, "Meta de investimento")
    }
}

private fun zeroSeVazio(t: String): Dinheiro? = if (t.isBlank()) Dinheiro.ZERO else Dinheiro.interpretar(t)

@Composable
private fun FormModelo(e: EstadoMes, vm: PrincipalViewModel, original: ModeloRecorrente?, grupoInicial: Grupo, aoFechar: () -> Unit) {
    var nome by remember { mutableStateOf(original?.nome ?: "") }
    var valor by remember { mutableStateOf(original?.valor?.paraCampo() ?: "") }
    var grupo by remember { mutableStateOf(grupoInicial) }
    var categoria by remember { mutableStateOf(original?.categoria ?: "") }
    var dia by remember { mutableStateOf(original?.diaVencimento?.toString() ?: "") }
    var frequencia by remember { mutableStateOf(original?.frequencia ?: Frequencia.MENSAL) }
    var inicio by remember { mutableStateOf(formatarMesCampo(original?.mesInicio ?: YearMonth.from(e.hoje))) }
    var fim by remember { mutableStateOf(original?.mesFim?.let(::formatarMesCampo) ?: "") }
    var cartaoId by remember { mutableStateOf(original?.cartaoId) }
    var ativo by remember { mutableStateOf(original?.ativo ?: true) }
    var observacoes by remember { mutableStateOf(original?.observacoes ?: "") }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf(false) }

    FormularioDialog(
        if (original == null) "Nova despesa recorrente" else "Despesa recorrente", erro, aoFechar,
        aoConfirmar = {
            val v = Dinheiro.interpretar(valor)
            val d = dia.trim().ifEmpty { null }?.toIntOrNull()
            val mi = interpretarMes(inicio)
            val mf = if (fim.isBlank()) null else interpretarMes(fim)
            erro = when {
                v == null -> "Informe o valor."
                dia.isNotBlank() && (d == null || d !in 1..31) -> "Dia de vencimento deve ser de 1 a 31."
                mi == null -> "Primeiro mês no formato MM/AAAA."
                fim.isNotBlank() && mf == null -> "Último mês no formato MM/AAAA."
                else -> null
            }
            if (erro == null && v != null && mi != null) {
                val m = ModeloRecorrente(
                    id = original?.id ?: 0, nome = nome, valor = v, grupo = grupo, categoria = categoria.ifBlank { null },
                    diaVencimento = d, frequencia = frequencia, mesInicio = mi, mesFim = mf, cartaoId = cartaoId,
                    ativo = ativo, observacoes = observacoes.ifBlank { null },
                )
                vm.executar("Despesa salva.", aoErro = { erro = it }, aoConcluir = aoFechar) { salvarModelo(m) }
            }
        },
        aoExcluir = original?.let { { confirmar = true } },
    ) {
        Text(
            "Gerada automaticamente em cada mês. Ao salvar, os meses abertos a partir do atual são atualizados " +
                "(exceto o que já foi pago ou ajustado à mão). Meses passados e fechados nunca mudam.",
            style = MaterialTheme.typography.bodySmall, color = Cores.neutra(), modifier = Modifier.padding(bottom = 8.dp),
        )
        CampoTexto(nome, { nome = it }, "Nome (ex.: Faculdade, Conta de celular)")
        CampoDinheiro(valor, { valor = it }, "Valor")
        Rotulo("Grupo")
        Escolha(Grupo.entries, grupo, { it.rotulo }, { grupo = it })
        CampoTexto(categoria, { categoria = it }, "Categoria (opcional)")
        CampoTexto(dia, { dia = it.filter(Char::isDigit).take(2) }, "Dia de vencimento (opcional)", numerico = true)
        Rotulo("Frequência")
        Escolha(Frequencia.entries, frequencia, { it.rotulo }, { frequencia = it })
        Row {
            CampoTexto(inicio, { inicio = it }, "Primeiro mês", Modifier.weight(1f).padding(end = 4.dp), suporte = "MM/AAAA")
            CampoTexto(fim, { fim = it }, "Último mês", Modifier.weight(1f).padding(start = 4.dp), suporte = "Vazio = sem fim")
        }
        if (e.cartoes.isNotEmpty()) {
            Rotulo("Forma de pagamento")
            Escolha(listOf<Long?>(null) + e.cartoes.filter { it.ativo || it.id == cartaoId }.map { it.id }, cartaoId,
                { id -> if (id == null) "Direto da conta" else "Cartão ${e.nomeCartao(id)}" }, { cartaoId = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Ativa", Modifier.weight(1f))
            Switch(checked = ativo, onCheckedChange = { ativo = it })
        }
        OutlinedTextField(observacoes, { observacoes = it }, label = { Text("Observações (opcional)") }, modifier = Modifier.fillMaxWidth())
    }
    if (confirmar && original != null) {
        ConfirmarDialog(
            "Excluir \"${original.nome}\"?",
            "Some dos meses abertos a partir do atual (se ainda não foi pago). O que já foi pago continua no histórico. " +
                "Se a despesa só acabou, prefira definir o último mês.",
            aoConfirmar = { vm.executar("Despesa excluída.", aoConcluir = aoFechar) { excluirModelo(original.id) } },
            aoCancelar = { confirmar = false },
        )
    }
}

@Composable
private fun FormModeloLazer(vm: PrincipalViewModel, original: ModeloLazer?, aoFechar: () -> Unit) {
    var nome by remember { mutableStateOf(original?.nome ?: "") }
    var valor by remember { mutableStateOf(original?.valor?.paraCampo() ?: "") }
    var ativo by remember { mutableStateOf(original?.ativo ?: true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf(false) }
    FormularioDialog(
        if (original == null) "Novo item de lazer padrão" else "Item de lazer padrão", erro, aoFechar,
        aoConfirmar = {
            val v = zeroSeVazio(valor)
            if (v == null) erro = "Confira o valor."
            else vm.executar("Item salvo.", aoErro = { erro = it }, aoConcluir = aoFechar) {
                salvarModeloLazer(ModeloLazer(original?.id ?: 0, nome, v, ativo))
            }
        },
        aoExcluir = original?.let { { confirmar = true } },
    ) {
        CampoTexto(nome, { nome = it }, "Nome (ex.: Comida fora)")
        CampoDinheiro(valor, { valor = it }, "Limite mensal")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Ativo", Modifier.weight(1f))
            Switch(checked = ativo, onCheckedChange = { ativo = it })
        }
        Text("Ajustes feitos em um mês específico (em Planejar) não são sobrescritos.",
            style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
    }
    if (confirmar && original != null) {
        ConfirmarDialog("Excluir item padrão?", "\"${original.nome}\" deixa de ser criado nos próximos meses. Itens com gastos são mantidos.",
            aoConfirmar = { vm.executar("Item excluído.", aoConcluir = aoFechar) { excluirModeloLazer(original.id) } },
            aoCancelar = { confirmar = false })
    }
}

@Composable
private fun FormCartao(vm: PrincipalViewModel, original: Cartao?, aoFechar: () -> Unit) {
    var nome by remember { mutableStateOf(original?.nome ?: "") }
    var fechamento by remember { mutableStateOf(original?.diaFechamento?.toString() ?: "") }
    var vencimento by remember { mutableStateOf(original?.diaVencimento?.toString() ?: "") }
    var ativo by remember { mutableStateOf(original?.ativo ?: true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf(false) }
    FormularioDialog(
        if (original == null) "Novo cartão" else "Cartão", erro, aoFechar,
        aoConfirmar = {
            val f = fechamento.toIntOrNull(); val v = vencimento.toIntOrNull()
            if (f == null || v == null) erro = "Informe os dias de fechamento e vencimento."
            else vm.executar("Cartão salvo.", aoErro = { erro = it }, aoConcluir = aoFechar) {
                salvarCartao(Cartao(original?.id ?: 0, nome, f, v, ativo))
            }
        },
        aoExcluir = original?.let { { confirmar = true } },
    ) {
        CampoTexto(nome, { nome = it }, "Nome (ex.: Nubank)")
        CampoTexto(fechamento, { fechamento = it.filter(Char::isDigit).take(2) }, "Dia de fechamento", numerico = true,
            suporte = "Compras a partir deste dia vão para a fatura seguinte")
        CampoTexto(vencimento, { vencimento = it.filter(Char::isDigit).take(2) }, "Dia de vencimento", numerico = true)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Ativo", Modifier.weight(1f))
            Switch(checked = ativo, onCheckedChange = { ativo = it })
        }
    }
    if (confirmar && original != null) {
        ConfirmarDialog("Excluir cartão?", "Só é possível excluir cartões sem nenhum uso. Se já foi usado, desative.",
            aoConfirmar = { vm.executar("Cartão excluído.", aoConcluir = aoFechar) { excluirCartao(original.id) } },
            aoCancelar = { confirmar = false })
    }
}
