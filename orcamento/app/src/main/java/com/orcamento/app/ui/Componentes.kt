package com.orcamento.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orcamento.core.Datas
import com.orcamento.core.Dinheiro
import com.orcamento.core.Envelope
import com.orcamento.core.Nivel
import com.orcamento.core.Situacao
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@Composable
fun SeletorMes(mes: YearMonth, fechado: Boolean, aoAnterior: () -> Unit, aoProximo: () -> Unit, aoHoje: () -> Unit, ehMesAtual: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = aoAnterior) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fechado) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 4.dp).width(16.dp))
                }
                Text(Datas.formatarMes(mes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (fechado) Text("Mês fechado", style = MaterialTheme.typography.labelSmall, color = Cores.neutra())
            else if (!ehMesAtual) Text("Voltar para o mês atual", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = aoHoje).padding(2.dp))
        }
        IconButton(onClick = aoProximo) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo mês") }
    }
}

@Composable
fun Secao(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    acao: (@Composable () -> Unit)? = null,
    aoTocar: (() -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .then(if (aoTocar != null) Modifier.clickable(onClick = aoTocar) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (subtitulo != null) Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
                }
                acao?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            conteudo()
        }
    }
}

@Composable
fun LinhaValor(rotulo: String, valor: Dinheiro, destaque: Boolean = false, cor: Color? = null, prefixo: String = "") {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            rotulo, Modifier.weight(1f),
            style = if (destaque) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (destaque) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            prefixo + valor.formatar(),
            style = if (destaque) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Medium,
            color = cor ?: Color.Unspecified,
        )
    }
}

@Composable
fun Barra(fracao: Float, cor: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fracao.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp),
        color = cor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

fun fracao(parte: Dinheiro, total: Dinheiro): Float =
    if (total.centavos <= 0) (if (parte.ehPositivo) 1f else 0f) else (parte.centavos.toFloat() / total.centavos.toFloat())

/** Barra + textos de um orçamento: quanto gastou, de quanto, quanto resta ou quanto passou. */
@Composable
fun ResumoEnvelope(envelope: Envelope, mostrarReserva: Boolean = true) {
    val cor = Cores.doNivel(envelope.nivel)
    val pct = envelope.percentualUsado
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(envelope.gasto.formatar(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(" de ${envelope.disponivel.formatar()}", style = MaterialTheme.typography.bodyMedium, color = Cores.neutra(),
                modifier = Modifier.padding(bottom = 2.dp))
            Spacer(Modifier.weight(1f))
            if (pct != null) Text("$pct%", style = MaterialTheme.typography.labelLarge, color = cor)
        }
        Spacer(Modifier.height(6.dp))
        Barra(fracao(envelope.gasto, envelope.disponivel), cor)
        Spacer(Modifier.height(6.dp))
        val texto = when (envelope.nivel) {
            Nivel.ACIMA -> "Passou ${envelope.excesso.formatar()} do limite"
            Nivel.SEM_LIMITE -> "Nenhum limite definido"
            else -> "Resta ${envelope.restante.formatar()} · ${envelope.nivel.rotulo.lowercase()}"
        }
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = cor, fontWeight = FontWeight.Medium)
        if (mostrarReserva && envelope.coberturaReserva.ehPositivo) {
            Text("Inclui ${envelope.coberturaReserva.formatar()} da reserva de lazer", style = MaterialTheme.typography.bodySmall, color = Cores.neutra())
        }
    }
}

@Composable
fun EtiquetaSituacao(s: Situacao, rotuloPago: String = s.rotulo) {
    val cor = Cores.daSituacao(s)
    Surface(shape = RoundedCornerShape(8.dp), color = cor.copy(alpha = 0.12f)) {
        Text(
            if (s == Situacao.PAGO) rotuloPago else s.rotulo,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = cor,
        )
    }
}

@Composable
fun Etiqueta(texto: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(texto, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EstadoVazio(texto: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Cores.neutra())
    }
}

@Composable
fun CampoDinheiro(
    texto: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    erro: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    aoConcluir: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    suporte: String? = null,
) {
    OutlinedTextField(
        value = texto,
        onValueChange = { novo -> aoMudar(novo.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(rotulo) },
        prefix = { Text("R$ ") },
        isError = erro,
        supportingText = if (suporte != null) { { Text(suporte) } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { aoConcluir() }, onNext = null),
        modifier = modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
    )
}

@Composable
fun CampoTexto(
    texto: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    aoConcluir: () -> Unit = {},
    numerico: Boolean = false,
    suporte: String? = null,
    erro: Boolean = false,
) {
    OutlinedTextField(
        value = texto,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        isError = erro,
        supportingText = if (suporte != null) { { Text(suporte) } } else null,
        keyboardOptions = KeyboardOptions(
            capitalization = if (numerico) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            keyboardType = if (numerico) KeyboardType.Number else KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { aoConcluir() }),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Linha de chips com rolagem horizontal. */
@Composable
fun <T> Escolha(opcoes: List<T>, selecionado: T?, rotulo: (T) -> String, aoEscolher: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.forEach { op ->
            FilterChip(selected = op == selecionado, onClick = { aoEscolher(op) }, label = { Text(rotulo(op)) })
        }
    }
}

@Composable
fun Rotulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.labelMedium, color = Cores.neutra(), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun ConfirmarDialog(titulo: String, texto: String, confirmar: String = "Excluir", aoConfirmar: () -> Unit, aoCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = { aoConfirmar(); aoCancelar() }) { Text(confirmar) } },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}

/** Diálogo de formulário com erro mostrado dentro dele (o snackbar ficaria escondido atrás). */
@Composable
fun FormularioDialog(
    titulo: String,
    erro: String?,
    aoCancelar: () -> Unit,
    aoConfirmar: () -> Unit,
    textoConfirmar: String = "Salvar",
    aoExcluir: (() -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                conteudo()
                if (erro != null) {
                    Text(erro, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp).semantics { contentDescription = "Erro: $erro" })
                }
            }
        },
        confirmButton = { TextButton(onClick = aoConfirmar) { Text(textoConfirmar) } },
        dismissButton = {
            Row {
                if (aoExcluir != null) TextButton(onClick = aoExcluir) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = aoCancelar) { Text("Cancelar") }
            }
        },
    )
}

fun LocalDate.paraMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun Long.millisUtcParaData(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorDataDialog(inicial: LocalDate, aoEscolher: (LocalDate) -> Unit, aoFechar: () -> Unit) {
    val estado = rememberDatePickerState(initialSelectedDateMillis = inicial.paraMillisUtc())
    DatePickerDialog(
        onDismissRequest = aoFechar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let { aoEscolher(it.millisUtcParaData()) }
                aoFechar()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    ) {
        DatePicker(state = estado)
    }
}

/** Campo de data que abre o calendário. */
@Composable
fun CampoData(rotulo: String, data: LocalDate, hoje: LocalDate, aoMudar: (LocalDate) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    val texto = when (data) {
        hoje -> "Hoje, ${Datas.formatarDiaMes(data)}"
        hoje.minusDays(1) -> "Ontem, ${Datas.formatarDiaMes(data)}"
        else -> Datas.formatar(data)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { aberto = true },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DateRange, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(rotulo, style = MaterialTheme.typography.labelSmall, color = Cores.neutra())
                Text(texto, style = MaterialTheme.typography.bodyLarge)
            }
            Text("Alterar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
    if (aberto) SeletorDataDialog(data, aoMudar) { aberto = false }
}

/** Interpreta "MM/AAAA" ou "M/AAAA". */
fun interpretarMes(texto: String): YearMonth? {
    val partes = texto.trim().split("/")
    if (partes.size != 2) return null
    val m = partes[0].toIntOrNull() ?: return null
    val a = partes[1].toIntOrNull() ?: return null
    if (m !in 1..12 || a !in 2000..2100) return null
    return YearMonth.of(a, m)
}

fun formatarMesCampo(mes: YearMonth): String = "%02d/%d".format(mes.monthValue, mes.year)
