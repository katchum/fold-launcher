# VC-Launcher Integration & Non-Foreground Bridge Architecture (`v0.2.0`)

## 1. Executive Summary

The **Fold8 & Slab Launcher** (`com.xsun.foldlauncher`) includes a purpose-built native integration module (`com.xsun.foldlauncher.vc`) for **VC (`vc-host` / `dev.agentconversation.*`)**.

When VC is not the foreground application on Android, its WebView conversation loop pauses and its native widgets and deep-link activities remain un-exported to third-party launchers. Because the home launcher and its companion `FoldAccessibilityService` are permanently resident in memory and have real-time visibility into foreground window transitions and lock-screen state, **Fold8 & Slab Launcher** acts as an always-ready **VC Background Bridge, Multi-Instance Switcher, Dual-Pane Split Workspace & Lock-Screen Cockpit**.

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

### 2.4 Multi-Instance VC Packages on Galaxy Z Fold 8 (`SM-F976U1`) & Galaxy S23 Ultra (`SM-S918U1`)
- Across the connected Galaxy Z Fold 8 (`100.101.43.86:42595`) and Galaxy S23 Ultra (`100.68.53.123:40919`), three primary side-by-side VC instances are used daily:
  - **`PROD` (`dev.agentconversation.vci`)**: `SystemTweaker.VC_PROD_PACKAGE` — Primary production VC client.
  - **`PREVIEW` (`dev.agentconversation.preview`)**: `SystemTweaker.VC_PREVIEW_PACKAGE` — Everyday preview/staging build.
  - **`COMPARE` (`dev.agentconversation.compare`)**: `SystemTweaker.VC_COMPARE_PACKAGE` — Side-by-side comparison build on Galaxy Z Fold 8.
  - Additional fallback IDs: `dev.agentconversation`, `dev.agentconversation.preview.preflight`, `com.example.agentconversation`.
- **Talk Intent Action**: `dev.agentconversation.widget.TALK` (`SystemTweaker.VC_TALK_ACTION`).
- **Live Tailscale VC Host**: `http://100.96.188.90:8080` (`SystemTweaker.DEFAULT_VC_HOST_URL`), with `http://127.0.0.1:4327` supported for `adb reverse` loopback.

---

## 3. Concrete Architecture & `v0.2.0` Capabilities (`com.xsun.foldlauncher.vc`)

### 3.1 Data Models, Multi-Instance Target & State (`VcModels.kt`)
- **`VcInstanceTarget` (`PROD`, `PREVIEW`, `COMPARE`)**: Allows the user to select which installed VC package (`dev.agentconversation.vci`, `dev.agentconversation.preview`, or `dev.agentconversation.compare`) is the active launch & voice target directly from `VcCommandCenterCard`.
- **`VcSplitPreset`**: Defines 4 1-tap Z Fold 8 dual-pane split-screen workspace combinations (`VC_TERMUX`, `PROD_COMPARE`, `PROD_PREVIEW`, `VC_CHROME`).
- **`VcConnectionConfig`**: Stores `serverUrl` (defaulting to `http://100.96.188.90:8080`), `authToken`, `monitorEnabled`, `notifyWhenBackgrounded`, and preferred `VcInstanceTarget`.
- **`VcAttentionItem` & `VcAgentStatus`**: Models `/api/attention` events (`approval`, `completed`, `failed`, `interrupted`) and active agent roster entries (`working`, `approval`, `idle`, `failed`, `completed`).
- **`VcLiveSummary`**: Immutable snapshot of `workingCount`, `approvalCount`, `unreadCount`, `failedCount`, `runningCount`, `attentionItems`, `activeAgents`, `cursor`, `rosterTag`, and `connectionState` (`UNCONFIGURED`, `CONNECTING`, `LIVE`, `STALE`, `ERROR`).

### 3.2 Zero-Dependency HTTP & Multi-Instance Intent Bridge (`VcBridgeClient.kt` & `SystemTweaker.kt`)
- Uses `java.net.HttpURLConnection` + `org.json.JSONObject` with `minSdk = 29` safe `readBoundedBytes` streaming:
  - **Delta & Full Attention Polling (`fetchLiveSummary`)**: Calls `GET /api/attention?since=<cursor>&rosterTag=<tag>` and merges delta events into the in-memory summary (`reset: true` handling and 200-read-event capping), with `GET /api/snapshot` fallback.
  - **Inline Quick-Send & Live Steer (`sendQuickMessage`)**: Calls `POST /api/sessions/<sessionId>/message` (or `/steer` when the target agent is `working`) directly from the home screen card.
  - **Mark Attention Read (`markAttentionRead`)**: Calls `POST /api/attention` with `{"id": "<eventId>"}` or `{"all": true}`.
  - **1-Tap Z Fold 8 Dual-Pane Split-Screen Workspace Combos (`FLAG_ACTIVITY_LAUNCH_ADJACENT`)**:
    1. **`VC + Termux`**: Launches the selected VC instance alongside `com.termux`.
    2. **`Prod + Compare`**: Launches `dev.agentconversation.vci` (`PROD`) side-by-side with `dev.agentconversation.compare` (`COMPARE`).
    3. **`Prod + Preview`**: Launches `dev.agentconversation.vci` (`PROD`) side-by-side with `dev.agentconversation.preview` (`PREVIEW`).
    4. **`VC + Chrome`**: Launches the selected VC instance alongside `com.android.chrome`.

### 3.3 Foreground-Aware Background Monitor & Lock-Screen Integration (`VcBackgroundMonitor.kt`)
- **Real-Time Foreground Detection**: `FoldAccessibilityService` tracks `dev.agentconversation.vci`, `dev.agentconversation.preview`, and `dev.agentconversation.compare` window state transitions (`TYPE_WINDOW_STATE_CHANGED`).
- **Adaptive Battery-Friendly Polling**:
  - On **Fold8 Home Screen / Lock-Screen Cockpit**: polls every **10 seconds** for live card updates.
  - When **any VC instance is in the foreground**: suppresses duplicate background notifications.
  - When **VC is in the background**: polls every **30 seconds** while agents are running/awaiting approval, or up to **30 minutes** when idle.
- **Smart Notification Diffing (`computeNewBackgroundAlerts`)**: Emits `vc_urgent_alerts` (approvals/failures) and `vc_agent_updates` (completions) only for newly arrived unread events when VC is backgrounded.
- **One UI 9.0 Lock-Screen Micro-HUD & Shortcuts**:
  - `FoldAccessibilityService` renders a lock-screen micro-HUD overlay (`TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED`) showing live VC `Working` / `Approve` counts on the Galaxy Z Fold 8 lock screen.
  - `SystemTweaker` & `scripts/adb-setup-fold8.sh` configure `lock_application_shortcut` so the One UI 9.0 lock-screen corner shortcuts launch `dev.agentconversation.vci` and `com.xsun.foldlauncher`.

### 3.4 120Hz Home Screen `VcCommandCenterCard` (`VcCommandCenterCard.kt`)
- **Multi-Instance Switcher Bar**: 1-tap `PROD` (`vci`) / `PREVIEW` / `COMPARE` selector pills showing installation status and active target.
- **Inline Pending Approval Banner**: Highlighted amber alert banner at the top of the card whenever `approvalCount > 0`, with 1-tap jump to the awaiting agent and 1-tap `Ack`.
- **1-Tap Dual-Pane Split-Screen Row**: Dedicated chips for `◫ VC+Termux`, `◫ Prod+Compare`, `◫ Prod+Preview`, and `◫ VC+Chrome`.
- **Active Agents, Attention Feed & Inline Quick-Send / Steer Bar**: Zero-IPC `BasicTextField` to message or steer any active VC agent directly from the Cover Screen, Unfolded Inner Display, Flex Tabletop mode, or Lock-Screen Cockpit.
