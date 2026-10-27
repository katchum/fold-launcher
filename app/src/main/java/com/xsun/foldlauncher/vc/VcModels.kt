package com.xsun.foldlauncher.vc

/**
 * Known package names for the VC Android app across release, everyday preview, preflight,
 * and legacy debug builds (`vc-host/android/app/build.gradle`).
 */
object VcPackages {
    const val RELEASE = "dev.agentconversation"
    const val PREVIEW = "dev.agentconversation.preview"
    const val COMPARE = "dev.agentconversation.compare"
    const val VCI = "dev.agentconversation.vci"
    const val PREFLIGHT = "dev.agentconversation.preview.preflight"
    const val LEGACY = "com.example.agentconversation"
    const val TALK_ACTION = "dev.agentconversation.widget.TALK"
    const val EXTRA_SESSION_ID = "dev.agentconversation.extra.SESSION_ID"
    const val EXTRA_PROJECT_ID = "dev.agentconversation.extra.PROJECT_ID"

    val ALL_VC_PACKAGES: List<String> = listOf(
        RELEASE,
        PREVIEW,
        COMPARE,
        VCI,
        PREFLIGHT,
        LEGACY
    )

    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_API_PACKAGE = "com.termux.api"
    const val CHROME_PACKAGE = "com.android.chrome"
    const val DEFAULT_LOCAL_SERVER_URL = "http://100.96.188.90:8080"
    const val LOOPBACK_SERVER_URL = "http://127.0.0.1:4327"
    const val RECENT_ATTENTION_WINDOW_MS = 24L * 60L * 60L * 1000L

    fun isVcPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName in ALL_VC_PACKAGES || packageName.startsWith("dev.agentconversation")
    }
}

/**
 * Multi-instance VC application variants installed side-by-side on the Z Fold 8 (`SM-F971U1`).
 */
enum class VcVariant(
    val label: String,
    val packageName: String,
    val shortBadge: String
) {
    PROD("VC Prod", VcPackages.RELEASE, "PROD"),
    PREVIEW("VC Preview", VcPackages.PREVIEW, "PREV"),
    COMPARE("VC Compare", VcPackages.COMPARE, "CMP");

    companion object {
        fun fromPackageName(pkg: String?): VcVariant {
            return when (pkg?.trim()) {
                PREVIEW.packageName, VcPackages.PREFLIGHT -> PREVIEW
                COMPARE.packageName -> COMPARE
                else -> PROD
            }
        }
    }
}

enum class VcRunState {
    WORKING,
    AWAITING_APPROVAL,
    FAILED,
    COMPLETED,
    IDLE
}

enum class VcConnectionState {
    UNCONFIGURED,
    CONNECTING,
    LIVE,
    STALE,
    ERROR
}

enum class VcAttentionKind(val wireName: String, val label: String, val isUrgent: Boolean) {
    APPROVAL("approval", "Needs Approval", true),
    FAILED("failed", "Failed", true),
    INTERRUPTED("interrupted", "Interrupted", true),
    COMPLETED("completed", "Completed", false),
    UPDATE("update", "Updated", false);

    companion object {
        fun fromWire(raw: String?): VcAttentionKind {
            return when (raw?.trim()?.lowercase()) {
                "approval" -> APPROVAL
                "failed", "error" -> FAILED
                "interrupted" -> INTERRUPTED
                "completed" -> COMPLETED
                else -> UPDATE
            }
        }
    }
}

/**
 * Configuration for connecting the Launcher's background bridge to a VC host.
 *
 * @property serverUrl Base URL of the VC host (e.g. `http://127.0.0.1:4327` via `adb reverse` or `https://host:port`).
 * @property authToken Device session token (`agent_session` cookie / Bearer token). Optional for loopback dev servers.
 * @property monitorEnabled Whether background polling and home-screen live status are enabled.
 * @property notifyWhenBackgrounded Whether to post Android notifications when new attention events arrive while VC is not in the foreground.
 */
data class VcConnectionConfig(
    val serverUrl: String = VcPackages.DEFAULT_LOCAL_SERVER_URL,
    val authToken: String = "",
    val monitorEnabled: Boolean = true,
    val notifyWhenBackgrounded: Boolean = true
) {
    val normalizedServerUrl: String
        get() = serverUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() = normalizedServerUrl.startsWith("http://") || normalizedServerUrl.startsWith("https://")
}

/**
 * One attention feed item from VC's `GET /api/attention` endpoint.
 */
data class VcAttentionItem(
    val id: String,
    val sessionId: String,
    val agentName: String,
    val projectName: String,
    val kind: VcAttentionKind,
    val text: String,
    val createdAt: Long,
    val read: Boolean = false,
    val seq: Long = 0L,
    val projectId: String = "general"
) {
    fun isActionable(referenceTimeMs: Long): Boolean {
        if (read) return false
        if (kind == VcAttentionKind.APPROVAL || kind == VcAttentionKind.FAILED || kind == VcAttentionKind.INTERRUPTED) {
            return true
        }
        if (referenceTimeMs <= 0L || createdAt <= 0L) return true
        return createdAt >= (referenceTimeMs - VcPackages.RECENT_ATTENTION_WINDOW_MS)
    }
}

/**
 * Status of an active (non-archived) VC agent session from `roster`, `working`, or `/api/snapshot`.
 */
data class VcAgentStatus(
    val sessionId: String,
    val name: String,
    val project: String,
    val role: String = "worker",
    val status: String = "idle",
    val approvalCount: Int = 0,
    val queuedCount: Int = 0,
    val hasUnreadUpdate: Boolean = false,
    val projectId: String = "general"
) {
    val isWorking: Boolean
        get() = status.equals("working", ignoreCase = true)

    val isFailed: Boolean
        get() = status.equals("failed", ignoreCase = true) ||
            status.equals("interrupted", ignoreCase = true)

    val needsApproval: Boolean
        get() = approvalCount > 0 || status.equals("approval", ignoreCase = true)

    val state: VcRunState
        get() = when {
            needsApproval -> VcRunState.AWAITING_APPROVAL
            isWorking -> VcRunState.WORKING
            isFailed -> VcRunState.FAILED
            hasUnreadUpdate || status.equals("completed", ignoreCase = true) -> VcRunState.COMPLETED
            else -> VcRunState.IDLE
        }

    val title: String
        get() = "$name · $project"

    val priorityOrder: Int
        get() = when {
            needsApproval -> 0
            isWorking -> 1
            isFailed -> 2
            hasUnreadUpdate -> 3
            queuedCount > 0 -> 4
            else -> 5
        }
}

/**
 * Immutable live summary of the VC host for the Launcher UI (`VcCommandCenterCard`)
 * and `VcBackgroundMonitor`.
 */
data class VcLiveSummary(
    val workingCount: Int = 0,
    val approvalCount: Int = 0,
    val unreadCount: Int = 0,
    val failedCount: Int = 0,
    val runningCount: Int = 0,
    val attentionItems: List<VcAttentionItem> = emptyList(),
    val activeAgents: List<VcAgentStatus> = emptyList(),
    val cursor: String? = null,
    val rosterTag: String? = null,
    val nextWakeAt: Long? = null,
    val lastUpdatedEpochMs: Long = 0L,
    val connectionState: VcConnectionState = VcConnectionState.UNCONFIGURED,
    val statusMessage: String = "Ready to connect to VC host",
    val isVcInstalled: Boolean = false,
    val installedVcPackage: String? = null,
    val isVcInForeground: Boolean = false,
    val selectedVariant: VcVariant = VcVariant.PROD,
    val installedVariants: Set<VcVariant> = emptySet()
) {
    val pendingApprovals: Int
        get() = approvalCount

    val approvalWaitingCount: Int
        get() = approvalCount

    val unreadUpdateCount: Int
        get() = unreadCount

    val sessions: List<VcAgentStatus>
        get() = activeAgents

    val hasUrgentAttention: Boolean
        get() = approvalCount > 0 || failedCount > 0

    val headlineSummary: String
        get() {
            if (connectionState == VcConnectionState.UNCONFIGURED) {
                return "VC Host Not Configured"
            }
            if (connectionState == VcConnectionState.ERROR && lastUpdatedEpochMs == 0L) {
                return "VC Host Offline"
            }
            val parts = mutableListOf<String>()
            if (approvalCount > 0) parts.add("$approvalCount to approve")
            if (failedCount > 0) parts.add("$failedCount failed")
            if (workingCount > 0) parts.add("$workingCount working")
            if (unreadCount > 0) parts.add("$unreadCount unread")
            return if (parts.isEmpty()) "All caught up" else parts.joinToString(" · ")
        }
}
