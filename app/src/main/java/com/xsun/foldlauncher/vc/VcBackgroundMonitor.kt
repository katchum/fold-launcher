package com.xsun.foldlauncher.vc

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.xsun.foldlauncher.data.LauncherPreferences
import com.xsun.foldlauncher.service.FoldAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground-aware background monitor for VC (`vc-host` / `dev.agentconversation`).
 *
 * Solves VC's non-foreground limitations:
 * - When `ConversationActivity` stops (`onStop`), VC halts its 1.2s WebView snapshot loop after
 *   1.5s (`HIDE_DELAY_MS`) and stretches `AgentMonitorService` background polls to 30s–30min.
 * - `VcBackgroundMonitor` hooks into `FoldAccessibilityService` (`TYPE_WINDOW_STATE_CHANGED`) and
 *   `LauncherViewModel` to track whether VC is currently in the foreground or background.
 * - Polls `/api/attention` with delta cursors (`?since=<cursor>&rosterTag=<tag>`) at an adaptive,
 *   battery-friendly interval and posts Android system notifications (`NotificationManager`)
 *   whenever a new approval request, failure, or agent completion arrives while VC is backgrounded.
 */
class VcBackgroundMonitor private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val preferences = LauncherPreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _config = MutableStateFlow(
        VcConnectionConfig(
            serverUrl = preferences.vcServerUrl,
            authToken = preferences.vcAuthToken,
            monitorEnabled = preferences.vcMonitorEnabled,
            notifyWhenBackgrounded = preferences.vcNotifyBackground
        )
    )
    val config: StateFlow<VcConnectionConfig> = _config.asStateFlow()

    private val _liveSummary = MutableStateFlow(
        VcLiveSummary(
            connectionState = if (_config.value.isConfigured) {
                VcConnectionState.CONNECTING
            } else {
                VcConnectionState.UNCONFIGURED
            },
            isVcInstalled = VcBridgeClient.resolveInstalledVcPackage(appContext) != null,
            installedVcPackage = VcBridgeClient.resolveInstalledVcPackage(appContext)
        )
    )
    val liveSummary: StateFlow<VcLiveSummary> = _liveSummary.asStateFlow()

    private val _lastSendFeedback = MutableStateFlow<String?>(null)
    val lastSendFeedback: StateFlow<String?> = _lastSendFeedback.asStateFlow()

    @Volatile
    private var isVcInForeground: Boolean = false

    @Volatile
    private var isLauncherInForeground: Boolean = true

    @Volatile
    private var hasCompletedInitialSeed: Boolean = false

    private val seenUnreadIdsLock = Any()
    private var seenUnreadIds: Set<String> = preferences.vcSeenAttentionIds

    private var pollingJob: Job? = null

    init {
        ensureNotificationChannels()
        observeAccessibilityForegroundState()
        startPollingLoop()
    }

    private fun observeAccessibilityForegroundState() {
        scope.launch {
            FoldAccessibilityService.isVcInForeground.collectLatest { vcForeground ->
                val wasForeground = isVcInForeground
                isVcInForeground = vcForeground
                _liveSummary.value = _liveSummary.value.copy(isVcInForeground = vcForeground)
                if (wasForeground && !vcForeground) {
                    delay(1_600L)
                    pollOnce()
                }
            }
        }
    }

    /**
     * Called by `FoldAccessibilityService` on `TYPE_WINDOW_STATE_CHANGED` and by `MainActivity`
     * on `onResume` so the monitor knows immediately whether VC is on screen or backgrounded.
     */
    fun onForegroundPackageChanged(packageName: String) {
        if (packageName.isBlank()) return
        // Ignore system UI overlays / input methods so they don't clobber foreground app state
        if (packageName == "com.android.systemui" ||
            packageName.contains("inputmethod") ||
            packageName.contains("honeyboard")
        ) {
            return
        }

        val wasVcForeground = isVcInForeground
        val nowVcForeground = VcPackages.isVcPackage(packageName)
        val nowLauncherForeground = packageName.startsWith("com.xsun.foldlauncher")

        isVcInForeground = nowVcForeground
        isLauncherInForeground = nowLauncherForeground

        _liveSummary.value = _liveSummary.value.copy(
            isVcInForeground = nowVcForeground
        )

        // When VC transitions from foreground -> background, trigger a poll right after VC's
        // 1.5s HIDE_DELAY_MS WebView gate closes, or immediately when returning to the Launcher.
        if (nowLauncherForeground || (wasVcForeground && !nowVcForeground)) {
            scope.launch {
                if (wasVcForeground && !nowVcForeground) {
                    delay(1_600L)
                }
                pollOnce()
            }
        }
    }

    fun onLauncherResumed() {
        isLauncherInForeground = true
        isVcInForeground = false
        refreshNow()
    }

    fun updateConfig(
        serverUrl: String,
        authToken: String,
        monitorEnabled: Boolean = _config.value.monitorEnabled,
        notifyWhenBackgrounded: Boolean = _config.value.notifyWhenBackgrounded
    ) {
        val normalizedUrl = VcBridgeClient.normalizeServerUrlInput(serverUrl)
        val cleanedToken = authToken.trim()

        preferences.vcServerUrl = normalizedUrl
        preferences.vcAuthToken = cleanedToken
        preferences.vcMonitorEnabled = monitorEnabled
        preferences.vcNotifyBackground = notifyWhenBackgrounded

        val newConfig = VcConnectionConfig(
            serverUrl = normalizedUrl,
            authToken = cleanedToken,
            monitorEnabled = monitorEnabled,
            notifyWhenBackgrounded = notifyWhenBackgrounded
        )
        val urlChanged = _config.value.normalizedServerUrl != newConfig.normalizedServerUrl
        _config.value = newConfig

        if (urlChanged) {
            // Reset cursor & initial seed for the new host
            hasCompletedInitialSeed = false
            _liveSummary.value = VcLiveSummary(
                connectionState = if (newConfig.isConfigured) {
                    VcConnectionState.CONNECTING
                } else {
                    VcConnectionState.UNCONFIGURED
                },
                isVcInstalled = VcBridgeClient.resolveInstalledVcPackage(appContext) != null,
                installedVcPackage = VcBridgeClient.resolveInstalledVcPackage(appContext),
                isVcInForeground = isVcInForeground
            )
        }
        refreshNow()
    }

    fun refreshNow() {
        scope.launch {
            pollOnce()
        }
    }

    fun sendQuickMessage(
        sessionId: String?,
        text: String,
        steerIfWorking: Boolean = true,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        scope.launch {
            val currentConfig = _config.value
            val currentAgents = _liveSummary.value.activeAgents
            val result = VcBridgeClient.sendQuickMessage(
                config = currentConfig,
                sessionId = sessionId,
                text = text,
                steerIfWorking = steerIfWorking,
                activeAgents = currentAgents
            )
            val feedback = result.fold(
                onSuccess = { msg -> "✓ $msg" },
                onFailure = { err -> "✕ ${err.message?.take(48) ?: "Send failed"}" }
            )
            _lastSendFeedback.value = feedback
            if (result.isSuccess) {
                pollOnce()
            }
            if (onComplete != null) {
                withContext(Dispatchers.Main) {
                    onComplete(result.isSuccess, feedback)
                }
            }
        }
    }

    fun markAttentionRead(eventId: String? = null, markAll: Boolean = false) {
        scope.launch {
            val ok = VcBridgeClient.markAttentionRead(
                config = _config.value,
                eventId = eventId,
                markAll = markAll
            )
            if (ok) {
                pollOnce()
            }
        }
    }

    fun launchVc(sessionId: String? = null, startVoiceTalk: Boolean = false): Boolean {
        return VcBridgeClient.launchVcApp(
            context = appContext,
            config = _config.value,
            sessionId = sessionId,
            startVoiceTalk = startVoiceTalk
        )
    }

    fun launchVcVoiceTalk(sessionId: String? = null): Boolean {
        return VcBridgeClient.launchVcApp(
            context = appContext,
            config = _config.value,
            sessionId = sessionId,
            startVoiceTalk = true
        )
    }

    fun launchVcSplitScreen(preferredSecondaryPackage: String? = null): Boolean {
        return VcBridgeClient.launchVcSplitScreenWorkspace(
            context = appContext,
            config = _config.value,
            preferredSecondaryPackage = preferredSecondaryPackage
        )
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                val cfg = _config.value
                if (cfg.monitorEnabled && cfg.isConfigured && shouldPollInCurrentPowerState()) {
                    pollOnce()
                }
                val delayMs = computeNextPollDelayMs(_liveSummary.value)
                delay(delayMs)
            }
        }
    }

    private fun shouldPollInCurrentPowerState(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val interactive = pm?.isInteractive ?: true
        if (interactive) return true
        // When screen is off, only poll if an agent was actively working/running
        val current = _liveSummary.value
        return current.workingCount > 0 || current.runningCount > 0
    }

    internal fun computeNextPollDelayMs(summary: VcLiveSummary): Long {
        val cfg = _config.value
        if (!cfg.monitorEnabled || !cfg.isConfigured) return IDLE_BACKGROUND_POLL_MS

        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val interactive = pm?.isInteractive ?: true
        if (!interactive) {
            return if (summary.workingCount > 0 || summary.runningCount > 0) {
                SCREEN_OFF_WORKING_POLL_MS
            } else {
                IDLE_BACKGROUND_POLL_MS
            }
        }

        if (isLauncherInForeground) {
            return LAUNCHER_FOREGROUND_POLL_MS
        }
        if (isVcInForeground || FoldAccessibilityService.isVcInForeground.value) {
            // VC is in the foreground running its own 1.2s loop; keep launcher cache warm at low frequency
            return VC_FOREGROUND_WARM_POLL_MS
        }
        // Owner's background polling rule (docs/attention-feed.md):
        // When VC is not in the foreground, poll /api/attention every 30s while any agent is running
        // (running > 0), or up to 30 min when idle (clamped by nextWakeAt when scheduled wakes exist).
        return if (summary.runningCount > 0 || summary.workingCount > 0 || summary.approvalCount > 0) {
            ACTIVE_BACKGROUND_POLL_MS
        } else {
            val wakeAt = summary.nextWakeAt
            if (wakeAt != null && wakeAt > 0L) {
                val untilWake = wakeAt - System.currentTimeMillis()
                untilWake.coerceIn(ACTIVE_BACKGROUND_POLL_MS, IDLE_BACKGROUND_POLL_MS)
            } else {
                IDLE_BACKGROUND_POLL_MS
            }
        }
    }

    private fun pollOnce() {
        val cfg = _config.value
        val installedPkg = VcBridgeClient.resolveInstalledVcPackage(appContext)
        val installed = installedPkg != null

        if (!cfg.isConfigured) {
            _liveSummary.value = VcLiveSummary(
                connectionState = VcConnectionState.UNCONFIGURED,
                statusMessage = "Configure VC host URL",
                isVcInstalled = installed,
                installedVcPackage = installedPkg,
                isVcInForeground = isVcInForeground
            )
            return
        }

        val updatedSummary = VcBridgeClient.fetchLiveSummary(
            config = cfg,
            previousSummary = _liveSummary.value,
            isVcInstalled = installed,
            installedVcPackage = installedPkg,
            isVcInForeground = isVcInForeground
        )
        _liveSummary.value = updatedSummary

        if (updatedSummary.connectionState == VcConnectionState.LIVE) {
            val isSeed = !hasCompletedInitialSeed
            hasCompletedInitialSeed = true

            val diffResult = synchronized(seenUnreadIdsLock) {
                val diff = VcBridgeClient.computeNewBackgroundAlerts(
                    previousSeenUnreadIds = seenUnreadIds,
                    currentSummary = updatedSummary,
                    isVcInForeground = isVcInForeground,
                    isInitialSeed = isSeed
                )
                seenUnreadIds = diff.updatedSeenUnreadIds
                preferences.vcSeenAttentionIds = seenUnreadIds
                diff
            }

            if (cfg.notifyWhenBackgrounded && diffResult.alertsToNotify.isNotEmpty()) {
                postBackgroundNotifications(diffResult.alertsToNotify, cfg)
            }
        }
    }

    private fun postBackgroundNotifications(
        alerts: List<VcAttentionItem>,
        config: VcConnectionConfig
    ) {
        if (!canPostNotifications()) return
        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
        ensureNotificationChannels()

        for (alert in alerts.take(4)) {
            val channelId = if (alert.kind.isUrgent) {
                CHANNEL_URGENT_ALERTS
            } else {
                CHANNEL_AGENT_UPDATES
            }
            val notificationId = NOTIFICATION_ID_BASE + (alert.id.hashCode() and 0x7FFF)

            val openIntent = buildOpenVcPendingIntent(
                requestCode = notificationId,
                config = config,
                sessionId = alert.sessionId
            )

            val titlePrefix = when (alert.kind) {
                VcAttentionKind.APPROVAL -> "Approval Needed · "
                VcAttentionKind.FAILED -> "Agent Failed · "
                VcAttentionKind.INTERRUPTED -> "Interrupted · "
                VcAttentionKind.COMPLETED -> "Completed · "
                VcAttentionKind.UPDATE -> "VC · "
            }
            val title = "$titlePrefix${alert.agentName} (${alert.projectName})"
            val body = alert.text.ifBlank { alert.kind.label }

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(appContext, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(appContext)
            }

            builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)

            if (openIntent != null) {
                builder.setContentIntent(openIntent)
            }

            try {
                nm.notify(notificationId, builder.build())
            } catch (_: Exception) {
            }
        }
    }

    private fun buildOpenVcPendingIntent(
        requestCode: Int,
        config: VcConnectionConfig,
        sessionId: String?
    ): PendingIntent? {
        val pkg = VcBridgeClient.resolveInstalledVcPackage(appContext)
        val targetIntent: Intent = if (pkg != null) {
            appContext.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (!sessionId.isNullOrBlank()) {
                    putExtra("sessionId", sessionId)
                }
            }
        } else if (config.isConfigured) {
            Intent(Intent.ACTION_VIEW, Uri.parse(config.normalizedServerUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            null
        } ?: return null

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return try {
            PendingIntent.getActivity(appContext, requestCode, targetIntent, flags)
        } catch (_: Exception) {
            null
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
        try {
            val urgentChannel = NotificationChannel(
                CHANNEL_URGENT_ALERTS,
                "VC Approvals & Failures",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts when a VC agent needs approval or fails while VC is in the background"
            }
            val updatesChannel = NotificationChannel(
                CHANNEL_AGENT_UPDATES,
                "VC Agent Completions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when a VC agent finishes its turn while VC is in the background"
            }
            nm.createNotificationChannel(urgentChannel)
            nm.createNotificationChannel(updatesChannel)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val CHANNEL_URGENT_ALERTS = "vc_urgent_alerts"
        const val CHANNEL_AGENT_UPDATES = "vc_agent_updates"
        private const val NOTIFICATION_ID_BASE = 7000

        const val LAUNCHER_FOREGROUND_POLL_MS = 10_000L
        const val ACTIVE_BACKGROUND_POLL_MS = 30_000L
        const val VC_FOREGROUND_WARM_POLL_MS = 30_000L
        const val SCREEN_OFF_WORKING_POLL_MS = 30_000L
        const val IDLE_BACKGROUND_POLL_MS = 30 * 60_000L

        @Volatile
        private var instance: VcBackgroundMonitor? = null

        fun getInstance(context: Context): VcBackgroundMonitor {
            return instance ?: synchronized(this) {
                instance ?: VcBackgroundMonitor(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
