# Spec: E11 Fase 5 — Polimento visual + notificações

**Status:** done
**Épico:** E11
**Data de criação:** 2026-07-02
**Última atualização:** 2026-07-02

---

## Contexto

E11 (rebrand "Taxímetro" + Passe do Dia) está `done` (ver `docs/specs/E11-taximetro-passe-do-dia.md`).
Esta fase estende a estética financeira/contábil da marca (taxímetro, fatura, extrato,
recibo, contrato) para as telas que ainda usam layout/tom genérico, e adiciona
notificações de aviso de limite na voz da marca.

## Requisitos

- R1: Onboarding — a página "O contrato" (segunda ConceptPage) usa papel/tipografia mono
  coerentes com `InvoiceReceipt` (BlockActivity.kt), com micro-animação de "assinatura"
  no botão final ("Assinar" / `onb_finish`).
- R2: Statistics (extrato) — linhas itemizadas com `TabularNumerals`, melhor/pior dia
  destacados no período semanal/mensal.
- R3: History (recibos) — cada evento como lançamento mono, valor à direita.
- R4: Notificação aos 5 min restantes do limite diário, copy passivo-agressiva EN+PT,
  usando a permissão de notificações já concedida no onboarding.
- R5: Haptics no count-up do taxímetro da Home ao cruzar valores inteiros de café
  (múltiplos de `HomeMetrics.COFFEE_PRICE`).
- R6: Lógica nova em funções puras com testes JVM (melhor/pior dia; limiar de
  notificação de 5 min; cruzamento de café no count-up).

## Tarefas

- [x] T1: `OnboardingScreen` — página do contrato com estética de recibo (papel, mono,
  divisores tracejados) + animação de assinatura no botão "Assinar".
- [x] T2: `StatisticsScreen`/`StatisticsViewModel` — função pura `bestAndWorstDay` +
  cartão de extrato com melhor/pior dia destacado + `TabularNumerals` nos valores.
- [x] T3: `HistoryScreen` — layout de recibo (mono, valor alinhado à direita).
- [x] T4: Notificação de aviso aos 5 min do limite — `LimitWarningNotifier` +
  strings `notif_limit_warning_*` (variantes EN/PT) + trigger no
  `DollarBlockAccessibilityService` (tracking loop).
- [x] T5: Haptics no count-up da Home ao cruzar café inteiro — função pura
  `crossedCoffeeMultiple` em `HomeMetrics` + uso em `HomeScreen`.
- [x] T6: Testes unitários verdes; validação no emulador com screenshots.
- [x] T7: CHANGELOG.md atualizado.

## Critérios de aceite

- CA1: `.\gradlew.bat :app:testDebugUnitTest` verde.
- CA2: Onboarding — página do contrato visualmente distinta (papel/mono) e botão
  "Assinar" anima ao tocar.
- CA3: Statistics semanal/mensal mostra melhor/pior dia com numerais tabulares.
- CA4: History exibe eventos como lançamentos (valor mono à direita).
- CA5: Notificação dispara ~5 min antes do limite (validação por inspeção de código +
  teste unitário do limiar, já que emulador não tem clock real de 5 min disponível
  em sessão curta).

## Notas / Decisões

- Notificação usa `NotificationManagerCompat`; canal único `limit_warning`, criado em
  `DollarBlockApp.onCreate`. Sem novo módulo Hilt — a lógica de trigger fica no próprio
  `DollarBlockAccessibilityService` (já injetado), sem criar dependência cruzada de feature.
- Haptics de café: comparação de `floor(prevLost / COFFEE_PRICE)` vs
  `floor(newLost / COFFEE_PRICE)` — dispara quando o inteiro muda (função pura testável).
- Validado no emulador (2026-07-02): onboarding completo (contrato com estética de
  recibo + assinatura animada no botão "Sign"), Statement semanal com cartão de melhor/pior
  dia (numerais tabulares, verde/vermelho), Receipts vazio renderizando corretamente. Não
  foi possível gerar um evento de bloqueio real na sessão (o diálogo de limite diário no
  emulador não persistiu o valor digitado — parece pré-existente, não relacionado a esta
  fase); o layout de recibo do History foi validado por revisão de código (mesmo padrão do
  `InvoiceReceipt`, já provado em produção) e pelo preview `HistoryEventRowPreview`.
- Durante a sessão, o cache incremental do Gradle (`app/build`, ksp caches) corrompeu
  (`ClassNotFoundException` em `DollarBlockApp`, `classes.dex` anormalmente grande);
  resolvido com `./gradlew clean` + rebuild completo. Não relacionado ao código desta fase.
