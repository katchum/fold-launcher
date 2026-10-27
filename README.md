# Fold8 Launcher (`com.xsun.foldlauncher`) — `v0.2.0`

**Ultra-Fast, Zero-Bloat 120Hz Adaptive Home Launcher, Lock-Screen Cockpit & VC Companion for Samsung Galaxy Z Fold 8 and Android Slab Phones**

[![Android CI](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml/badge.svg)](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml)

Fold8 Launcher is a lightweight, 120Hz-native Jetpack Compose home screen replacement and system companion engineered specifically for the **Samsung Galaxy Z Fold 8** (`SM-F976U1`, One UI 9.0 / Android 16) and **Android Slab Phones** (Samsung Galaxy S23 Ultra `SM-S918U1`, One UI 8.5 / Android 16).

> **Live Project & Architecture Health**: See [`docs/PROJECT-HEALTH.md`](docs/PROJECT-HEALTH.md) and [`docs/vc-launcher-integration.md`](docs/vc-launcher-integration.md).

---

## What's New in `v0.2.0`

### 1. Hardware Hinge-Angle Sensor (`0°..180°`), Cover Landscape Fix & Interactive Posture Switcher (`posture`)
- **Live `Sensor.TYPE_HINGE_ANGLE` Readout**: Reads hardware hinge angle (`0°..180°`) in real time and displays it in the Top-Right Hero Header and Floating HUD.
- **Z Fold 8 Cover Display Landscape Fix (`1248x1972` / `475dp x 751dp`)**: Accurately classifies the Z Fold 8 cover screen in landscape (`min(widthDp, heightDp) < 580dp` when `FoldingFeature == null` and `hingeAngle < 30°`) as `COMPACT` rather than `EXPANDED`.
- **Interactive `PostureOverride` Switcher**: 1-tap header selector (`AUTO` / `FLEX` / `INNER` / `COVER` / `TENT`) to force or preview any posture layout on either foldable or slab hardware.

### 2. Z Fold 8 One UI 9.0 Lock Screen & Continuity Cockpit (`service`, `hud`, `MainActivity`)
- **Instant Face Unlock Bypass**: Sets `face_stay_on_lock_screen = 0` via `WRITE_SECURE_SETTINGS` so Face Unlock goes straight to the workspace without swiping.
- **Lock Screen Corner Shortcut Remapping**: Configures `lock_application_shortcut` on One UI 9.0 to map the bottom-left and bottom-right lock screen shortcuts directly to **VC** (`dev.agentconversation.vci`) and **Fold8 Launcher** (`com.xsun.foldlauncher`).
- **Lock-Screen Micro-HUD Pill (`TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED`)**: Renders a non-intrusive live telemetry & VC status pill directly over the One UI 9.0 lock screen (`KeyguardManager.isKeyguardLocked`).
- **120Hz Lock-Screen Cockpit (`setShowWhenLocked(true)`)**: Allows launching the full 120Hz Fold8 Cockpit over the lock screen for instant status inspection and voice/agent control.

### 3. Multi-Instance VC Switcher & Z Fold 8 Dual-Pane Split Combos (`vc`)
- **Multi-Instance VC Switcher (`PROD` / `PREVIEW` / `COMPARE`)**: Switch seamlessly between `dev.agentconversation.vci` (`PROD`), `dev.agentconversation.preview` (`PREVIEW`), and `dev.agentconversation.compare` (`COMPARE`).
- **1-Tap Z Fold 8 Dual-Pane Split-Screen Combos**: Instant `FLAG_ACTIVITY_LAUNCH_ADJACENT` workspace pairs:
  - `VC + Termux`
  - `Prod + Compare`
  - `Prod + Preview`
  - `VC + Chrome`
- **Inline Pending Approval Banner**: High-visibility amber banner inside `VcCommandCenterCard` whenever `approvalCount > 0`.

### 4. Tent Mode Standby HUD, 2-Finger Trackpad Scroll, Category Pills & 2D DPI Calculator (`spatial`, `omni`, `ui`)
- **120Hz Tent Mode Standby HUD (`TentModeStandbyHud`)**: Dedicated low-glare OLED standby dashboard when propped in Tent posture (`30°..110°`).
- **Flex-Mode Virtual Trackpad 2-Finger Scroll Strip**: Adds a dedicated right-edge two-finger/scroll strip and tactile cursor acceleration to `FlexVirtualTrackpadCard`.
- **App Drawer Category Filter Pills**: Instant horizontal filter chips (`All`, `VC & Dev`, `Frequent`, `System`) across Spotlight and workspace grids.
- **2D `px`-to-`dp` `@ 420dpi` Calculator (`OmniCommandEngine`)**: Evaluates 2D resolution expressions like `1248x1972 px to dp` (`475dp x 751dp @ 420dpi`) and `1968x2184 px to dp` directly inside Spotlight.

---

## Core Architecture & Features

### 1. Adaptive Posture Engine (Galaxy Z Fold 8 & Slab Phones)
Powered by `androidx.window:window` (`WindowInfoTracker` & `FoldingFeature`), `Sensor.TYPE_HINGE_ANGLE`, and `PostureClassifier`, Fold8 Launcher adapts in real time without activity restarts:
- **Compact / Cover / Slab Mode (`COMPACT`)**: One-handed 4-column thumb-zone dock and scannable favorite list optimized for narrow cover screens and standard slab phones.
- **Flex / Tabletop Mode (`FLEX_TABLETOP`)**: Detects half-opened horizontal hinge postures (`FoldingFeature.State.HALF_OPENED` or hinge angle `75°..145°`), splitting the UI across the hinge crease with the clock & status header above the fold and interactive controls + virtual trackpad below.
- **Tent / Presentation Mode (`TENT` override / `30°..110°`)**: Renders `TentModeStandbyHud` with oversized OLED clock, VC live status, and media/system telemetry.
- **Expanded Inner Tablet / DeX Mode (`EXPANDED`)**: Dual-pane workspace (5–6 columns) with side-by-side favorites, quick-action cards, and one-tap **Split-Screen App Pair** launching (`FLAG_ACTIVITY_LAUNCH_ADJACENT`).

### 2. 0ms Swipe-Up Spotlight Search + Inline Math + Omni-Command Palette
- **Instant Gesture Trigger**: Swipe up anywhere on the home screen (or tap the search pill) to immediately open the Spotlight overlay and focus the keyboard with zero animation lag.
- **Smart Scoring & Shorthand Ranking (`SearchEngine`)**: Ranks installed apps using exact match, prefix match, multi-word acronym matching (`yt` → *YouTube*, `gm` → *Google Maps*), word-boundary matching, substring matching, fuzzy subsequence matching, and launch frequency boosting.
- **Inline Calculator & 2D `px`-to-`dp` Converter**: Evaluates mathematical expressions (`(2560 - 1080) / 2`) and 2D foldable display geometry (`1248x1972 px to dp @ 420dpi`) directly inside the search bar.

### 3. System Tweaker & Companion `FoldAccessibilityService`
Solves daily annoyances on Samsung One UI 8.5 / 9.0 without root:
- **Samsung DeX Gboard Auto-Guard**: Monitors `Settings.Secure.DEFAULT_INPUT_METHOD` and display configuration changes, automatically restoring **Gboard** and routing the DeX virtual keyboard to the phone screen (`dex_on_screen_keyboard_location = 1`).
- **Lock Screen & Home Screen Landscape Rotation**: One-tap toggle writing `lock_screen_allow_rotation = 1` and `accelerometer_rotation = 1`.
- **Zero-Drain Home Gestures**: Swipe down anywhere for notification shade (`GLOBAL_ACTION_NOTIFICATIONS`); double-tap empty space to lock screen (`GLOBAL_ACTION_LOCK_SCREEN`).

---

## One-Time Non-Root ADB Setup & Debloat Scripts

Fold8 Launcher includes two standalone, non-root scripts in [`scripts/`](scripts/) to configure your Galaxy Z Fold 8 or Android slab phone in seconds.

### 1. Grant Permissions & Configure One UI 9.0 Lock Screen / DeX / Rotation (`scripts/adb-setup-fold8.sh`)
Grants `android.permission.WRITE_SECURE_SETTINGS` to both debug and release builds, enables lock screen landscape rotation, configures instant Face Unlock (`face_stay_on_lock_screen = 0`), remaps lock screen shortcuts (`lock_application_shortcut`) to VC and Fold8 Launcher, configures DeX keyboard routing for Gboard, and sets Gboard as the default IME:

```bash
chmod +x scripts/adb-setup-fold8.sh
./scripts/adb-setup-fold8.sh

# Optional: also disable Samsung Honeyboard for user 0 so One UI can never fall back to it
./scripts/adb-setup-fold8.sh --disable-honeyboard
```

### 2. Safe Non-Root Samsung Bloatware Removal (`scripts/adb-debloat-fold8.sh`)
Removes background battery hogs (Bixby wakeup agents, Samsung Free/SPage, Customization Service telemetry, AR Zone stickers, GOS/Game Launcher throttling, preinstalled Meta/Microsoft stubs) for `user 0` while **strictly preserving Samsung DeX, Knox/Wallet/Pass, Galaxy AI, S Pen, Foldable Continuity, and Good Lock**:

```bash
chmod +x scripts/adb-debloat-fold8.sh
./scripts/adb-debloat-fold8.sh
```

---

## Building & Installing

### Build Commands
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

### Output APK Locations
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK (R8 Minified, ADB-installable)**: `app/build/outputs/apk/release/app-release.apk`
