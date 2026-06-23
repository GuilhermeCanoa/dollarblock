# Spec: E1 — Camada de dados (pendências)

**Status:** in-progress
**Épico:** E1
**Data de criação:** 2026-06-23
**Última atualização:** 2026-06-23

---

## Contexto

O E1 foi entregue na fatia de histórico de eventos (`BlockEventEntity`, `UnlockEventEntity`, `EventDao`, `EventsRepository`). Restam as entidades que suportam monitoramento de uso e estatísticas de usuário, além dos testes de DAO que garantem que as queries Room funcionam corretamente.

Hoje `MonitoredApp` vive em DataStore (`BlockPreferences`) — uma limitação que impede queries relacionais, joins com `DailyUsage` e tipagem de motivo de bloqueio (manual vs. limite). Migrar para Room é pré-requisito do E5-refactor.

## Requisitos

- R1: `MonitoredAppEntity` (já existe como entidade Room) deve ser a única fonte de verdade sobre apps monitorados — a cópia em DataStore deve ser depreciada.
- R2: `DailyUsageEntity` e `DailyUsageDao` prontos com queries de range por `epochDay`.
- R3: `UserStats` (entidade ou modelo de domínio) deve agregar métricas de uso histórico para o Profile.
- R4: Testes de DAO (`EventDaoTest`, `MonitoredAppDaoTest`, `DailyUsageDaoTest`) usando Room in-memory.

## Tarefas

- [ ] T1: Criar/validar `MonitoredAppDao` com queries: `observeMonitored()`, `getByPackage()`, `setDailyLimit()`, `setMonitored()`.
- [ ] T2: Criar/validar `DailyUsageDao` com queries: `observeRange(start, end)`, `upsertUsage()`, `getByDay()`.
- [ ] T3: Deprecar o uso de DataStore para o conjunto de apps monitorados em `BlockPreferences` — migrar leitura/escrita para `MonitoredAppRepository`.
- [ ] T4: Definir `UserStats` (pode ser apenas um data class de domínio agregado, sem entidade Room própria).
- [ ] T5: Escrever `EventDaoTest` (Room in-memory) — insert, query por range, delete.
- [ ] T6: Escrever `MonitoredAppDaoTest` — toggle monitoramento, set limite, observe.
- [ ] T7: Escrever `DailyUsageDaoTest` — upsert idempotente, query por epochDay range.

## Critérios de aceite

- CA1: `./gradlew :app:testDebugUnitTest` passa com os novos testes de DAO.
- CA2: Remover DataStore como fonte de apps monitorados não quebra o serviço de bloqueio nem a aba Apps.

## Notas / Decisões

- `MonitoredAppEntity` já existe no schema v2 com `createdAt` e `usageBaselineMillis` — não criar nova entidade, apenas garantir DAO completo.
- Testes de DAO são instrumentados (`androidTest/`) — precisam rodar no emulador ou com Robolectric.
- `UserStats` provavelmente não precisa de entidade Room: é um agregado calculado via query no `MonitoredAppRepository` ou `EventsRepository`.
