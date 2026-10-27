# VC-Launcher Integration & Non-Foreground Bridge Architecture

## 1. Executive Summary

The **Fold8 & Slab Launcher** (`com.xsun.foldlauncher`) includes a purpose-built native integration module (`com.xsun.foldlauncher.vc`) for **VC (`vc-host` / `dev.agentconversation`)**.

When VC is not the foreground application on Android, its WebView conversation loop pauses and its native widgets and deep-link activities remain un-exported to third-party launchers. Because the home launcher and its companion `FoldAccessibilityService` are permanently resident in memory and have real-time visibility into foreground window transitions, **Fold8 & Slab Launcher** acts as an always-ready **VC Background Bridge & Home Screen Command Center**.

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
- Because `AgentMonitorService` uses plain delayed messages with no wake locks or exact alarms, Android Doze, App Standby Buckets, or Samsung One UI deep-sleep policies can stretch or suspend background ticks. Furthermore, if VC has not been launched yet since boot or the user tapped "Stop updates", `AgentMonitorService` is not running at all.

### 2.3 Native VC AppWidgets & Deep-Link Activity Are Un-Exported (`docs/widget-plan.md` & `AndroidManifest.xml`)
- In `vc-host/android/app/src/main/AndroidManifest.xml`:
  - `VcTalkWidget`, `VcBriefWidget`, and `VcWidget` are declared with `android:exported="false"`.
  - `ConversationActivity` (which accepts `sessionId`, `backend`, and `dev.agentconversation.widget.TALK` intents) is also `android:exported="false"`.
  - Only `MainActivity` is `android:exported="true"`, and `MainActivity.java` explicitly strips all external extras (`startActivity(new Intent(this, ConversationActivity.class)); finish();`).
- Consequently, third-party Android launchers cannot bind VC's `AppWidgetProvider` receivers and cannot rely on standard `AppWidgetHostView` embedding to render VC's status or agent bars.

### 2.4 Package Variants Across Everyday Builds & Connected `SM-S918U1`
- VC uses multiple application IDs depending on build variant (`vc-host/android/app/build.gradle` and the connected Galaxy S23 Ultra `SM-S918U1`):
  - `dev.agentconversation.vci` (`SystemTweaker.VC_PROD_PACKAGE` — production VC package installed on `SM-S918U1`)
  - `dev.agentconversation.preview` (`SystemTweaker.VC_PREVIEW_PACKAGE` — preview/everyday debug package installed on `SM-S918U1`)
  - `dev.agentconversation` (upstream base release application ID)
  - `dev.agentconversation.preview.preflight` (preflight build)
  - `com.example.agentconversation` (legacy / test package identifier)
- Talk Intent Action: `dev.agentconversation.widget.TALK` (`SystemTweaker.VC_TALK_ACTION`)
- Live Tailscale VC Host: `http://100.96.188.90:8080` (`SystemTweaker.DEFAULT_VC_HOST_URL`), with `http://127.0.0.1:4327` supported for `adb reverse` loopback.

---

## 3. Concrete Work Items & Launcher Architecture (`com.xsun.foldlauncher.vc`)

To give the user zero-latency visibility and control over VC agents whenever VC is in the background, `com.xsun.foldlauncher.vc` implements five components:

### Work Item 1: Data Models & State (`VcModels.kt`)
- **`VcConnectionConfig`**: Stores `serverUrl` (defaulting to `http://100.96.188.90:8080`, the live Tailscale VC host endpoint, with automatic support for `http://127.0.0.1:4327` loopback or any paired HTTPS host), `authToken` (`agent_session` cookie / Bearer token), `monitorEnabled`, and `notifyWhenBackgrounded`.
- **`VcAttentionKind`** & **`VcAttentionItem`**: Models `/api/attention` events (`approval`, `completed`, `failed`, `interrupted`, `unknown`) with `id`, `sessionId`, `projectId`, `agentName`, `projectName`, `kind`, `text` (`text` / `title` / `summary`), `createdAt`, `read`, and `seq`.
- **`VcAgentStatus`**: Models active agents from `/api/attention`'s `roster` and `working` arrays (or `/api/snapshot` sessions) with `sessionId`, `projectId`, `name`, `project`, `role`, `status` (`working`, `approval`, `idle`, `failed`, `completed`), `approvalCount`, and `queuedCount`.
- **`VcLiveSummary`**: Aggregated immutable UI & monitor state:
  - `workingCount`, `approvalCount`, `unreadCount`, `failedCount`, `runningCount`
  - `attentionItems: List<VcAttentionItem>` (sorted newest-first, actionable items prioritized)
  - `activeAgents: List<VcAgentStatus>` (sorted by urgency: Approval -> Working -> Failed -> Unread -> Idle)
  - `cursor`, `rosterTag`, `nextWakeAt`, `lastUpdatedEpochMs`, `connectionState` (`UNCONFIGURED`, `CONNECTING`, `LIVE`, `STALE`, `ERROR`).

### Work Item 2: Zero-Dependency HTTP & Intent Bridge (`VcBridgeClient.kt`)
- Uses `java.net.HttpURLConnection` + `org.json.JSONObject` (zero external libraries, maintaining our sub-2MB APK budget):
  - **Delta & Full Attention Polling (`fetchLiveSummary`)**: Calls `GET /api/attention?since=<cursor>&rosterTag=<tag>` and merges delta events into the in-memory summary (matching `AttentionPoll.java` and `WidgetSnapshot.java` rules, including `reset: true` handling and 200-read-event capping). Also supports `GET /api/snapshot` fallback for full session state.
  - **Authentication**: Sends both `Cookie: agent_session=<token>` and `Authorization: Bearer <token>` when `authToken` is configured, working seamlessly with paired VC hosts, Tailscale hosts, and local loopback servers.
  - **Inline Quick-Send & Live Steer (`sendQuickMessage`)**: Calls `POST /api/sessions/<sessionId>/message` (or `/steer` when the target agent is currently `working`) with `{"text": "...", "requestId": "<uuid>"}` so the user can reply to or steer an agent directly from the launcher home screen without switching apps.
  - **Mark Attention Read (`markAttentionRead`)**: Calls `POST /api/attention` with `{"id": "<eventId>"}`, `{"all": true}`, or `{"seen": {"sessionId": "<id>", "upTo": <epochMs>}}`.
  - **Multi-Variant App Launch, Voice Talk & Z Fold 8 Split-Screen Workspace**:
    - Delegates to `SystemTweaker.launchVc(context, sessionId, projectId, startVoiceTalk, adjacentSplitScreen)` and automatically resolves whichever VC APK is installed (`dev.agentconversation.vci`, `dev.agentconversation.preview`, `dev.agentconversation`, `dev.agentconversation.preview.preflight`, `com.example.agentconversation`), falling back to opening `serverUrl` in the browser if no APK is installed.
    - Supports 1-tap Voice Talk launch (`dev.agentconversation.widget.TALK`).
    - **1-Tap Split-Screen VC Workspace**: Launches VC side-by-side with `com.termux` or the user's browser on the Z Fold 8 7.6"+ inner display using `FLAG_ACTIVITY_LAUNCH_ADJACENT`.

### Work Item 3: Foreground-Aware Background Monitor (`VcBackgroundMonitor.kt`)
- Bridges `FoldAccessibilityService` (`TYPE_WINDOW_STATE_CHANGED` & `FoldAccessibilityService.isVcInForeground: StateFlow<Boolean>`) and `LauncherViewModel`:
  - **Real-Time Foreground Detection**: `FoldAccessibilityService` updates `isVcInForeground` and notifies `VcBackgroundMonitor.onForegroundPackageChanged(packageName)` whenever the active window changes. The monitor knows immediately whether `dev.agentconversation.vci` / `dev.agentconversation.preview` is on screen (`isVcInForeground == true`) or backgrounded (`isVcInForeground == false`).
  - **Adaptive Battery-Friendly Polling (`docs/attention-feed.md` Policy)**:
    - When the user is on the **Fold8 Home Screen**: polls every **10 seconds** so the home screen `VcCommandCenterCard` stays live.
    - When **VC is in the foreground**: suppresses redundant background notifications and defers to VC's own 1.2s foreground loop.
    - When **VC is not in the foreground**: polls `/api/attention` every **30 seconds** while any agent is running (`runningCount > 0 || workingCount > 0 || approvalCount > 0`), or up to **30 minutes** (`min(30 min, nextWakeAt - now)`, never below 30s) when idle.
  - **Smart Notification Diffing (`computeNewBackgroundAlerts`)**:
    - Tracks seen unread event IDs (`seenUnreadIds`).
    - When VC is **not** in the foreground, any newly arrived unread `approval`, `failed`/`interrupted`, or `completed` event triggers a system notification via `NotificationManager`:
      - Channel `vc_urgent_alerts` (`IMPORTANCE_HIGH`): Approvals & Failures.
      - Channel `vc_agent_updates` (`IMPORTANCE_DEFAULT`): Completed turns & updates.
    - Includes 1-tap notification deep links to **Open VC** directly to the alerting `sessionId`.

### Work Item 4: 120Hz Home Screen UI (`VcCommandCenterCard.kt`)
- Because VC's native `RemoteViews` widgets (`VcTalkWidget`, `VcBriefWidget`, `VcWidget`) are not wired/exported on main yet, `VcCommandCenterCard` renders a native 120Hz Jetpack Compose VC widget directly on the Launcher Home Screen (Cover Screen, Unfolded Inner Display left dashboard hemisphere, and Flex Tabletop mode) using VC's dark widget palette (`#11171C` dark surface, `#2A343C` border line, `#172D28` operation surface, `#B9E6C8` mint accent):
  - **Live Status Strip**: Shows connection indicator (`LIVE`, `STALE`, `ERROR`), `Working`, `Approve`, `Unread`, and `Failed` counts.
  - **1-Tap Workspace Actions**: `🎙 Talk` (`dev.agentconversation.widget.TALK`), `↗ Open VC`, `◫ Split-Screen VC`, `✓ Mark All Read`, `↻ Refresh`, and inline `Host ⚙` configuration drawer.
  - **Active Agents & Project Pills**: Horizontal scrollable chips showing each active/working/approval agent with color-coded status dots (`AmberWarning` for approval, `ElectricCyan` for working, `CoralDanger` for failed, `EmeraldSuccess` for updated/idle). Tapping an agent selects it as the target for quick-send or opens VC.
  - **Top Actionable Attention Feed**: Displays the top 3 unread attention items with one-tap `✓` (mark read) or tap-to-open VC.
  - **Inline Quick-Send / Steer Bar**: A zero-IPC `BasicTextField` right inside the card allowing the user to type a prompt or steer instruction and send it directly to the selected VC agent (or `General`) in 1 tap.

### Work Item 5: Unit Test Verification (`VcBridgeParserTest.kt`)
- Pure JVM unit tests in `app/src/test/java/com/xsun/foldlauncher/vc/VcBridgeParserTest.kt` verifying:
  - Full `/api/attention` response parsing (`events`, `working`, `roster`, `running`, `cursor`, `rosterTag`).
  - Delta `/api/attention?since=...` merging (adding new events, marking existing events read, preserving roster when `rosterTag` is unchanged, replacing on `reset: true`).
  - `/api/snapshot` array/object fallback parsing.
  - Background notification diffing (`computeNewBackgroundAlerts`): verifying that notifications are only emitted for new unread events when `isVcInForeground == false`, and suppressed on initial baseline seed or when VC is in the foreground.
