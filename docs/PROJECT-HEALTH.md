# Fold8 & Slab Launcher (`com.xsun.foldlauncher`) — Project & Repo Health (`v0.4.0`)

**Repository**: [`katchum/fold-launcher`](https://github.com/katchum/fold-launcher)
**Workspace Path**: `/home/xsun/projects/android-launcher/fold-launcher`
**Current Version**: `v0.4.0` (`versionCode = 4`, `versionName = "0.4.0"`)
**Maintainer**: Repo Health & Project Health Lead

---

## 1. Module Architecture & Ownership Matrix

The codebase is organized under `app/src/main/java/com/xsun/foldlauncher/` into clean, decoupled packages with zero circular dependencies and strict main-thread safety:

| Module / Package | Key Source Files | Owning Lead / Specialist | Responsibility |
| :--- | :--- | :--- | :--- |
| **`model`** | `model/AppEntry.kt` | Core Architecture / Data Lead | Immutable domain models for installed apps (`AppEntry`), foldable split-screen app pairs (`SplitPair`), workspace categories (`All`, `VC & Dev`, `Frequent`, `System`), and `WorkspaceProfile` (`ALL`, `VC_DEV`, `FOCUS`, `OLED_BLACK`). |
| **`search`** | `search/SearchEngine.kt` | Search & Omni-Command Specialist | Zero-allocation Spotlight app ranking (exact, prefix, multi-word acronym, word-boundary, substring, fuzzy subsequence, frequency boost) and inline math expression evaluator (`evaluateInlineMath`). |
| **`posture` & `display`** | `posture/PostureState.kt`, `display/CoverScreenPresentation.kt` | Foldable Posture & Dual-Display Lead | Real-time `WindowInfoTracker` / `FoldingFeature` + hardware `Sensor.TYPE_HINGE_ANGLE` (`0°..180°`) tracking, Z Fold 8 `1248x1972` (`475dp x 751dp`) Cover Display landscape classification fix, interactive `PostureOverride` switcher, Mechanical Watch-Crown Hinge Haptic Detent detection (`HingeDetentTick`, `computeHingeDetentCrossed`), Fold-Close transition detection (`FoldCloseActionMode`, `isFoldCloseTransition`), and Secondary Display OLED Companion (`display/CoverScreenPresentation.kt` & `DualScreenPresentationController`). |
| **`service`** | `service/FoldAccessibilityService.kt`, `service/SystemTweaker.kt` | System Services & One UI Tweaker Lead | Non-root Samsung DeX Gboard Auto-Guard, Watch-Crown Hinge Haptic actuator (`triggerHingeDetentHaptic` firing `EFFECT_CLICK` at `15°`/`75°`/`155°` and `EFFECT_TICK` at `15°` increments), Fold-Close Action Router (`STAY_AWAKE_COVER`, `AUTO_VC_VOICE`, `TENT_STANDBY`, `INSTANT_LOCK`), Termux/Shell snippet & split-screen dispatcher, One UI 9.0 Lock Screen customization, zero-permission Torch (`CameraManager.setTorchMode`), and live `HardwareTelemetrySnapshot`. |
| **`vc`** | `vc/VcModels.kt`, `vc/VcBridgeClient.kt`, `vc/VcBackgroundMonitor.kt`, `vc/VcCommandCenterCard.kt` | VC Non-Foreground Bridge Specialist | Zero-dependency HTTP/JSON bridge to `vc-host`, Multi-Agent Squad Telemetry (`VcSquadTelemetry`, `VcSquadTelemetryBar` segmented progress bar), 1-Tap Markdown Status Copy (`buildMarkdownStatusSnippet`, `📋 Copy Status`), Multi-Instance VC Switcher (`PROD`/`PREVIEW`/`COMPARE`), 1-tap Z Fold 8 Dual-Pane Split-Screen combos, 1-Tap VC Prompt Macros (`VcPromptMacro`), and Pinned Coordinator Session (`pinnedSessionId`). |
| **`hud`** | `hud/FloatingHudOverlay.kt` | Telemetry & Hardware HUD Specialist | System-wide draggable 120Hz Floating HUD Pill (`TYPE_APPLICATION_OVERLAY`) and Lock-Screen Micro-HUD (`TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED`) displaying live FPS, RAM, battery temperature (°C) & current (mA), torch state, hinge angle (`0°..180°`), posture badge, and VC status dot. |
| **`spatial`** | `spatial/SpatialLauncherFeatures.kt` | 120Hz Spatial & Ergonomics Specialist | One-handed `RadialThumbArcOverlay`, 3D gyroscope tilt parallax (`spatialParallaxCard`), `FlexVirtualTrackpadCard` with 2-finger scroll strip, 120Hz `TentModeStandbyHud`, and AMOLED 3:2 Lissajous burn-in protection pixel-shift (`computeOledPixelShiftOffset`). |
| **`omni`** | `omni/OmniCommandEngine.kt` | Search & Omni-Command Specialist | Spotlight Omni-Command Palette parser & executor including `v0.4.0` Termux/Shell commands (`termux: <cmd>`, `sh: <cmd>`, `termux-split: <cmd>`) and Hinge/Fold-Close commands (`> haptics`, `> detents`, `> fold <stay|voice|tent|lock>`), `v0.3.0` commands (`> torch`, `> dual`, `> profile`, `vc:`), and 2D `px`-to-`dp` `@ 420dpi` calculator. |
| **`data`** | `data/AppRepository.kt`, `data/LauncherPreferences.kt` | Core Architecture / Data Lead | Asynchronous `LauncherApps` / `PackageManager` icon & metadata loader on `Dispatchers.IO`, live package broadcast updates, persistent `pinnedDockPackages`, `hingeDetentHapticsEnabled`, `foldCloseActionMode`, `workspaceProfile`, and `pinnedSessionId`. |
| **`ui/theme`** | `ui/theme/Theme.kt` | 120Hz Compose UI Lead | True-AMOLED pitch-black (`#000000`) & glassmorphic dark palette (`ObsidianSurface`, `ElectricCyan`, `EmeraldSuccess`, `AmberWarning`, `CoralDanger`) tuned for zero OLED smear at 120Hz. |
| **`ui/animation`** | `ui/animation/LauncherAnimations.kt` | 120Hz Compose UI Lead | Critically damped spring specs (`DampingRatioNoBouncy`, `StiffnessMediumLow`) and posture transition animations engineered for sub-8.3ms frame times. |
| **`ui/components`** | `ui/components/TopStatusBar.kt`, `AppIconTile.kt`, `NativeWidgets.kt`, `SpotlightOverlay.kt`, `TaskbarDock.kt` | 120Hz Compose UI Lead | Oversized Top-Right Hero Clock, interactive `📌`/`📍` Pin-to-Dock button on App Drawer tiles (`AppIconTile.kt`), Hinge Haptics & Fold-Close Action Mode selectors (`NativeWidgets.kt`), `LockScreenCockpitCard`, and custom-pinned `TaskbarDock`. |
| **`ui/screens`** | `ui/screens/LauncherScreens.kt` | 120Hz Compose UI Lead | Posture- and profile-adaptive home screens (`CompactCoverScreen`, `ExpandedInnerScreen`, `FlexTabletopScreen`, `TentModeStandbyHud`, `OLED_BLACK` canvas) with custom dock pinning and category filter pills. |
| **`viewmodel`** | `viewmodel/LauncherViewModel.kt`, `MainActivity.kt` | Core Architecture / Integration Lead | Unidirectional `StateFlow` state holder and `MainActivity` orchestrating hinge detent haptics, fold-close routing, custom dock pinning, VC squad telemetry, and dual-screen presentation lifecycle. |

---

## 2. Feature Checklist (`v0.1.0` – `v0.4.0`)

### 2.1 `v0.4.0` Watch-Crown Hinge Haptics, Fold-Close Router, Custom Dock Pinning & Squad Telemetry
- [x] **Mechanical Watch-Crown Hinge Haptic Detents (`HingeDetentTick`, `computeHingeDetentCrossed`, `triggerHingeDetentHaptic`)**: Fires `EFFECT_CLICK` at major posture thresholds (`15°`, `75°`, `155°`) and `EFFECT_TICK` at `15°` watch-crown increments (`30°`, `45°`, `60°`, `90°`, `105°`, `120°`, `135°`, `170°`).
- [x] **Configurable Fold-Close Action Router (`FoldCloseActionMode` & `isFoldCloseTransition`)**: Routes physical fold-close transitions (`>= 45°` to `< 15°`) to `STAY_AWAKE_COVER`, `AUTO_VC_VOICE`, `TENT_STANDBY`, or `INSTANT_LOCK`.
- [x] **Interactive Custom Dock Pinning (`pinnedDockPackages` + 1-Tap `📌`/`📍` Button)**: Pin or unpin any app directly from App Drawer and workspace tiles into the persistent `TaskbarDock`.
- [x] **Multi-Agent Squad Telemetry Bar (`VcSquadTelemetry`, `VcSquadTelemetryBar`) & 1-Tap Markdown Status Copy (`buildMarkdownStatusSnippet`, `📋 Copy Status`)**: Real-time segmented squad progress bar and 1-tap Markdown status clipboard export.
- [x] **Omni-Bar Termux / Shell Snippet Dispatcher & Hinge Commands**: `termux: <cmd>`, `sh: <cmd>`, `termux-split: <cmd>`, `> haptics`, `> detents`, and `> fold <stay|voice|tent|lock>`.

### 2.2 `v0.3.0`, `v0.2.0` & `v0.1.0` Foundation
- [x] **Z Fold 8 Secondary Display OLED Companion (`CoverScreenPresentation.kt` & `DualScreenPresentationController`)**.
- [x] **Dedicated Lock-Screen Ambient Cockpit Card (`LockScreenCockpitCard`, `Unlock & Split VC + Termux`, inline VC approval)**.
- [x] **Zero-Permission Hardware Flashlight (`CameraManager.setTorchMode`) & Live Hardware Telemetry (`HardwareTelemetrySnapshot`)**.
- [x] **Workspace Profiles (`ALL`, `VC_DEV`, `FOCUS`, `OLED_BLACK`) & 3:2 Lissajous Burn-In Pixel-Shift (`computeOledPixelShiftOffset`)**.
- [x] **1-Tap VC Prompt Macros (`VcPromptMacro`), Pinned Coordinator Session (`pinnedSessionId`), Multi-Instance VC Switcher (`PROD`/`PREVIEW`/`COMPARE`), and 4 Split-Screen Combos**.
- [x] **Hardware `Sensor.TYPE_HINGE_ANGLE` (`0°..180°`), Z Fold 8 Cover Landscape Fix (`1248x1972` / `475dp x 751dp`), and Interactive `PostureOverride` Switcher**.

---

## 3. Unit Test Suite Inventory (`./gradlew testDebugUnitTest`)

All 5 pure-JVM unit test suites reside under `app/src/test/java/com/xsun/foldlauncher/` and run on every PR and push to `main`:

| Test Suite | Path | Coverage Scope | Status |
| :--- | :--- | :--- | :--- |
| **`SearchEngineTest`** | `app/src/test/java/com/xsun/foldlauncher/search/SearchEngineTest.kt` | Exact, prefix, multi-word acronym (`yt`, `gm`, `as`), substring, fuzzy subsequence scoring, launch count tie-breaking, and `evaluateInlineMath`. | Passing |
| **`PostureClassifierTest`** | `app/src/test/java/com/xsun/foldlauncher/posture/PostureClassifierTest.kt` | `COMPACT` (cover portrait & `751x475dp` Z Fold 8 cover landscape fix), `FLEX_TABLETOP`, `EXPANDED`, `TENT` mode, `computeHingeDetentCrossed` major/minor detents, and `isFoldCloseTransition` routing. | Passing |
| **`VcBridgeParserTest`** | `app/src/test/java/com/xsun/foldlauncher/vc/VcBridgeParserTest.kt` | `/api/attention` full & delta parsing, `/api/snapshot` fallback, `computeNewBackgroundAlerts`, `VcPromptMacro`, `VcSquadTelemetry` computation, and `buildMarkdownStatusSnippet`. | Passing |
| **`OmniCommandEngineTest`** | `app/src/test/java/com/xsun/foldlauncher/omni/OmniCommandEngineTest.kt` | `v0.4.0` commands (`termux:`, `sh:`, `termux-split:`, `> haptics`, `> detents`, `> fold`), `v0.3.0` commands (`> torch`, `> dual`, `> profile`, `vc:`), and 2D `px`-to-`dp` `@ 420dpi` calculator. | Passing |
| **`SpatialMathTest`** | `app/src/test/java/com/xsun/foldlauncher/spatial/SpatialMathTest.kt` | Ergonomic thumb-arc geometry, radial hit-testing, parallax pitch/roll clamping, trackpad scroll delta projection, and 3:2 Lissajous `computeOledPixelShiftOffset` bounds. | Passing |

---

## 4. CI & Dual-Device Verification Status

### 4.1 GitHub Actions CI & Releases (`katchum/fold-launcher`)
- **Workflow**: [`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml) (`check` job running `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` on Temurin JDK 17).
- **Published Releases**:
  - [`v0.1.0`](https://github.com/katchum/fold-launcher/releases/tag/v0.1.0) (`7b8a02c6d253e9c055f11c50d2fa42557b45d63b`)
  - [`v0.2.0`](https://github.com/katchum/fold-launcher/releases/tag/v0.2.0) (`2845680c8d6120019881fb6283e0d2528b2243f2`)
  - [`v0.3.0`](https://github.com/katchum/fold-launcher/releases/tag/v0.3.0) (`7e5d2122645a93fb3b85fb649661717b90b2a840`)
  - [`v0.4.0`](https://github.com/katchum/fold-launcher/releases/tag/v0.4.0): Mechanical Watch-Crown Hinge Haptic Detents, Fold-Close Action Router, Interactive Custom Dock Pinning, Multi-Agent Squad Telemetry Bar & Markdown Status Copy, and Omni-Bar Termux/Shell Snippet Dispatcher.

### 4.2 Dual-Device Live ADB Targets (`--user 0` Policy)
1. **Samsung Galaxy Z Fold 8 (`SM-F976U1` / `SM-F971U1`)**: `100.101.43.86:42595` (One UI 9.0 / Android 16)
2. **Samsung Galaxy S23 Ultra (`SM-S918U1`)**: `100.68.53.123:40919` (One UI 8.5 / Android 16)
