# Spec: E23 — Mascote vivo (Home + aviso de troca de limite)

**Status:** done
**Épico:** E23 (adhoc)
**Data de criação:** 2026-11-05
**Última atualização:** 2026-11-05

---

## Contexto

A Home é estática: o número da conta, a barra de orçamento e os cartões. O aviso de troca
de limite (Apps → limite diário) é só texto. Pedido: deixar a Home "mais viva" com o escudo
do DollarBlock aparecendo de vez em quando — espiando do canto, investigando, atravessando
a parte de cima por trás dos números — e pôr o mesmo personagem no aviso de troca de
limite, reagindo: inconformado e em dúvida quando o limite sobe, aprovando quando desce.
Estilo: o mesmo do onboarding (escudo em camadas desenhado em `Canvas`, óculos wayfarer
com brilho, sobrancelhas grossas) e do guia `docs/DollarBlock-Illustrations-Visual-Style-Guide.md`
(escudo = cabeça/tronco, `$` no peito, braços e pernas finos escuros, tênis de sola clara).

## Requisitos

- R1: Personagem reutilizável desenhado em código (`core/designsystem/mascot/`), com pose
  parametrizada (posição, inclinação, sobrancelhas, olhar, braços, passada, adereço).
- R2: Home — o escudo aparece **às vezes** (primeira vez após alguns segundos, depois a cada
  ~12–25 s), sempre por trás do número, recortado no bloco do topo. Atos sorteados, sem
  repetir o anterior:
  - **Espiar do canto**: inclina-se a partir da borda, olha para o número, ergue a
    sobrancelha, volta.
  - **Atravessar**: entra por um lado, para no meio, confere o número com a lupa, sai pelo
    outro lado.
  - **Investigar**: entra um pouco, examina o número com a lupa, coça a cabeça, volta.
- R3: Aviso de troca de limite — o escudo fica no topo do diálogo, em loop lento:
  - **Aumentou**: sobrancelha erguida, cabeça inclinada, coça a cabeça com um "?" no ar,
    depois balança a cabeça devagar com a outra mão na cintura (inconformado).
  - **Reduziu**: comemora com os dois braços para cima, socando o ar, corpo balançando,
    brilho nos óculos (ver D3).
- R4: "Remover animações" do sistema respeitado: Home não mostra o personagem; no diálogo
  ele fica numa pose parada que se sustenta sozinha.
- R5: Decorativo para o leitor de tela; não intercepta toques (o toque no número continua
  abrindo a explicação).
- R6: Coreografia em funções puras (`MascotChoreography`) com testes JVM: todo ato da Home
  começa e termina fora da vista (sem "pulo"), e as poses do diálogo são periódicas.

## Decisões

- D1 (2026-11-05): na espiada aparece só uma lasca do rosto na borda. Testamos trazê-lo mais para dentro e o usuário preferiu a versão discreta.
- D2: na travessia, a parada para conferir o número fica perto da borda (13% / 87% da largura), ao lado do número e não atrás. Atrás do "$", o rosto sumia.
- D3 (2026-11-05): o polegar para cima da aprovação, no tamanho do diálogo, parecia o dedo do meio. A pedido do usuário, virou comemoração com os dois braços para cima, mais óbvia (exceção pontual ao "sem euforia" do guia §4).

## Fora de escopo

- Outras telas (Estatísticas, Perfil, tela de bloqueio) — só depois de validar esta.
- Lottie / arte raster nova.

## Tarefas

- [x] T1: `MascotPose` + `MascotChoreography` (puro) + testes.
- [x] T2: `drawShieldMascot` (Canvas) no estilo do onboarding.
- [x] T3: `HomeMascot` atrás do herói da Home.
- [x] T4: `LimitReactionMascot` no `LimitChangeNoticeDialog` (+ slot `illustration` no
  `DollarBlockDialog`).
- [x] T5: Validado no emulador (tema escuro; a Home não muda com o modo claro do sistema) + CHANGELOG + status `done`.
- [ ] T6: Validar num celular real.

## Critérios de aceite

- CA1: Na Home, o escudo aparece sozinho de tempos em tempos, por trás do número, e some
  sem cortes bruscos.
- CA2: Aumentar um limite mostra o escudo inconformado/em dúvida; reduzir, aprovando.
- CA3: Com animações desligadas, nada se move.
- CA4: `testDebugUnitTest` verde.
