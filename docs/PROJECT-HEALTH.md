# Fold8 & Slab Launcher (`com.xsun.foldlauncher`) — Project & Repo Health (`v0.2.0`)

**Repository**: [`katchum/fold-launcher`](https://github.com/katchum/fold-launcher)
**Workspace Path**: `/home/xsun/projects/android-launcher/fold-launcher`
**Current Version**: `v0.2.0` (`versionCode = 2`, `versionName = "0.2.0"`)
**Maintainer**: Repo Health & Project Health Lead

---

## 1. Module Architecture & Ownership Matrix

The codebase is organized under `app/src/main/java/com/xsun/foldlauncher/` into clean, decoupled packages with zero circular dependencies and strict main-thread safety:

| Module / Package | Key Source Files | Owning Lead / Specialist | Responsibility |
| :--- | :--- | :--- | :--- |
| **`model`** | `model/AppEntry.kt` | Core Architecture / Data Lead | Immutable domain models for installed apps (`AppEntry`), foldable split-screen app pairs (`SplitPair`), and workspace categories (`All`, `VC & Dev`, `Frequent`, `System`). |
| **`search`** | `search/SearchEngine.kt` | Search & Omni-Command Specialist | Zero-allocation Spotlight app ranking (exact, prefix, multi-word acronym, word-boundary, substring, fuzzy subsequence, frequency boost) and inline math expression evaluator (`evaluateInlineMath`). |
| **`posture`** | `posture/PostureState.kt` | Foldable Posture & WindowManager Lead | Real-time `WindowInfoTracker` / `FoldingFeature` + hardware `Sensor.TYPE_HINGE_ANGLE` (`0°..180°`) tracking, Z Fold 8 `1248x1972` (`475dp x 751dp`) Cover Display landscape classification fix, and interactive `PostureOverride` switcher (`AUTO`, `FLEX`, `INNER`, `COVER`, `TENT`). |
| **`service`** | `service/FoldAccessibilityService.kt`, `service/SystemTweaker.kt` | System Services & One UI Tweaker Lead | Non-root Samsung DeX Gboard Auto-Guard, One UI 9.0 Lock Screen customization (`face_stay_on_lock_screen = 0`, `lock_application_shortcut` remapping to VC & Fold8 Launcher), `TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED` Lock-Screen Micro-HUD Pill, multi-instance VC & split-screen `FLAG_ACTIVITY_LAUNCH_ADJACENT` combos, and foreground package detection. |
| **`vc`** | `vc/VcModels.kt`, `vc/VcBridgeClient.kt`, `vc/VcBackgroundMonitor.kt`, `vc/VcCommandCenterCard.kt` | VC Non-Foreground Bridge Specialist | Zero-dependency HTTP/JSON bridge to `vc-host` (`/api/attention`, `/api/snapshot`, `/api/sessions/<id>/message` & `/steer`), Multi-Instance VC Switcher (`PROD` / `PREVIEW` / `COMPARE`), 1-tap Z Fold 8 Dual-Pane Split-Screen combos (`VC + Termux`, `Prod + Compare`, `Prod + Preview`, `VC + Chrome`), inline Pending Approval Banner, and foreground-aware background polling. |
| **`hud`** | `hud/FloatingHudOverlay.kt` | Telemetry & Hardware HUD Specialist | System-wide draggable 120Hz Floating HUD Pill (`TYPE_APPLICATION_OVERLAY`) and Lock-Screen Micro-HUD (`TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED`) displaying live FPS, RAM, battery temperature (°C), hinge angle (`0°..180°`), posture badge, and VC status dot. |
| **`spatial`** | `spatial/SpatialLauncherFeatures.kt` | 120Hz Spatial & Ergonomics Specialist | One-handed `RadialThumbArcOverlay`, 3D gyroscope tilt parallax (`spatialParallaxCard`), `FlexVirtualTrackpadCard` with dedicated 2-finger scroll strip, and 120Hz `TentModeStandbyHud`. |
| **`omni`** | `omni/OmniCommandEngine.kt` | Search & Omni-Command Specialist | Spotlight Omni-Command Palette parser & executor (`>adb`, `>dex`, `>rotate`, `>vc`, unit/currency converters, 2D `px`-to-`dp` `@ 420dpi` resolution calculator, Base64/URL/SHA-256/Timestamp developer tools, Samsung hidden diagnostics). |
| **`data`** | `data/AppRepository.kt`, `data/LauncherPreferences.kt` | Core Architecture / Data Lead | Asynchronous `LauncherApps` / `PackageManager` icon & metadata loader on `Dispatchers.IO`, live package broadcast updates, launch frequency & pin persistence. |
| **`ui/theme`** | `ui/theme/Theme.kt` | 120Hz Compose UI Lead | True-AMOLED pitch-black (`#000000`) & glassmorphic dark palette (`ObsidianSurface`, `ElectricCyan`, `EmeraldSuccess`, `AmberWarning`, `CoralDanger`) tuned for zero OLED smear at 120Hz. |
| **`ui/animation`** | `ui/animation/LauncherAnimations.kt` | 120Hz Compose UI Lead | Critically damped spring specs (`DampingRatioNoBouncy`, `StiffnessMediumLow`) and posture transition animations engineered for sub-8.3ms frame times. |
| **`ui/components`** | `ui/components/TopStatusBar.kt`, `AppIconTile.kt`, `NativeWidgets.kt`, `SpotlightOverlay.kt`, `TaskbarDock.kt` | 120Hz Compose UI Lead | Oversized Top-Right Hero Clock, live `0°..180°` hinge badge, interactive `PostureOverride` chip bar (`AUTO`/`FLEX`/`INNER`/`COVER`/`TENT`), adaptive app icon tiles, zero-IPC native widgets, 0ms swipe-up Spotlight overlay, and taskbar dock. |
| **`ui/screens`** | `ui/screens/LauncherScreens.kt` | 120Hz Compose UI Lead | Posture-adaptive home screens (`CompactCoverScreen`, `ExpandedInnerScreen`, `FlexTabletopScreen`, `TentModeStandbyHud`) and App Drawer Category Filter Pills (`All`, `VC & Dev`, `Frequent`, `System`). |
| **`viewmodel`** | `viewmodel/LauncherViewModel.kt`, `MainActivity.kt` | Core Architecture / Integration Lead | Unidirectional `StateFlow` state holder and `MainActivity` (`setShowWhenLocked(true)` 120Hz Lock-Screen Cockpit) coordinating all launcher subsystems. |

---

## 2. Feature Checklist (`v0.1.0` + `v0.2.0`)

### 2.1 `v0.2.0` Hardware, Lock-Screen Cockpit & Dual-Device Features
- [x] **Hardware `Sensor.TYPE_HINGE_ANGLE` (`0°..180°`) Live Readout**: Real-time hinge sensor listener integrated into `PostureState`, Top-Right Hero Header, Floating HUD Pill, and Tent Mode detection.
- [x] **Z Fold 8 Cover Display Landscape Classification Fix (`1248x1972` / `475dp x 751dp`)**: Ensures rotated cover screen (`min(widthDp, heightDp) < 580dp` with no unfolded `FoldingFeature` and `hingeAngle < 30°`) stays in `COMPACT` cover posture instead of misclassifying as `EXPANDED`.
- [x] **Interactive `PostureOverride` Switcher (`AUTO` / `FLEX` / `INNER` / `COVER` / `TENT`)**: Instant 1-tap posture mode override from the home header and System Tweaker.
- [x] **Z Fold 8 One UI 9.0 Lock Screen & Continuity Customization**:
  - `face_stay_on_lock_screen = 0` instant Face Unlock bypass to home screen.
  - `lock_application_shortcut` remapped to **VC** (`dev.agentconversation.vci`) and **Fold8 Launcher** (`com.xsun.foldlauncher`).
  - `TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED` Lock-Screen Micro-HUD Pill in `FoldAccessibilityService`.
  - `MainActivity` `setShowWhenLocked(true)` 120Hz Lock-Screen Cockpit.
- [x] **Multi-Instance VC Switcher (`PROD` / `PREVIEW` / `COMPARE`)**: Instant target selector across `dev.agentconversation.vci` (`PROD`), `dev.agentconversation.preview` (`PREVIEW`), and `dev.agentconversation.compare` (`COMPARE`).
- [x] **1-Tap Z Fold 8 Dual-Pane Split-Screen Combos**: `VC + Termux`, `Prod + Compare`, `Prod + Preview`, and `VC + Chrome` via `FLAG_ACTIVITY_LAUNCH_ADJACENT`.
- [x] **Inline Pending Approval Banner**: High-contrast actionable approval alert banner inside `VcCommandCenterCard`.
- [x] **120Hz Tent Mode Standby HUD (`TentModeStandbyHud`)**: Dedicated low-power AMOLED standby display with oversized clock, hinge angle, and live VC agent ticker.
- [x] **Flex-Mode Virtual Trackpad 2-Finger Scroll Strip**: Dedicated right-edge scroll strip and tap-to-click gestures in `FlexVirtualTrackpadCard`.
- [x] **App Drawer Category Filter Pills**: Instant horizontal category filtering (`All`, `VC & Dev`, `Frequent`, `System`).
- [x] **2D `px`-to-`dp` `@ 420dpi` Resolution Calculator**: Evaluates 2D pixel dimensions (`1248x1972 px to dp`, `1968x2184 px to dp`) in `OmniCommandEngine`.

### 2.2 Core & VC Non-Foreground Background Integration (`v0.1.0` Foundation)
- [x] **Real-Time Posture Continuity (`COMPACT`, `FLEX_TABLETOP`, `EXPANDED`, `TENT`)** without activity restarts.
- [x] **Oversized Top-Right Hero Clock & Live Posture Header**.
- [x] **0ms Swipe-Up Spotlight Search + Acronym Ranking + Inline Math**.
- [x] **Samsung DeX Gboard Auto-Guard & Non-Root Landscape Rotation**.
- [x] **VC Non-Foreground HTTP/JSON Bridge (`VcBridgeClient`) & Adaptive Monitor (`VcBackgroundMonitor`)** polling `/api/attention` and emitting smart unread notifications (`computeNewBackgroundAlerts`).
- [x] **120Hz System-Wide Floating Telemetry HUD Pill (`FloatingHudService`)** and **One-Handed Radial Thumb Arc (`RadialThumbArcOverlay`)**.

---

## 3. Unit Test Suite Inventory (`./gradlew testDebugUnitTest`)

All 5 pure-JVM unit test suites reside under `app/src/test/java/com/xsun/foldlauncher/` and run on every PR and push to `main`:

| Test Suite | Path | Coverage Scope | Status |
| :--- | :--- | :--- | :--- |
| **`SearchEngineTest`** | `app/src/test/java/com/xsun/foldlauncher/search/SearchEngineTest.kt` | Exact, prefix, multi-word acronym (`yt`, `gm`, `as`), substring, fuzzy subsequence scoring, launch count tie-breaking, and `evaluateInlineMath`. | Passing |
| **`PostureClassifierTest`** | `app/src/test/java/com/xsun/foldlauncher/posture/PostureClassifierTest.kt` | `COMPACT` (cover screen portrait & `751x475dp` Z Fold 8 cover landscape fix), `FLEX_TABLETOP` (horizontal `HALF_OPENED` hinge & sensor fallback), `EXPANDED` (unfolded 7.6"+ inner display), `TENT` mode, and `PostureOverride` behavior. | Passing |
| **`VcBridgeParserTest`** | `app/src/test/java/com/xsun/foldlauncher/vc/VcBridgeParserTest.kt` | Full `/api/attention` payload parsing, delta cursor merging (`reset: false` vs `reset: true`), read-acknowledgement transitions, `/api/snapshot` fallback parsing, and `computeNewBackgroundAlerts`. | Passing |
| **`OmniCommandEngineTest`** | `app/src/test/java/com/xsun/foldlauncher/omni/OmniCommandEngineTest.kt` | Omni-Command prefix detection, 1D & 2D `px`-to-`dp` `@ 420dpi` calculator (`1248x1972 px to dp`), unit converters, developer encodings, and system shortcuts. | Passing |
| **`SpatialMathTest`** | `app/src/test/java/com/xsun/foldlauncher/spatial/SpatialMathTest.kt` | Ergonomic thumb-arc polar-to-Cartesian layout coordinates, radial hit-testing, parallax pitch/roll clamping, and trackpad/scroll delta projection. | Passing |

---

## 4. CI & Dual-Device Verification Status

### 4.1 GitHub Actions CI & Releases (`katchum/fold-launcher`)
- **Workflow**: [`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml) (`check` job running `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` on Temurin JDK 17).
- **Releases**:
  - [`v0.1.0`](https://github.com/katchum/fold-launcher/releases/tag/v0.1.0): Initial 120Hz release (`7b8a02c6d253e9c055f11c50d2fa42557b45d63b`).
  - [`v0.2.0`](https://github.com/katchum/fold-launcher/releases/tag/v0.2.0): Hardware hinge sensor, Cover landscape fix, One UI 9.0 Lock-Screen Cockpit & Micro-HUD, Multi-Instance VC Switcher (`PROD`/`PREVIEW`/`COMPARE`), 4 Split-Screen combos, Tent Mode HUD, and 2D DPI calculator.

### 4.2 Dual-Device Live ADB Verification (`--user 0` Policy)
1. **Primary Foldable Target — Samsung Galaxy Z Fold 8 (`SM-F976U1`)**:
   - **Endpoint**: `100.101.43.86:42595` (One UI 9.0 / Android 16)
   - ** Displays Verified**: Cover Display (`1248x1972` / `475dp x 751dp` @ 420dpi) & Unfolded Inner Display (`1968x2184` @ 420dpi), Hardware Hinge Angle (`0°..180°`), One UI 9.0 Lock-Screen Shortcuts & Face Unlock bypass, Multi-Instance VC (`vci`, `preview`, `compare`) + Termux/Chrome split pairs.
2. **Primary Slab Target — Samsung Galaxy S23 Ultra (`SM-S918U1`)**:
   - **Endpoint**: `100.68.53.123:40919` (One UI 8.5 / Android 16)
   - **Features Verified**: 120Hz `COMPACT` slab layout, `PostureOverride` preview switcher (`AUTO`/`FLEX`/`INNER`/`COVER`/`TENT`), Floating Telemetry HUD, Radial Thumb Arc, and VC Background Monitor.
