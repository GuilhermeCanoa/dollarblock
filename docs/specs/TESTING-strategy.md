# Spec: Estratégia de testes automatizados do core

**Status:** done
**Épico:** E10 (Qualidade — transversal)
**Data de criação:** 2026-07-25
**Última atualização:** 2026-07-25

---

## Contexto

O app está em produção na Play Store e o dono não é desenvolvedor Android. Ele
precisa de **confiança de que as funcionalidades core funcionam de verdade**, sem
depender de IA a cada verificação. Antes desta spec havia 7 suítes de teste JVM (JUnit4
puro), mas **a lógica mais crítica e mais sujeita a bug — a medição de tempo de uso a
partir dos eventos do `UsageStatsManager` — tinha cobertura zero**, porque estava
grudada nas chamadas de I/O do Android dentro de `UsageStatsProvider`. Não havia nenhum
teste de banco (Room) nem script de validação ponta-a-ponta reutilizável.

## Estratégia por camada (diagnóstico honesto)

| Camada | Como validar | Custo | Por quê |
|---|---|---|---|
| Medição de uso (agregação de sessões) | **Unit JVM** após extrair `UsageAggregator` puro | Barato | É matemática pura; só precisava ser separada do I/O |
| Aviso de limite (`LimitWarningPolicy`) | **Unit JVM** | Barato | Já era função pura |
| Métricas da Home (`HomeMetrics`) | **Unit JVM** | Barato | Já era função pura |
| Desbloqueio até meia-noite (`BlockPreferences`) | **Unit JVM** (lógica estática) | Barato | `endOfDayMillis`/serialização são puras |
| Persistência de uso (`DailyUsageDao`) | **Unit JVM via Robolectric** (Room in-memory) | Médio | Room exige SQLite do Android; Robolectric roda na JVM, sem emulador |
| Bloqueio ponta-a-ponta (`AccessibilityService` → `BlockActivity`) | **Script adb no emulador** | Alto | `AccessibilityService` + `startActivity` não é instanciável em teste; o re-assert/cold-start só se prova em device |
| Notificação de aviso (visual) | **Manual / script prepara cenário** | Alto | `NotificationManagerCompat` + SO |
| Pagamento (Google Play Billing) | **Manual** | — | Depende de serviço externo do Play |

**Explicitamente não automatizado** (baixo retorno): testes Compose com Espresso das
telas, intents de settings, checagem de permissões especiais e o fluxo de Billing.

## Requisitos

- R1: A lógica de somar sessões de `UsageEvents` é testável na JVM, isolada do Android.
- R2: Cobertura de fronteira para: sessão fechada, sessão em andamento, virada da
  meia-noite, eventos fora de ordem, app nunca fechado, multi-app.
- R3: `LimitWarningPolicy` cobre: exatamente no ponto de aviso, exatamente no limite,
  uso já acima do limite, aviso único em polls sucessivos, arredondamento de minutos.
- R4: O índice único `(packageName, epochDay)` e o `upsertUsage` do `DailyUsageDao` são
  testados sem emulador.
- R5: Um comando roda toda a suíte barata; outro instala no emulador, concede permissões,
  força o limite e verifica o bloqueio — ambos com saída legível para não-dev.
- R6: A refatoração **não altera o comportamento observável** da medição de uso
  (exceto correções seguras de robustez, documentadas).

## Tarefas

- [x] T1: Extrair `UsageAggregator` puro; reescrever os 3 métodos de eventos de
  `UsageStatsProvider` para traduzir eventos + delegar (`readSessionEvents`).
- [x] T2: `UsageAggregatorTest` (18 casos).
- [x] T3: Expandir `LimitWarningPolicyTest` (+5) e `HomeMetricsTest` (+2).
- [x] T4: Adicionar Robolectric + `room-testing` + `coroutines-test` (só `testImplementation`);
  `DailyUsageDaoTest` (6 casos).
- [x] T5: `DebugSetupReceiver` em `src/debug/` (broadcast `DEBUG_SET_LIMIT`) para montar
  cenário via adb — não existe no release.
- [x] T6: `scripts/run-unit-tests.ps1`, `scripts/smoke-test.ps1`, `scripts/README.md`.

## Critérios de aceite

- CA1: `./gradlew :app:testDebugUnitTest` verde. Verificado: **77 testes, 0 falhas**
  (`UsageAggregatorTest` 18, `LimitWarningPolicyTest` 11, `HomeMetricsTest` 17,
  `DailyUsageDaoTest` 6, demais inalterados).
- CA2: `./gradlew :app:assembleDebug` compila com o `src/debug/` (receiver + manifest).
- CA3: `scripts/run-unit-tests.ps1` roda a suíte e imprime OK/FALHOU.
- CA4: `scripts/smoke-test.ps1` num emulador: liga permissões, configura limite de 1 min,
  força uso real e afirma `BlockActivity`.

## Notas / Decisões

- **`UsageAggregator` sem constantes Android:** usa um enum próprio `Type {RESUMED, PAUSED}`
  e `SessionEvent`. O provider traduz `ACTIVITY_RESUMED/PAUSED/STOPPED` em
  `readSessionEvents`. Assim o agregador nunca importa `android.*` e roda em JUnit puro.
- **`OngoingPolicy`** modela as 3 variações que existiam nos métodos originais: ignorar a
  sessão em andamento; fechá-la em `now` usando o `overrideStart` (instante exato conhecido
  pelo serviço de acessibilidade) com fallback para o último RESUMED; e o caso de
  `getUsageMillisSince` (sem lower bound, RESUMED em aberto sempre conta até agora).
- **Mudança de comportamento intencional e segura:** o método multi-app original somava
  deltas mesmo negativos em pares de eventos invertidos (fora de ordem). O `UsageAggregator`
  **descarta durações não-positivas**, o que é o comportamento correto para o cenário
  "eventos fora de ordem" exigido — evita inflar/deflacionar o tempo medido. Nenhum outro
  comportamento observável muda.
- **Robolectric em vez de `androidTest`:** os testes de DAO ficam na suíte JVM barata
  (`src/test/`), que o dono roda com um comando, sem emulador ligado. `androidTest` real
  exigiria device a cada execução — pior para o objetivo. Custo: ~9s de startup do
  Robolectric na primeira classe. Exigiu `testOptions.unitTests.isIncludeAndroidResources = true`.
- **`DebugSetupReceiver` em `src/debug/`:** não precisa de guarda de release porque o
  source set `debug` simplesmente não é compilado no build de release — o receiver não
  existe lá. Exposto (`exported=true`) só no debug para o adb conseguir dispará-lo.
- **Limitação do smoke test:** o Android não permite injetar `UsageStats` sintético, então
  o script usa **tempo real** (~90s com o app-alvo em foreground) para ultrapassar um limite
  de 1 min. É a única forma determinística de validar o caminho real do bloqueio.

### Adendo (2026-07-26) — bug de orquestração do serviço encontrado em uso real

O usuário relatou notificações de "app prestes a bloquear" para apps que não estava
usando (Chrome e outros) — clássico vazamento de estado do `AccessibilityService` em
segundo plano. A causa (loop de tracking não parava ao trocar para app não-monitorado +
aviso sem guarda de foreground) **não era coberta por nenhum teste**, porque a decisão
morava dentro da classe do serviço, não-testável em unit test.

Correção seguindo o mesmo princípio do `UsageAggregator`: extrair a decisão para uma
função pura **`TrackingDecision`** (regra central: nada dispara se o app rastreado não é
mais o foreground) e cobrir com `TrackingDecisionTest` (8 casos), incluindo a reprodução
direta do bug. O serviço passou a delegar a essa função e a chamar `stopTracking()` a
cada troca de app. **Lição:** toda decisão que hoje só é validável em device é candidata
a extração para função pura — foi assim que o bug real virou teste de regressão.
