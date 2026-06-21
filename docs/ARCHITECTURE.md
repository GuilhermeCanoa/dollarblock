# DollarBlock — Arquitetura

> "Your Number One App for Time Management"

Documento de decisões arquiteturais e estrutura técnica do MVP. Toda decisão relevante
é registrada aqui (estilo ADR enxuto) para guiar os próximos desenvolvimentos.

---

## 1. Decisões travadas (ADRs)

### ADR-001 — Mecanismo de bloqueio: Híbrido
**Decisão:** usar `UsageStatsManager` para **medir** o tempo de uso e um
`AccessibilityService` para **detectar em tempo real** o app em primeiro plano e
**disparar o bloqueio instantâneo** (via overlay / tela própria).

**Por quê:** é a abordagem usada por apps reais de bem-estar digital (Opal, Forest).
UsageStats sozinho tem atraso (polling) e mede mal o "agora"; Accessibility sozinho
mede tempo de forma imprecisa. O híbrido entrega medição confiável + bloqueio imediato.

**Custos/riscos:** duas permissões sensíveis (Usage Access + Accessibility) e maior
complexidade de serviços em background. Mitigado por camada de monitoramento isolada.

### ADR-002 — Estrutura: módulo único + pacotes
**Decisão:** um único módulo Gradle `:app` com pacotes separados por camada
(Clean Architecture simplificada), em vez de multi-módulo Gradle.

**Por quê:** velocidade de MVP, menos boilerplate de build, fácil de evoluir para
multi-módulo depois (os pacotes já mapeiam 1:1 para futuros módulos).

### ADR-003 — Cobrança apenas simulada no MVP
**Decisão:** desbloqueio registra evento com `penaltyAmount` simulado; nenhuma
integração financeira real. O domínio já expõe uma abstração `PaymentGateway`
(implementação no-op) para plugar gateway real no futuro.

---

## 2. Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Arquitetura | MVVM + Clean Architecture simplificada |
| DI | Hilt |
| Persistência local | Room |
| Preferências | DataStore (Preferences) |
| Assíncrono | Coroutines + Flow |
| Navegação | Navigation Compose |
| Trabalho periódico | WorkManager (+ Foreground Service quando necessário) |
| Build | Gradle Kotlin DSL + Version Catalog (`libs.versions.toml`) |

**SDK alvo (proposto):** `minSdk = 26` (Android 8.0 — base sólida para UsageStats e
Compose), `targetSdk`/`compileSdk` = mais recente estável. Ajustável.

---

## 3. Estrutura de pacotes (`com.dollarblock`)

```
com.dollarblock
├── DollarBlockApp.kt           // Application + @HiltAndroidApp
├── MainActivity.kt             // host do NavHost + bottom bar
│
├── core/
│   ├── designsystem/           // theme, color, typography, componentes base
│   ├── common/                 // Result, dispatchers, utils de tempo/data, constantes
│   └── navigation/             // rotas, NavHost, DollarBlockBottomBar
│
├── domain/
│   ├── model/                  // modelos de domínio (puros)
│   ├── repository/             // interfaces de repositório
│   └── usecase/                // casos de uso
│
├── data/
│   ├── local/
│   │   ├── db/                 // Room: Database, DAOs, @Entity
│   │   └── prefs/              // DataStore
│   ├── usage/                  // data source UsageStatsManager
│   ├── apps/                   // data source PackageManager (apps instalados)
│   ├── mapper/                 // entity <-> domain
│   └── repository/             // implementações dos repositórios
│
├── service/
│   ├── accessibility/          // DollarBlockAccessibilityService (detecção foreground)
│   ├── monitoring/             // Worker/ForegroundService de coleta de uso
│   └── blocking/               // BlockTrigger + OverlayManager (SYSTEM_ALERT_WINDOW)
│
└── feature/
    ├── onboarding/             // ui + viewmodel
    ├── home/
    ├── apps/
    ├── statistics/
    ├── profile/
    └── blocking/               // tela de bloqueio (UI)
```

Regra de dependência: `feature/service` → `domain` ← `data`. `domain` não conhece
Android nem Room. Pacotes mapeiam para futuros módulos Gradle 1:1.

---

## 4. Modelo de dados (Room)

| Entidade | Campos principais | Observações |
|---|---|---|
| `MonitoredApp` | `packageName` (PK), `appName`, `isMonitored`, `dailyLimitMinutes`, `createdAt` | ícone carregado em runtime via PackageManager, não armazenado |
| `DailyUsage` | `id` (PK), `packageName`, `epochDay`, `usedMillis`, `updatedAt`; índice único `(packageName, epochDay)` | granularidade diária |
| `BlockEvent` | `id` (PK), `packageName`, `timestamp`, `epochDay`, `usedMillis`, `limitMillis` | um por bloqueio disparado |
| `UnlockEvent` | `id` (PK), `packageName`, `timestamp`, `blockEventId` (FK), `penaltyAmount` (simulado), `unlockUntil` | janela de liberação após desbloqueio |
| `UserStats` | `epochDay` (PK), `dailyScore`, `timeSavedMillis`, `goalsMet` | agregados diários (recalculáveis) |

---

## 5. Permissões e justificativas

| Permissão | Uso | Quando pedir |
|---|---|---|
| `PACKAGE_USAGE_STATS` (Usage Access) | medir tempo de uso por app | Onboarding |
| `BIND_ACCESSIBILITY_SERVICE` | detectar app em foreground e disparar bloqueio | Onboarding |
| `SYSTEM_ALERT_WINDOW` (overlay) | exibir tela de bloqueio sobre o app alvo | Onboarding |
| `POST_NOTIFICATIONS` (Android 13+) | foreground service de monitoramento + avisos | Onboarding |
| `QUERY_ALL_PACKAGES` | listar apps instalados para seleção | declarada no manifest |

---

## 6. Riscos técnicos conhecidos

- **Política da Play Store:** `QUERY_ALL_PACKAGES` e `AccessibilityService` exigem
  justificativa formal na publicação. Para MVP/sideload é aceitável; documentar antes
  de publicar.
- **Battery/background:** OEMs matam serviços; usar Foreground Service + WorkManager e
  orientar usuário sobre otimização de bateria.
- **Latência de bloqueio:** Accessibility minimiza, mas há janela de ~centenas de ms.
- **Reset diário:** depende de timezone do dispositivo; usar `epochDay` local consistente.
