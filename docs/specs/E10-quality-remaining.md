# Spec: E10 — Qualidade (pendências)

**Status:** in-progress
**Épico:** E10
**Data de criação:** 2026-06-23
**Última atualização:** 2026-06-23

---

## Contexto

O E10 é transversal e foi entregue na fatia de workflow (DI por feature, navegação por feature, `HomeMetrics` testável, git hook de pre-push). Restam as pendências de qualidade de produto: estados de loading/erro/vazio, testes de DAO/ViewModel, acessibilidade e configuração de release build.

## Requisitos

- R1: Estados loading/erro/vazio padronizados em todas as abas (Home, Apps, Statistics, Profile).
- R2: Testes de DAO com Room in-memory para `EventDao`, `MonitoredAppDao`, `DailyUsageDao`.
- R3: Testes de ViewModel para ao menos `HomeViewModel` e `StatisticsViewModel`.
- R4: Acessibilidade básica: `contentDescription` em ícones e botões sem texto.
- R5: Config de build release: `minifyEnabled`, `proguardFiles`, `signingConfig` (estrutura pronta, chave não no repo).

## Tarefas

**Estados UI**
- [ ] T1: Definir sealed class `UiState<T>` (Loading / Success / Error / Empty) em `core/`.
- [ ] T2: Aplicar `UiState` em `HomeViewModel`, `AppsViewModel`, `StatisticsViewModel`, `ProfileViewModel`.
- [ ] T3: Criar composables de estado (`LoadingView`, `ErrorView`, `EmptyView`) em `core/designsystem/`.

**Testes**
- [ ] T4: `EventDaoTest` (instrumented) — insert block/unlock, query range.
- [ ] T5: `MonitoredAppDaoTest` (instrumented) — toggle, set limit, upsert baseline.
- [ ] T6: `DailyUsageDaoTest` (instrumented) — upsert idempotente, query por epochDay.
- [ ] T7: `HomeViewModelTest` (JVM, Turbine) — emissão correta de `UiState` a partir de flows mockados.

**Acessibilidade**
- [ ] T8: Auditar `HomeScreen`, `AppsScreen`, `StatisticsScreen`, `ProfileScreen` com TalkBack — adicionar `contentDescription` faltantes.

**Release build**
- [ ] T9: Configurar `buildTypes.release` em `app/build.gradle.kts` com `minifyEnabled = true` e regras ProGuard básicas (Room, Hilt, Compose).
- [ ] T10: Documentar processo de assinatura em `docs/RELEASE.md` (sem commitar a keystore).

**CI (futuro)**
- [ ] T11: GitHub Actions workflow rodando `:app:testDebugUnitTest` em todo PR (`.github/workflows/ci.yml`).

## Critérios de aceite

- CA1: Todas as abas exibem spinner enquanto dados carregam e mensagem de erro/vazio quando aplicável.
- CA2: `./gradlew :app:testDebugUnitTest` passa com todos os testes novos.
- CA3: `./gradlew :app:assembleRelease` completa sem erros com ProGuard habilitado.

## Notas / Decisões

- `UiState` sealed class é preferível a múltiplos booleanos (`isLoading`, `hasError`) — evita estados impossíveis.
- Testes de ViewModel com Turbine (`app.cash.turbine`) para coletar flows. Adicionar dependência em `libs.versions.toml`.
- ProGuard rules para Hilt e Room existem nas bibliotecas como consumer rules — verificar se são suficientes antes de adicionar rules customizadas.
- CI é baixa prioridade enquanto o time é de 2 devs com pre-push hook local, mas vale ter o arquivo de workflow pronto.
