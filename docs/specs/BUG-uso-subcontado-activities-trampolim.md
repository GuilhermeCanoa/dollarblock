# BUG — Uso subcontado quando o app usa activities-trampolim (Chrome)

**Status:** done
**Descoberto em:** 09/07/2026, durante a geração das capturas de tablet (Play Store) no emulador.
**Corrigido em:** 26/07/2026 — pareamento de sessão passou a ser por `className` com união de intervalos.

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

- [x] Extrair o pareamento RESUMED/PAUSED para função pura testável (`UsageAggregator`, 25/07)
- [x] Corrigir pareamento por classe/instância nas 3 funções baseadas em eventos
- [x] Testes JVM: padrão trampolim (Chrome), padrão simples, sessão em andamento
- [ ] Validar no emulador: Chrome deve contar ~o mesmo que `dumpsys usagestats` (pendente — validação manual/smoke)

## Solução implementada (26/07/2026)

O `UsageAggregator` passou a rastrear a sessão por **`className`** em vez de por pacote,
medindo a **união dos intervalos de foreground**: mantém, por pacote, o conjunto de
activities atualmente resumidas; o pacote está em foreground enquanto o conjunto é
não-vazio. Um `RESUMED` de classe abre; um `PAUSED`/`STOPPED` só fecha a sessão quando o
conjunto fica vazio.

Isso corrige o trampolim naturalmente: o `STOPPED LauncherActivity` tenta remover uma
classe que não está no conjunto (no-op) e **não** fecha a sessão aberta pela
`TabbedActivity`. Como medimos união (e não soma), activities sobrepostas do mesmo app
não contam tempo dobrado. `SessionEvent` ganhou `className` (nullable; quando ausente,
um sentinel por pacote reproduz o comportamento "uma sessão por pacote").

Testes: `UsageAggregatorTest` cobre padrão trampolim, trampolim na abertura, activities
sobrepostas, duas sessões separadas e sessão trampolim em andamento.

**Mudança de comportamento consciente:** dois `RESUMED` sem `className` seguidos (sem
`PAUSED` entre eles) agora contam da **primeira** entrada até a saída (união), não da
última — o teste correspondente foi atualizado. Sem impacto no fluxo real, onde os
eventos trazem `className`.
