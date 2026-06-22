---
name: feature-scaffold
description: Cria o esqueleto de uma feature nova no DollarBlock no padrão anti-conflito — pacote feature/<nome> com Screen, ViewModel, arquivo de navegação (rota + NavGraphBuilder extension) e módulo Hilt próprio. Use quando o usuário pedir para "criar uma feature", "scaffold de tela", "nova aba", "começar o épico X" ou adicionar uma tela nova ao app.
---

# feature-scaffold — esqueleto de feature DollarBlock

Gera uma feature vertical seguindo as convenções do projeto para que **dois devs em
paralelo quase nunca toquem o mesmo arquivo** (ver `docs/MERGE_HOTSPOTS.md` e
`CONTRIBUTING.md` seção 5).

## Quando usar
Pedidos como: "cria a feature de metas", "scaffold da tela de settings", "nova aba X",
"começa o épico E7 (statistics real)".

## Entradas a confirmar com o usuário antes de gerar
1. **Nome da feature** em kebab/single word → vira o pacote `feature/<nome>` e o prefixo
   das classes (ex.: `goals` → `GoalsScreen`, `GoalsViewModel`).
2. **É aba de primeiro nível?** (aparece na bottom bar) ou tela secundária navegada de dentro
   de outra. Só abas de primeiro nível tocam `TopLevelDestination` (arquivo compartilhado —
   avisar o time).
3. **Precisa de ViewModel?** (tem estado/lógica) — default sim.
4. **Precisa de repositório/DAO?** Se sim, qual interface de domínio e qual DAO. Lembre que
   alterar o schema do Room é processo sensível (só uma pessoa por vez — ver hotspots).

## Passos

### 1. Tela — `feature/<nome>/<Nome>Screen.kt`
Composable raiz da feature. Se houver ViewModel, injeta via `hiltViewModel()` e coleta o
estado com `collectAsStateWithLifecycle()`. Usa o design system (`core/designsystem`).

```kotlin
package com.dollarblock.feature.<nome>

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun <Nome>Screen(viewModel: <Nome>ViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // TODO: UI on-brand usando core/designsystem
}
```

### 2. ViewModel (se aplicável) — `feature/<nome>/<Nome>ViewModel.kt`
`@HiltViewModel`, expõe `StateFlow<<Nome>UiState>`. **Regra de ouro:** qualquer cálculo de
negócio fica em função/objeto **puro** separado (ex.: `<Nome>Metrics.kt`), nunca dentro de
`combine {}` — para ser testável em JVM (ver passo 5 e `feature/home/HomeMetrics.kt` como
referência).

### 3. Navegação — `feature/<nome>/<Nome>Navigation.kt`
Fonte única da rota + extension que registra a tela. **Não edite o NavHost além de chamar a
extension** (uma linha isolada → merge automático).

```kotlin
package com.dollarblock.feature.<nome>

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val <NOME>_ROUTE = "<nome>"

fun NavGraphBuilder.<nome>Screen() {
    composable(<NOME>_ROUTE) { <Nome>Screen() }
}
```
Depois adicione `<nome>Screen()` ao `core/navigation/DollarBlockNavHost.kt`. Se for aba de
primeiro nível, adicione a entrada no `TopLevelDestination` (compartilhado — avise o time) e
a string em `res/values/strings.xml` no grupo da feature.

### 4. DI — `di/<Nome>Module.kt` (só se a feature tiver repositório/DAO)
Módulo Hilt **próprio da feature** (nunca edite o de outra). Padrão: `abstract class` com
`@Binds` do repositório + `companion object` com `@Provides` do(s) DAO(s). Veja
`di/EventsModule.kt` / `di/MonitoredAppModule.kt` como referência.

### 5. Teste — `app/src/test/.../feature/<nome>/<Nome>MetricsTest.kt`
Teste JVM da lógica pura extraída no passo 2. Sem Android/Room/Compose. Veja
`HomeMetricsTest.kt` como referência.

### 6. Verificação
```bash
# Windows
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain --no-daemon
```
Garanta `BUILD SUCCESSFUL` e testes verdes antes de encerrar.

## Checklist final
- [ ] `feature/<nome>/` com Screen (+ ViewModel + Metrics puro se aplicável)
- [ ] `<Nome>Navigation.kt` com rota + extension; NavHost chama a extension
- [ ] `di/<Nome>Module.kt` próprio (se tiver repo/DAO)
- [ ] Teste JVM da lógica pura
- [ ] Build + testes verdes
- [ ] Se mexeu em arquivo compartilhado (TopLevelDestination/strings/Room), avisar o time
