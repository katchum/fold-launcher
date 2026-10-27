# Fold8 Launcher (`com.xsun.foldlauncher`) — `v0.3.0`

**Ultra-Fast, Zero-Bloat 120Hz Adaptive Home Launcher, Dual-Display OLED Companion, Lock-Screen Cockpit & VC Command Center for Samsung Galaxy Z Fold 8 and Android Slab Phones**

[![Android CI](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml/badge.svg)](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml)

Fold8 Launcher is a lightweight, 120Hz-native Jetpack Compose home screen replacement and system companion engineered specifically for the **Samsung Galaxy Z Fold 8** (`SM-F976U1` / `SM-F971U1`, One UI 9.0 / Android 16) and **Android Slab Phones** (Samsung Galaxy S23 Ultra `SM-S918U1`, One UI 8.5 / Android 16).

> **Live Project & Architecture Health**: See [`docs/PROJECT-HEALTH.md`](docs/PROJECT-HEALTH.md) and [`docs/vc-launcher-integration.md`](docs/vc-launcher-integration.md).

---

## What's New in `v0.3.0`

### 1. Z Fold 8 Secondary Display OLED Companion (`CoverScreenPresentation.kt` & `DualScreenPresentationController`)
- **Dual-Display `android.app.Presentation` Engine**: Uses `DisplayManager` to detect secondary displays on the unfolded Galaxy Z Fold 8 and drives a dedicated **120Hz Cover-Screen OLED Companion** (`CoverScreenPresentation`) while you work on the 7.6"+ inner display.
- Displays a zero-glare `#000000` AMOLED Hero Clock, live hinge angle (`0°..180°`), hardware thermal/battery current telemetry, and live VC agent status (`Working`, `To Approve`, `Unread`) on the rear/cover screen.

### 2. Dedicated Lock-Screen Ambient Cockpit Card (`LockScreenCockpitCard`)
- **1-Tap Keyguard Dismiss & Split Workspace (`KeyguardManager.requestDismissKeyguard`)**: Directly from the lock screen, tap **`Unlock & Split VC + Termux`** to trigger biometric/credential dismissal and immediately launch VC + Termux side-by-side (`FLAG_ACTIVITY_LAUNCH_ADJACENT`).
- **Inline Lock-Screen VC Approval**: Inspect and approve/acknowledge pending VC agent actions right on the lock screen without navigating through app drawers.

### 3. Zero-Permission Hardware Torch (`CameraManager.setTorchMode`) & Live Hardware Telemetry (`HardwareTelemetrySnapshot`)
- **Instant Hardware Flashlight / Torch**: Zero-permission `CameraManager.setTorchMode` integration (`SystemTweaker`) controllable from the Quick Action cards, Floating HUD Pill, Lock-Screen Cockpit, and Omni-Bar (`> torch`).
- **Live Hardware Telemetry (`HardwareTelemetrySnapshot`)**: Real-time battery current (`mA` via `BatteryManager.BATTERY_PROPERTY_current_now`), thermal reading (`°C`), available/total RAM (`ActivityManager.MemoryInfo`), and 120Hz frame pacing.

### 4. Workspace Profiles (`ALL`, `VC_DEV`, `FOCUS`, `OLED_BLACK`)
- Switch the entire launcher workspace in 1 tap or via Omni-Bar (`> profile <all|vc|focus|oled>`):
  - **`ALL`**: Full dual-hemisphere dashboard, widgets, and categorized app grid.
  - **`VC_DEV`**: Prioritizes VC Command Center, dev/terminal tools (`VC`, `Termux`, `ADB`, `Chrome`), and multi-instance split combos.
  - **`FOCUS`**: Minimalist distraction-free layout hiding non-essential cards and social clutter.
  - **`OLED_BLACK`**: Pure `#000000` zero-smear AMOLED canvas with burn-in pixel shift for maximum battery savings on the 7.6"+ inner display.

### 5. 1-Tap VC Prompt Macros (`VcPromptMacro`) & Pinned Coordinator Session (`pinnedSessionId`)
- **1-Tap Prompt Macros**: Dispatch common agent instructions in a single tap from `VcCommandCenterCard`:
  - `Status`
  - `Approve & Go`
  - `Verify CI/Tests`
  - `Ship Release`
  - `Keep Building`
- **Pinned Coordinator Session (`pinnedSessionId`)**: Pin your primary Coordinator/General session so 1-tap macros, quick-send messages, and Omni-Bar `vc:` commands always route to the right session even when multiple sub-agents are active.

### 6. AMOLED 3:2 Lissajous Burn-In Protection Pixel-Shift (`computeOledPixelShiftOffset`)
- Continuously computes a bounded 3:2 Lissajous sub-pixel/pixel trajectory (`computeOledPixelShiftOffset`) for persistent OLED HUD elements (Tent Mode Standby HUD, Cover Screen Presentation, and Lock-Screen Micro-HUD) to prevent static sub-pixel wear on LTPO AMOLED panels.

### 7. Omni-Bar `v0.3.0` Commands (`OmniCommandEngine`)
- **`> torch`** (or `flashlight`): Toggles hardware camera torch via `CameraManager.setTorchMode`.
- **`> dual`**: Toggles the Z Fold 8 Secondary Display OLED Companion (`DualScreenPresentationController`).
- **`> profile <all|vc|focus|oled>`**: Switches the active `WorkspaceProfile`.
- **`vc: status|approve|ship|test`**: Dispatches instant `VcPromptMacro` commands directly from Spotlight to the pinned or active VC session.

---

## Highlights from `v0.2.0` & `v0.1.0`

- **Hardware `Sensor.TYPE_HINGE_ANGLE` (`0°..180°`), Z Fold 8 Cover Landscape Fix (`1248x1972` / `475dp x 751dp`), and Interactive `PostureOverride` Switcher (`AUTO` / `FLEX` / `INNER` / `COVER` / `TENT`)**.
- **Z Fold 8 One UI 9.0 Lock Screen & Continuity**: Instant Face Unlock bypass (`face_stay_on_lock_screen = 0`), lock-screen shortcut remapping (`lock_application_shortcut`) to VC and Fold8 Launcher, and `TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED` Lock-Screen Micro-HUD Pill.
- **Multi-Instance VC Switcher (`PROD` / `PREVIEW` / `COMPARE`) & 1-Tap Split Combos**: `VC + Termux`, `Prod + Compare`, `Prod + Preview`, and `VC + Chrome`.
- **120Hz Tent Mode Standby HUD (`TentModeStandbyHud`), Flex 2-Finger Trackpad Scroll Strip, Category Filter Pills, and 2D `px`-to-`dp` `@ 420dpi` Calculator**.
- **Samsung DeX Gboard Auto-Guard (`FoldAccessibilityService`) & Non-Root Landscape Rotation**.

---

## One-Time Non-Root ADB Setup & Debloat Scripts

### 1. Grant Permissions & Configure One UI 9.0 Lock Screen / DeX / Rotation (`scripts/adb-setup-fold8.sh`)
```bash
chmod +x scripts/adb-setup-fold8.sh
./scripts/adb-setup-fold8.sh

# Optional: also disable Samsung Honeyboard for user 0 so One UI can never fall back to it
./scripts/adb-setup-fold8.sh --disable-honeyboard
```

### 2. Safe Non-Root Samsung Bloatware Removal (`scripts/adb-debloat-fold8.sh`)
```bash
chmod +x scripts/adb-debloat-fold8.sh
./scripts/adb-debloat-fold8.sh
```

---

## Building & Installing

```bash
# Run all 5 unit test suites (PostureClassifierTest, SearchEngineTest, VcBridgeParserTest, OmniCommandEngineTest, SpatialMathTest)
./gradlew testDebugUnitTest

# Run Android Lint checks
./gradlew lintDebug

# Build Debug APK (package: com.xsun.foldlauncher.debug)
./gradlew assembleDebug

# Build R8-minified, resource-shrunk Release APK (package: com.xsun.foldlauncher)
./gradlew assembleRelease
```
