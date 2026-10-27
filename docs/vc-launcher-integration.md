# VC-Launcher Integration & Non-Foreground Bridge Architecture (`v0.3.0`)

## 1. Executive Summary

The **Fold8 & Slab Launcher** (`com.xsun.foldlauncher`) includes a purpose-built native integration module (`com.xsun.foldlauncher.vc`) for **VC (`vc-host` / `dev.agentconversation.*`)**.

When VC is not the foreground application on Android, its WebView conversation loop pauses and its native widgets and deep-link activities remain un-exported to third-party launchers. Because the home launcher and its companion `FoldAccessibilityService` are permanently resident in memory and have real-time visibility into foreground window transitions, secondary displays, and lock-screen state, **Fold8 & Slab Launcher** acts as an always-ready **VC Background Bridge, Multi-Instance Switcher, 1-Tap Prompt Macro Dispatcher, Dual-Display OLED Companion & Lock-Screen Cockpit**.

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
  - Additional fallback IDs: `dev.agentconversation`, `dev.agentconversation.preview.preflight`, `com.example.agentconversation`.
- **Talk Intent Action**: `dev.agentconversation.widget.TALK` (`SystemTweaker.VC_TALK_ACTION`).
- **Live Tailscale VC Host**: `http://100.96.188.90:8080` (`SystemTweaker.DEFAULT_VC_HOST_URL`), with `http://127.0.0.1:4327` supported for `adb reverse` loopback.

---

## 3. Concrete Architecture & `v0.3.0` Capabilities (`com.xsun.foldlauncher.vc`)

### 3.1 Data Models, Multi-Instance Target, Prompt Macros & Pinned Coordinator (`VcModels.kt`)
- **`VcInstanceTarget` (`PROD`, `PREVIEW`, `COMPARE`)**: Selects which installed VC package (`dev.agentconversation.vci`, `dev.agentconversation.preview`, or `dev.agentconversation.compare`) is the active launch & voice target.
- **`VcSplitPreset`**: Defines 4 1-tap Z Fold 8 dual-pane split-screen workspace combinations (`VC_TERMUX`, `PROD_COMPARE`, `PROD_PREVIEW`, `VC_CHROME`).
- **`VcPromptMacro` (`v0.3.0`)**: Built-in 1-tap prompt macros for rapid hands-free agent orchestration from the home screen, lock screen, or Omni-Bar:
  - **`STATUS` (`Status`)**: Requests a concise live progress & blocker summary across active workers.
  - **`APPROVE_AND_GO` (`Approve & Go`)**: Approves pending items and instructs agents to proceed to verification.
  - **`VERIFY_CI` (`Verify CI/Tests`)**: Instructs the target session to run unit tests, lint, and GitHub Actions CI checks.
  - **`SHIP_RELEASE` (`Ship Release`)**: Instructs the target session to tag, package APKs, and publish the release.
  - **`KEEP_BUILDING` (`Keep Building`)**: Instructs the coordinator/worker to continue autonomous execution on the next milestone.
- **Pinned Coordinator Session (`pinnedSessionId` in `VcConnectionConfig` / `LauncherPreferences`)**:
  - Allows pinning a primary Coordinator or General session ID (`pinnedSessionId`) so 1-tap macros, quick-send messages, and Omni-Bar `vc: status|approve|ship|test` commands always target the pinned session by default (with automatic fallback to the most urgent approval/working session).

### 3.2 Zero-Dependency HTTP & Multi-Instance Intent Bridge (`VcBridgeClient.kt` & `SystemTweaker.kt`)
- Uses `java.net.HttpURLConnection` + `org.json.JSONObject` with `minSdk = 29` safe `readBoundedBytes` streaming:
  - **Delta & Full Attention Polling (`fetchLiveSummary`)**: Calls `GET /api/attention?since=<cursor>&rosterTag=<tag>` and merges delta events into the in-memory summary (`reset: true` handling and 200-read-event capping), with `GET /api/snapshot` fallback.
  - **Inline Quick-Send, Live Steer & Prompt Macro Dispatch (`sendQuickMessage` / `sendPromptMacro`)**: Calls `POST /api/sessions/<sessionId>/message` (or `/steer` when the target agent is `working`) directly from `VcCommandCenterCard`, `LockScreenCockpitCard`, or `OmniCommandEngine` (`vc: status|approve|ship|test`).
  - **Mark Attention Read (`markAttentionRead`)**: Calls `POST /api/attention` with `{"id": "<eventId>"}` or `{"all": true}`.
  - **1-Tap Z Fold 8 Dual-Pane Split-Screen Workspace Combos (`FLAG_ACTIVITY_LAUNCH_ADJACENT`)**:
    1. **`VC + Termux`**
    2. **`Prod + Compare`** (`dev.agentconversation.vci` + `dev.agentconversation.compare`)
    3. **`Prod + Preview`** (`dev.agentconversation.vci` + `dev.agentconversation.preview`)
    4. **`VC + Chrome`**

### 3.3 Lock-Screen Ambient Cockpit (`LockScreenCockpitCard`) & Secondary Display OLED Companion (`CoverScreenPresentation`)
- **Dedicated Lock-Screen Ambient Cockpit Card (`LockScreenCockpitCard`)**:
  - Displayed when `KeyguardManager.isKeyguardLocked` is active over the One UI 9.0 lock screen (`MainActivity.setShowWhenLocked(true)`).
  - Provides a 1-tap **`Unlock & Split VC + Termux`** button powered by `KeyguardManager.requestDismissKeyguard(activity, KeyguardDismissCallback)` that dismisses the keyguard and immediately launches the dual-pane VC + Termux split workspace.
  - Supports **Inline Lock-Screen VC Approval** (`Approve & Go` / `Ack`) directly from the lock screen.
- **Z Fold 8 Secondary Display OLED Companion (`CoverScreenPresentation.kt` & `DualScreenPresentationController`)**:
  - Uses `DisplayManager` + `android.app.Presentation` to project a live `#000000` AMOLED companion HUD onto the Z Fold 8 secondary/cover display (`> dual` in Omni-Bar or toggle in System Tweaker), showing live VC `Working` / `Approve` / `Unread` counters and active agent status protected by 3:2 Lissajous burn-in pixel shift (`computeOledPixelShiftOffset`).

### 3.4 120Hz Home Screen `VcCommandCenterCard` (`VcCommandCenterCard.kt`)
- **Multi-Instance Switcher Bar**: 1-tap `PROD` (`vci`) / `PREVIEW` / `COMPARE` selector pills.
- **Pinned Session Indicator & Pin Toggle**: Visual pin badge (`📌`) on agent chips so you can lock your primary Coordinator session as the default target.
- **1-Tap VC Prompt Macro Strip**: Horizontal quick-fire chips (`Status`, `Approve & Go`, `Verify CI/Tests`, `Ship Release`, `Keep Building`).
- **Inline Pending Approval Banner**: High-visibility amber banner whenever `approvalCount > 0`, with 1-tap jump and 1-tap `Ack`.
- **1-Tap Dual-Pane Split-Screen Row**: `◫ VC+Termux`, `◫ Prod+Compare`, `◫ Prod+Preview`, and `◫ VC+Chrome`.
