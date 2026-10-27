package com.xsun.foldlauncher.vc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VcSurfaceDark = Color(0xFF11171C)
private val VcBorderLine = Color(0xFF2A343C)
private val VcOpSurface = Color(0xFF172D28)
private val VcMintAccent = Color(0xFFB9E6C8)

/**
 * 120Hz Jetpack Compose VC Command Center Card for the Fold8 & Slab Launcher Home Screen.
 *
 * Provides live visibility and 1-tap control when VC is not in the foreground:
 * - Live counts: Working / Needs Approval / Unread Updates / Failed
 * - Active agent pills with project & status indicators
 * - Top attention feed items (approvals, failures, completed turns) with 1-tap Open & Acknowledge
 * - 1-tap "Open VC" and "Split-Screen VC" (Z Fold 8 inner screen multitasking)
 * - Inline Quick-Send / Live Steer composer directly to VC agents without leaving the Home screen
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VcCommandCenterCard(
    summary: VcLiveSummary,
    config: VcConnectionConfig,
    lastSendFeedback: String?,
    onOpenVc: (sessionId: String?) -> Unit,
    onSplitScreenVc: () -> Unit,
    onRefreshVc: () -> Unit,
    onSendQuickMessage: (sessionId: String?, text: String, steerIfWorking: Boolean) -> Unit,
    onMarkAttentionRead: (eventId: String?, markAll: Boolean) -> Unit,
    onUpdateConfig: (serverUrl: String, authToken: String, monitorEnabled: Boolean, notifyBackground: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onTalkVc: ((sessionId: String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(22.dp)
    var showConfigEditor by remember { mutableStateOf(false) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var quickMessageDraft by remember { mutableStateOf("") }

    var draftServerUrl by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var draftAuthToken by remember(config.authToken) { mutableStateOf(config.authToken) }
    var draftNotifyBackground by remember(config.notifyWhenBackgrounded) {
        mutableStateOf(config.notifyWhenBackgrounded)
    }

    val selectedAgent = remember(summary.activeAgents, selectedSessionId) {
        summary.activeAgents.firstOrNull { it.sessionId == selectedSessionId }
            ?: summary.activeAgents.firstOrNull { it.role.equals("general", ignoreCase = true) }
            ?: summary.activeAgents.firstOrNull()
    }

    val borderColor = when {
        summary.approvalCount > 0 -> FoldLauncherColors.AmberWarning
        summary.failedCount > 0 -> FoldLauncherColors.CoralDanger
        summary.workingCount > 0 -> VcMintAccent
        else -> VcBorderLine
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        VcSurfaceDark.copy(alpha = 0.96f),
                        VcOpSurface.copy(alpha = 0.88f)
                    )
                )
            )
            .border(1.dp, borderColor, cardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header Row: VC Command Center Title + Live Connection Badge + Config Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            when (summary.connectionState) {
                                VcConnectionState.LIVE -> if (summary.hasUrgentAttention) {
                                    FoldLauncherColors.AmberWarning
                                } else {
                                    FoldLauncherColors.EmeraldSuccess
                                }
                                VcConnectionState.CONNECTING -> FoldLauncherColors.ElectricCyan
                                VcConnectionState.STALE -> FoldLauncherColors.AmberWarning
                                VcConnectionState.ERROR,
                                VcConnectionState.UNCONFIGURED -> FoldLauncherColors.CoralDanger
                            }
                        )
                )
                Column {
                    Text(
                        text = "VC COMMAND CENTER · BG BRIDGE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.CyanBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildSubheaderText(summary),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = FoldLauncherColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "↻",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.ElectricCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(FoldLauncherColors.GlassSurfaceHighlight)
                        .clickable(onClick = onRefreshVc)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = if (showConfigEditor) "Done" else "Host ⚙",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (showConfigEditor) FoldLauncherColors.CyanBright else FoldLauncherColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (showConfigEditor) FoldLauncherColors.CyanGlow
                            else FoldLauncherColors.GlassSurfaceHighlight
                        )
                        .clickable { showConfigEditor = !showConfigEditor }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 2. Live VC Agent Counters Strip (Working / Needs Approval / Unread / Failed)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VcMetricBadge(
                label = "WORKING",
                count = summary.workingCount,
                activeColor = FoldLauncherColors.ElectricCyan,
                modifier = Modifier.weight(1f)
            )
            VcMetricBadge(
                label = "APPROVE",
                count = summary.approvalCount,
                activeColor = FoldLauncherColors.AmberWarning,
                modifier = Modifier.weight(1f)
            )
            VcMetricBadge(
                label = "UNREAD",
                count = summary.unreadCount,
                activeColor = FoldLauncherColors.EmeraldSuccess,
                modifier = Modifier.weight(1f)
            )
            VcMetricBadge(
                label = "FAILED",
                count = summary.failedCount,
                activeColor = FoldLauncherColors.CoralDanger,
                modifier = Modifier.weight(1f)
            )
        }

        // 3. 1-Tap Workspace Actions (Talk / Open VC / Split-Screen VC / Ack All)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VcActionPill(
                text = "🎙 Talk",
                emphasized = true,
                onClick = {
                    val sid = selectedAgent?.sessionId
                    if (onTalkVc != null) {
                        onTalkVc(sid)
                    } else {
                        SystemTweaker.launchVc(
                            context = context,
                            sessionId = sid,
                            projectId = selectedAgent?.projectId,
                            startVoiceTalk = true,
                            adjacentSplitScreen = false
                        )
                    }
                }
            )
            VcActionPill(
                text = if (summary.approvalCount > 0) {
                    "⚡ Open VC (${summary.approvalCount} Approve)"
                } else {
                    "↗ Open VC"
                },
                emphasized = summary.approvalCount > 0,
                onClick = { onOpenVc(selectedAgent?.sessionId) }
            )
            VcActionPill(
                text = "◫ Split-Screen VC",
                emphasized = false,
                onClick = onSplitScreenVc
            )
            if (summary.attentionItems.isNotEmpty()) {
                VcActionPill(
                    text = "✓ Mark All Read",
                    emphasized = false,
                    onClick = { onMarkAttentionRead(null, true) }
                )
            }
        }

        // 4. Active Agents Strip (Tap to target for inline quick-send or double-tap to open)
        if (summary.activeAgents.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                summary.activeAgents.take(8).forEach { agent ->
                    val isSelected = selectedAgent?.sessionId == agent.sessionId
                    val dotColor = when {
                        agent.needsApproval -> FoldLauncherColors.AmberWarning
                        agent.isWorking -> FoldLauncherColors.ElectricCyan
                        agent.isFailed -> FoldLauncherColors.CoralDanger
                        agent.hasUnreadUpdate -> FoldLauncherColors.EmeraldSuccess
                        else -> FoldLauncherColors.TextMuted
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) FoldLauncherColors.CyanGlow
                                else FoldLauncherColors.GlassSurfaceHighlight
                            )
                            .border(
                                1.dp,
                                if (isSelected) FoldLauncherColors.ElectricCyan
                                else FoldLauncherColors.GlassBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (selectedSessionId == agent.sessionId) {
                                    onOpenVc(agent.sessionId)
                                } else {
                                    selectedSessionId = agent.sessionId
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = "${agent.name} · ${agent.project}",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) FoldLauncherColors.CyanBright else FoldLauncherColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 5. Top Actionable Attention Feed Items (Approvals / Failures / Recent Completions)
        if (summary.attentionItems.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ATTENTION FEED (${summary.attentionItems.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (summary.hasUrgentAttention) {
                        FoldLauncherColors.AmberWarning
                    } else {
                        FoldLauncherColors.CyanBright
                    }
                )
                summary.attentionItems.take(3).forEach { item ->
                    VcAttentionRow(
                        item = item,
                        onOpen = {
                            selectedSessionId = item.sessionId
                            onOpenVc(item.sessionId)
                        },
                        onAcknowledge = {
                            onMarkAttentionRead(item.id, false)
                        }
                    )
                }
            }
        }

        // 6. Inline Quick-Send / Live Steer Bar right on the Home Screen
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val targetName = selectedAgent?.let { "${it.name} (${it.project})" } ?: "General"
                val modeTag = if (selectedAgent?.isWorking == true) "LIVE STEER" else "QUICK SEND"
                Text(
                    text = "$modeTag → $targetName",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!lastSendFeedback.isNullOrBlank()) {
                    Text(
                        text = lastSendFeedback,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (lastSendFeedback.startsWith("✓")) {
                            FoldLauncherColors.EmeraldSuccess
                        } else {
                            FoldLauncherColors.AmberWarning
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp, max = 68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.72f))
                        .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (quickMessageDraft.isEmpty()) {
                        Text(
                            text = if (selectedAgent?.isWorking == true) {
                                "Steer running VC agent from Home screen…"
                            } else {
                                "Message VC agent without leaving Home…"
                            },
                            fontSize = 12.sp,
                            color = FoldLauncherColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = quickMessageDraft,
                        onValueChange = { quickMessageDraft = it },
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = FoldLauncherColors.TextPrimary
                        ),
                        cursorBrush = SolidColor(FoldLauncherColors.ElectricCyan),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val canSend = quickMessageDraft.trim().isNotEmpty()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (canSend) FoldLauncherColors.ElectricCyan
                            else FoldLauncherColors.GlassSurfaceHighlight
                        )
                        .clickable(enabled = canSend) {
                            val textToSend = quickMessageDraft.trim()
                            if (textToSend.isNotEmpty()) {
                                quickMessageDraft = ""
                                onSendQuickMessage(
                                    selectedAgent?.sessionId,
                                    textToSend,
                                    selectedAgent?.isWorking == true
                                )
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedAgent?.isWorking == true) "Steer" else "Send",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSend) Color(0xFF02131D) else FoldLauncherColors.TextMuted
                    )
                }
            }
        }

        // 7. Collapsible Host & Background Notification Configuration Drawer
        if (showConfigEditor) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.82f))
                    .border(1.dp, FoldLauncherColors.GlassBorderStrong, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "VC HOST CONNECTION & BACKGROUND NOTIFICATIONS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.CyanBright
                )

                Text(
                    text = "Server URL (e.g. http://100.96.188.90:8080 Tailscale or http://127.0.0.1:4327)",
                    fontSize = 10.sp,
                    color = FoldLauncherColors.TextSecondary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FoldLauncherColors.GlassSurface)
                        .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    BasicTextField(
                        value = draftServerUrl,
                        onValueChange = { draftServerUrl = it },
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = FoldLauncherColors.TextPrimary
                        ),
                        cursorBrush = SolidColor(FoldLauncherColors.ElectricCyan),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = "Device Token / agent_session Cookie (optional for local loopback)",
                    fontSize = 10.sp,
                    color = FoldLauncherColors.TextSecondary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FoldLauncherColors.GlassSurface)
                        .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    if (draftAuthToken.isEmpty()) {
                        Text(
                            text = "Leave blank on local loopback or paste agent_session token…",
                            fontSize = 11.sp,
                            color = FoldLauncherColors.TextMuted
                        )
                    }
                    BasicTextField(
                        value = draftAuthToken,
                        onValueChange = { draftAuthToken = it },
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = FoldLauncherColors.TextPrimary
                        ),
                        cursorBrush = SolidColor(FoldLauncherColors.ElectricCyan),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (draftNotifyBackground) {
                            "🔔 BG Alerts When VC Not Foreground: ON"
                        } else {
                            "🔕 BG Alerts When VC Not Foreground: OFF"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (draftNotifyBackground) {
                            FoldLauncherColors.EmeraldSuccess
                        } else {
                            FoldLauncherColors.TextSecondary
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { draftNotifyBackground = !draftNotifyBackground }
                            .padding(vertical = 4.dp)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(FoldLauncherColors.ElectricCyan)
                            .clickable {
                                onUpdateConfig(
                                    draftServerUrl,
                                    draftAuthToken,
                                    true,
                                    draftNotifyBackground
                                )
                                showConfigEditor = false
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Save & Poll",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF02131D)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VcMetricBadge(
    label: String,
    count: Int,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val active = count > 0
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) activeColor.copy(alpha = 0.16f)
                else FoldLauncherColors.GlassSurfaceHighlight
            )
            .border(
                1.dp,
                if (active) activeColor else FoldLauncherColors.GlassBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (active) activeColor else FoldLauncherColors.TextSecondary
        )
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) FoldLauncherColors.TextPrimary else FoldLauncherColors.TextMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun VcActionPill(
    text: String,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (emphasized) FoldLauncherColors.CyanGlow
                else FoldLauncherColors.GlassSurfaceHighlight
            )
            .border(
                1.dp,
                if (emphasized) FoldLauncherColors.ElectricCyan else FoldLauncherColors.GlassBorder,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (emphasized) FoldLauncherColors.CyanBright else FoldLauncherColors.TextPrimary
        )
    }
}

@Composable
private fun VcAttentionRow(
    item: VcAttentionItem,
    onOpen: () -> Unit,
    onAcknowledge: () -> Unit
) {
    val badgeColor = when (item.kind) {
        VcAttentionKind.APPROVAL -> FoldLauncherColors.AmberWarning
        VcAttentionKind.FAILED, VcAttentionKind.INTERRUPTED -> FoldLauncherColors.CoralDanger
        VcAttentionKind.COMPLETED, VcAttentionKind.UPDATE -> FoldLauncherColors.EmeraldSuccess
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.62f))
            .border(1.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.kind.label.uppercase(Locale.ROOT),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor
                )
                Text(
                    text = "${item.agentName} · ${item.projectName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.text,
                fontSize = 11.sp,
                color = FoldLauncherColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "✓",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = FoldLauncherColors.CyanBright,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(FoldLauncherColors.GlassSurfaceHighlight)
                .clickable(onClick = onAcknowledge)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun buildSubheaderText(summary: VcLiveSummary): String {
    val timePart = if (summary.lastUpdatedEpochMs > 0L) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        "Updated ${fmt.format(Date(summary.lastUpdatedEpochMs))}"
    } else {
        null
    }
    val fgPart = if (summary.isVcInForeground) "VC Foreground" else "BG Guard Active"
    return listOfNotNull(summary.headlineSummary, timePart, fgPart).joinToString(" · ")
}
