# Merge Hotspots — DollarBlock

Os arquivos abaixo são **compartilhados** e concentram a maioria dos conflitos de merge
quando dois devs trabalham em paralelo. Para cada um há uma técnica que torna as adições
paralelas **auto-mescláveis** pelo git. Antes de editar qualquer um deles, avise o time.

> Regra geral: o git mescla bem **adições em linhas diferentes e isoladas**. Conflito real
> nasce quando dois commits mudam **a mesma linha** ou linhas adjacentes sem separação.

---

## 1. `gradle/libs.versions.toml`

**Por quê:** os dois adicionam dependências/versões no mesmo bloco.

**Técnica:** manter as seções `[versions]`, `[libraries]` e `[plugins]` em **ordem
alfabética**. Adições alfabéticas caem em linhas distantes e o git resolve sozinho.
Nunca adicione "no fim do bloco" — insira na posição alfabética.

---

## 2. `app/build.gradle.kts`

**Por quê:** bloco `dependencies { }` editado pelos dois (hoje já tem linhas Room duplicadas).

**Técnica:** uma dependência por linha, agrupada por comentário de seção já existente
(`// Hilt`, `// Room`, `// Test`...). Adicione dentro da seção correta. Evite reordenar o
bloco inteiro num PR — isso transforma toda adição alheia em conflito.

---

## 3. `app/src/main/AndroidManifest.xml`

**Por quê:** novas `Activity`/`Service`/`<uses-permission>` entram no mesmo arquivo.

**Técnica:** cada componente em seu **próprio bloco, separado por uma linha em branco**.
Permissões agrupadas no topo, uma por linha. Blocos isolados = merge automático.

---

## 4. `data/local/db/DollarBlockDatabase.kt` — Room (o mais perigoso)

**Por quê:** adicionar entidade exige mexer na lista `entities`, possivelmente no `version`
e numa `Migration`. Dois PRs fazendo isso na mesma semana **sempre** colidem, e errar a
migração corrompe o banco do usuário.

**Técnica / processo:**
- **Só uma pessoa por vez** mexe no schema do banco. Anuncie no canal antes de começar.
- O PR que altera o schema (`version`, `entities`, `Migration`) **entra antes** de qualquer
  outro PR que dependa dele. Quem está esperando faz rebase depois.
- Bump de `version` + `Migration(n, n+1)` + teste de migração andam **juntos no mesmo PR**.
- DAOs e bindings de repositório foram movidos para módulos Hilt **por feature** (ver abaixo),
  então adicionar um DAO normalmente **não** toca o `DatabaseModule` compartilhado.

---

## 5. Navegação — `core/navigation/`

**Por quê:** historicamente todos editavam `DollarBlockNavHost` e `TopLevelDestination`.

**Técnica (já aplicada):** cada feature declara sua rota e sua `NavGraphBuilder` extension
no próprio pacote (`feature/<nome>/<Nome>Navigation.kt`). O `DollarBlockNavHost` só chama
`homeScreen()`, `appsScreen()` etc. — adições viram **uma linha isolada**.
`TopLevelDestination` (abas da bottom bar) continua compartilhado, mas referencia as
constantes de rota das features; mexa nele só ao adicionar/remover uma aba de primeiro nível.

---

## 6. Hilt DI — `di/`

**Por quê:** `DatabaseModule` e `RepositoryModule` eram dois arquivos editados por todos.

**Técnica (já aplicada):** um **módulo por feature** (`EventsModule`, `MonitoredAppModule`...).
`DatabaseModule` agora provê apenas o `DollarBlockDatabase`. Cada dev mexe só no módulo da
sua feature e os arquivos nunca colidem.

---

## 7. `res/values/strings.xml`

**Por quê:** strings de UI adicionadas pelos dois.

**Técnica:** agrupe por feature com comentário (`<!-- home -->`, `<!-- statistics -->`) e
adicione dentro do grupo da sua feature. Nunca reordene o arquivo inteiro.
