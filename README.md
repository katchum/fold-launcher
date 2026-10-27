# Fold8 Launcher (`com.xsun.foldlauncher`) — `v0.4.0`

**Ultra-Fast, Zero-Bloat 120Hz Adaptive Home Launcher, Watch-Crown Hinge Haptics, Fold-Close Router, Dual-Display OLED Companion & VC Squad Command Center for Samsung Galaxy Z Fold 8 and Android Slab Phones**

[![Android CI](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml/badge.svg)](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml)

Fold8 Launcher is a lightweight, 120Hz-native Jetpack Compose home screen replacement and system companion engineered specifically for the **Samsung Galaxy Z Fold 8** (`SM-F976U1` / `SM-F971U1`, One UI 9.0 / Android 16) and **Android Slab Phones** (Samsung Galaxy S23 Ultra `SM-S918U1`, One UI 8.5 / Android 16).

> **Live Project & Architecture Health**: See [`docs/PROJECT-HEALTH.md`](docs/PROJECT-HEALTH.md) and [`docs/vc-launcher-integration.md`](docs/vc-launcher-integration.md).

---

## What's New in `v0.4.0`

### 1. Mechanical Watch-Crown Hinge Haptic Detents (`HingeDetentTick`, `computeHingeDetentCrossed`, `triggerHingeDetentHaptic`)
- **Tactile Hinge Feedback**: Turns the Galaxy Z Fold 8 physical hinge into a precision mechanical watch crown as you open or close the display (`0°..180°`):
  - **Major Posture Detents (`15°`, `75°`, `155°`)**: Fires crisp `VibrationEffect.EFFECT_CLICK` haptics when crossing the Cover (`15°`), Flex/Tent (`75°`), and Unfolded Inner (`155°`) posture thresholds.
  - **Minor Watch-Crown Ticks (every `15°` increment)**: Fires subtle `VibrationEffect.EFFECT_TICK` detents across `30°`, `45°`, `60°`, `90°`, `105°`, `120°`, `135°`, and `170°`.
- Toggleable in 1 tap from the System Tweaker card or via Omni-Bar (`> haptics` / `> detents`).

### 2. Configurable Fold-Close Action Router (`FoldCloseActionMode` & `isFoldCloseTransition`)
- **Smart Hinge-Close Continuity (`isFoldCloseTransition`)**: Detects when the Galaxy Z Fold 8 hinge transitions from unfolded/flex (`>= 45°`) to closed (`< 15°`) and executes your chosen `FoldCloseActionMode`:
  - **`STAY_AWAKE_COVER`**: Smoothly transitions to the Cover Screen (`COMPACT` mode) without locking.
  - **`AUTO_VC_VOICE`**: Automatically launches VC Voice Talk (`dev.agentconversation.widget.TALK`) on the Cover Screen so you can keep talking to your agents one-handed while walking.
  - **`TENT_STANDBY`**: Switches into the low-power `#000000` AMOLED `TentModeStandbyHud`.
  - **`INSTANT_LOCK`**: Immediately locks the device via `FoldAccessibilityService` (`GLOBAL_ACTION_LOCK_SCREEN`).
- Configurable from the System Tweaker card or via Omni-Bar (`> fold <stay|voice|tent|lock>`).

### 3. Interactive Custom Dock Pinning (`pinnedDockPackages`)
- **1-Tap Pin-to-Dock (`📌` / `📍`)**: Every app tile in the App Drawer and workspace grid now includes an interactive Pin-to-Dock toggle badge (`📌`/`📍`) backed by persistent `pinnedDockPackages` in `LauncherPreferences`, letting you customize your persistent 120Hz `TaskbarDock` on the fly.

### 4. Multi-Agent Squad Telemetry Bar (`VcSquadTelemetry` & `VcSquadTelemetryBar`) & 1-Tap Markdown Status Copy (`buildMarkdownStatusSnippet`)
- **`VcSquadTelemetryBar`**: Segmented 120Hz visual progress bar inside `VcCommandCenterCard` showing real-time squad distribution across **Approval Needed** (Amber), **Working** (Cyan), **Failed** (Coral), and **Idle/Completed** (Emerald) agents.
- **1-Tap `📋 Copy Status` (`buildMarkdownStatusSnippet`)**: Generates a clean, structured Markdown status report of all active VC agents, pending approvals, and unread attention items and copies it straight to the system clipboard.

### 5. Omni-Bar Termux / Shell Snippet Dispatcher & Hinge Commands (`OmniCommandEngine`)
- **Termux & Shell Snippet Dispatcher**:
  - `termux: <cmd>` / `sh: <cmd>`: Copies the shell snippet to the clipboard and launches Termux (`com.termux`) ready to paste/execute.
  - `termux-split: <cmd>`: Copies the shell snippet and launches **VC + Termux** side-by-side in Z Fold 8 dual-pane split-screen mode (`FLAG_ACTIVITY_LAUNCH_ADJACENT`).
- **Hinge & Fold-Close Commands**:
  - `> haptics` or `> detents`: Toggles Mechanical Watch-Crown Hinge Haptic Detents.
  - `> fold <stay|voice|tent|lock>`: Sets the active `FoldCloseActionMode`.

---

## Highlights from `v0.3.0`, `v0.2.0` & `v0.1.0`

- **Z Fold 8 Secondary Display OLED Companion (`CoverScreenPresentation.kt` & `DualScreenPresentationController`)**: Drives a 120Hz `#000000` AMOLED companion HUD on the secondary display (`DisplayManager` + `android.app.Presentation`) while unfolded.
- **Dedicated Lock-Screen Ambient Cockpit Card (`LockScreenCockpitCard`)**: `KeyguardManager.requestDismissKeyguard` 1-tap **`Unlock & Split VC + Termux`** and inline lock-screen VC approval.
- **Zero-Permission Hardware Torch (`CameraManager.setTorchMode`) & Live Hardware Telemetry (`HardwareTelemetrySnapshot`)**.
- **Workspace Profiles (`ALL`, `VC_DEV`, `FOCUS`, `OLED_BLACK`) & 3:2 Lissajous Burn-In Pixel-Shift (`computeOledPixelShiftOffset`)**.
- **1-Tap VC Prompt Macros (`Status`, `Approve & Go`, `Verify CI/Tests`, `Ship Release`, `Keep Building`), Pinned Coordinator Session (`pinnedSessionId`), Multi-Instance VC Switcher (`PROD`/`PREVIEW`/`COMPARE`), and 4 Split-Screen Combos (`VC+Termux`, `Prod+Compare`, `Prod+Preview`, `VC+Chrome`)**.
- **Hardware `Sensor.TYPE_HINGE_ANGLE` (`0°..180°`), Z Fold 8 Cover Landscape Fix (`1248x1972` / `475dp x 751dp`), and Interactive `PostureOverride` Switcher (`AUTO`/`FLEX`/`INNER`/`COVER`/`TENT`)**.
- **Samsung DeX Gboard Auto-Guard (`FoldAccessibilityService`) & Non-Root Landscape Rotation**.

---

## One-Time Non-Root ADB Setup & Debloat Scripts

```bash
chmod +x scripts/adb-setup-fold8.sh scripts/adb-debloat-fold8.sh
./scripts/adb-setup-fold8.sh
./scripts/adb-debloat-fold8.sh
```

---

## Building & Installing

```bash
# Run all 5 unit test suites (PostureClassifierTest, SearchEngineTest, VcBridgeParserTest, OmniCommandEngineTest, SpatialMathTest)
./gradlew testDebugUnitTest

# Run Android Lint checks
./gradlew lintDebug

# Build Debug & R8-Minified Release APKs
./gradlew assembleDebug assembleRelease
```
