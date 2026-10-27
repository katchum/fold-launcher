# Fold8 & Slab Launcher (`com.xsun.foldlauncher`) — Project & Repo Health

**Repository**: [`katchum/fold-launcher`](https://github.com/katchum/fold-launcher)
**Workspace Path**: `/home/xsun/projects/android-launcher/fold-launcher`
**Maintainer**: Repo Health & Project Health Lead

---

## 1. Module Architecture & Ownership Matrix

The codebase is organized under `app/src/main/java/com/xsun/foldlauncher/` into clean, decoupled packages with zero circular dependencies and strict main-thread safety:

| Module / Package | Key Source Files | Owning Lead / Specialist | Responsibility |
| :--- | :--- | :--- | :--- |
| **`model`** | `model/AppEntry.kt` | Core Architecture / Data Lead | Immutable domain models for installed apps (`AppEntry`), foldable split-screen app pairs (`SplitPair`), and workspace categories. |
| **`search`** | `search/SearchEngine.kt` | Search & Omni-Command Specialist | Zero-allocation Spotlight app ranking (exact, prefix, multi-word acronym, word-boundary, substring, fuzzy subsequence, frequency boost) and inline math expression evaluator (`evaluateInlineMath`). |
| **`posture`** | `posture/PostureState.kt` | Foldable Posture & WindowManager Lead | Real-time `WindowInfoTracker` / `FoldingFeature` hinge sensor state tracking and `PostureClassifier` (`COMPACT`, `FLEX_TABLETOP`, `EXPANDED`) with adaptive grid sizing. |
| **`service`** | `service/FoldAccessibilityService.kt`, `service/SystemTweaker.kt` | System Services & One UI Tweaker Lead | Non-root Samsung DeX Gboard Auto-Guard (`DEFAULT_INPUT_METHOD` observer + `dex_on_screen_keyboard_location=1`), lock/home screen landscape rotation toggles, split-screen `FLAG_ACTIVITY_LAUNCH_ADJACENT` launcher, global gestures (swipe-down shade, double-tap lock), and foreground package notifications to `VcBackgroundMonitor`. |
| **`vc`** | `vc/VcModels.kt`, `vc/VcBridgeClient.kt`, `vc/VcBackgroundMonitor.kt`, `vc/VcCommandCenterCard.kt` | VC Non-Foreground Bridge Specialist | Zero-dependency HTTP/JSON bridge to `vc-host` (`/api/attention`, `/api/snapshot`, `/api/sessions/<id>/message` & `/steer`), foreground-aware adaptive background polling, smart unread alert diffing, 120Hz home screen `VcCommandCenterCard`, and 1-tap Z Fold 8 VC + Termux/Browser split workspace. |
| **`hud`** | `hud/SystemTelemetryHud.kt`, `hud/TelemetryCollector.kt` | Telemetry & Hardware HUD Specialist | Zero-bloat live hardware telemetry HUD (frame rate / 120Hz refresh status, RAM pressure, battery thermal/current readouts, active display DP/px posture geometry). |
| **`spatial`** | `spatial/SpatialMath.kt`, `spatial/ReachabilityOverlay.kt` | 120Hz Spatial & Ergonomics Specialist | One-handed thumb-arc geometry, reachability pull-down cursor mapping for 7.6"+ inner tablet displays and tall slab screens (S23 Ultra), and spring-damped spatial physics. |
| **`omni`** | `omni/OmniCommandEngine.kt` | Search & Omni-Command Specialist | Spotlight Omni-Command Palette parser & executor (`>adb`, `>dex`, `>rotate`, `>vc`, deep links, Base64/URL/JSON/Timestamp clipboard transformers, system settings shortcuts). |
| **`data`** | `data/AppRepository.kt`, `data/LauncherPreferences.kt` | Core Architecture / Data Lead | Asynchronous `LauncherApps` / `PackageManager` icon & metadata loader on `Dispatchers.IO`, live `PackageMonitor` broadcast updates, launch frequency & pin persistence. |
| **`ui/theme`** | `ui/theme/Theme.kt` | 120Hz Compose UI Lead | True-AMOLED pitch-black (`#000000`) & glassmorphic dark palette (`ObsidianSurface`, `ElectricCyan`, `EmeraldSuccess`, `AmberWarning`, `CoralDanger`) tuned for zero OLED smear at 120Hz. |
| **`ui/animation`** | `ui/animation/LauncherAnimations.kt` | 120Hz Compose UI Lead | Critically damped spring specs (`DampingRatioNoBouncy`, `StiffnessMediumLow`) and posture transition animations engineered for sub-8.3ms frame times. |
| **`ui/components`** | `ui/components/TopStatusBar.kt`, `AppIconTile.kt`, `NativeWidgets.kt`, `SpotlightOverlay.kt`, `TaskbarDock.kt` | 120Hz Compose UI Lead | Oversized Top-Right Hero Clock & posture badge header, adaptive app icon tiles, zero-IPC native widgets, 0ms swipe-up Spotlight overlay, and persistent taskbar dock. |
| **`ui/screens`** | `ui/screens/LauncherScreens.kt` | 120Hz Compose UI Lead | Posture-adaptive home screens: `CompactCoverScreen` (cover display & S23 Ultra slab), `ExpandedInnerScreen` (dual-hemisphere Z Fold 8 tablet & DeX), and `FlexTabletopScreen` (crease-aware horizontal fold split). |
| **`viewmodel`** | `viewmodel/LauncherViewModel.kt`, `MainActivity.kt` | Core Architecture / Integration Lead | Unidirectional `StateFlow` state holder coordinating `AppRepository`, `PostureState`, `SearchEngine`, `SystemTweaker`, `VcBackgroundMonitor`, `OmniCommandEngine`, and `SpatialMath`. |

---

## 2. Feature Checklist

### 2.1 Core & "Impossible on Stock Samsung One UI Home" Features
- [x] **Real-Time 3-Posture Continuity (`COMPACT`, `FLEX_TABLETOP`, `EXPANDED`)**: Zero-restart layout morphing between cover screen / slab phone, half-opened tabletop crease split, and unfolded 7.6"+ dual-hemisphere inner display.
- [x] **Oversized Top-Right Hero Clock & Live Posture Header**: Replaces One UI's cramped top-left carrier bar with a high-contrast top-right hero clock, date, battery/thermal strip, and live hinge posture badge.
- [x] **0ms Swipe-Up Spotlight Search + Acronym Ranking + Inline Math**: Instant keyboard focus on swipe-up anywhere; ranks apps via acronym shorthand (`yt` -> YouTube, `gm` -> Google Maps), prefix, and launch frequency; evaluates math expressions inline (`(2560 - 1080) / 2`).
- [x] **Samsung DeX Gboard Auto-Guard (`FoldAccessibilityService` + `SystemTweaker`)**: Automatically intercepts Samsung DeX's forced fallback to `com.samsung.android.honeyboard`, restores Gboard (`com.google.android.inputmethod.latin`), and sets `dex_on_screen_keyboard_location = 1` without root.
- [x] **Non-Root Lock Screen & Home Screen Landscape Rotation**: One-tap toggle writing `lock_screen_allow_rotation = 1` and `accelerometer_rotation = 1` via `WRITE_SECURE_SETTINGS`.
- [x] **True 1-Tap Split-Screen App Pairs (`FLAG_ACTIVITY_LAUNCH_ADJACENT`)**: Instant dual-app launching on unfolded Z Fold 8 / DeX displays.
- [x] **Spotlight Omni-Command Palette (`omni`)**: Prefix commands (`>`, `/`, `=`) for instant developer actions, clipboard decoding/formatting, system deep-links, and VC agent steering directly from the search bar.
- [x] **120Hz One-Handed Spatial Thumb-Arc & Reachability (`spatial`)**: Ergonomic thumb-zone arc layout and one-handed reachability cursor for tall slab displays (S23 Ultra) and wide unfolded canvases.
- [x] **Live Hardware & Frame-Time Telemetry HUD (`hud`)**: Real-time 120Hz refresh verification, memory footprint, and battery current/thermal readout right on the home screen.
- [x] **One-Time Non-Root ADB Setup & Safe Debloat Scripts (`scripts/`)**: `adb-setup-fold8.sh` and `adb-debloat-fold8.sh` preserving DeX, Knox/Wallet, S Pen, and foldable continuity.

### 2.2 VC Non-Foreground Background Integration (`com.xsun.foldlauncher.vc`)
- [x] **Un-Exported Widget & Paused WebView Bypass**: Directly polls `/api/attention?since=<cursor>&rosterTag=<tag>` (with `/api/snapshot` fallback) using zero-dependency `HttpURLConnection`, bypassing VC's 1.5s background WebView pause (`ConversationRuntime.HIDE_DELAY_MS`) and un-exported `AppWidgetProvider` limitation.
- [x] **Real-Time Foreground Detection via `FoldAccessibilityService`**: Immediately detects when `dev.agentconversation*` leaves or enters the foreground (`TYPE_WINDOW_STATE_CHANGED`), pausing redundant background alerts while VC is active on screen and resuming adaptive polling (8s on home screen, 15s when agents are working in background) when VC is backgrounded.
- [x] **Smart Unread Notification Diffing (`computeNewBackgroundAlerts`)**: Emits high-priority Android notifications (`vc_urgent_alerts` for approvals/failures, `vc_agent_updates` for completed turns) only for newly arrived unread events while VC is not in the foreground.
- [x] **120Hz Home Screen `VcCommandCenterCard`**: Live status badges (`Working`, `To Approve`, `Unread`, `Failed`), active agent pills, top actionable attention items with 1-tap `Ack`, inline quick-send / live `/steer` bar, and 1-tap `Split VC + Termux` launcher.

---

## 3. Unit Test Suite Inventory (`./gradlew testDebugUnitTest`)

All pure JVM unit test suites reside under `app/src/test/java/com/xsun/foldlauncher/` and run on every PR and push to `main`:

| Test Suite | Path | Coverage Scope | Status |
| :--- | :--- | :--- | :--- |
| **`SearchEngineTest`** | `app/src/test/java/com/xsun/foldlauncher/search/SearchEngineTest.kt` | Exact, prefix, multi-word acronym (`yt`, `gm`, `as`), substring, fuzzy subsequence scoring, launch count tie-breaking, and `evaluateInlineMath` arithmetic/parentheses/division-by-zero safety. | Tracked & Active |
| **`PostureClassifierTest`** | `app/src/test/java/com/xsun/foldlauncher/posture/PostureClassifierTest.kt` | `COMPACT` (cover screen & S23 Ultra slab), `FLEX_TABLETOP` (horizontal `HALF_OPENED` hinge), `EXPANDED` (unfolded 7.6"+ inner display & vertical book posture), and adaptive grid column calculations. | Tracked & Active |
| **`VcBridgeParserTest`** | `app/src/test/java/com/xsun/foldlauncher/vc/VcBridgeParserTest.kt` | Full `/api/attention` payload parsing, delta cursor merging (`reset: false` vs `reset: true`), read-acknowledgement transitions, `/api/snapshot` fallback parsing, and `computeNewBackgroundAlerts` foreground/baseline suppression. | Specialist Suite |
| **`OmniCommandEngineTest`** | `app/src/test/java/com/xsun/foldlauncher/omni/OmniCommandEngineTest.kt` | Omni-Command prefix detection, system toggle commands, clipboard transformations, and fallback query routing. | Specialist Suite |
| **`SpatialMathTest`** | `app/src/test/java/com/xsun/foldlauncher/spatial/SpatialMathTest.kt` | Ergonomic thumb-arc polar-to-Cartesian layout coordinates, one-handed reachability cursor projection, and spring velocity clamping. | Specialist Suite |

---

## 4. CI, Sandbox & Live Device Verification Status

### 4.1 Sandbox (`bwrap`) Capabilities Audit
- **GitHub CLI (`gh`) & Git Remote Push**: **WORKING** inside `bwrap`.
  - Authenticated as `katchum` (`repo`, `workflow`, `read:org`, `gist` scopes).
  - Created public repository [`https://github.com/katchum/fold-launcher`](https://github.com/katchum/fold-launcher) and configured `origin` remote with local credential helper (`!/usr/bin/gh auth git-credential`).
  - Note: `/home/xsun/.gitconfig` is bind-mounted read-only (`Device or resource busy` on `--global`), so all Git configuration changes must use `git config --local`.
- **Java 17 & Gradle Wrapper (`./gradlew`)**: **WORKING** inside `bwrap`.
  - OpenJDK `17.0.20.1` and Gradle `8.10.2` daemon initialize and execute cleanly inside the sandbox.
  - Note: The host `/usr/lib/android-sdk` only contains `platform-tools` (`adb`) and does not have `platforms/android-35` or `build-tools` installed locally. Full Android compilation (`testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`) runs in **GitHub Actions CI** (`.github/workflows/android-ci.yml`) on `ubuntu-latest` where the full Android SDK is pre-installed, producing downloadable `app-debug` and `app-release` APK artifacts.

### 4.2 GitHub Actions CI (`.github/workflows/android-ci.yml`)
- **Workflow**: `Android CI` (`check` job)
- **Scope Fast-Path**: `scripts/ci-scope.sh` bypasses heavy Gradle builds when a commit only modifies `docs/**/*.md`.
- **Full Verification Gate**: Runs `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` on Temurin JDK 17 and uploads:
  - `app-debug` (`app/build/outputs/apk/debug/app-debug.apk`)
  - `app-release` (`app/build/outputs/apk/release/app-release.apk`, R8 minified & resource-shrunk)
  - `lint-report-debug` (`app/build/reports/lint-results-debug.html`)
- **Acceptance Gate**: `scripts/acceptance-status.sh <40-hex-sha> approve|reject "<note>"` posts the `acceptance` commit status on `katchum/fold-launcher`.

### 4.3 Live Test Device Status (`SM-S918U1` at `100.68.53.123:40919`)
- **Connected Device**: `100.68.53.123:40919` (`model:SM_S918U1`, `device:dm3q`, Samsung Galaxy S23 Ultra running One UI 8.5 / Android 16) — **ONLINE & REACHABLE** via `adb` inside `bwrap`.
- **Install Policy**: Strictly `--user 0` (`adb -s 100.68.53.123:40919 install --user 0 -r ...`), coordinated through the single **Integration Agent**.
