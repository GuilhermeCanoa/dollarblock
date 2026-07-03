# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

This project uses the Gradle wrapper. If JDK is not in PATH, point `JAVA_HOME` to the JBR bundled with Android Studio before running:

```bash
# Linux/macOS
export JAVA_HOME="/path/to/android-studio/jbr"
./gradlew :app:assembleDebug --console=plain --no-daemon

# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug --console=plain --no-daemon
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

> `gradle.properties` contains `kotlin.compiler.execution.strategy=in-process` — do not remove it; it works around Kotlin daemon connectivity issues on some machines.

### Install and run on emulator

```bash
./gradlew :app:installDebug --console=plain --no-daemon

# Wake emulator before screenshotting (display may be in Doze — screenshots come back black)
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am start -W -n com.dollarblock/.MainActivity
```

**Screenshots** — do NOT redirect with `>` in PowerShell (corrupts PNG):
```bash
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png local.png
```

**Accessibility service** (required for blocking to work):
```bash
adb shell settings put secure enabled_accessibility_services \
  com.dollarblock/com.dollarblock.service.accessibility.DollarBlockAccessibilityService
adb shell settings put secure accessibility_enabled 1
```
Run these after the app is installed and registered; re-run if the value reverts to `null`.

**UI interaction via adb:**
- Find composable bounds: `adb shell uiautomator dump /sdcard/ui.xml` then pull and parse `text="..." bounds="[x1,y1][x2,y2]"`.
- Open an app without knowing the Activity: `adb shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1`.
- After `am start` on a freshly built Home, Flows (apps/blocked/events) take ~2–4 s to emit — wait before taking validation screenshots.

### Run tests

```bash
./gradlew :app:testDebugUnitTest --console=plain --no-daemon
```

---

## Architecture

Single Gradle module (`:app`) with Clean Architecture packages. Dependency rule: `feature/service` → `domain` ← `data`. The `domain` package has no Android or Room imports.

### Key packages

| Package | Role |
|---|---|
| `core/designsystem` | DollarBlock theme, color tokens, typography, shared Compose components |
| `core/navigation` | `DollarBlockNavHost`, `DollarBlockBottomBar`, `TopLevelDestination` routes |
| `domain/model` | Pure Kotlin data models (`RecentEvent`, `MonitoredAppUsage`) |
| `domain/repository` | Repository interfaces (`EventsRepository`, `MonitoredAppRepository`) |
| `data/local/db` | Room: `DollarBlockDatabase`, DAOs, `@Entity` classes |
| `data/local/prefs` | `BlockPreferences` (blocked-app set + unlock windows) and `OnboardingPreferences` (onboarding-completed flag) — both DataStore |
| `data/permissions` | `PermissionsProvider` — checks + builds intents for the 4 permissions (Usage Access, Accessibility, Overlay, Notifications) |
| `data/repository` | Repository implementations injected via Hilt modules in `di/` |
| `data/usage` | `UsageStatsProvider` wrapping `UsageStatsManager` |
| `data/apps` | `InstalledAppsProvider` wrapping `PackageManager` |
| `service/accessibility` | `DollarBlockAccessibilityService` — detects foreground app, fires blocking |
| `service/monitoring` | `UsageSyncWorker` + `UsageSyncScheduler` — periodic usage sync via WorkManager |
| `feature/onboarding` | `OnboardingScreen` (concept pager + guided permission requests) + `OnboardingViewModel` |
| `feature/blocking` | `BlockActivity` (blocking UI) + `payment/` (Google Pay + Stripe charge via AWS backend) |
| `feature/home` | Dashboard: Daily Score, Time Saved, Active Limits, Recent Events |
| `feature/apps` | App list with monitoring toggle, limit config, usage bar |
| `feature/statistics` | Period selector + bar chart (Compose Canvas) |
| `feature/profile` | Real permission status + today's stats header; `HistoryScreen` (full block/unlock timeline) on a nested `HISTORY_ROUTE` |

### Blocking mechanism (ADR-001)

`UsageStatsManager` measures daily usage; `DollarBlockAccessibilityService` detects the foreground app in real time and calls `startActivity(BlockActivity)` when a limit is reached. To survive the target app's cold-start re-raise (~40 ms), the service re-asserts the block on every `TYPE_WINDOW_STATE_CHANGED` event with an additional delayed re-assertion (~500 ms). Debounce only the *registration* of the event, not the launch.

### Payment (E9 — real Stripe charge, test mode)

`feature/blocking/payment/` wires a **real Stripe charge through an AWS backend**, in Stripe *test* mode:
- `GooglePayConfig.kt` — `ENVIRONMENT_TEST`, gateway `"stripe"` with a `pk_test_` publishable key. `PRICE = "1.00"` BRL (**day pass** — E11); the amount actually charged is fixed in the `unlock-charge` Lambda and must be kept in sync.
- `BlockActivity.handlePaymentData` extracts the Google Pay token, isolates the Stripe token `id` via `StripeToken.extractId`, and calls `PaymentApiClient.charge()` → `POST /unlock-charge` (live API Gateway + Lambda + Stripe). Unlock is granted **only** on `status == "succeeded"`; idempotency via a per-request UUID.
- On success, `BlockPreferences.grantUnlockForToday(pkg)` frees the app until **local midnight** (wall-clock `unlockUntilMs`; at most one payment per app per day by construction); the unlock is logged through `EventsRepository.recordUnlock`.
- A debug-only "Simulate payment" fallback bypasses the charge.
- Brand voice (E11): deadpan "time-bank manager" tone — taxímetro/fatura/extrato/recibos vocabulary; see MANIFESTO.md "Como falamos" before writing any user-facing string.

Production (`pk_live_`/`sk_live_`, `ENVIRONMENT_PRODUCTION`, merchantId, key out of source) is not yet activated. See `docs/PAYMENTS_SETUP.md` and `docs/BACKEND_STRIPE.md`.

### Room schema

`DollarBlockDatabase` is at **version 4** (`MonitoredAppEntity` gained `createdAt` in v2, `usageBaselineMillis` was added in v2 and dropped again in v4 — usage is always 100% of the device's raw `UsageStatsManager`/`UsageEvents` measurement, no DollarBlock-side baseline/adjustment).

| Entity | PK | Notes |
|---|---|---|
| `MonitoredAppEntity` | `packageName` | App name + monitoring flag + `dailyLimitMinutes?` + `createdAt` |
| `DailyUsageEntity` | `id` | Unique index on `(packageName, epochDay)` |
| `BlockEventEntity` | `id` | One row per blocking trigger |
| `UnlockEventEntity` | `id` | FK to `BlockEvent`; holds `penaltyAmount` (simulated) + `unlockUntil` |

### Hilt modules

Split **per feature** in `di/` to minimize merge conflicts between parallel devs (see `docs/MERGE_HOTSPOTS.md`): `DatabaseModule` provides only the `DollarBlockDatabase`; `EventsModule` provides `EventDao` + binds `EventsRepository`; `MonitoredAppModule` provides `MonitoredAppDao`/`DailyUsageDao` + binds `MonitoredAppRepository`. Add a new feature's DI in its own module file — don't edit another feature's module.

### Navigation (per feature)

Each feature owns a `feature/<name>/<Name>Navigation.kt` declaring its route constant (e.g. `HOME_ROUTE`) and a `NavGraphBuilder.<name>Screen()` extension. `DollarBlockNavHost` just composes them (`homeScreen()`, `appsScreen()`, …) and `TopLevelDestination` references the route constants. Adding a screen is a one-line, conflict-free change. Non-top-level (detail) screens follow the same pattern but take nav callbacks instead of appearing in `TopLevelDestination` — e.g. `historyScreen(onBack)` on `HISTORY_ROUTE`, opened from Profile via `profileScreen(onOpenHistory)`; the bottom bar simply doesn't highlight while there.

### Parallel-dev workflow

Two devs work in parallel. `CONTRIBUTING.md` defines the workflow (feature-vertical ownership, rebase-before-PR, shared-file etiquette); `.github/CODEOWNERS` maps owners; `docs/MERGE_HOTSPOTS.md` lists conflict-prone files and anti-conflict techniques. A versioned `.githooks/pre-push` runs `:app:testDebugUnitTest` before every push (activate with `git config core.hooksPath .githooks`). Put business logic in pure functions (e.g. `feature/home/HomeMetrics.kt`) with JVM unit tests — not inside ViewModel `combine {}` or Composables.

---

## Epics status (as of E8)

- **E0** Foundation + Design System ✅
- **E0.5** Navigable shell with mock data ✅
- **E1** Data layer — events history slice ✅; remaining entities (`UserStats`) + DAO tests pending
- **E2** Onboarding & permissions ✅ — concept pager + guided requests for the 4 permissions; first-run routing via `OnboardingPreferences` flag (replaces the old Usage Access gate)
- **E3** Apps — list, monitoring toggle, daily-limit dialog, name search ✅
- **E4** Monitoring — `UsageStatsManager` reads + periodic sync to `DailyUsageEntity` ✅
- **E5** Blocking engine ✅ — manual on/off per app + usage-triggered blocking (foreground polling)
- **E6** Home dashboard ✅ — Daily Score / Time Saved / Active Limits over monitored apps with limits
- **E7** Statistics ✅ — daily/weekly/monthly aggregation + per-app weekly score over `DailyUsageEntity` (raw usage, no baseline — see E12)
- **E8** Profile & History ✅ — real permission status (re-checked on resume) + real today's stats header + `HistoryScreen` (full block/unlock timeline, grouped by day) on a nested `HISTORY_ROUTE`
- **E9** Google Pay + **real Stripe charge via AWS backend** ✅ (test mode); production keys/env pending
- **E10** Quality (transversal) — parallel-dev workflow + per-feature DI/Navigation + unit-test
  scaffolding (`HomeMetrics`) + pre-push test gate ✅; DAO/ViewModel tests + CI pending
- **E12** Uso = 100% da métrica do celular ✅ — `usageBaselineMillis` removido (Room v3→v4); limite
  diário, UI e Statistics comparam/exibem uso bruto do `UsageStatsManager`/`UsageEvents` desde a
  meia-noite, sem baseline por app. Ver `docs/specs/E12-uso-100-porcento-real.md`.

Docs: `docs/ROADMAP.md` (epics + acceptance criteria), `docs/ARCHITECTURE.md` (ADRs + data model).

---

## Project State (leia antes de implementar)

- **Status dos épicos:** `docs/ROADMAP.md`
- **Specs detalhados de pendências:** `docs/specs/` — um arquivo por épico/tarefa não-trivial pendente
- **Log de mudanças funcionais:** `docs/CHANGELOG.md` — atualize sempre que entregar algo significativo
- **Template de spec:** `docs/specs/TEMPLATE.md`
- **Próximo passo sugerido:** E8 está ✅ segundo o CLAUDE.md acima; verificar specs em `docs/specs/` para pendências reais de E1, E5, E7, E10

> Antes de implementar qualquer feature ou mudança de regra não-trivial: crie ou atualize o spec em `docs/specs/`. Ao concluir: marque tarefas no spec, atualize o status para `done` e adicione entrada no `docs/CHANGELOG.md`.
