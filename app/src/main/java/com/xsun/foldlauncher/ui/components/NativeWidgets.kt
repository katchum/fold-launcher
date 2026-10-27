package com.xsun.foldlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.hud.FloatingHudControlCard
import com.xsun.foldlauncher.hud.FloatingHudController
import com.xsun.foldlauncher.hud.VcHudAgentStatus
import com.xsun.foldlauncher.model.AppPair
import com.xsun.foldlauncher.posture.DevicePostureState
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.spatial.SpatialTiltState
import com.xsun.foldlauncher.spatial.rememberSpatialTiltState
import com.xsun.foldlauncher.spatial.spatialParallaxCard
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.vc.VcBackgroundMonitor
import com.xsun.foldlauncher.vc.VcCommandCenterCard
import com.xsun.foldlauncher.vc.VcConnectionConfig
import com.xsun.foldlauncher.vc.VcLiveSummary
import com.xsun.foldlauncher.vc.VcPackages
import kotlin.math.roundToInt

/**
 * Built-in Z Fold 8 Multi-Window Power Presets (1-tap side-by-side split-screen).
 */
val DefaultZFold8AppPairPresets: List<AppPair> = listOf(
    AppPair(
        id = "preset_vc_termux",
        title = "VC + Termux",
        primaryPackage = VcPackages.PREVIEW,
        secondaryPackage = VcPackages.TERMUX_PACKAGE
    ),
    AppPair(
        id = "preset_vc_prod_compare",
        title = "VC Prod + Compare",
        primaryPackage = VcPackages.RELEASE,
        secondaryPackage = VcPackages.PREVIEW
    ),
    AppPair(
        id = "preset_vc_chrome",
        title = "VC + Chrome",
        primaryPackage = VcPackages.PREVIEW,
        secondaryPackage = VcPackages.CHROME_PACKAGE
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NativeDashboardCards(
    postureState: DevicePostureState,
    batteryPct: Int,
    isCharging: Boolean,
    isAccessibilityActive: Boolean,
    isWriteSecureSettingsGranted: Boolean,
    lockScreenRotationEnabled: Boolean,
    onToggleLockRotation: () -> Unit,
    onEnforceGboardDeX: () -> Unit,
    onLockScreenNow: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onOpenDefaultLauncherPrompt: () -> Unit,
    isDefaultLauncher: Boolean,
    quickNoteText: String,
    onQuickNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    spatialTiltState: SpatialTiltState? = null,
    vcSummaryOverride: VcLiveSummary? = null,
    vcConfigOverride: VcConnectionConfig? = null,
    onOpenSpotlight: (() -> Unit)? = null,
    onToggleRadialArc: (() -> Unit)? = null,
    hingeAngleDegrees: Float? = null,
    customAppPairs: List<AppPair> = emptyList(),
    onLaunchAppPair: ((AppPair) -> Unit)? = null
) {
    val context = LocalContext.current
    val vcMonitor = remember(context) { VcBackgroundMonitor.getInstance(context) }
    val monitorSummary by vcMonitor.liveSummary.collectAsState()
    val monitorConfig by vcMonitor.config.collectAsState()
    val lastSendFeedback by vcMonitor.lastSendFeedback.collectAsState()

    val effectiveVcSummary = vcSummaryOverride ?: monitorSummary
    val effectiveVcConfig = vcConfigOverride ?: monitorConfig

    val sensorHingeAngle = rememberHardwareHingeAngleDegrees()
    val effectiveHingeAngle = hingeAngleDegrees ?: sensorHingeAngle

    val hingeFactor = when {
        postureState.isFlexTabletop -> 0.55f
        postureState.isBookPosture -> 0.35f
        else -> 0f
    }
    val tilt = spatialTiltState ?: rememberSpatialTiltState(
        enabled = true,
        hingeAngleFactor = hingeFactor
    )

    var showFloatingHudConfigurator by remember { mutableStateOf(false) }

    LaunchedEffect(
        effectiveVcSummary.approvalCount,
        effectiveVcSummary.failedCount,
        effectiveVcSummary.workingCount
    ) {
        val mappedStatus = when {
            effectiveVcSummary.approvalCount > 0 -> VcHudAgentStatus.APPROVAL_NEEDED
            effectiveVcSummary.failedCount > 0 -> VcHudAgentStatus.ERROR
            effectiveVcSummary.workingCount > 0 -> VcHudAgentStatus.WORKING
            else -> VcHudAgentStatus.IDLE_READY
        }
        FloatingHudController.setVcAgentStatus(context, mappedStatus)
    }

    val cardShape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Default Home Role Banner (only when not currently default launcher)
        if (!isDefaultLauncher) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .spatialParallaxCard(tilt = tilt, depthLayer = 0.8f)
                    .clip(cardShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                FoldLauncherColors.IndigoAccent.copy(alpha = 0.35f),
                                FoldLauncherColors.GlassSurfaceElevated
                            )
                        )
                    )
                    .border(1.dp, FoldLauncherColors.ElectricCyan, cardShape)
                    .clickable(onClick = onOpenDefaultLauncherPrompt)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SET AS DEFAULT FOLD8 HOME LAUNCHER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.CyanBright
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Replace One UI Home for 120Hz zero-bloat home gesture & inner taskbar",
                        fontSize = 12.sp,
                        color = FoldLauncherColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(FoldLauncherColors.ElectricCyan)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Activate",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF02131D)
                    )
                }
            }
        }

        // 2. Live 120Hz VC Command Center & Background Bridge Card
        VcCommandCenterCard(
            summary = effectiveVcSummary,
            config = effectiveVcConfig,
            lastSendFeedback = lastSendFeedback,
            onOpenVc = { sessionId -> vcMonitor.launchVc(sessionId) },
            onSplitScreenVc = { vcMonitor.launchVcSplitScreen() },
            onRefreshVc = { vcMonitor.refreshNow() },
            onSendQuickMessage = { sessionId, text, steerIfWorking ->
                vcMonitor.sendQuickMessage(sessionId, text, steerIfWorking)
            },
            onMarkAttentionRead = { eventId, markAll ->
                vcMonitor.markAttentionRead(eventId, markAll)
            },
            onUpdateConfig = { serverUrl, authToken, monitorEnabled, notifyBackground ->
                vcMonitor.updateConfig(serverUrl, authToken, monitorEnabled, notifyBackground)
            },
            modifier = Modifier.spatialParallaxCard(tilt = tilt, depthLayer = 1.15f)
        )

        // 3. Z Fold 8 System Dashboard Card (120Hz VRR & Hinge Angle Telemetry + Companion Tweak Pills)
        SystemDashboardCard(
            postureState = postureState,
            batteryPct = batteryPct,
            isCharging = isCharging,
            isAccessibilityActive = isAccessibilityActive,
            isWriteSecureSettingsGranted = isWriteSecureSettingsGranted,
            lockScreenRotationEnabled = lockScreenRotationEnabled,
            onToggleLockRotation = onToggleLockRotation,
            onEnforceGboardDeX = onEnforceGboardDeX,
            onLockScreenNow = onLockScreenNow,
            onOpenWallpaperPicker = onOpenWallpaperPicker,
            hingeAngleDegrees = effectiveHingeAngle,
            showFloatingHudConfigurator = showFloatingHudConfigurator,
            onToggleFloatingHudConfigurator = {
                showFloatingHudConfigurator = !showFloatingHudConfigurator
            },
            onToggleRadialArc = onToggleRadialArc,
            modifier = Modifier.spatialParallaxCard(tilt = tilt, depthLayer = 1.0f)
        )

        // Expandable System-Wide Floating Micro-HUD Pill Studio
        if (showFloatingHudConfigurator) {
            FloatingHudControlCard(
                isAccessibilityServiceActive = isAccessibilityActive,
                onOpenAccessibilitySettings = { SystemTweaker.openAccessibilitySettings(context) },
                onTestAnywhereSpotlight = onOpenSpotlight,
                modifier = Modifier.spatialParallaxCard(tilt = tilt, depthLayer = 1.1f)
            )
        }

        // 4. Z Fold 8 Multi-App Pair Presets Card (1-Tap Split-Screen Power Pairs)
        AppPairsQuickCard(
            customAppPairs = customAppPairs,
            onLaunchAppPair = onLaunchAppPair,
            modifier = Modifier.spatialParallaxCard(tilt = tilt, depthLayer = 0.95f)
        )

        // 5. Inline Interactive 120Hz Quick Scratchpad Card (Zero-IPC BasicTextField right on Home Screen)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .spatialParallaxCard(tilt = tilt, depthLayer = 0.9f)
                .clip(cardShape)
                .background(FoldLauncherColors.GlassSurface)
                .border(1.dp, FoldLauncherColors.GlassBorder, cardShape)
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "QUICK SCRATCHPAD · INSTANT NOTE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.CyanBright
                )
                if (quickNoteText.isNotEmpty()) {
                    Text(
                        text = "Clear",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FoldLauncherColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onQuickNoteChange("") }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp, max = 88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.65f))
                    .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopStart
            ) {
                if (quickNoteText.isEmpty()) {
                    Text(
                        text = "Tap to jot a quick note, ADB snippet, or reminder right on the home screen…",
                        fontSize = 13.sp,
                        color = FoldLauncherColors.TextMuted
                    )
                }
                BasicTextField(
                    value = quickNoteText,
                    onValueChange = onQuickNoteChange,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = FoldLauncherColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(FoldLauncherColors.ElectricCyan),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Z Fold 8 System Dashboard Card with live 120Hz VRR & Hinge Angle Sensor Telemetry Gauge
 * (`120Hz VRR • Hinge 180°`) and quick hardware/DeX/HUD controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemDashboardCard(
    postureState: DevicePostureState,
    batteryPct: Int,
    isCharging: Boolean,
    isAccessibilityActive: Boolean,
    isWriteSecureSettingsGranted: Boolean,
    lockScreenRotationEnabled: Boolean,
    onToggleLockRotation: () -> Unit,
    onEnforceGboardDeX: () -> Unit,
    onLockScreenNow: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    modifier: Modifier = Modifier,
    hingeAngleDegrees: Float? = null,
    showFloatingHudConfigurator: Boolean = false,
    onToggleFloatingHudConfigurator: (() -> Unit)? = null,
    onToggleRadialArc: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val hudState by FloatingHudController.state.collectAsState()
    val sensorHingeAngle = rememberHardwareHingeAngleDegrees()

    val resolvedHingeDeg = (hingeAngleDegrees ?: sensorHingeAngle ?: when {
        postureState.isFlexTabletop -> 118f
        postureState.isBookPosture -> 135f
        postureState.isUnfoldedInner -> 180f
        else -> 0f
    }).coerceIn(0f, 180f)

    val roundedHingeDeg = resolvedHingeDeg.roundToInt()
    val hingeProgress = (resolvedHingeDeg / 180f).coerceIn(0.04f, 1f)
    val postureModeText = when {
        roundedHingeDeg in 25..74 -> "TENT"
        postureState.isFlexTabletop || roundedHingeDeg in 75..145 -> "FLEX"
        postureState.isBookPosture -> "BOOK"
        postureState.isUnfoldedInner || roundedHingeDeg >= 146 -> "UNFOLDED"
        else -> "COVER"
    }

    val cardShape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(FoldLauncherColors.GlassSurface)
            .border(1.dp, FoldLauncherColors.GlassBorder, cardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Z FOLD 8 COMPANION · DeX · HUD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.ElectricCyan
                )
                StatusBadgeDot(
                    label = if (isWriteSecureSettingsGranted) "ADB OK" else "ADB Required",
                    ok = isWriteSecureSettingsGranted
                )
                StatusBadgeDot(
                    label = if (isAccessibilityActive) "A11y OK" else "A11y Off",
                    ok = isAccessibilityActive
                )
            }
            Text(
                text = "${postureState.widthDp}×${postureState.heightDp}dp · ${if (isCharging) "⚡" else ""}$batteryPct%",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = FoldLauncherColors.TextSecondary
            )
        }

        // Live Z Fold 8 Hinge & Display VRR Telemetry Gauge ("120Hz VRR • Hinge 180°")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.65f))
                .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(FoldLauncherColors.EmeraldSuccess)
                    )
                    Text(
                        text = "120Hz VRR • Hinge ${roundedHingeDeg}°",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.CyanBright
                    )
                }
                Text(
                    text = "$postureModeText · LTPO 1–120Hz",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.TextSecondary
                )
            }

            // Visual Hinge Angle Bar (0° Closed Cover -> 90° Flex -> 180° Flat Unfolded)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(FoldLauncherColors.GlassSurfaceHighlight)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(hingeProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    FoldLauncherColors.IndigoAccent,
                                    FoldLauncherColors.ElectricCyan,
                                    FoldLauncherColors.EmeraldSuccess
                                )
                            )
                        )
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickTweakPill(
                title = if (hudState.isEnabled) "◉ Micro-HUD Pill: ON" else "◎ Micro-HUD Pill: OFF",
                active = hudState.isEnabled,
                onClick = {
                    val attached = FloatingHudController.setHudEnabled(context, !hudState.isEnabled)
                    if (!hudState.isEnabled && !attached) {
                        SystemTweaker.openAccessibilitySettings(context)
                    }
                }
            )
            if (onToggleFloatingHudConfigurator != null) {
                QuickTweakPill(
                    title = if (showFloatingHudConfigurator) "▴ Hide HUD Studio" else "▾ Configure HUD",
                    active = showFloatingHudConfigurator,
                    onClick = onToggleFloatingHudConfigurator
                )
            }
            if (onToggleRadialArc != null) {
                QuickTweakPill(
                    title = "◡ One-Handed Arc Deck",
                    active = false,
                    onClick = onToggleRadialArc
                )
            }
            QuickTweakPill(
                title = if (lockScreenRotationEnabled) "⟳ Lock Rotation: ON" else "⟳ Lock Rotation: OFF",
                active = lockScreenRotationEnabled,
                onClick = onToggleLockRotation
            )
            QuickTweakPill(
                title = "⌨ Enforce Gboard in DeX",
                active = isWriteSecureSettingsGranted,
                onClick = onEnforceGboardDeX
            )
            QuickTweakPill(
                title = "🔒 Lock Screen",
                active = isAccessibilityActive,
                onClick = onLockScreenNow
            )
            QuickTweakPill(
                title = "✦ Wallpaper",
                active = false,
                onClick = onOpenWallpaperPicker
            )
        }
    }
}

/**
 * 1-Tap Z Fold 8 Multi-Window App Pair Launcher Card with built-in power presets:
 * - `VC + Termux`
 * - `VC Prod + Compare`
 * - `VC + Chrome`
 * Plus any user-customized AppPairs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppPairsQuickCard(
    modifier: Modifier = Modifier,
    customAppPairs: List<AppPair> = emptyList(),
    onLaunchAppPair: ((AppPair) -> Unit)? = null
) {
    val context = LocalContext.current
    val effectivePairs = remember(customAppPairs) {
        if (customAppPairs.isEmpty()) {
            DefaultZFold8AppPairPresets
        } else {
            (customAppPairs + DefaultZFold8AppPairPresets).distinctBy {
                "${it.primaryPackage}|${it.secondaryPackage}"
            }
        }
    }

    val cardShape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(FoldLauncherColors.GlassSurface)
            .border(1.dp, FoldLauncherColors.GlassBorder, cardShape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Z FOLD 8 SPLIT-SCREEN POWER PAIRS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.CyanBright
            )
            Text(
                text = "1-TAP MULTI-WINDOW",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.TextSecondary
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            effectivePairs.forEach { pair ->
                val chipShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .clip(chipShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    FoldLauncherColors.IndigoAccent.copy(alpha = 0.28f),
                                    FoldLauncherColors.GlassSurfaceHighlight
                                )
                            )
                        )
                        .border(1.dp, FoldLauncherColors.GlassBorderStrong, chipShape)
                        .clickable {
                            if (onLaunchAppPair != null) {
                                onLaunchAppPair(pair)
                            } else {
                                SystemTweaker.launchSplitScreenPair(
                                    context = context,
                                    primaryPackage = pair.primaryPackage,
                                    secondaryPackage = pair.secondaryPackage
                                )
                            }
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⊞",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.ElectricCyan
                    )
                    Text(
                        text = pair.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadgeDot(
    label: String,
    ok: Boolean
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(FoldLauncherColors.GlassSurfaceHighlight)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (ok) FoldLauncherColors.EmeraldSuccess else FoldLauncherColors.AmberWarning
                )
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = FoldLauncherColors.TextSecondary
        )
    }
}

@Composable
private fun QuickTweakPill(
    title: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(pillShape)
            .background(
                if (active) FoldLauncherColors.CyanGlow else FoldLauncherColors.GlassSurfaceHighlight
            )
            .border(
                1.dp,
                if (active) FoldLauncherColors.GlassBorderStrong else FoldLauncherColors.GlassBorder,
                pillShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) FoldLauncherColors.CyanBright else FoldLauncherColors.TextPrimary
        )
    }
}
