# Spec: Cobranças do gerente (notificações de custo do tempo de tela)

**Status:** done
**Épico:** E22
**Data de criação:** 2026-10-26
**Última atualização:** 2026-10-26

---

## Contexto

Até aqui a única notificação era o aviso de "faltam 5 min" do limite (E13). O usuário
quer mais lembretes do custo do tempo de tela, no tom do MANIFESTO (gerente do banco do
tempo: seco, deadpan, sem emoji, sem motivacional). Rafael aprovou 24 frases em
2026-10-26 e cortou uma (C12, "movimentação suspeita"); ficaram 23.

## Requisitos

- R1: 23 frases (ids A1–A5, B6–B11, C13–C17, D18–D20, E21–E24) em PT, EN e HI, com os
  números reais do usuário: tempo, valor (salário e moeda configurados) e projeções anuais.
- R2: Gatilhos:
  - **Sessão longa** (A, C13–C16 e projeções B quando há histórico): app monitorado
    com limite aberto há 30 min seguidos, no máximo uma vez por app por dia.
  - **Projeções B6–B11**: só com pelo menos 3 dias de histórico e média ≥ 15 min/dia, e
    cada frase só quando o número dela é crível (ex.: B8 só com ≥ 1 maratona, B11 entre
    5% e 99% do curso).
  - **C17 débito automático**: primeira abertura do dia a até 45 min do horário habitual
    (5 de 7 dias perto da mediana); no máximo 1×/semana por app.
  - **E21**: primeira abertura de um app monitorado antes das 9h; no máximo 1× a cada 3 dias.
  - **E22**: app monitorado aberto até 10 s depois de desbloquear a tela; no máximo 1× a cada 2 dias.
  - **D18–D20 fechamento do dia**: a partir das 21h, uma vez por dia (pelo worker de 15 min).
  - **E23/E24**: recibo do 1º e do 2º passe pago no dia (a cortesia não gera recibo).
- R3: Teto de **2 cobranças por dia**; o fechamento do dia e o aviso de limite ficam fora
  do teto. O sorteio nunca repete a última frase enviada quando há alternativa.
- R4: Canal próprio ("Cobranças do gerente") e uma chave própria no Perfil, separada do
  aviso de limite. Tocar na notificação abre o app.

## Tarefas

- [x] T1: Catálogo + contas + regras puras (`service/nudge/Nudge.kt`) com testes (`NudgeTest`).
- [x] T2: `NudgePreferences` (ligado/desligado, contagem do dia, última frase, data de cada gatilho).
- [x] T3: `NudgeNotifier` (canal, BigText, toque abre o app) e `NudgeEngine` (gatilhos e texto).
- [x] T4: Ganchos: serviço de acessibilidade (sessão, abertura, desbloqueio), `UsageSyncWorker`
  (fechamento), `BlockActivity.onPaymentSuccess` (passe), `DollarBlockApp` (canal).
- [x] T5: Chave "Cobranças do gerente" no Perfil.
- [x] T6: Textos PT/EN/HI.
- [x] T7: Atalho de debug (`DEBUG_NUDGE`, `DEBUG_NUDGE_SESSION`) no `DebugSetupReceiver`.
- [x] T8: Validado no Galaxy S9+ (2026-10-26): preview das 23 frases, sessão longa real (dispara 1×, não repete no dia) e fechamento do dia real às 21h+.

## Critérios de aceite

- CA1: `./gradlew :app:testDebugUnitTest` verde.
- CA2: No debug, `adb shell am broadcast -a com.dollarblock.DEBUG_NUDGE --es nudge ALL --es pkg <pkg> --es label <Nome> -p com.dollarblock`
  posta as 23 frases com números coerentes.
- CA3: `adb shell am broadcast -a com.dollarblock.DEBUG_NUDGE_SESSION --ei minutes 1 -p com.dollarblock`
  e 1 min dentro de um app monitorado com limite → uma cobrança da sessão; não repete no
  mesmo dia para o mesmo app.

## Notas / Decisões

- **B10:** a frase aprovada ("É o preço de {item}") usa o catálogo de comparações da Home
  e só entra com unidades inteiras ("2 PS5"), não com frações ("37% de um iPhone").
- **B11:** a frase aprovada dizia "um quarto de um curso"; isso só vale para cerca de
  175 h por ano. Virou um percentual calculado ("26% de um curso completo de espanhol").
- **A1:** os 900 km valem para 30 min por dia, que é exatamente o gatilho. Comparação por
  idioma: PT São Paulo–Rio ida e volta (~860 km); EN Nova York–Detroit (~990 km); HI
  Délhi–Ahmedabad (~950 km).
- A sessão longa só é medida em apps **com limite**: o loop de acompanhamento do serviço
  só roda para eles. Apps monitorados sem limite recebem só as cobranças de hábito e o
  fechamento do dia.
- **Referências:** caminhada a 5 km/h; livro ≈ 7 h; plano de maratona ≈ 80 h; curso de
  espanhol ≈ 700 h (FSI, 600–750 h).
- O Android mostra no máximo **25 notificações ativas por app**; o preview `ALL` do debug
  ocupa quase toda essa cota (as seguintes são descartadas até limpar). Na vida real o teto
  de 2 por dia deixa isso longe.
