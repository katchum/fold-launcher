package com.xsun.foldlauncher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.xsun.foldlauncher.BuildConfig
import com.xsun.foldlauncher.applySystemBarsToWindow
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
import com.xsun.foldlauncher.ui.animation.LauncherSpringSpecs
import com.xsun.foldlauncher.ui.animation.RadialTriggerCorner
import com.xsun.foldlauncher.ui.animation.launcherGestures
import com.xsun.foldlauncher.ui.components.AppIconTile
import com.xsun.foldlauncher.ui.components.OneUiConnectivityPillsRow
import com.xsun.foldlauncher.ui.components.OneUiQuickNavigationBar
import com.xsun.foldlauncher.ui.components.PersistentTaskbarDock
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.ui.theme.IconShapePreset
import com.xsun.foldlauncher.vc.VcBackgroundMonitor
import com.xsun.foldlauncher.vc.VcCommandCenterCard
import com.xsun.foldlauncher.vc.VcLiveSummary
import com.xsun.foldlauncher.viewmodel.WorkspaceProfile
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HOME_PAGE_TITLES = listOf("Dev", "Main", "Work")

/**
 * v1.0.0 Three-Page (`Dev` on left | `Main` in middle | `Work` on right) Minimal One UI Home Screen (`minimal` edition).
 *
 * - Uses `HorizontalPager` (`rememberPagerState(initialPage = 1, pageCount = { 3 })`) across all postures:
 *   - Page 0 (Left — `Dev`): Shows `OneUiConnectivityPillsRow` (Tailscale switch + IP & Wireless Debugging switch +
 *     `<ip>:<port>`) at the top, plus the user's configured Dev Apps grid below.
 *   - Page 1 (Middle — `Main`, Default Home): Shows ONLY the user's configured Main Apps grid over the system wallpaper
 *     (up to 12 apps on 4-col Cover/Slab or 18 apps on 6-col Inner, rendered in static non-scrolling rows).
 *   - Page 2 (Right — `Work`): Exclusively shows the user's Work Profile (`UserHandle{10}`) / Work Apps grid + an
 *     understated `"+ Edit Work Apps"` pill that opens the multi-select page app editor sheet.
 * - No bottom dock and no home search bar on any of the 3 pages—only a minimal 3-page indicator (`Dev • Main • Work`)
 *   with a subtle `✎` page-apps edit button at the bottom.
 * - Swiping up opens App Search; swiping down opens the Samsung One UI Quick Settings / Control Center pane.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinimalOneUiHomeScreen(
    columns: Int,
    hideSystemStatusBar: Boolean = false,
    isWirelessAdbEnabled: Boolean,
    wirelessAdbPort: Int?,
    tailscaleIp: String?,
    lanIp: String?,
    onToggleWirelessAdb: (() -> Unit)?,
    onOpenWirelessAdbSettings: (() -> Unit)?,
    onToggleTailscaleVpn: (() -> Unit)?,
    onOpenTailscaleApp: (() -> Unit)?,
    mainPageApps: List<AppEntry>,
    workPageApps: List<AppEntry>,
    devPageApps: List<AppEntry>,
    allApps: List<AppEntry>,
    iconShapePreset: IconShapePreset,
    onTogglePageApp: ((Int, String) -> Unit)?,
    onAppClick: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onOpenSearch: () -> Unit,
    onExpandNotifications: () -> Unit,
    onDoubleTapLock: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    LaunchedEffect(hideSystemStatusBar) {
        val actWindow = (view.context as? android.app.Activity)?.window
        applySystemBarsToWindow(actWindow, hideSystemStatusBar)
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val axisLockPx = with(density) { 12.dp.toPx() }
    val triggerPx = with(density) { 56.dp.toPx() }
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    var editingPageIndex by remember { mutableIntStateOf(-1) }

    BackHandler(enabled = pagerState.currentPage != 1 && editingPageIndex < 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(1)
        }
    }

    val triggerInstantSearch = remember(onOpenSearch, keyboardController) {
        {
            onOpenSearch()
            keyboardController?.show()
            Unit
        }
    }

    val maxAppsPerPage = columns * 3
    val horizontalPad = if (columns >= 6) 22.dp else 16.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(axisLockPx, triggerPx, triggerInstantSearch, onExpandNotifications) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var totalDx = 0f
                    var totalDy = 0f
                    // 0 = undecided, 1 = locked horizontal (Pager owns gesture), 2 = locked vertical (Search/QuickSettings owns gesture)
                    var gestureAxis = 0
                    var triggered = false
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break
                        if (!change.pressed) break
                        val delta = change.position - change.previousPosition
                        totalDx += delta.x
                        totalDy += delta.y

                        if (gestureAxis == 0) {
                            val absX = abs(totalDx)
                            val absY = abs(totalDy)
                            if (absX > axisLockPx || absY > axisLockPx || pagerState.isScrollInProgress) {
                                gestureAxis = if (pagerState.isScrollInProgress || absX * 1.15f >= absY) {
                                    1 // Locked to horizontal page swipe: never trigger Search or Quick Settings
                                } else if (absY >= absX * 1.8f) {
                                    2 // Locked to vertical swipe: consume events before HorizontalPager sees them
                                } else {
                                    1 // Ambiguous diagonal defaults safely to horizontal / no-search
                                }
                            }
                        }

                        if (gestureAxis == 2) {
                            // Consume in Initial pass so HorizontalPager cannot scroll horizontally during a vertical swipe
                            change.consume()
                            if (!triggered) {
                                if (totalDy < -triggerPx && abs(totalDy) > abs(totalDx) * 2.0f) {
                                    triggered = true
                                    triggerInstantSearch()
                                } else if (totalDy > triggerPx && abs(totalDy) > abs(totalDx) * 2.0f) {
                                    triggered = true
                                    onExpandNotifications()
                                }
                            }
                        }
                    }
                }
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
                .padding(vertical = 12.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                when (pageIndex) {
                    0 -> {
                        // Page 0 (Left) — Development View: OneUiConnectivityPillsRow at top + configured Dev Apps grid
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = horizontalPad)
                        ) {
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

                            Spacer(modifier = Modifier.weight(1f))

                            StaticNonScrollingAppGrid(
                                apps = devPageApps.take(columns * 2),
                                columns = columns,
                                iconShapePreset = iconShapePreset,
                                onAppClick = onAppClick,
                                onAppLongClick = onAppLongClick
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    1 -> {
                        // Page 1 (Middle — Default Home) — Main View: Top Quick Navigation Bar + configured Main Apps grid over the system wallpaper
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = horizontalPad)
                        ) {
                            OneUiQuickNavigationBar(
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            StaticNonScrollingAppGrid(
                                apps = mainPageApps.take(maxAppsPerPage),
                                columns = columns,
                                iconShapePreset = iconShapePreset,
                                onAppClick = onAppClick,
                                onAppLongClick = onAppLongClick
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    else -> {
                        // Page 2 (Right) — Work View: Exclusively the user's Work Profile / Work Apps + understated Edit pill
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = horizontalPad),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "WORK",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0x9917181E))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                        .clickable { editingPageIndex = 2 }
                                        .padding(horizontal = 11.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "+ Edit Work Apps",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            StaticNonScrollingAppGrid(
                                apps = workPageApps.take(columns * 5),
                                columns = columns,
                                iconShapePreset = iconShapePreset,
                                compact = workPageApps.size > maxAppsPerPage,
                                onAppClick = onAppClick,
                                onAppLongClick = onAppLongClick
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            // Minimal 3-Page Indicator ("Dev  •  Main  •  Work") + subtle "✎" page apps editor button
            OneUiMinimalPageIndicatorBar(
                currentPage = pagerState.currentPage,
                onSelectPage = { targetPage ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(targetPage)
                    }
                },
                onEditCurrentPageApps = {
                    editingPageIndex = pagerState.currentPage
                },
                modifier = Modifier.padding(horizontal = horizontalPad)
            )
        }

        if (editingPageIndex in 0..2) {
            val activeSet = when (editingPageIndex) {
                0 -> devPageApps.map { it.uniqueKey }.toSet()
                1 -> mainPageApps.map { it.uniqueKey }.toSet()
                else -> workPageApps.map { it.uniqueKey }.toSet()
            }
            EditPageAppsSheet(
                pageIndex = editingPageIndex,
                hideSystemStatusBar = hideSystemStatusBar,
                onSelectPageIndex = { editingPageIndex = it },
                allApps = allApps,
                selectedPackages = activeSet,
                iconShapePreset = iconShapePreset,
                onToggleApp = { pkg ->
                    onTogglePageApp?.invoke(editingPageIndex, pkg)
                },
                onOpenLauncherSettings = {
                    editingPageIndex = -1
                    onOpenSettings()
                },
                onDismiss = { editingPageIndex = -1 }
            )
        }
    }
}

/**
 * Static, non-scrolling rows of app icons (`Row` + `Column`, never `LazyVerticalGrid`).
 */
@Composable
private fun StaticNonScrollingAppGrid(
    apps: List<AppEntry>,
    columns: Int,
    iconShapePreset: IconShapePreset,
    onAppClick: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (apps.isEmpty()) return
    val rows = remember(apps, columns) {
        apps.chunked(columns)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
    ) {
        rows.forEach { rowApps ->
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
                                compact = compact,
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

/**
 * Minimal 3-page indicator (`Main  •  Work  •  Dev`) with a subtle active pill and a small `✎` button
 * to edit the current page's apps.
 */
@Composable
private fun OneUiMinimalPageIndicatorBar(
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
    onEditCurrentPageApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x9914151B))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HOME_PAGE_TITLES.forEachIndexed { index, title ->
                val selected = index == currentPage
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) Color.White.copy(alpha = 0.14f)
                            else Color.Transparent
                        )
                        .clickable { onSelectPage(index) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else Color(0xFF94A3B8)
                    )
                }
                if (index < HOME_PAGE_TITLES.lastIndex) {
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.25f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onEditCurrentPageApps),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✎",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}

/**
 * Multi-select bottom sheet to check/uncheck which installed apps belong to `Main`, `Work`, or `Dev`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPageAppsSheet(
    pageIndex: Int,
    hideSystemStatusBar: Boolean = false,
    onSelectPageIndex: (Int) -> Unit,
    allApps: List<AppEntry>,
    selectedPackages: Set<String>,
    iconShapePreset: IconShapePreset,
    onToggleApp: (String) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pageTitle = HOME_PAGE_TITLES.getOrElse(pageIndex) { "Page" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF17181E),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        val sheetView = LocalView.current
        LaunchedEffect(hideSystemStatusBar) {
            val dialogWin = (sheetView.parent as? DialogWindowProvider)?.window
            val actWin = (sheetView.context as? android.app.Activity)?.window
            applySystemBarsToWindow(dialogWin, hideSystemStatusBar)
            applySystemBarsToWindow(actWin, hideSystemStatusBar)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit $pageTitle Apps",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${selectedPackages.size} apps selected for $pageTitle page",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onOpenLauncherSettings)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "⚙ Settings",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HOME_PAGE_TITLES.forEachIndexed { idx, label ->
                    val active = idx == pageIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (active) Color(0xFF0381FE).copy(alpha = 0.24f)
                                else Color.White.copy(alpha = 0.06f)
                            )
                            .border(
                                1.dp,
                                if (active) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectPageIndex(idx) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            val sortedApps = remember(allApps, pageIndex) {
                if (pageIndex == 2) {
                    allApps.sortedWith(
                        compareByDescending<AppEntry> { it.isWorkProfile }
                            .thenBy { it.label.lowercase() }
                    )
                } else {
                    allApps
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sortedApps.forEach { app ->
                    val checked = app.uniqueKey in selectedPackages ||
                        (!app.isWorkProfile && app.packageName in selectedPackages)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (checked) Color(0xFF0381FE).copy(alpha = 0.16f)
                                else Color.White.copy(alpha = 0.04f)
                            )
                            .border(
                                1.dp,
                                if (checked) Color(0xFF38BDF8).copy(alpha = 0.55f)
                                else Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { onToggleApp(app.uniqueKey) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppIconTile(
                                app = app,
                                iconShapePreset = iconShapePreset,
                                showLabel = false,
                                compact = true,
                                onClick = { onToggleApp(app.uniqueKey) },
                                onLongClick = {}
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (app.isWorkProfile) "Work Profile · ${app.packageName}" else app.packageName,
                                    fontSize = 11.sp,
                                    color = if (app.isWorkProfile) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (checked) Color(0xFF38BDF8)
                                    else Color.White.copy(alpha = 0.08f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (checked) "✓" else "",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0D0E12)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * v1.0.0 Swipe-Up App Search Overlay (`OneUiMinimalAppSearchOverlay`) with:
 *
 * 1. Horizontally Scrollable Recent App Thumbnail Cards (`RecentAppsThumbnailStrip`) on Top:
 *    - Shown immediately when Search opens with an empty query (`query.trim().isEmpty()`), AND kept on top
 *      while typing (`query.trim().isNotEmpty()`).
 *    - Horizontally scrollable `LazyRow` of rich mini-window thumbnail preview cards (`RecentAppThumbnailCard`).
 * 2. Smooth 2-Row Suggested Matches Section on Bottom When Typing:
 *    - When `query.trim().isNotEmpty()`, smoothly animates in (`AnimatedVisibility` with `expandVertically` +
 *      `fadeIn` using `LauncherSpringSpecs.Snappy120HzIntOffset` & `animateContentSize`) a 2-row (`4` per row,
 *      up to `8` apps) Suggested Matches card right below the Recent Apps thumbnail strip and directly above the
 *      bottom-docked search input box.
 * 3. Search Text Box Docked Right on Top of the Keyboard:
 *    - Anchored at the bottom of the overlay (`Modifier.fillMaxSize().imePadding().navigationBarsPadding()`),
 *      sitting directly on top of the software keyboard.
 * 4. No Close (`✕`) Button — Swipe Down (`>= 48.dp`), Backdrop Tap, or System Back Exits Search:
 *    - Swiping down anywhere on the Search overlay (`detectVerticalDragGestures` downward drag `>= 48.dp`)
 *      cleanly hides the keyboard and exits Search (`keyboardController?.hide(); onDismiss()`), symmetric with
 *      swiping up on Home to enter Search.
 */
@Composable
fun OneUiMinimalAppSearchOverlay(
    visible: Boolean,
    hideSystemStatusBar: Boolean = false,
    query: String,
    onQueryChange: (String) -> Unit,
    recentApps: List<AppEntry> = emptyList(),
    rankedApps: List<AppEntry>,
    iconShapePreset: IconShapePreset,
    columns: Int = 4,
    onLaunchApp: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val swipeDownDismissPx = with(density) { 48.dp.toPx() }

    val dismissSearchCleanly = remember(keyboardController, onDismiss) {
        {
            keyboardController?.hide()
            onDismiss()
        }
    }

    LaunchedEffect(visible, hideSystemStatusBar) {
        val actWin = (view.context as? android.app.Activity)?.window
        applySystemBarsToWindow(actWin, hideSystemStatusBar)
        if (visible) {
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
            delay(40)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
            delay(80)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
            applySystemBarsToWindow(actWin, hideSystemStatusBar)
        } else {
            keyboardController?.hide()
            applySystemBarsToWindow(actWin, hideSystemStatusBar)
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
        val searchBarShape = RoundedCornerShape(24.dp)
        val trimmedQuery = query.trim()
        val displayRecentApps = remember(recentApps, rankedApps) {
            if (recentApps.isNotEmpty()) recentApps else rankedApps.take(8)
        }
        val topMatches = remember(trimmedQuery, rankedApps) {
            if (trimmedQuery.isEmpty()) emptyList() else rankedApps.take(8)
        }
        val matchRows = remember(topMatches) {
            topMatches.chunked(4).take(2)
        }
        val topMatch = topMatches.firstOrNull()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE60B0C10))
                .pointerInput(swipeDownDismissPx, dismissSearchCleanly) {
                    var verticalDragTotal = 0f
                    var dismissed = false
                    detectVerticalDragGestures(
                        onDragStart = {
                            verticalDragTotal = 0f
                            dismissed = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            verticalDragTotal += dragAmount
                            if (!dismissed && verticalDragTotal >= swipeDownDismissPx) {
                                dismissed = true
                                change.consume()
                                dismissSearchCleanly()
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Tapping the empty area above the results dismisses Search and hides the keyboard
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = dismissSearchCleanly
                        )
                )

                // Smoothly animated bottom-anchored Search deck:
                // - Top: Horizontally Scrollable Recent App Thumbnail Cards (always visible when empty AND when typing)
                // - Bottom: 2-Row Suggested Matches section that smoothly expands underneath Recent Apps when typing
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                ) {
                    if (displayRecentApps.isNotEmpty()) {
                        RecentAppsThumbnailStrip(
                            recentApps = displayRecentApps,
                            iconShapePreset = iconShapePreset,
                            compact = matchRows.isNotEmpty(),
                            onLaunchApp = onLaunchApp,
                            onAppLongClick = onAppLongClick
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    AnimatedVisibility(
                        visible = matchRows.isNotEmpty(),
                        enter = expandVertically(
                            animationSpec = spring(
                                dampingRatio = 0.84f,
                                stiffness = 500f
                            ),
                            expandFrom = Alignment.Bottom
                        ) + fadeIn(animationSpec = LauncherSpringSpecs.SearchSheetSpring),
                        exit = shrinkVertically(
                            animationSpec = spring(
                                dampingRatio = 0.84f,
                                stiffness = 500f
                            ),
                            shrinkTowards = Alignment.Bottom
                        ) + fadeOut(animationSpec = LauncherSpringSpecs.SearchSheetSpring)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(Color(0xD917181E))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SUGGESTED MATCHES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.0.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = "${topMatches.size} ${if (topMatches.size == 1) "app" else "apps"}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF38BDF8)
                                )
                            }

                            matchRows.forEach { rowApps ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (colIdx in 0 until 4) {
                                        val app = rowApps.getOrNull(colIdx)
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
                                                    onClick = { onLaunchApp(app) },
                                                    onLongClick = { onAppLongClick(app) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Search Text Box Docked Right on Top of the Keyboard (no close '✕' button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(searchBarShape)
                        .background(Color(0xFF1C1E26))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), searchBarShape)
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
                }
            }
        }
    }
}

/**
 * Horizontally scrollable Recent Apps thumbnail strip (`RecentAppsThumbnailStrip`) displayed at the top
 * of `OneUiMinimalAppSearchOverlay` both when the search box is empty and while typing.
 */
@Composable
private fun RecentAppsThumbnailStrip(
    recentApps: List<AppEntry>,
    iconShapePreset: IconShapePreset,
    compact: Boolean = false,
    onLaunchApp: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xCC14161D))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT APPS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp,
                color = Color(0xFF94A3B8)
            )
            Text(
                text = "Swipe ⟷",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.45f)
            )
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = recentApps,
                key = { it.uniqueKey }
            ) { app ->
                RecentAppThumbnailCard(
                    app = app,
                    iconShapePreset = iconShapePreset,
                    compact = compact,
                    onClick = { onLaunchApp(app) },
                    onLongClick = { onAppLongClick(app) }
                )
            }
        }
    }
}

/**
 * Individual Recent App mini-window thumbnail card (`RecentAppThumbnailCard`) inside `RecentAppsThumbnailStrip`.
 */
@Composable
private fun RecentAppThumbnailCard(
    app: AppEntry,
    iconShapePreset: IconShapePreset,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cardWidth = if (compact) 94.dp else 102.dp
    val cardHeight = if (compact) 114.dp else 128.dp
    val cardShape = RoundedCornerShape(20.dp)
    val previewSurfaceShape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(cardShape)
            .background(Color(0xE61B1D26))
            .border(
                1.dp,
                if (app.isWorkProfile) Color(0xFF0381FE).copy(alpha = 0.40f)
                else Color.White.copy(alpha = 0.10f),
                cardShape
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Mini-window title bar (subtle multitasking window chrome + Work profile indicator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38BDF8).copy(alpha = 0.65f))
                )
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                )
            }
            if (app.isWorkProfile) {
                Text(
                    text = "WORK",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF38BDF8)
                )
            }
        }

        // Mini-window preview surface with centered app icon + briefcase badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp)
                .clip(previewSurfaceShape)
                .background(Color.White.copy(alpha = 0.04f))
                .border(0.5.dp, Color.White.copy(alpha = 0.06f), previewSurfaceShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            AppIconTile(
                app = app,
                iconShapePreset = iconShapePreset,
                showLabel = false,
                compact = compact,
                onClick = onClick,
                onLongClick = onLongClick
            )
        }

        // Bottom app title
        Text(
            text = app.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}

// ============================================================================
// Experimental Max Feature Edition (`com.xsun.foldlauncher.max`, BuildConfig.IS_MAX_EDITION == true)
// ============================================================================

@Composable
fun WorkspaceProfilePillBar(
    workspaceProfile: WorkspaceProfile,
    onSelectWorkspaceProfile: (WorkspaceProfile) -> Unit,
    isTorchEnabled: Boolean = false,
    onToggleTorch: (() -> Unit)? = null,
    isDualScreenPresentationActive: Boolean = false,
    onToggleDualScreenPresentation: (() -> Unit)? = null,
    hingeHapticsEnabled: Boolean = true,
    onToggleHingeHaptics: (() -> Unit)? = null,
    foldCloseModeLabel: String = "📱 Keep Cover Awake",
    onCycleFoldCloseActionMode: (() -> Unit)? = null,
    unfoldModeLabel: String = "🖥️ Inner Cockpit",
    onCycleUnfoldActionMode: (() -> Unit)? = null,
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
                    .background(if (selected) FoldLauncherColors.CyanGlow else surfaceColor)
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

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isTorchEnabled) FoldLauncherColors.AmberWarning.copy(alpha = 0.22f) else surfaceColor)
                .border(
                    1.dp,
                    if (isTorchEnabled) FoldLauncherColors.AmberWarning else FoldLauncherColors.GlassBorder,
                    RoundedCornerShape(14.dp)
                )
                .clickable {
                    if (onToggleTorch != null) onToggleTorch() else SystemTweaker.toggleTorch(context, isTorchEnabled)
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

        if (onToggleDualScreenPresentation != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDualScreenPresentationActive) FoldLauncherColors.CyanGlow else surfaceColor)
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

        if (onToggleHingeHaptics != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (hingeHapticsEnabled) FoldLauncherColors.CyanGlow else surfaceColor)
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
    }
}

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

@Composable
private fun MaxEditionHomeScreen(
    columns: Int,
    isFlexPosture: Boolean = false,
    postureState: DevicePostureState,
    onSetPostureOverride: ((PostureOverride) -> Unit)?,
    workspaceProfile: WorkspaceProfile,
    onSelectWorkspaceProfile: ((WorkspaceProfile) -> Unit)?,
    isTorchEnabled: Boolean,
    onToggleTorch: (() -> Unit)?,
    isDualScreenPresentationActive: Boolean,
    onToggleDualScreenPresentation: (() -> Unit)?,
    hingeHapticsEnabled: Boolean,
    onToggleHingeHaptics: (() -> Unit)?,
    foldCloseModeLabel: String,
    onCycleFoldCloseActionMode: (() -> Unit)?,
    unfoldModeLabel: String,
    onCycleUnfoldActionMode: (() -> Unit)?,
    isWirelessAdbEnabled: Boolean,
    wirelessAdbPort: Int?,
    tailscaleIp: String?,
    lanIp: String?,
    onToggleWirelessAdb: (() -> Unit)?,
    onOpenWirelessAdbSettings: (() -> Unit)?,
    onToggleTailscaleVpn: (() -> Unit)?,
    onOpenTailscaleApp: (() -> Unit)?,
    allApps: List<AppEntry>,
    favoriteApps: List<AppEntry>,
    dockApps: List<AppEntry>,
    recentApps: List<AppEntry>,
    appPairs: List<AppPair>,
    iconShapePreset: IconShapePreset,
    batteryPct: Int,
    isCharging: Boolean,
    nextEventText: String,
    onAppClick: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onLaunchAppPair: (AppPair) -> Unit,
    onOpenSearch: () -> Unit,
    onExpandNotifications: () -> Unit,
    onDoubleTapLock: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val vcMonitor = remember(context) { VcBackgroundMonitor.getInstance(context) }
    val vcSummary by vcMonitor.liveSummary.collectAsState()
    val vcConfig by vcMonitor.config.collectAsState()
    val vcFeedback by vcMonitor.lastSendFeedback.collectAsState()

    var vcExpanded by remember { mutableStateOf(false) }
    var showVirtualTrackpad by remember { mutableStateOf(false) }
    var arcTriggerGeneration by remember { mutableIntStateOf(0) }
    var arcRightHanded by remember { mutableStateOf(true) }
    var arcStartExpanded by remember { mutableStateOf(false) }

    val triggerInstantSearch = remember(onOpenSearch, keyboardController) {
        {
            onOpenSearch()
            keyboardController?.show()
            Unit
        }
    }

    val allAppsMap = remember(allApps) { allApps.associateBy { it.packageName } }
    val homeGridApps = remember(favoriteApps, recentApps, allApps, columns) {
        (favoriteApps + recentApps + allApps).distinctBy { it.packageName }.take(columns * 4)
    }
    val radialArcApps = remember(favoriteApps, recentApps, allApps) {
        (favoriteApps + recentApps + allApps).distinctBy { it.packageName }.take(8)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
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
                .padding(horizontal = if (columns >= 6) 20.dp else 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WorkspaceProfilePillBar(
                workspaceProfile = workspaceProfile,
                onSelectWorkspaceProfile = { onSelectWorkspaceProfile?.invoke(it) },
                isTorchEnabled = isTorchEnabled,
                onToggleTorch = onToggleTorch,
                isDualScreenPresentationActive = isDualScreenPresentationActive,
                onToggleDualScreenPresentation = onToggleDualScreenPresentation,
                hingeHapticsEnabled = hingeHapticsEnabled,
                onToggleHingeHaptics = onToggleHingeHaptics,
                foldCloseModeLabel = foldCloseModeLabel,
                onCycleFoldCloseActionMode = onCycleFoldCloseActionMode,
                unfoldModeLabel = unfoldModeLabel,
                onCycleUnfoldActionMode = onCycleUnfoldActionMode
            )

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
                if (isFlexPosture) {
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

            if (postureState.isTentMode) {
                TentModeStandbyHud(
                    hingeAngleDegrees = postureState.hingeAngleDegrees,
                    batteryPct = batteryPct,
                    isCharging = isCharging,
                    nextEventText = nextEventText,
                    vcSummary = vcSummary,
                    onTalkToVc = { vcMonitor.launchVcVoiceTalk() },
                    onOpenVcApp = { vcMonitor.launchVc(null) },
                    onExitTentMode = { onSetPostureOverride?.invoke(PostureOverride.AUTO) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isFlexPosture && showVirtualTrackpad) {
                    FlexVirtualTrackpadCard(
                        favoriteApps = homeGridApps,
                        onLaunchApp = onAppClick,
                        onContextApp = onAppLongClick,
                        onOpenSearch = triggerInstantSearch,
                        onTriggerSplitScreen = {
                            val firstPair = appPairs.firstOrNull()
                            if (firstPair != null) onLaunchAppPair(firstPair) else vcMonitor.launchVcSplitScreen()
                        },
                        onQuickLaunchVc = { vcMonitor.launchVc(null) },
                        hingeAngleDegrees = postureState.hingeAngleDegrees,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        userScrollEnabled = false,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (columns >= 6) 12.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = homeGridApps,
                            key = { "${it.uniqueKey}/${it.activityName}" }
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
 * v0.8.0 Cover Screen (Z Fold 8 folded) & Standard Slab Phone (Galaxy S23 Ultra) Home Screen.
 * - In `minimal` flavor (`BuildConfig.IS_MAX_EDITION == false`): renders the 3-page (`Main` | `Work` | `Development`)
 *   4-column One UI Home Screen (`MinimalOneUiHomeScreen`).
 * - In `max` flavor (`BuildConfig.IS_MAX_EDITION == true`): renders the full experimental `MaxEditionHomeScreen`.
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
    mainPageApps: List<AppEntry> = pinnedCoverApps,
    workPageApps: List<AppEntry> = emptyList(),
    devPageApps: List<AppEntry> = emptyList(),
    onTogglePageApp: ((Int, String) -> Unit)? = null,
    hideSystemStatusBar: Boolean = false,
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
    if (BuildConfig.IS_MAX_EDITION) {
        MaxEditionHomeScreen(
            columns = 4,
            isFlexPosture = false,
            postureState = postureState,
            onSetPostureOverride = onSetPostureOverride,
            workspaceProfile = workspaceProfile,
            onSelectWorkspaceProfile = onSelectWorkspaceProfile,
            isTorchEnabled = isTorchEnabled,
            onToggleTorch = onToggleTorch,
            isDualScreenPresentationActive = isDualScreenPresentationActive,
            onToggleDualScreenPresentation = onToggleDualScreenPresentation,
            hingeHapticsEnabled = hingeHapticsEnabled,
            onToggleHingeHaptics = onToggleHingeHaptics,
            foldCloseModeLabel = foldCloseModeLabel,
            onCycleFoldCloseActionMode = onCycleFoldCloseActionMode,
            unfoldModeLabel = unfoldModeLabel,
            onCycleUnfoldActionMode = onCycleUnfoldActionMode,
            isWirelessAdbEnabled = isWirelessAdbEnabled,
            wirelessAdbPort = wirelessAdbPort,
            tailscaleIp = tailscaleIp,
            lanIp = lanIp,
            onToggleWirelessAdb = onToggleWirelessAdb,
            onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
            onToggleTailscaleVpn = onToggleTailscaleVpn,
            onOpenTailscaleApp = onOpenTailscaleApp,
            allApps = allApps,
            favoriteApps = pinnedCoverApps,
            dockApps = dockApps,
            recentApps = recentApps,
            appPairs = appPairs,
            iconShapePreset = iconShapePreset,
            batteryPct = batteryPct,
            isCharging = isCharging,
            nextEventText = nextEventText,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick,
            onLaunchAppPair = onLaunchAppPair,
            onOpenSearch = onOpenSearch,
            onExpandNotifications = onExpandNotifications,
            onDoubleTapLock = onDoubleTapLock,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
    } else {
        MinimalOneUiHomeScreen(
            columns = 4,
            hideSystemStatusBar = hideSystemStatusBar,
            isWirelessAdbEnabled = isWirelessAdbEnabled,
            wirelessAdbPort = wirelessAdbPort,
            tailscaleIp = tailscaleIp,
            lanIp = lanIp,
            onToggleWirelessAdb = onToggleWirelessAdb,
            onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
            onToggleTailscaleVpn = onToggleTailscaleVpn,
            onOpenTailscaleApp = onOpenTailscaleApp,
            mainPageApps = mainPageApps,
            workPageApps = workPageApps,
            devPageApps = devPageApps,
            allApps = allApps,
            iconShapePreset = iconShapePreset,
            onTogglePageApp = onTogglePageApp,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick,
            onOpenSearch = onOpenSearch,
            onExpandNotifications = onExpandNotifications,
            onDoubleTapLock = onDoubleTapLock,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
    }
}

/**
 * v0.8.0 Unfolded 7.6"+ Z Fold 8 Inner Display Home Screen.
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
    mainPageApps: List<AppEntry> = pinnedInnerApps,
    workPageApps: List<AppEntry> = emptyList(),
    devPageApps: List<AppEntry> = emptyList(),
    onTogglePageApp: ((Int, String) -> Unit)? = null,
    hideSystemStatusBar: Boolean = false,
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
    if (BuildConfig.IS_MAX_EDITION) {
        MaxEditionHomeScreen(
            columns = 6,
            isFlexPosture = false,
            postureState = postureState,
            onSetPostureOverride = onSetPostureOverride,
            workspaceProfile = workspaceProfile,
            onSelectWorkspaceProfile = onSelectWorkspaceProfile,
            isTorchEnabled = isTorchEnabled,
            onToggleTorch = onToggleTorch,
            isDualScreenPresentationActive = isDualScreenPresentationActive,
            onToggleDualScreenPresentation = onToggleDualScreenPresentation,
            hingeHapticsEnabled = hingeHapticsEnabled,
            onToggleHingeHaptics = onToggleHingeHaptics,
            foldCloseModeLabel = foldCloseModeLabel,
            onCycleFoldCloseActionMode = onCycleFoldCloseActionMode,
            unfoldModeLabel = unfoldModeLabel,
            onCycleUnfoldActionMode = onCycleUnfoldActionMode,
            isWirelessAdbEnabled = isWirelessAdbEnabled,
            wirelessAdbPort = wirelessAdbPort,
            tailscaleIp = tailscaleIp,
            lanIp = lanIp,
            onToggleWirelessAdb = onToggleWirelessAdb,
            onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
            onToggleTailscaleVpn = onToggleTailscaleVpn,
            onOpenTailscaleApp = onOpenTailscaleApp,
            allApps = allApps,
            favoriteApps = pinnedInnerApps,
            dockApps = dockApps,
            recentApps = recentApps,
            appPairs = appPairs,
            iconShapePreset = iconShapePreset,
            batteryPct = batteryPct,
            isCharging = isCharging,
            nextEventText = nextEventText,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick,
            onLaunchAppPair = onLaunchAppPair,
            onOpenSearch = onOpenSearch,
            onExpandNotifications = onExpandNotifications,
            onDoubleTapLock = onDoubleTapLock,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
    } else {
        MinimalOneUiHomeScreen(
            columns = 6,
            hideSystemStatusBar = hideSystemStatusBar,
            isWirelessAdbEnabled = isWirelessAdbEnabled,
            wirelessAdbPort = wirelessAdbPort,
            tailscaleIp = tailscaleIp,
            lanIp = lanIp,
            onToggleWirelessAdb = onToggleWirelessAdb,
            onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
            onToggleTailscaleVpn = onToggleTailscaleVpn,
            onOpenTailscaleApp = onOpenTailscaleApp,
            mainPageApps = mainPageApps,
            workPageApps = workPageApps,
            devPageApps = devPageApps,
            allApps = allApps,
            iconShapePreset = iconShapePreset,
            onTogglePageApp = onTogglePageApp,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick,
            onOpenSearch = onOpenSearch,
            onExpandNotifications = onExpandNotifications,
            onDoubleTapLock = onDoubleTapLock,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
    }
}

/**
 * v0.8.0 Half-Opened Horizontal Flex / Tabletop Posture Home Screen.
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
    mainPageApps: List<AppEntry> = favoriteApps,
    workPageApps: List<AppEntry> = emptyList(),
    devPageApps: List<AppEntry> = emptyList(),
    onTogglePageApp: ((Int, String) -> Unit)? = null,
    hideSystemStatusBar: Boolean = false,
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
    if (BuildConfig.IS_MAX_EDITION) {
        MaxEditionHomeScreen(
            columns = 6,
            isFlexPosture = true,
            postureState = postureState,
            onSetPostureOverride = onSetPostureOverride,
            workspaceProfile = workspaceProfile,
            onSelectWorkspaceProfile = onSelectWorkspaceProfile,
            isTorchEnabled = isTorchEnabled,
            onToggleTorch = onToggleTorch,
            isDualScreenPresentationActive = isDualScreenPresentationActive,
            onToggleDualScreenPresentation = onToggleDualScreenPresentation,
            hingeHapticsEnabled = hingeHapticsEnabled,
            onToggleHingeHaptics = onToggleHingeHaptics,
            foldCloseModeLabel = foldCloseModeLabel,
            onCycleFoldCloseActionMode = onCycleFoldCloseActionMode,
            unfoldModeLabel = unfoldModeLabel,
            onCycleUnfoldActionMode = onCycleUnfoldActionMode,
            isWirelessAdbEnabled = isWirelessAdbEnabled,
            wirelessAdbPort = wirelessAdbPort,
            tailscaleIp = tailscaleIp,
            lanIp = lanIp,
            onToggleWirelessAdb = onToggleWirelessAdb,
            onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
            onToggleTailscaleVpn = onToggleTailscaleVpn,
            onOpenTailscaleApp = onOpenTailscaleApp,
            allApps = allApps,
            favoriteApps = favoriteApps,
            dockApps = dockApps,
            recentApps = recentApps,
            appPairs = appPairs,
            iconShapePreset = iconShapePreset,
            batteryPct = batteryPct,
            isCharging = isCharging,
            nextEventText = nextEventText,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick,
            onLaunchAppPair = onLaunchAppPair,
            onOpenSearch = onOpenSearch,
            onExpandNotifications = onExpandNotifications,
            onDoubleTapLock = onDoubleTapLock,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
    } else {
        MinimalOneUiHomeScreen(
            columns = 6,
            hideSystemStatusBar = hideSystemStatusBar,
            isWirelessAdbEnabled = isWirelessAdbEnabled,
            wirelessAdbPort = wirelessAdbPort,
            tailscaleIp = tailscaleIp,
            lanIp = lanIp,
            onToggleWirelessAdb = onToggleWirelessAdb,
            onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
            onToggleTailscaleVpn = onToggleTailscaleVpn,
            onOpenTailscaleApp = onOpenTailscaleApp,
            mainPageApps = mainPageApps,
            workPageApps = workPageApps,
            devPageApps = devPageApps,
            allApps = allApps,
            iconShapePreset = iconShapePreset,
            onTogglePageApp = onTogglePageApp,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick,
            onOpenSearch = onOpenSearch,
            onExpandNotifications = onExpandNotifications,
            onDoubleTapLock = onDoubleTapLock,
            onOpenSettings = onOpenSettings,
            modifier = modifier
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
        val sheetView = LocalView.current
        LaunchedEffect(hideSystemStatusBar) {
            val dialogWin = (sheetView.parent as? DialogWindowProvider)?.window
            val actWin = (sheetView.context as? android.app.Activity)?.window
            applySystemBarsToWindow(dialogWin, hideSystemStatusBar)
            applySystemBarsToWindow(actWin, hideSystemStatusBar)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (BuildConfig.IS_MAX_EDITION) {
                    "Fold8 Launcher Max Settings"
                } else {
                    "Launcher Settings"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.TextPrimary
            )
            Text(
                text = if (BuildConfig.IS_MAX_EDITION) {
                    "Experimental Max Feature Edition · VC Bridge & System-Wide Micro-HUD"
                } else {
                    "Minimal One UI Launcher · Main / Work / Dev Pages"
                },
                fontSize = 12.sp,
                color = FoldLauncherColors.TextSecondary
            )

            HorizontalDivider(color = FoldLauncherColors.GlassBorder)

            if (BuildConfig.IS_MAX_EDITION) {
                FloatingHudControlCard(
                    isAccessibilityServiceActive = isAccessibilityActive,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
                HorizontalDivider(color = FoldLauncherColors.GlassBorder)
            }

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
                        text = "Hide System Bars",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.TextPrimary
                    )
                    Text(
                        text = "Hides status & navigation bars across all launcher screens, sheets, and search",
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
                            text = if (hidden.isWorkProfile) "${hidden.label} (Work)" else hidden.label,
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
                                .clickable { onUnhideApp(hidden.uniqueKey) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
