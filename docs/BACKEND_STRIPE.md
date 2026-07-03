# DollarBlock — Backend de cobrança real (Stripe)

> **Status:** ✅ **Implementado e funcionando em modo TESTE**, com **preço dinâmico**
> (spec: `docs/specs/E11-preco-dinamico.md`). O app cobra de fato via Stripe (conta teste)
> através de um backend AWS serverless (repo separado `dollarblock-payment`, `C:\dev\dollarblock-payment`):
> `POST https://duj02ll1zl.execute-api.us-east-1.amazonaws.com/test/unlock-charge` e
> `GET https://duj02ll1zl.execute-api.us-east-1.amazonaws.com/test/pricing`.
> O preço (`day_pass`, hoje R$1,00) vive em variável de ambiente da Lambda
> (`PRICE_DAY_PASS_BRL_CENTS`), não mais hardcoded — o cliente só envia `product`+`currency`,
> nunca o valor. O fluxo de produção (`pk_live_`/`sk_live_`, `ENVIRONMENT_PRODUCTION`) ainda
> não foi ativado.
>
> No app, ver `feature/blocking/payment/`: `GooglePayConfig` (gateway `stripe` + `pk_test_`),
> `PaymentApiClient` (chamada aos endpoints `/unlock-charge` e `/pricing`), `StripeToken`
> (extração do `id` do token) e `BlockActivity.handlePaymentData` (orquestra a cobrança e só
> desbloqueia em `succeeded`). `data/repository/PricingRepository` resolve o preço a exibir
> com cache em `PricingPreferences` (DataStore) e fallback para `GooglePayConfig.DEFAULT_PRICE`.
>
> ⚠️ Deploy do stack `dollarblock-payment-test` com o código de preço dinâmico está pendente
> de autorização de custo AWS (regra global do projeto) — ver "Notas / Decisões" do spec.
>
> As seções abaixo descrevem a especificação/arquitetura que guiou a implementação inicial e o
> que falta para produção.

Objetivo: transformar o pagamento de teste (Google Pay `ENVIRONMENT_TEST`) em **cobrança
real** via **Stripe**, sem expor a chave secreta no app.

---

## 1. Por que precisa de backend

O app **nunca** deve conter a chave secreta do PSP (`sk_...`). O fluxo seguro é:

```
App (Google Pay, gateway=stripe, pk_test/pk_live)
   └─ usuário escolhe cartão → recebe TOKEN do Google Pay (contém um token Stripe)
        └─ App envia { packageName, amount, currency, paymentToken } ao BACKEND
             └─ Backend chama Stripe com sk_... → cria/confirma PaymentIntent
                  └─ retorna { status: succeeded } → App concede o desbloqueio
```

---

## 2. Mudanças no app Android (quando o backend existir)

Em `feature/blocking/payment/GooglePayConfig.kt`, trocar o gateway de teste por Stripe:

```json
"tokenizationSpecification": {
  "type": "PAYMENT_GATEWAY",
  "parameters": {
    "gateway": "stripe",
    "stripe:version": "2020-08-27",
    "stripe:publishableKey": "pk_test_xxx"   // pk_live_ em produção
  }
}
```

Em `BlockActivity.onPaymentSuccess(...)`:
- Hoje: concede o desbloqueio direto (mock).
- Depois: extrair o token de `PaymentData.getFromIntent(intent).toJson()` →
  `paymentMethodData.tokenizationData.token` → enviar ao backend → só conceder
  o desbloqueio se o backend retornar sucesso.
- Manter o **fallback de simulação** apenas em build de debug.

Ambiente: `WalletConstants.ENVIRONMENT_TEST` (Stripe test) → `ENVIRONMENT_PRODUCTION` (live).

---

## 3. Arquitetura do backend (AWS serverless, custo $0 sem uso)

> ⚠️ Antes de criar qualquer recurso AWS, seguir as regras do projeto: **parar e pedir
> autorização de custo** (mesmo on-demand). Listar recursos antes do deploy.

- **API Gateway (HTTP API)** — endpoint público HTTPS.
- **AWS Lambda** (Node.js ou Python) — lógica de cobrança.
- **Secrets Manager** — guarda `STRIPE_SECRET_KEY`.
- **DynamoDB (on-demand)** *(opcional)* — ledger de transações / idempotência.
- (Sem NAT Gateway, sem recursos com custo fixo.)

Custo: todos on-demand. Secrets Manager tem custo por segredo ativo (~US$0,40/mês) —
**confirmar antes de criar** ou usar variável de ambiente da Lambda (menos seguro) no início.

---

## 4. Endpoint

`POST /unlock-charge`

Request:
```json
{
  "packageName": "com.google.android.youtube",
  "product": "day_pass",
  "currency": "BRL",
  "paymentToken": "<token do Google Pay / Stripe>",
  "idempotencyKey": "<uuid gerado no app>"
}
```

Response (sucesso):
```json
{ "status": "succeeded", "paymentIntentId": "pi_..." }
```

Regras no backend:
- **Validar o valor no servidor** — o app nunca envia `amount`; envia `product`+`currency`
  e o backend resolve o preço em `pricing.js` (via env vars da Lambda).
- Usar **idempotencyKey** para não cobrar duas vezes.
- Registrar a transação (DynamoDB) com packageName, product, valor, status, timestamp.

`GET /pricing`

Response:
```json
{ "day_pass": { "BRL": "1.00", "USD": "1.00" } }
```

Usado pelo app para exibir o preço sem hardcode (`PricingRepository`, cache em DataStore).

---

## 5. Stripe — fluxo na Lambda

O token do Google Pay (gateway stripe) já é um token Stripe (`tok_...`).
Criar e confirmar um PaymentIntent:

```js
// Node.js (stripe SDK)
const pi = await stripe.paymentIntents.create({
  amount: 499,                 // em centavos, calculado no servidor
  currency: 'brl',
  confirm: true,
  payment_method_data: {
    type: 'card',
    card: { token: paymentToken },
  },
}, { idempotencyKey });
return pi.status; // 'succeeded'
```

---

## 6. Credenciais necessárias (o que obter)

| Onde | Credencial | Como obter |
|---|---|---|
| App (cliente) | `pk_test_...` / `pk_live_...` | Stripe Dashboard → Developers → API keys |
| Backend (secreto) | `sk_test_...` / `sk_live_...` | idem — **só no backend/Secrets Manager** |
| Google (produção) | Merchant ID + acesso de produção | pay.google.com/business/console |
| Stripe (opcional) | Webhook signing secret | para confirmar eventos assíncronos |

---

## 7. Passo a passo (resumo para a próxima sessão)

1. Criar conta Stripe (modo teste) → copiar `pk_test_` e `sk_test_`.
2. No app: trocar gateway `example` → `stripe` com `pk_test_` (seção 2).
3. Implementar a Lambda `unlock-charge` (seção 5) + API Gateway HTTP API.
4. Guardar `sk_test_` no Secrets Manager (pedir autorização de custo).
5. App: enviar token ao endpoint; só desbloquear no `succeeded`.
6. Testar com cartões de teste do Stripe:
   - `4242 4242 4242 4242` (Visa, sucesso), validade futura, CVC qualquer.
   - cartões de falha: ver https://stripe.com/docs/testing
7. Produção: registrar no Google Pay & Wallet Console, trocar para `pk_live_`/`sk_live_`
   e `ENVIRONMENT_PRODUCTION`; revisar PCI/compliance.

---

## 8. Prompt sugerido para o próximo chat

> "Implemente o backend de cobrança do DollarBlock conforme `docs/BACKEND_STRIPE.md`:
> uma AWS Lambda + API Gateway HTTP API com endpoint `POST /unlock-charge` que cobra via
> Stripe (chave no Secrets Manager), com validação de valor server-side e idempotência.
> Depois ajuste o app (GooglePayConfig + BlockActivity) para gateway `stripe` e para só
> desbloquear após sucesso do backend. Peça autorização de custo AWS antes de criar recursos. Mantenha
> a opção de funcionalidade mockada para fins de teste apenas"

---

## 9. Segurança (checklist)

- [ ] Chave secreta só no backend (Secrets Manager / env), nunca no APK.
- [ ] Valor validado no servidor (tabela de preços), não vindo do cliente.
- [ ] Idempotência por requisição (evitar cobrança dupla).
- [ ] HTTPS only; autenticação do app no endpoint (ex.: token de app / App Check).
- [ ] Logs sem dados de cartão (o app nunca vê o PAN — Google Pay tokeniza).
