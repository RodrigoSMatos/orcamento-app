package com.orcamento.core

import java.time.LocalDate
import java.time.YearMonth

data class PreviaFechamento(
    val resumo: ResumoMes,
    /** Quanto sobrou do lazer (já contando o que veio da reserva). Máximo que pode ir para a reserva. */
    val sobraLazer: Dinheiro,
    val pendencias: List<ItemConta>,
)

data class ResumoHistorico(
    val resumo: ResumoMes,
    /** Saldo da reserva no último dia do mês. */
    val saldoReservaNoFim: Dinheiro,
)

/**
 * Todas as regras de negócio. Toda alteração passa por aqui e é validada antes de gravar.
 * Os indicadores nunca são guardados: são sempre recalculados a partir dos registros,
 * por isso não existe indicador "desatualizado".
 */
class ServicoFinanceiro(
    private val repo: Repositorio,
    private val hoje: () -> LocalDate = { LocalDate.now() },
) {
    val mesAtual: YearMonth get() = YearMonth.from(hoje())

    // ───────────────────────── Consultas ─────────────────────────

    fun planoPadrao(): PlanoPadrao = repo.planoPadrao()
    fun cartoes(): List<Cartao> = repo.cartoes().sortedBy { it.nome.lowercase() }
    fun modelos(): List<ModeloRecorrente> = repo.modelos().sortedBy { it.nome.lowercase() }
    fun modelosLazer(): List<ModeloLazer> = repo.modelosLazer().sortedBy { it.nome.lowercase() }
    fun itensLazer(mes: YearMonth): List<ItemLazer> = repo.itensLazer(mes).sortedBy { it.nome.lowercase() }
    fun aportes(mes: YearMonth): List<Aporte> =
        repo.aportes(mes.atDay(1), mes.atEndOfMonth()).sortedWith(compareByDescending<Aporte> { it.data }.thenByDescending { it.id })
    fun lancamentos(mes: YearMonth): List<Lancamento> =
        repo.lancamentos(mes.atDay(1), mes.atEndOfMonth()).sortedWith(compareByDescending<Lancamento> { it.data }.thenByDescending { it.id })

    // ───────────────────────── Meses ─────────────────────────

    /** Garante que o mês exista. Ao criar, copia o plano padrão e gera recorrentes e itens de lazer. */
    fun garantirMes(mes: YearMonth): PlanoMes = repo.transacao { repo.planoMes(mes) ?: criarMes(mes) }

    private fun criarMes(mes: YearMonth): PlanoMes {
        val padrao = repo.planoPadrao()
        val plano = PlanoMes(mes, padrao.renda, padrao.limiteAvulsos, padrao.metaInvestimento)
        repo.salvarPlanoMes(plano)
        repo.modelos().filter { it.aplicaEm(mes) }.forEach { repo.salvarCompromisso(it.gerarCompromisso(mes)) }
        repo.modelosLazer().filter { it.ativo }.forEach {
            repo.salvarItemLazer(ItemLazer(mes = mes, nome = it.nome, limite = it.valor, modeloId = it.id))
        }
        return plano
    }

    fun estaFechado(mes: YearMonth): Boolean = repo.planoMes(mes)?.fechado == true

    private fun exigirAberto(mes: YearMonth) {
        val plano = garantirMes(mes)
        if (plano.fechado) throw RegraViolada("${Datas.formatarMes(mes)} está fechado. Reabra o mês para alterar.")
    }

    /** Meses já criados, abertos, a partir do mês atual — os únicos que mudanças em modelos alcançam. */
    private fun mesesAbertosDesdeOAtual(): List<YearMonth> =
        repo.planosMes().filter { !it.fechado && it.mes >= mesAtual }.map { it.mes }.sorted()

    /**
     * Altera renda/limite de avulsos/meta de UM mês.
     * Se [tambemComoPadrao], vira o padrão para meses novos e também atualiza os meses futuros já criados
     * e abertos — mas só os campos que ainda estavam iguais ao padrão antigo (não sobrescreve ajuste manual).
     */
    fun atualizarPlanoMes(mes: YearMonth, renda: Dinheiro, limiteAvulsos: Dinheiro, metaInvestimento: Dinheiro, tambemComoPadrao: Boolean) {
        if (renda.ehNegativo || limiteAvulsos.ehNegativo || metaInvestimento.ehNegativo) throw RegraViolada("Os valores não podem ser negativos.")
        repo.transacao {
            exigirAberto(mes)
            val plano = repo.planoMes(mes)!!
            repo.salvarPlanoMes(plano.copy(renda = renda, limiteAvulsos = limiteAvulsos, metaInvestimento = metaInvestimento))
            if (tambemComoPadrao) {
                val antigo = repo.planoPadrao()
                val novo = PlanoPadrao(renda, limiteAvulsos, metaInvestimento)
                repo.salvarPlanoPadrao(novo)
                repo.planosMes().filter { !it.fechado && it.mes > mes }.forEach { p ->
                    repo.salvarPlanoMes(
                        p.copy(
                            renda = if (p.renda == antigo.renda) novo.renda else p.renda,
                            limiteAvulsos = if (p.limiteAvulsos == antigo.limiteAvulsos) novo.limiteAvulsos else p.limiteAvulsos,
                            metaInvestimento = if (p.metaInvestimento == antigo.metaInvestimento) novo.metaInvestimento else p.metaInvestimento,
                        ),
                    )
                }
            }
        }
    }

    fun salvarPlanoPadrao(p: PlanoPadrao) {
        if (p.renda.ehNegativo || p.limiteAvulsos.ehNegativo || p.metaInvestimento.ehNegativo) throw RegraViolada("Os valores não podem ser negativos.")
        repo.transacao { repo.salvarPlanoPadrao(p) }
    }

    fun resumo(mes: YearMonth): ResumoMes {
        val plano = garantirMes(mes)
        val janela = mes.atDay(1) to mes.atEndOfMonth()
        return Calculadora.resumir(
            plano = plano,
            compromissos = repo.compromissos(mes),
            itensLazer = repo.itensLazer(mes),
            lancamentosDoMes = repo.lancamentos(janela.first, janela.second),
            aportesDoMes = repo.aportes(janela.first, janela.second),
            movimentosReserva = repo.movimentosReserva(),
            faturas = faturasDoMes(mes),
            hoje = hoje(),
        )
    }

    /** Todos os meses criados, do mais recente para o mais antigo. */
    fun historico(): List<ResumoHistorico> =
        repo.planosMes().map { it.mes }.sortedDescending().map { mes ->
            ResumoHistorico(resumo(mes), saldoReserva(ate = mes.atEndOfMonth()))
        }

    // ───────────────────────── Lançamentos (gastos do dia a dia) ─────────────────────────

    /** Registra ou edita um gasto. Passar do limite NÃO bloqueia — o excesso aparece no resumo. */
    fun salvarLancamento(l: Lancamento): Long = repo.transacao {
        val descricao = l.descricao.trim()
        if (descricao.isEmpty()) throw RegraViolada("Informe a descrição do gasto.")
        if (!l.valor.ehPositivo) throw RegraViolada("O valor precisa ser maior que zero.")
        exigirAberto(l.mes)
        if (l.id != 0L) {
            val antigo = repo.lancamento(l.id) ?: throw RegraViolada("Gasto não encontrado.")
            exigirAberto(antigo.mes)
        }
        val itemId = when (l.destino) {
            Destino.AVULSO -> null
            Destino.LAZER -> {
                val item = l.itemLazerId?.let { repo.itemLazer(it) } ?: throw RegraViolada("Escolha em qual item de lazer este gasto entra.")
                if (item.mes != l.mes) throw RegraViolada("O item de lazer escolhido é de outro mês. Escolha um item de ${Datas.formatarMes(l.mes)}.")
                item.id
            }
        }
        if (l.cartaoId != null && repo.cartao(l.cartaoId) == null) throw RegraViolada("Cartão não encontrado.")
        repo.salvarLancamento(
            l.copy(
                descricao = descricao,
                itemLazerId = itemId,
                categoria = l.categoria?.trim()?.ifEmpty { null },
                observacoes = l.observacoes?.trim()?.ifEmpty { null },
            ),
        )
    }

    fun excluirLancamento(id: Long) = repo.transacao {
        val l = repo.lancamento(id) ?: return@transacao
        exigirAberto(l.mes)
        repo.excluirLancamento(id)
    }

    // ───────────────────────── Compromissos ─────────────────────────

    /** Cria um compromisso avulso do mês ou edita um existente (gerado ou manual). */
    fun salvarCompromisso(c: Compromisso): Long = repo.transacao {
        val nome = c.nome.trim()
        if (nome.isEmpty()) throw RegraViolada("Informe o nome do compromisso.")
        if (!c.valorPlanejado.ehPositivo) throw RegraViolada("O valor planejado precisa ser maior que zero.")
        if (c.vencimento != null && YearMonth.from(c.vencimento) != c.mes) {
            throw RegraViolada("O vencimento precisa estar dentro de ${Datas.formatarMes(c.mes)}.")
        }
        if (c.pago && c.valorPago != null && !c.valorPago.ehPositivo) throw RegraViolada("O valor pago precisa ser maior que zero.")
        if (c.cartaoId != null && repo.cartao(c.cartaoId) == null) throw RegraViolada("Cartão não encontrado.")
        exigirAberto(c.mes)
        var novo = c.copy(nome = nome, categoria = c.categoria?.trim()?.ifEmpty { null }, observacoes = c.observacoes?.trim()?.ifEmpty { null })
        if (c.id != 0L) {
            val antigo = repo.compromisso(c.id) ?: throw RegraViolada("Compromisso não encontrado.")
            if (antigo.mes != c.mes) throw RegraViolada("Não é possível mover um compromisso para outro mês. Exclua e cadastre no mês certo.")
            val mudouDados = antigo.nome != novo.nome || antigo.valorPlanejado != novo.valorPlanejado ||
                antigo.grupo != novo.grupo || antigo.categoria != novo.categoria ||
                antigo.vencimento != novo.vencimento || antigo.cartaoId != novo.cartaoId
            if (antigo.modeloId != null && mudouDados) novo = novo.copy(editadoManualmente = true)
        }
        repo.salvarCompromisso(novo)
    }

    /** Um toque: marca como pago hoje pelo valor planejado. Valor e data podem ser corrigidos. */
    fun marcarPago(id: Long, data: LocalDate? = null, valor: Dinheiro? = null) = repo.transacao {
        val c = repo.compromisso(id) ?: throw RegraViolada("Compromisso não encontrado.")
        exigirAberto(c.mes)
        if (valor != null && !valor.ehPositivo) throw RegraViolada("O valor pago precisa ser maior que zero.")
        repo.salvarCompromisso(c.copy(pago = true, dataPagamento = data ?: hoje(), valorPago = valor ?: c.valorPlanejado))
    }

    fun desmarcarPago(id: Long) = repo.transacao {
        val c = repo.compromisso(id) ?: throw RegraViolada("Compromisso não encontrado.")
        exigirAberto(c.mes)
        repo.salvarCompromisso(c.copy(pago = false, dataPagamento = null, valorPago = null))
    }

    fun excluirCompromisso(id: Long) = repo.transacao {
        val c = repo.compromisso(id) ?: return@transacao
        exigirAberto(c.mes)
        repo.excluirCompromisso(id)
    }

    // ───────────────────────── Modelos recorrentes ─────────────────────────

    /**
     * Salva um modelo e sincroniza os meses abertos a partir do mês atual:
     * - gera o compromisso onde o modelo se aplica e ainda não existe;
     * - atualiza os gerados que estão pendentes e não foram editados à mão;
     * - remove os pendentes não editados de meses onde o modelo deixou de se aplicar.
     * Meses passados e meses fechados nunca são alterados.
     */
    fun salvarModelo(m: ModeloRecorrente): Long = repo.transacao {
        val nome = m.nome.trim()
        if (nome.isEmpty()) throw RegraViolada("Informe o nome da despesa.")
        if (!m.valor.ehPositivo) throw RegraViolada("O valor precisa ser maior que zero.")
        if (m.diaVencimento != null && m.diaVencimento !in 1..31) throw RegraViolada("O dia de vencimento deve estar entre 1 e 31.")
        if (m.mesFim != null && m.mesFim < m.mesInicio) throw RegraViolada("O último mês não pode ser antes do primeiro.")
        if (m.cartaoId != null && repo.cartao(m.cartaoId) == null) throw RegraViolada("Cartão não encontrado.")
        val id = repo.salvarModelo(m.copy(nome = nome, categoria = m.categoria?.trim()?.ifEmpty { null }))
        sincronizarModelo(repo.modelo(id)!!)
        id
    }

    private fun sincronizarModelo(m: ModeloRecorrente) {
        for (mes in mesesAbertosDesdeOAtual()) {
            val gerados = repo.compromissos(mes).filter { it.modeloId == m.id }
            val livres = gerados.filter { !it.pago && !it.editadoManualmente }
            if (m.aplicaEm(mes)) {
                if (gerados.isEmpty()) repo.salvarCompromisso(m.gerarCompromisso(mes))
                else livres.forEach { repo.salvarCompromisso(m.atualizar(it)) }
            } else {
                livres.forEach { repo.excluirCompromisso(it.id) }
            }
        }
    }

    /** Exclui o modelo. Compromissos já pagos ou editados ficam, mas desvinculados. */
    fun excluirModelo(id: Long) = repo.transacao {
        val m = repo.modelo(id) ?: return@transacao
        sincronizarModelo(m.copy(ativo = false))
        repo.compromissosDoModelo(id).forEach { repo.salvarCompromisso(it.copy(modeloId = null)) }
        repo.excluirModelo(id)
    }

    // ───────────────────────── Lazer ─────────────────────────

    fun salvarModeloLazer(m: ModeloLazer): Long = repo.transacao {
        val nome = m.nome.trim()
        if (nome.isEmpty()) throw RegraViolada("Informe o nome do item de lazer.")
        if (m.valor.ehNegativo) throw RegraViolada("O valor não pode ser negativo.")
        val id = repo.salvarModeloLazer(m.copy(nome = nome))
        sincronizarModeloLazer(repo.modeloLazer(id)!!)
        id
    }

    private fun sincronizarModeloLazer(m: ModeloLazer) {
        for (mes in mesesAbertosDesdeOAtual()) {
            val itens = repo.itensLazer(mes).filter { it.modeloId == m.id }
            if (m.ativo) {
                if (itens.isEmpty()) repo.salvarItemLazer(ItemLazer(mes = mes, nome = m.nome, limite = m.valor, modeloId = m.id))
                else itens.filter { !it.editadoManualmente }.forEach { repo.salvarItemLazer(it.copy(nome = m.nome, limite = m.valor)) }
            } else {
                itens.filter { !it.editadoManualmente && repo.contarLancamentosDoItem(it.id) == 0 }
                    .forEach { repo.excluirItemLazer(it.id) }
            }
        }
    }

    fun excluirModeloLazer(id: Long) = repo.transacao {
        val m = repo.modeloLazer(id) ?: return@transacao
        sincronizarModeloLazer(m.copy(ativo = false))
        repo.itensLazerDoModelo(id).forEach { repo.salvarItemLazer(it.copy(modeloId = null)) }
        repo.excluirModeloLazer(id)
    }

    /** Cria ou ajusta um item de lazer de um mês específico. */
    fun salvarItemLazer(i: ItemLazer): Long = repo.transacao {
        val nome = i.nome.trim()
        if (nome.isEmpty()) throw RegraViolada("Informe o nome do item de lazer.")
        if (i.limite.ehNegativo) throw RegraViolada("O limite não pode ser negativo.")
        exigirAberto(i.mes)
        var novo = i.copy(nome = nome)
        if (i.id != 0L) {
            val antigo = repo.itemLazer(i.id) ?: throw RegraViolada("Item não encontrado.")
            if (antigo.mes != i.mes) throw RegraViolada("Não é possível mover um item de lazer para outro mês.")
            if (antigo.modeloId != null && (antigo.nome != novo.nome || antigo.limite != novo.limite)) {
                novo = novo.copy(editadoManualmente = true)
            }
        }
        repo.salvarItemLazer(novo)
    }

    fun excluirItemLazer(id: Long) = repo.transacao {
        val i = repo.itemLazer(id) ?: return@transacao
        exigirAberto(i.mes)
        val usos = repo.contarLancamentosDoItem(id)
        if (usos > 0) throw RegraViolada("Este item tem $usos gasto(s). Mova-os para outro item ou exclua-os antes.")
        repo.excluirItemLazer(id)
    }

    // ───────────────────────── Investimentos ─────────────────────────

    fun salvarAporte(a: Aporte): Long = repo.transacao {
        if (!a.valor.ehPositivo) throw RegraViolada("O valor do aporte precisa ser maior que zero.")
        exigirAberto(a.mes)
        if (a.id != 0L) {
            val antigo = repo.aporte(a.id) ?: throw RegraViolada("Aporte não encontrado.")
            exigirAberto(antigo.mes)
        }
        repo.salvarAporte(a.copy(descricao = a.descricao?.trim()?.ifEmpty { null }))
    }

    fun excluirAporte(id: Long) = repo.transacao {
        val a = repo.aporte(id) ?: return@transacao
        exigirAberto(a.mes)
        repo.excluirAporte(id)
    }

    fun totalInvestido(): Dinheiro = repo.totalAportado()

    // ───────────────────────── Reserva de lazer ─────────────────────────

    /** Saldo = entradas − saídas (até a data, se informada). */
    fun saldoReserva(ate: LocalDate? = null): Dinheiro =
        repo.movimentosReserva().filter { ate == null || !it.data.isAfter(ate) }.somarDinheiro { it.valorComSinal }

    fun extratoReserva(): List<MovimentoReserva> =
        repo.movimentosReserva().sortedWith(compareByDescending<MovimentoReserva> { it.data }.thenByDescending { it.id })

    /** Depósito manual: saldo inicial ou ajuste. */
    fun depositarNaReserva(valor: Dinheiro, descricao: String, motivo: String?, data: LocalDate = hoje()): Long = repo.transacao {
        if (!valor.ehPositivo) throw RegraViolada("O valor precisa ser maior que zero.")
        repo.salvarMovimentoReserva(
            MovimentoReserva(data = data, tipo = TipoMovimentoReserva.ENTRADA_MANUAL, valor = valor,
                descricao = descricao.trim().ifEmpty { "Depósito" }, motivo = motivo?.trim()?.ifEmpty { null }),
        )
    }

    /** Retira dinheiro da reserva para outra finalidade (não aumenta nenhum orçamento do mês). */
    fun retirarDaReserva(valor: Dinheiro, descricao: String, motivo: String?, data: LocalDate = hoje()): Long = repo.transacao {
        exigirSaldo(valor)
        repo.salvarMovimentoReserva(
            MovimentoReserva(data = data, tipo = TipoMovimentoReserva.SAIDA_RETIRADA, valor = valor,
                descricao = descricao.trim().ifEmpty { "Retirada" }, motivo = motivo?.trim()?.ifEmpty { null }),
        )
    }

    /**
     * Usa a reserva para aumentar o orçamento de lazer (ou de avulsos) de um mês.
     * O gasto continua contado uma única vez, no orçamento; a reserva só diz de onde veio o dinheiro.
     */
    fun usarReservaNoMes(mes: YearMonth, envelope: Destino, valor: Dinheiro, motivo: String?): Long = repo.transacao {
        exigirAberto(mes)
        exigirSaldo(valor)
        repo.salvarMovimentoReserva(
            MovimentoReserva(
                data = Datas.dataPadraoNoMes(mes, hoje()), tipo = TipoMovimentoReserva.SAIDA_COBERTURA, valor = valor,
                descricao = "Reforço de ${envelope.rotulo.lowercase()} em ${Datas.formatarMes(mes)}",
                motivo = motivo?.trim()?.ifEmpty { null }, mesReferencia = mes, envelope = envelope,
            ),
        )
    }

    private fun exigirSaldo(valor: Dinheiro) {
        if (!valor.ehPositivo) throw RegraViolada("O valor precisa ser maior que zero.")
        val saldo = saldoReserva()
        if (valor > saldo) throw RegraViolada("A reserva tem ${saldo.formatar()}. Não é possível usar ${valor.formatar()}.")
    }

    fun excluirMovimentoReserva(id: Long) = repo.transacao {
        val m = repo.movimentoReserva(id) ?: return@transacao
        if (m.tipo == TipoMovimentoReserva.ENTRADA_FECHAMENTO) {
            throw RegraViolada("Esta entrada veio do fechamento de ${m.mesReferencia?.let { Datas.formatarMes(it) }}. Para desfazer, reabra esse mês.")
        }
        m.mesReferencia?.let { exigirAberto(it) }
        if (m.tipo.entrada && saldoReserva() - m.valor < Dinheiro.ZERO) {
            throw RegraViolada("Esse dinheiro já foi usado. Excluir este depósito deixaria a reserva negativa.")
        }
        repo.excluirMovimentoReserva(id)
    }

    // ───────────────────────── Fechamento mensal ─────────────────────────

    fun previaFechamento(mes: YearMonth): PreviaFechamento {
        val r = resumo(mes)
        return PreviaFechamento(r, r.lazer.restante, r.contas.pendentes)
    }

    /**
     * Fecha o mês: trava alterações e, se [valorParaReserva] > 0, transfere essa parte da sobra de lazer para a reserva.
     * Nada é transferido sem que a pessoa informe o valor. Pendências não impedem o fechamento.
     */
    fun fecharMes(mes: YearMonth, valorParaReserva: Dinheiro) = repo.transacao {
        val plano = garantirMes(mes)
        if (plano.fechado) throw RegraViolada("${Datas.formatarMes(mes)} já está fechado.")
        if (mes > mesAtual) throw RegraViolada("Só é possível fechar meses que já começaram.")
        if (valorParaReserva.ehNegativo) throw RegraViolada("O valor não pode ser negativo.")
        val sobra = previaFechamento(mes).sobraLazer
        if (valorParaReserva > sobra) throw RegraViolada("A sobra de lazer é ${sobra.formatar()}. Não é possível guardar ${valorParaReserva.formatar()}.")
        if (valorParaReserva.ehPositivo) {
            repo.salvarMovimentoReserva(
                MovimentoReserva(
                    data = mes.atEndOfMonth(), tipo = TipoMovimentoReserva.ENTRADA_FECHAMENTO, valor = valorParaReserva,
                    descricao = "Sobra de lazer de ${Datas.formatarMes(mes)}", mesReferencia = mes,
                ),
            )
        }
        repo.salvarPlanoMes(plano.copy(fechado = true, fechadoEm = hoje()))
    }

    /** Reabre o mês e desfaz a transferência do fechamento — desde que esse dinheiro ainda esteja na reserva. */
    fun reabrirMes(mes: YearMonth) = repo.transacao {
        val plano = repo.planoMes(mes) ?: throw RegraViolada("Mês não encontrado.")
        if (!plano.fechado) throw RegraViolada("${Datas.formatarMes(mes)} já está aberto.")
        val entradas = repo.movimentosReserva().filter { it.tipo == TipoMovimentoReserva.ENTRADA_FECHAMENTO && it.mesReferencia == mes }
        val saldoSem = saldoReserva() - entradas.somarDinheiro { it.valor }
        if (saldoSem.ehNegativo) {
            throw RegraViolada(
                "A sobra deste mês já foi usada pela reserva. Para reabrir, a reserva precisaria de mais ${(-saldoSem).formatar()}.",
            )
        }
        entradas.forEach { repo.excluirMovimentoReserva(it.id) }
        repo.salvarPlanoMes(plano.copy(fechado = false, fechadoEm = null))
    }

    // ───────────────────────── Cartões e faturas ─────────────────────────

    fun salvarCartao(c: Cartao): Long = repo.transacao {
        if (c.nome.isBlank()) throw RegraViolada("Informe o nome do cartão.")
        if (c.diaFechamento !in 1..31 || c.diaVencimento !in 1..31) throw RegraViolada("Os dias devem estar entre 1 e 31.")
        repo.salvarCartao(c.copy(nome = c.nome.trim()))
    }

    fun excluirCartao(id: Long) = repo.transacao {
        val usos = repo.contarUsosDoCartao(id)
        if (usos > 0) throw RegraViolada("Este cartão está em $usos registro(s). Desative-o em vez de excluir.")
        repo.excluirCartao(id)
    }

    fun faturasDoMes(mes: YearMonth): List<FaturaCalculada> =
        repo.cartoes().map { fatura(it, mes) }.filter { it.cartao.ativo || it.temConteudo }

    fun fatura(cartaoId: Long, mes: YearMonth): FaturaCalculada =
        fatura(repo.cartao(cartaoId) ?: throw RegraViolada("Cartão não encontrado."), mes)

    private fun fatura(cartao: Cartao, mes: YearMonth): FaturaCalculada {
        val (de, ate) = CicloCartao.janelaDeBusca(mes)
        val compras = repo.lancamentos(de, ate)
            .filter { it.cartaoId == cartao.id && CicloCartao.mesDaFatura(cartao, it.data) == mes }
            .sortedBy { it.data }
        val compromissos = repo.compromissosEntre(mes.minusMonths(2), mes)
            .filter { it.cartaoId == cartao.id && CicloCartao.mesDaFatura(cartao, it.dataCobranca) == mes }
            .sortedBy { it.dataCobranca }
        return FaturaCalculada(cartao, mes, repo.fatura(cartao.id, mes), compras, compromissos)
    }

    /**
     * Paga a fatura e marca como pagos todos os compromissos que estão nela (numa ação só).
     * Compromissos já marcados mantêm a data e o valor que tinham. Compromissos de meses fechados não são tocados.
     */
    fun pagarFatura(cartaoId: Long, mes: YearMonth, data: LocalDate? = null, valor: Dinheiro? = null) = repo.transacao {
        exigirAberto(mes)
        val f = fatura(cartaoId, mes)
        val pago = valor ?: f.totalCalculado
        if (!pago.ehPositivo) throw RegraViolada("O valor pago precisa ser maior que zero.")
        val dataPagamento = data ?: hoje()
        val base = f.registro ?: Fatura(cartaoId = cartaoId, mesVencimento = mes)
        repo.salvarFatura(base.copy(pago = true, dataPagamento = dataPagamento, valorPago = pago))
        f.compromissos.filter { !it.pago && !estaFechado(it.mes) }.forEach {
            repo.salvarCompromisso(it.copy(pago = true, dataPagamento = dataPagamento, valorPago = it.valorPlanejado))
        }
    }

    /** Desfaz o pagamento da fatura e desmarca os compromissos dela (exceto os de meses fechados). */
    fun desfazerPagamentoFatura(cartaoId: Long, mes: YearMonth) = repo.transacao {
        exigirAberto(mes)
        val registro = repo.fatura(cartaoId, mes) ?: return@transacao
        repo.salvarFatura(registro.copy(pago = false, dataPagamento = null, valorPago = null))
        fatura(cartaoId, mes).compromissos.filter { it.pago && !estaFechado(it.mes) }.forEach {
            repo.salvarCompromisso(it.copy(pago = false, dataPagamento = null, valorPago = null))
        }
    }

    /** Ajuste da fatura: valores no cartão que não são gastos lançados no app. Pode ser negativo (estorno). */
    fun ajustarFatura(cartaoId: Long, mes: YearMonth, ajuste: Dinheiro, observacoes: String?) = repo.transacao {
        exigirAberto(mes)
        val base = repo.fatura(cartaoId, mes) ?: Fatura(cartaoId = cartaoId, mesVencimento = mes)
        repo.salvarFatura(base.copy(ajuste = ajuste, observacoes = observacoes?.trim()?.ifEmpty { null }))
    }
}
