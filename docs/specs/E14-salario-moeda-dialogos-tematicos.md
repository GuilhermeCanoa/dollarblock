# E14 — Salário configurável, moeda, diálogos temáticos e polimento de UX

**Status:** done
**Data:** 2026-07-04

## Contexto

Lote de correções e features pedidas pelo product owner: aba Apps lenta, avisos de
permissão ausente, salário configurável no lugar dos R$ 2.000 fixos, moeda de exibição,
padronização de todos os pop-ups no tema da casa, transparência ética na tela de
bloqueio, ajustes de copy do onboarding, sugeridos personalizados, carimbo menor e
remoção do botão de pagamento simulado.

## Entregas

- [x] **Bug — aba Apps lenta:** `InstalledAppsProvider` agora cacheia o resultado de
  `getLaunchableApps()` (singleton, cache por processo). A recarga de label+ícone de
  todos os apps acontecia a cada navegação para a aba.
- [x] **Aviso diário de permissões:** `PermissionNagPreferences` (DataStore) +
  `MainViewModel.checkPermissionNag()` — modal temático ("O taxímetro está rodando às
  cegas") no máximo 1×/dia de navegação, listando as permissões faltantes, com CTA para
  o Profile.
- [x] **Salário configurável:** `MoneyPreferences` (DataStore `dollarblock_money`) +
  `MoneySettings`/`AppCurrency`/`MoneyFormat` em `domain/model`. Card "Adicione o seu
  salário" na Home abre modal com copy provocativo; `HomeMetrics.perMinuteRate(salário)`
  substitui a constante nos cálculos de Home, Statistics e Profile. Padrão continua
  R$ 2.000/mês.
- [x] **Moeda de exibição (BRL/USD):** preferência no Profile (Automática/Real/Dólar);
  Automática resolve pelo locale (BR/pt → Real, resto → Dólar), igual ao idioma. Só
  formatação dos valores derivados do salário — a cobrança do passe do dia segue em BRL
  ("Dinheiro gasto/economizado" continuam em R$ por serem cobranças reais).
- [x] **Diálogos temáticos:** novo `DollarBlockDialog` no design system (vidro + stroke
  Mint Glow + overline mono estilo recibo + divisor tracejado). Todos os `AlertDialog`
  genéricos migrados (Home info cards, Apps ×4, Profile ×3 + moeda + sobre, nag de
  permissões, ética do bloqueio). Regra registrada em `docs/STYLEGUIDE_ANDROID.md` §4.
- [x] **Ética do bloqueio:** link discreto "Entenda o bloqueio de tela" no rodapé da
  tela de bloqueio → diálogo "O combinado não sai caro" (não é vírus, pagar é opcional,
  monitoria pode ser desativada na aba Apps). Mesmo conteúdo estendido no "Sobre o
  DollarBlock" do Profile (`about_body`).
- [x] **Onboarding:** "Assinar" → "Topo o desafio" (EN "Challenge accepted"), rodapé do
  contrato ajustado; página Quick Summary internacionalizada (era hardcoded em inglês)
  com o copy "Aqui estão os apps que estão roubando o seu tempo…".
- [x] **Sugeridos personalizados:** "Os suspeitos de sempre" agora traz primeiro o top 5
  de uso real da semana (`UsageStatsProvider.getWeeklyUsageByPackage()`), depois a lista
  fixa de redes sociais; sempre filtrando apps já no taxímetro.
- [x] **Carimbo BLOQUEADO:** reduzido (12sp, borda 2dp) e posicionado à direita do nome
  do app no recibo — o nome voltou a ser legível.
- [x] **Pagamento simulado:** botão removido da UI; caminho de código preservado atrás
  de `BlockingDevFlags.SIMULATED_PAYMENTS` (const false, só efetiva em debug).

## Testes

- `MoneySettingsTest` — formatação BRL/USD, resolução de moeda por locale e
  `perMinuteRate`. Suite completa verde (`:app:testDebugUnitTest`).

## Fora de escopo / notas

- Conversão cambial real não existe: a moeda é só símbolo/formatação.
- O cache de apps instalados não se invalida em install/uninstall durante o processo.
