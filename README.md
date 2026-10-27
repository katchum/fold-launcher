# Fold8 Launcher (`com.xsun.foldlauncher`) — `v0.5.0`

**Ultra-Fast, Zero-Bloat 120Hz Adaptive Home Launcher, Unfold-Open Auto-Split Continuity, Tailscale Mesh VPN Telemetry, Scratchpad Context Injector & VC Squad Command Center for Samsung Galaxy Z Fold 8 and Android Slab Phones**

[![Android CI](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml/badge.svg)](https://github.com/katchum/fold-launcher/actions/workflows/android-ci.yml)

Fold8 Launcher is a lightweight, 120Hz-native Jetpack Compose home screen replacement and system companion engineered specifically for the **Samsung Galaxy Z Fold 8** (`SM-F976U1` / `SM-F971U1`, One UI 9.0 / Android 16) and **Android Slab Phones** (Samsung Galaxy S23 Ultra `SM-S918U1`, One UI 8.5 / Android 16).

> **Live Project & Architecture Health**: See [`docs/PROJECT-HEALTH.md`](docs/PROJECT-HEALTH.md) and [`docs/vc-launcher-integration.md`](docs/vc-launcher-integration.md).

---

## What's New in `v0.5.0`

### 1. Unfold-Open Auto-Split Continuity (`UnfoldActionMode` & `isUnfoldOpenTransition`)
- **Automatic Unfold-to-Workspace Handoff (`isUnfoldOpenTransition`)**: Detects when the Galaxy Z Fold 8 hinge opens from closed/flex (`<= 120°`) across the `155°` inner display threshold and automatically triggers your configured `UnfoldActionMode`:
  - **`INNER_DASHBOARD`**: Opens the 120Hz dual-hemisphere Expanded Inner Dashboard.
  - **`AUTO_SPLIT_VC_TERMUX`**: Automatically launches **VC + Termux** side-by-side (`FLAG_ACTIVITY_LAUNCH_ADJACENT`) as soon as you unfold the phone.
  - **`AUTO_SPLIT_PROD_COMPARE`**: Automatically launches **VC Prod (`dev.agentconversation.vci`) + VC Compare (`dev.agentconversation.compare`)** side-by-side.
  - **`AUTO_SPLIT_VC_BROWSER`**: Automatically launches **VC + Chrome** side-by-side.
- Configurable in 1 tap from the System Tweaker card or via Omni-Bar (`> unfold <dashboard|split|vc+termux|compare|browser>`).

### 2. Live Tailscale Mesh VPN & LAN IP Telemetry (`NetworkTelemetrySnapshot`, `isTailscaleCgnatIpv4`, `TailscaleMeshTelemetryPill`)
- **Zero-Latency Network & Tailscale Mesh Detection (`NetworkTelemetrySnapshot`)**: Inspects active network interfaces (`tun0` / `tailscale0` / `wlan0` + `ConnectivityManager.TRANSPORT_VPN`) and identifies RFC 6598 CGNAT Tailscale IPv4 addresses (`100.64.0.0/10` via `isTailscaleCgnatIpv4`) alongside local Wi-Fi/LAN IPv4 addresses.
- **1-Tap IP Copy (`TailscaleMeshTelemetryPill`)**: Displays live `TAILSCALE <100.x.y.z>` and `LAN <192.168.x.y>` pills in the Top-Right Hero Header, Floating Telemetry HUD, and System Tweaker with 1-tap clipboard copy for instant wireless ADB (`adb connect`) and SSH/VC pairing.

### 3. Persistent Scratchpad & 1-Tap Device Telemetry Context Injector (`VcScratchpadPacket` & `buildTelemetryEnrichedPrompt`)
- **Collapsible `📝 Quick Scratchpad & Telemetry Injector` in `VcCommandCenterCard`**:
  - Persist quick notes, ADB endpoints, or prompt drafts in `LauncherPreferences` across app restarts and posture changes.
  - **1-Tap Device Telemetry Context Injector (`buildTelemetryEnrichedPrompt`)**: Optionally attaches a structured live hardware/posture/network context footer (`[Device Context: Z Fold 8 | Posture=... | Hinge=...° | Battery=...% (...°C, ...mA) | Tailscale=... | LAN=...]`) when dispatching your scratchpad note or prompt to VC agents.

### 4. Omni-Bar Unfold Continuity & Scratchpad Commands (`OmniCommandEngine`)
- **Unfold-Open Continuity Commands**:
  - `> unfold <dashboard|split|vc+termux|compare|browser>`: Switches the active `UnfoldActionMode`.
- **Scratchpad & VC Note Dispatch Commands**:
  - `note: <text>` / `memo: <text>`: Saves `<text>` directly to the persistent launcher scratchpad.
  - `note-vc: <text>`: Formats `<text>` with live device telemetry (`buildTelemetryEnrichedPrompt`), saves it to the scratchpad, and dispatches it immediately to the pinned or active VC session.

---

## Highlights from `v0.4.0`, `v0.3.0`, `v0.2.0` & `v0.1.0`

- **Mechanical Watch-Crown Hinge Haptic Detents (`HingeDetentTick`, `computeHingeDetentCrossed`, `triggerHingeDetentHaptic`)**: Fires `EFFECT_CLICK` at `15°`/`75°`/`155°` posture thresholds and `EFFECT_TICK` at `15°` increments.
- **Configurable Fold-Close Action Router (`FoldCloseActionMode`: `STAY_AWAKE_COVER`, `AUTO_VC_VOICE`, `TENT_STANDBY`, `INSTANT_LOCK` + `isFoldCloseTransition`)**.
- **Interactive Custom Dock Pinning (`pinnedDockPackages` + 1-tap `📌`/`📍` Pin-to-Dock button on App Drawer tiles)**.
- **Multi-Agent Squad Telemetry Bar (`VcSquadTelemetry`, `VcSquadTelemetryBar`) & 1-Tap Markdown Status Copy (`buildMarkdownStatusSnippet`, `📋 Copy Status`)**.
- **Z Fold 8 Secondary Display OLED Companion (`CoverScreenPresentation.kt` & `DualScreenPresentationController`)**.
- **Dedicated Lock-Screen Ambient Cockpit Card (`LockScreenCockpitCard`, `KeyguardManager.requestDismissKeyguard` 1-tap `Unlock & Split VC + Termux`, inline VC approval)**.
- **Zero-Permission Hardware Torch (`CameraManager.setTorchMode`), Workspace Profiles (`ALL`, `VC_DEV`, `FOCUS`, `OLED_BLACK`), and 3:2 Lissajous Burn-In Pixel-Shift (`computeOledPixelShiftOffset`)**.
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
