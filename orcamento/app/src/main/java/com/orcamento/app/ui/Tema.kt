package com.orcamento.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.orcamento.core.Nivel
import com.orcamento.core.Situacao

private val Claro = lightColorScheme(
    primary = Color(0xFF1B6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE8DD),
    onPrimaryContainer = Color(0xFF00281A),
    secondary = Color(0xFF4D6358),
    secondaryContainer = Color(0xFFD0E8DA),
    background = Color(0xFFF7FAF7),
    surface = Color(0xFFF7FAF7),
    surfaceVariant = Color(0xFFDDE5DE),
)

private val Escuro = darkColorScheme(
    primary = Color(0xFF8FD5B5),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFCFE8DD),
    secondary = Color(0xFFB4CCBF),
    secondaryContainer = Color(0xFF364B41),
)

@Composable
fun TemaOrcamento(conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Escuro else Claro, content = conteudo)
}

/** Cores com significado fixo. Sempre acompanhadas de texto — nunca a cor sozinha. */
object Cores {
    @Composable
    fun ok() = if (isSystemInDarkTheme()) Color(0xFF81C995) else Color(0xFF2E7D32)

    @Composable
    fun atencao() = if (isSystemInDarkTheme()) Color(0xFFFFC266) else Color(0xFF9A5B00)

    @Composable
    fun acima() = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFC62828)

    @Composable
    fun neutra() = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun doNivel(n: Nivel) = when (n) {
        Nivel.DENTRO -> ok()
        Nivel.ATENCAO -> atencao()
        Nivel.ACIMA -> acima()
        Nivel.SEM_LIMITE -> neutra()
    }

    @Composable
    fun daSituacao(s: Situacao) = when (s) {
        Situacao.PAGO -> ok()
        Situacao.PROXIMO -> atencao()
        Situacao.VENCIDO -> acima()
        Situacao.PENDENTE -> neutra()
    }
}
