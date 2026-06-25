# DollarBlock — Roadmap de Épicos (MVP)

> "Your Number One App for Time Management"

Plano de épicos sequenciado para construir a primeira versão do DollarBlock.
Cada épico tem **objetivo**, **entregáveis**, **critérios de aceite** e **dependências**.
Decisões técnicas em [`ARCHITECTURE.md`](./ARCHITECTURE.md).

**Ordem de execução:** E0 → E0.5 → E1 → E2 → E3 → E4 → E5 → E6 → E7 → E8 → E9, com
E10 (qualidade) atravessando todos. Cada épico deve deixar o projeto **compilável**.

---

## E0 — Fundação do projeto & Design System
**Objetivo:** projeto Android moderno compilável, com identidade visual DollarBlock.

**Entregáveis**
- Setup Gradle (Kotlin DSL + version catalog), plugins (Compose, Hilt, KSP, Room).
- `DollarBlockApp` (`@HiltAndroidApp`) + `MainActivity`.
- Design System: paleta (verde dólar / verde escuro / sucesso vibrante / alerta amarelo
  / penalidade vermelho / neutros), tipografia, `DollarBlockTheme`, componentes base
  (card de métrica, botão primário, botão de penalidade).
- Tom de voz central: arquivo de strings com mensagens ("Meta cumprida.", "Limite atingido.",
  "Desbloqueio necessário.", "Você economizou X minutos hoje.").
- Navegação: `NavHost` + Bottom Navigation (Home / Apps / Statistics / Profile) com telas placeholder.

**Aceite:** app instala, abre, navega entre as 4 abas com tema aplicado. ✅ **Concluído.**

---

## E0.5 — Shell navegável (placeholders ricos)
**Objetivo:** dar "vida" à casca do app antes dos dados reais — permite navegar e validar
a visão de UX/identidade de cada aba com dados de exemplo.

**Entregáveis**
- `PreviewBanner` reutilizável ("Pré-visualização — dados de exemplo").
- **Apps**: lista de apps com avatar, uso atual vs limite (barra de progresso) e toggle de
  monitoramento funcional (estado local).
- **Statistics**: seletor de período (Diário/Semanal/Mensal) + gráfico de barras simples
  (Compose Canvas) + cartões de resumo, todos com dados mock por período.
- **Profile**: cabeçalho do usuário, faixa de estatísticas, seção de Permissões (com status)
  e seção de Preferências/Histórico.

**Aceite:** as 4 abas exibem conteúdo on-brand navegável; toggles e seletor respondem.
Dados são mock e serão substituídos nos épicos E3 (Apps), E7 (Statistics) e E8 (Profile).
✅ **Concluído** — verificado por screenshot nas 4 telas no emulador.

---

## E1 — Camada de dados & domínio (persistência)
**Objetivo:** base de dados e contratos de domínio prontos.

**Entregue (fatia — histórico de eventos):**
- Room: `DollarBlockDatabase` + `BlockEventEntity`/`UnlockEventEntity` + `EventDao`.
- Domínio: `RecentEvent`, `EventsRepository` (interface) + impl + módulos Hilt
  (`DatabaseModule`/`RepositoryModule`).
- Registro real: o serviço grava bloqueios; `BlockActivity` grava desbloqueios pagos
  (valor + método). Home "Recent Events" exibe o histórico real.

**Restante:**
- Entidades `MonitoredApp` (hoje em DataStore), `DailyUsage`, `UserStats` + DAOs.
- Mappers/casos de uso adicionais; testes de DAO (Room in-memory).

**Aceite (fatia atual):** bloqueios e pagamentos aparecem em "Recent Events". ✅ **Validado.** **Depende de E0.**

---

## E2 — Onboarding & permissões
**Objetivo:** primeira execução explica o conceito e solicita permissões com clareza.

**Entregue (validado no emulador):**
- Fluxo de onboarding em `feature/onboarding/` (`OnboardingScreen` + `OnboardingViewModel`):
  `HorizontalPager` com páginas de conceito (DollarBlock como treinador de tempo) e
  "como funciona a penalidade", indicador de páginas e navegação Continuar/Pular.
- Solicitação guiada das 4 permissões na última página: Usage Access, Accessibility,
  Overlay, Notifications — cada uma com ícone, o **porquê** e botão que abre a tela do
  sistema (ou o runtime permission de Notifications no Android 13+). Status "Concedida"
  re-checado em `ON_RESUME`. Usage Access é obrigatória para concluir.
- `data/permissions/PermissionsProvider` agrega checagem + intents das 4 permissões.
- Gate de roteamento na `MainActivity`: flag `onboarding_completed` em
  `data/local/prefs/OnboardingPreferences` (DataStore) decide onboarding vs. abas;
  substitui o antigo gate mínimo de Usage Access.
- Manifest: `SYSTEM_ALERT_WINDOW` + `POST_NOTIFICATIONS` declaradas.

**Aceite:** usuário novo passa pelo onboarding, concede permissões, chega à Home;
reabrir o app pula o onboarding. ✅ **Validado.** **Depende de E1.**

---

## E3 — Apps: listagem & configuração de limites
**Objetivo:** usuário escolhe apps e define limites diários.

**Entregáveis**
- Data source `PackageManager`: apps instalados (ícone, nome, package). ✅
- Tela Apps: lista, toggle de monitoramento, uso atual vs limite (barra de progresso). ✅
- **Definição de limite diário pela UI**: toque na linha do app abre `DailyLimitDialog`
  (campo numérico em minutos, validação > 0, opção "Remover limite"). ✅
- **Busca por nome**: campo de busca no topo da lista (`AppsSearchField`), filtra em tempo
  real por `label`, com mensagem de "nenhum resultado" e botão de limpar. ✅
- Persistência em `MonitoredAppEntity.dailyLimitMinutes` (Room), via
  `MonitoredAppRepository.setDailyLimit`.
- **Barra dupla de progresso** (2026-06-23): Barra 1 mostra uso vs limite (trava em 100% e muda para `penalty` quando excedido); Barra 2 (overtime) aparece só quando o limite foi ultrapassado, mostrando progresso dentro da janela de 5 min de desbloqueio pago — reinicia a cada janela. Componente `UsageBar` extraído internamente.

**Aceite:** ativar/desativar, definir limite, buscar por nome e visualizar barra dupla de progresso persistem e refletem na lista. ✅ **Validado.** **Depende de E1.**

---

## E4 — Monitoramento (núcleo de medição de uso)
**Objetivo:** camada isolada que mede tempo de uso e atualiza o banco periodicamente.

**Entregáveis**
- Data source `UsageStatsManager` (leitura de uso por app/dia).
- Cálculo de tempo + atualização periódica (WorkManager / Foreground Service).
- Escrita em `DailyUsage`; reset diário por `epochDay` local.

**Aceite:** uso real dos apps monitorados aparece atualizado em `DailyUsage`.
**Depende de E1, E3** (precisa saber o que monitorar). Permissões de E2.

---

## E5 — Bloqueio (Blocking Engine)
**Objetivo:** detectar limite atingido e bloquear o app com tela própria.

**Entregue (validado no emulador):**
- `DollarBlockAccessibilityService` detecta o app em foreground e abre a `BlockActivity`
  (tela de bloqueio DollarBlock) por cima.
- Controle na Home: **seletor de apps instalados** (`InstalledAppsProvider` via PackageManager)
  + **botão habilitar/desabilitar** bloqueio por app.
- Conjunto de bloqueados persistido em DataStore (`BlockPreferences`), compartilhado UI↔serviço.
- Detecção de status do serviço de Acessibilidade na Home + atalho para Configurações.
- **Disparo por uso ≥ limite**: a cada troca de janela, o serviço também checa de forma
  assíncrona (`MonitoredAppDao` + `UsageStatsProvider.getTodayUsageMinutes`) se o app é
  monitorado, tem `dailyLimitMinutes` definido e já atingiu o limite hoje — nesse caso
  bloqueia igual ao bloqueio manual, respeitando a mesma janela de desbloqueio pago
  (`BlockPreferences.isUnlockWindowExpired`). ✅
- **Polling enquanto um app monitorado fica em foreground** (sem trocar de janela, ex.:
  rolando um feed por minutos): sem isso, nem o uso na tela Apps nem o bloqueio por
  limite avançavam até o usuário fechar/reabrir o app. Resolvido com um loop de coroutine
  no próprio `DollarBlockAccessibilityService` que, a cada 3s (`POLL_INTERVAL_MS`), chama
  `MonitoredAppRepository.syncTodayUsage()` (atualiza Room → UI via Flow) e reavalia o
  limite. É iniciado/parado por app conforme o foreground muda. ✅

**Entregue adicionalmente (2026-06-23):**
- **Fix overlays/IME**: `isRealApp()` no `DollarBlockAccessibilityService` filtra eventos de overlays de sistema, IME e painéis de notificação antes de qualquer avaliação — esses eventos corrompiam `lastForegroundPackage` e impediam o bloqueio correto no tracking loop.
- **Unlock por tempo real de uso**: a janela de desbloqueio agora é medida via `UsageStatsProvider.getUsageMillisSince` (tempo real de foreground registrado pelo SO), sem session tracking próprio.

**Restante do E5 (depende de E1):**
- Migrar o conjunto bloqueado-manualmente de DataStore para Room (unificar com
  `MonitoredAppEntity`).
- Tipar o motivo do bloqueio no `BlockEvent` (manual vs. limite diário) para exibir
  diferenciado no histórico (E8).

**Aceite (fatia atual):** app selecionado e bloqueado pela Home → abre bloqueado mostra a
tela do DollarBlock; desabilitar libera o acesso. App monitorado que atinge o limite
diário também é bloqueado automaticamente ao ser aberto. ✅ **Validado.**

---

## E6 — Home (dashboard)
**Objetivo:** painel diário motivador.

**Entregue (validado):**
- `HomeViewModel` combina `MonitoredAppRepository.observeMonitoredAppsUsage()` com os
  demais flows da Home para calcular, em tempo real, somente sobre apps monitorados
  **com `dailyLimitMinutes` definido** (apps sem limite não entram em nenhuma das 3 métricas):
  - **Active Limits**: contagem desses apps.
  - **Time Saved (hoje)**: soma de `max(0, limite − usado)` entre eles, em minutos.
  - **Daily Score** (0–100): média entre apps de `(limite − usado)/limite`, cada termo
    limitado a [0, 1] antes da média (um app muito acima do limite contribui com 0,
    nunca negativo, para não arrastar a média injustamente). `null` (exibido como “—”)
    se nenhum app monitorado tem limite definido ainda.
  - Pagamentos (`BlockPreferences.grantUnlock`) não alteram `dailyLimitMinutes` — só criam
    uma janela de trégua onde o bloqueio é ignorado — entao o tempo usado durante essa
    janela continua contando contra o limite normalmente nas 3 métricas, sem lógica extra.
- Daily Score / Time Saved / Active Limits exibidos na Home com dados reais.
- Recent Events (já entregue no E1) permanece.

**Entregue adicionalmente (2026-06-23):**
- Home redesenhada com animação `AnimatedContent` nos valores das métricas (slide up/down).
- Frases motivacionais rotativas com swipe horizontal (string-array em strings.xml).
- Métricas revistas: **Currently Blocked** substitui Active Limits (conta apps que já ultrapassaram o limite hoje); novo card **Addiction Tracker** exibe tentativas de abertura de apps bloqueados desde meia-noite (`EventsRepository.blockAttemptsToday()`).
- Seção de controle manual de bloqueio (dropdown + chips) removida da Home — fluxo consolidado na aba Apps.
- UI migrada para inglês.

**Restante:** Streak/histórico de scores fica para mais adiante se fizer sentido.

**Aceite:** Home reflete dados reais de uso, limites e eventos. ✅ **Validado.**
**Depende de E1, E4, E5.**

---

## E7 — Statistics
**Objetivo:** visão de uso ao longo do tempo.

**Entregue (validado):**
- `StatisticsViewModel` agrega `DailyUsageEntity` por período sobre `DailyUsageDao.observeRange`,
  combinando com `MonitoredAppDao.observeMonitored` e `EventDao.countBlocksInRange`:
  - **Diário**: uma barra por app monitorado (top 7 por uso) no dia de hoje.
  - **Semanal**: barras seg→dom da semana atual (rótulos de dia da semana, pt-BR).
  - **Mensal**: 4 barras (Sem 1 → Sem 4), somando uso por janela de 7 dias.
  - Cartões de resumo: tempo total, app mais usado e nº de bloqueios no período.
- **Baseline por app** (`usageBaselineMillis` + `createdAt` em `MonitoredAppEntity`): no
  dia em que o app foi adicionado ao monitoramento, subtrai o uso pré-DollarBlock; nos
  dias seguintes `DailyUsageEntity` já parte de zero à meia-noite, sem ajuste.
- **Score semanal por app**: para cada app monitorado *com* `dailyLimitMinutes` definido,
  média dos últimos 7 dias de `(limite − usado)/limite` (mesma lógica do Daily Score do E6,
  por app, em janela semanal), ordenado por score. Apps sem limite não entram.
- Gráfico de barras em `StatisticsScreen` (Compose Canvas) ligado ao estado real.

**Restante:** extrair os agregadores do `StatisticsViewModel` para funções puras testáveis
(padrão `HomeMetrics`); testes de DAO de `observeRange`.

**Aceite:** gráficos renderizam com dados reais nos três períodos. ✅ **Validado.**
**Depende de E1, E4.**

---

## E8 — Profile & Histórico

**Entregue (validado no dispositivo):**
- **Permissões reais**: `ProfileViewModel` injeta `PermissionsProvider` e expõe o estado das
  4 permissões (Usage Access, Acessibilidade, Sobreposição, Notificações), re-checado em
  `ON_RESUME`. Cada linha mostra Concedida/Pendente; tocar numa pendente abre a tela do
  sistema (`intentFor`) — ou o runtime permission de Notificações no Android 13+. ✅
- **Cabeçalho de estatísticas reais**: limites ativos e tempo economizado de hoje
  reaproveitam `HomeMetrics.compute` sobre `observeMonitoredAppsUsage`; bloqueios de hoje
  vêm de `EventDao.countBlocksInRange`. Substitui os tiles mock (sequência/economizado/metas). ✅
- **Tela de Histórico** (`HistoryScreen` + `HistoryViewModel`): lista completa de bloqueios e
  desbloqueios a partir de `EventsRepository.recentEvents`, agrupada por dia (cabeçalho de
  data pt-BR), reaproveitando o estilo de linha da Home. Rota própria `HISTORY_ROUTE`
  (`historyScreen()`), fora da bottom bar, com botão Voltar; aberta pelo item "Histórico"
  das Preferências. ✅

**Restante (menor):**
- Preferências Tema/Sobre ainda são placeholders (não acionáveis).

**Aceite:** histórico lista eventos reais; Profile mostra estatísticas reais e permite
revisar/conceder permissões. ✅ **Validado.** **Depende de E1, E5.**

---

## E9 — Pagamento (Google Pay + Stripe) & desbloqueio

**Entregue (Google Pay + cobrança Stripe real em modo TESTE):**
- Botão **"Pagar com Google Pay"** na tela de bloqueio (`PaymentsClient`, `ENVIRONMENT_TEST`,
  gateway **`"stripe"`** com `pk_test_`) + fallback **"Simular pagamento (teste)"** (debug).
- **Cobrança real via backend**: o token do Google Pay é extraído
  (`paymentMethodData.tokenizationData.token`), o `id` do token Stripe é isolado por
  `StripeToken.extractId` e enviado por `PaymentApiClient.charge()` ao endpoint
  `POST /unlock-charge` (AWS API Gateway + Lambda + Stripe). O desbloqueio só é concedido
  quando o backend responde `status: "succeeded"`. Idempotência por `idempotencyKey` (UUID).
- Pagamento OK → `BlockPreferences.grantUnlock(pkg, janela)` libera por
  `UNLOCK_WINDOW_MINUTES` (5 min) e reabre o app; depois volta a bloquear. O desbloqueio é
  registrado via `EventsRepository.recordUnlock` (valor + método) e aparece no histórico.
- Config em `feature/blocking/payment/` (`GooglePayConfig`, `PaymentApiClient`, `StripeToken`).
  Backend documentado em [`BACKEND_STRIPE.md`](./BACKEND_STRIPE.md); tutorial de pagamento em
  [`PAYMENTS_SETUP.md`](./PAYMENTS_SETUP.md).

**Restante (produção):**
- `pk_live_`/`sk_live_` + `ENVIRONMENT_PRODUCTION` + `merchantInfo.merchantId`
  (Google Pay & Wallet Console); botão oficial `com.google.pay.button` (diretrizes de marca).
- Mover `pk_test_` para fora do código-fonte (BuildConfig/secret) antes de qualquer release.
- Persistir `UnlockEvent`/`penaltyAmount` em Room; modelos de saldo/depósito/transação.

**Aceite (fatia atual):** pagar com Google Pay cobra de fato via Stripe (conta teste) e
libera o app pela janela; falha de cobrança não libera. ✅ **Validado.**

---

## E10 — Qualidade, QA & build (transversal)
**Objetivo:** robustez e polimento contínuos.

**Entregáveis**
- Estados loading/erro/vazio padronizados; acessibilidade; revisão de tom de voz.
- Testes: unit (use cases), DAO (Room in-memory); smoke manual do fluxo de bloqueio.
- Config de build release.

**Entregue (fatia — fluxo de trabalho paralelo & testes):**
- **Colaboração 2 devs:** `CONTRIBUTING.md` (branches, rebase, divisão por feature),
  `.github/CODEOWNERS` (donos por área + auto-review), `docs/MERGE_HOTSPOTS.md`
  (técnicas anti-conflito nos arquivos compartilhados).
- **DI por feature:** `DatabaseModule` agora provê só o DB; `EventsModule` e
  `MonitoredAppModule` provêm DAO + binding de cada feature (substituem o antigo
  `RepositoryModule`) — dois devs raramente tocam o mesmo arquivo de DI.
- **Navegação por feature:** rota + `NavGraphBuilder` extension no pacote de cada feature
  (`<Nome>Navigation.kt`); `DollarBlockNavHost` apenas as compõe.
- **Testabilidade:** cálculo das métricas da Home extraído para `HomeMetrics` (puro) com
  `HomeMetricsTest`; padrão "lógica testável fora de ViewModel/Composable" documentado.
- **Gate de qualidade:** git hook `.githooks/pre-push` (versionado) roda
  `:app:testDebugUnitTest` e aborta o push se falhar. Ativar com
  `git config core.hooksPath .githooks`.

**Restante:**
- Estados loading/erro/vazio padronizados; acessibilidade; revisão de tom de voz.
- Testes de DAO (Room in-memory) e de ViewModels; smoke manual do fluxo de bloqueio.
- Config de build release; (futuro) CI rodando os mesmos testes no PR.

**Aceite:** suíte de testes verde; fluxo principal validado manualmente.

---

## Resumo de dependências

```
E0 ─► E0.5 ─► E1 ─► E2
                 ├─► E3 ─► E4 ─► E5 ─► E6
                 │              │     ├─► E8
                 │              └────►E9
                 └─► E7
E10 ── transversal a todos
```

E0.5 é uma camada de UI placeholder: E3/E7/E8 substituem seus mocks por dados reais.

**Status atual:** E0–E9 entregues e validados — todas as 4 abas operam com dados reais.

**Próximo passo sugerido:** **E10** (transversal) — testes de DAO/ViewModel (Room in-memory),
estados loading/erro/vazio padronizados e config de build release. Extrair os agregadores do
`StatisticsViewModel`/`ProfileViewModel` para funções puras testáveis (padrão `HomeMetrics`).
