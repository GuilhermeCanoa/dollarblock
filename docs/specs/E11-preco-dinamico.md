# Spec: Preço dinâmico do passe do dia (backend + app)

**Status:** in-progress (bloqueado em T5/T8 — aguardando autorização de deploy AWS)
**Épico:** E11
**Data de criação:** 2026-07-02
**Última atualização:** 2026-07-02

---

## Contexto

O preço do "passe do dia" está hardcoded em dois lugares que já divergiram: o app mostra
R$ 1,00 (`GooglePayConfig.PRICE`) mas o Lambda `unlockCharge` (repo separado
`dollarblock-payment`, `src/pricing.js`) cobra R$ 4,99 (`PRICES_BRL_CENTS.default`). Isso
significa que hoje o usuário está sendo cobrado um valor diferente do que vê na tela.

Objetivo: preço definido em uma tabela server-side configurável (variável de ambiente do
Lambda), exposta via `GET /pricing`, consumida pelo app com cache local e fallback. O
cliente nunca define o valor cobrado — apenas `product` + `currency`.

## Requisitos

- R1: Lambda tem uma tabela de preços `{ day_pass: { BRL: "1.00", USD: "1.00" } }` vinda de
  variável de ambiente (SAM parameter), não mais hardcoded em `pricing.js`.
- R2: `POST /unlock-charge` passa a receber `product` (`"day_pass"`) + `currency`; resolve o
  amount na tabela server-side; rejeita `product`/`currency` desconhecidos com 400.
- R3: Nova rota `GET /pricing` (mesma API Gateway/Lambda) retorna a tabela completa.
- R4: App tem `PaymentApiClient.getPricing()` com cache em DataStore e fallback para o
  valor embutido (`"1.00"` BRL) se a rede falhar.
- R5: `GooglePayConfig`, `InvoiceReceipt` e `EventsRepository.recordUnlock` usam o mesmo
  valor resolvido (fonte única, sem hardcode duplicado).
- R6: Currency continua fixo em BRL nesta entrega (multimoedas fica preparado, não ativado).
- R7: Nenhum recurso AWS novo com custo fixo criado; qualquer deploy pede autorização.

## Tarefas

- [x] T1: `pricing.js` → tabela vinda de env vars (`PRICE_DAY_PASS_BRL_CENTS`,
      `PRICE_DAY_PASS_USD_CENTS`), corrigido para 100 (R$1,00).
- [x] T2: `unlockCharge.js` aceita `product`, valida contra a tabela.
- [x] T3: Novo handler `pricing.js` (rota) + rota `GET /pricing` no `template.yaml`.
- [x] T4: `samconfig`/template — parâmetros de preço com default 100 cents.
- [ ] T5: Deploy do stack `dollarblock-payment-test` atualizado — **bloqueado**: pedi
      autorização de custo AWS via AskUserQuestion e não houve resposta em 60s; `sam build`
      já validado localmente (build succeeded), falta só o `sam deploy`.
- [x] T6: App: `PaymentApiClient.getPricing()` + cache DataStore (`PricingPreferences`) +
      fallback via `PricingRepository`.
- [x] T7: App: `GooglePayConfig`/`BlockActivity`/`InvoiceReceipt` consomem preço resolvido
      (`dayPassPrice` state, buscado em `onCreate`).
- [ ] T8: Teste ponta a ponta em Stripe test mode — depende do deploy (T5).
- [x] T9: Atualizar `docs/BACKEND_STRIPE.md` + `docs/CHANGELOG.md`.

## Critérios de aceite

- CA1: `POST /unlock-charge` com `product: "day_pass", currency: "BRL"` cobra 100 cents.
- CA2: `POST /unlock-charge` com `product` desconhecido retorna 400.
- CA3: `GET /pricing` retorna `{"day_pass":{"BRL":"1.00","USD":"1.00"}}`.
- CA4: App exibe o preço vindo de `/pricing` (ou fallback offline) de forma consistente na
  tela de bloqueio, no `transactionInfo` do Google Pay e no evento registrado.

## Notas / Decisões

- Backend vive em repo separado `C:\dev\dollarblock-payment` (não neste repo) — mudanças de
  Lambda/SAM são feitas lá; este spec documenta o lado do app e serve de referência cruzada.
- Preço fixado em R$1,00 para ambas moedas nesta entrega (USD só estrutural, não ativado).
- `sam build --template-file infra\template.yaml --config-env test` já roda com sucesso
  (Lambdas `UnlockChargeFunction` + nova `PricingFunction`), incluindo `npm test` do backend
  (7/7 testes passando) como parte do build hook do SAM.
- `sam deploy` não foi executado: a regra global do projeto exige parar e pedir autorização
  explícita antes de qualquer ação AWS com custo (mesmo on-demand) e aguardar resposta —
  não posso assumir autorização implícita. Até lá, o Lambda em produção (test stage)
  continua cobrando R$4,99 sem `product`/`GET /pricing` — o app já está pronto para o novo
  contrato mas cairá no fallback local (`DEFAULT_PRICE = "1.00"`) até o deploy acontecer,
  e a chamada de `/unlock-charge` vai falhar com 400 (`Missing required fields: product`)
  contra o Lambda antigo até o deploy ser feito.
- Para retomar: reautorizar o deploy (ver alerta de custo no histórico da sessão) e rodar
  `sam deploy --config-env test --parameter-overrides "Environment=test StripeSecretKey=<sk_test_ atual, já lida do Lambda em produção>"`
  a partir de `C:\dev\dollarblock-payment`.
