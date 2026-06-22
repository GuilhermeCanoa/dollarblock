---
name: pre-pr
description: Prepara a branch atual do DollarBlock para abrir um Pull Request — rebase em cima de origin/master, build, testes unitários e checagem de arquivos compartilhados (hotspots de conflito), reportando o que precisa de atenção. Use quando o usuário disser "vou abrir um PR", "prepara pro PR", "pronto pra revisar", "pre-pr" ou pedir para validar a branch antes de enviar.
---

# pre-pr — higiene de branch antes do Pull Request

Roda a checagem que dois devs em paralelo precisam fazer antes de abrir um PR, para evitar
PR quebrado e merge-surpresa. Reflete as regras de `CONTRIBUTING.md` e `docs/MERGE_HOTSPOTS.md`.

## Quando usar
"Prepara pro PR", "pronto pra revisar", "valida a branch", "pre-pr". **Não** use em `master`
nem para fazer push — esta skill só prepara/valida; abrir o PR é passo separado.

## Passos (pare e reporte no primeiro problema, não force)

### 0. Pré-condições
- Confirme que **não está em `master`** (`git rev-parse --abbrev-ref HEAD`). Se estiver, pare
  e avise — features vivem em branches `feature/<epic>-<desc>`.
- Verifique a árvore: se houver mudanças não commitadas, liste-as e pergunte se devem entrar
  no commit antes de prosseguir (não commite por conta própria sem confirmar).

### 1. Sincronizar com a base (rebase, não merge)
```bash
git fetch origin
git rebase origin/master
```
Se houver conflito: **pare**, liste os arquivos em conflito e oriente o usuário a resolver
(`git status`). Não tente resolver silenciosamente schema do Room ou arquivos compartilhados.

### 2. Build + testes
```bash
# Windows
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain --no-daemon
```
Se falhar, reporte o trecho relevante do log e pare. Não abra PR com build/teste vermelho.

### 3. Checagem de arquivos compartilhados (hotspots)
Liste os arquivos tocados na branch vs `origin/master` e sinalize se algum é hotspot:
```bash
git diff --name-only origin/master...HEAD
```
Hotspots a destacar (ver `docs/MERGE_HOTSPOTS.md`):
- `app/src/main/AndroidManifest.xml`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `data/local/db/DollarBlockDatabase.kt` (schema Room — **só uma pessoa por vez**)
- `core/navigation/TopLevelDestination.kt`
- `res/values/strings.xml`
- `di/DatabaseModule.kt`, `core/**`

Se algum foi tocado, avise: *"Você mexeu em &lt;arquivo compartilhado&gt; — confirme que
combinou com o outro dev e que está em ordem alfabética / blocos isolados conforme MERGE_HOTSPOTS."*

### 4. Higiene de lógica testável
Se o diff adicionou cálculo de negócio dentro de um `combine {}` de ViewModel ou de um
`@Composable`, sinalize que deveria ser extraído para função pura + teste (padrão
`HomeMetrics`/`HomeMetricsTest`).

### 5. Resumo do PR
Produza um resumo pronto para colar na descrição do PR:
- Épico/objetivo, arquivos principais, testes adicionados, hotspots tocados (se houver),
  e "checklist verde: rebase ✓ build ✓ testes ✓".

## Importante
- **Não dá push e não abre o PR automaticamente** — só prepara e reporta. O usuário decide
  quando enviar. O hook `.githooks/pre-push` ainda roda os testes no push como rede de segurança.
- Em caso de conflito de rebase ou falha de teste, o entregável é um relatório claro do que
  corrigir, não uma tentativa de "consertar a qualquer custo".
