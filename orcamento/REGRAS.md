# Regras financeiras do app

Este documento é a especificação: cada regra abaixo está implementada em `core/` e coberta por testes em `core/src/test`.
Nada é guardado como "indicador": todo número é recalculado a partir dos registros, então nenhum total fica desatualizado.

## 1. Dinheiro

- Valores guardados como **centavos inteiros** (`Long`). Nunca `Double`. R$ 0,10 + R$ 0,20 = R$ 0,30 exatamente.
- Entrada aceita `53,57`, `1.136,00`, `R$ 50`, `12.5`. Exibição sempre `R$ 1.136,00` / `-R$ 53,30`.
- Percentuais arredondados para o inteiro mais próximo; sem base (limite zero) não há percentual.

## 2. Conceitos (cada um tem seu próprio saldo)

| Conceito | O que é | Saldo |
|---|---|---|
| **Compromissos** | Contas do mês (essenciais, não essenciais, outros), geradas de recorrentes ou avulsas | planejado / pago |
| **Lazer do mês** | Soma dos itens de lazer do mês (ex.: Comida fora 150 + Rolê 100 + Diversos 100) | limite − gasto |
| **Gastos avulsos** | Compras do dia a dia fora do lazer | limite − gasto |
| **Reserva de lazer** | Dinheiro acumulado de sobras de lazer de meses fechados | entradas − saídas |
| **Investimento** | Meta do mês × aportes registrados | meta − aportado |
| **Sobra do mês** | O que a renda não destinou a nada | ver §4 |

Esses saldos **nunca são somados entre si**. Sobra de um não vira saldo de outro automaticamente.

## 3. Orçamentos (lazer e avulsos)

```
disponível = limite + cobertura da reserva no mês
saldo      = disponível − gasto           (fica negativo quando estoura)
excesso    = max(0, gasto − disponível)
% usado    = gasto ÷ disponível
```

- Situação: **dentro** (< 80%), **perto do limite** (80–100%), **acima** (gasto > disponível). Sempre exibida com texto e valor, não só cor.
- Passar do limite **não bloqueia** o cadastro. O app mostra quanto passou.
- Gasto de lazer precisa indicar o item de lazer do mesmo mês.

## 4. Sobra do mês (o "Valor Restante" da planilha)

```
sobra planejada = renda − compromissos − orçamento de lazer − limite de avulsos − meta de investimento
sobra atual     = sobra planejada − excesso de lazer − excesso de avulsos − aportes acima da meta
```

- Compromissos entram pelo **valor efetivo**: o valor pago, quando já pago; o planejado, quando pendente.
- Sobra de envelope (ex.: avulsos com R$ 50 sobrando) **não** aumenta a sobra do mês antes do fechamento. É dinheiro reservado para aquela finalidade.
- Gasto coberto pela reserva **não** reduz a sobra, porque esse dinheiro foi separado em meses anteriores.
- Conferência com a sua planilha: renda 2.115 − 1.136 − 310 − 350 = **R$ 319** (teste `sobraPlanejadaReproduzAPlanilha`).

## 5. Planejado × realizado × pago

- **Planejado**: `valorPlanejado` do compromisso; limite do orçamento; meta.
- **Realizado**: gasto registrado; aporte registrado; compromisso marcado como pago (ou conferido no cartão).
- **Pago (caixa)**: dinheiro que saiu da conta. Para compras no cartão, isso só acontece quando a fatura é paga.
- Marcar como pago guarda **data** e **valor efetivamente pago**, que pode ser diferente do planejado. Desmarcar apaga os dois.
- Situação de um compromisso: pago → **Pago**; vencimento antes de hoje → **Vencido**; vence em até 3 dias → **Vence em breve**; senão **Pendente**.

## 6. Cartão de crédito (sem contar duas vezes)

Regra: **a compra é o gasto; pagar a fatura é só quitar a dívida.**

- Cada gasto ou compromisso pode ser "no cartão X". Ele conta **uma vez**, no seu grupo, no mês em que aconteceu.
- A **fatura** é calculada pela soma das compras e compromissos do cartão naquele ciclo, mais um **ajuste** manual (anuidade, parcelas antigas, estornos).
- Ciclo: compra **antes** do dia de fechamento entra na fatura que fecha nesse mês. Compra **no dia do fechamento ou depois** entra na seguinte. A fatura vence no primeiro dia de vencimento após o fechamento.
- **Contas a pagar** (visão de caixa) = compromissos pagos direto da conta + faturas que vencem no mês. Compromissos no cartão não aparecem ali separados, porque já estão dentro da fatura.
- Na aba Contas, os compromissos no cartão ficam **dentro da fatura** (toque na fatura para abrir a lista).
  - **Marcar a fatura como paga** marca como pagos todos os compromissos dela, com a data do pagamento. Os que já estavam marcados mantêm a data e o valor que tinham.
  - **Desfazer o pagamento da fatura** desmarca os compromissos dela.
  - Cada compromisso pode ser marcado ou desmarcado individualmente dentro da lista, sem mudar a situação da fatura.
  - Compromissos de meses fechados não são alterados por essas ações.
  - Compromissos do mês que caem numa fatura de outro mês aparecem numa seção à parte, só para consulta.
- A fatura nunca entra em "gastos" nem na sobra do mês.
- **Sem integração bancária.** A fatura do app é a soma do que foi registrado. Se o valor do banco for diferente, a diferença vai no ajuste.

## 7. Recorrência e meses

- Cada mês tem **sua própria cópia** do plano (renda, limite de avulsos, meta) e dos compromissos e itens de lazer.
- Ao criar um mês, o app copia o plano padrão e gera os compromissos dos modelos que se aplicam (frequência, primeiro e último mês). O índice único `(mês, modelo)` no banco impede duplicar.
- Um compromisso excluído de um mês não volta sozinho.
- **Salvar um modelo** sincroniza só os meses **abertos a partir do mês atual**:
  - gera o compromisso onde falta;
  - atualiza os que estão pendentes e não foram editados à mão;
  - remove os pendentes e não editados onde o modelo deixou de valer (ex.: "Último mês = 12/2026" ao terminar a faculdade).
- **Meses passados e meses fechados nunca são alterados.** Pago ou editado à mão também não é sobrescrito.
- Mudar o orçamento de um mês afeta só aquele mês. Com "usar também nos próximos meses", vira o padrão e atualiza os meses futuros já criados, mas só os campos que ainda estavam iguais ao padrão antigo.
- Gastos e aportes pertencem ao mês da sua data. Mudar a data muda o mês.

## 8. Reserva de lazer

```
saldo da reserva = Σ entradas − Σ saídas
```

| Movimento | Quando |
|---|---|
| Entrada · sobra de mês fechado | no fechamento, com o valor que **você** escolher (0 até a sobra de lazer) |
| Entrada · depósito | manual (saldo inicial, dinheiro extra) |
| Saída · uso no mês | aumenta o orçamento de lazer **ou** de avulsos de um mês aberto |
| Saída · retirada | dinheiro sai da reserva para outra finalidade |

- Nenhuma transferência automática.
- A reserva não fica negativa: saídas maiores que o saldo são recusadas.
- Usar a reserva **não cria gasto**: aumenta o disponível do orçamento, e o gasto continua contado uma vez só.
- Toda movimentação guarda valor, data, descrição e motivo, e aparece no extrato.

## 9. Fechamento mensal

1. Disponível para meses que já começaram.
2. O app mostra a **sobra de lazer** (`disponível − gasto`, mínimo zero) e as contas pendentes.
3. Você escolhe quanto vai para a reserva: nada, metade, tudo ou um valor.
4. O mês fica **travado**: não aceita novos gastos, edições, pagamentos nem mudanças de orçamento. Mudanças em modelos também não o alcançam.
5. **Reabrir** desfaz a transferência para a reserva. Se esse dinheiro já foi usado, a reabertura é recusada e o app diz quanto faltaria. Isso evita um saldo de reserva negativo.

Contas pendentes **não impedem** o fechamento, apenas geram aviso.

## 10. Investimentos

```
falta       = max(0, meta − aportado)
progresso   = aportado ÷ meta        (sem percentual se meta = 0)
acima       = max(0, aportado − meta)
total geral = Σ todos os aportes
```

Definir a meta não registra aporte. Aporte não é gasto de consumo: a meta já sai da sobra planejada, e só o que passar da meta reduz a sobra.

## 11. Confiabilidade

- Toda operação roda em **transação**: ou grava tudo, ou nada (teste `erroNoMeioDaOperacaoNaoDeixaNadaPelaMetade`).
- Todas as operações do banco passam por uma única thread, então nunca se cruzam.
- Validações do núcleo: valores positivos, descrição obrigatória, vencimento dentro do mês, item de lazer do mesmo mês, cartão existente, mês aberto.

## Decisões que tomei por você (fácil mudar)

- **Aviso de vencimento**: 3 dias antes (`DIAS_AVISO_VENCIMENTO`).
- **"Perto do limite"**: a partir de 80% (`LIMIAR_ATENCAO`).
- **Sobra de avulsos** no fechamento não vai para a reserva, que é só de lazer, como você descreveu. A sobra de avulsos fica registrada no histórico do mês.
- **Sobra livre do mês** não é acumulada em lugar nenhum. O app não controla o saldo bancário.
- **Ajuste de fatura** entra só na visão de caixa. Se for um gasto real (ex.: juros), registre como gasto no cartão para ele entrar no orçamento.
