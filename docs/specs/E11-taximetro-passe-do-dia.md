# Spec: Rebrand "Taxímetro" + Passe do Dia

**Status:** done
**Épico:** E11
**Data de criação:** 2026-07-02
**Última atualização:** 2026-07-02

---

## Contexto

O app está funcionalmente completo (E0–E10) mas com tom genérico ("Your Number One App for Time Management", "Keep it up"), destoando do MANIFESTO.md. Este épico implementa a identidade verbal/visual definida com o usuário e simplifica a dinâmica de pagamento.

**Big Idea: "O taxímetro do seu vício"** — todo o app deriva da metáfora financeira: taxímetro (Home), fatura (bloqueio), extrato (estatísticas), recibos (histórico), contrato (onboarding).

**Voz: "o gerente do seu banco de tempo"** — seco, irônico, deadpan. Nunca motivacional, nunca moralista. O app não julga, ele *cobra*.

- ✅ Fatos com preço: "47 min de Instagram hoje. R$ 2,18. Um café."
- ✅ Ironia por subestimação: "O TikTok agradece a doação."
- ❌ Motivacional: "Você consegue! 💪"
- ❌ Bronca moralista: "Você está viciado."

**Passe do dia**: pagamento único de R$ 1,00 libera o **app bloqueado** (só ele) até a **meia-noite local**. Máximo 1 pagamento/dia por app (consequência natural: pago, não bloqueia mais até meia-noite). Substitui a janela de 5 minutos medida em tempo de uso.

**Mascote**: não há personagem; o taxímetro é o objeto-assinatura da marca.

## Requisitos

- R1: Pagamento bem-sucedido libera o app bloqueado até a meia-noite local do dia do pagamento (wall-clock, não tempo de uso).
- R2: Preço exibido e registrado = R$ 1,00 (valor real cobrado depende do Lambda `unlock-charge` — follow-up fora do repo).
- R3: Todas as strings (EN + PT) seguem a voz "gerente do banco de tempo"; nenhuma string motivacional/genérica sobra.
- R4: MANIFESTO.md descreve o passe do dia e registra as regras de voz.
- R5: Home exibe o taxímetro: contador monetário com count-up animado, numerais mono/tabulares, vermelho com prejuízo / verde sem, e linha de equivalência concreta ("= 1 café").
- R6: Tela de bloqueio tem estética de fatura/recibo com carimbo "BLOQUEADO" e copy escalonada pelo nº de desbloqueios pagos no dia.
- R7: Lógica nova (equivalências, fim do dia, serialização do grant) em funções puras com testes JVM.

## Tarefas

- [x] T1: `GooglePayConfig`: PRICE=1.00, remover janela de minutos.
- [x] T2: `BlockPreferences`: `UnlockGrant(unlockUntilMs)`, serialização v2 `"pkg|untilMs"`, `grantUnlockForToday()`.
- [x] T3: `DollarBlockAccessibilityService`: gate de unlock por wall-clock (`now < unlockUntilMs`).
- [x] T4: `BlockActivity`: usar `grantUnlockForToday`; toast "liberado até a meia-noite".
- [x] T5: Copy pass completo `values/strings.xml` + `values-pt/strings.xml`.
- [x] T6: MANIFESTO.md atualizado (passe do dia + seção "Como falamos").
- [x] T7: Home taxímetro: count-up + equivalência (`HomeMetrics` puro + testes) + token tipográfico mono.
- [x] T8: Tela de bloqueio: layout recibo + carimbo + copy escalonada (`countUnlocksSince` no EventDao).
- [x] T9: Testes unitários verdes; validação end-to-end no emulador.
- [x] T10: CHANGELOG.md atualizado.

## Critérios de aceite

- CA1: `.\gradlew.bat :app:testDebugUnitTest` verde.
- CA2: No emulador: app com limite de 1 min estoura → tela de fatura nova → "Simular pagamento" → app abre e **não** re-bloqueia em reaberturas sucessivas (passe até meia-noite).
- CA3: Home mostra contador com count-up e equivalência; screenshots capturadas.
- CA4: Nenhuma string com tom motivacional/genérico restante nos dois idiomas.

## Notas / Decisões

- Validado no emulador (2026-07-02): Calendar com limite de 1 min → fatura com carimbo "BLOCKED" → "Simulate payment" → app liberado, sem re-bloqueio após 40s de uso + relançamento; Home registra "Bailed out Calendar · R$ 1.00". A equivalência/hero vermelho não apareceu na validação porque o sync do `DailyUsageEntity` (E4) estava defasado — comportamento pré-existente; caminho coberto por testes JVM.

- Moeda permanece BRL; multimoeda (USD para estrangeiros) fica para épico futuro.
- Grants antigos no formato de 3 partes (`pkg|grantedAt|duration`) são ignorados no parse — janelas de 5 min pré-migração simplesmente expiram. Perda aceitável.
- O amount real cobrado é fixo no Lambda (o app não envia amount) — ajustar o Lambda para 1.00 BRL é follow-up manual fora deste repositório.
- Copy escalonada usa contagem de unlocks pagos do dia entre todos os apps (query nova `countUnlocksSince`).
