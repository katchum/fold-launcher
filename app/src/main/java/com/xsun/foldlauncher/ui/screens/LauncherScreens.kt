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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import com.xsun.foldlauncher.spatial.rememberSpatialTiltState
import com.xsun.foldlauncher.spatial.spatialParallaxCard
import com.xsun.foldlauncher.ui.animation.AlphabetWaveScrubber
import com.xsun.foldlauncher.ui.animation.RadialTriggerCorner
import com.xsun.foldlauncher.ui.animation.launcherGestures
import com.xsun.foldlauncher.ui.components.AppIconTile
import com.xsun.foldlauncher.ui.components.NativeDashboardCards
import com.xsun.foldlauncher.ui.components.PersistentTaskbarDock
import com.xsun.foldlauncher.ui.components.TopStatusBar
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.ui.theme.IconShapePreset
import com.xsun.foldlauncher.vc.VcBackgroundMonitor
import kotlinx.coroutines.launch

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
    val vcSummary by vcMonitor.summary.collectAsState()
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
 * One-handed Cover Screen (Z Fold 8 folded) & Standard Slab Phone (Galaxy S23 Ultra) Home Screen.
 * Combines:
 * - Oversized 26sp+ Top-Right Clock status bar with 3D spatial depth parallax
 * - Collapsible 120Hz Native Companion Cards (including Live VC Command Center & Floating Micro-HUD Pill control)
 * - Pinned Cover favorites strip
 * - Full 120Hz app grid with right-edge AlphabetWaveScrubber
 * - Bottom thumb-zone dock + One-Handed Radial Thumb Arc ("Arc Deck") overlay
 */
@Composable
fun CoverOrSlabHomeScreen(
    postureState: DevicePostureState,
    postureOverride: PostureOverride = PostureOverride.AUTO,
    onCyclePostureOverride: (() -> Unit)? = null,
    onSetPostureOverride: ((PostureOverride) -> Unit)? = null,
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
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val spatialTilt = rememberSpatialTiltState(enabled = true, hingeAngleFactor = 0f)

    var selectedCategory by remember { mutableStateOf("All") }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var showCompanionCards by remember { mutableStateOf(true) }
    var arcTriggerGeneration by remember { mutableIntStateOf(0) }
    var arcRightHanded by remember { mutableStateOf(true) }
    var arcStartExpanded by remember { mutableStateOf(false) }

    val allAppsMap = remember(allApps) { allApps.associateBy { it.packageName } }
    val categories = remember(allApps) {
        (CORE_DRAWER_CATEGORY_PILLS + allApps.map { it.category }.distinct().sorted()).distinct()
    }
    val filteredApps = remember(allApps, selectedCategory) {
        filterAppsByDrawerPill(allApps, selectedCategory)
    }
    val letters = remember(filteredApps) {
        val available = filteredApps.map { it.firstLetter }.distinct().sorted()
        if (available.isEmpty()) ('A'..'Z').toList() else available
    }
    val radialArcApps = remember(pinnedCoverApps, recentApps, allApps) {
        (pinnedCoverApps + recentApps + allApps).distinctBy { it.packageName }.take(8)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .launcherGestures(
                    onSwipeUpSearch = onOpenSearch,
                    onSwipeDownNotifications = onExpandNotifications,
                    onDoubleTapLock = onDoubleTapLock,
                    onLongPressHomeSettings = onOpenSettings,
                    onCornerSwipeRadialArc = { corner ->
                        arcRightHanded = (corner == RadialTriggerCorner.BOTTOM_RIGHT)
                        arcStartExpanded = true
                        arcTriggerGeneration++
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TopStatusBar(
                postureState = postureState,
                batteryPct = batteryPct,
                isCharging = isCharging,
                nextEventText = nextEventText,
                isDeXConnected = isDeXConnected,
                hingeAngleDegrees = postureState.hingeAngleDegrees,
                postureOverrideLabel = postureOverride.shortLabel,
                onCyclePostureOverride = onCyclePostureOverride,
                onClockClick = onClockClick,
                onSearchPillClick = onOpenSearch,
                onSettingsClick = onOpenSettings,
                modifier = Modifier.spatialParallaxCard(tilt = spatialTilt, depthLayer = 0.7f)
            )

            if (postureState.isTentMode) {
                TentModeStandbyBannerCard(
                    hingeAngleDegrees = postureState.hingeAngleDegrees,
                    batteryPct = batteryPct,
                    isCharging = isCharging,
                    nextEventText = nextEventText,
                    onExitTentMode = { onSetPostureOverride?.invoke(PostureOverride.AUTO) }
                )
            }

            // Section header with toggle for 120Hz Native Companion & VC Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "COVER / SLAB · ${filteredApps.size} APPS · 3D PARALLAX",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.ElectricCyan
                )
                Text(
                    text = if (showCompanionCards) "Hide VC & Deck ▲" else "Show VC & Deck ▼",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FoldLauncherColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showCompanionCards = !showCompanionCards }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            if (showCompanionCards) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.46f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NativeDashboardCards(
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
                        onOpenDefaultLauncherPrompt = onOpenDefaultLauncherPrompt,
                        isDefaultLauncher = isDefaultLauncher,
                        quickNoteText = quickNoteText,
                        onQuickNoteChange = onQuickNoteChange,
                        hingeAngleDegrees = postureState.hingeAngleDegrees,
                        customAppPairs = appPairs,
                        onLaunchAppPair = onLaunchAppPair,
                        spatialTiltState = spatialTilt,
                        onOpenSpotlight = onOpenSearch,
                        onToggleRadialArc = {
                            arcStartExpanded = true
                            arcTriggerGeneration++
                        }
                    )
                }
            }

            // Pinned Cover Favorites Strip
            if (pinnedCoverApps.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .spatialParallaxCard(tilt = spatialTilt, depthLayer = 0.85f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(FoldLauncherColors.GlassSurface)
                        .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "PINNED FAVORITES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.CyanBright,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pinnedCoverApps.forEach { app ->
                            AppIconTile(
                                app = app,
                                iconShapePreset = iconShapePreset,
                                showLabel = true,
                                compact = true,
                                onClick = { onAppClick(app) },
                                onLongClick = { onAppLongClick(app) },
                                modifier = Modifier.width(68.dp)
                            )
                        }
                    }
                }
            }

            // App Drawer Category Filter Pills (All, VC & Dev, Frequent, System)
            AppDrawerCategoryPillsBar(
                categories = categories,
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it }
            )

            // Main App Grid + Right-Edge One-Handed AlphabetWaveScrubber
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(postureState.recommendedGridColumns.coerceAtLeast(4)),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    items(
                        items = filteredApps,
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

                AlphabetWaveScrubber(
                    letters = letters,
                    activeLetter = activeLetter,
                    onLetterSelected = { letter ->
                        activeLetter = letter
                        val targetIdx = filteredApps.indexOfFirst { it.firstLetter == letter }
                        if (targetIdx >= 0) {
                            coroutineScope.launch {
                                gridState.scrollToItem(targetIdx)
                            }
                        }
                    }
                )
            }

            // Bottom Thumb-Zone Dock
            PersistentTaskbarDock(
                dockApps = dockApps,
                recentApps = recentApps,
                appPairs = appPairs,
                allAppsMap = allAppsMap,
                iconShapePreset = iconShapePreset,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onLaunchAppPair = onLaunchAppPair,
                onOpenAllAppsSearch = onOpenSearch
            )
        }

        // One-Handed Radial Thumb Arc ("Arc Deck") Overlay anchored at bottom thumb corner
        key(arcTriggerGeneration, arcRightHanded) {
            RadialThumbArcOverlay(
                apps = radialArcApps,
                iconShapePreset = iconShapePreset,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onOpenSearch = onOpenSearch,
                initialRightHanded = arcRightHanded,
                initiallyExpanded = arcStartExpanded
            )
        }
    }
}

/**
 * Unfolded 7.6"+ Z Fold 8 Inner Display & DeX Dual-Hemisphere Dashboard Screen.
 * Left pane hosts 120Hz native companion cards (VC Command Center, Floating Micro-HUD, DeX tweaks),
 * pinned inner favorites, folders, and split-screen pairs with 3D spatial depth parallax.
 * Right pane hosts a categorized full-speed app matrix with instant category chips and wave scrubber.
 * Bottom edge anchors the persistent PC-style taskbar dock + Radial Thumb Arc.
 */
@Composable
fun UnfoldedInnerDashboardScreen(
    postureState: DevicePostureState,
    postureOverride: PostureOverride = PostureOverride.AUTO,
    onCyclePostureOverride: (() -> Unit)? = null,
    onSetPostureOverride: ((PostureOverride) -> Unit)? = null,
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
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val spatialTilt = rememberSpatialTiltState(
        enabled = true,
        hingeAngleFactor = if (postureState.isBookPosture) 0.35f else 0.1f
    )

    var selectedCategory by remember { mutableStateOf("All") }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var arcTriggerGeneration by remember { mutableIntStateOf(0) }
    var arcRightHanded by remember { mutableStateOf(true) }
    var arcStartExpanded by remember { mutableStateOf(false) }

    val allAppsMap = remember(allApps) { allApps.associateBy { it.packageName } }
    val categories = remember(allApps) {
        (CORE_DRAWER_CATEGORY_PILLS + allApps.map { it.category }.distinct().sorted()).distinct()
    }
    val filteredApps = remember(allApps, selectedCategory) {
        filterAppsByDrawerPill(allApps, selectedCategory)
    }
    val letters = remember(filteredApps) {
        val available = filteredApps.map { it.firstLetter }.distinct().sorted()
        if (available.isEmpty()) ('A'..'Z').toList() else available
    }
    val radialArcApps = remember(pinnedInnerApps, recentApps, allApps) {
        (pinnedInnerApps + recentApps + allApps).distinctBy { it.packageName }.take(8)
    }

    val hingeGapDp = remember(postureState) {
        val left = postureState.hingeLeftDp
        val right = postureState.hingeRightDp
        if (left != null && right != null && right > left) {
            (right - left).coerceIn(12, 36).dp
        } else {
            16.dp
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .launcherGestures(
                    onSwipeUpSearch = onOpenSearch,
                    onSwipeDownNotifications = onExpandNotifications,
                    onDoubleTapLock = onDoubleTapLock,
                    onLongPressHomeSettings = onOpenSettings,
                    onCornerSwipeRadialArc = { corner ->
                        arcRightHanded = (corner == RadialTriggerCorner.BOTTOM_RIGHT)
                        arcStartExpanded = true
                        arcTriggerGeneration++
                    }
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Oversized High-Visibility Top Status Bar
            TopStatusBar(
                postureState = postureState,
                batteryPct = batteryPct,
                isCharging = isCharging,
                nextEventText = nextEventText,
                isDeXConnected = isDeXConnected,
                hingeAngleDegrees = postureState.hingeAngleDegrees,
                postureOverrideLabel = postureOverride.shortLabel,
                onCyclePostureOverride = onCyclePostureOverride,
                onClockClick = onClockClick,
                onSearchPillClick = onOpenSearch,
                onSettingsClick = onOpenSettings,
                modifier = Modifier.spatialParallaxCard(tilt = spatialTilt, depthLayer = 0.65f)
            )

            if (postureState.isTentMode) {
                TentModeStandbyBannerCard(
                    hingeAngleDegrees = postureState.hingeAngleDegrees,
                    batteryPct = batteryPct,
                    isCharging = isCharging,
                    nextEventText = nextEventText,
                    onExitTentMode = { onSetPostureOverride?.invoke(PostureOverride.AUTO) }
                )
            }

            // 2. Dual-Hemisphere Inner Workspace (Left Dashboard + Hinge Gutter + Right App Matrix)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                // Left Hemisphere: Native 120Hz Widgets + VC Command Center + Pinned Favorites + Folders
                Column(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NativeDashboardCards(
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
                        onOpenDefaultLauncherPrompt = onOpenDefaultLauncherPrompt,
                        isDefaultLauncher = isDefaultLauncher,
                        quickNoteText = quickNoteText,
                        onQuickNoteChange = onQuickNoteChange,
                        hingeAngleDegrees = postureState.hingeAngleDegrees,
                        customAppPairs = appPairs,
                        onLaunchAppPair = onLaunchAppPair,
                        spatialTiltState = spatialTilt,
                        onOpenSpotlight = onOpenSearch,
                        onToggleRadialArc = {
                            arcStartExpanded = true
                            arcTriggerGeneration++
                        }
                    )

                    // Pinned Inner Screen Favorites Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .spatialParallaxCard(tilt = spatialTilt, depthLayer = 0.95f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(FoldLauncherColors.GlassSurface)
                            .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(22.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "INNER DISPLAY FAVORITES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FoldLauncherColors.ElectricCyan
                            )
                            if (onCreateSampleAppPair != null) {
                                Text(
                                    text = "+ Save Split Pair",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FoldLauncherColors.CyanBright,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(FoldLauncherColors.CyanGlow)
                                        .clickable(onClick = onCreateSampleAppPair)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pinnedInnerApps.take(10).forEach { app ->
                                AppIconTile(
                                    app = app,
                                    iconShapePreset = iconShapePreset,
                                    showLabel = true,
                                    compact = false,
                                    onClick = { onAppClick(app) },
                                    onLongClick = { onAppLongClick(app) },
                                    modifier = Modifier.width(74.dp)
                                )
                            }
                        }
                    }

                    // Smart Folders Row (if folders are configured)
                    if (folders.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .spatialParallaxCard(tilt = spatialTilt, depthLayer = 0.85f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(FoldLauncherColors.GlassSurface)
                                .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(22.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SMART FOLDERS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FoldLauncherColors.CyanBright
                            )
                            folders.forEach { folder ->
                                val resolved = folder.packageNames.mapNotNull { allAppsMap[it] }
                                if (resolved.isNotEmpty()) {
                                    Text(
                                        text = "${folder.name} (${resolved.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FoldLauncherColors.TextSecondary
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        resolved.forEach { app ->
                                            AppIconTile(
                                                app = app,
                                                iconShapePreset = iconShapePreset,
                                                showLabel = true,
                                                compact = true,
                                                onClick = { onAppClick(app) },
                                                onLongClick = { onAppLongClick(app) },
                                                modifier = Modifier.width(64.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Physical Hinge / Crease Safe Gutter
                Spacer(modifier = Modifier.width(hingeGapDp))

                // Right Hemisphere: Categorized 120Hz All-Apps Matrix + AlphabetWaveScrubber
                Column(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight()
                        .spatialParallaxCard(tilt = spatialTilt, depthLayer = 0.9f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(FoldLauncherColors.GlassSurface)
                        .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(24.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Category Filter Bar (All, VC & Dev, Frequent, System + categories)
                    AppDrawerCategoryPillsBar(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 76.dp),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            items(
                                items = filteredApps,
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

                        AlphabetWaveScrubber(
                            letters = letters,
                            activeLetter = activeLetter,
                            onLetterSelected = { letter ->
                                activeLetter = letter
                                val idx = filteredApps.indexOfFirst { it.firstLetter == letter }
                                if (idx >= 0) {
                                    coroutineScope.launch {
                                        gridState.scrollToItem(idx)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // 3. Persistent Floating PC-Style Taskbar Dock
            PersistentTaskbarDock(
                dockApps = dockApps,
                recentApps = recentApps,
                appPairs = appPairs,
                allAppsMap = allAppsMap,
                iconShapePreset = iconShapePreset,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onLaunchAppPair = onLaunchAppPair,
                onOpenAllAppsSearch = onOpenSearch
            )
        }

        // One-Handed Radial Thumb Arc ("Arc Deck") Overlay for thumb corner access on wide screens
        key(arcTriggerGeneration, arcRightHanded) {
            RadialThumbArcOverlay(
                apps = radialArcApps,
                iconShapePreset = iconShapePreset,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onOpenSearch = onOpenSearch,
                initialRightHanded = arcRightHanded,
                initiallyExpanded = arcStartExpanded
            )
        }
    }
}

/**
 * Z Fold 8 Half-Opened Horizontal Flex / Tabletop Posture Home Screen.
 * Splits the screen across the horizontal crease into:
 * - Upper viewing/status hemisphere with TopStatusBar, VC Command Center, and NativeDashboardCards
 * - Lower tactile Control Deck featuring the interactive 120Hz FlexVirtualTrackpadCard (laser cursor,
 *   left/right click, VC deck, split-screen trigger), App Grid toggle, and PersistentTaskbarDock.
 */
@Composable
fun FlexTabletopHomeScreen(
    postureState: DevicePostureState,
    postureOverride: PostureOverride = PostureOverride.AUTO,
    onCyclePostureOverride: (() -> Unit)? = null,
    onSetPostureOverride: ((PostureOverride) -> Unit)? = null,
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
    val vcMonitor = remember(context) { VcBackgroundMonitor.getInstance(context) }
    val spatialTilt = rememberSpatialTiltState(enabled = true, hingeAngleFactor = 0.55f)
    val allAppsMap = remember(allApps) { allApps.associateBy { it.packageName } }

    var showVirtualTrackpad by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf("All") }

    val deckApps = remember(favoriteApps, allApps) {
        if (favoriteApps.isNotEmpty()) {
            (favoriteApps + allApps).distinctBy { it.packageName }
        } else {
            allApps
        }
    }
    val categories = remember(deckApps) {
        (CORE_DRAWER_CATEGORY_PILLS + deckApps.map { it.category }.distinct().sorted()).distinct()
    }
    val filteredDeckApps = remember(deckApps, selectedCategory) {
        filterAppsByDrawerPill(deckApps, selectedCategory)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .launcherGestures(
                onSwipeUpSearch = onOpenSearch,
                onSwipeDownNotifications = onExpandNotifications,
                onDoubleTapLock = onDoubleTapLock,
                onLongPressHomeSettings = onOpenSettings
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Upper Viewing Hemisphere (above horizontal fold crease)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.46f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TopStatusBar(
                postureState = postureState,
                batteryPct = batteryPct,
                isCharging = isCharging,
                nextEventText = nextEventText,
                isDeXConnected = isDeXConnected,
                hingeAngleDegrees = postureState.hingeAngleDegrees,
                postureOverrideLabel = postureOverride.shortLabel,
                onCyclePostureOverride = onCyclePostureOverride,
                onClockClick = onClockClick,
                onSearchPillClick = onOpenSearch,
                onSettingsClick = onOpenSettings,
                modifier = Modifier.spatialParallaxCard(tilt = spatialTilt, depthLayer = 0.8f)
            )

            if (postureState.isTentMode) {
                TentModeStandbyBannerCard(
                    hingeAngleDegrees = postureState.hingeAngleDegrees,
                    batteryPct = batteryPct,
                    isCharging = isCharging,
                    nextEventText = nextEventText,
                    onExitTentMode = { onSetPostureOverride?.invoke(PostureOverride.AUTO) }
                )
            }

            NativeDashboardCards(
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
                onOpenDefaultLauncherPrompt = onOpenDefaultLauncherPrompt,
                isDefaultLauncher = isDefaultLauncher,
                quickNoteText = quickNoteText,
                onQuickNoteChange = onQuickNoteChange,
                hingeAngleDegrees = postureState.hingeAngleDegrees,
                customAppPairs = appPairs,
                onLaunchAppPair = onLaunchAppPair,
                spatialTiltState = spatialTilt,
                onOpenSpotlight = onOpenSearch
            )
        }

        // Horizontal Hinge Crease Indicator Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(FoldLauncherColors.HingeGuideTint)
        )

        // Lower Tactile Control Deck Hemisphere (below horizontal fold crease)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.54f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FLEX TABLETOP CONTROL DECK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.ElectricCyan
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (showVirtualTrackpad) FoldLauncherColors.CyanGlow
                                else FoldLauncherColors.GlassSurfaceHighlight
                            )
                            .border(
                                1.dp,
                                if (showVirtualTrackpad) FoldLauncherColors.ElectricCyan
                                else FoldLauncherColors.GlassBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { showVirtualTrackpad = !showVirtualTrackpad }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (showVirtualTrackpad) "⊞ Switch to App Grid" else "⊹ Switch to Trackpad",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoldLauncherColors.CyanBright
                        )
                    }
                }
            }

            if (showVirtualTrackpad) {
                FlexVirtualTrackpadCard(
                    favoriteApps = deckApps,
                    onLaunchApp = onAppClick,
                    onContextApp = onAppLongClick,
                    onOpenSearch = onOpenSearch,
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
                    modifier = Modifier.weight(1f)
                )
            } else {
                AppDrawerCategoryPillsBar(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(postureState.recommendedGridColumns.coerceAtLeast(5)),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(FoldLauncherColors.GlassSurface)
                        .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(22.dp))
                        .padding(8.dp)
                ) {
                    items(
                        items = filteredDeckApps,
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

            PersistentTaskbarDock(
                dockApps = dockApps,
                recentApps = recentApps,
                appPairs = appPairs,
                allAppsMap = allAppsMap,
                iconShapePreset = iconShapePreset,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onLaunchAppPair = onLaunchAppPair,
                onOpenAllAppsSearch = onOpenSearch
            )
        }
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
