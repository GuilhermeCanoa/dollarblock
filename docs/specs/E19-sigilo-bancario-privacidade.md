# Spec: Sigilo bancário — privacidade como argumento de confiança

**Status:** done
**Épico:** E19 (adhoc — confiança / privacidade)
**Data de criação:** 2026-10-04
**Última atualização:** 2026-10-04

---

## Contexto

O DollarBlock pede permissões que assustam (Acessibilidade, Acesso de uso) e cobra
dinheiro. A desconfiança é natural — e o app tem a melhor resposta possível: não tem
servidor, conta, login nem rastreador; tudo fica no aparelho. Essa vantagem estava
escondida no diálogo de Acessibilidade e na política de privacidade.

## Estratégia

**Uma ideia, poucos lugares, no momento da dúvida.** A ideia vem da voz da casa (gerente
de banco do seu tempo): **"Sigilo bancário"**. Ela aparece só onde a desconfiança nasce,
nunca em Home/Apps/Extrato:

| Momento da dúvida | Onde | Forma |
|---|---|---|
| "Por que esse app quer tudo isso?" | Perfil, acima das Permissões | Cartão de uma linha → diálogo com o extrato |
| "Pra onde vai meu cartão?" | Tela de bloqueio, sob o botão de pagar | Uma linha discreta, só com Play Billing |
| "O que a Acessibilidade lê?" | Disclosure de Acessibilidade (já existia) | Mantido como está |

Regra: a mensagem tem que ser **verificável e literalmente verdadeira**. Por isso o backup
em nuvem do Google foi desligado — sem isso "não sai do seu celular" seria falso.

## Requisitos

- R1: Cartão "Sigilo bancário" no Perfil abre diálogo com: o que não existe (servidor,
  conta, login, dados pessoais, rastreador), o que fica no aparelho, quem processa o
  pagamento e como apagar tudo.
- R2: Linha de pagamento na tela de bloqueio somente com `PaymentProvider.PLAY_BILLING`
  e só quando o botão de pagar aparece.
- R3: Nenhum dado do app vai para o backup em nuvem (Android 11- e 12+). Transferência
  direta entre aparelhos (12+) continua.
- R4: Strings en + pt na voz da casa (sem emoji, sem exclamação).

## Tarefas

- [x] T1: Strings `privacy_*` (en/pt).
- [x] T2: `PrivacyCard` + `PrivacyDialog` em `ProfileScreen`.
- [x] T3: Linha `privacy_payment_note` em `BlockActivity`.
- [x] T4: `data_extraction_rules.xml` / `backup_rules.xml` excluem todos os domínios da nuvem.
- [x] T5: Política de privacidade (`PLAYSTORE_PRIVACY_SUBMISSION.md`) atualizada sobre backup.

## Pendências / próximos passos

- Onboarding (primeira impressão): depois que o E18 entrar, adicionar uma linha
  `privacy_row_desc` na página de **Acesso de uso** — a primeira permissão pedida. Não foi
  feito aqui para não conflitar com o E18 em andamento.
- Ficha da Play Store: abrir a descrição curta/longa com "Sem conta, sem servidor: seus
  dados não saem do seu celular" — é o primeiro contato e o selo "Nenhum dado coletado"
  do Data Safety reforça.
- Prova verificável mais forte: remover a permissão `INTERNET` do manifesto se o caminho
  Stripe (E9) for abandonado de vez — aí "não sai do celular" vira algo que qualquer um
  confere na Play Store. Antes, confirmar que Play Billing funciona sem ela.

## Critérios de aceite

- CA1: `:app:assembleDebug` e `:app:testDebugUnitTest` verdes.
- CA2: No emulador, Perfil mostra o cartão acima das Permissões e o diálogo abre/fecha.
