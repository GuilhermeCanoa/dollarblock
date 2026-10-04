# Spec: Onboarding com permissões progressivas (uma por página)

**Status:** done
**Épico:** E18 (adhoc — UX de onboarding)
**Data de criação:** 2026-10-04
**Última atualização:** 2026-10-04

---

## Contexto

O onboarding pedia as 4 permissões numa única página ("A burocracia"). Ver quatro pedidos
sensíveis de uma vez assusta o usuário e derruba a conversão logo na primeira abertura.
Além disso, o botão final ficava desabilitado sem Acesso de uso — o usuário era refém.

## Estratégia

Pedir **uma permissão por página**, cada uma no momento em que o usuário entende para que
ela serve, alternando a "papelada" com uma recompensa:

| # | Página | Tipo | Por que aqui |
|---|---|---|---|
| 1–3 | Conceito (marca, confronto, contrato) | — | Motivação antes de qualquer pedido |
| 4 | **Acesso de uso** | obrigatória | "Primeiro, o taxímetro" — base de tudo |
| 5 | Resumo rápido (top apps) | recompensa | Prova imediata do valor da permissão que acabou de dar |
| 6 | **Acessibilidade** | obrigatória | "Agora, a tranca" — com a declaração em destaque (Play) |
| 7 | **Notificações** | opcional | Pedido leve (diálogo do sistema) entre duas idas às Configurações; só no Android 13+ |
| 8 | **Sobreposição** | obrigatória (desde 2026-10-04) | Confiabilidade da tela de bloqueio em alguns aparelhos |
| 9 | Você no controle + "Topo o desafio" | — | Fecha o contrato |

Cada página de permissão mostra: "Papelada N de M", selo **obrigatória/opcional**, o porquê,
e um bloco **"Sem ela"** com o que o app perde.

## Requisitos

- R1: Uma permissão por página, com porquê, selo obrigatória/opcional e consequência de recusar.
- R2: Ninguém fica preso: "Agora não" sempre avança. Numa obrigatória, aparece antes um aviso
  ("Seguir sem X?") com a perda de funcionalidade e as opções "Conceder agora" / "Seguir assim mesmo".
- R3: Swipe desabilitado *a partir de* uma página de permissão (para não pular o aviso);
  voltar pelo botão Back do sistema funciona em qualquer página.
- R4: O botão final nunca fica desabilitado; se faltar obrigatória, um aviso lista o que falta e
  aponta o Perfil.
- R5: O aviso diário pós-onboarding ("taxímetro às cegas") considera só permissões obrigatórias —
  recusar uma opcional não gera cobrança diária.
- R6: Ordem/obrigatoriedade em funções puras com teste JVM.

## Tarefas

- [x] T1: `AppPermission.required` (Usage + Acessibilidade obrigatórias).
- [x] T2: `OnboardingFlow.kt` — lista de páginas + `missingRequiredPermissions` (puro) + testes.
- [x] T3: `OnboardingScreen` — página por permissão, diálogo de pular obrigatória, finish sempre habilitado.
- [x] T4: Strings en + pt; remoção das strings da página única.
- [x] T5: `MainViewModel.checkPermissionNag` só com obrigatórias.
- [x] T6: Smoke test atravessa as páginas novas.

## Critérios de aceite

- CA1: `:app:testDebugUnitTest` verde, incluindo `OnboardingFlowTest`.
- CA2: No emulador, com permissões negadas: cada página aparece sozinha; "Agora não" numa
  obrigatória abre o aviso; dá para concluir o onboarding sem nada concedido.
- CA3: `scripts/smoke-test-emulator.ps1` passa.

## Notas / Decisões

- ~~Sobreposição é opcional~~ — **revisto em 2026-10-04:** virou obrigatória e foi para logo
  depois da Acessibilidade (ordem 6 → Sobreposição → Notificações). No Android puro a
  acessibilidade já é isenta das restrições de abrir activity em segundo plano, mas em vários
  fabricantes (MIUI, ColorOS…) sem a sobreposição a tela de bloqueio é segurada — e a tranca é
  o produto.
- Não há avanço automático ao voltar das Configurações com a permissão concedida: a página
  mostra "Concedida" e o botão vira "Continuar" — previsível e fácil de automatizar.
