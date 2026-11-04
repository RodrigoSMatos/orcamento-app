# Orçamento — app Android de planejamento financeiro pessoal

App local-first: os dados ficam só no celular (SQLite). Sem login, sem servidor, sem integração bancária.
As regras de cálculo estão em [REGRAS.md](REGRAS.md).

## Como rodar

1. Instale o **Android Studio** (versão recente).
2. Abra a pasta `orcamento/` (File → Open). O Gradle baixa as dependências na primeira vez.
   - Se o Android Studio sugerir atualizar o Android Gradle Plugin ou o Kotlin, pode aceitar.
3. Conecte o celular com **depuração USB** ativada (Configurações → Sobre o telefone → toque 7× em "Número da versão" → Opções do desenvolvedor → Depuração USB), ou use um emulador.
4. Clique em **Run ▶** com o módulo `app` selecionado.

Para gerar um APK e instalar sem cabo: **Build → Build App Bundle(s) / APK(s) → Build APK(s)** e copie o arquivo `app/build/outputs/apk/debug/app-debug.apk` para o celular.

### Testes das regras

```
./gradlew :core:test
```

São 45 testes: limites de avulsos, sobra da planilha, recorrência, ciclo do cartão, fatura sem contagem dupla, reserva, fechamento e reabertura, investimentos e transações.

## Primeiro uso

1. **Ajustes → Valores padrão**: renda mensal, limite de gastos avulsos, meta de investimento.
2. **Ajustes → Recorrentes**: cadastre cada despesa fixa (faculdade, celular, ajuda em casa, assinaturas…). Nada vem pré-cadastrado, e cada pessoa monta as suas.
3. **Ajustes → Itens de lazer padrão**: ex.: Comida fora, Rolês, Diversos.
4. **Ajustes → Cartões**: nome, dia de fechamento e dia de vencimento.
5. Se já tem dinheiro guardado para lazer: **Planejar → Reserva → Depositar** (saldo inicial).

## Estrutura

```
core/   Regras de negócio em Kotlin puro (sem Android) + testes
  Dinheiro.kt            valores em centavos, formatação e leitura em R$
  Modelos.kt             entidades: compromisso, gasto, reserva, fatura…
  Datas.kt               datas BR, situação de vencimento, ciclo do cartão
  Calculadora.kt         todos os indicadores do mês (funções puras)
  ServicoFinanceiro.kt   casos de uso + validações + fechamento
  Repositorio.kt         interface de persistência
app/    Android (Jetpack Compose + Room)
  data/                  tabelas, DAO, repositório Room, backup
  ui/                    telas: Início, Gastos, Contas, Planejar, Ajustes
```

A interface só chama `ServicoFinanceiro`; nenhuma tela calcula saldo por conta própria.

## Backup

- **Ajustes → Salvar backup** gera um arquivo `.db` com todos os dados. Guarde no Drive ou no computador.
- **Restaurar backup** substitui os dados atuais. Se o arquivo for inválido, os dados atuais são mantidos.
- **Exportar gastos (CSV)** abre no Excel ou no Google Planilhas.
- O backup automático do Android (conta Google) também inclui o banco, quando está ativado no celular.

## O que esta versão NÃO faz

- Não se conecta a bancos, ao Nubank nem a corretoras. Tudo é lançado por você.
- Não sincroniza entre aparelhos (um celular só; use o backup para trocar).
- Sem notificações de vencimento por enquanto.

## Próximos passos possíveis

- Notificação no dia anterior ao vencimento (WorkManager).
- Widget na tela inicial para registrar um gasto sem abrir o app.
- Gráficos de evolução mensal.
- Sincronização na nuvem, que exigiria login e backend. Avaliar custo e complexidade antes.
