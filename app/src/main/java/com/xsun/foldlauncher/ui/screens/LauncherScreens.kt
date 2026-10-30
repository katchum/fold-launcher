package com.xsun.foldlauncher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.model.AppPair
import com.xsun.foldlauncher.model.FolderItem
import com.xsun.foldlauncher.posture.DevicePostureState
import com.xsun.foldlauncher.posture.PostureOverride
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.ui.animation.LauncherSpringSpecs
import com.xsun.foldlauncher.ui.components.AppIconTile
import com.xsun.foldlauncher.ui.components.OneUiConnectivityPillsRow
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.ui.theme.IconShapePreset
import com.xsun.foldlauncher.viewmodel.WorkspaceProfile
import kotlinx.coroutines.delay

/**
 * v0.7.0 Fresh, Minimal One UI Home Screen shared across Cover/Slab (4 columns) and Unfolded Inner / Flex (6 columns).
 *
 * Strictly non-scrolling (`Box` + `Column(Modifier.fillMaxSize())` with static `Row`s—zero `LazyVerticalGrid` or
 * `verticalScroll` on the Home Screen) so any upward swipe anywhere on the screen (`detectVerticalDragGestures`)
 * immediately opens the App Search Drawer and pops up the software keyboard (`0ms`).
 *
 * Layout:
 * 1. Top (`statusBarsPadding()`): `OneUiConnectivityPillsRow` (Tailscale + Wireless Debugging card with IP/port & switches).
 * 2. Middle (`Spacer(Modifier.weight(1f))` + static 2-row grid of favorite apps: `8` apps on 4-col Cover/Slab, `12` apps on 6-col Inner).
 * 3. Bottom (`navigationBarsPadding()`):
 *    - Minimal One UI Search Pill (`"Search apps"` — tapping or swiping up anywhere opens App Search).
 *    - Persistent 1-row Bottom Favorites Dock (`4` icons on Cover/Slab, `5` icons on Unfolded Inner).
 */
@Composable
private fun MinimalOneUiHomeScreen(
    columns: Int,
    dockSlotCount: Int,
    isWirelessAdbEnabled: Boolean,
    wirelessAdbPort: Int?,
    tailscaleIp: String?,
    lanIp: String?,
    onToggleWirelessAdb: (() -> Unit)?,
    onOpenWirelessAdbSettings: (() -> Unit)?,
    onToggleTailscaleVpn: (() -> Unit)?,
    onOpenTailscaleApp: (() -> Unit)?,
    favoriteApps: List<AppEntry>,
    dockApps: List<AppEntry>,
    recentApps: List<AppEntry>,
    allApps: List<AppEntry>,
    iconShapePreset: IconShapePreset,
    onAppClick: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onOpenSearch: () -> Unit,
    onExpandNotifications: () -> Unit,
    onDoubleTapLock: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val triggerInstantSearch = remember(onOpenSearch, keyboardController) {
        {
            onOpenSearch()
            keyboardController?.show()
            Unit
        }
    }

    val effectiveDockApps = remember(dockApps, favoriteApps, allApps, dockSlotCount) {
        (dockApps + favoriteApps + allApps)
            .distinctBy { it.packageName }
            .take(dockSlotCount)
    }

    val maxGridApps = columns * 2
    val staticGridApps = remember(favoriteApps, recentApps, allApps, effectiveDockApps, maxGridApps) {
        val dockPkgs = effectiveDockApps.map { it.packageName }.toSet()
        val nonDock = (favoriteApps + recentApps + allApps)
            .distinctBy { it.packageName }
            .filterNot { it.packageName in dockPkgs }
        val pool = if (nonDock.size >= maxGridApps) {
            nonDock
        } else {
            (nonDock + effectiveDockApps).distinctBy { it.packageName }
        }
        pool.take(maxGridApps)
    }

    val gridRows = remember(staticGridApps, columns) {
        staticGridApps.chunked(columns).take(2)
    }

    val horizontalPad = if (columns >= 6) 22.dp else 16.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(triggerInstantSearch, onExpandNotifications) {
                var verticalDragTotal = 0f
                var triggered = false
                detectVerticalDragGestures(
                    onDragStart = {
                        verticalDragTotal = 0f
                        triggered = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        verticalDragTotal += dragAmount
                        if (!triggered) {
                            if (verticalDragTotal < -36f) {
                                triggered = true
                                change.consume()
                                triggerInstantSearch()
                            } else if (verticalDragTotal > 56f) {
                                triggered = true
                                change.consume()
                                onExpandNotifications()
                            }
                        }
                    }
                )
            }
            .pointerInput(onDoubleTapLock, onOpenSettings) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTapLock() },
                    onLongPress = { onOpenSettings() }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = horizontalPad, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top: Clean 2-Row One UI Connectivity Card (Tailscale + Wireless Debugging)
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

            // Push favorite apps + search pill + dock cleanly toward the comfortable bottom thumb zone
            Spacer(modifier = Modifier.weight(1f))

            // 2. Middle: Fixed, non-scrolling 1-2 rows of Favorite Apps (4x2 = 8 on Cover/Slab, 6x2 = 12 on Inner)
            if (gridRows.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    gridRows.forEach { rowApps ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (colIndex in 0 until columns) {
                                val app = rowApps.getOrNull(colIndex)
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (app != null) {
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
                        }
                    }
                }
            }

            // 3. Bottom: Minimal One UI Search Pill ("Search apps") + Persistent 1-Row Bottom Favorites Dock
            OneUiMinimalSearchPill(
                onOpenSearch = triggerInstantSearch,
                onOpenSettings = onOpenSettings
            )

            OneUiMinimalFavoritesDock(
                dockApps = effectiveDockApps,
                slotCount = dockSlotCount,
                iconShapePreset = iconShapePreset,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick
            )
        }
    }
}

/**
 * Sleek, minimal One UI Search Pill (`"Search apps"`).
 * Tapping anywhere on the pill opens the App Search Drawer and immediately focuses the keyboard (`0ms`).
 * Includes a subtle trailing `⚙` icon for Launcher Settings.
 */
@Composable
private fun OneUiMinimalSearchPill(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(pillShape)
            .background(Color(0xD917181E))
            .border(1.dp, Color.White.copy(alpha = 0.10f), pillShape)
            .clickable(onClick = onOpenSearch)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "⌕",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
            )
            Text(
                text = "Search apps",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE2E8F0)
            )
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚙",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

/**
 * Persistent 1-row One UI Bottom Favorites Dock (`4` icons on Cover/Slab, `5` icons on Unfolded Inner).
 * Rendered with a simple non-scrolling `Row` inside a frosted dark-glass container.
 */
@Composable
private fun OneUiMinimalFavoritesDock(
    dockApps: List<AppEntry>,
    slotCount: Int,
    iconShapePreset: IconShapePreset,
    onAppClick: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockShape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(dockShape)
            .background(Color(0xD917181E))
            .border(1.dp, Color.White.copy(alpha = 0.10f), dockShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        dockApps.take(slotCount).forEach { app ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AppIconTile(
                    app = app,
                    iconShapePreset = iconShapePreset,
                    showLabel = false,
                    compact = false,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongClick(app) }
                )
            }
        }
    }
}

/**
 * v0.7.0 Clean Full-Screen One UI App Search Drawer (`OneUiMinimalAppSearchOverlay`).
 * - Triggered by swiping up anywhere on the Home Screen or tapping the `"Search apps"` pill.
 * - Immediately requests focus on the search `BasicTextField` and calls `LocalSoftwareKeyboardController.current?.show()`
 *   with short retries (`0ms`, `40ms`, `120ms`) so the Android IME pops up 100% reliably every single time.
 * - Shows a clean One UI search bar at the top and the filtered grid of installed apps below—with zero
 *   omni-command cheat sheets or clipboard vaults.
 */
@Composable
fun OneUiMinimalAppSearchOverlay(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    rankedApps: List<AppEntry>,
    iconShapePreset: IconShapePreset,
    columns: Int = 4,
    onLaunchApp: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(visible) {
        if (visible) {
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
            delay(40)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
            delay(80)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = LauncherSpringSpecs.SearchSheetSpring) +
            slideInVertically(
                animationSpec = LauncherSpringSpecs.Snappy120HzIntOffset,
                initialOffsetY = { it / 8 }
            ),
        exit = fadeOut(animationSpec = LauncherSpringSpecs.SearchSheetSpring) +
            slideOutVertically(
                animationSpec = LauncherSpringSpecs.Snappy120HzIntOffset,
                targetOffsetY = { it / 8 }
            )
    ) {
        val backdropInteraction = remember { MutableInteractionSource() }
        val searchBarShape = RoundedCornerShape(24.dp)
        val topMatch = rankedApps.firstOrNull()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20D0E12))
                .clickable(
                    interactionSource = backdropInteraction,
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Clean One UI Search Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(searchBarShape)
                        .background(Color(0xFF1C1E26))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), searchBarShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⌕",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search apps",
                                fontSize = 16.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            ),
                            cursorBrush = SolidColor(Color(0xFF38BDF8)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (topMatch != null) {
                                        onLaunchApp(topMatch)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    if (query.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { onQueryChange("") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Close",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                // Filtered Grid of Installed Apps
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(
                        items = rankedApps,
                        key = { "${it.packageName}/${it.activityName}" }
                    ) { app ->
                        AppIconTile(
                            app = app,
                            iconShapePreset = iconShapePreset,
                            showLabel = true,
                            compact = false,
                            onClick = { onLaunchApp(app) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * v0.7.0 Cover Screen (Z Fold 8 folded) & Standard Slab Phone (Galaxy S23 Ultra) Home Screen.
 * Delegates to the clean, non-scrolling 4-column One UI Home Screen (`MinimalOneUiHomeScreen`).
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
    MinimalOneUiHomeScreen(
        columns = 4,
        dockSlotCount = 4,
        isWirelessAdbEnabled = isWirelessAdbEnabled,
        wirelessAdbPort = wirelessAdbPort,
        tailscaleIp = tailscaleIp,
        lanIp = lanIp,
        onToggleWirelessAdb = onToggleWirelessAdb,
        onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
        onToggleTailscaleVpn = onToggleTailscaleVpn,
        onOpenTailscaleApp = onOpenTailscaleApp,
        favoriteApps = pinnedCoverApps,
        dockApps = dockApps,
        recentApps = recentApps,
        allApps = allApps,
        iconShapePreset = iconShapePreset,
        onAppClick = onAppClick,
        onAppLongClick = onAppLongClick,
        onOpenSearch = onOpenSearch,
        onExpandNotifications = onExpandNotifications,
        onDoubleTapLock = onDoubleTapLock,
        onOpenSettings = onOpenSettings,
        modifier = modifier
    )
}

/**
 * v0.7.0 Unfolded 7.6"+ Z Fold 8 Inner Display Home Screen.
 * Delegates to the clean, non-scrolling 6-column One UI Home Screen (`MinimalOneUiHomeScreen`).
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
    MinimalOneUiHomeScreen(
        columns = 6,
        dockSlotCount = 5,
        isWirelessAdbEnabled = isWirelessAdbEnabled,
        wirelessAdbPort = wirelessAdbPort,
        tailscaleIp = tailscaleIp,
        lanIp = lanIp,
        onToggleWirelessAdb = onToggleWirelessAdb,
        onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
        onToggleTailscaleVpn = onToggleTailscaleVpn,
        onOpenTailscaleApp = onOpenTailscaleApp,
        favoriteApps = pinnedInnerApps,
        dockApps = dockApps,
        recentApps = recentApps,
        allApps = allApps,
        iconShapePreset = iconShapePreset,
        onAppClick = onAppClick,
        onAppLongClick = onAppLongClick,
        onOpenSearch = onOpenSearch,
        onExpandNotifications = onExpandNotifications,
        onDoubleTapLock = onDoubleTapLock,
        onOpenSettings = onOpenSettings,
        modifier = modifier
    )
}

/**
 * v0.7.0 Half-Opened Horizontal Flex / Tabletop Posture Home Screen.
 * Delegates to the clean, non-scrolling 6-column One UI Home Screen (`MinimalOneUiHomeScreen`).
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
    MinimalOneUiHomeScreen(
        columns = 6,
        dockSlotCount = 5,
        isWirelessAdbEnabled = isWirelessAdbEnabled,
        wirelessAdbPort = wirelessAdbPort,
        tailscaleIp = tailscaleIp,
        lanIp = lanIp,
        onToggleWirelessAdb = onToggleWirelessAdb,
        onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
        onToggleTailscaleVpn = onToggleTailscaleVpn,
        onOpenTailscaleApp = onOpenTailscaleApp,
        favoriteApps = favoriteApps,
        dockApps = dockApps,
        recentApps = recentApps,
        allApps = allApps,
        iconShapePreset = iconShapePreset,
        onAppClick = onAppClick,
        onAppLongClick = onAppLongClick,
        onOpenSearch = onOpenSearch,
        onExpandNotifications = onExpandNotifications,
        onDoubleTapLock = onDoubleTapLock,
        onOpenSettings = onOpenSettings,
        modifier = modifier
    )
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
                text = "Launcher Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.TextPrimary
            )
            Text(
                text = "Minimal One UI Launcher · Background DeX Gboard Guard",
                fontSize = 12.sp,
                color = FoldLauncherColors.TextSecondary
            )

            HorizontalDivider(color = FoldLauncherColors.GlassBorder)

            // 1. System Status Bar Toggle
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
                        text = "Hide System Status Bar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.TextPrimary
                    )
                    Text(
                        text = "Keep off (recommended) to show native Android clock & status icons",
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

            // 2. Icon Shape Presets
            Text(
                text = "ICON SHAPE",
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

            // 3. System Permissions & One-Time ADB Setup
            Text(
                text = "SYSTEM PERMISSIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.ElectricCyan
            )

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
                        "✓ WRITE_SECURE_SETTINGS Granted (Wireless ADB Toggle & DeX Gboard Guard Active)"
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

            // 4. Hidden Apps Management
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
