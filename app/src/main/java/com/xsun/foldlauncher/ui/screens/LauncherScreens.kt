package com.xsun.foldlauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.hud.FloatingHudControlCard
import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.model.AppPair
import com.xsun.foldlauncher.model.FolderItem
import com.xsun.foldlauncher.posture.DevicePostureState
import com.xsun.foldlauncher.posture.PostureOverride
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.spatial.FlexVirtualTrackpadCard
import com.xsun.foldlauncher.spatial.RadialThumbArcOverlay
import com.xsun.foldlauncher.spatial.TentModeStandbyHud
import com.xsun.foldlauncher.ui.animation.RadialTriggerCorner
import com.xsun.foldlauncher.ui.animation.launcherGestures
import com.xsun.foldlauncher.ui.components.AppIconTile
import com.xsun.foldlauncher.ui.components.OneUiConnectivityPillsRow
import com.xsun.foldlauncher.ui.components.PersistentTaskbarDock
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.ui.theme.IconShapePreset
import com.xsun.foldlauncher.vc.VcBackgroundMonitor
import com.xsun.foldlauncher.vc.VcCommandCenterCard
import com.xsun.foldlauncher.vc.VcLiveSummary
import com.xsun.foldlauncher.viewmodel.WorkspaceProfile

private val CORE_DRAWER_CATEGORY_PILLS = listOf("All", "VC & Dev", "Frequent", "System")

private fun isVcAndDevPackage(app: AppEntry): Boolean {
    val pkg = app.packageName.lowercase()
    val lbl = app.label.lowercase()
    return pkg.startsWith("dev.agentconversation") ||
        pkg.startsWith("com.termux") ||
        pkg == "com.android.chrome" ||
        pkg.contains("chrome") ||
        pkg.startsWith("com.xsun.foldlauncher") ||
        pkg == "com.android.settings" ||
        pkg.contains("github") ||
        pkg.contains("adb") ||
        pkg.contains("terminal") ||
        pkg.contains("developer") ||
        lbl.contains("termux") ||
        lbl.contains("agent") ||
        lbl.contains("vc") ||
        lbl.contains("settings")
}

private fun filterAppsByDrawerPill(allApps: List<AppEntry>, selectedCategory: String): List<AppEntry> {
    return when (selectedCategory) {
        "All" -> allApps
        "VC & Dev" -> {
            val matched = allApps.filter { isVcAndDevPackage(it) }
            if (matched.isNotEmpty()) matched else allApps.take(12)
        }
        "Frequent" -> allApps.sortedByDescending { it.launchCount }.take(18)
        "System" -> allApps.filter {
            it.isSystemApp ||
                it.category.equals("System", ignoreCase = true) ||
                it.category.equals("Utilities", ignoreCase = true)
        }
        else -> allApps.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }
}

@Composable
private fun AppDrawerCategoryPillsBar(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { category ->
            val active = category == selectedCategory
            val isVcDev = category == "VC & Dev"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            active -> FoldLauncherColors.CyanGlow
                            isVcDev -> FoldLauncherColors.ElectricCyan.copy(alpha = 0.10f)
                            else -> FoldLauncherColors.GlassSurfaceHighlight
                        }
                    )
                    .border(
                        1.dp,
                        when {
                            active -> FoldLauncherColors.ElectricCyan
                            isVcDev -> FoldLauncherColors.ElectricCyan.copy(alpha = 0.45f)
                            else -> FoldLauncherColors.GlassBorder
                        },
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectCategory(category) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isVcDev) "⚡ VC & Dev" else category,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        active -> FoldLauncherColors.CyanBright
                        isVcDev -> FoldLauncherColors.ElectricCyan
                        else -> FoldLauncherColors.TextSecondary
                    }
                )
            }
        }
    }
}

/**
 * v0.4.0 Interactive App Drawer Tile with a 1-tap `📌` (pinned) / `📍` (unpinned) Pin-to-Dock button
 * in the top-right corner so the user can customize their persistent Z Fold 8 / S23 Ultra dock live.
 */
@Composable
private fun DrawerAppTileWithDockPin(
    app: AppEntry,
    isDockPinned: Boolean,
    iconShapePreset: IconShapePreset,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePinDock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AppIconTile(
            app = app,
            iconShapePreset = iconShapePreset,
            showLabel = true,
            compact = compact,
            onClick = onClick,
            onLongClick = onLongClick
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (isDockPinned) FoldLauncherColors.CyanGlow
                    else FoldLauncherColors.GlassSurfaceElevated.copy(alpha = 0.88f)
                )
                .border(
                    1.dp,
                    if (isDockPinned) FoldLauncherColors.ElectricCyan else FoldLauncherColors.GlassBorder,
                    CircleShape
                )
                .clickable(onClick = onTogglePinDock),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isDockPinned) "📌" else "📍",
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun TentModeStandbyBannerCard(
    hingeAngleDegrees: Float?,
    batteryPct: Int,
    isCharging: Boolean,
    nextEventText: String,
    onExitTentMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vcMonitor = remember(context) { VcBackgroundMonitor.getInstance(context) }
    val vcSummary by vcMonitor.liveSummary.collectAsState()
    val angleText = hingeAngleDegrees?.let { "${it.toInt()}°" } ?: "TENT"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FoldLauncherColors.AmberWarning.copy(alpha = 0.14f))
                .border(1.dp, FoldLauncherColors.AmberWarning.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "⛺ TENT MODE STANDBY ACTIVE · $angleText",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.AmberWarning
                )
                Text(
                    text = "120Hz OLED bedside & desk ambient HUD · Tap to restore automatic posture",
                    fontSize = 10.sp,
                    color = FoldLauncherColors.TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(FoldLauncherColors.CyanGlow)
                    .border(1.dp, FoldLauncherColors.ElectricCyan, RoundedCornerShape(12.dp))
                    .clickable(onClick = onExitTentMode)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Exit Tent Mode (AUTO)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.CyanBright
                )
            }
        }

        TentModeStandbyHud(
            hingeAngleDegrees = hingeAngleDegrees,
            batteryPct = batteryPct,
            isCharging = isCharging,
            nextEventText = nextEventText,
            vcSummary = vcSummary,
            onTalkToVc = { vcMonitor.launchVcVoiceTalk() },
            onOpenVcApp = { vcMonitor.launchVc(null) },
            onExitTentMode = onExitTentMode
        )
    }
}

/**
 * v0.5.0 1-Tap Workspace Profile Pill Bar (`🌐 All Panels`, `⚡ VC & Dev`, `🎯 Minimal Focus`, `🌑 Pure OLED`)
 * plus instant Hardware Torch, Dual-Screen Cover Mirror, Hinge Detent Haptics, Fold-Close & Unfold-Open Action Mode pills.
 */
@Composable
fun WorkspaceProfilePillBar(
    workspaceProfile: WorkspaceProfile,
    onSelectWorkspaceProfile: (WorkspaceProfile) -> Unit,
    isTorchEnabled: Boolean = false,
    onToggleTorch: (() -> Unit)? = null,
    isDualScreenPresentationActive: Boolean = false,
    onToggleDualScreenPresentation: (() -> Unit)? = null,
    isKeyguardLocked: Boolean = false,
    showLockCockpit: Boolean = false,
    onToggleLockCockpit: (() -> Unit)? = null,
    hingeHapticsEnabled: Boolean = true,
    onToggleHingeHaptics: (() -> Unit)? = null,
    foldCloseModeLabel: String = "📱 Keep Cover Awake",
    onCycleFoldCloseActionMode: (() -> Unit)? = null,
    unfoldModeLabel: String = "🖥️ Inner Cockpit",
    onCycleUnfoldActionMode: (() -> Unit)? = null,
    tailscaleIp: String? = null,
    lanIp: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val surfaceColor = if (workspaceProfile == WorkspaceProfile.OLED_BLACK) {
        FoldLauncherColors.OledBlack
    } else {
        FoldLauncherColors.GlassSurfaceHighlight
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WorkspaceProfile.entries.forEach { profile ->
            val selected = profile == workspaceProfile
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) FoldLauncherColors.CyanGlow else surfaceColor
                    )
                    .border(
                        1.dp,
                        if (selected) FoldLauncherColors.ElectricCyan else FoldLauncherColors.GlassBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectWorkspaceProfile(profile) }
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${profile.badge} ${profile.label}",
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (selected) FoldLauncherColors.CyanBright else FoldLauncherColors.TextSecondary
                )
            }
        }

        // Instant 1-Tap Hardware Torch Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isTorchEnabled) FoldLauncherColors.AmberWarning.copy(alpha = 0.22f)
                    else surfaceColor
                )
                .border(
                    1.dp,
                    if (isTorchEnabled) FoldLauncherColors.AmberWarning else FoldLauncherColors.GlassBorder,
                    RoundedCornerShape(14.dp)
                )
                .clickable {
                    if (onToggleTorch != null) {
                        onToggleTorch()
                    } else {
                        SystemTweaker.toggleTorch(context, isTorchEnabled)
                    }
                }
                .padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isTorchEnabled) "🔦 Torch ON" else "🔦 Torch",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isTorchEnabled) FoldLauncherColors.AmberWarning else FoldLauncherColors.TextPrimary
            )
        }

        // Dual-Screen Cover Presentation Pill
        if (onToggleDualScreenPresentation != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isDualScreenPresentationActive) FoldLauncherColors.CyanGlow else surfaceColor
                    )
                    .border(
                        1.dp,
                        if (isDualScreenPresentationActive) FoldLauncherColors.ElectricCyan else FoldLauncherColors.GlassBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(onClick = onToggleDualScreenPresentation)
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isDualScreenPresentationActive) "🪞 Cover HUD: ON" else "🪞 Cover HUD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDualScreenPresentationActive) FoldLauncherColors.CyanBright else FoldLauncherColors.TextSecondary
                )
            }
        }

        // Lock-Screen Cockpit Bar Toggle Pill
        if (onToggleLockCockpit != null) {
            val active = isKeyguardLocked || showLockCockpit
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (active) FoldLauncherColors.EmeraldSuccess.copy(alpha = 0.18f) else surfaceColor
                    )
                    .border(
                        1.dp,
                        if (active) FoldLauncherColors.EmeraldSuccess else FoldLauncherColors.GlassBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(onClick = onToggleLockCockpit)
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isKeyguardLocked) "🔒 Lock Cockpit" else "🔓 Cockpit Bar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) FoldLauncherColors.EmeraldSuccess else FoldLauncherColors.TextSecondary
                )
            }
        }

        // v0.4.0 Hinge Detent Haptics Pill
        if (onToggleHingeHaptics != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (hingeHapticsEnabled) FoldLauncherColors.CyanGlow else surfaceColor
                    )
                    .border(
                        1.dp,
                        if (hingeHapticsEnabled) FoldLauncherColors.ElectricCyan else FoldLauncherColors.GlassBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(onClick = onToggleHingeHaptics)
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (hingeHapticsEnabled) "📳 Haptics: ON" else "📳 Haptics: OFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hingeHapticsEnabled) FoldLauncherColors.CyanBright else FoldLauncherColors.TextSecondary
                )
            }
        }

        // v0.4.0 Fold-Close Action Mode Pill
        if (onCycleFoldCloseActionMode != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(surfaceColor)
                    .border(1.dp, FoldLauncherColors.ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onCycleFoldCloseActionMode)
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = foldCloseModeLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.CyanBright
                )
            }
        }

        // v0.5.0 Unfold-Open Action Mode Pill
        if (onCycleUnfoldActionMode != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(surfaceColor)
                    .border(1.dp, FoldLauncherColors.ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onCycleUnfoldActionMode)
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "📖 $unfoldModeLabel",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.CyanBright
                )
            }
        }

        // v0.5.0 Live Tailscale / LAN IP Telemetry Pill
        val activeIpBadge = when {
            !tailscaleIp.isNullOrBlank() -> "🔗 $tailscaleIp"
            !lanIp.isNullOrBlank() -> "🌐 $lanIp"
            else -> null
        }
        if (activeIpBadge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(surfaceColor)
                    .border(1.dp, FoldLauncherColors.EmeraldSuccess.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = activeIpBadge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.EmeraldSuccess
                )
            }
        }
    }
}

/**
 * v0.6.0 Compact 1-Row One UI VC Bar:
 * Shows live VC agent status + 1-tap `Open VC` + 1-tap `VC + Termux` split-screen + an `Expand` chevron
 * if the user wants to view the full `VcCommandCenterCard`, plus a 1-tap `⚙` Launcher Settings button.
 */
@Composable
private fun OneUiCompactVcBar(
    vcSummary: VcLiveSummary,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenVc: () -> Unit,
    onSplitVcTermux: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusDotColor = when {
        vcSummary.approvalCount > 0 -> FoldLauncherColors.AmberWarning
        vcSummary.failedCount > 0 -> FoldLauncherColors.CoralDanger
        vcSummary.workingCount > 0 || vcSummary.runningCount > 0 -> FoldLauncherColors.ElectricCyan
        else -> FoldLauncherColors.EmeraldSuccess
    }
    val statusText = when {
        vcSummary.approvalCount > 0 -> "VC · ${vcSummary.approvalCount} approval"
        vcSummary.workingCount > 0 -> "VC · ${vcSummary.workingCount} working"
        vcSummary.runningCount > 0 -> "VC · ${vcSummary.runningCount} active"
        vcSummary.totalSessions > 0 -> "VC · ${vcSummary.totalSessions} ready"
        else -> "VC Ready"
    }

    val barShape = RoundedCornerShape(22.dp)
    val pillShape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(Color(0xCC1C1C22))
            .border(1.dp, Color(0x33FFFFFF), barShape)
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusDotColor)
            )
            Text(
                text = statusText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(Color(0x2E0381FE))
                    .border(1.dp, Color(0x8068B5FF), pillShape)
                    .clickable(onClick = onOpenVc)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Open VC",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(Color(0x26FFFFFF))
                    .border(1.dp, Color(0x33FFFFFF), pillShape)
                    .clickable(onClick = onSplitVcTermux)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "VC + Termux",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE3F2FF)
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable(onClick = onToggleExpand),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (expanded) "▴" else "▾",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚙",
                    fontSize = 12.sp,
                    color = Color(0xFFD0D4E0)
                )
            }
        }
    }
}

/**
 * v0.6.0 Subtle One UI "Swipe up for apps & search" pill above the Home Dock.
 * Tapping or swiping up opens the App Search Drawer and immediately focuses the search input
 * & shows the software keyboard (`0ms`).
 */
@Composable
private fun OneUiSwipeUpSearchPill(
    onTriggerSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x991C1C22))
                .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(18.dp))
                .clickable(onClick = onTriggerSearch)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "⌃",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB8C4D8)
            )
            Text(
                text = "Swipe up for apps & search",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFDCE2EE)
            )
        }
    }
}

/**
 * v0.6.0 Streamlined One UI-Matched Cover Screen (Z Fold 8 folded) & Standard Slab Phone (Galaxy S23 Ultra) Home Screen.
 * - Option A for Status Bar / Clock: Keeps Android's native top status bar visible (`statusBarsPadding()`)
 *   with zero duplicate `TopStatusBar` or oversized home-screen clock.
 * - Zero Lock-Screen Hijacking: Renders over the live system wallpaper with no `LockScreenCockpitCard`.
 * - Single-screen One UI layout:
 *   1. Top Row: `OneUiConnectivityPillsRow` (Wireless Debugging & Tailscale Quick-Toggle Pills).
 *   2. Compact 1-Row VC Bar (`OneUiCompactVcBar`, expandable on demand).
 *   3. Clean 4-column One UI Home App Grid over the live system wallpaper.
 *   4. Subtle One UI `"Swipe up for apps & search"` pill + One UI Bottom Favorites Dock (`PersistentTaskbarDock`).
 */
@Composable
fun CoverOrSlabHomeScreen(
    postureState: DevicePostureState,
    postureOverride: PostureOverride = PostureOverride.AUTO,
    onCyclePostureOverride: (() -> Unit)? = null,
    onSetPostureOverride: ((PostureOverride) -> Unit)? = null,
    workspaceProfile: WorkspaceProfile = WorkspaceProfile.ALL,
    onSelectWorkspaceProfile: ((WorkspaceProfile) -> Unit)? = null,
    onCycleWorkspaceProfile: (() -> Unit)? = null,
    isTorchEnabled: Boolean = false,
    onToggleTorch: (() -> Unit)? = null,
    isDualScreenPresentationActive: Boolean = false,
    onToggleDualScreenPresentation: (() -> Unit)? = null,
    isKeyguardLocked: Boolean = false,
    onUnlockAndSplitVcTermux: (() -> Unit)? = null,
    pinnedDockPackages: Set<String> = emptySet(),
    onTogglePinDockPackage: ((String) -> Unit)? = null,
    hingeHapticsEnabled: Boolean = true,
    onToggleHingeHaptics: (() -> Unit)? = null,
    foldCloseModeLabel: String = "📱 Keep Cover Awake",
    onCycleFoldCloseActionMode: (() -> Unit)? = null,
    unfoldModeLabel: String = "🖥️ Inner Cockpit",
    onCycleUnfoldActionMode: (() -> Unit)? = null,
    isWirelessAdbEnabled: Boolean = false,
    wirelessAdbPort: Int? = null,
    onToggleWirelessAdb: (() -> Unit)? = null,
    onOpenWirelessAdbSettings: (() -> Unit)? = null,
    onToggleTailscaleVpn: (() -> Unit)? = null,
    onOpenTailscaleApp: (() -> Unit)? = null,
    tailscaleIp: String? = null,
    lanIp: String? = null,
    allApps: List<AppEntry>,
    pinnedCoverApps: List<AppEntry>,
    dockApps: List<AppEntry>,
    recentApps: List<AppEntry>,
    appPairs: List<AppPair>,
    folders: List<FolderItem> = emptyList(),
    iconShapePreset: IconShapePreset,
    batteryPct: Int,
    isCharging: Boolean,
    nextEventText: String,
    isDeXConnected: Boolean,
    isDefaultLauncher: Boolean,
    isAccessibilityActive: Boolean,
    isWriteSecureSettingsGranted: Boolean,
    lockScreenRotationEnabled: Boolean,
    quickNoteText: String,
    onQuickNoteChange: (String) -> Unit,
    onAppClick: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onLaunchAppPair: (AppPair) -> Unit,
    onOpenSearch: () -> Unit,
    onExpandNotifications: () -> Unit,
    onDoubleTapLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onClockClick: () -> Unit,
    onToggleLockRotation: () -> Unit,
    onEnforceGboardDeX: () -> Unit,
    onLockScreenNow: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onOpenDefaultLauncherPrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val vcMonitor = remember(context) { VcBackgroundMonitor.getInstance(context) }
    val vcSummary by vcMonitor.liveSummary.collectAsState()
    val vcConfig by vcMonitor.config.collectAsState()
    val vcFeedback by vcMonitor.lastSendFeedback.collectAsState()

    var vcExpanded by remember { mutableStateOf(false) }
    var arcTriggerGeneration by remember { mutableIntStateOf(0) }
    var arcRightHanded by remember { mutableStateOf(true) }
    var arcStartExpanded by remember { mutableStateOf(false) }

    val triggerInstantSearch = remember(onOpenSearch, keyboardController) {
        {
            onOpenSearch()
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
            keyboardController?.show()
            Unit
        }
    }

    val allAppsMap = remember(allApps) { allApps.associateBy { it.packageName } }
    val homeGridApps = remember(pinnedCoverApps, recentApps, allApps) {
        (pinnedCoverApps + recentApps + allApps).distinctBy { it.packageName }.take(20)
    }
    val radialArcApps = remember(pinnedCoverApps, recentApps, allApps) {
        (pinnedCoverApps + recentApps + allApps).distinctBy { it.packageName }.take(8)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .launcherGestures(
                onSwipeUpSearch = triggerInstantSearch,
                onSwipeDownNotifications = onExpandNotifications,
                onDoubleTapLock = onDoubleTapLock,
                onLongPressHomeSettings = onOpenSettings,
                onCornerSwipeRadialArc = { corner ->
                    arcRightHanded = (corner == RadialTriggerCorner.BOTTOM_RIGHT)
                    arcStartExpanded = true
                    arcTriggerGeneration++
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Top Row: Two side-by-side Wireless Debugging & Tailscale Quick-Toggle Pills
            OneUiConnectivityPillsRow(
                isWirelessAdbEnabled = isWirelessAdbEnabled,
                wirelessAdbPort = wirelessAdbPort,
                tailscaleIp = tailscaleIp,
                lanIp = lanIp,
                onToggleWirelessAdb = { onToggleWirelessAdb?.invoke() },
                onOpenWirelessAdbSettings = { onOpenWirelessAdbSettings?.invoke() },
                onToggleTailscale = { onToggleTailscaleVpn?.invoke() },
                onOpenTailscaleApp = { onOpenTailscaleApp?.invoke() }
            )

            // 2. Compact 1-Row VC Bar (expandable on demand)
            OneUiCompactVcBar(
                vcSummary = vcSummary,
                expanded = vcExpanded,
                onToggleExpand = { vcExpanded = !vcExpanded },
                onOpenVc = { vcMonitor.launchVc(null) },
                onSplitVcTermux = { vcMonitor.launchVcSplitScreen() },
                onOpenSettings = onOpenSettings
            )

            if (vcExpanded) {
                VcCommandCenterCard(
                    summary = vcSummary,
                    config = vcConfig,
                    lastSendFeedback = vcFeedback,
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
                    }
                )
            }

            if (postureState.isTentMode) {
                TentModeStandbyBannerCard(
                    hingeAngleDegrees = postureState.hingeAngleDegrees,
                    batteryPct = batteryPct,
                    isCharging = isCharging,
                    nextEventText = nextEventText,
                    onExitTentMode = { onSetPostureOverride?.invoke(PostureOverride.AUTO) }
                )
            }

            // 3. Clean 4-Column One UI Home App Grid over the live system wallpaper
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .launcherGestures(
                        onSwipeUpSearch = triggerInstantSearch,
                        onSwipeDownNotifications = onExpandNotifications,
                        onDoubleTapLock = onDoubleTapLock,
                        onLongPressHomeSettings = onOpenSettings
                    )
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = homeGridApps,
                        key = { "${it.packageName}/${it.activityName}" }
                    ) { app ->
                        AppIconTile(
                            app = app,
                            iconShapePreset = iconShapePreset,
                            showLabel = true,
                            compact = false,
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }
                }
            }

            // 4. Subtle One UI "Swipe up for apps & search" Pill + Bottom Favorites Dock
            OneUiSwipeUpSearchPill(onTriggerSearch = triggerInstantSearch)

            PersistentTaskbarDock(
                dockApps = dockApps,
                recentApps = recentApps,
                appPairs = appPairs,
                allAppsMap = allAppsMap,
                iconShapePreset = iconShapePreset,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onLaunchAppPair = onLaunchAppPair,
                onOpenAllAppsSearch = triggerInstantSearch
            )
        }

        key(arcTriggerGeneration, arcRightHanded) {
            RadialThumbArcOverlay(
                apps = radialArcApps,
                iconShapePreset = iconShapePreset,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onOpenSearch = triggerInstantSearch,
                initialRightHanded = arcRightHanded,
                initiallyExpanded = arcStartExpanded
            )
        }
    }
}

/**
 * v0.6.0 Streamlined One UI-Matched Unfolded 7.6"+ Z Fold 8 Inner Display Home Screen.
 * - Option A for Status Bar / Clock: Keeps Android's native top status bar visible (`statusBarsPadding()`)
 *   with zero duplicate `TopStatusBar` or oversized home-screen clock.
 * - Zero Lock-Screen Hijacking: Renders over the live system wallpaper with no `LockScreenCockpitCard`.
 * - Single-screen One UI layout:
 *   1. Top Row: `OneUiConnectivityPillsRow` (Wireless Debugging & Tailscale Quick-Toggle Pills).
 *   2. Compact 1-Row VC Bar (`OneUiCompactVcBar`, expandable on demand).
 *   3. Clean 6-column One UI Home App Grid over the live system wallpaper.
 *   4. Subtle One UI `"Swipe up for apps & search"` pill + One UI Bottom Favorites Dock (`PersistentTaskbarDock`).
 */
@Composable
fun UnfoldedInnerDashboardScreen(
    postureState: DevicePostureState,
    postureOverride: PostureOverride = PostureOverride.AUTO,
    onCyclePostureOverride: (() -> Unit)? = null,
    onSetPostureOverride: ((PostureOverride) -> Unit)? = null,
    workspaceProfile: WorkspaceProfile = WorkspaceProfile.ALL,
    onSelectWorkspaceProfile: ((WorkspaceProfile) -> Unit)? = null,
    onCycleWorkspaceProfile: (() -> Unit)? = null,
    isTorchEnabled: Boolean = false,
    onToggleTorch: (() -> Unit)? = null,
    isDualScreenPresentationActive: Boolean = false,
    onToggleDualScreenPresentation: (() -> Unit)? = null,
    isKeyguardLocked: Boolean = false,
    onUnlockAndSplitVcTermux: (() -> Unit)? = null,
    pinnedDockPackages: Set<String> = emptySet(),
    onTogglePinDockPackage: ((String) -> Unit)? = null,
    hingeHapticsEnabled: Boolean = true,
    onToggleHingeHaptics: (() -> Unit)? = null,
    foldCloseModeLabel: String = "📱 Keep Cover Awake",
    onCycleFoldCloseActionMode: (() -> Unit)? = null,
    unfoldModeLabel: String = "🖥️ Inner Cockpit",
    onCycleUnfoldActionMode: (() -> Unit)? = null,
    isWirelessAdbEnabled: Boolean = false,
    wirelessAdbPort: Int? = null,
    onToggleWirelessAdb: (() -> Unit)? = null,
    onOpenWirelessAdbSettings: (() -> Unit)? = null,
    onToggleTailscaleVpn: (() -> Unit)? = null,
    onOpenTailscaleApp: (() -> Unit)? = null,
    tailscaleIp: String? = null,
    lanIp: String? = null,
    allApps: List<AppEntry>,
    pinnedInnerApps: List<AppEntry>,
    dockApps: List<AppEntry>,
    recentApps: List<AppEntry>,
    appPairs: List<AppPair>,
    folders: List<FolderItem> = emptyList(),
    iconShapePreset: IconShapePreset,
    batteryPct: Int,
    isCharging: Boolean,
    nextEventText: String,
    isDeXConnected: Boolean,
    isDefaultLauncher: Boolean,
    isAccessibilityActive: Boolean,
    isWriteSecureSettingsGranted: Boolean,
    lockScreenRotationEnabled: Boolean,
    quickNoteText: String,
    onQuickNoteChange: (String) -> Unit,
    onAppClick: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onLaunchAppPair: (AppPair) -> Unit,
    onCreateSampleAppPair: (() -> Unit)? = null,
    onOpenSearch: () -> Unit,
    onExpandNotifications: () -> Unit,
    onDoubleTapLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onClockClick: () -> Unit,
    onToggleLockRotation: () -> Unit,
    onEnforceGboardDeX: () -> Unit,
    onLockScreenNow: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onOpenDefaultLauncherPrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val vcMonitor = remember(context) { VcBackgroundMonitor.getInstance(context) }
    val vcSummary by vcMonitor.liveSummary.collectAsState()
    val vcConfig by vcMonitor.config.collectAsState()
    val vcFeedback by vcMonitor.lastSendFeedback.collectAsState()

    var vcExpanded by remember { mutableStateOf(false) }
    var arcTriggerGeneration by remember { mutableIntStateOf(0) }
    var arcRightHanded by remember { mutableStateOf(true) }
    var arcStartExpanded by remember { mutableStateOf(false) }

    val triggerInstantSearch = remember(onOpenSearch, keyboardController) {
        {
            onOpenSearch()
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
            keyboardController?.show()
            Unit
        }
    }

    val allAppsMap = remember(allApps) { allApps.associateBy { it.packageName } }
    val homeGridApps = remember(pinnedInnerApps, recentApps, allApps) {
        (pinnedInnerApps + recentApps + allApps).distinctBy { it.packageName }.take(24)
    }
    val radialArcApps = remember(pinnedInnerApps, recentApps, allApps) {
        (pinnedInnerApps + recentApps + allApps).distinctBy { it.packageName }.take(8)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .launcherGestures(
                onSwipeUpSearch = triggerInstantSearch,
                onSwipeDownNotifications = onExpandNotifications,
                onDoubleTapLock = onDoubleTapLock,
                onLongPressHomeSettings = onOpenSettings,
                onCornerSwipeRadialArc = { corner ->
                    arcRightHanded = (corner == RadialTriggerCorner.BOTTOM_RIGHT)
                    arcStartExpanded = true
                    arcTriggerGeneration++
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top Row: Two side-by-side Wireless Debugging & Tailscale Quick-Toggle Pills
            OneUiConnectivityPillsRow(
                isWirelessAdbEnabled = isWirelessAdbEnabled,
                wirelessAdbPort = wirelessAdbPort,
                tailscaleIp = tailscaleIp,
                lanIp = lanIp,
                onToggleWirelessAdb = { onToggleWirelessAdb?.invoke() },
                onOpenWirelessAdbSettings = { onOpenWirelessAdbSettings?.invoke() },
                onToggleTailscale = { onToggleTailscaleVpn?.invoke() },
                onOpenTailscaleApp = { onOpenTailscaleApp?.invoke() }
            )

            // 2. Compact 1-Row VC Bar (expandable on demand)
            OneUiCompactVcBar(
                vcSummary = vcSummary,
                expanded = vcExpanded,
                onToggleExpand = { vcExpanded = !vcExpanded },
                onOpenVc = { vcMonitor.launchVc(null) },
                onSplitVcTermux = { vcMonitor.launchVcSplitScreen() },
                onOpenSettings = onOpenSettings
            )

            if (vcExpanded) {
                VcCommandCenterCard(
                    summary = vcSummary,
                    config = vcConfig,
                    lastSendFeedback = vcFeedback,
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
                    }
                )
            }

            if (postureState.isTentMode) {
                TentModeStandbyBannerCard(
                    hingeAngleDegrees = postureState.hingeAngleDegrees,
                    batteryPct = batteryPct,
                    isCharging = isCharging,
                    nextEventText = nextEventText,
                    onExitTentMode = { onSetPostureOverride?.invoke(PostureOverride.AUTO) }
                )
            }

            // 3. Clean 6-Column One UI Inner Home App Grid over the live system wallpaper
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .launcherGestures(
                        onSwipeUpSearch = triggerInstantSearch,
                        onSwipeDownNotifications = onExpandNotifications,
                        onDoubleTapLock = onDoubleTapLock,
                        onLongPressHomeSettings = onOpenSettings
                    )
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = homeGridApps,
                        key = { "${it.packageName}/${it.activityName}" }
                    ) { app ->
                        AppIconTile(
                            app = app,
                            iconShapePreset = iconShapePreset,
                            showLabel = true,
                            compact = false,
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }
                }
            }

            // 4. Subtle One UI "Swipe up for apps & search" Pill + Bottom Favorites Dock
            OneUiSwipeUpSearchPill(onTriggerSearch = triggerInstantSearch)

            PersistentTaskbarDock(
                dockApps = dockApps,
                recentApps = recentApps,
                appPairs = appPairs,
                allAppsMap = allAppsMap,
                iconShapePreset = iconShapePreset,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onLaunchAppPair = onLaunchAppPair,
                onOpenAllAppsSearch = triggerInstantSearch
            )
        }

        key(arcTriggerGeneration, arcRightHanded) {
            RadialThumbArcOverlay(
                apps = radialArcApps,
                iconShapePreset = iconShapePreset,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onOpenSearch = triggerInstantSearch,
                initialRightHanded = arcRightHanded,
                initiallyExpanded = arcStartExpanded
            )
        }
    }
}

/**
 * v0.6.0 Streamlined One UI-Matched Half-Opened Horizontal Flex / Tabletop Posture Home Screen.
 */
@Composable
fun FlexTabletopHomeScreen(
    postureState: DevicePostureState,
    postureOverride: PostureOverride = PostureOverride.AUTO,
    onCyclePostureOverride: (() -> Unit)? = null,
    onSetPostureOverride: ((PostureOverride) -> Unit)? = null,
    workspaceProfile: WorkspaceProfile = WorkspaceProfile.ALL,
    onSelectWorkspaceProfile: ((WorkspaceProfile) -> Unit)? = null,
    onCycleWorkspaceProfile: (() -> Unit)? = null,
    isTorchEnabled: Boolean = false,
    onToggleTorch: (() -> Unit)? = null,
    isDualScreenPresentationActive: Boolean = false,
    onToggleDualScreenPresentation: (() -> Unit)? = null,
    isKeyguardLocked: Boolean = false,
    onUnlockAndSplitVcTermux: (() -> Unit)? = null,
    pinnedDockPackages: Set<String> = emptySet(),
    onTogglePinDockPackage: ((String) -> Unit)? = null,
    hingeHapticsEnabled: Boolean = true,
    onToggleHingeHaptics: (() -> Unit)? = null,
    foldCloseModeLabel: String = "📱 Keep Cover Awake",
    onCycleFoldCloseActionMode: (() -> Unit)? = null,
    unfoldModeLabel: String = "🖥️ Inner Cockpit",
    onCycleUnfoldActionMode: (() -> Unit)? = null,
    isWirelessAdbEnabled: Boolean = false,
    wirelessAdbPort: Int? = null,
    onToggleWirelessAdb: (() -> Unit)? = null,
    onOpenWirelessAdbSettings: (() -> Unit)? = null,
    onToggleTailscaleVpn: (() -> Unit)? = null,
    onOpenTailscaleApp: (() -> Unit)? = null,
    tailscaleIp: String? = null,
    lanIp: String? = null,
    allApps: List<AppEntry>,
    favoriteApps: List<AppEntry>,
    dockApps: List<AppEntry>,
    recentApps: List<AppEntry>,
    appPairs: List<AppPair>,
    iconShapePreset: IconShapePreset,
    batteryPct: Int,
    isCharging: Boolean,
    nextEventText: String,
    isDeXConnected: Boolean,
    isDefaultLauncher: Boolean,
    isAccessibilityActive: Boolean,
    isWriteSecureSettingsGranted: Boolean,
    lockScreenRotationEnabled: Boolean,
    quickNoteText: String,
    onQuickNoteChange: (String) -> Unit,
    onAppClick: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onLaunchAppPair: (AppPair) -> Unit,
    onOpenSearch: () -> Unit,
    onExpandNotifications: () -> Unit,
    onDoubleTapLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onClockClick: () -> Unit,
    onToggleLockRotation: () -> Unit,
    onEnforceGboardDeX: () -> Unit,
    onLockScreenNow: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onOpenDefaultLauncherPrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val vcMonitor = remember(context) { VcBackgroundMonitor.getInstance(context) }
    val vcSummary by vcMonitor.liveSummary.collectAsState()
    val vcConfig by vcMonitor.config.collectAsState()
    val vcFeedback by vcMonitor.lastSendFeedback.collectAsState()
    val allAppsMap = remember(allApps) { allApps.associateBy { it.packageName } }

    var showVirtualTrackpad by remember { mutableStateOf(false) }
    var vcExpanded by remember { mutableStateOf(false) }

    val triggerInstantSearch = remember(onOpenSearch, keyboardController) {
        {
            onOpenSearch()
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
            keyboardController?.show()
            Unit
        }
    }

    val deckApps = remember(favoriteApps, recentApps, allApps) {
        (favoriteApps + recentApps + allApps).distinctBy { it.packageName }.take(18)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .launcherGestures(
                onSwipeUpSearch = triggerInstantSearch,
                onSwipeDownNotifications = onExpandNotifications,
                onDoubleTapLock = onDoubleTapLock,
                onLongPressHomeSettings = onOpenSettings
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Top Row: Two side-by-side Wireless Debugging & Tailscale Quick-Toggle Pills
        OneUiConnectivityPillsRow(
            isWirelessAdbEnabled = isWirelessAdbEnabled,
            wirelessAdbPort = wirelessAdbPort,
            tailscaleIp = tailscaleIp,
            lanIp = lanIp,
            onToggleWirelessAdb = { onToggleWirelessAdb?.invoke() },
            onOpenWirelessAdbSettings = { onOpenWirelessAdbSettings?.invoke() },
            onToggleTailscale = { onToggleTailscaleVpn?.invoke() },
            onOpenTailscaleApp = { onOpenTailscaleApp?.invoke() }
        )

        // 2. Compact 1-Row VC Bar + Trackpad toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OneUiCompactVcBar(
                vcSummary = vcSummary,
                expanded = vcExpanded,
                onToggleExpand = { vcExpanded = !vcExpanded },
                onOpenVc = { vcMonitor.launchVc(null) },
                onSplitVcTermux = { vcMonitor.launchVcSplitScreen() },
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC1C1C22))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .clickable { showVirtualTrackpad = !showVirtualTrackpad }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Text(
                    text = if (showVirtualTrackpad) "⊞ Grid" else "⊹ Pad",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (vcExpanded) {
            VcCommandCenterCard(
                summary = vcSummary,
                config = vcConfig,
                lastSendFeedback = vcFeedback,
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
                }
            )
        }

        // 3. Main Area: Clean 6-Column One UI Home App Grid or Flex Trackpad
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .launcherGestures(
                    onSwipeUpSearch = triggerInstantSearch,
                    onSwipeDownNotifications = onExpandNotifications,
                    onDoubleTapLock = onDoubleTapLock,
                    onLongPressHomeSettings = onOpenSettings
                )
        ) {
            if (showVirtualTrackpad) {
                FlexVirtualTrackpadCard(
                    favoriteApps = deckApps,
                    onLaunchApp = onAppClick,
                    onContextApp = onAppLongClick,
                    onOpenSearch = triggerInstantSearch,
                    onTriggerSplitScreen = {
                        val firstPair = appPairs.firstOrNull()
                        if (firstPair != null) {
                            onLaunchAppPair(firstPair)
                        } else {
                            vcMonitor.launchVcSplitScreen()
                        }
                    },
                    onQuickLaunchVc = {
                        vcMonitor.launchVc(null)
                    },
                    hingeAngleDegrees = postureState.hingeAngleDegrees,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = deckApps,
                        key = { "${it.packageName}/${it.activityName}" }
                    ) { app ->
                        AppIconTile(
                            app = app,
                            iconShapePreset = iconShapePreset,
                            showLabel = true,
                            compact = true,
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }
                }
            }
        }

        // 4. Subtle One UI "Swipe up for apps & search" Pill + Bottom Favorites Dock
        OneUiSwipeUpSearchPill(onTriggerSearch = triggerInstantSearch)

        PersistentTaskbarDock(
            dockApps = dockApps,
            recentApps = recentApps,
            appPairs = appPairs,
            allAppsMap = allAppsMap,
            iconShapePreset = iconShapePreset,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick,
            onLaunchAppPair = onLaunchAppPair,
            onOpenAllAppsSearch = triggerInstantSearch
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LauncherSettingsSheet(
    visible: Boolean,
    hideSystemStatusBar: Boolean,
    onToggleHideSystemStatusBar: (Boolean) -> Unit,
    iconShapePreset: IconShapePreset,
    onSelectIconShape: (IconShapePreset) -> Unit,
    hiddenApps: List<AppEntry>,
    onUnhideApp: (String) -> Unit,
    isAccessibilityActive: Boolean,
    isWriteSecureSettingsGranted: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val adbCommand = remember(context) { SystemTweaker.getAdbGrantCommand(context) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FoldLauncherColors.GlassSurfaceElevated,
        contentColor = FoldLauncherColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Fold8 & Slab Launcher Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.TextPrimary
            )
            Text(
                text = "Zero-bloat 120Hz OLED launcher, VC Background Bridge & System-Wide Micro-HUD",
                fontSize = 12.sp,
                color = FoldLauncherColors.TextSecondary
            )

            HorizontalDivider(color = FoldLauncherColors.GlassBorder)

            // 1. System-Wide Floating Top-Right Micro-HUD Pill Studio
            FloatingHudControlCard(
                isAccessibilityServiceActive = isAccessibilityActive,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings
            )

            HorizontalDivider(color = FoldLauncherColors.GlassBorder)

            // 2. System Status Bar Immersion Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FoldLauncherColors.GlassSurface)
                    .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hide Small System Status Bar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.TextPrimary
                    )
                    Text(
                        text = "Use only the oversized 26sp+ Top-Right Launcher Clock on Home",
                        fontSize = 11.sp,
                        color = FoldLauncherColors.TextSecondary
                    )
                }
                Switch(
                    checked = hideSystemStatusBar,
                    onCheckedChange = onToggleHideSystemStatusBar,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = FoldLauncherColors.OledBlack,
                        checkedTrackColor = FoldLauncherColors.ElectricCyan
                    )
                )
            }

            // 3. Icon Shape Presets
            Text(
                text = "ICON SHAPE MASK",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.ElectricCyan
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IconShapePreset.entries.forEach { preset ->
                    val selected = preset == iconShapePreset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) FoldLauncherColors.CyanGlow
                                else FoldLauncherColors.GlassSurface
                            )
                            .border(
                                1.dp,
                                if (selected) FoldLauncherColors.ElectricCyan
                                else FoldLauncherColors.GlassBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectIconShape(preset) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = preset.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) FoldLauncherColors.CyanBright else FoldLauncherColors.TextPrimary
                        )
                    }
                }
            }

            HorizontalDivider(color = FoldLauncherColors.GlassBorder)

            // 4. System Permissions & One-Time ADB Setup
            Text(
                text = "FOLD8 SYSTEM BRIDGE & PERMISSIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.ElectricCyan
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FoldLauncherColors.GlassSurface)
                    .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenAccessibilitySettings)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAccessibilityActive) {
                            "✓ Accessibility Service Active"
                        } else {
                            "Enable Fold8 Accessibility Companion"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAccessibilityActive) {
                            FoldLauncherColors.EmeraldSuccess
                        } else {
                            FoldLauncherColors.AmberWarning
                        }
                    )
                    Text(
                        text = "Powers System-Wide Floating HUD, double-tap to lock & split-screen",
                        fontSize = 11.sp,
                        color = FoldLauncherColors.TextSecondary
                    )
                }
                Text(
                    text = "Open ↗",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.ElectricCyan
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FoldLauncherColors.GlassSurface)
                    .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        clipboardManager.setText(AnnotatedString(adbCommand))
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isWriteSecureSettingsGranted) {
                        "✓ WRITE_SECURE_SETTINGS Granted via ADB"
                    } else {
                        "One-Time ADB Grant (Tap to Copy Command)"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isWriteSecureSettingsGranted) {
                        FoldLauncherColors.EmeraldSuccess
                    } else {
                        FoldLauncherColors.CyanBright
                    }
                )
                Text(
                    text = adbCommand,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = FoldLauncherColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 5. Hidden Apps Management
            if (hiddenApps.isNotEmpty()) {
                HorizontalDivider(color = FoldLauncherColors.GlassBorder)
                Text(
                    text = "HIDDEN APPS (${hiddenApps.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.AmberWarning
                )
                hiddenApps.forEach { hidden ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(FoldLauncherColors.GlassSurface)
                            .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = hidden.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = FoldLauncherColors.TextPrimary
                        )
                        Text(
                            text = "Unhide",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoldLauncherColors.ElectricCyan,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onUnhideApp(hidden.packageName) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
