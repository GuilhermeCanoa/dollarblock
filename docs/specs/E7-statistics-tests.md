# Spec: E7 — Statistics (pendências de qualidade)

**Status:** in-progress
**Épico:** E7
**Data de criação:** 2026-06-23
**Última atualização:** 2026-06-23

---

## Contexto

O E7 foi entregue com gráficos funcionando com dados reais nos três períodos (diário, semanal, mensal) e score semanal por app. A pendência é de qualidade: a lógica de agregação está dentro do `StatisticsViewModel` (em `combine {}`), o que a torna não-testável com testes JVM. O padrão estabelecido no projeto é extrair lógica para funções puras (ex.: `HomeMetrics.kt`) e testá-las separadamente.

## Requisitos

- R1: Funções de agregação de Statistics (agrupamento por período, cálculo de score semanal por app) devem viver em um arquivo puro (`StatisticsMetrics.kt` ou similar), sem dependência de Android/Room/Compose.
- R2: Testes JVM cobrindo os agregadores: agrupamento diário, semanal (seg→dom), mensal (Sem 1→4), cálculo de score semanal.
- R3: Testes de DAO para `DailyUsageDao.observeRange` usando Room in-memory.

## Tarefas

- [ ] T1: Criar `feature/statistics/StatisticsMetrics.kt` com funções puras extraídas do `StatisticsViewModel`.
  - `groupByDay(usages, date): List<BarData>`
  - `groupByWeek(usages, weekStart): List<BarData>`
  - `groupByMonth(usages, monthStart): List<BarData>`
  - `weeklyScorePerApp(usages, limits, days): List<AppScore>`
- [ ] T2: Atualizar `StatisticsViewModel` para delegar cálculos a `StatisticsMetrics`.
- [ ] T3: Criar `StatisticsMetricsTest` (JVM) cobrindo os casos de borda:
  - Período sem dados → barras zeradas
  - App sem limite → não entra no score
  - Baseline no dia de adição
- [ ] T4: Criar `DailyUsageDaoTest` (instrumented) — upsert, query por range, verificar que epochDay único por app funciona.

## Critérios de aceite

- CA1: `./gradlew :app:testDebugUnitTest` passa com `StatisticsMetricsTest`.
- CA2: `StatisticsViewModel` não contém lógica de cálculo inline — apenas combina flows e delega.
- CA3: Gráficos continuam renderizando corretamente no emulador após o refactor.

## Notas / Decisões

- Seguir exatamente o padrão de `HomeMetrics.kt` — função pura, sem Android imports, testável com JVM.
- `BarData` pode ser um data class simples em `feature/statistics/` sem passar por `domain/model/` (é UI model).
- Testes de DAO são instrumentados e precisam de emulador ou Robolectric.
