# DollarBlock — Roadmap de Épicos (MVP)

> "Your Number One App for Time Management"

Plano de épicos sequenciado para construir a primeira versão do DollarBlock.
Cada épico tem **objetivo**, **entregáveis**, **critérios de aceite** e **dependências**.
Decisões técnicas em [`ARCHITECTURE.md`](./ARCHITECTURE.md).

**Ordem de execução:** E0 → E0.5 → E1 → E2 → E3 → E4 → E5 → E6 → E7 → E8 → E9, com
E10 (qualidade) atravessando todos. Cada épico deve deixar o projeto **compilável**.

---

## E0 — Fundação do projeto & Design System
**Objetivo:** projeto Android moderno compilável, com identidade visual DollarBlock.

**Entregáveis**
- Setup Gradle (Kotlin DSL + version catalog), plugins (Compose, Hilt, KSP, Room).
- `DollarBlockApp` (`@HiltAndroidApp`) + `MainActivity`.
- Design System: paleta (verde dólar / verde escuro / sucesso vibrante / alerta amarelo
  / penalidade vermelho / neutros), tipografia, `DollarBlockTheme`, componentes base
  (card de métrica, botão primário, botão de penalidade).
- Tom de voz central: arquivo de strings com mensagens ("Meta cumprida.", "Limite atingido.",
  "Desbloqueio necessário.", "Você economizou X minutos hoje.").
- Navegação: `NavHost` + Bottom Navigation (Home / Apps / Statistics / Profile) com telas placeholder.

**Aceite:** app instala, abre, navega entre as 4 abas com tema aplicado. ✅ **Concluído.**

---

## E0.5 — Shell navegável (placeholders ricos)
**Objetivo:** dar "vida" à casca do app antes dos dados reais — permite navegar e validar
a visão de UX/identidade de cada aba com dados de exemplo.

**Entregáveis**
- `PreviewBanner` reutilizável ("Pré-visualização — dados de exemplo").
- **Apps**: lista de apps com avatar, uso atual vs limite (barra de progresso) e toggle de
  monitoramento funcional (estado local).
- **Statistics**: seletor de período (Diário/Semanal/Mensal) + gráfico de barras simples
  (Compose Canvas) + cartões de resumo, todos com dados mock por período.
- **Profile**: cabeçalho do usuário, faixa de estatísticas, seção de Permissões (com status)
  e seção de Preferências/Histórico.

**Aceite:** as 4 abas exibem conteúdo on-brand navegável; toggles e seletor respondem.
Dados são mock e serão substituídos nos épicos E3 (Apps), E7 (Statistics) e E8 (Profile).
✅ **Concluído** — verificado por screenshot nas 4 telas no emulador.

---

## E1 — Camada de dados & domínio (persistência)
**Objetivo:** base de dados e contratos de domínio prontos.

**Entregáveis**
- Room: entidades `MonitoredApp`, `DailyUsage`, `BlockEvent`, `UnlockEvent`, `UserStats`;
  DAOs; `DollarBlockDatabase`.
- DataStore: flags de app (onboarding concluído, prefs).
- Modelos de domínio + mappers entity↔domain.
- Interfaces de repositório (em `domain`) + implementações (em `data`) + módulos Hilt.
- Casos de uso base (CRUD de apps monitorados, leitura de uso/eventos).

**Aceite:** testes de DAO passam; repositórios injetáveis expõem `Flow`. **Depende de E0.**

---

## E2 — Onboarding & permissões
**Objetivo:** primeira execução explica o conceito e solicita permissões com clareza.

**Entregáveis**
- Fluxo de onboarding (conceito DollarBlock, benefícios, "como funciona a penalidade").
- Solicitação guiada: Usage Access, Accessibility, Overlay, Notifications — cada uma com
  o **porquê** e atalho para a tela do sistema.
- Gate de roteamento: 1ª execução → onboarding (flag no DataStore).

**Aceite:** usuário novo passa pelo onboarding, concede permissões, chega à Home;
reabrir o app pula o onboarding. **Depende de E1.**

---

## E3 — Apps: listagem & configuração de limites
**Objetivo:** usuário escolhe apps e define limites diários.

**Entregáveis**
- Data source `PackageManager`: apps instalados (ícone, nome, package).
- Tela Apps: lista, toggle de monitoramento, definição de limite diário, uso atual vs limite.
- Persistência em `MonitoredApp`.

**Aceite:** ativar/desativar e definir limite persiste e reflete na lista. **Depende de E1.**

---

## E4 — Monitoramento (núcleo de medição de uso)
**Objetivo:** camada isolada que mede tempo de uso e atualiza o banco periodicamente.

**Entregáveis**
- Data source `UsageStatsManager` (leitura de uso por app/dia).
- Cálculo de tempo + atualização periódica (WorkManager / Foreground Service).
- Escrita em `DailyUsage`; reset diário por `epochDay` local.

**Aceite:** uso real dos apps monitorados aparece atualizado em `DailyUsage`.
**Depende de E1, E3** (precisa saber o que monitorar). Permissões de E2.

---

## E5 — Bloqueio (Blocking Engine)
**Objetivo:** detectar limite atingido e bloquear o app com tela própria.

**Entregue antecipadamente (fatia mínima — validada no emulador):**
- `DollarBlockAccessibilityService` detecta o app em foreground e abre a `BlockActivity`
  (tela de bloqueio DollarBlock) por cima.
- Controle na Home: **seletor de apps instalados** (`InstalledAppsProvider` via PackageManager)
  + **botão habilitar/desabilitar** bloqueio por app.
- Conjunto de bloqueados persistido em DataStore (`BlockPreferences`), compartilhado UI↔serviço.
- Detecção de status do serviço de Acessibilidade na Home + atalho para Configurações.

**Restante do E5 (depende de E1/E4):**
- Disparo por **uso ≥ limite** (em vez de bloqueio binário).
- Registrar `BlockEvent`; **Unlock** com `UnlockEvent` (`penaltyAmount` simulado) e janela
  `unlockUntil`. Migrar bloqueado-set para Room.

**Aceite (fatia atual):** app selecionado e bloqueado pela Home → abre bloqueado mostra a
tela do DollarBlock; desabilitar libera o acesso. ✅ **Validado.**

---

## E6 — Home (dashboard)
**Objetivo:** painel diário motivador.

**Entregáveis**
- Daily Score, Time Saved (hoje), Active Limits (qtd monitorada), Recent Events (últimos bloqueios).
- ViewModel agregando repositórios via `Flow`; estados loading/vazio/erro.

**Aceite:** Home reflete dados reais de uso, limites e eventos. **Depende de E1, E4, E5.**

---

## E7 — Statistics
**Objetivo:** visão de uso ao longo do tempo.

**Entregáveis**
- Agregações diário / semanal / mensal sobre `DailyUsage`.
- Gráficos simples (Compose Canvas ou lib leve).

**Aceite:** gráficos renderizam com dados reais nos três períodos. **Depende de E1, E4.**

---

## E8 — Profile & Histórico
**Objetivo:** ajustes, gestão de permissões e linha do tempo de eventos.

**Entregáveis**
- Profile: status/atalhos de permissões, preferências, "sobre".
- Histórico: bloqueios, desbloqueios e metas cumpridas.

**Aceite:** histórico lista eventos reais; Profile permite revisar permissões.
**Depende de E1, E5.**

---

## E9 — Hooks para cobrança futura
**Objetivo:** preparar terreno para cobrança/recompensas/depósitos sem implementar pagamento real.

**Entregáveis**
- Abstração `PaymentGateway` (impl no-op) no domínio.
- Modelos stub de saldo/depósito/transação; `UnlockEvent` já carrega valor de penalidade.

**Aceite:** desbloqueio passa por uma `PaymentGateway` simulada, sem acoplar UI a pagamento real.
**Depende de E5.**

---

## E10 — Qualidade, QA & build (transversal)
**Objetivo:** robustez e polimento contínuos.

**Entregáveis**
- Estados loading/erro/vazio padronizados; acessibilidade; revisão de tom de voz.
- Testes: unit (use cases), DAO (Room in-memory); smoke manual do fluxo de bloqueio.
- Config de build release.

**Aceite:** suíte de testes verde; fluxo principal validado manualmente.

---

## Resumo de dependências

```
E0 ─► E0.5 ─► E1 ─► E2
                 ├─► E3 ─► E4 ─► E5 ─► E6
                 │              │     ├─► E8
                 │              └────►E9
                 └─► E7
E10 ── transversal a todos
```

E0.5 é uma camada de UI placeholder: E3/E7/E8 substituem seus mocks por dados reais.

**Próximo passo sugerido:** após E0.5 (shell navegável), iniciar **E1** (dados & domínio).
