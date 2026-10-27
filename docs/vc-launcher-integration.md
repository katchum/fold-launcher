# VC-Launcher Integration & Non-Foreground Bridge Architecture (`v0.5.0`)

## 1. Executive Summary

The **Fold8 & Slab Launcher** (`com.xsun.foldlauncher`) includes a purpose-built native integration module (`com.xsun.foldlauncher.vc`) for **VC (`vc-host` / `dev.agentconversation.*`)**.

When VC is not the foreground application on Android, its WebView conversation loop pauses and its native widgets and deep-link activities remain un-exported to third-party launchers. Because the home launcher and its companion `FoldAccessibilityService` are permanently resident in memory and have real-time visibility into foreground window transitions, physical unfold/fold-close transitions, Tailscale Mesh VPN status, secondary displays, and lock-screen state, **Fold8 & Slab Launcher** acts as an always-ready **VC Background Bridge, Unfold-Open Split-Workspace Router, Scratchpad & Device Telemetry Context Injector, Multi-Agent Squad Telemetry Dashboard & Lock-Screen Cockpit**.

---

## 2. Diagnosis: What Happens When VC Is Not in the Foreground

Inspection of `/home/xsun/projects/vc-host` (`docs/foreground-polling.md`, `docs/attention-feed.md`, `docs/widget-plan.md`, `docs/bridge-contract.md`, and `android/app/src/main/`) reveals four specific architectural constraints when VC leaves the foreground:

### 2.1 WebView Snapshot Polling Halts After 1.5 Seconds (`docs/foreground-polling.md`)
- `ConversationActivity` runs as `singleTask`. When the user presses **Home**, **Back** (`moveTaskToBack(true)`), switches to another app, or turns the screen off, `ConversationActivity.onStop()` triggers `ConversationRuntime.started(this, false)`.
- After `HIDE_DELAY_MS = 1500ms`, `ConversationRuntime` sets `window.vcForeground = false`, dispatches `vc-foreground-change`, and calls `cancelAttentionWait()` (unless a live voice call is active in `VoiceService`).
- The WebView's 1.2s `/api/snapshot` loop and held-open `/api/attention?since=...&wait=25000` long-poll stop completely.

### 2.2 Native Background Poll Stretching & OS Throttling (`docs/attention-feed.md`)
- In the background, `AgentMonitorService` (`foregroundServiceType="dataSync"`) schedules `/api/attention` polls via plain `Handler.postDelayed` (`AttentionPoll.java`):
  - **Every 30 seconds** when `running > 0`
  - **Every 30 minutes** when `running == 0`
- Because `AgentMonitorService` uses plain delayed messages with no wake locks or exact alarms, Android Doze, App Standby Buckets, or Samsung One UI deep-sleep policies can stretch or suspend background ticks.

### 2.3 Native VC AppWidgets & Deep-Link Activity Are Un-Exported (`docs/widget-plan.md` & `AndroidManifest.xml`)
- In `vc-host/android/app/src/main/AndroidManifest.xml`, `VcTalkWidget`, `VcBriefWidget`, `VcWidget`, and `ConversationActivity` are declared with `android:exported="false"`.
- Only `MainActivity` is `android:exported="true"`. Third-party launchers therefore cannot bind VC's `AppWidgetProvider` receivers via standard `AppWidgetHostView` embedding.

### 2.4 Multi-Instance VC Packages on Galaxy Z Fold 8 & Galaxy S23 Ultra
- Across the connected Galaxy Z Fold 8 (`100.101.43.86:42595`) and Galaxy S23 Ultra (`100.68.53.123:40919`), three primary side-by-side VC instances are used daily:
  - **`PROD` (`dev.agentconversation.vci`)**: `SystemTweaker.VC_PROD_PACKAGE` — Primary production VC client.
  - **`PREVIEW` (`dev.agentconversation.preview`)**: `SystemTweaker.VC_PREVIEW_PACKAGE` — Everyday preview/staging build.
  - **`COMPARE` (`dev.agentconversation.compare`)**: `SystemTweaker.VC_COMPARE_PACKAGE` — Side-by-side comparison build on Galaxy Z Fold 8.
- **Talk Intent Action**: `dev.agentconversation.widget.TALK` (`SystemTweaker.VC_TALK_ACTION`).
- **Live Tailscale VC Host**: `http://100.96.188.90:8080` (`SystemTweaker.DEFAULT_VC_HOST_URL`), with `http://127.0.0.1:4327` supported for `adb reverse` loopback.

---

## 3. Concrete Architecture & `v0.5.0` Capabilities (`com.xsun.foldlauncher.vc`)

### 3.1 Persistent Scratchpad & 1-Tap Device Telemetry Context Injector (`VcScratchpadPacket` & `buildTelemetryEnrichedPrompt`)
- **Collapsible `📝 Quick Scratchpad & Telemetry Injector` in `VcCommandCenterCard` (`v0.5.0`)**:
  - Stores a persistent scratchpad memo (`scratchpadText`) in `LauncherPreferences` so prompt drafts, wireless ADB ports, or debugging notes survive posture transitions and reboots.
  - **`VcScratchpadPacket` & `buildTelemetryEnrichedPrompt`**: When `injectTelemetryOnSend` is enabled (or when using `Send + Telemetry` / `note-vc: <text>`), the launcher automatically enriches the outgoing VC message with a structured hardware/posture/network context block:
    - Device model & active posture (`COMPACT` / `FLEX_TABLETOP` / `EXPANDED` / `TENT`)
    - Live hardware hinge angle (`0°..180°`)
    - Battery percentage, thermal reading (`°C`), battery current (`mA`), and available RAM (`MB`)
    - Active Tailscale CGNAT IPv4 (`100.64.0.0/10`) and LAN IPv4 (`NetworkTelemetrySnapshot`)
- **Omni-Bar Scratchpad Integration**:
  - `note: <text>` / `memo: <text>`: Saves `<text>` to the persistent scratchpad.
  - `note-vc: <text>`: Saves `<text>` to the scratchpad, enriches it via `buildTelemetryEnrichedPrompt`, and dispatches it immediately to the pinned or active VC session.

### 3.2 Unfold-Open Auto-Split Continuity (`UnfoldActionMode` & `isUnfoldOpenTransition`) & Fold-Close Voice Handoff
- **Unfold-Open Auto-Split Continuity (`v0.5.0`)**:
  - When the user unfolds the Galaxy Z Fold 8 across `155°` (`isUnfoldOpenTransition` from `<= 120°` to `>= 155°`), the launcher automatically executes the configured `UnfoldActionMode` (`> unfold <dashboard|split|vc+termux|compare|browser>`):
    - `INNER_DASHBOARD`: Stays on the 120Hz dual-hemisphere home dashboard.
    - `AUTO_SPLIT_VC_TERMUX`: Launches **VC + Termux** side-by-side (`FLAG_ACTIVITY_LAUNCH_ADJACENT`).
    - `AUTO_SPLIT_PROD_COMPARE`: Launches **VC Prod (`vci`) + VC Compare (`compare`)** side-by-side.
    - `AUTO_SPLIT_VC_BROWSER`: Launches **VC + Chrome** side-by-side.
- **Fold-Close to VC Voice (`FoldCloseActionMode.AUTO_VC_VOICE`, `v0.4.0`)**:
  - When folding the Z Fold 8 shut (`isFoldCloseTransition` from `>= 45°` to `< 15°`) with `AUTO_VC_VOICE` active (`> fold voice`), automatically launches VC Voice Talk (`dev.agentconversation.widget.TALK`) on the Cover Display.

### 3.3 Live Tailscale Mesh VPN & LAN IP Telemetry (`NetworkTelemetrySnapshot` & `TailscaleMeshTelemetryPill`)
- Because VC (`http://100.96.188.90:8080`) and wireless ADB (`100.101.43.86` / `100.68.53.123`) operate over Tailscale Mesh VPN, `SystemTweaker` continuously inspects network interfaces and `isTailscaleCgnatIpv4` (`100.64.0.0/10`), surfacing the live Tailscale IPv4 and LAN IPv4 in `TailscaleMeshTelemetryPill` with 1-tap clipboard copy.

### 3.4 Multi-Agent Squad Telemetry (`VcSquadTelemetry`) & Markdown Status Export (`buildMarkdownStatusSnippet`)
- **`VcSquadTelemetry` & `VcSquadTelemetryBar`**: Renders a 120Hz segmented progress bar inside `VcCommandCenterCard` color-coded by agent state (`AmberWarning` for approvals, `ElectricCyan` for working, `CoralDanger` for failed, `EmeraldSuccess` for completed/idle).
- **1-Tap Markdown Status Copy (`buildMarkdownStatusSnippet` & `📋 Copy Status`)**: Copies a structured Markdown status summary of all active VC agents and unread attention items to the clipboard.

### 3.5 Lock-Screen Ambient Cockpit (`LockScreenCockpitCard`) & Secondary Display OLED Companion (`CoverScreenPresentation`)
- **Dedicated Lock-Screen Ambient Cockpit Card (`LockScreenCockpitCard`)**: Provides a 1-tap **`Unlock & Split VC + Termux`** button (`KeyguardManager.requestDismissKeyguard`) and **Inline Lock-Screen VC Approval** directly over the One UI 9.0 lock screen.
- **Z Fold 8 Secondary Display OLED Companion (`CoverScreenPresentation.kt` & `DualScreenPresentationController`)**: Projects a live `#000000` AMOLED companion HUD onto the Z Fold 8 secondary/cover display (`> dual`) protected by 3:2 Lissajous burn-in pixel shift (`computeOledPixelShiftOffset`).
