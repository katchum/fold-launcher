# VC-Launcher Integration & Non-Foreground Bridge Architecture (`v0.4.0`)

## 1. Executive Summary

The **Fold8 & Slab Launcher** (`com.xsun.foldlauncher`) includes a purpose-built native integration module (`com.xsun.foldlauncher.vc`) for **VC (`vc-host` / `dev.agentconversation.*`)**.

When VC is not the foreground application on Android, its WebView conversation loop pauses and its native widgets and deep-link activities remain un-exported to third-party launchers. Because the home launcher and its companion `FoldAccessibilityService` are permanently resident in memory and have real-time visibility into foreground window transitions, physical hinge-close transitions, secondary displays, and lock-screen state, **Fold8 & Slab Launcher** acts as an always-ready **VC Background Bridge, Multi-Agent Squad Telemetry Dashboard, Fold-Close Voice Router, Dual-Display OLED Companion & Lock-Screen Cockpit**.

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

## 3. Concrete Architecture & `v0.4.0` Capabilities (`com.xsun.foldlauncher.vc`)

### 3.1 Multi-Agent Squad Telemetry (`VcSquadTelemetry`) & Markdown Status Export (`buildMarkdownStatusSnippet`)
- **`VcSquadTelemetry` (`v0.4.0`)**: Aggregates active agent roster metrics into a normalized squad telemetry breakdown (`totalAgents`, `workingCount`, `approvalCount`, `failedCount`, `idleOrCompletedCount`, `workingFraction`, `approvalFraction`, `failedFraction`, `idleFraction`, and `activeRatioPercent`).
- **`VcSquadTelemetryBar` (`v0.4.0`)**: Renders a 120Hz segmented progress bar inside `VcCommandCenterCard` color-coded by agent state (`AmberWarning` for approvals, `ElectricCyan` for working, `CoralDanger` for failed, `EmeraldSuccess` for completed/idle) along with live squad utilization percentage.
- **1-Tap Markdown Status Copy (`buildMarkdownStatusSnippet` & `📋 Copy Status`)**: Formats a concise, structured Markdown snapshot of connection status, squad telemetry counters, active/pinned agents, and top unread attention items, copying it directly to the Android clipboard in 1 tap.

### 3.2 Fold-Close Auto-VC Voice Handoff (`FoldCloseActionMode.AUTO_VC_VOICE`) & Termux Split Dispatcher
- **Fold-Close to VC Voice (`FoldCloseActionMode.AUTO_VC_VOICE`)**: When the user folds the Galaxy Z Fold 8 shut (`isFoldCloseTransition` from `>= 45°` to `< 15°`) with `AUTO_VC_VOICE` active (`> fold voice`), the launcher automatically triggers `SystemTweaker.launchVc(..., startVoiceTalk = true)` on the Cover Display so the user can seamlessly continue their conversation by voice while on the move.
- **Omni-Bar Termux / Shell Snippet Dispatcher (`termux:`, `sh:`, `termux-split:`)**:
  - `termux: <cmd>` / `sh: <cmd>`: Copies `<cmd>` to the clipboard and opens `com.termux`.
  - `termux-split: <cmd>`: Copies `<cmd>` to the clipboard and launches the active VC instance + `com.termux` side-by-side (`FLAG_ACTIVITY_LAUNCH_ADJACENT`).

### 3.3 Data Models, Multi-Instance Target, Prompt Macros & Pinned Coordinator (`VcModels.kt`)
- **`VcInstanceTarget` (`PROD`, `PREVIEW`, `COMPARE`)**: Selects which installed VC package (`dev.agentconversation.vci`, `dev.agentconversation.preview`, or `dev.agentconversation.compare`) is the active launch & voice target.
- **`VcSplitPreset`**: Defines 4 1-tap Z Fold 8 dual-pane split-screen workspace combinations (`VC_TERMUX`, `PROD_COMPARE`, `PROD_PREVIEW`, `VC_CHROME`).
- **`VcPromptMacro`**: Built-in 1-tap prompt macros (`Status`, `Approve & Go`, `Verify CI/Tests`, `Ship Release`, `Keep Building`).
- **Pinned Coordinator Session (`pinnedSessionId`)**: Pins a primary Coordinator or General session ID so 1-tap macros, quick-send messages, and Omni-Bar `vc:` commands always route to the pinned session by default.

### 3.4 Lock-Screen Ambient Cockpit (`LockScreenCockpitCard`) & Secondary Display OLED Companion (`CoverScreenPresentation`)
- **Dedicated Lock-Screen Ambient Cockpit Card (`LockScreenCockpitCard`)**: Provides a 1-tap **`Unlock & Split VC + Termux`** button (`KeyguardManager.requestDismissKeyguard`) and **Inline Lock-Screen VC Approval** directly over the One UI 9.0 lock screen.
- **Z Fold 8 Secondary Display OLED Companion (`CoverScreenPresentation.kt` & `DualScreenPresentationController`)**: Projects a live `#000000` AMOLED companion HUD onto the Z Fold 8 secondary/cover display (`> dual`) protected by 3:2 Lissajous burn-in pixel shift (`computeOledPixelShiftOffset`).
