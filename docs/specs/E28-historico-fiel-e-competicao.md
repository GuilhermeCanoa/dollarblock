# Spec: Histórico fiel (livro-razão de configurações) + competição contra si mesmo

**Status:** draft
**Épico:** E28
**Data de criação:** 2026-11-26
**Última atualização:** 2026-11-26

---

## Contexto

Hoje o app não tem memória de configuração. O Extrato (E24) colore cada dia comparando o uso com o
**limite atual** do app (`MonitoredAppEntity.dailyLimitMinutes`), então mudar um limite recolore o
passado inteiro. A spec E24 já registrava isso como dívida. Também não fica registrado quem
desligou o monitoramento, quem afrouxou o limite às 22h para não pagar, quem apertou para se
desafiar, quem tocou em "pagar" e desistiu, nem quem desligou a acessibilidade para fugir da tranca.

Sem isso não dá para fazer **competição contra si mesmo**: não há placar honesto se as regras do
jogo podem mudar no meio da partida sem deixar rastro.

Esta spec tem duas partes:
1. **Parte A — Livro-razão (decidido):** registro append-only e local de toda alteração de
   configuração e de todo momento de decisão do usuário, com uma função pura que reconstrói "o
   que valia" em qualquer instante.
2. **Parte B — Competição contra si mesmo (ideias a priorizar):** o que construir em cima do
   livro-razão, começando pelo calendário do Extrato.

Tudo continua **local** (E19 — sigilo bancário). Um dashboard geral fica para o futuro, fora
desta spec.

---

## Parte A — Livro-razão

### Modelo

Nova tabela append-only `ledger_events` (Room v4 → v5). Nunca há UPDATE nem DELETE: corrigir
algo é gravar um evento novo.

| Campo | Tipo | Nota |
|---|---|---|
| `id` | Long PK auto | |
| `timestamp` | Long | wall-clock ms |
| `epochDay` | Long | dia **local no momento da escrita**; congela o dia se o fuso mudar depois |
| `type` | String (enum `LedgerType`) | ver abaixo |
| `packageName` | String? | nulo para eventos globais |
| `appLabel` | String? | congelado na hora; o app pode ser desinstalado depois |
| `oldValue` / `newValue` | String? | ex.: limite em minutos; `null` = sem limite |
| `usedMillisAtEvent` | Long? | uso do app no dia no momento do evento: dá contexto para "afrouxou depois de estourar" |
| `source` | String | `APPS_SCREEN`, `FREE_PASS_DIALOG`, `BLOCK_SCREEN`, `SYSTEM`, `MIGRATION` |
| `meta` | String? | JSON pequeno para casos raros, sem schema rígido |

`block_events` e `unlock_events` **continuam como estão**. A linha do tempo do dia é um merge
das três fontes feito numa função pura (`DayTimeline`), sem duplicar dados.

### Tipos de evento (`LedgerType`)

**Configuração por app**
- `APP_MONITORED` / `APP_UNMONITORED` (toggle na tela Apps)
- `APP_REMOVED` (`deleteMonitoredApp`) e `APP_UNINSTALLED` (detectado no sync)
- `LIMIT_SET`: old → new. A direção (apertou/afrouxou/criou/removeu) é derivada, não gravada.

**Momentos de decisão na tranca** (hoje "recusou" é inferido: bloqueio sem recibo)
- `BLOCK_PAY_TAPPED`: tocou em pagar
- `BLOCK_PAY_CANCELED`: abriu o fluxo de pagamento e desistiu (o "quase pagou")
- `BLOCK_PAY_FAILED`: erro de Billing (E17)
- `BLOCK_LEFT`: saiu da tela sem pagar (recusa explícita)
- O pagamento em si continua em `unlock_events`.

**Integridade do jogo** (fuga)
- `GUARD_OFF` / `GUARD_ON`, com `meta.permission` = `ACCESSIBILITY` | `USAGE_ACCESS` | `OVERLAY`.
  Gravado no `onDestroy`/`onUnbind` do serviço quando possível, e sempre na detecção ao voltar
  ao app (`PermissionsProvider` no resume). O timestamp da detecção pode ser posterior ao
  desligamento real; nesse caso `meta.detected = true`.

**Configuração global** (mexe no preço e na percepção)
- `SALARY_SET`, `CURRENCY_SET`: o preço do minuto depende do salário (E14). Sem esse histórico
  o "custo do dia" do passado também é recalculado com o valor atual.

### Gênese (migração)

Na migração v4 → v5, gravar um `LIMIT_SET` (e um `APP_MONITORED`) por app existente, com
`source = MIGRATION` e `timestamp = createdAt`. Esses eventos têm `meta.approximate = true`:
"o limite atual valeu desde o começo", que é o que o E24 já assume hoje. Daí para frente o
histórico é fiel.

### Reconstrução: `ConfigTimeline` (pura, domain, testada)

```kotlin
object ConfigTimeline {
    fun limitAt(events: List<LedgerEvent>, pkg: String, atMs: Long): Int?
    fun monitoredAt(events: List<LedgerEvent>, pkg: String, atMs: Long): Boolean
    /** O "contrato do dia": limite vigente à 00:00 local. */
    fun contractFor(events: List<LedgerEvent>, pkg: String, day: Long, zone: ZoneId): Int?
    /** Mudanças de limite no dia, já classificadas (apertou/afrouxou/criou/removeu). */
    fun renegotiations(events: List<LedgerEvent>, pkg: String, day: Long): List<Renegotiation>
}
```

### Regra do placar (decisão de produto: confirmar)

- **Contrato do dia** = limite vigente à meia-noite. É contra ele que o dia é julgado.
- **Apertar no meio do dia** vale a partir do momento da mudança. Se estourar o limite mais
  apertado, mas não o contrato, o dia fica "dentro, com desafio perdido" e não pune a sequência.
  Apertar nunca prejudica o placar.
- **Afrouxar no meio do dia**: se o uso no momento já estava ≥ 80% do contrato ou acima dele,
  o dia ganha a marca **"renegociou"** (asterisco). Dia renegociado **não conta como dentro do
  limite e quebra a sequência**, mesmo que o uso tenha ficado abaixo do limite novo. Afrouxar
  com folga (ex.: de manhã, uso 5 de 30 min) é só ajuste: registra, mas não pune.
- **Tranca desligada** (`GUARD_OFF`) num dia em que o uso passou do contrato: tom novo
  **"fugiu"**, o pior de todos, acima de PAID. Pagar é honesto; fugir não é.
- Recolorir o passado deixa de acontecer: `StatementCalendar.appTone` passa a receber o
  contrato do dia em vez de `app.dailyLimitMinutes`.

### Requisitos

- R1: Toda chamada de escrita de configuração (`setMonitored`, `setDailyLimit`,
  `deleteMonitoredApp`, `setMonthlySalary`, `setCurrencyPreference`) grava o evento
  correspondente **na mesma transação/fluxo**, centralizada num `LedgerRepository`
  (`EventsModule`, ou um `LedgerModule` próprio, por causa da regra anti-conflito).
- R2: A tela de bloqueio grava `BLOCK_PAY_TAPPED`, `BLOCK_PAY_CANCELED`, `BLOCK_PAY_FAILED` e
  `BLOCK_LEFT`.
- R3: Mudança de permissão de guarda grava `GUARD_OFF`/`GUARD_ON` sem duplicar: só grava
  quando o estado muda em relação ao último evento.
- R4: Migração v5 com eventos de gênese; `exportSchema = true` + teste de migração (com o
  histórico valendo como placar, perder dado numa migração é perder o jogo).
- R5: `ConfigTimeline` e `DayTimeline` puros, com testes JVM cobrindo apertar, afrouxar com e
  sem folga, remover limite, desmonitorar e remonitorar, e virada de dia.
- R6: O Extrato (calendário, folha do dia, tira por app) usa o contrato do dia. Mudar um
  limite hoje não altera nenhum dia passado.
- R7: Backup: decidir se `ledger_events` entra no Auto Backup do Android. Recomendação: sim,
  porque o histórico sobreviveria a uma reinstalação. Sem isso, desinstalar zera o placar e
  vira uma forma de fuga. Conferir com E19.

---

## Parte B — Competição contra si mesmo (ideias)

Ordenadas por quanto dependem só do livro-razão e por impacto. Voz: gerente de banco seco (ver
MANIFESTO "Como falamos").

### B1. Folha do dia vira "livro-caixa" (calendário, extensão direta do E24)
Tocar num dia mostra a linha do tempo real:
`00:00 contrato: Instagram 30 min` → `14:10 abriu` → `18:02 tranca` → `18:03 tocou em pagar` →
`18:03 desistiu` → `21:40 limite 30 → 60 min ⚑ renegociado`.
O calendário ganha os tons novos (asterisco de renegociado e "fugiu").

### B2. Pagamentos com visão clara
Uma seção própria no Extrato, sem misturar com o resto:
- total pago no período, por app e por dia da semana/horário ("você paga às quartas, depois das 22h");
- **preço real do minuto comprado**: valor pago ÷ minutos usados depois do desbloqueio;
- "quase pagou": vezes que tocou em pagar e desistiu, e quanto isso guardou;
- passe livre e cortesia separados, sem contar como pago nem como resistido (E24 R10).

### B3. Rating de crédito de tempo (o motor da competição)
Uma nota estilo agência de risco (AAA → D), recalculada todo dia a partir do livro-razão:
- sobe com dias dentro, trancas aguentadas e limites apertados;
- desce com pagamento, renegociação e, principalmente, fuga.
Cada mudança de nota tem um motivo legível que entra no histórico: "Rebaixado de A para BBB:
renegociou o limite do TikTok às 23:10." O vocabulário de banco já existe no app, então isso
cabe sem inventar persona nova. É o candidato mais forte a "motivo para abrir o app todo dia".

### B4. Você vs. você do mês passado
Placar lado a lado na abertura do mês (fechamento da fatura): minutos, dias dentro, pagos,
recusados, renegociações e **soma dos limites** (você apertou ou afrouxou o próprio contrato?).
Uma linha de veredito no tom do gerente.

### B5. Recordes pessoais ("registrado em cartório")
Maior sequência, dia de menor uso, mais tempo sem abrir um app, mês mais barato, mais faturas
recusadas num mês. Bater um recorde aparece uma vez, como o parabéns do E27. Dá para começar só
com dados que já existem; o livro-razão garante que um recorde não foi conseguido afrouxando o
limite.

### B6. Fantasma da semana passada (Home)
Na Home, uma marca discreta na barra de uso: "a esta hora, na quarta passada, você estava com
42 min". Corrida contra si mesmo no tempo real, sem gamificação pesada.

### B7. Proposta de aperto (desafio que o próprio histórico sugere)
Quando o usuário fica bem abaixo do contrato por N dias ("média de 18 min num limite de 45"), o
gerente propõe apertar: "Seu limite está folgado. Proponho 30 min." Aceitar grava
`LIMIT_SET` com `source = SUGGESTION` e alimenta o rating. Recusar não pune.

### B8. Horário fraco
Mapa de calor dia da semana × hora com os momentos em que a tranca disparou, em que pagou e em
que renegociou. Mostra onde a pessoa perde o jogo.

### B9. Retrospectiva (mensal → anual)
Um "extrato anual" em cards, só com dados locais. Vem depois, quando houver histórico suficiente.

### Futuro (fora do escopo): dashboard geral
O `LedgerEvent` já nasce com tipos estáveis e sem PII além de nomes de pacote. Se um dia houver
comparação anônima (percentis), o caminho é agregar no aparelho e enviar só números, com opt-in.
Nada disso agora.

---

## Fases sugeridas

1. **F1 — Livro-razão (Parte A):** tabela, migração e gênese, gravação nos pontos de escrita,
   `ConfigTimeline` e testes. Sem UI nova; só para de recolorir o passado.
2. **F2 — Calendário fiel:** contrato do dia, tons "renegociou" e "fugiu", linha do tempo na
   folha do dia (B1) e seção de pagamentos (B2).
3. **F3 — Placar:** recordes (B5), você vs. mês passado (B4).
4. **F4 — Rating (B3)** + proposta de aperto (B7).
5. **F5 — Extras:** fantasma (B6), horário fraco (B8), retrospectiva (B9).

## Tarefas (F1)

- [ ] T1: `LedgerEventEntity` + `LedgerDao` + `LedgerType` + migração v4→v5 com gênese
- [ ] T2: `exportSchema = true`, schemas versionados e teste de migração
- [ ] T3: `LedgerRepository` (+ módulo Hilt próprio); gravar em `MonitoredAppRepositoryImpl`
      e `MoneyPreferences`
- [ ] T4: Eventos da tela de bloqueio (R2)
- [ ] T5: `GUARD_OFF`/`GUARD_ON` (serviço + resume)
- [ ] T6: `ConfigTimeline` puro + testes
- [ ] T7: `StatementCalendar` usando o contrato do dia + testes atualizados
- [ ] T8: Smoke test verde (o bloqueio continua funcionando com as escritas novas)

## Critérios de aceite (F1)

- CA1: `./gradlew :app:testDebugUnitTest` verde, com testes de `ConfigTimeline` e de migração.
- CA2: No emulador, um app com limite de 30 min tem dias passados verdes. Mudar o limite para
  5 min hoje não recolore nenhum dia anterior.
- CA3: Desmonitorar e remonitorar, mudar o limite e desligar a acessibilidade geram uma linha
  cada em `ledger_events` (conferir via `adb shell run-as … sqlite3` ou log de debug).

## Perguntas em aberto

- P1: Confirmar a regra do placar: contrato à meia-noite, o limiar de 80% para "renegociou" e
  "fugiu" ser pior que pagar.
- P2: Auto Backup do histórico (R7) contra o sigilo do E19.
- P3: Renegociar deve aparecer na hora (o gerente comenta no diálogo de limite: "Isso vai
  constar no seu extrato.") ou só depois, no Extrato? Recomendação: na hora. É o momento em
  que a pessoa decide, e um aviso seco ali já desestimula a trapaça.

## Notas / Decisões

- Append-only e reconstrução por função pura, em vez de snapshot diário gravado por worker: o
  snapshot depende do worker rodar à meia-noite (Doze), enquanto o evento é gravado no momento
  da ação. Se a performance pedir, um cache de "contrato do dia" pode ser derivado depois.
- `block_events`/`unlock_events` não migram para o livro-razão, para não mexer em `MoneyReport`
  e no histórico, que já funcionam. O merge acontece na leitura.
