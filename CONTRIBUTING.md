# Contributing — DollarBlock

Guia de trabalho para o time. O projeto é desenvolvido por **2 devs em paralelo**, então
a maior parte deste documento existe para **reduzir conflitos de merge** e manter qualidade
de código. Leia também [`docs/MERGE_HOTSPOTS.md`](docs/MERGE_HOTSPOTS.md).

---

## 1. Divisão do trabalho — por *feature*, não por *camada*

A regra de ouro: **cada dev é dono de features verticais inteiras** (UI + ViewModel +
repositório + DAO + módulo DI + navegação daquela feature). Evite "um faz o back, o outro
faz a UI da mesma tela" — isso garante conflito nos mesmos arquivos.

A Clean Architecture do projeto (`feature/service → domain ← data`) já favorece essa divisão.
Veja o mapa de donos em [`.github/CODEOWNERS`](.github/CODEOWNERS).

---

## 2. Fluxo de branches

- Branch a partir de `master`: `feature/<epic>-<descricao-curta>` (ex.: `feature/e7-statistics-real-data`).
- **PRs pequenos e diários** > branch gigante de uma semana. Branch que vive 5 dias colide
  com Room/navegação de quem está mexendo em paralelo.
- **Rebase** em cima do `master` antes de abrir o PR (não merge):
  ```bash
  git fetch origin
  git rebase origin/master
  ```
  Resolva o conflito do seu lado e mantenha o histórico linear.
- Todo PR precisa de **1 review** do outro dev (o CODEOWNERS atribui automaticamente).

---

## 3. Arquivos compartilhados — "pare e avise"

Antes de editar qualquer um destes, avise o outro dev no canal do time. Detalhes e técnicas
anti-conflito em [`docs/MERGE_HOTSPOTS.md`](docs/MERGE_HOTSPOTS.md):

- `app/src/main/AndroidManifest.xml`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `data/local/db/DollarBlockDatabase.kt` (schema do Room — **só uma pessoa por vez**)
- `core/navigation/TopLevelDestination.kt` e `res/values/strings.xml`

Os módulos Hilt e a navegação foram **quebrados por feature** justamente para você quase
nunca precisar tocar em arquivo compartilhado ao adicionar uma feature nova (ver seção 5).

---

## 4. Qualidade — build e testes obrigatórios

Antes de **todo push** os testes unitários devem passar. Isso é garantido por um git hook
versionado (ver seção 6) e deve ser rodado manualmente também:

```bash
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon

# Linux/macOS
export JAVA_HOME="/path/to/android-studio/jbr"
./gradlew :app:testDebugUnitTest --console=plain --no-daemon
```

Regras de teste:
- Toda **lógica de negócio** (cálculos de score/tempo, parsing, mapeamento) deve viver em
  uma função/classe **pura** (sem Android, sem Room, sem Compose) e ter teste unitário JVM.
  Exemplos: `feature/home/HomeMetrics.kt` (+ `HomeMetricsTest`), `feature/blocking/payment/StripeToken`.
- Não coloque lógica testável dentro de um `combine {}` de ViewModel ou de um `@Composable` —
  extraia para uma função pura e teste-a.
- Testes JVM ficam em `app/src/test/...`; testes instrumentados (Room/UI) em `app/src/androidTest/...`.

---

## 5. Adicionando uma feature nova (sem tocar em arquivo compartilhado)

1. Crie o pacote `feature/<nome>/`.
2. **Navegação:** crie `feature/<nome>/<Nome>Navigation.kt` com a rota e a extension:
   ```kotlin
   const val MINHA_ROUTE = "minha"

   fun NavGraphBuilder.minhaScreen() {
       composable(MINHA_ROUTE) { MinhaScreen() }
   }
   ```
   Registre no `DollarBlockNavHost` chamando `minhaScreen()` (uma linha isolada).
3. **DI:** crie `di/<Nome>Module.kt` próprio da feature (não edite o módulo de outra feature).
4. **Testes:** lógica pura em arquivo separado + teste JVM.

---

## 6. Git hook de pre-push (versionado)

Os hooks ficam em `.githooks/` (versionados no repo). Ative uma vez por clone:

```bash
git config core.hooksPath .githooks
```

A partir daí, **todo `git push` roda os testes unitários** e é abortado se algum falhar —
protegendo `master` e as branches de integração. Para um push emergencial documentado,
use `git push --no-verify` (evite; explique no PR por que pulou).

---

## 7. Skills do projeto (Claude Code)

Duas skills automatizam a disciplina deste guia. O **conteúdo** vive versionado em
`.agents/skills/<nome>/SKILL.md`; a descoberta acontece via `.claude/skills/` (essa pasta é
gitignorada — recriada localmente, como as skills externas).

- **`feature-scaffold`** — gera o esqueleto de uma feature nova no padrão anti-conflito
  (pacote `feature/<nome>` + Screen + ViewModel + `<Nome>Navigation.kt` + módulo Hilt próprio
  + teste da lógica pura). Use ao iniciar uma tela/épico novo (ver seção 5).
- **`pre-pr`** — antes de abrir um PR: rebase em `origin/master`, build, testes e checagem dos
  arquivos compartilhados (hotspots), com resumo pronto pro PR. Não dá push nem abre o PR.

### Ativar as skills num clone novo
A pasta `.claude/skills/` não é versionada. Após clonar, recrie as entradas locais apontando
para o conteúdo versionado em `.agents/skills/` (uma vez por clone):

```bash
# Linux/macOS — symlinks
ln -s "$PWD/.agents/skills/feature-scaffold" .claude/skills/feature-scaffold
ln -s "$PWD/.agents/skills/pre-pr"           .claude/skills/pre-pr

# Windows (PowerShell, como administrador ou com Developer Mode)
New-Item -ItemType SymbolicLink -Path .claude\skills\feature-scaffold -Target .agents\skills\feature-scaffold
New-Item -ItemType SymbolicLink -Path .claude\skills\pre-pr           -Target .agents\skills\pre-pr
```

> Se editar uma skill, edite sempre o arquivo em `.agents/skills/...` (a fonte versionada).

---

## 8. Commits

- Mensagens em português, no imperativo, referenciando o épico quando aplicável
  (ex.: `E7: gráfico de barras com dados reais`).
- Não commite `local.properties`, `*.apk`, `paylog.txt` nem nada em `build/`.
