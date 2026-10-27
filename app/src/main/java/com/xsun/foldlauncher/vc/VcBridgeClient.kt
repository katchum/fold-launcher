package com.xsun.foldlauncher.vc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.xsun.foldlauncher.service.SystemTweaker
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Result of diffing a newly polled [VcLiveSummary] against previously seen unread attention IDs.
 */
data class VcAlertDiffResult(
    val alertsToNotify: List<VcAttentionItem>,
    val updatedSeenUnreadIds: Set<String>
)

/**
 * Zero-dependency HTTP & Intent bridge connecting Fold8 & Slab Launcher to VC (`vc-host` /
 * `dev.agentconversation`).
 *
 * Uses `HttpURLConnection` + `org.json.JSONObject` (with a pure-Kotlin JSON fallback for JVM
 * unit tests where Android's `org.json` stub is unmocked).
 */
object VcBridgeClient {

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    private const val MAX_READ_EVENTS_KEPT = 200

    /**
     * Extracts the base server origin (`https://host:port` or `http://127.0.0.1:4327`) if the user
     * pastes a full VC pairing link (`https://host:port/pair#v=1&c=...`) or URL with path.
     */
    fun normalizeServerUrlInput(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return VcPackages.DEFAULT_LOCAL_SERVER_URL
        return try {
            val withScheme = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                if (trimmed.startsWith("127.0.0.1") ||
                    trimmed.startsWith("localhost") ||
                    trimmed.startsWith("100.") ||
                    trimmed.startsWith("192.168.") ||
                    trimmed.startsWith("10.")
                ) {
                    "http://$trimmed"
                } else {
                    "https://$trimmed"
                }
            } else {
                trimmed
            }
            val uri = URI(withScheme)
            val scheme = (uri.scheme ?: "http").lowercase()
            val host = uri.host ?: return withScheme.trimEnd('/')
            val portPart = if (uri.port != -1) ":${uri.port}" else ""
            "$scheme://$host$portPart"
        } catch (_: Exception) {
            trimmed.substringBefore("/pair").substringBefore("#").trimEnd('/')
        }
    }

    /**
     * Queries VC's `/api/attention` endpoint (using `since` and `rosterTag` delta parameters when
     * available) and falls back to `/api/snapshot` if needed.
     */
    fun fetchLiveSummary(
        config: VcConnectionConfig,
        previousSummary: VcLiveSummary? = null,
        isVcInstalled: Boolean = false,
        installedVcPackage: String? = null,
        isVcInForeground: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): VcLiveSummary {
        if (!config.isConfigured) {
            return VcLiveSummary(
                connectionState = VcConnectionState.UNCONFIGURED,
                statusMessage = "Configure VC host URL in Command Center",
                isVcInstalled = isVcInstalled,
                installedVcPackage = installedVcPackage,
                isVcInForeground = isVcInForeground
            )
        }

        val baseUrl = config.normalizedServerUrl
        val query = buildAttentionQuery(
            cursor = previousSummary?.cursor,
            rosterTag = previousSummary?.rosterTag
        )
        val attentionUrl = "$baseUrl/api/attention$query"

        return try {
            val rawAttention = executeHttp(
                urlString = attentionUrl,
                method = "GET",
                authToken = config.authToken
            )
            parseAndMergeAttention(
                rawJson = rawAttention,
                previousSummary = previousSummary,
                nowMs = nowMs,
                isVcInstalled = isVcInstalled,
                installedVcPackage = installedVcPackage,
                isVcInForeground = isVcInForeground
            )
        } catch (attentionError: Exception) {
            // Try fallback to GET /api/snapshot if /api/attention is unavailable
            try {
                val rawSnapshot = executeHttp(
                    urlString = "$baseUrl/api/snapshot",
                    method = "GET",
                    authToken = config.authToken
                )
                parseSnapshotResponse(
                    rawJson = rawSnapshot,
                    nowMs = nowMs,
                    isVcInstalled = isVcInstalled,
                    installedVcPackage = installedVcPackage,
                    isVcInForeground = isVcInForeground
                )
            } catch (_: Exception) {
                val msg = attentionError.message ?: "VC host unreachable"
                if (previousSummary != null && previousSummary.lastUpdatedEpochMs > 0L) {
                    previousSummary.copy(
                        connectionState = VcConnectionState.STALE,
                        statusMessage = "Offline · ${msg.take(48)}",
                        isVcInstalled = isVcInstalled,
                        installedVcPackage = installedVcPackage,
                        isVcInForeground = isVcInForeground
                    )
                } else {
                    VcLiveSummary(
                        connectionState = VcConnectionState.ERROR,
                        statusMessage = msg.take(64),
                        isVcInstalled = isVcInstalled,
                        installedVcPackage = installedVcPackage,
                        isVcInForeground = isVcInForeground
                    )
                }
            }
        }
    }

    fun buildAttentionQuery(cursor: String?, rosterTag: String?): String {
        if (cursor.isNullOrBlank()) return ""
        val encodedCursor = urlEncode(cursor)
        val rosterPart = if (!rosterTag.isNullOrBlank()) {
            "&rosterTag=${urlEncode(rosterTag)}"
        } else {
            ""
        }
        return "?since=$encodedCursor$rosterPart"
    }

    /**
     * Parses a VC `GET /api/attention` JSON payload (either full or delta) and merges it with
     * [previousSummary] following `AttentionPoll.java` and `WidgetSnapshot.java` rules.
     */
    fun parseAndMergeAttention(
        rawJson: String,
        previousSummary: VcLiveSummary? = null,
        nowMs: Long = System.currentTimeMillis(),
        isVcInstalled: Boolean = false,
        installedVcPackage: String? = null,
        isVcInForeground: Boolean = false
    ): VcLiveSummary {
        val root = parseJsonObjectMap(rawJson)
        val cursor = (root["cursor"] as? String)?.takeIf { it.isNotEmpty() }
        val rosterTag = (root["rosterTag"] as? String)?.takeIf { it.isNotEmpty() }
        val reset = root["reset"] == true
        val hadPreviousCursor = !previousSummary?.cursor.isNullOrBlank()
        val isFullReplace = !hadPreviousCursor || reset || cursor == null

        // Merge attention events by ID
        val mergedEventsMap = LinkedHashMap<String, VcAttentionItem>()
        if (!isFullReplace && previousSummary != null) {
            for (item in previousSummary.attentionItems) {
                mergedEventsMap[item.id] = item
            }
        }

        val rawEvents = root["events"] as? List<*> ?: emptyList<Any?>()
        for (entry in rawEvents) {
            val obj = entry as? Map<*, *> ?: continue
            val id = obj["id"]?.toString()?.trim().orEmpty()
            if (id.isEmpty()) continue
            val sessionId = obj["sessionId"]?.toString()?.trim().orEmpty()
            val projectId = obj["projectId"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "general"
            val name = obj["name"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "Agent"
            val project = (obj["project"] ?: obj["projectName"])?.toString()?.trim()
                .takeUnless { it.isNullOrEmpty() } ?: "General"
            val kind = VcAttentionKind.fromWire(obj["kind"]?.toString())
            val text = (obj["text"] ?: obj["title"] ?: obj["summary"])?.toString()?.trim().orEmpty()
            val createdAt = toLong(obj["createdAt"], nowMs)
            val read = obj["read"] == true
            val seq = toLong(obj["seq"], 0L)

            if (read) {
                mergedEventsMap.remove(id)
            } else {
                mergedEventsMap[id] = VcAttentionItem(
                    id = id,
                    sessionId = sessionId,
                    agentName = name,
                    projectName = project,
                    kind = kind,
                    text = text.ifEmpty { kind.label },
                    createdAt = createdAt,
                    read = false,
                    seq = seq,
                    projectId = projectId
                )
            }
        }

        val removedIds = root["removed"] as? List<*> ?: emptyList<Any?>()
        for (removed in removedIds) {
            val id = removed?.toString()?.trim().orEmpty()
            if (id.isNotEmpty()) mergedEventsMap.remove(id)
        }

        // Order unread events: urgent (approvals/failures) first, then newest first
        val allUnreadEvents = mergedEventsMap.values
            .filter { !it.read && it.isActionable(nowMs) }
            .sortedWith(
                compareByDescending<VcAttentionItem> { it.kind == VcAttentionKind.APPROVAL }
                    .thenByDescending { it.kind == VcAttentionKind.FAILED || it.kind == VcAttentionKind.INTERRUPTED }
                    .thenByDescending { it.createdAt }
            )
            .take(MAX_READ_EVENTS_KEPT)

        // Parse working list (always sent by /api/attention when supported)
        val workingSessionIds = LinkedHashSet<String>()
        val workingAgentsById = LinkedHashMap<String, VcAgentStatus>()
        val rawWorking = root["working"] as? List<*>
        if (rawWorking != null) {
            for (w in rawWorking) {
                val obj = w as? Map<*, *> ?: continue
                val sid = (obj["id"] ?: obj["sessionId"])?.toString()?.trim().orEmpty()
                if (sid.isEmpty()) continue
                val name = obj["name"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "Agent"
                val project = obj["project"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "General"
                workingSessionIds.add(sid)
                workingAgentsById[sid] = VcAgentStatus(
                    sessionId = sid,
                    name = name,
                    project = project,
                    role = "worker",
                    status = "working"
                )
            }
        }

        val unreadBySession = allUnreadEvents.groupBy { it.sessionId }

        // Parse roster (omitted in delta reply when rosterTag matches previousSummary.rosterTag)
        val rawRoster = root["roster"] as? List<*>
        val baseRoster: List<VcAgentStatus> = if (rawRoster != null) {
            rawRoster.mapNotNull { r ->
                val obj = r as? Map<*, *> ?: return@mapNotNull null
                val sid = (obj["id"] ?: obj["sessionId"])?.toString()?.trim().orEmpty()
                if (sid.isEmpty()) return@mapNotNull null
                val role = obj["role"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "worker"
                val name = obj["name"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: if (role == "general") "General" else "Agent"
                val project = obj["project"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "General"
                val projectId = obj["projectId"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "general"
                val status = obj["status"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "idle"
                val approvals = toInt(obj["approvals"], 0).coerceAtLeast(0)
                val queued = toInt(obj["queuedCount"], 0).coerceAtLeast(0)
                VcAgentStatus(
                    sessionId = sid,
                    name = name,
                    project = project,
                    role = role,
                    status = status,
                    approvalCount = approvals,
                    queuedCount = queued,
                    projectId = projectId
                )
            }
        } else {
            previousSummary?.activeAgents ?: emptyList()
        }

        // Enrich roster with live working state & unread attention flags
        val combinedAgentsMap = LinkedHashMap<String, VcAgentStatus>()
        for (agent in baseRoster) {
            val sessionEvents = unreadBySession[agent.sessionId].orEmpty()
            val eventApprovals = sessionEvents.count { it.kind == VcAttentionKind.APPROVAL }
            val hasFailedEvent = sessionEvents.any {
                it.kind == VcAttentionKind.FAILED || it.kind == VcAttentionKind.INTERRUPTED
            }
            val effectiveWorking = if (rawWorking != null) {
                agent.sessionId in workingSessionIds
            } else {
                agent.isWorking
            }
            val effectiveStatus = when {
                effectiveWorking -> "working"
                hasFailedEvent -> "failed"
                rawWorking != null && agent.status.equals("working", ignoreCase = true) -> "idle"
                else -> agent.status
            }
            val effectiveApprovals = if (rawRoster != null) {
                maxOf(agent.approvalCount, eventApprovals)
            } else {
                eventApprovals
            }
            combinedAgentsMap[agent.sessionId] = agent.copy(
                status = effectiveStatus,
                approvalCount = effectiveApprovals,
                hasUnreadUpdate = sessionEvents.isNotEmpty()
            )
        }

        // Ensure any working agent not yet in roster is included
        for ((sid, workingAgent) in workingAgentsById) {
            if (!combinedAgentsMap.containsKey(sid)) {
                val sessionEvents = unreadBySession[sid].orEmpty()
                combinedAgentsMap[sid] = workingAgent.copy(
                    approvalCount = sessionEvents.count { it.kind == VcAttentionKind.APPROVAL },
                    hasUnreadUpdate = sessionEvents.isNotEmpty()
                )
            }
        }

        // Ensure any agent with unread attention events is represented
        for ((sid, events) in unreadBySession) {
            if (sid.isNotEmpty() && !combinedAgentsMap.containsKey(sid)) {
                val first = events.first()
                val approvals = events.count { it.kind == VcAttentionKind.APPROVAL }
                val failed = events.any {
                    it.kind == VcAttentionKind.FAILED || it.kind == VcAttentionKind.INTERRUPTED
                }
                combinedAgentsMap[sid] = VcAgentStatus(
                    sessionId = sid,
                    name = first.agentName,
                    project = first.projectName,
                    status = when {
                        approvals > 0 -> "approval"
                        failed -> "failed"
                        else -> "completed"
                    },
                    approvalCount = approvals,
                    hasUnreadUpdate = true
                )
            }
        }

        val sortedAgents = combinedAgentsMap.values.sortedWith(
            compareBy<VcAgentStatus> { it.priorityOrder }
                .thenBy { it.project.lowercase() }
                .thenBy { it.name.lowercase() }
        )

        val workingCount = if (rawWorking != null) {
            workingSessionIds.size
        } else {
            sortedAgents.count { it.isWorking }
        }

        // Counts follow WidgetSnapshot.java: count unique agents needing approval / updates
        val approvalAgents = HashSet<String>()
        val failedAgents = HashSet<String>()
        val unreadAgents = HashSet<String>()

        for (event in allUnreadEvents) {
            val key = event.sessionId.ifEmpty { event.id }
            unreadAgents.add(key)
            if (event.kind == VcAttentionKind.APPROVAL) {
                approvalAgents.add(key)
            }
            if (event.kind == VcAttentionKind.FAILED || event.kind == VcAttentionKind.INTERRUPTED) {
                failedAgents.add(key)
            }
        }
        for (agent in sortedAgents) {
            if (agent.approvalCount > 0) {
                approvalAgents.add(agent.sessionId)
                unreadAgents.add(agent.sessionId)
            }
            if (agent.isFailed) {
                failedAgents.add(agent.sessionId)
            }
        }

        val runningCount = toInt(root["running"], workingCount).coerceAtLeast(workingCount)
        val nextWakeAt = if (root["nextWakeAt"] != null) toLong(root["nextWakeAt"], 0L).takeIf { it > 0L } else null

        val summary = VcLiveSummary(
            workingCount = workingCount,
            approvalCount = approvalAgents.size,
            unreadCount = unreadAgents.size,
            failedCount = failedAgents.size,
            runningCount = runningCount,
            attentionItems = allUnreadEvents,
            activeAgents = sortedAgents,
            cursor = cursor,
            rosterTag = if (cursor == null) null else (rosterTag ?: previousSummary?.rosterTag),
            nextWakeAt = nextWakeAt,
            lastUpdatedEpochMs = nowMs,
            connectionState = VcConnectionState.LIVE,
            statusMessage = "Connected",
            isVcInstalled = isVcInstalled,
            installedVcPackage = installedVcPackage,
            isVcInForeground = isVcInForeground
        )
        return summary.copy(statusMessage = summary.headlineSummary)
    }

    /**
     * Parses a VC `GET /api/snapshot` JSON payload (array of sessions or `{sessions: [...]}`)
     * as a fallback if `/api/attention` is unavailable.
     */
    fun parseSnapshotResponse(
        rawJson: String,
        nowMs: Long = System.currentTimeMillis(),
        isVcInstalled: Boolean = false,
        installedVcPackage: String? = null,
        isVcInForeground: Boolean = false
    ): VcLiveSummary {
        val trimmed = rawJson.trim()
        val sessionsList: List<*> = if (trimmed.startsWith("[")) {
            parseJsonArrayList(trimmed)
        } else {
            val map = parseJsonObjectMap(trimmed)
            (map["sessions"] as? List<*>) ?: emptyList<Any?>()
        }

        val agents = mutableListOf<VcAgentStatus>()
        val attentionItems = mutableListOf<VcAttentionItem>()

        for (rawSession in sessionsList) {
            val s = rawSession as? Map<*, *> ?: continue
            if (s["archived"] == true) continue
            val sid = s["id"]?.toString()?.trim().orEmpty()
            if (sid.isEmpty()) continue
            val role = s["role"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "worker"
            val name = s["name"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                ?: if (role == "general") "General" else "Agent"
            val project = s["projectName"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "General"
            val status = s["status"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "idle"
            val rawApprovals = s["approvals"] as? List<*> ?: emptyList<Any?>()
            val queuedMessages = s["queuedMessages"] as? List<*> ?: emptyList<Any?>()

            for (rawApp in rawApprovals) {
                val appObj = rawApp as? Map<*, *> ?: continue
                val appId = appObj["id"]?.toString()?.trim().orEmpty().ifEmpty { UUID.randomUUID().toString() }
                val reason = appObj["reason"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: "Approval requested"
                attentionItems.add(
                    VcAttentionItem(
                        id = "approval:$sid:$appId",
                        sessionId = sid,
                        agentName = name,
                        projectName = project,
                        kind = VcAttentionKind.APPROVAL,
                        text = reason,
                        createdAt = nowMs,
                        read = false
                    )
                )
            }

            if (status.equals("failed", ignoreCase = true) || status.equals("interrupted", ignoreCase = true)) {
                val errText = s["error"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: "Agent $status"
                attentionItems.add(
                    VcAttentionItem(
                        id = "status:$sid:$status",
                        sessionId = sid,
                        agentName = name,
                        projectName = project,
                        kind = VcAttentionKind.fromWire(status),
                        text = errText,
                        createdAt = nowMs,
                        read = false
                    )
                )
            }

            agents.add(
                VcAgentStatus(
                    sessionId = sid,
                    name = name,
                    project = project,
                    role = role,
                    status = status,
                    approvalCount = rawApprovals.size,
                    queuedCount = queuedMessages.size,
                    hasUnreadUpdate = rawApprovals.isNotEmpty()
                )
            )
        }

        val sortedAgents = agents.sortedBy { it.priorityOrder }
        val workingCount = sortedAgents.count { it.isWorking }
        val approvalCount = sortedAgents.count { it.needsApproval }
        val failedCount = sortedAgents.count { it.isFailed }

        val summary = VcLiveSummary(
            workingCount = workingCount,
            approvalCount = approvalCount,
            unreadCount = attentionItems.map { it.sessionId }.distinct().size,
            failedCount = failedCount,
            runningCount = workingCount,
            attentionItems = attentionItems,
            activeAgents = sortedAgents,
            lastUpdatedEpochMs = nowMs,
            connectionState = VcConnectionState.LIVE,
            isVcInstalled = isVcInstalled,
            installedVcPackage = installedVcPackage,
            isVcInForeground = isVcInForeground
        )
        return summary.copy(statusMessage = summary.headlineSummary)
    }

    /**
     * Computes which attention items should raise a background Android notification.
     *
     * Rules:
     * 1. Only unread, actionable attention items not present in [previousSeenUnreadIds] qualify.
     * 2. If [isVcInForeground] is `true`, no background notifications are emitted (VC is already visible),
     *    and current unread IDs are marked as seen so leaving VC does not re-notify old items.
     * 3. If [isInitialSeed] is `true` (first poll after launcher startup), existing unread items are
     *    recorded as baseline seen IDs without spamming notifications.
     */
    fun computeNewBackgroundAlerts(
        previousSeenUnreadIds: Set<String>,
        currentSummary: VcLiveSummary,
        isVcInForeground: Boolean,
        isInitialSeed: Boolean = false
    ): VcAlertDiffResult {
        val currentUnread = currentSummary.attentionItems.filter { !it.read }
        val currentUnreadIds = currentUnread.map { it.id }.toSet()

        if (isInitialSeed || isVcInForeground) {
            return VcAlertDiffResult(
                alertsToNotify = emptyList(),
                updatedSeenUnreadIds = (previousSeenUnreadIds intersect currentUnreadIds) + currentUnreadIds
            )
        }

        val newAlerts = currentUnread
            .filter { it.id !in previousSeenUnreadIds }
            .sortedWith(
                compareByDescending<VcAttentionItem> { it.kind.isUrgent }
                    .thenByDescending { it.createdAt }
            )

        val updatedSeen = (previousSeenUnreadIds intersect currentUnreadIds) + currentUnreadIds
        return VcAlertDiffResult(
            alertsToNotify = newAlerts,
            updatedSeenUnreadIds = updatedSeen
        )
    }

    /**
     * Sends a quick message or live steer instruction directly to a VC agent session from the
     * Launcher Home Screen without needing to bring VC to the foreground.
     */
    fun sendQuickMessage(
        config: VcConnectionConfig,
        sessionId: String?,
        text: String,
        steerIfWorking: Boolean = false,
        activeAgents: List<VcAgentStatus> = emptyList()
    ): Result<String> {
        val cleanedText = text.trim()
        if (cleanedText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message text cannot be empty"))
        }
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("VC host URL is not configured"))
        }

        return try {
            val baseUrl = config.normalizedServerUrl
            val targetSessionId = resolveTargetSessionId(baseUrl, config.authToken, sessionId, activeAgents)
            val targetAgent = activeAgents.firstOrNull { it.sessionId == targetSessionId }
            val endpointAction = if (steerIfWorking && targetAgent?.isWorking == true) "steer" else "message"

            val payload = buildJsonObjectString(
                mapOf(
                    "text" to cleanedText,
                    "requestId" to UUID.randomUUID().toString()
                )
            )
            executeHttp(
                urlString = "$baseUrl/api/sessions/${urlEncode(targetSessionId)}/$endpointAction",
                method = "POST",
                authToken = config.authToken,
                body = payload
            )
            val targetLabel = targetAgent?.name ?: "General"
            Result.success(if (endpointAction == "steer") "Steered $targetLabel" else "Sent to $targetLabel")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marks a single attention item, a session's seen timestamp, or all attention items as read
     * on the VC host (`POST /api/attention`).
     */
    fun markAttentionRead(
        config: VcConnectionConfig,
        eventId: String? = null,
        markAll: Boolean = false,
        seenSessionId: String? = null,
        seenUpToMs: Long? = null
    ): Boolean {
        if (!config.isConfigured) return false
        val body = when {
            markAll -> "{\"all\":true}"
            !eventId.isNullOrBlank() -> buildJsonObjectString(mapOf("id" to eventId))
            !seenSessionId.isNullOrBlank() -> {
                val upTo = seenUpToMs ?: System.currentTimeMillis()
                "{\"seen\":{\"sessionId\":\"${escapeJson(seenSessionId)}\",\"upTo\":$upTo}}"
            }
            else -> return false
        }
        return try {
            executeHttp(
                urlString = "${config.normalizedServerUrl}/api/attention",
                method = "POST",
                authToken = config.authToken,
                body = body
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveTargetSessionId(
        baseUrl: String,
        authToken: String,
        requestedSessionId: String?,
        activeAgents: List<VcAgentStatus>
    ): String {
        if (!requestedSessionId.isNullOrBlank()) return requestedSessionId
        val generalAgent = activeAgents.firstOrNull {
            it.role.equals("general", ignoreCase = true) || it.name.equals("General", ignoreCase = true)
        }
        if (generalAgent != null) return generalAgent.sessionId
        if (activeAgents.isNotEmpty()) return activeAgents.first().sessionId

        // Ask VC host to ensure/return the General session via POST /api/general
        val rawGeneral = executeHttp(
            urlString = "$baseUrl/api/general",
            method = "POST",
            authToken = authToken,
            body = "{}"
        )
        val map = parseJsonObjectMap(rawGeneral)
        val id = map["id"]?.toString()?.trim().orEmpty()
        if (id.isNotEmpty()) return id
        throw IllegalStateException("No active VC agent session found")
    }

    /**
     * Resolves whichever VC Android package (`dev.agentconversation.preview`, `dev.agentconversation`,
     * `com.example.agentconversation`, etc.) is currently installed on the device.
     */
    fun resolveInstalledVcPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in VcPackages.ALL_VC_PACKAGES) {
            try {
                if (pm.getLaunchIntentForPackage(pkg) != null) {
                    return pkg
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Opens the VC Android app (`dev.agentconversation.vci` or `dev.agentconversation.preview`
     * via `SystemTweaker.launchVc` or package launch intent, optionally starting voice Talk mode
     * via `dev.agentconversation.widget.TALK`, or falls back to opening the VC web UI in browser).
     */
    fun launchVcApp(
        context: Context,
        config: VcConnectionConfig,
        sessionId: String? = null,
        projectId: String? = null,
        startVoiceTalk: Boolean = false
    ): Boolean {
        if (SystemTweaker.launchVc(
                context = context,
                sessionId = sessionId,
                projectId = projectId,
                startVoiceTalk = startVoiceTalk,
                adjacentSplitScreen = false
            )
        ) {
            return true
        }

        val pkg = resolveInstalledVcPackage(context)
        if (pkg != null) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    if (startVoiceTalk) {
                        action = VcPackages.TALK_ACTION
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    if (!sessionId.isNullOrBlank()) {
                        putExtra("sessionId", sessionId)
                        putExtra(VcPackages.EXTRA_SESSION_ID, sessionId)
                    }
                    if (!projectId.isNullOrBlank()) {
                        putExtra("projectId", projectId)
                        putExtra(VcPackages.EXTRA_PROJECT_ID, projectId)
                    }
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    return true
                }
            } catch (_: Exception) {
            }
        }

        // Fallback: open VC Web UI in browser
        if (config.isConfigured) {
            return try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(config.normalizedServerUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    /**
     * Launches a 1-tap Z Fold 8 Split-Screen workspace pairing VC (`dev.agentconversation*`) on one
     * half of the inner display with Termux (`com.termux`) or Chrome/Browser on the adjacent half.
     */
    fun launchVcSplitScreenWorkspace(
        context: Context,
        config: VcConnectionConfig,
        preferredSecondaryPackage: String? = null
    ): Boolean {
        val primaryLaunched = launchVcApp(context, config)
        val pm = context.packageManager

        val secondaryCandidates = listOfNotNull(
            preferredSecondaryPackage,
            VcPackages.TERMUX_PACKAGE,
            VcPackages.CHROME_PACKAGE,
            "com.sec.android.app.sbrowser"
        )
        var secondaryIntent: Intent? = null
        for (candidate in secondaryCandidates) {
            try {
                val intent = pm.getLaunchIntentForPackage(candidate)
                if (intent != null) {
                    secondaryIntent = intent.apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        )
                    }
                    break
                }
            } catch (_: Exception) {
            }
        }

        if (secondaryIntent == null && config.isConfigured) {
            secondaryIntent = Intent(Intent.ACTION_VIEW, Uri.parse(config.normalizedServerUrl)).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
            }
        }

        if (secondaryIntent != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    context.startActivity(secondaryIntent)
                } catch (_: Exception) {
                }
            }, 140L)
            return true
        }
        return primaryLaunched
    }

    private fun executeHttp(
        urlString: String,
        method: String = "GET",
        authToken: String = "",
        body: String? = null
    ): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            val cleanedToken = authToken.trim()
            if (cleanedToken.isNotEmpty()) {
                val cookieValue = if (cleanedToken.startsWith("agent_session=")) {
                    cleanedToken
                } else {
                    "agent_session=$cleanedToken"
                }
                setRequestProperty("Cookie", cookieValue)
                setRequestProperty("Authorization", "Bearer ${cleanedToken.removePrefix("agent_session=")}")
            }
        }

        try {
            if (body != null && method == "POST") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                connection.outputStream.use { out: OutputStream ->
                    out.write(bytes)
                }
            }

            val status = connection.responseCode
            val stream: InputStream? = if (status >= 400) connection.errorStream else connection.inputStream
            val responseText = stream?.use { input ->
                val bytes = input.readNBytes(MAX_RESPONSE_BYTES)
                String(bytes, StandardCharsets.UTF_8)
            }.orEmpty()

            if (status !in 200..299) {
                throw IllegalStateException("HTTP $status: ${responseText.take(120)}")
            }
            return responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun toLong(value: Any?, default: Long): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull() ?: default
            else -> default
        }
    }

    private fun toInt(value: Any?, default: Int): Int {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull() ?: default
            else -> default
        }
    }

    private fun buildJsonObjectString(fields: Map<String, String>): String {
        return try {
            val json = JSONObject()
            for ((k, v) in fields) {
                json.put(k, v)
            }
            val result = json.toString()
            if (result.isNullOrEmpty()) throw RuntimeException("Stub")
            result
        } catch (_: Throwable) {
            val entries = fields.entries.joinToString(",") { (k, v) ->
                "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
            }
            "{$entries}"
        }
    }

    private fun escapeJson(s: String): String {
        return buildString(s.length + 8) {
            for (ch in s) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    /**
     * Parses a JSON object string into a `Map<String, Any?>`. Uses Android's `org.json.JSONObject`
     * at runtime and falls back to [SimpleJsonTokenizer] in JVM unit tests where `android.jar` is unmocked.
     */
    internal fun parseJsonObjectMap(raw: String): Map<String, Any?> {
        try {
            val json = JSONObject(raw)
            val keys = json.keys()
            if (keys != null) {
                return jsonObjectToMap(json)
            }
        } catch (_: Throwable) {
            // Fall through to pure-Kotlin parser for JVM unit tests
        }
        val parsed = SimpleJsonTokenizer(raw).parseValue()
        @Suppress("UNCHECKED_CAST")
        return (parsed as? Map<String, Any?>) ?: emptyMap()
    }

    internal fun parseJsonArrayList(raw: String): List<Any?> {
        try {
            val arr = JSONArray(raw)
            if (arr.length() >= 0) {
                return jsonArrayToList(arr)
            }
        } catch (_: Throwable) {
            // Fall through to pure-Kotlin parser for JVM unit tests
        }
        val parsed = SimpleJsonTokenizer(raw).parseValue()
        @Suppress("UNCHECKED_CAST")
        return (parsed as? List<Any?>) ?: emptyList()
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        val iter = obj.keys()
        while (iter.hasNext()) {
            val key = iter.next()
            if (obj.isNull(key)) {
                map[key] = null
            } else {
                val v = obj.opt(key)
                map[key] = when (v) {
                    is JSONObject -> jsonObjectToMap(v)
                    is JSONArray -> jsonArrayToList(v)
                    JSONObject.NULL -> null
                    else -> v
                }
            }
        }
        return map
    }

    private fun jsonArrayToList(arr: JSONArray): List<Any?> {
        val list = ArrayList<Any?>(arr.length())
        for (i in 0 until arr.length()) {
            if (arr.isNull(i)) {
                list.add(null)
            } else {
                val v = arr.opt(i)
                list.add(
                    when (v) {
                        is JSONObject -> jsonObjectToMap(v)
                        is JSONArray -> jsonArrayToList(v)
                        JSONObject.NULL -> null
                        else -> v
                    }
                )
            }
        }
        return list
    }

    /**
     * Lightweight recursive-descent JSON parser with zero external dependencies.
     */
    private class SimpleJsonTokenizer(private val input: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= input.length) return null
            return when (val c = input[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected JSON char '$c' at $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // '{'
            skipWhitespace()
            if (pos < input.length && input[pos] == '}') {
                pos++
                return map
            }
            while (pos < input.length) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                if (pos < input.length && input[pos] == ':') pos++
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos < input.length && input[pos] == ',') {
                    pos++
                } else if (pos < input.length && input[pos] == '}') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // '['
            skipWhitespace()
            if (pos < input.length && input[pos] == ']') {
                pos++
                return list
            }
            while (pos < input.length) {
                list.add(parseValue())
                skipWhitespace()
                if (pos < input.length && input[pos] == ',') {
                    pos++
                } else if (pos < input.length && input[pos] == ']') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return list
        }

        private fun parseString(): String {
            if (pos < input.length && input[pos] == '"') pos++
            val sb = StringBuilder()
            while (pos < input.length) {
                val c = input[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\' && pos < input.length) {
                    when (val esc = input[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 <= input.length) {
                                val hex = input.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun parseBoolean(): Boolean {
            return if (input.startsWith("true", pos)) {
                pos += 4
                true
            } else {
                pos += 5
                false
            }
        }

        private fun parseNull(): Any? {
            pos += 4
            return null
        }

        private fun parseNumber(): Number {
            val start = pos
            while (pos < input.length && input[pos] in "-+0123456789.eE") {
                pos++
            }
            val token = input.substring(start, pos)
            return if ('.' in token || 'e' in token || 'E' in token) {
                token.toDoubleOrNull() ?: 0.0
            } else {
                token.toLongOrNull() ?: 0L
            }
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
