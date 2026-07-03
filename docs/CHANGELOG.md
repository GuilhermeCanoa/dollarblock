# DollarBlock — Changelog Funcional

Registro cronológico de mudanças funcionais significativas: features entregues, mudanças de regra de negócio, refactors com impacto comportamental e decisões técnicas relevantes. Atualize sempre que implementar algo que não seja correção trivial de bug.

**Formato:**
```
## [YYYY-MM-DD] — Título curto
**Tipo:** feature | bugfix | regra | refactor | config
**Épico:** E[N] ou adhoc
Descrição funcional.
```

---

## [2026-07-03] — E13: cards Dinheiro gasto/economizado, balões nos cards, avisos de limite e apps sugeridos
**Tipo:** feature
**Épico:** E13 (adhoc) — spec: `docs/specs/E13-cards-dinheiro-avisos-limite-sugeridos.md`

- **Home · Dinheiro gasto:** novo card com a soma de tudo que o usuário já pagou por passes do
  dia (unlock_events, todas as datas). Fonte atrás de `MoneySummaryRepository` (domain) com impl
  Room em módulo Hilt próprio (`MoneySummaryModule`) — trocar por API = trocar o binding.
- **Home · Dinheiro economizado:** para cada par (app, dia) com evento de bloqueio e nenhum
  pagamento naquele dia, conta 1 passe do dia resistido × preço atual do passe
  (`PricingRepository`). Critério usa os eventos do histórico, não os botões da tela de bloqueio;
  agregação por app/dia porque o serviço registra vários block_events por sessão. Cálculo puro em
  `domain/model/MoneyReport` (testado em `MoneyReportTest`).
- **Home · balões informativos:** todo card (taxímetro-herói, gasto, economizado, Bloqueado agora,
  Rastreador de vício) abre ao toque um diálogo explicando o que significa e como a conta é feita.
  O card "Bloqueado agora" deixou de navegar direto para Apps; o balão ganhou a ação "Ver apps".
- **Apps · aviso de troca de limite:** aumentar um limite existente exibe mensagem irônica
  deadpan; reduzir exibe reconhecimento seco. Primeiro limite/remoção não avisam
  (`classifyLimitChange`, testado).
- **Apps · sugeridos:** Instagram, Facebook, TikTok e YouTube aparecem numa seção "Os suspeitos
  de sempre" quando instalados e fora do taxímetro, acima de "Desativados"; toque adiciona ao
  monitoramento. Nada aparece se nenhum estiver instalado.
- **Apps · validação de limite:** o limite diário agora aceita apenas 1–1440 minutos (o dia só
  tem 24 horas); antes valores como 2060 min eram salvos sem crítica.
- **Bugfix (formatação BRL):** em aparelhos pt-BR os valores saíam "R$ 0.00" — o swap
  vírgula/ponto de `formatReais` assumia `String.format` em inglês. Corrigido com `Locale.US`
  explícito na Home, Statistics e Profile.

---

## [2026-07-03] — Correções: bloqueio contínuo, carimbo, passe do dia e apps desativados
**Tipo:** bugfix
**Épico:** adhoc

Quatro correções na experiência de bloqueio e na aba Apps:

- `DollarBlockAccessibilityService.scheduleTracking`: o loop de polling não se rearmava
  após disparar um bloqueio (`break`), então se o usuário fechasse a `BlockActivity` e
  continuasse no app monitorado, o bloqueio não era reafirmado. Agora o loop continua
  (`continue` em vez de `break`) e reavalia a cada `REASSERT_POLL_INTERVAL_MS` (2s).
- `BlockActivity.InvoiceReceipt`: o carimbo "BLOQUEADO" era centralizado no card inteiro
  (`Alignment.Center` sobre toda a `Column`), colidindo com qualquer texto que caísse no
  meio vertical do recibo. Agora o carimbo é centralizado especificamente sobre a linha do
  nome do app (label envolvido em um `Box` próprio).
- `AppsViewModel`/`AppsScreen`: apps com passe do dia ativo (`BlockPreferences.unlockGrants`)
  agora exibem um badge "Passe do dia ativo" na lista de Apps — antes esse estado não tinha
  nenhuma sinalização na UI (dado existia só no serviço de acessibilidade/BlockActivity).
- Desativar o monitoramento de um app não remove mais o registro da lista: ele passa para
  uma seção "Desativados" na mesma tela, com switch para reativar e um botão de lixeira
  para excluir permanentemente (`MonitoredAppDao.delete` novo).

---

## [2026-07-03] — E12: uso de tempo = 100% da métrica do celular
**Tipo:** regra
**Épico:** E12

Remove o "baseline" (`usageBaselineMillis`) que descontava, do uso comparado ao limite
diário e exibido na UI, o tempo já gasto no app antes de ele ser monitorado pelo DollarBlock.
Agora o limite diário, a UI (Apps/Home) e o Statistics usam 100% do uso bruto reportado pelo
`UsageStatsManager`/`UsageEvents` desde a meia-noite local — sem métrica própria de "uso
líquido". Consequência: se o usuário já usou 1h de um app hoje e configura um limite de
50 min, o app bloqueia no próximo evento de foreground, sem esperar novo uso.

- Room `DollarBlockDatabase` v3→v4: `MIGRATION_3_4` remove a coluna `usageBaselineMillis` de
  `monitored_apps`.
- `MonitoredAppRepositoryImpl.setMonitored`/`observeMonitoredAppsUsage` param de captura/
  subtração de baseline removido.
- `DollarBlockAccessibilityService.effectiveUsageMillis` agora só repassa o uso bruto via
  `UsageEvents` (sem ajuste).
- `StatisticsViewModel`: `baselineByApp`/`effectiveMillis` removidos; todos os agregados
  (totais, top apps, gráfico, melhor/pior dia) usam `usedMillis` bruto direto.
- Passe do dia (unlock total até meia-noite) não muda.
- `docs/REGRAS_CONTROLE_TEMPO.md` atualizado: §2/§3/§7 refletem uso 100% bruto (sem baseline);
  §5/§6/§8/§10 corrigidos para o modelo atual de "passe do dia" (E11) — o documento ainda
  descrevia o modelo antigo (R$4,99 / janela de 5 min de uso real), pré-E11.
- Spec: `docs/specs/E12-uso-100-porcento-real.md`. Fora de escopo: "tempo comprado" como
  add-on por app ao limite configurado — não existe hoje (só o passe do dia existe);
  registrado como follow-up pendente de decisão de produto.

## [2026-07-02] — E11 Fase 5: polimento visual + avisos de limite
**Tipo:** feature
**Épico:** E11

Estende a estética financeira/contábil da marca (spec:
`docs/specs/E11-fase5-polimento-visual.md`) às telas que ainda usavam layout genérico, e
adiciona o aviso passivo-agressivo de limite:

- **Onboarding — "O contrato":** a página de penalidade (`onb_penalty_title`/`_body`)
  ganhou papel/tipografia mono coerentes com o recibo de `BlockActivity` (mesmo padrão de
  `InvoiceReceipt`, incluindo divisores tracejados). O botão final "Assinar" traça uma
  assinatura animada (path recortado via `PathMeasure`) antes de confirmar o onboarding,
  com haptic no fim do traço.
- **Statement (Statistics):** novo cartão de extrato com melhor/pior dia do período
  (semanal/mensal), numerais tabulares (`TabularNumerals`) e destaque verde/vermelho.
  Lógica pura nova: `HomeMetrics.bestAndWorstDay`.
- **Receipts (History):** cada evento agora é um lançamento de recibo — mono, valor em
  numerais tabulares alinhado à direita, "Paid"/"Locked, no charge" como descrição.
- **Aviso de limite:** notificação passivo-agressiva ("Faltam 5 min de Instagram. Prepare
  o cartão, ou a sua dignidade.") disparada ~5 min antes do limite diário, via canal
  `limit_warning`. Lógica pura nova: `LimitWarningPolicy.shouldWarn` (trigger único por
  cruzamento de janela, tratando limites menores que 5 min). Novo `LimitWarningNotifier`
  injetado no `DollarBlockAccessibilityService`.
- **Haptics do taxímetro:** o count-up da Home vibra ao cruzar cada café inteiro de
  prejuízo (`HomeMetrics.crossedCoffeeMultiple`).

Validado com `:app:testDebugUnitTest` (34 testes, incluindo os novos de
`HomeMetricsTest` e `LimitWarningPolicyTest`) e no emulador: onboarding completo (contrato
+ assinatura), tela de extrato com melhor/pior dia, tela de recibos.

---

## [2026-07-02] — Preço dinâmico do passe do dia (backend + app)
**Tipo:** feature / regra
**Épico:** E11

Corrige a divergência apontada no changelog anterior: o Lambda `unlock-charge` (repo
separado `dollarblock-payment`, fora deste repositório) cobrava R$ 4,99 enquanto o app
exibia R$ 1,00. Preço agora é server-authoritative e configurável sem deploy de código
(spec: `docs/specs/E11-preco-dinamico.md`):

- **Backend** (`dollarblock-payment`): `pricing.js` passa a ler a tabela de preços de
  variáveis de ambiente da Lambda (`PRICE_DAY_PASS_BRL_CENTS`/`_USD_CENTS`, default 100 =
  R$1,00/US$1,00) em vez de valor hardcoded. `POST /unlock-charge` agora exige `product`
  (`"day_pass"`) além de `currency`, rejeita produto/moeda desconhecidos com 400 e nunca
  aceita amount do cliente. Nova rota `GET /pricing` (mesma API Gateway/Lambda) devolve a
  tabela formatada (`{"day_pass":{"BRL":"1.00","USD":"1.00"}}`) para o app exibir sem
  hardcode. `infra/template.yaml` ganhou os parâmetros de preço e a função `PricingFunction`
  — nenhum recurso novo com custo fixo.
- **App**: `PaymentApiClient.getPricing()` consulta `/pricing`; novo
  `PricingRepository` (com `PricingPreferences`/DataStore) resolve o preço a exibir com
  cache local e fallback para `GooglePayConfig.DEFAULT_PRICE` ("1.00") se a rede falhar.
  `BlockActivity` busca o preço no `onCreate` e o usa de ponta a ponta: `transactionInfo`
  do Google Pay, `InvoiceReceipt` (fatura) e `EventsRepository.recordUnlock` — uma única
  fonte, sem valor duplicado.
- Multimoedas preparado (tabela já tem BRL+USD) mas **não ativado**: `CURRENCY_CODE`
  continua fixo em BRL nesta entrega.
- ⚠️ Deploy do stack `dollarblock-payment-test` atualizado (código Lambda + rota nova)
  ainda pendente — aguardando autorização de custo AWS (regra global do projeto).

---

## [2026-07-02] — Rebrand "Taxímetro" + Passe do Dia (R$ 1 até a meia-noite)
**Tipo:** feature / regra
**Épico:** E11

Mudança de identidade e de regra de negócio (spec: `docs/specs/E11-taximetro-passe-do-dia.md`):

- **Passe do dia**: o desbloqueio pago deixa de ser janela de 5 min medida em tempo de uso e passa a liberar o app bloqueado até a **meia-noite local** (wall-clock), por **R$ 1,00** — no máximo 1 pagamento/dia por app. `UnlockGrant` agora guarda `unlockUntilMs` (serialização v2 `pkg|untilMs`; grants antigos de 3 partes são descartados no parse). `DollarBlockAccessibilityService` simplificado: gate de unlock é `now < unlockUntilMs`. ⚠️ O amount do Lambda `unlock-charge` (fora do repo) precisa ser ajustado para 1.00 BRL.
- **Voz nova ("gerente do banco de tempo")**: copy pass completo em `values/strings.xml` + `values-pt/strings.xml` — tom seco/irônico/deadpan (fatura, extrato, recibos, resgate, movimentações, contrato). MANIFESTO.md atualizado com o passe do dia e a seção "Como falamos".
- **Home = taxímetro**: hero com count-up animado (Animatable), numerais tabulares (token `TabularNumerals` em `Type.kt`), gradiente que fica **vermelho** quando há prejuízo (verde marca quando o dia está limpo) e linha de equivalência concreta ("= 2 cafés", "= 1 pizza inteira") via `HomeMetrics.equivalence()` (função pura + testes).
- **Tela de bloqueio = fatura**: recibo em papel com tipografia mono, carimbo "BLOQUEADO" estampado em diagonal com animação de spring + haptic, e copy escalonada pelo nº de resgates pagos no dia (`EventDao.countUnlocksSince` + `EventsRepository.unlocksPaidToday()`).
- Aba Apps: removida a "Barra 3" (progresso da janela de 5 min) — sem sentido no modelo de passe do dia.

---

## [2026-06-29] — Suporte a multi-idiomas (i18n: inglês + português)
**Tipo:** feature
**Épico:** adhoc

App agora suporta inglês e português, trocável na aba **Profile → Idioma** (System / English / Português). Implementação via API oficial de *per-app language* do AndroidX (`AppCompatDelegate.setApplicationLocales` + `LocaleListCompat`), com `autoStoreLocales=true` no manifest (`AppLocalesMetadataHolderService`) que persiste e restaura a escolha no cold start — nativo no Android 13+ e backportado abaixo disso via `androidx.appcompat`. **System** segue o idioma do celular: `pt-BR`/`pt-PT`/… resolvem para `values-pt`; qualquer outro idioma cai no `values` padrão (inglês). Trocar o idioma recria as Activities automaticamente. A preferência também é espelhada em DataStore (`LanguagePreferences`, no padrão de `ThemePreferences`) para exibir o valor atual e entrar no reset de debug. Adicionados: `res/values-pt/strings.xml` (tradução completa), `res/xml/locales_config.xml`, `core/locale/LocaleManager`.

---

## [2026-06-23] — Redesign da Home + UI totalmente em inglês
**Tipo:** feature / refactor
**Épico:** E6 / adhoc

Home redesenhada: cartões de métricas com animação `AnimatedContent` (slide up/down ao trocar de valor), suporte a swipe horizontal entre abas de período, frases motivacionais rotativas com swipe (`string-array` em `strings.xml`) e ícone `FormatQuote`. Toda a UI (strings.xml) migrada para inglês. A seção de controle manual de bloqueio (dropdown de app + chips de bloqueados) foi removida da Home — o bloqueio por limite e por toggle na aba Apps é o fluxo canônico. `BlockActivity` recebeu ajuste visual complementar.

---

## [2026-06-23] — Apps: barra dupla de progresso (uso + overtime)
**Tipo:** feature
**Épico:** E3 / E5

Aba Apps agora exibe duas barras por app monitorado com limite: **Barra 1** (limite) trava em 100% quando o uso excede o limite e muda de cor para `penalty`; **Barra 2** (overtime) só aparece quando o limite foi ultrapassado, mostrando o progresso dentro da janela de 5 min comprada via desbloqueio pago — reinicia a cada janela (`overtimeMinutes % UNLOCK_WINDOW_MINUTES`). Componente `UsageBar` extraído para reutilização interna.

---

## [2026-06-23] — Home: métrica "Currently Blocked" + "Addiction Tracker"
**Tipo:** feature
**Épico:** E6

Terceiro card de métrica na Home substituído: **Active Limits** (contagem estática) foi trocado por **Currently Blocked** (apps que já atingiram o limite hoje — lógica em `HomeMetrics.currentlyBlockedCount`). Novo card **Addiction Tracker** exibe o total de tentativas de abertura de apps bloqueados desde o início do dia (`EventsRepository.blockAttemptsToday()` → `EventDao.countBlocksSince`). `HomeUiState` ganhou `addictionAttempts`.

---

## [2026-06-23] — Fix: AccessibilityService filtrava overlays e IME como apps reais
**Tipo:** bugfix
**Épico:** E5

Eventos `TYPE_WINDOW_STATE_CHANGED` de overlays de sistema, IME e painéis de notificação corrompiam `lastForegroundPackage` no `DollarBlockAccessibilityService`, fazendo o tracking loop avaliar o bloqueio com o package errado. Corrigido com `isRealApp()` que filtra esses eventos antes de qualquer avaliação (verifica `getLaunchIntentForPackage != null`; exceção para o launcher do sistema).

---

## [2026-06-23] — Cobrança Stripe real via backend AWS (modo teste)
**Tipo:** feature
**Épico:** E9

O desbloqueio pago deixou de usar gateway `"example"` (mock) e passou a enviar o token do Google Pay para `POST /unlock-charge` em AWS API Gateway + Lambda. O desbloqueio só é concedido quando o backend responde `status: "succeeded"`. Idempotência por UUID por requisição. Janela de desbloqueio: 5 minutos (`UNLOCK_WINDOW_MINUTES = 5`). Backend documentado em `docs/BACKEND_STRIPE.md`.

---

## [2026-06-23] — Polling de uso enquanto app fica em foreground
**Tipo:** feature / bugfix
**Épico:** E5

Sem esta mudança, o uso na aba Apps e o disparo de bloqueio por limite não avançavam enquanto o usuário ficava no mesmo app sem trocar de janela (ex.: rolando um feed). Corrigido com loop de coroutine no `DollarBlockAccessibilityService` que a cada 3s (`POLL_INTERVAL_MS`) chama `syncTodayUsage()` e reavalia o limite. O loop é iniciado/parado conforme o foreground muda.

---

## [2026-06-23] — Baseline de uso por app no dia de adição
**Tipo:** regra
**Épico:** E7

Ao adicionar um app ao monitoramento, o uso acumulado até aquele momento no dia é salvo em `MonitoredAppEntity.usageBaselineMillis`. A tela de Statistics subtrai esse baseline do uso do dia de adição, evitando que o histórico pré-DollarBlock contamine as métricas. Nos dias seguintes, `DailyUsageEntity` já parte de zero à meia-noite sem ajuste.

---

## [2026-06-23] — Bloqueio por limite diário (disparo automático)
**Tipo:** feature
**Épico:** E5

O `DollarBlockAccessibilityService` passou a checar, a cada `TYPE_WINDOW_STATE_CHANGED`, se o app em foreground é monitorado, tem `dailyLimitMinutes` definido e já atingiu o limite hoje via `UsageStatsProvider.getTodayUsageMinutes`. Se sim, bloqueia igual ao bloqueio manual, respeitando a janela de desbloqueio pago.

---

## [2026-06-23] — Daily Score com lógica de média por app
**Tipo:** regra
**Épico:** E6

Daily Score (0–100) é calculado como média de `(limite − usado)/limite` entre apps monitorados com limite definido. Cada termo é limitado a [0, 1] antes da média — um app muito acima do limite contribui com 0, nunca negativo. Apps sem limite não entram. Exibido como "—" se nenhum app tem limite definido.

---

## [2026-06-23] — Workflow parallel-dev: DI por feature + navegação por feature
**Tipo:** config / refactor
**Épico:** E10

`DatabaseModule` passou a prover apenas o `DollarBlockDatabase`; `EventsModule` e `MonitoredAppModule` provêem DAOs + bindings de cada feature. Cada feature tem seu próprio `<Nome>Navigation.kt` com rota e extensão de `NavGraphBuilder`. Objetivo: dois devs raramente tocam o mesmo arquivo de DI ou navegação ao adicionar features em paralelo.

---

## [2026-06-23] — Onboarding com 4 permissões guiadas
**Tipo:** feature
**Épico:** E2

Substituiu o antigo gate mínimo de Usage Access por um fluxo de onboarding completo (`HorizontalPager`) que explica o conceito e solicita as 4 permissões (Usage Access, Accessibility, Overlay, Notifications) com contexto e botão direto à tela do sistema. Flag `onboarding_completed` em DataStore controla o roteamento na `MainActivity`.

---

## [2026-06-23] — Busca por nome na aba Apps
**Tipo:** feature
**Épico:** E3

Campo de busca (`AppsSearchField`) no topo da lista de apps filtra em tempo real por `label`, com mensagem de "nenhum resultado" e botão de limpar. Implementado em `feature/apps/`.
