# DollarBlock — Pagamento (Google Pay) — Setup & Tutorial

Como funciona o pagamento que **desbloqueia** um app, o que já está pronto para teste
local e o que é preciso configurar para cobrança real.

---

## 1. Esclarecimento: Google Pay ≠ "Google Wallet passes"

- **Google Wallet / Passes API**: cartões de fidelidade, ingressos, embarque. **Não cobra** o usuário.
- **Google Pay API** (`PaymentsClient`): abre a folha de pagamento do Google, o usuário
  **escolhe um cartão da conta Google** e o app recebe um **token** que um PSP (gateway de
  pagamento) usa para cobrar. **É isto que o DollarBlock usa.**

---

## 2. O que JÁ está pronto (teste local — sem conta, sem chaves)

Implementação em `feature/blocking/payment/GooglePayConfig.kt` e `feature/blocking/BlockActivity.kt`:

- Ambiente **`ENVIRONMENT_TEST`**.
- Gateway de teste do Google: **`"example"`** → devolve um token **fake**, **não cobra** e
  **não exige conta de PSP nem API keys**.
- Botão **"Pagar com Google Pay"** (quando disponível) + botão **"Simular pagamento (teste)"**
  (fallback que funciona em qualquer aparelho/emulador).
- Pagamento bem-sucedido → libera o app por `UNLOCK_WINDOW_MINUTES` (15 min) e reabre o app.

### Requisitos para ver a folha real do Google Pay no aparelho
- Google Play Services instalado (imagem "Google Play"/aparelho real).
- Conta Google logada (idealmente com algum cartão). Em `ENVIRONMENT_TEST` o cartão **não é cobrado**.
- Sem conta/cartão, use **"Simular pagamento (teste)"** — o fluxo de desbloqueio funciona igual.

> Nada a configurar para o teste local. Pode rodar direto.

---

## 3. Ir para PRODUÇÃO (cobrança real)

Resumo do que muda. Há 3 frentes: **PSP**, **Google Pay & Wallet Console** e **backend**.

### 3.1 Escolher um PSP (gateway de pagamento)
Ex.: Stripe, Adyen, Braintree, Mercado Pago, PagSeguro/PagBank, Cielo.
Crie a conta e obtenha:
- `gateway` (ex.: `stripe`, `adyen`, `braintree`…).
- `gatewayMerchantId` (id do comerciante no PSP).
- Chaves: **publicável** (vai no app, ex. Stripe `pk_test_…`/`pk_live_…`) e **secreta**
  (fica **só no backend**, ex. Stripe `sk_test_…`/`sk_live_…`).

Exemplo de `tokenizationSpecification` para Stripe (substitui o `"example"`):
```json
{
  "type": "PAYMENT_GATEWAY",
  "parameters": {
    "gateway": "stripe",
    "stripe:version": "2020-08-27",
    "stripe:publishableKey": "pk_test_xxx"
  }
}
```

### 3.2 Google Pay & Wallet Console
1. Acesse https://pay.google.com/business/console e crie o perfil de negócio.
2. Obtenha o **Merchant ID** (Google).
3. Solicite **acesso de produção** ("Request production access"): enviar nome do app,
   screenshots da tela de pagamento e da integração, conforme o checklist do console.
4. Preencha `merchantInfo.merchantId` no request (em `GooglePayConfig.paymentDataRequest()`).

### 3.3 Código
Em `GooglePayConfig.kt`:
- `ENVIRONMENT_TEST` → `WalletConstants.ENVIRONMENT_PRODUCTION`.
- Trocar o bloco `gateway "example"` pelo do seu PSP (ver 3.1).
- Adicionar `merchantInfo.merchantId` (Merchant ID do Google).
- Ajustar `PRICE`/`CURRENCY_CODE` conforme a regra de cobrança.

### 3.4 Backend (obrigatório para cobrar de verdade)
- O app envia o **token** (`PaymentData.toJson()`) ao **seu backend**.
- O backend chama o PSP com a **chave secreta** para efetivar a cobrança.
- **Nunca** coloque a chave secreta no app.
- O backend confirma o sucesso → o app concede o desbloqueio.

Sugestão de backend $0-on-demand (alinhado às regras do projeto): **AWS Lambda + API Gateway (HTTP API)**
+ **Secrets Manager** para a chave do PSP. (Pedir autorização de custo antes de criar recursos AWS.)

---

## 4. Credenciais por etapa

| Etapa | App (cliente) | Backend | Google |
|---|---|---|---|
| Teste local (atual) | nada (gateway `example`) | — | — |
| Teste com PSP real | chave publicável `pk_test_` | chave secreta `sk_test_` | — |
| Produção | chave publicável `pk_live_` | chave secreta `sk_live_` | Merchant ID + acesso de produção |

---

## 5. Onde mexer no código

- `feature/blocking/payment/GooglePayConfig.kt` — ambiente, gateway, preço, merchantInfo.
- `feature/blocking/BlockActivity.kt` — fluxo `isReadyToPay` / `loadPaymentData` / resultado.
- Para produção: trocar o botão custom por **`com.google.pay.button:compose-pay-button`**
  (botão oficial, exigido pelas diretrizes de marca do Google Pay).
- Registrar `UnlockEvent` (valor da penalidade) quando o E1 (Room) estiver pronto.
