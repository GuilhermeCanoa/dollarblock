# BUG — Uso subcontado quando o app usa activities-trampolim (Chrome)

**Status:** pending
**Descoberto em:** 09/07/2026, durante a geração das capturas de tablet (Play Store) no emulador.

## Sintoma

Chrome com ~8 min de uso real no dia (confirmado em `dumpsys usagestats`,
`totalTimeUsed="07:47"`) aparecia como **0m** na tela Apps e no Prejuízo de hoje.
YouTube, no mesmo período, contava corretamente.

## Causa raiz

As funções baseadas em eventos do `UsageStatsProvider`
(`getTodayUsageMillisViaEvents` — as duas sobrecargas — e `getUsageMillisSince`)
rastreiam a sessão com **um único slot de resume por pacote** (`resumes[pkg]` /
`resumeTime`). O Chrome (e qualquer app com activity-trampolim, ex.
`ChromeLauncherActivity`, `FirstRunActivity`) emite a sequência:

```
RESUMED  TabbedActivity      ← resumes[chrome] = t0
STOPPED  LauncherActivity    ← consome resumes[chrome] (par errado!), soma ~0
...
PAUSED   TabbedActivity      ← slot vazio → sessão inteira descartada
```

O `STOPPED` da activity **anterior** chega depois do `RESUMED` da atual e "rouba" o
slot do pacote; o `PAUSED` que fecharia a sessão real não encontra resume e a sessão
é perdida. Resultado: subcontagem sistemática para apps desse padrão.

## Impacto

- Tela Apps, Home (prejuízo) e Extrato mostram uso menor que o real para apps afetados.
- O **bloqueio não é afetado**: o `DollarBlockAccessibilityService` usa as variantes
  agregadas (`queryAndAggregateUsageStats` / `totalTimeInForeground`), que são corretas.
  (Divergência visível: app bloqueado com barra de uso quase vazia.)

## Fix proposto

Rastrear o resume por **classe** (ou `className` + `instanceId`), não por pacote:
`PAUSED`/`STOPPED` só fecham sessão se corresponderem à mesma classe do `RESUMED`
pendente; sessões de classes diferentes do mesmo pacote não podem se consumir.
Alternativa: ignorar `STOPPED` quando já houve `PAUSED`/`STOPPED` da mesma instância.

Adicionar teste JVM com a sequência de eventos do Chrome acima (padrão trampolim)
e com o padrão simples (YouTube) — as funções são puras o bastante para extrair o
loop de pareamento para uma função testável.

## Tarefas

- [ ] Extrair o pareamento RESUMED/PAUSED para função pura testável
- [ ] Corrigir pareamento por classe/instância nas 3 funções baseadas em eventos
- [ ] Testes JVM: padrão trampolim (Chrome), padrão simples, sessão em andamento
- [ ] Validar no emulador: Chrome deve contar ~o mesmo que `dumpsys usagestats`
