# E17 — Saída de cortesia quando o pagamento falha

**Status:** done
**Épico:** adhoc (correção de UX crítica no bloqueio)
**Data:** 2026-09-06

## Problema

Na tela de bloqueio (`BlockActivity`), qualquer falha do provedor de pagamento — Play
Billing sem conexão, produto `day_pass` não carregado, erro no fluxo de compra, Google Pay
indisponível, falha na cobrança Stripe — resultava apenas em um toast de erro. O usuário
ficava **sem nenhuma forma de acessar o app-alvo até a meia-noite**, mesmo tendo tentado
pagar. O erro é nosso (ou da loja), mas quem pagava a conta era o usuário.

## Regra

Quando a cobrança falha **por erro** (nunca por desistência do usuário), a tela de bloqueio
passa a oferecer uma **saída de cortesia**: um botão que libera o app-alvo até a meia-noite
sem cobrar nada, no tom da marca ("a maquininha deu pau… você deu sorte").

Distinção importante:

| Situação | Comportamento |
|---|---|
| Usuário cancela a compra (`USER_CANCELED`) | Bloqueio normal — sem cortesia (senão vira bypass trivial) |
| Erro do Billing / cobrança / loja indisponível | Estado de falha + botão de cortesia |
| Play não fica `ready` em 8 s | Estado de falha + botão de cortesia |
| Google Pay indisponível no device (caminho Stripe) | Estado de falha + botão de cortesia |

O desbloqueio de cortesia usa o mesmo `BlockPreferences.grantUnlockForToday` do caminho
pago (liberado até a meia-noite local) e é registrado no extrato via
`EventsRepository.recordUnlock` com `amount = "0.00"` e `method = PaymentMethod.COURTESY`,
para o histórico deixar explícito que nada foi cobrado.

Quando a loja está disponível, o estado de falha também oferece "Tentar pagar de novo";
uma nova tentativa limpa o estado de falha antes de iniciar o fluxo.

## Tarefas

- [x] `PaymentMethod.COURTESY` no `domain/model/RecentEvent.kt`
- [x] `paymentFailed` StateFlow + `onPaymentError()` / `onCourtesyUnlock()` na `BlockActivity`
- [x] Todos os caminhos de erro (Billing, Google Pay sheet, `loadPaymentData`, token,
      cobrança Stripe, `launchBillingFlow`) roteados para `onPaymentError()`
- [x] Timeout de 8 s esperando o Play ficar `ready`
- [x] `isReadyToPay == false` liga o estado de falha no caminho Stripe
- [x] `PaymentFailedNotice` composable + strings en/pt
- [x] Rótulo "Cortesia (falha no pagamento)" no Home e no HistoryScreen
- [x] Build + testes unitários

## Fora de escopo

- Limite de quantas cortesias um usuário pode receber (hoje é uma por app/dia por
  construção do grant, mas nada impede repetir em dias seguidos se a loja seguir quebrada).
