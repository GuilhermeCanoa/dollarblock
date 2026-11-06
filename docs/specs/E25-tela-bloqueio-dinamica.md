# Spec: E25 — Tela de bloqueio dinâmica (3 cenas do mascote, sorteadas)

**Status:** done
**Épico:** E25 (adhoc)
**Data de criação:** 2026-11-06
**Última atualização:** 2026-11-06

---

## Contexto

A tela de bloqueio (`BlockActivity`) tem informação demais. Hoje ela mostra a ilustração
`block_handover.jpg` (guardião + cofre + luminária), o título "Sua fatura chegou", o recibo
completo, a frase de reflexão em menta, a nota de privacidade do Google Play, os botões e o
link "Entenda o bloqueio". Tudo disputa a atenção num momento em que o usuário só precisa
entender duas coisas: **o limite acabou** e **dá para pagar o passe do dia ou ir embora**.

O pedido:

- Tirar a ilustração estática.
- Pôr no lugar o **mascote vivo** (o escudo desenhado em código do E23,
  `core/designsystem/mascot/`) interagindo com a fatura.
- Manter o recibo parecido com o de hoje.
- Ter **três cenas** que se alternam a cada bloqueio, sorteadas, para a tela não ficar
  repetitiva.

Mockups aprovados: `docs/design/block-screen-variants/` (`comparativo.png`, um PNG por cena;
`src/mock.html` é o HTML que gerou os prints, com o mascote portado para SVG e as mesmas
proporções e poses).

| Cena | Arquivo | Ideia |
|---|---|---|
| **A — O mascote é a tela** | `A-mascote-e-a-tela.png` | Sem recibo grande. O mascote segura um recibo em miniatura. Abaixo: "Instagram fechou por hoje", uma linha de apoio, **R$ 1,00** grande em menta, "vale até a meia-noite" e um chip "ver fatura completa" que abre o recibo de hoje numa bottom sheet. |
| **B — Atrás do recibo** | `B-atras-do-recibo.png` | O mascote fica atrás do recibo e o segura pelas bordas de cima: aparecem cabeça e mãos. O recibo é o de hoje, sem a linha de nº e data. |
| **C — O carimbo** | `C-carimbo.png` | O recibo fica levemente torto (−1,5°), com a mensagem curta e o carimbo "BLOQUEADO" maior. O mascote está de pé à direita, com um carimbo de borracha na mão. Mantém o título "Sua fatura chegou", em tamanho menor. |

### O que sai em todas as cenas

- A ilustração `block_handover.jpg` e todo o `BlockHandover.kt` (arte, escurecimento,
  `receiptHandover`).
- A frase de reflexão (`home_quotes`).
- O título grande. Só a cena C mantém uma versão menor.
- A nota de privacidade como linha separada. Ela vai para o rodapé:
  "Entenda o bloqueio · cobrança pelo Google Play". **O texto continua visível; só muda de
  lugar** (é exigência do E19 e não pode sumir).
- Nas cenas B e C o preço também vai para o botão: "Pagar o passe do dia · R$ 1,00".

### Análise de performance — sem impeditivo

- **Só uma cena é composta por bloqueio.** As outras duas nem entram na composição; sortear
  não custa nada em tempo de execução.
- **Ganho de memória:** sai o decode do `block_handover.jpg` (1024×1536 ARGB ≈ 6 MB de
  bitmap) e o APK fica menor. O mascote é vetorial (`Canvas`).
- **Primeiro frame mais leve:** a tela precisa aparecer antes que o app bloqueado volte para
  a frente (ADR-001, cerca de 40 ms). Sem decodificar o JPG, o primeiro frame fica mais
  barato. O sorteio da cena tem de ser síncrono e em memória: nada de ler DataStore antes de
  desenhar.
- **Custo por frame:** é o mesmo do `HomeMascot`/`LimitReactionMascot` (E23), que já roda
  bem na Home. O `drawShieldMascot` aloca alguns `Path` a cada frame. É aceitável, mas
  **o laço de frames não pode ser contínuo para sempre**:
  - a entrada (≤ 2 s) roda a 60 fps;
  - no repouso, gestos curtos (1–2 s) separados por pausas de 6–12 s feitas com `delay`
    (sem `withFrameNanos`), como no `HomeMascot`;
  - o laço só roda com a activity em `RESUMED`. Use `repeatOnLifecycle` ou
    `LocalLifecycleOwner`. A `BlockActivity` é re-afirmada por cima do app alvo várias vezes
    e não pode deixar animação girando em segundo plano.
- **"Remover animações" ligado** (`ANIMATOR_DURATION_SCALE == 0`): cada cena abre direto na
  pose final parada, sem laço nenhum, como hoje (`rememberAnimationsEnabled`).

## Requisitos

- R1: A cada abertura da `BlockActivity`, sorteia uma cena entre {A, B, C} com chance igual,
  **sem repetir a cena da abertura anterior** enquanto o processo estiver vivo. O sorteio é
  uma função pura `pickBlockScene(last, random)` com teste. A última cena fica num
  `object`/singleton em memória, sem persistir em disco.
- R2: Em build **debug**, o extra `extra_scene` (`A`|`B`|`C`) na intent força a cena, para
  revisar cada uma por adb:
  `adb shell am start -n com.dollarblock/.feature.blocking.BlockActivity --es extra_label Instagram --es extra_package com.instagram.android --es extra_scene B`.
  No release o extra é ignorado.
- R3: As três cenas usam **os mesmos controles** (`BlockControls`) e **o mesmo rodapé**:
  - pagar;
  - processando;
  - falha → cortesia (E17), com botão de tentar de novo;
  - Billing indisponível;
  - "Simular pagamento" (debug);
  - "Ficar com meu dinheiro";
  - link "Entenda o bloqueio", que abre o `DollarBlockDialog` ético.

  A lógica de pagamento da `BlockActivity` (`onPay`, `onCourtesyUnlock` etc.) **não muda**.
  A cena é só um slot visual.
- R4: Os botões ficam visíveis e tocáveis em **≤ 1 s** depois que a tela abre, em qualquer
  cena (hoje são cerca de 750 ms). A animação da cena nunca segura uma ação.
- R5: O recibo continua sendo o `InvoiceReceipt`, que ganha parâmetros opcionais com o
  padrão de hoje: `showMeta`, `showBarcode`, `emphasizedStamp` (carimbo maior, da cena C) e
  `message`. A cena B usa `showMeta = false`. A cena C usa a mensagem curta e
  `emphasizedStamp = true`.
- R6: O mascote ganha dois adereços: **recibo em miniatura** e **carimbo de borracha**
  (`MascotProp.RECEIPT`, `MascotProp.STAMP`). Também ganha um jeito de desenhar **só as
  mãos** por cima de outro conteúdo (cena B: as mãos ficam na frente do papel e o corpo
  atrás). Tudo isso continua desenhado em código, sem arte nova.
- R7: As coreografias das cenas são funções puras do tempo, num arquivo novo
  (`feature/blocking/scene/BlockSceneChoreography.kt`), com testes. O arquivo do E23,
  `MascotChoreography.kt`, fica intocado, para não gerar conflito.
- R8: Strings novas em `values`, `values-pt` e `values-hi`, na voz da marca (MANIFESTO,
  "Como falamos"): título da cena A (`%1$s fechou por hoje`), subtítulo, "ver fatura
  completa", rodapé combinado e botão com preço (`Pagar o passe do dia · %1$s`). O preço vem
  do Play Billing quando estiver carregado (`playPrice`), como hoje.
- R9: Acessibilidade: o mascote é decorativo (sem semântica). O TalkBack lê, na ordem:
  título/app → valor → botões. Na cena A, o chip "ver fatura completa" é um botão com
  rótulo.
- R10: Os textos que o smoke test procura continuam presentes em **todas** as cenas:
  - "Ficar com meu dinheiro" / "Keep my money";
  - o aviso de falha;
  - o botão "de graça".

## Coreografia das cenas

Tempos a partir do primeiro frame. Haptic = `HapticFeedbackType.LongPress` (o mesmo de
hoje). "Pose final" é a que aparece com as animações desligadas.

### A — O mascote é a tela

| t (ms) | O que acontece |
|---|---|
| 0–700 | O mascote entra andando pela direita até o centro (`walking`, como no E23), com o recibinho na mão da frente. O texto (título → subtítulo → valor) aparece em fade escalonado de 80 ms. |
| 700–1000 | Ele para e estende o recibinho para o usuário (braço em `HoldForward` → pose de entrega; sobrancelha da frente erguida, `quirk`). Haptic quando o braço para. Os controles aparecem com fade a partir de 600 ms. |
| repouso | Respira (y ± 0,6 u). A cada 6–12 s, um gesto sorteado: **bater o pé**, impaciente (2 batidas); **brilho nos óculos** (`glint`); **olhar para o botão** (`look` + `lookDown`). |
| toque no recibinho ou no chip | O recibinho "salta" (escala 1 → 1,15 → 0) e abre uma `ModalBottomSheet` com o `InvoiceReceipt` completo. O carimbo cai como hoje (escala 2,4 → 1 com mola, haptic). |

Pose final: de pé no centro, braço estendido segurando o recibinho, a outra mão na cintura.

### B — Atrás do recibo

| t (ms) | O que acontece |
|---|---|
| 0–600 | Recibo + mascote sobem juntos de baixo da tela (translateY 100% → 0, `FastOutSlowIn`), com uma oscilação de papel (rotação −2° → 1° → 0, em mola). |
| 600–900 | Ele espia por cima: as sobrancelhas sobem (`brow` 0 → 0,5) e a cabeça inclina (`tilt` 0 → −6° → 0). Os controles aparecem com fade a partir de 500 ms. |
| 900–1150 | O carimbo "BLOQUEADO" cai (o mesmo de hoje). No impacto: haptic, o recibo dá um tremor de 2 px e ele franze a testa (`brow` → −0,4). |
| repouso | Respira (a cabeça sobe e desce cerca de 1 u). A cada 6–12 s: **olhar para os botões** (`lookDown`), **sobrancelha da frente** (`quirk`) ou **puxar o recibo** um pouco para cima (2 px). |

Pose final: espiando por cima, testa levemente franzida, mãos na borda de cima do papel.

### C — O carimbo

| t (ms) | O que acontece |
|---|---|
| 0–700 | O recibo é "impresso" de cima para baixo: um clip vertical revela o papel de 0 → 100%, de modo linear, com a borda serrilhada de baixo sempre visível. O título "Sua fatura chegou" aparece em fade. |
| 300–900 | O mascote entra pela direita até a borda do recibo e ergue o carimbo (o braço da frente sobe para cerca de 120°). |
| 900–1100 | Bate o carimbo: o braço desce rápido e "BLOQUEADO" aparece **grande** (escala 1,6 → 1, com mola). Haptic + tremor de tela de 3 px por 120 ms. Os controles aparecem com fade a partir de 700 ms. |
| 1100–1800 | Ele assopra o carimbo (inclina o escudo, com 2–3 bolinhas de "fumaça" subindo, o mesmo efeito do vapor da caneca do E23) e volta à pose satisfeita: braço da frente com o carimbo erguido, a outra mão na cintura, brilho nos óculos. |
| repouso | A cada 6–12 s: **girar o carimbo** na mão ou **bater o pé**. |

Pose final: ao lado do recibo, carimbo erguido, mão na cintura, sobrancelhas erguidas.

**Fora de escopo:** o som de impressora que eu sugeri na apresentação da cena C. Exigiria
um asset de áudio e respeitar o modo silencioso. Se quiser, fica para depois.

## Tarefas

- [x] T1: `feature/blocking/scene/BlockScene.kt`: `enum class BlockScene { A_MASCOT, B_BEHIND, C_STAMP }`, `pickBlockScene(last, random)` e um `BlockSceneRotation` (singleton em memória com a última cena).
- [x] T2: Teste JVM de `pickBlockScene`: nunca repete a cena anterior, as três aparecem, distribuição ≈ uniforme em 3.000 sorteios e `last = null` funciona.
- [x] T3: Mascote:
  - `MascotProp.RECEIPT` e `MascotProp.STAMP` (desenho em `ShieldMascot.kt`, seguindo o padrão de `drawMug`/`drawPhone`);
  - função pública para desenhar só as mãos da pose (`drawShieldMascotHands`);
  - pose de "agarrar" para a cena B;
  - expor como `internal` o necessário de `armPoints`.
  - Não alterar o comportamento dos atos do E23 (os testes do E23 continuam verdes).
- [x] T4: `InvoiceReceipt`: parâmetros `showMeta`, `showBarcode` e `emphasizedStamp`, com padrão = o comportamento de hoje.
- [x] T5: `BlockSceneChoreography.kt`: poses puras por cena (`entrance(t)`, `idle(gesture, t)` e `still()`), com testes: a entrada termina exatamente na pose final, o repouso fecha o laço sem emenda e as poses ficam dentro da tela.
- [x] T6: Composables das cenas em `feature/blocking/scene/`: `MascotHeroScene` (A, com a `ModalBottomSheet`), `HeldReceiptScene` (B) e `StampScene` (C). Cada uma recebe o estado do recibo e expõe `controlsAlpha`.
- [x] T7: Refatorar `BlockScreen` em `BlockActivity.kt` para um scaffold: slot da cena (peso flexível) + `BlockControls` (extraídos do código atual, sem mudar o comportamento) + rodapé combinado. Remover a frase de reflexão, a nota de privacidade separada e o título (exceto na cena C).
- [x] T8: Remover `BlockHandover.kt` e `res/drawable-nodpi/block_handover.jpg`, depois de confirmar com `grep` que não sobrou nenhum uso. Remover `home_quotes` só se não houver outro uso.
- [x] T9: Extra de debug `extra_scene` (R2). Atualizar o comentário de uso em `app/src/debug/java/com/dollarblock/debug/DebugSetupReceiver.kt`.
- [x] T10: Strings (R8) em `values`, `values-pt` e `values-hi`.
- [x] T11: Laço de animação dependente do lifecycle (`RESUMED`) e caminho sem animação (pose final parada).
- [x] T12: Validação no emulador (fixar `ANDROID_SERIAL` no emulador, ver memória de smoke test): abrir cada cena via `extra_scene`, tirar screenshot e comparar com os PNGs de `docs/design/block-screen-variants/`. Testar também os estados "processando", "falha/cortesia" (Billing indisponível no emulador) e com "Remover animações" ligado.
- [x] T13: `testDebugUnitTest` verde + `scripts/smoke-test-emulator.ps1` verde (a cena sai sorteada; os textos verificados existem nas três).
- [x] T14: Docs:
  - status do spec → `done`;
  - entrada no `docs/CHANGELOG.md` (só acrescentar no fim; o arquivo é hotspot);
  - atualizar a seção "Blocking"/"Payment" do `CLAUDE.md`, se mencionar a arte do handover.

## Critérios de aceite

- CA1: Abrir a tela de bloqueio 6 vezes seguidas: nunca aparece a mesma cena duas vezes em seguida, e as três aparecem.
- CA2: Cada cena, na pose final, bate visualmente com o PNG correspondente em `docs/design/block-screen-variants/`.
- CA3: Os botões ficam tocáveis em ≤ 1 s em todas as cenas. Pagar, cortesia (E17), "Ficar com meu dinheiro" e o diálogo "Entenda o bloqueio" funcionam igual a hoje.
- CA4: Com "Remover animações" ligado, cada cena abre parada na pose final.
- CA5: Com a tela de bloqueio em segundo plano (Home do sistema por cima), não há laço de frames rodando. Verificar com `adb shell dumpsys gfxinfo com.dollarblock`: o total de frames não cresce.
- CA6: `block_handover.jpg` não existe mais no APK.
- CA7: `sh scripts/pre-release-check.sh` e `powershell -File scripts/smoke-test-emulator.ps1` verdes.

## Notas / Decisões

- **Por que sortear em memória, sem persistir:** a `BlockActivity` roda no mesmo processo do
  serviço de acessibilidade. Um singleton basta para "não repetir a anterior", e ler
  DataStore atrasaria o primeiro frame, que precisa ganhar a corrida contra o app bloqueado
  (ADR-001). Se o processo morrer, repetir uma vez não faz mal.
- **Por que o recibo continua sendo o `InvoiceReceipt`:** o conteúdo muda a cada bloqueio
  (app, preço, data), e o carimbo com haptic já é o momento marcante da tela.
- **Trabalho em paralelo:** outra feature (E24, extrato/calendário) está em andamento em
  `feature/statistics`, `feature/profile/HistoryScreen.kt`, `domain/model` e `HomeScreen`.
  Este épico não deve tocar nesses arquivos. Os arquivos compartilhados que ele toca
  (`strings.xml` ×3, `CHANGELOG.md`, `ShieldMascot.kt`) recebem só acréscimos.

## Implementação — desvios e achados (2026-11-06)

- **Re-afirmação do bloqueio:** o serviço relança a BlockActivity duas vezes por bloqueio
  (a 2ª ~500 ms depois, com `FLAG_ACTIVITY_CLEAR_TASK`, que **recria** a activity). Um
  sorteio por `onCreate` trocaria a cena no meio da entrada. `resolveBlockScene` (puro,
  testado) mantém a cena e o início da entrada quando o mesmo app reabre dentro de
  `BLOCK_SCENE_REASSERT_WINDOW_MS` (2 s); a nova instância continua a entrada de onde a
  outra estava. Recriação por configuração mantém a cena (savedInstanceState) e abre na
  pose final.
- **`MascotProp`:** o enum mora em `MascotChoreography.kt`, que o R7 pede intocado. A única
  mudança nele foi acrescentar `RECEIPT, STAMP` à linha do enum; nenhum ato do E23 mudou
  (os 14 testes do E23 continuam verdes). A pose de "agarrar" (`Held.Grip`) e as demais
  poses das cenas ficam em `BlockSceneChoreography.kt`.
- **Adereços:** no `STAMP`, `propPhase` é o giro do carimbo (graus) e `propOpen` o
  progresso do sopro (0..1); no `RECEIPT`, `propOpen` é o "salto" do toque.
- **`InvoiceReceipt`:** `stampScale`/`stampAlpha` passaram de `Animatable` para lambdas
  `() -> Float` (único chamador era a BlockActivity), para as cenas B/C dirigirem o carimbo
  pela coreografia pura e a sheet da cena A continuar com `Animatable`.
- **`home_quotes`:** ficou sem uso, mas **não** foi removido — os `strings.xml` deste épico
  só recebem acréscimos.
- **Smoke test:** o regex do botão de cortesia (`for free|de gra`) também casava com o
  subtítulo da cena A e com mensagens do recibo, e o `TapText` toca o primeiro nó. Passou a
  procurar o rótulo exato (`^Open .+ for free$|^Abrir .+ de gra`).
- **Não validado no emulador:** o estado "processando" e o botão de pagar habilitado
  (o emulador não tem Play Store, então o Billing nunca fica pronto; `SIMULATED_PAYMENTS`
  está desligado). O código desses ramos foi movido sem alteração para `BlockControls`.
- `scripts/build-block-handover.ps1` (gerava a arte removida) ficou no repositório, sem uso.
