# Spec: E8 — Profile & Histórico

**Status:** done
**Épico:** E8
**Data de criação:** 2026-06-23
**Última atualização:** 2026-11-26

---

## Contexto

A aba Profile está em mock (`feature/profile/ProfileScreen.kt` com streak/permissões hardcoded). É a única aba ainda sem dados reais. E8 liga o Profile ao estado real do sistema: permissões via `PermissionsProvider`, histórico via `EventsRepository`, e preferências reais. É o último épico funcional antes do polish de produção.

Depende de E5-blocking-refactor para exibir razão do bloqueio no histórico (manual vs. limite).

## Requisitos

- R1: Seção de Permissões no Profile mostra status real (concedida/pendente) das 4 permissões via `PermissionsProvider`, com atalho para abrir a configuração.
- R2: Tela de Histórico lista eventos reais de `EventsRepository.recentEvents()` — bloqueios, desbloqueios pagos, com data/hora e app.
- R3: Histórico diferencia visualmente bloqueio manual de bloqueio por limite (depende do E5-refactor com `BlockReason`).
- R4: Seção de Preferências exibe opções reais (ex.: janela de desbloqueio, configurações futuras).
- R5: Seção "Sobre" com versão do app (`BuildConfig.VERSION_NAME`).

## Tarefas

- [x] T1: Criar `ProfileViewModel` com flows de `PermissionsProvider` (status das 4 permissões).
- [x] T2: Atualizar `ProfileScreen` para consumir o ViewModel — remover dados hardcoded de permissões.
- [x] T3: Criar `HistoryScreen` em `feature/profile/history/` com lazy list de `RecentEvent`.
- [x] T4: Criar `HistoryViewModel` consumindo `EventsRepository.recentEvents()`.
- [x] T5: Adicionar navegação para `HistoryScreen` a partir do Profile (botão "Ver histórico").
- [x] T6: Adicionar `HistoryNavigation.kt` e registrar no `DollarBlockNavHost`.
- [x] ~~T7~~ (não se aplica: o bloqueio manual saiu, `BlockReason` só tem `DAILY_LIMIT`): Diferenciar visualmente `BlockReason.MANUAL` vs `BlockReason.DAILY_LIMIT` no item de lista do histórico.
- [x] T8: Adicionar seção "Sobre" com versão do app.
- [x] T9: Validar no emulador: permissões refletem estado real, histórico lista eventos reais.

## Critérios de aceite

- CA1: Conceder/revogar uma permissão no sistema → Profile reflete mudança ao retornar à tela (ON_RESUME).
- CA2: Histórico exibe ao menos os últimos 20 eventos com app name, tipo e timestamp.
- CA3: Bloqueio manual e por limite aparecem com labels distintos no histórico.

## Notas / Decisões

- `PermissionsProvider` já está em `data/permissions/` — reutilizar diretamente, não duplicar lógica.
- `RecentEvent` já é um modelo de domínio em `domain/model/` — verificar se precisa de campo `reason` ou se é derivado do tipo de evento.
- A tela de Histórico pode ser uma rota separada (`history`) ou uma seção dentro do ProfileScreen com lazy column — preferir rota separada para manter o padrão de navegação por feature.
- **Próximo épico a ser implementado** após E5-blocking-refactor estar concluído.


## Fechamento (2026-11-26)

Implementado ao longo de E8/E19/E26 (ProfileViewModel, HistoryScreen em `HISTORY_ROUTE`, permissões reais re-checadas no resume). T8 concluído na release 1.0.2: o diálogo "Sobre" mostra `BuildConfig.VERSION_NAME`. T7 descartado — não há mais bloqueio manual.
