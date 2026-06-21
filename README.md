# DollarBlock

App Android que bloqueia apps ao atingir limites diários de uso e cobra uma penalidade financeira para desbloquear.

Conceito e motivação: [MANIFESTO.md](./MANIFESTO.md).

---

## Requisitos

- Android Studio Hedgehog ou superior (inclui JBR)
- Android SDK 33+
- Emulador ou dispositivo físico Android

---

## Build

```powershell
# Windows
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug --console=plain --no-daemon
```

```bash
# Linux / macOS
export JAVA_HOME="/path/to/android-studio/jbr"
./gradlew :app:assembleDebug --console=plain --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### Instalar e rodar no emulador

```bash
./gradlew :app:installDebug --console=plain --no-daemon
adb shell am start -W -n com.dollarblock/.MainActivity
```

### Testes

```bash
./gradlew :app:testDebugUnitTest --console=plain --no-daemon
```

---

## Permissões necessárias

O app requer três permissões do sistema, concedidas manualmente pelo usuário:

| Permissão | Finalidade |
|---|---|
| Usage Access | Medir o tempo de uso por app |
| Accessibility Service | Detectar o app em foreground e disparar o bloqueio |
| Display Over Other Apps | Sobrepor a tela de bloqueio |

Para habilitar o Accessibility Service via adb (útil em testes):

```bash
adb shell settings put secure enabled_accessibility_services \
  com.dollarblock/com.dollarblock.service.accessibility.DollarBlockAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

---

## Arquitetura

Single module (`:app`) com Clean Architecture. Regra de dependência: `feature/service` → `domain` ← `data`.

```
app/
├── core/
│   ├── designsystem/     # Tema, cores, tipografia, componentes base
│   └── navigation/       # NavHost, BottomBar, rotas
├── domain/
│   ├── model/            # Modelos Kotlin puros
│   └── repository/       # Interfaces de repositório
├── data/
│   ├── local/db/         # Room: Database, DAOs, Entities
│   ├── local/prefs/      # DataStore: apps bloqueados, janelas de desbloqueio
│   ├── repository/       # Implementações dos repositórios
│   ├── usage/            # UsageStatsProvider
│   └── apps/             # InstalledAppsProvider
├── service/
│   ├── accessibility/    # DollarBlockAccessibilityService
│   └── monitoring/       # UsageSyncWorker + WorkManager
└── feature/
    ├── blocking/         # BlockActivity + GooglePayConfig
    ├── home/             # Dashboard
    ├── apps/             # Lista de apps e configuração de limites
    ├── statistics/       # Gráficos de uso por período
    └── profile/          # Permissões, preferências, histórico
```

### Stack

| Camada | Tecnologia |
|---|---|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Banco de dados | Room |
| Preferências | DataStore |
| Background | WorkManager |
| Pagamento | Google Pay API → Stripe (produção) |

### Mecanismo de bloqueio

`DollarBlockAccessibilityService` escuta eventos `TYPE_WINDOW_STATE_CHANGED`. Quando o app em foreground atingiu o limite diário, inicia `BlockActivity` por cima. Para sobreviver ao cold-start do app alvo (~40 ms), o serviço re-asserta o bloqueio a cada evento com uma re-assertiva adicional com delay de ~500 ms.

---

## Status

| Funcionalidade | Status |
|---|---|
| Design System & navegação | Concluído |
| Bloqueio por app | Concluído |
| Monitoramento de uso | Concluído |
| Histórico de bloqueios e desbloqueios | Concluído |
| Google Pay (modo teste) | Concluído |
| Onboarding & permissões | Em desenvolvimento |
| Dashboard com dados reais | Em desenvolvimento |
| Integração Stripe (produção) | Planejado |

Roadmap completo: [`docs/ROADMAP.md`](./docs/ROADMAP.md)

---

## Documentação

| Arquivo | Conteúdo |
|---|---|
| [`MANIFESTO.md`](./MANIFESTO.md) | Conceito, problema e proposta de valor |
| [`docs/ROADMAP.md`](./docs/ROADMAP.md) | Épicos e critérios de aceite |
| [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) | ADRs e modelo de dados |
| [`docs/PAYMENTS_SETUP.md`](./docs/PAYMENTS_SETUP.md) | Configuração do Google Pay e PSP |
| [`CLAUDE.md`](./CLAUDE.md) | Guia de desenvolvimento (build, adb, convenções) |
