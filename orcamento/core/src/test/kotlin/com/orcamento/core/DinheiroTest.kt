package com.orcamento.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

fun r(texto: String): Dinheiro = Dinheiro.interpretar(texto) ?: error("valor inválido no teste: $texto")

inline fun esperaFalha(trecho: String? = null, bloco: () -> Unit): RegraViolada {
    try {
        bloco()
    } catch (e: RegraViolada) {
        if (trecho != null && !(e.message ?: "").contains(trecho)) throw AssertionError("Mensagem '${e.message}' não contém '$trecho'")
        return e
    }
    throw AssertionError("Era esperado RegraViolada")
}

class DinheiroTest {
    @Test fun interpretaFormatosBrasileiros() {
        assertEquals(5357L, r("53,57").centavos)
        assertEquals(113600L, r("1.136,00").centavos)
        assertEquals(113600L, r("1.136").centavos)
        assertEquals(5000L, r("R$ 50").centavos)
        assertEquals(1250L, r("12.5").centavos)
        assertEquals(50L, r("0,5").centavos)
        assertEquals(123456789L, r("1.234.567,89").centavos)
    }

    @Test fun rejeitaValoresInvalidos() {
        assertNull(Dinheiro.interpretar(""))
        assertNull(Dinheiro.interpretar("abc"))
        assertNull(Dinheiro.interpretar("-10"))
        assertNull(Dinheiro.interpretar("1,234"))
        assertNull(Dinheiro.interpretar("1,2,3"))
    }

    @Test fun formataEmReais() {
        assertEquals("R$ 1.136,00", r("1136").formatar())
        assertEquals("R$ 0,05", Dinheiro(5).formatar())
        assertEquals("-R$ 53,30", (r("300") - r("353,30")).formatar())
        assertEquals("R$ 1.234.567,89", Dinheiro(123456789).formatar())
        assertEquals("53,57", r("53,57").paraCampo())
    }

    @Test fun somaSemErroDeArredondamento() {
        assertEquals(r("0,30"), r("0,10") + r("0,20"))
        val dezMil = List(10_000) { r("0,01") }.somar()
        assertEquals(r("100"), dezMil)
    }

    @Test fun percentualArredondaENaoDividePorZero() {
        assertEquals(52, Percentual.de(r("182,57"), r("350")))
        assertEquals(114, Percentual.de(r("400"), r("350")))
        assertNull(Percentual.de(r("10"), Dinheiro.ZERO))
        assertEquals(0, Percentual.de(Dinheiro.ZERO, r("10")))
    }
}
