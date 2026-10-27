# Fold8 & Slab Launcher (`com.xsun.foldlauncher`) — Project & Repo Health (`v0.3.0`)

**Repository**: [`katchum/fold-launcher`](https://github.com/katchum/fold-launcher)
**Workspace Path**: `/home/xsun/projects/android-launcher/fold-launcher`
**Current Version**: `v0.3.0` (`versionCode = 3`, `versionName = "0.3.0"`)
**Maintainer**: Repo Health & Project Health Lead

---

## 1. Module Architecture & Ownership Matrix

The codebase is organized under `app/src/main/java/com/xsun/foldlauncher/` into clean, decoupled packages with zero circular dependencies and strict main-thread safety:

| Module / Package | Key Source Files | Owning Lead / Specialist | Responsibility |
| :--- | :--- | :--- | :--- |
| **`model`** | `model/AppEntry.kt` | Core Architecture / Data Lead | Immutable domain models for installed apps (`AppEntry`), foldable split-screen app pairs (`SplitPair`), workspace categories (`All`, `VC & Dev`, `Frequent`, `System`), and `WorkspaceProfile` (`ALL`, `VC_DEV`, `FOCUS`, `OLED_BLACK`). |
| **`search`** | `search/SearchEngine.kt` | Search & Omni-Command Specialist | Zero-allocation Spotlight app ranking (exact, prefix, multi-word acronym, word-boundary, substring, fuzzy subsequence, frequency boost) and inline math expression evaluator (`evaluateInlineMath`). |
| **`posture` & `display`** | `posture/PostureState.kt`, `display/CoverScreenPresentation.kt` | Foldable Posture & Dual-Display Lead | Real-time `WindowInfoTracker` / `FoldingFeature` + hardware `Sensor.TYPE_HINGE_ANGLE` (`0°..180°`) tracking, Z Fold 8 `1248x1972` (`475dp x 751dp`) Cover Display landscape classification fix, interactive `PostureOverride` switcher (`AUTO`, `FLEX`, `INNER`, `COVER`, `TENT`), and Secondary Display OLED Companion (`display/CoverScreenPresentation.kt` & `DualScreenPresentationController` via `DisplayManager` + `android.app.Presentation`). |
| **`service`** | `service/FoldAccessibilityService.kt`, `service/SystemTweaker.kt` | System Services & One UI Tweaker Lead | Non-root Samsung DeX Gboard Auto-Guard, One UI 9.0 Lock Screen customization (`face_stay_on_lock_screen = 0`, `lock_application_shortcut` remapping), `KeyguardManager.requestDismissKeyguard` 1-tap `Unlock & Split VC + Termux`, zero-permission hardware flashlight (`CameraManager.setTorchMode`), live `HardwareTelemetrySnapshot` (thermal/battery current/RAM), and `TYPE_ACCESSIBILITY_OVERLAY` Lock-Screen Micro-HUD Pill. |
| **`vc`** | `vc/VcModels.kt`, `vc/VcBridgeClient.kt`, `vc/VcBackgroundMonitor.kt`, `vc/VcCommandCenterCard.kt` | VC Non-Foreground Bridge Specialist | Zero-dependency HTTP/JSON bridge to `vc-host`, Multi-Instance VC Switcher (`PROD` / `PREVIEW` / `COMPARE`), 1-tap Z Fold 8 Dual-Pane Split-Screen combos (`VC + Termux`, `Prod + Compare`, `Prod + Preview`, `VC + Chrome`), 1-Tap VC Prompt Macros (`VcPromptMacro`: `Status`, `Approve & Go`, `Verify CI/Tests`, `Ship Release`, `Keep Building`), Pinned Coordinator Session (`pinnedSessionId`), and inline Pending Approval Banner. |
| **`hud`** | `hud/FloatingHudOverlay.kt` | Telemetry & Hardware HUD Specialist | System-wide draggable 120Hz Floating HUD Pill (`TYPE_APPLICATION_OVERLAY`) and Lock-Screen Micro-HUD (`TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED`) displaying live FPS, RAM, battery temperature (°C) & current (mA), torch state, hinge angle (`0°..180°`), posture badge, and VC status dot. |
| **`spatial`** | `spatial/SpatialLauncherFeatures.kt` | 120Hz Spatial & Ergonomics Specialist | One-handed `RadialThumbArcOverlay`, 3D gyroscope tilt parallax (`spatialParallaxCard`), `FlexVirtualTrackpadCard` with 2-finger scroll strip, 120Hz `TentModeStandbyHud`, and AMOLED 3:2 Lissajous burn-in protection pixel-shift (`computeOledPixelShiftOffset`). |
| **`omni`** | `omni/OmniCommandEngine.kt` | Search & Omni-Command Specialist | Spotlight Omni-Command Palette parser & executor including `v0.3.0` commands (`> torch`, `> dual`, `> profile <all|vc|focus|oled>`, `vc: status|approve|ship|test`), 2D `px`-to-`dp` `@ 420dpi` calculator, unit/currency converters, and developer tools. |
| **`data`** | `data/AppRepository.kt`, `data/LauncherPreferences.kt` | Core Architecture / Data Lead | Asynchronous `LauncherApps` / `PackageManager` icon & metadata loader on `Dispatchers.IO`, live package broadcast updates, launch frequency, workspace profile, and `pinnedSessionId` persistence. |
| **`ui/theme`** | `ui/theme/Theme.kt` | 120Hz Compose UI Lead | True-AMOLED pitch-black (`#000000`) & glassmorphic dark palette (`ObsidianSurface`, `ElectricCyan`, `EmeraldSuccess`, `AmberWarning`, `CoralDanger`) tuned for zero OLED smear at 120Hz. |
| **`ui/animation`** | `ui/animation/LauncherAnimations.kt` | 120Hz Compose UI Lead | Critically damped spring specs (`DampingRatioNoBouncy`, `StiffnessMediumLow`) and posture transition animations engineered for sub-8.3ms frame times. |
| **`ui/components`** | `ui/components/TopStatusBar.kt`, `AppIconTile.kt`, `NativeWidgets.kt`, `SpotlightOverlay.kt`, `TaskbarDock.kt` | 120Hz Compose UI Lead | Oversized Top-Right Hero Clock, live `0°..180°` hinge badge, interactive `PostureOverride` & `WorkspaceProfile` chip bars, `LockScreenCockpitCard`, zero-IPC native widgets, 0ms swipe-up Spotlight overlay, and taskbar dock. |
| **`ui/screens`** | `ui/screens/LauncherScreens.kt` | 120Hz Compose UI Lead | Posture- and profile-adaptive home screens (`CompactCoverScreen`, `ExpandedInnerScreen`, `FlexTabletopScreen`, `TentModeStandbyHud`, `OLED_BLACK` canvas) and App Drawer Category Filter Pills (`All`, `VC & Dev`, `Frequent`, `System`). |
| **`viewmodel`** | `viewmodel/LauncherViewModel.kt`, `MainActivity.kt` | Core Architecture / Integration Lead | Unidirectional `StateFlow` state holder and `MainActivity` (`setShowWhenLocked(true)` Lock-Screen Cockpit & `DualScreenPresentationController` lifecycle owner) coordinating all launcher subsystems. |

---

## 2. Feature Checklist (`v0.1.0` + `v0.2.0` + `v0.3.0`)

### 2.1 `v0.3.0` Dual-Display OLED Companion, Lock-Screen Cockpit, Torch & VC Macros
- [x] **Z Fold 8 Secondary Display OLED Companion (`CoverScreenPresentation.kt` & `DualScreenPresentationController`)**: Uses `DisplayManager` + `android.app.Presentation` to render a live 120Hz AMOLED companion dashboard on the Z Fold 8 secondary/cover display while unfolded.
- [x] **Dedicated Lock-Screen Ambient Cockpit Card (`LockScreenCockpitCard`)**: Features `KeyguardManager.requestDismissKeyguard` 1-tap **`Unlock & Split VC + Termux`** and inline lock-screen VC approval controls.
- [x] **Zero-Permission Hardware Flashlight / Torch (`CameraManager.setTorchMode`) & Live Hardware Telemetry (`HardwareTelemetrySnapshot`)**: Instant camera torch toggle and real-time thermal (`°C`), battery current (`mA`), and RAM telemetry without `CAMERA` runtime permission prompts.
- [x] **Workspace Profiles (`ALL`, `VC_DEV`, `FOCUS`, `OLED_BLACK`)**: Instant workspace mode switching including a pure `#000000` `OLED_BLACK` canvas.
- [x] **1-Tap VC Prompt Macros (`VcPromptMacro`) & Pinned Coordinator Session (`pinnedSessionId`)**: Instant 1-tap macro chips (`Status`, `Approve & Go`, `Verify CI/Tests`, `Ship Release`, `Keep Building`) and persistent coordinator session pinning.
- [x] **AMOLED 3:2 Lissajous Burn-In Protection Pixel-Shift (`computeOledPixelShiftOffset`)**: Continuous bounded Lissajous drift for standby/lock-screen/secondary-display OLED elements.
- [x] **Omni-Bar `v0.3.0` Commands**: `> torch`, `> dual`, `> profile <all|vc|focus|oled>`, and `vc: status|approve|ship|test`.

### 2.2 `v0.2.0` & `v0.1.0` Foundation
- [x] **Hardware `Sensor.TYPE_HINGE_ANGLE` (`0°..180°`) Live Readout, Z Fold 8 Cover Display Landscape Fix (`1248x1972` / `475dp x 751dp`), and Interactive `PostureOverride` Switcher (`AUTO`/`FLEX`/`INNER`/`COVER`/`TENT`)**.
- [x] **One UI 9.0 Lock-Screen Customization (`face_stay_on_lock_screen = 0`, `lock_application_shortcut` remapping, `TYPE_ACCESSIBILITY_OVERLAY` Micro-HUD Pill)**.
- [x] **Multi-Instance VC Switcher (`PROD`/`PREVIEW`/`COMPARE`) & 1-Tap Split Combos (`VC+Termux`, `Prod+Compare`, `Prod+Preview`, `VC+Chrome`)**.
- [x] **120Hz `TentModeStandbyHud`, Flex 2-Finger Trackpad Scroll Strip, Category Filter Pills, and 2D `px`-to-`dp` `@ 420dpi` Calculator**.
- [x] **Samsung DeX Gboard Auto-Guard, Non-Root Landscape Rotation, Floating Telemetry HUD Pill, and Radial Thumb Arc**.

---

## 3. Unit Test Suite Inventory (`./gradlew testDebugUnitTest`)

All 5 pure-JVM unit test suites reside under `app/src/test/java/com/xsun/foldlauncher/` and run on every PR and push to `main`:

| Test Suite | Path | Coverage Scope | Status |
| :--- | :--- | :--- | :--- |
| **`SearchEngineTest`** | `app/src/test/java/com/xsun/foldlauncher/search/SearchEngineTest.kt` | Exact, prefix, multi-word acronym (`yt`, `gm`, `as`), substring, fuzzy subsequence scoring, launch count tie-breaking, and `evaluateInlineMath`. | Passing |
| **`PostureClassifierTest`** | `app/src/test/java/com/xsun/foldlauncher/posture/PostureClassifierTest.kt` | `COMPACT` (cover portrait & `751x475dp` Z Fold 8 cover landscape fix), `FLEX_TABLETOP`, `EXPANDED`, `TENT` mode, `PostureOverride`, and dual-display presentation eligibility. | Passing |
| **`VcBridgeParserTest`** | `app/src/test/java/com/xsun/foldlauncher/vc/VcBridgeParserTest.kt` | `/api/attention` full & delta parsing, `/api/snapshot` fallback, `computeNewBackgroundAlerts`, `VcPromptMacro` resolution, and `pinnedSessionId` target selection. | Passing |
| **`OmniCommandEngineTest`** | `app/src/test/java/com/xsun/foldlauncher/omni/OmniCommandEngineTest.kt` | `v0.3.0` commands (`> torch`, `> dual`, `> profile <all|vc|focus|oled>`, `vc: status|approve|ship|test`), 2D `px`-to-`dp` `@ 420dpi` calculator, unit converters, and developer encodings. | Passing |
| **`SpatialMathTest`** | `app/src/test/java/com/xsun/foldlauncher/spatial/SpatialMathTest.kt` | Ergonomic thumb-arc geometry, radial hit-testing, parallax pitch/roll clamping, trackpad scroll delta projection, and 3:2 Lissajous `computeOledPixelShiftOffset` burn-in protection bounds. | Passing |

---

## 4. CI & Dual-Device Verification Status

### 4.1 GitHub Actions CI & Releases (`katchum/fold-launcher`)
- **Workflow**: [`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml) (`check` job running `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` on Temurin JDK 17).
- **Published Releases**:
  - [`v0.1.0`](https://github.com/katchum/fold-launcher/releases/tag/v0.1.0): Initial 120Hz release (`7b8a02c6d253e9c055f11c50d2fa42557b45d63b`).
  - [`v0.2.0`](https://github.com/katchum/fold-launcher/releases/tag/v0.2.0): Hardware hinge sensor, Cover landscape fix, One UI 9.0 Lock-Screen Cockpit & Micro-HUD, Multi-Instance VC Switcher, and 4 Split-Screen combos (`2845680c8d6120019881fb6283e0d2528b2243f2`).
  - [`v0.3.0`](https://github.com/katchum/fold-launcher/releases/tag/v0.3.0): Secondary Display OLED Companion (`CoverScreenPresentation`), `LockScreenCockpitCard` (`Unlock & Split VC + Termux`), zero-permission Hardware Torch & live telemetry, Workspace Profiles (`ALL`/`VC_DEV`/`FOCUS`/`OLED_BLACK`), 1-Tap VC Prompt Macros & Pinned Coordinator Session, and 3:2 Lissajous burn-in pixel shift.

### 4.2 Dual-Device Live ADB Targets (`--user 0` Policy)
1. **Samsung Galaxy Z Fold 8 (`SM-F976U1` / `SM-F971U1`)**: `100.101.43.86:42595` (One UI 9.0 / Android 16)
2. **Samsung Galaxy S23 Ultra (`SM-S918U1`)**: `100.68.53.123:40919` (One UI 8.5 / Android 16)
