# Spec: Compliance Google Play — política de pagamentos (Stripe/Google Pay)

**Status:** done (código) — pendências operacionais no Play Console listadas em "Implementação"
**Épico:** E16 — adhoc (pré-requisito para submissão à Play Store)
**Data de criação:** 2026-07-07
**Última atualização:** 2026-07-07

---

## Contexto

O DollarBlock cobra R$5 (passe do dia) via Google Pay + Stripe direto, através de um
backend AWS próprio (`POST /unlock-charge`, repo `dollarblock-payment`), sem usar Google
Play Billing e sem estar inscrito em nenhum programa de billing alternativo da Google.

A hipótese levantada foi: como o Google deixou de *exigir* Google Play Billing em todos os
casos (mudança de política 2026, pós-decisão do 9th Circuit contra a Google), o uso do
Stripe já estaria em compliance. Essa hipótese foi verificada e está **incorreta**.

### Por que isso importa

Submeter o app à Play Store no estado atual arrisca rejeição na revisão por violação da
Payments Policy, com possível suspensão da conta de developer em caso de reincidência.

## Conclusão da análise de compliance

**Status atual: NÃO compliant para submissão.**

- O que o app cobra — desbloquear o app bloqueado por 24h — se enquadra textualmente na
  categoria que a Payments Policy do Google define como **exigindo Google Play Billing**:
  > "App functionality or content (such as an ad-free version of an app or new features
  > not available in the free version)"
- A remoção da obrigatoriedade de Google Play Billing em 2026 **não é uma isenção geral**.
  Ela só vale dentro de programas formais com **enrollment obrigatório**:
  - Alternative Billing Program
  - User Choice Billing
  - External Content Links Program

  Todos exigem: inscrição prévia via Play Console, aprovação de telas/choice screen,
  integração com as *external payments APIs* para reportar transações, e o pagamento de
  uma taxa de serviço à Google (10–20%, mesmo fora do Play Billing).
- Nenhum desses programas está configurado no projeto (nada em `docs/`, specs ou
  `CHANGELOG.md` menciona enrollment).
- As exceções que dispensam Google Play Billing **sem** enrollment — apps consumption-only,
  bens/serviços físicos, serviços 1:1 não gravados (ex.: aula de música, consultoria) — não
  se aplicam: o desbloqueio é consumido inteiramente dentro do próprio app.
- A exigência de "transparência" (notificar o Play Console sobre transações via meios
  externos/links na web) é uma obrigação *dentro* desses programas formais — pressupõe
  estar inscrito neles, não é uma via alternativa para evitar o billing obrigatório.

## Alternativas

### Alternativa 1 — Migrar para Google Play Billing Library

Trocar Google Pay + Stripe pelo Play Billing Library nativo.

- **Prós:** caminho mais simples, sem enrollment em programas especiais; é o padrão que a
  Play Store já entende e aprova nativamente; sem necessidade de choice screen ou APIs de
  reporte adicionais.
- **Contras:** Google fica com ~15–30% de comissão sobre cada passe do dia (hoje via
  Stripe a comissão é só a do próprio Stripe, bem menor); exige recriar o fluxo de compra
  como um "produto" gerenciado no Play Console (`BillingClient`, produtos consumíveis) e
  descartar/arquivar boa parte de `feature/blocking/payment/` e o backend
  `dollarblock-payment` (ou mantê-lo só para lógica de negócio pós-confirmação de compra).
- **Impacto:** maior — reescreve o core loop de monetização (E9–E11).

### Alternativa 2 — Inscrever no Alternative/User Choice Billing

Manter Stripe como está, mas formalizar o enrollment no programa da Google.

- **Prós:** mantém a arquitetura atual (Google Pay + Stripe + backend AWS) quase intacta;
  preserva a comissão menor do Stripe (ainda descontada a taxa de serviço da Google).
- **Contras:** mais trabalho de integração (chamar as *external payments APIs* para
  reportar transações, implementar/expor a choice screen exigida pela Google, submeter
  telas para aprovação prévia via Play Console antes de cada mudança); ainda paga taxa de
  serviço de 10–20% à Google mesmo processando fora do Play Billing; enrollment tem prazo e
  requisitos específicos por região (o programa citado nas buscas é majoritariamente
  voltado a EEA/Índia/Coreia do Sul/US — confirmar elegibilidade para o Brasil antes de
  seguir este caminho).
- **Impacto:** médio — trabalho de integração, mas sem reescrever o modelo de cobrança.

### Alternativa 3 — Reestruturar o modelo de cobrança

Mudar o que é vendido para cair numa exceção válida sem precisar de Play Billing nem
enrollment (ex.: consumption-only, serviço não vinculado a "desbloqueio de funcionalidade
dentro do app").

- **Prós:** evita comissão da Google e complexidade de enrollment.
- **Contras:** o "desbloqueio do app bloqueado" é o core loop do produto (E9–E11); descaracterizar
  isso pode exigir repensar a mecânica central do DollarBlock, não é uma mudança superficial.
- **Impacto:** alto e incerto — depende de desenhar um modelo de produto genuinamente
  diferente, não é uma correção de compliance simples.

## Decisão

**Alternativa 1 escolhida** (2026-07-07): migrar a cobrança do passe do dia para a Google
Play Billing Library, **mantendo o código Stripe/Google Pay compilável porém desabilitado**
para eventual reuso (distribuição fora da Play Store ou enrollment futuro em User Choice
Billing).

## Implementação (2026-07-07)

### O que mudou no app

- **`feature/blocking/payment/PaymentConfig.kt`** (novo) — switch de provider:
  `PaymentConfig.PROVIDER = PaymentProvider.PLAY_BILLING`. Trocar para
  `STRIPE_GOOGLE_PAY` reativa o caminho antigo inteiro (nada foi apagado).
- **`feature/blocking/payment/PlayBillingManager.kt`** (novo) — wrapper do
  `BillingClient` (billing-ktx 7.1.1): conecta, carrega o produto consumível
  `day_pass` (INAPP), expõe `ready`/`formattedPrice`, lança o fluxo de compra e
  **consome** cada compra confirmada (um passe por compra, recomprável). Na conexão,
  consome compras PURCHASED órfãs (app morto entre compra e consumo) concedendo o
  desbloqueio ao app-alvo atual.
- **`BlockActivity`** — faz branch por `PaymentConfig.PROVIDER`; o caminho
  Stripe/Google Pay (`checkReadyToPay`, `startGooglePayPayment`, `handlePaymentData`,
  `PaymentApiClient`, `StripeToken`, `GooglePayConfig`) continua no código, apenas não
  é executado. Preço do recibo usa o `formattedPrice` localizado do Play quando
  disponível; extrato registra valor/moeda reais do produto (`priceAmountMicros`).
- **`PaymentMethod.PLAY_BILLING`** novo; Home/Histórico exibem "Google Play".
- **`PricingRepository`** — com Play Billing ativo retorna direto
  `GooglePayConfig.DEFAULT_PRICE` (sem bater no backend Stripe desabilitado).
  ⚠️ `DEFAULT_PRICE` deve ser mantido em sincronia manual com o preço do produto
  `day_pass` no Play Console (usado no cálculo de "total economizado" e como fallback
  de exibição offline).
- Sem verificação server-side de compra por enquanto (aceita `PURCHASED` +
  `consumeAsync` client-side).

### Backend (`dollarblock-payment`)

- **Nenhuma mudança necessária agora.** A stack AWS (unlock-charge/pricing) fica
  associada ao caminho Stripe desabilitado.
- Follow-up opcional (anti-fraude): verificação server-side do `purchaseToken` via
  Google Play Developer API (`purchases.products.get`) implementada como novo endpoint
  neste mesmo repo/stack, se um dia justificar-se.

### Pendências operacionais (Play Console, fora do código)

- [ ] Criar o produto no app no Play Console: **ID `day_pass`**, consumível gerenciado,
      preço **R$ 5,00** (igual a `GooglePayConfig.DEFAULT_PRICE`).
- [ ] Subir um build assinado a uma track (internal testing) — produtos in-app só podem
      ser testados com o app distribuído pelo Play e com **license testers** configurados.
- [ ] Conta de developer com perfil de pagamentos (merchant) ativo para vender produtos.

Ver `docs/PLAYSTORE_PRIVACY_SUBMISSION.md` §6 (atualizado com estes passos).

## Notas / Decisões

- 2026-07-07: análise de compliance realizada.
  Confirmado via busca nas páginas oficiais (Play Console Help / Android Developers) que:
  Google Play Billing é obrigatório para "app functionality or content not available in
  the free version"; a suspensão da obrigatoriedade de Play Billing em 2026 aplica-se
  apenas dentro dos programas com enrollment formal.
- 2026-07-07: Alternativa 1 implementada (ver "Implementação" acima). Billing Library
  7.1.1 atende à exigência "Billing Library 7+" para apps novos desde ago/2025.
