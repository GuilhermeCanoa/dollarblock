# Spec: Uso de tempo = 100% da métrica do celular (remover baseline)

**Status:** done
**Épico:** E12 (adhoc — simplificação de regra de cálculo)
**Data de criação:** 2026-07-03
**Última atualização:** 2026-07-03 (implementação concluída, exceto validação manual em emulador — T8)

---

## Contexto

Hoje o DollarBlock subtrai um "baseline" (`usageBaselineMillis`, capturado no momento em
que o app passa a ser monitorado) do uso bruto reportado pelo `UsageStatsManager` antes de
comparar com o limite diário e antes de exibir na UI (Apps, Home, Statistics). Isso significa
que o app usa uma métrica própria ("uso desde que o DollarBlock passou a observar"), não o
uso real do dia inteiro.

O usuário quer descomplicar: usar 100% da métrica do celular. Se o usuário já usou 1h de
Instagram hoje e depois configura um limite de 50 minutos, o app deve considerar as 2 coisas
e bloquear imediatamente — sem baseline nenhum subtraído. As métricas do DollarBlock devem
ser apenas as mínimas necessárias para o funcionamento (ex: detectar app em primeiro plano,
sessão em andamento), nunca uma métrica alternativa de "uso líquido".

## Requisitos

- R1: `dailyLimitMinutes` é comparado contra o uso bruto do dia (via `UsageStatsManager`
  /`UsageEvents`, mesma fonte já usada), sem subtrair `usageBaselineMillis`.
- R2: A UI (Apps, Home) exibe o mesmo valor bruto usado na comparação de limite — sem
  divergência entre "o que é mostrado" e "o que é usado para bloquear".
- R3: Statistics também usa o valor bruto (sem baseline por dia de criação) para consistência
  — os números do extrato devem bater com os números usados para bloqueio.
- R4: `usageBaselineMillis` e toda a lógica de captura/subtração são removidas (campo,
  migração de "esvaziar" ou depreciar, sets em `MonitoredAppRepositoryImpl.setMonitored`,
  leituras em `DollarBlockAccessibilityService.effectiveUsageMillis` e
  `StatisticsViewModel.baselineByApp`/`effectiveMillis`).
- R5: Se um app já monitorado tiver `dailyLimitMinutes` configurado e o uso bruto do dia já
  ultrapassar o limite (por já ter sido usado antes de configurar o limite), o próximo evento
  de foreground do app já deve bloquear — sem esperar novo uso.
- R6: Comportamento do passe do dia (unlock total até meia-noite) não muda.

## Tarefas

- [x] T1: `usageBaselineMillis` removido de `MonitoredAppEntity`; Room v3→v4 (`MIGRATION_3_4`)
  faz `ALTER TABLE monitored_apps DROP COLUMN usageBaselineMillis`, registrada em
  `DatabaseModule`.
- [x] T2: `MonitoredAppRepositoryImpl.setMonitored` — não captura mais baseline ao monitorar.
- [x] T3: `MonitoredAppRepositoryImpl.observeMonitoredAppsUsage` — usa `rawMillis` direto.
- [x] T4: `DollarBlockAccessibilityService.effectiveUsageMillis` — repassa uso bruto direto
  (`getTodayUsageMillisViaEvents`), sem subtração.
- [x] T5: `StatisticsViewModel` — `baselineByApp`/`effectiveMillis` removidos; todos os
  agregados (totais, top apps, gráfico, melhor/pior dia) usam `usedMillis` bruto direto.
- [x] T6: `CLAUDE.md` atualizado (Room schema v4, seção Statistics, lista de épicos).
- [x] T7: Testes unitários existentes (`HomeMetrics` etc.) não referenciavam baseline —
  `:app:testDebugUnitTest` verde sem alterações de teste necessárias.
- [ ] T8: Validação manual no emulador — pendente (requer sessão interativa; instruções em
  `CLAUDE.md` para instalar e usar um app de teste).
- [x] T9: `docs/CHANGELOG.md` atualizado.

## Critérios de aceite

- CA1: `.\gradlew.bat :app:testDebugUnitTest` verde.
- CA2: No emulador — usar um app monitorável por >X min sem limite configurado; configurar
  limite < uso já feito hoje; próxima abertura do app bloqueia imediatamente.
- CA3: Números exibidos em Apps/Home/Statistics para o dia corrente batem entre si e com o
  uso real reportado pelo Android (comparar com "Configurações > Bem-estar digital" ou
  equivalente) para um app de teste.
- CA4: Nenhuma referência residual a `usageBaselineMillis` em código de produção.

## Notas / Decisões

- Escopo **não inclui** "tempo comprado" como add-on por app ao limite configurado — isso é
  uma feature nova (hoje só existe o passe do dia = unlock total até meia-noite). Fica
  registrado como possível follow-up, pendente de decisão do usuário sobre preço/fluxo/backend
  antes de virar spec própria.
- Passe do dia (`docs/specs/E11-taximetro-passe-do-dia.md`) não é afetado por este spec.
