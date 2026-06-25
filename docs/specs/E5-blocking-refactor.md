# Spec: E5 — Refactor de bloqueio (pendências)

**Status:** done
**Épico:** E5
**Data de criação:** 2026-06-23
**Última atualização:** 2026-06-23

---

## Contexto

O E5 foi entregue com bloqueio por limite funcionando. A feature de bloqueio manual (toggle por app na Home) foi **removida da UI** — `BlockPreferences.setBlocked` não é mais chamado por ninguém. O path de bloqueio manual no `DollarBlockAccessibilityService` (linha `blockPreferences.shouldBlock()`) é código morto.

Resta um item de limpeza: o `BlockEventEntity` não distingue se o bloqueio foi por limite diário ou manual, o que impede histórico diferenciado. Como o bloqueio manual foi removido, a distinção relevante passa a ser `DAILY_LIMIT` (único tipo real) vs. eventual futuro tipo.

## Requisitos

- R1: `BlockEventEntity` deve ter um campo `reason: BlockReason` (enum: `DAILY_LIMIT`; extensível no futuro).
- R2: Remover o path de bloqueio manual morto do `DollarBlockAccessibilityService` (chamada a `blockPreferences.shouldBlock()`).
- R3: Remover `blockedPackages`/`setBlocked`/`shouldBlock` do `BlockPreferences` (manter apenas `grantUnlock`/`getUnlockGrant`).

## Tarefas

- [x] T1: Adicionar enum `BlockReason` em `domain/model/` (valor: `DAILY_LIMIT`).
- [x] T2: Adicionar campo `reason: BlockReason` em `BlockEventEntity` + migração Room (schema v3).
- [x] T3: Atualizar `EventsRepository.recordBlock()` para receber e persistir `BlockReason`.
- [x] T4: Atualizar `DollarBlockAccessibilityService`: remover path manual, passar `BlockReason.DAILY_LIMIT` ao registrar.
- [x] T5: Remover `blockedPackages`/`setBlocked`/`shouldBlock` do `BlockPreferences`.
- [x] T6: Validar no emulador: app que atinge limite é bloqueado normalmente; histórico não quebra.

## Critérios de aceite

- CA1: App instala e migra sem crash (Room migration sem `fallbackToDestructiveMigration`).
- CA2: App que atinge limite diário aparece no histórico sem erro de tipo.
- CA3: `BlockPreferences` não tem mais referência a `blockedPackages`.

## Notas / Decisões

- Schema v3: `DollarBlockDatabase` com `@Database(version = 3)` e migration `2 → 3` adicionando coluna `reason TEXT NOT NULL DEFAULT 'DAILY_LIMIT'` em `block_events`.
- Manter `BlockPreferences` apenas para `grantUnlock`/`getUnlockGrant` — são estado temporário (janela de desbloqueio pós-pagamento), não entidade persistente.
