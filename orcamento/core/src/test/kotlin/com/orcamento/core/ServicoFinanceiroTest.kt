package com.orcamento.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ServicoFinanceiroTest {
    private lateinit var repo: RepositorioEmMemoria
    private var hoje = LocalDate.of(2026, 10, 20)
    private lateinit var s: ServicoFinanceiro
    private val out = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val set = YearMonth.of(2026, 9)

    @Before fun preparar() {
        repo = RepositorioEmMemoria()
        hoje = LocalDate.of(2026, 10, 20)
        s = ServicoFinanceiro(repo) { hoje }
    }

    private fun gasto(dia: Int, desc: String, valor: String, mes: YearMonth = out, destino: Destino = Destino.AVULSO, item: Long? = null, cartao: Long? = null) =
        s.salvarLancamento(Lancamento(data = mes.atDay(dia), descricao = desc, valor = r(valor), destino = destino, itemLazerId = item, cartaoId = cartao))

    private fun modelo(nome: String, valor: String, grupo: Grupo = Grupo.ESSENCIAL, dia: Int? = 10, inicio: YearMonth = out, fim: YearMonth? = null,
                       freq: Frequencia = Frequencia.MENSAL, cartao: Long? = null) =
        s.salvarModelo(ModeloRecorrente(nome = nome, valor = r(valor), grupo = grupo, diaVencimento = dia, mesInicio = inicio, mesFim = fim, frequencia = freq, cartaoId = cartao))

    // ───────── Gastos avulsos ─────────

    @Test fun exemploDoLimiteDeAvulsos() {
        s.salvarPlanoPadrao(PlanoPadrao(limiteAvulsos = r("350")))
        gasto(2, "Milk-shake", "28"); gasto(2, "Padaria", "14"); gasto(2, "Restaurante", "53,57")
        gasto(4, "Camiseta básica", "50"); gasto(5, "Chinelo", "37")
        val a = s.resumo(out).avulsos
        assertEquals(r("350"), a.limite)
        assertEquals(r("182,57"), a.gasto)
        assertEquals(r("167,43"), a.saldo)
        assertEquals(52, a.percentualUsado)
        assertEquals(Dinheiro.ZERO, a.excesso)
        assertEquals(Nivel.DENTRO, a.nivel)
    }

    @Test fun ultrapassarOLimiteNaoBloqueiaEMostraOExcesso() {
        s.salvarPlanoPadrao(PlanoPadrao(limiteAvulsos = r("350")))
        gasto(1, "Tênis", "400")
        val a = s.resumo(out).avulsos
        assertEquals(r("50"), a.excesso)
        assertEquals(-r("50"), a.saldo)
        assertEquals(Nivel.ACIMA, a.nivel)
    }

    @Test fun planilhaDeGastosDiariosDeOutubro() {
        s.salvarPlanoPadrao(PlanoPadrao(limiteAvulsos = r("300")))
        listOf(2 to "28", 2 to "14", 2 to "53,57", 4 to "50", 5 to "37", 6 to "16,33", 6 to "30", 6 to "25",
            13 to "16", 16 to "26,4", 20 to "17", 27 to "15", 27 to "25").forEachIndexed { i, (dia, v) -> gasto(dia, "Item $i", v) }
        val a = s.resumo(out).avulsos
        assertEquals(r("353,30"), a.gasto)
        assertEquals("-R$ 53,30", a.saldo.formatar())
        assertEquals(r("53,30"), a.excesso)
    }

    @Test fun lancamentoExigeDescricaoEValorPositivo() {
        esperaFalha("descrição") { gasto(1, "  ", "10") }
        esperaFalha("maior que zero") { s.salvarLancamento(Lancamento(data = out.atDay(1), descricao = "x", valor = Dinheiro.ZERO)) }
    }

    @Test fun editarEExcluirAtualizamOsTotais() {
        s.salvarPlanoPadrao(PlanoPadrao(limiteAvulsos = r("100")))
        val id = gasto(1, "Lanche", "30")
        s.salvarLancamento(repo.lancamento(id)!!.copy(valor = r("45")))
        assertEquals(r("45"), s.resumo(out).avulsos.gasto)
        s.excluirLancamento(id)
        assertEquals(Dinheiro.ZERO, s.resumo(out).avulsos.gasto)
    }

    @Test fun mudarAdataMoveOGastoDeMes() {
        val id = gasto(31, "Pizza", "40")
        s.salvarLancamento(repo.lancamento(id)!!.copy(data = LocalDate.of(2026, 11, 1)))
        assertEquals(Dinheiro.ZERO, s.resumo(out).avulsos.gasto)
        assertEquals(r("40"), s.resumo(nov).avulsos.gasto)
    }

    // ───────── Sobra do planejamento (a planilha) ─────────

    @Test fun sobraPlanejadaReproduzAPlanilha() {
        s.salvarPlanoPadrao(PlanoPadrao(renda = r("2115")))
        modelo("Conta Vivo", "143"); modelo("Ajuda mãe", "100"); modelo("Cabelo", "70"); modelo("Faculdade", "823")
        modelo("Claude", "125", Grupo.NAO_ESSENCIAL); modelo("Amazon Prime", "20", Grupo.NAO_ESSENCIAL)
        modelo("Max", "45", Grupo.NAO_ESSENCIAL); modelo("CSB", "120", Grupo.NAO_ESSENCIAL)
        s.salvarModeloLazer(ModeloLazer(nome = "Comida FDS", valor = r("150")))
        s.salvarModeloLazer(ModeloLazer(nome = "Rolê", valor = r("100")))
        s.salvarModeloLazer(ModeloLazer(nome = "Diversos", valor = r("100")))
        val res = s.resumo(out)
        assertEquals(r("1136"), res.totalEssenciais)
        assertEquals(r("310"), res.totalNaoEssenciais)
        assertEquals(r("350"), res.lazer.limite)
        assertEquals(r("319"), res.sobraPlanejada)
    }

    @Test fun estouroReduzASobraMasSobraDeEnvelopeNaoAumenta() {
        s.salvarPlanoPadrao(PlanoPadrao(renda = r("1000"), limiteAvulsos = r("300")))
        assertEquals(r("700"), s.resumo(out).sobraPlanejada)
        gasto(1, "Pouco", "100")
        assertEquals(r("700"), s.resumo(out).sobraAtual) // os 200 restantes continuam reservados para avulsos
        gasto(2, "Muito", "250")
        assertEquals(r("650"), s.resumo(out).sobraAtual) // estourou 50
    }

    // ───────── Recorrência ─────────

    @Test fun frequenciaEFimDaRecorrencia() {
        val m = ModeloRecorrente(nome = "IPVA", valor = r("100"), grupo = Grupo.ESSENCIAL, mesInicio = YearMonth.of(2026, 1),
            frequencia = Frequencia.TRIMESTRAL, mesFim = YearMonth.of(2026, 10))
        assertTrue(m.aplicaEm(YearMonth.of(2026, 1)))
        assertFalse(m.aplicaEm(YearMonth.of(2026, 2)))
        assertTrue(m.aplicaEm(YearMonth.of(2026, 4)))
        assertTrue(m.aplicaEm(YearMonth.of(2026, 10)))
        assertFalse(m.aplicaEm(YearMonth.of(2027, 1)))
        assertFalse(m.aplicaEm(YearMonth.of(2025, 12)))
        assertFalse(m.copy(ativo = false).aplicaEm(YearMonth.of(2026, 4)))
    }

    @Test fun vencimentoNoDia31AjustaParaOFimDoMes() {
        modelo("Aluguel", "500", dia = 31, inicio = nov)
        val c = s.resumo(nov).compromissos.single()
        assertEquals(LocalDate.of(2026, 11, 30), c.vencimento)
    }

    @Test fun recorrentesSaoGeradasUmaVezPorMes() {
        s.garantirMes(out)
        modelo("Faculdade", "680")
        s.garantirMes(nov); s.garantirMes(nov); s.resumo(nov)
        assertEquals(1, repo.compromissos(nov).size)
        assertEquals(1, repo.compromissos(out).size)
    }

    @Test fun compromissoExcluidoNaoVoltaSozinho() {
        s.garantirMes(out)
        modelo("Faculdade", "680")
        s.excluirCompromisso(repo.compromissos(out).single().id)
        s.garantirMes(out); s.resumo(out)
        assertTrue(repo.compromissos(out).isEmpty())
    }

    @Test fun editarModeloNaoMexeNoPassadoNemNoQueJaFoiPagoOuEditado() {
        hoje = LocalDate.of(2026, 9, 5)
        modelo("Faculdade", "823", inicio = set)
        s.garantirMes(set); s.garantirMes(out); s.garantirMes(nov)
        hoje = LocalDate.of(2026, 10, 20)
        // setembro (passado) já pago; outubro pago; novembro editado à mão
        s.marcarPago(repo.compromissos(set).single().id)
        s.marcarPago(repo.compromissos(out).single().id)
        val dez = YearMonth.of(2026, 12); s.garantirMes(dez)
        val cNov = repo.compromissos(nov).single()
        s.salvarCompromisso(cNov.copy(observacoes = "só observação")) // não conta como edição de valor
        assertFalse(repo.compromisso(cNov.id)!!.editadoManualmente)

        val modeloId = repo.modelos().single().id
        s.salvarModelo(repo.modelo(modeloId)!!.copy(valor = r("680")))

        assertEquals(r("823"), repo.compromissos(set).single().valorPlanejado)
        assertEquals(r("823"), repo.compromissos(out).single().valorPlanejado)
        assertEquals(r("680"), repo.compromissos(nov).single().valorPlanejado)
        assertEquals(r("680"), repo.compromissos(dez).single().valorPlanejado)
        assertEquals(r("680"), s.resumo(YearMonth.of(2027, 1)).compromissos.single().valorPlanejado)

        s.salvarCompromisso(repo.compromissos(dez).single().copy(valorPlanejado = r("700")))
        s.salvarModelo(repo.modelo(modeloId)!!.copy(valor = r("690")))
        assertEquals(r("700"), repo.compromissos(dez).single().valorPlanejado)
        assertEquals(r("690"), repo.compromissos(nov).single().valorPlanejado)
    }

    @Test fun encerrarModeloRemovePendentesFuturosEPreservaHistorico() {
        hoje = LocalDate.of(2026, 9, 5)
        modelo("Faculdade", "680", inicio = set)
        s.garantirMes(set); s.garantirMes(out); s.garantirMes(nov)
        s.marcarPago(repo.compromissos(set).single().id)
        hoje = LocalDate.of(2026, 10, 20)
        val m = repo.modelos().single()
        s.salvarModelo(m.copy(mesFim = out)) // formatura em outubro
        assertEquals(1, repo.compromissos(set).size)
        assertEquals(1, repo.compromissos(out).size)
        assertTrue(repo.compromissos(nov).isEmpty())
        assertTrue(s.resumo(YearMonth.of(2027, 2)).compromissos.isEmpty())
    }

    @Test fun excluirModeloMantemPagosDesvinculados() {
        s.garantirMes(out)
        modelo("Streaming", "45", Grupo.NAO_ESSENCIAL)
        s.garantirMes(nov)
        s.marcarPago(repo.compromissos(out).single().id)
        s.excluirModelo(repo.modelos().single().id)
        val pago = repo.compromissos(out).single()
        assertNull(pago.modeloId)
        assertTrue(repo.compromissos(nov).isEmpty())
    }

    @Test fun novoModeloEntraNoMesAtualJaCriado() {
        s.garantirMes(out)
        modelo("Academia", "90")
        assertEquals(1, s.resumo(out).compromissos.size)
    }

    // ───────── Compromissos e pagamento ─────────

    @Test fun pagamentoComValorDiferenteEDesfazer() {
        s.garantirMes(out)
        modelo("Conta de luz", "120")
        val c = repo.compromissos(out).single()
        s.marcarPago(c.id, LocalDate.of(2026, 10, 8), r("131,40"))
        var res = s.resumo(out)
        assertEquals(r("131,40"), res.totalEssenciais)
        assertEquals(r("120"), res.compromissosPlanejados)
        assertEquals(r("131,40"), res.contas.pago)
        assertEquals(Dinheiro.ZERO, res.contas.pendente)
        assertEquals(100, res.contas.percentualQuitado)
        s.desmarcarPago(c.id)
        res = s.resumo(out)
        assertEquals(r("120"), res.contas.pendente)
        assertNull(repo.compromisso(c.id)!!.dataPagamento)
    }

    @Test fun situacaoVencidoProximoPendente() {
        val c = Compromisso(mes = out, nome = "x", valorPlanejado = r("1"), grupo = Grupo.OUTRO)
        assertEquals(Situacao.VENCIDO, c.copy(vencimento = LocalDate.of(2026, 10, 19)).situacao(hoje))
        assertEquals(Situacao.PROXIMO, c.copy(vencimento = hoje).situacao(hoje))
        assertEquals(Situacao.PROXIMO, c.copy(vencimento = LocalDate.of(2026, 10, 23)).situacao(hoje))
        assertEquals(Situacao.PENDENTE, c.copy(vencimento = LocalDate.of(2026, 10, 24)).situacao(hoje))
        assertEquals(Situacao.PAGO, c.copy(vencimento = LocalDate.of(2026, 10, 1), pago = true).situacao(hoje))
        assertEquals(Situacao.PENDENTE, c.situacao(hoje))
    }

    @Test fun vencimentoForaDoMesERejeitado() {
        esperaFalha("dentro de") {
            s.salvarCompromisso(Compromisso(mes = out, nome = "x", valorPlanejado = r("1"), grupo = Grupo.OUTRO, vencimento = LocalDate.of(2026, 11, 1)))
        }
    }

    // ───────── Cartão de crédito ─────────

    @Test fun cicloDoCartao() {
        val nubank = Cartao(id = 1, nome = "Nubank", diaFechamento = 3, diaVencimento = 10)
        assertEquals(LocalDate.of(2026, 10, 10), CicloCartao.vencimentoDaCompra(nubank, LocalDate.of(2026, 10, 2)))
        assertEquals(LocalDate.of(2026, 11, 10), CicloCartao.vencimentoDaCompra(nubank, LocalDate.of(2026, 10, 3)))
        val outro = Cartao(id = 2, nome = "Outro", diaFechamento = 25, diaVencimento = 5)
        assertEquals(LocalDate.of(2026, 11, 5), CicloCartao.vencimentoDaCompra(outro, LocalDate.of(2026, 10, 24)))
        assertEquals(LocalDate.of(2026, 12, 5), CicloCartao.vencimentoDaCompra(outro, LocalDate.of(2026, 10, 25)))
        val fim = Cartao(id = 3, nome = "Fim", diaFechamento = 31, diaVencimento = 8)
        assertEquals(LocalDate.of(2026, 3, 8), CicloCartao.vencimentoDaCompra(fim, LocalDate.of(2026, 2, 27)))
        assertEquals(LocalDate.of(2026, 4, 8), CicloCartao.vencimentoDaCompra(fim, LocalDate.of(2026, 2, 28)))
    }

    @Test fun todaCompraCaiDentroDaJanelaDeBuscaDaSuaFatura() {
        for ((f, v) in listOf(3 to 10, 25 to 5, 31 to 8, 1 to 1, 15 to 15, 28 to 2)) {
            val c = Cartao(id = 1, nome = "c", diaFechamento = f, diaVencimento = v)
            var d = LocalDate.of(2026, 1, 1)
            while (d.year == 2026) {
                val mes = CicloCartao.mesDaFatura(c, d)
                val (de, ate) = CicloCartao.janelaDeBusca(mes)
                assertTrue("$f/$v $d", !d.isBefore(de) && !d.isAfter(ate))
                assertTrue(CicloCartao.vencimentoDaCompra(c, d).isAfter(d))
                d = d.plusDays(1)
            }
        }
    }

    @Test fun faturaNaoContaDuasVezes() {
        s.salvarPlanoPadrao(PlanoPadrao(renda = r("2000"), limiteAvulsos = r("300")))
        val nubank = s.salvarCartao(Cartao(nome = "Nubank", diaFechamento = 3, diaVencimento = 10))
        modelo("Conta Vivo", "143", dia = 15, cartao = nubank)    // cobrada no cartão em 15/10 → fatura de novembro
        modelo("Faculdade", "680", dia = 10)                      // boleto direto
        gasto(5, "Camiseta", "50", cartao = nubank)                // fatura de novembro
        gasto(6, "Padaria", "16,33")                               // Pix

        val r10 = s.resumo(out)
        // Competência (consumo): tudo entra uma vez, cada um no seu grupo
        assertEquals(r("823"), r10.totalEssenciais)
        assertEquals(r("66,33"), r10.avulsos.gasto)
        assertEquals(r("2000") - r("823") - r("300"), r10.sobraPlanejada)
        // Caixa de outubro: só o boleto (a fatura de novembro não é deste mês e a Vivo está dentro dela)
        assertEquals(listOf("Faculdade"), r10.contas.itens.map { it.nome })

        val r11 = s.resumo(nov)
        val itemFatura = r11.contas.itens.single { it.tipo == TipoConta.FATURA }
        assertEquals(r("193"), itemFatura.valor) // 50 da camiseta + 143 da Vivo de outubro
        // A Vivo de novembro (15/11) já vai para a fatura de dezembro
        assertEquals(r("143"), s.fatura(nubank, YearMonth.of(2026, 12)).totalCalculado)
        // A fatura não é gasto: totais de consumo de novembro não incluem os R$ 193
        assertEquals(r("823"), r11.totalEssenciais)
        assertEquals(Dinheiro.ZERO, r11.avulsos.gasto)
    }

    @Test fun pagarFaturaAjusteEDesfazer() {
        val nubank = s.salvarCartao(Cartao(nome = "Nubank", diaFechamento = 3, diaVencimento = 10))
        gasto(20, "Tênis", "300", mes = set, cartao = nubank) // fatura de outubro
        s.ajustarFatura(nubank, out, r("12,90"), "Anuidade")
        var f = s.fatura(nubank, out)
        assertEquals(r("312,90"), f.totalCalculado)
        s.pagarFatura(nubank, out, LocalDate.of(2026, 10, 10))
        var contas = s.resumo(out).contas
        assertEquals(r("312,90"), contas.pago)
        assertEquals(Dinheiro.ZERO, contas.pendente)
        s.desfazerPagamentoFatura(nubank, out)
        f = s.fatura(nubank, out)
        assertFalse(f.pago)
        contas = s.resumo(out).contas
        assertEquals(r("312,90"), contas.pendente)
    }

    @Test fun cartaoEmUsoNaoPodeSerExcluido() {
        val c = s.salvarCartao(Cartao(nome = "Nubank", diaFechamento = 3, diaVencimento = 10))
        gasto(1, "x", "1", cartao = c)
        esperaFalha("Desative") { s.excluirCartao(c) }
    }

    // ───────── Lazer ─────────

    @Test fun lazerPorItemEExigeItemDoMesmoMes() {
        s.garantirMes(out)
        s.salvarModeloLazer(ModeloLazer(nome = "Comida fora", valor = r("150")))
        s.salvarModeloLazer(ModeloLazer(nome = "Rolê", valor = r("100")))
        val itens = repo.itensLazer(out)
        val comida = itens.first { it.nome == "Comida fora" }.id
        gasto(3, "Hambúrguer", "60", destino = Destino.LAZER, item = comida)
        val res = s.resumo(out)
        assertEquals(r("250"), res.lazer.limite)
        assertEquals(r("60"), res.lazer.gasto)
        assertEquals(r("90"), res.itensLazer.first { it.item.id == comida }.envelope.saldo)
        esperaFalha("item de lazer") { gasto(3, "Cinema", "30", destino = Destino.LAZER) }
        esperaFalha("outro mês") { gasto(3, "Cinema", "30", mes = nov, destino = Destino.LAZER, item = comida) }
        esperaFalha("gasto(s)") { s.excluirItemLazer(comida) }
    }

    @Test fun ajusteDeItemNoMesNaoEhSobrescritoPeloModelo() {
        s.garantirMes(out)
        val id = s.salvarModeloLazer(ModeloLazer(nome = "Rolê", valor = r("100")))
        s.garantirMes(nov)
        val itemNov = repo.itensLazer(nov).single()
        s.salvarItemLazer(itemNov.copy(limite = r("200"))) // aniversário em novembro
        s.salvarModeloLazer(repo.modeloLazer(id)!!.copy(valor = r("120")))
        assertEquals(r("120"), repo.itensLazer(out).single().limite)
        assertEquals(r("200"), repo.itensLazer(nov).single().limite)
    }

    // ───────── Reserva de lazer e fechamento ─────────

    private fun mesComLazer(limite: String = "350") {
        s.salvarPlanoPadrao(PlanoPadrao(renda = r("2000")))
        s.salvarModeloLazer(ModeloLazer(nome = "Lazer", valor = r(limite)))
    }

    private fun itemLazer(mes: YearMonth): Long { s.garantirMes(mes); return repo.itensLazer(mes).single().id }

    @Test fun fechamentoTransfereSoOQueAPessoaEscolher() {
        hoje = LocalDate.of(2026, 9, 30)
        mesComLazer()
        gasto(10, "Cinema", "250", mes = set, destino = Destino.LAZER, item = itemLazer(set))
        val previa = s.previaFechamento(set)
        assertEquals(r("100"), previa.sobraLazer)
        esperaFalha("sobra de lazer") { s.fecharMes(set, r("100,01")) }
        assertFalse(s.estaFechado(set))
        assertEquals(Dinheiro.ZERO, s.saldoReserva()) // nada foi transferido sozinho
        s.fecharMes(set, r("80"))
        assertTrue(s.estaFechado(set))
        assertEquals(r("80"), s.saldoReserva())
        assertEquals(TipoMovimentoReserva.ENTRADA_FECHAMENTO, s.extratoReserva().single().tipo)
    }

    @Test fun mesFechadoNaoAceitaAlteracoes() {
        hoje = LocalDate.of(2026, 9, 30)
        mesComLazer()
        val id = gasto(1, "x", "10", mes = set)
        modelo("Conta", "50", inicio = set)
        s.fecharMes(set, Dinheiro.ZERO)
        esperaFalha("fechado") { gasto(2, "y", "10", mes = set) }
        esperaFalha("fechado") { s.excluirLancamento(id) }
        esperaFalha("fechado") { s.marcarPago(repo.compromissos(set).single().id) }
        esperaFalha("fechado") { s.salvarLancamento(repo.lancamento(id)!!.copy(data = out.atDay(1))) }
        esperaFalha("fechado") { s.atualizarPlanoMes(set, r("1"), r("1"), r("1"), false) }
        // alterar o modelo depois não mexe no mês fechado
        s.salvarModelo(repo.modelos().single().copy(valor = r("999")))
        assertEquals(r("50"), repo.compromissos(set).single().valorPlanejado)
    }

    @Test fun naoFechaMesFuturo() {
        esperaFalha("já começaram") { s.fecharMes(nov, Dinheiro.ZERO) }
    }

    @Test fun usarReservaAumentaOOrcamentoSemContarDuasVezes() {
        hoje = LocalDate.of(2026, 9, 30)
        mesComLazer()
        s.fecharMes(set, r("350")) // não gastou nada em setembro
        hoje = LocalDate.of(2026, 10, 20)
        val antes = s.resumo(out).sobraAtual
        s.usarReservaNoMes(out, Destino.LAZER, r("200"), "Show")
        gasto(15, "Ingresso do show", "500", destino = Destino.LAZER, item = itemLazer(out))
        val res = s.resumo(out)
        assertEquals(r("550"), res.lazer.disponivel)
        assertEquals(r("50"), res.lazer.saldo)
        assertEquals(Dinheiro.ZERO, res.lazer.excesso)
        assertEquals(antes, res.sobraAtual) // o show não comeu a sobra do mês
        assertEquals(r("150"), s.saldoReserva())
        assertEquals(r("500"), res.lazer.gasto) // o gasto aparece uma vez só
    }

    @Test fun reservaNaoFicaNegativa() {
        s.depositarNaReserva(r("100"), "Saldo inicial", null)
        esperaFalha("A reserva tem") { s.usarReservaNoMes(out, Destino.LAZER, r("100,01"), null) }
        esperaFalha("A reserva tem") { s.retirarDaReserva(r("150"), "x", null) }
        s.retirarDaReserva(r("100"), "Presente", "aniversário")
        assertEquals(Dinheiro.ZERO, s.saldoReserva())
    }

    @Test fun reabrirMesDesfazTransferenciaSoSeODinheiroAindaExiste() {
        hoje = LocalDate.of(2026, 9, 30)
        mesComLazer()
        s.fecharMes(set, r("300"))
        hoje = LocalDate.of(2026, 10, 20)
        val uso = s.usarReservaNoMes(out, Destino.LAZER, r("200"), null)
        esperaFalha("já foi usada") { s.reabrirMes(set) }
        assertTrue(s.estaFechado(set))
        assertEquals(r("100"), s.saldoReserva())
        s.excluirMovimentoReserva(uso)
        s.reabrirMes(set)
        assertFalse(s.estaFechado(set))
        assertEquals(Dinheiro.ZERO, s.saldoReserva())
        gasto(2, "Correção", "10", mes = set) // agora aceita
    }

    @Test fun entradaDeFechamentoSoSaiReabrindo() {
        hoje = LocalDate.of(2026, 9, 30)
        mesComLazer()
        s.fecharMes(set, r("50"))
        esperaFalha("reabra") { s.excluirMovimentoReserva(s.extratoReserva().single().id) }
    }

    @Test fun saldoDaReservaNoFimDeCadaMes() {
        hoje = LocalDate.of(2026, 9, 30)
        mesComLazer()
        s.fecharMes(set, r("100"))
        hoje = LocalDate.of(2026, 10, 31)
        s.usarReservaNoMes(out, Destino.AVULSO, r("40"), null)
        s.fecharMes(out, r("350"))
        val h = s.historico().associate { it.resumo.mes to it.saldoReservaNoFim }
        assertEquals(r("100"), h[set])
        assertEquals(r("410"), h[out])
    }

    // ───────── Investimentos ─────────

    @Test fun progressoDoInvestimento() {
        s.salvarPlanoPadrao(PlanoPadrao(renda = r("2000"), metaInvestimento = r("400")))
        s.salvarAporte(Aporte(data = out.atDay(5), valor = r("150")))
        var inv = s.resumo(out).investimento
        assertEquals(r("250"), inv.falta)
        assertEquals(38, inv.percentual)
        assertFalse(inv.metaAtingida)
        val sobra = s.resumo(out).sobraAtual
        s.salvarAporte(Aporte(data = out.atDay(25), valor = r("300")))
        inv = s.resumo(out).investimento
        assertTrue(inv.metaAtingida)
        assertEquals(r("50"), inv.acimaDaMeta)
        assertEquals(sobra - r("50"), s.resumo(out).sobraAtual) // aportar além da meta sai da sobra, mas não é consumo
        assertEquals(r("450"), s.totalInvestido())
        assertNull(ResumoInvestimento(Dinheiro.ZERO, r("10")).percentual)
    }

    // ───────── Plano por mês ─────────

    @Test fun mudarOMesNaoMudaOutrosMeses() {
        s.salvarPlanoPadrao(PlanoPadrao(renda = r("2000"), limiteAvulsos = r("300")))
        s.garantirMes(set); s.garantirMes(out); s.garantirMes(nov)
        s.atualizarPlanoMes(out, r("2000"), r("500"), Dinheiro.ZERO, tambemComoPadrao = false)
        assertEquals(r("300"), repo.planoMes(set)!!.limiteAvulsos)
        assertEquals(r("300"), repo.planoMes(nov)!!.limiteAvulsos)
        assertEquals(r("300"), s.garantirMes(YearMonth.of(2027, 1)).limiteAvulsos)
    }

    @Test fun novoPadraoAlcancaMesesFuturosQueNaoForamAjustados() {
        s.salvarPlanoPadrao(PlanoPadrao(renda = r("2000"), limiteAvulsos = r("300")))
        val dez = YearMonth.of(2026, 12)
        s.garantirMes(set); s.garantirMes(out); s.garantirMes(nov); s.garantirMes(dez)
        s.atualizarPlanoMes(dez, r("2000"), r("450"), Dinheiro.ZERO, tambemComoPadrao = false) // ajuste manual
        s.atualizarPlanoMes(out, r("2300"), r("350"), Dinheiro.ZERO, tambemComoPadrao = true)
        assertEquals(r("2000"), repo.planoMes(set)!!.renda)     // passado intacto
        assertEquals(r("2300"), repo.planoMes(nov)!!.renda)
        assertEquals(r("350"), repo.planoMes(nov)!!.limiteAvulsos)
        assertEquals(r("2300"), repo.planoMes(dez)!!.renda)
        assertEquals(r("450"), repo.planoMes(dez)!!.limiteAvulsos) // ajuste manual preservado
        assertEquals(r("2300"), s.garantirMes(YearMonth.of(2027, 3)).renda)
    }

    @Test fun erroNoMeioDaOperacaoNaoDeixaNadaPelaMetade() {
        hoje = LocalDate.of(2026, 9, 30)
        mesComLazer()
        s.garantirMes(set)
        val antes = repo.movimentosReserva().size
        esperaFalha { s.fecharMes(set, r("9999")) }
        assertEquals(antes, repo.movimentosReserva().size)
        assertFalse(s.estaFechado(set))
        assertNotNull(repo.planoMes(set))
    }
}
