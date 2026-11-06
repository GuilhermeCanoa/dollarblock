# Spec: Extrato "Calendário" + histórico de 7 dias + passe livre do 1º dia

**Status:** done
**Épico:** E24
**Data de criação:** 2026-11-05
**Última atualização:** 2026-11-06

---

## Contexto

O Extrato era a tela mais fraca do app. Tinha um gráfico de linhas por app que, com uso diário
(discreto e cheio de picos), virava sempre um serrote. O donut e três `MetricCard`s repetiam o
mesmo número, e o visual eram cards Material empilhados, a cara da Home antiga.

O usuário escolheu, entre três propostas, a **visão de calendário**, com a condição de **não
repetir informação que já está na Home**. A Home já mostra: uso de hoje por app, tentativas de
hoje, próximo app a fechar, carteira do mês (pago, salvo, passes pagos/recusados) e preço do minuto.

Duas mudanças de dados/fluxo vêm junto:
- **Histórico de 7 dias:** ao começar a monitorar um app, preencher os 7 dias anteriores com o
  histórico que o Android já guarda. Antes, o histórico só começava no dia em que o app passava a
  ser monitorado.
- **Passe livre do 1º dia:** no dia da instalação, se o usuário define um limite menor ou igual ao
  uso que já teve hoje, o DollarBlock pergunta se ele quer um passe livre (sem cobrança) antes de
  bloquear.

## Requisitos

- R1: Extrato no visual da Home: glow no topo, rótulos mono e números Jakarta. Sem donut, sem
  gráfico de linhas e sem `MetricCard`.
- R2: Períodos Semana · Mês · Tudo. O "Hoje" sai, porque a Home já é o dia.
- R3: Herói "dias dentro do limite" (X de Y), com a sequência atual.
- R4: Calendário com uma bolinha por dia. Cores:
  - verde: dentro do limite;
  - âmbar: trancou e o usuário aguentou;
  - vermelho: pagou passe, ou passou do limite antes do DollarBlock;
  - anel mint: passe livre ou cortesia;
  - vazado: sem dado ou sem limite.
  Dias de antes do DollarBlock têm contorno tracejado.
- R5: Tocar num dia mostra o extrato daquele dia: uso, custo e desfecho de cada app.
- R6: Tiles só com dados que a Home não tem: média por dia (tempo + custo) e dia mais caro.
- R7: Lista de apps com a tira de dias do período e "x de y dias dentro".
- R8: Ao monitorar um app, os 7 dias anteriores são gravados (`insertIfNotExists`, sem
  sobrescrever). Esses dias nunca geram bloqueio nem cobrança.
- R9: No dia da instalação, se `uso de hoje ≥ novo limite`, aparece o diálogo do passe livre.
  - "Sim": o app fica liberado até a meia-noite, com um recibo de R$ 0 e método `passe_livre`.
  - "Não": só salva o limite.
- R10: O passe livre não conta como pago nem como resistido na carteira, igual à cortesia.

## Tarefas

- [x] T1: Extrair componentes visuais da Home para `core/designsystem/components/HomeStyle.kt`
- [x] T2: `StatementCalendar` (função pura) + testes
- [x] T3: Reescrever `StatisticsViewModel` e `StatisticsScreen`
- [x] T4: `UsageStatsProvider.getPastDaysUsage` + `MonitoredAppRepository.backfillUsage`
- [x] T5: `PaymentMethod.FREE_PASS` + `isFree()` em `MoneyReport`/`HistoryScreen`
- [x] T6: Oferta do passe livre do 1º dia em `AppsViewModel`/`AppsScreen` + testes
- [x] T7: Strings en/pt/hi
- [x] T8: Validar no emulador (CA1–CA3 + smoke test verde)

## Critérios de aceite

- CA1: `./gradlew :app:testDebugUnitTest` verde.
- CA2: Ao monitorar um app no emulador, os dias anteriores aparecem no Extrato com contorno
  tracejado.
- CA3: No dia da instalação, um limite abaixo do uso de hoje abre o diálogo. "Sim" libera o app e
  gera o recibo "Passe livre"; "Não" bloqueia na próxima abertura.

## Notas / Decisões

- O limite não tem histórico, então o status de cada dia usa o **limite atual** do app. Trocar o
  limite recolore os dias passados. Aceitável por enquanto. Se incomodar, o caminho é gravar o
  limite vigente em `DailyUsageEntity`.
- Os dias do backfill são os anteriores ao dia de `createdAt` do `MonitoredAppEntity`. Neles não
  existem eventos de bloqueio, então passar do limite vira vermelho ("teria pagado").
- O "1º dia" vem de `PackageInfo.firstInstallTime` do próprio DollarBlock, sem pref nova.
  Reinstalar conta como novo 1º dia, o que é aceitável.
- No dia da instalação, o smoke test (`pm clear` não muda `firstInstallTime`) pode cair na
  oferta ao definir o limite de 1 min. O script recusa ("pode bloquear"), porque quer o bloqueio.
- A oferta de passe livre aparece no máximo uma vez por app por dia (guardada só em memória no
  ViewModel). Se o usuário recusar e editar o limite de novo, não pergunta outra vez enquanto a
  tela estiver aberta.
