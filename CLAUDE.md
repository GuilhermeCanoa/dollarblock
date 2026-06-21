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
| `feature/blocking` | `BlockActivity` (blocking UI) + `payment/GooglePayConfig` (test-mode Google Pay) |
| `feature/home` | Dashboard: Daily Score, Time Saved, Active Limits, Recent Events |
| `feature/apps` | App list with monitoring toggle, limit config, usage bar |
| `feature/statistics` | Period selector + bar chart (Compose Canvas) |
| `feature/profile` | Permission status, preferences, history links |

### Blocking mechanism (ADR-001)

`UsageStatsManager` measures daily usage; `DollarBlockAccessibilityService` detects the foreground app in real time and calls `startActivity(BlockActivity)` when a limit is reached. To survive the target app's cold-start re-raise (~40 ms), the service re-asserts the block on every `TYPE_WINDOW_STATE_CHANGED` event with an additional delayed re-assertion (~500 ms). Debounce only the *registration* of the event, not the launch.

### Payment (E9 — test mode only)

`GooglePayConfig.kt` uses `ENVIRONMENT_TEST` and gateway `"example"` (no PSP keys). On successful payment, `BlockPreferences.grantUnlock(pkg, window)` frees the app for `UNLOCK_WINDOW_MINUTES` (15 min) then re-blocks. Production integration (Stripe + backend) is not yet wired. See `docs/PAYMENTS_SETUP.md` and `docs/BACKEND_STRIPE.md`.

### Room schema

| Entity | PK | Notes |
|---|---|---|
| `MonitoredAppEntity` | `packageName` | App name + monitoring flag + daily limit |
| `DailyUsageEntity` | `id` | Unique index on `(packageName, epochDay)` |
| `BlockEventEntity` | `id` | One row per blocking trigger |
| `UnlockEventEntity` | `id` | FK to `BlockEvent`; holds `penaltyAmount` (simulated) + `unlockUntil` |

### Hilt modules

Both live in `di/`: `DatabaseModule` provides the Room DB and DAOs; `RepositoryModule` binds interfaces to implementations.

---

## Epics status (as of E2)

- **E0** Foundation + Design System ✅
- **E0.5** Navigable shell with mock data ✅
- **E1** Data layer — events history slice ✅; remaining entities (`MonitoredApp`, `DailyUsage`, `UserStats`) + DAO tests pending
- **E2** Onboarding & permissions ✅ — concept pager + guided requests for the 4 permissions; first-run routing via `OnboardingPreferences` flag (replaces the old Usage Access gate)
- **E5** Blocking engine — minimum slice (manual on/off per app) ✅; usage-triggered blocking pending (needs E4)
- **E9** Google Pay — test-mode slice ✅; production PSP pending

Docs: `docs/ROADMAP.md` (epics + acceptance criteria), `docs/ARCHITECTURE.md` (ADRs + data model).
