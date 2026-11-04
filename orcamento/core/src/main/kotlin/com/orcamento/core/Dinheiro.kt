package com.orcamento.core

import java.math.BigDecimal

/**
 * Valor monetário em reais, guardado como número inteiro de centavos.
 * Nunca usamos Double/Float para dinheiro: R$ 0,10 + R$ 0,20 é exatamente R$ 0,30.
 */
@JvmInline
value class Dinheiro(val centavos: Long) : Comparable<Dinheiro> {

    operator fun plus(outro: Dinheiro) = Dinheiro(Math.addExact(centavos, outro.centavos))
    operator fun minus(outro: Dinheiro) = Dinheiro(Math.subtractExact(centavos, outro.centavos))
    operator fun unaryMinus() = Dinheiro(Math.negateExact(centavos))
    override fun compareTo(other: Dinheiro) = centavos.compareTo(other.centavos)

    val ehPositivo: Boolean get() = centavos > 0
    val ehNegativo: Boolean get() = centavos < 0
    val ehZero: Boolean get() = centavos == 0L

    /** O próprio valor, ou zero se for negativo. */
    fun minimoZero(): Dinheiro = if (centavos < 0) ZERO else this

    /** "R$ 1.136,00" ou "-R$ 53,30". */
    fun formatar(): String = if (centavos < 0) "-R$ ${semSimbolo(-centavos)}" else "R$ ${semSimbolo(centavos)}"

    /** "1.136,00" — para preencher campos de edição (sempre sem sinal). */
    fun paraCampo(): String = semSimbolo(kotlin.math.abs(centavos))

    override fun toString(): String = formatar()

    companion object {
        val ZERO = Dinheiro(0)

        fun reais(reais: Long, centavos: Long = 0) = Dinheiro(reais * 100 + centavos)

        private fun semSimbolo(valorPositivo: Long): String {
            val inteiros = (valorPositivo / 100).toString()
            val cents = (valorPositivo % 100).toString().padStart(2, '0')
            val agrupado = inteiros.reversed().chunked(3).joinToString(".").reversed()
            return "$agrupado,$cents"
        }

        private val FORMATO_VALIDO = Regex("""\d{1,13}(\.\d{1,2})?""")

        /**
         * Interpreta o que a pessoa digitou: "53,57", "1.136,00", "R$ 50", "50", "12.5".
         * Retorna null se o texto não for um valor válido e positivo com até 2 casas.
         */
        fun interpretar(texto: String): Dinheiro? {
            val t = texto.trim()
                .replace("R$", "")
                .replace(" ", "")
                .replace(" ", "")
            if (t.isEmpty() || t.startsWith("-")) return null
            val normalizado = when {
                t.contains(',') -> t.replace(".", "").replace(',', '.')
                t.count { it == '.' } > 1 -> t.replace(".", "")
                t.contains('.') && t.substringAfter('.').length == 3 -> t.replace(".", "")
                else -> t
            }
            if (!FORMATO_VALIDO.matches(normalizado)) return null
            return Dinheiro(BigDecimal(normalizado).movePointRight(2).longValueExact())
        }
    }
}

fun Iterable<Dinheiro>.somar(): Dinheiro = fold(Dinheiro.ZERO) { acc, v -> acc + v }

inline fun <T> Iterable<T>.somarDinheiro(seletor: (T) -> Dinheiro): Dinheiro =
    fold(Dinheiro.ZERO) { acc, item -> acc + seletor(item) }

fun maiorDinheiro(a: Dinheiro, b: Dinheiro): Dinheiro = if (a >= b) a else b

object Percentual {
    /** parte ÷ total em %, arredondado para o inteiro mais próximo. Null quando total ≤ 0 (sem base para comparar). */
    fun de(parte: Dinheiro, total: Dinheiro): Int? {
        if (total.centavos <= 0) return null
        if (parte.centavos <= 0) return 0
        return ((parte.centavos * 100 + total.centavos / 2) / total.centavos).toInt()
    }
}
