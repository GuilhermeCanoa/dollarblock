# Spec: Cards de dinheiro na Home, balões informativos, avisos de mudança de limite e apps sugeridos

**Status:** done
**Épico:** E13 (adhoc)
**Data de criação:** 2026-07-03
**Última atualização:** 2026-07-03

---

## Contexto

A Home mostra o prejuízo do dia, mas não mostra o dinheiro **real** que o usuário já pagou por
passes do dia, nem o quanto ele "economizou" ao resistir a telas de bloqueio sem pagar. Além
disso os cards não explicam como as contas são feitas, a aba Apps não reage quando o usuário
afrouxa/aperta o próprio limite, e a lista começa vazia sem sugerir os ralos de tempo óbvios.

## Requisitos

- R1: Novo card **Dinheiro gasto** na Home — soma de tudo que o usuário já pagou por liberações
  (unlock_events, todas as datas). A fonte dos dados fica atrás de uma interface de domínio
  (`MoneySummaryRepository`) para trocar por uma API depois sem tocar na UI.
- R2: Novo card **Dinheiro economizado** — para cada par (app, dia) com ≥1 evento de bloqueio
  (tela de bloqueio exibida) e **nenhum** evento de desbloqueio nesse dia, o usuário resistiu:
  conta 1 passe do dia economizado. Valor = nº de pares resistidos × preço do passe do dia.
  Critério usa os **eventos** que alimentam o histórico (block_events/unlock_events), não os
  botões da tela de bloqueio.
- R3: Todo card da Home (taxímetro-herói, Bloqueado agora, Rastreador de vício, Dinheiro gasto,
  Dinheiro economizado) mostra ao toque um balão informativo explicando o que significa e como a
  conta é feita. Tom da marca (deadpan contábil), EN + PT.
- R4: Na aba Apps, ao salvar um limite **maior** que o anterior, o app exibe uma mensagem irônica
  (deadpan, sem bronca); ao salvar um limite **menor**, uma mensagem de reconhecimento seco.
  Só quando havia limite anterior e ambos são não-nulos.
- R5: A aba Apps mostra uma seção de **apps sugeridos** (Instagram, Facebook, TikTok, YouTube)
  quando instalados e ainda não rastreados; a seção fica acima de "Desativados". Toque adiciona ao
  monitoramento. Não quebra quando nenhum sugerido está instalado (seção some).

## Tarefas

- [x] T1: Queries no `EventDao` (amounts de unlocks; pacote+timestamp de blocks/unlocks como Flow)
- [x] T2: `MoneySummaryRepository` (domain) + `MoneyReport` (cálculo puro em domain/model) +
  impl Room em data/repository + módulo Hilt próprio (`MoneySummaryModule`)
- [x] T3: HomeViewModel expõe `moneySpentTotal`/`moneySavedTotal`; HomeScreen adiciona os 2 cards
- [x] T4: Balão informativo (AlertDialog) para todos os cards da Home + strings EN/PT
- [x] T5: AppsViewModel detecta aumento/diminuição de limite e emite aviso; AppsScreen exibe diálogo
- [x] T6: Seção "Sugeridos" na AppsScreen acima de Desativados, filtrada por instalados/não rastreados
- [x] T7: Testes JVM (`MoneyReportTest`, classificação de mudança de limite) + build + CHANGELOG

## Critérios de aceite

- CA1: Pagar um passe (ou simular) soma R$ 1,00 no card Dinheiro gasto.
- CA2: App bloqueado no dia sem pagamento soma 1 × preço do passe no card Dinheiro economizado;
  se o usuário pagar naquele dia para aquele app, aquele dia deixa de contar como economia.
- CA3: Tocar em qualquer card da Home abre o balão explicativo correspondente.
- CA4: Subir limite de 30→60 min mostra a mensagem irônica; baixar 60→30 mostra a de reconhecimento;
  definir o primeiro limite ou remover limite não mostra nada.
- CA5: Com Instagram/YouTube instalados e não monitorados, aparecem em "Sugeridos" acima de
  "Desativados"; adicionados ao tocar; emulador sem os apps não quebra.
- CA6: `./gradlew :app:testDebugUnitTest` verde.

## Notas / Decisões

- "Dinheiro economizado" agrega por (app, dia local) e não por evento de bloqueio, porque o
  serviço de acessibilidade registra múltiplos block_events por sessão/reassert; por construção
  há no máximo 1 pagamento por app por dia (passe do dia), então a economia máxima por app/dia
  é 1 passe.
- O dia corrente conta como resistido enquanto não houver pagamento — se o usuário pagar depois,
  o valor migra de "economizado" para "gasto" sozinho (flows recomputam).
- Preço do passe usado na economia: `PricingRepository.getDayPassPrice()` (rede→cache→default),
  o mesmo preço exibido na tela de bloqueio. Dias antigos usam o preço atual (simplificação
  aceita; o histórico de preço não é armazenado).
- Card "Bloqueado agora" antes navegava para Apps ao toque; agora o toque abre o balão e o balão
  tem ação "Ver apps" para preservar o atalho.
- Descoberto na validação em aparelho pt-BR: `formatReais` fazia swap vírgula/ponto assumindo
  saída em inglês do `String.format`, exibindo "R$ 0.00". Corrigido com `Locale.US` explícito na
  Home, Statistics e Profile.
- O diálogo de limite aceitava valores absurdos (ex.: 2060 min); agora valida 1–1440 min
  (`MAX_DAILY_LIMIT_MINUTES`), com mensagem de erro no tom da casa.
