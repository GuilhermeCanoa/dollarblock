# Spec: E5 — Refactor de bloqueio (pendências)

**Status:** in-progress
**Épico:** E5
**Data de criação:** 2026-06-23
**Última atualização:** 2026-06-23

---

## Contexto

O E5 foi entregue com bloqueio manual + por limite funcionando. Restam dois itens de limpeza que impactam a qualidade dos dados: (1) o conjunto de apps bloqueados manualmente ainda vive em DataStore separado do `MonitoredAppEntity` no Room, criando duas fontes de verdade; (2) o `BlockEventEntity` não distingue se o bloqueio foi manual ou por limite diário, impedindo o histórico diferenciado no E8.

## Requisitos

- R1: O conjunto "bloqueado manualmente" deve ser persistido em `MonitoredAppEntity` (coluna `isBlockedManually` ou similar), não em DataStore separado.
- R2: `BlockEventEntity` deve ter um campo `reason: BlockReason` (enum: `MANUAL`, `DAILY_LIMIT`).
- R3: O serviço de acessibilidade e a aba Apps devem ler/escrever pelo `MonitoredAppRepository`, não pelo `BlockPreferences.blockedApps`.
- R4: O histórico de eventos (E8) pode exibir razão do bloqueio sem lógica adicional.

## Tarefas

- [ ] T1: Adicionar coluna `isBlockedManually: Boolean` em `MonitoredAppEntity` + migração Room (schema v3).
- [ ] T2: Adicionar enum `BlockReason` em `domain/model/`.
- [ ] T3: Adicionar campo `reason: BlockReason` em `BlockEventEntity` + migração Room (schema v3, mesmo migration do T1).
- [ ] T4: Atualizar `MonitoredAppRepository` e `MonitoredAppDao` para persistir/ler `isBlockedManually`.
- [ ] T5: Atualizar `DollarBlockAccessibilityService` para usar `MonitoredAppRepository` em vez de `BlockPreferences.blockedApps`.
- [ ] T6: Atualizar `BlockActivity` / `EventsRepository.recordBlock()` para receber e persistir `BlockReason`.
- [ ] T7: Deprecar `BlockPreferences.blockedApps` (manter `grantUnlock`/`isUnlockWindowExpired` — esses ficam em DataStore por ora).
- [ ] T8: Validar no emulador: bloqueio manual e por limite continuam funcionando; histórico mostra razão.

## Critérios de aceite

- CA1: App instala e migra sem crash (Room migration sem `fallbackToDestructiveMigration`).
- CA2: Bloquear manualmente um app e depois verificar no DB (ou histórico) que `reason = MANUAL`.
- CA3: App que atinge limite diário aparece no histórico com `reason = DAILY_LIMIT`.

## Notas / Decisões

- Schema v3 deve ser criado em `DollarBlockDatabase` com `@Database(version = 3)` e migration `2 → 3`.
- Manter `BlockPreferences` para `grantUnlock`/`isUnlockWindowExpired` — são estado temporário (janela de desbloqueio), não entidade persistente.
- Este refactor é pré-requisito do E8 para exibir histórico diferenciado por tipo de bloqueio.
