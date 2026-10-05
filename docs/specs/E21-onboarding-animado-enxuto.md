# Spec: E21 — Onboarding animado e enxuto

**Status:** done
**Épico:** E21 (adhoc) — dono: Rafael
**Data de criação:** 2026-10-04
**Última atualização:** 2026-10-05

---

## Contexto

O onboarding anterior (E2 → E18 → E20) funcionava e cumpria a política do Play, mas era
longo e estático: até **9 páginas** (3 de conceito + Uso + Resumo + Acessibilidade +
Sobreposição + Notificações + Controle), todas no molde "ícone num círculo + título +
parágrafo". O tutorial da Acessibilidade (E20) ficava escondido atrás de um link.

Objetivo: **menos páginas, entrada animada, cada permissão com um auxílio visual de como
habilitar**, textos curtos, diretos e honestos, sempre puxando o tema do app (tempo de tela
= dinheiro), e mais confiança — principalmente sobre os dados não saírem do celular.

## Fluxo — de 9 para 5 páginas

| # | Página | Substitui | O que acontece |
|---|---|---|---|
| 1 | **Entrada** | boas-vindas + "você já tentou antes" | Escudo entra com escala/mola; título e texto sobem em sequência; **a conta corre ao vivo** desde que o app abriu ("0:14 · R$ 0,01"), rotulada com a referência de R$ 2.000/mês |
| 2 | **O contrato** | contrato + "você no controle" | Cláusulas saem uma a uma, como recibo da maquininha; a 4ª é a saída livre. O botão **"Assinar o contrato"** traça a assinatura |
| 3 | **A medição** | Acesso de uso + Resumo rápido | Cartão "Sigilo bancário: nada sai do seu celular" + passo a passo animado. Concedida, a mesma página vira o resumo: **"Sua última semana custou R$ X"**, comparação ("= 4 passagens de ônibus"), donut que se desenha e valor que sobe contando |
| 4 | **A tranca** | Acessibilidade | Cartão de destaque "Não lê sua tela, o que você digita nem suas senhas" + passo a passo (lista → chave → Permitir). A declaração do Play continua antes das Configurações |
| 5 | **Últimos ajustes** | Sobreposição + Notificações | Dois cartões com "Ativar" e carimbo ao conceder; o da Sobreposição traz o passo a passo. **"Abrir a conta"** → carimbo "CONTA ABERTA" → Home |

Transversal:
- Permissão concedida ganha o carimbo **"CONCEDIDA"** (entra grande e "bate", com haptic),
  na estética do BLOQUEADO do recibo.
- Barra de progresso no topo no lugar das bolinhas.
- "Remover animações" do sistema respeitado: estados finais, sem loop
  (`rememberAnimationsEnabled`).
- Regras de permissão inalteradas: obrigatória continua obrigatória, "Agora não" pede
  "Seguir sem X?", e o botão final nunca trava (lista o que ficou pendente).
- Swipe só na Entrada — o contrato pede assinatura e as permissões, os botões.

## Decisões (2026-10-05, Rafael)

- D1: assinatura **no contrato**; o fim vira o carimbo "CONTA ABERTA".
- D2: Sobreposição e Notificações **na mesma página**.
- D3: auxílio visual **desenhado em Compose** (`PermissionHowTo`) — leve, segue o tema e
  vale para qualquer marca de celular. Os prints do E20 (`a11y_tutorial_*.webp`,
  `AccessibilityTutorial.kt`, `scripts/a11y-tutorial/`) foram removidos.
- D4: valor em dinheiro na **referência de R$ 2.000/mês**, a mesma da Home, sempre rotulada.
- Pedido do Rafael na validação: mais destaque para a privacidade na medição e na tranca →
  `TrustCard` (cadeado + título + três garantias), com o vocabulário do "Sigilo bancário"
  (E19). **Tudo o que o cartão afirma precisa continuar verdade** — se um dia entrar
  servidor, analytics ou backup, o cartão muda junto.

## Notas de implementação

- `OnboardingFlow.kt` (puro, testado): `onboardingPages`, `permissionsOn`,
  `pendingRequiredOn`, `missingRequiredPermissions`, `screenTimeCost`.
- `OnboardingVisuals.kt`: `Reveal`, `Stamp`/`GrantedStamp`, `OnboardingProgress`,
  `PermissionHowTo`.
- `OnboardingPages.kt`: uma função por página + `TrustCard`.
- Resumo da semana só com apps que abrem pela gaveta (sem launcher, Configurações,
  "Controlador de permissões"…); o custo usa a soma dos 5 primeiros.
- Valores em reais com espaço inseparável ("R$ 20,59" não quebra a linha).

## Tarefas

- [x] T1: Decisões fechadas e textos revisados.
- [x] T2: `OnboardingFlow` com as 5 páginas + `OnboardingFlowTest`.
- [x] T3: Entrada com animação e conta ao vivo.
- [x] T4: Contrato impresso cláusula a cláusula, assinatura no rodapé.
- [x] T5: Medição → resumo da semana na mesma página.
- [x] T6: Passo a passo da Acessibilidade na página.
- [x] T7: Últimos ajustes (dois cartões) + passo a passo da Sobreposição.
- [x] T8: Carimbos, barra de progresso, "remover animações".
- [x] T9: Strings EN + PT; órfãs removidas.
- [x] T10: `scripts/smoke-test-emulator.ps1` ajustado ao fluxo novo.
- [x] T11: Validado no emulador (pt-BR e EN, permissões concedidas e pendentes).
- [ ] T11b: Validar num celular real (Samsung/Xiaomi) — pendente.
- [x] T12: CHANGELOG + status `done`.

## Critérios de aceite

- CA1: 5 páginas (Notificações some do cartão abaixo do Android 13). ✅
- CA2: Toda permissão tem passo a passo visível sem tocar em link. ✅
- CA3: Declaração da Acessibilidade antes das Configurações, com ação afirmativa. ✅
- CA4: "Remover animações" → estados finais, sem loop. ✅ (por código; não testado no aparelho)
- CA5: testes unitários e `smoke-test-emulator.ps1` verdes. ✅
