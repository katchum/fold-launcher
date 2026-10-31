package com.xsun.foldlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xsun.foldlauncher.hud.EXTRA_OPEN_SPOTLIGHT
import com.xsun.foldlauncher.posture.DevicePosture
import com.xsun.foldlauncher.posture.rememberPostureState
import com.xsun.foldlauncher.ui.animation.PostureAnimatedContainer
import com.xsun.foldlauncher.ui.components.AppContextMenuSheet
import com.xsun.foldlauncher.ui.components.SpotlightSearchOverlay
import com.xsun.foldlauncher.ui.screens.CoverOrSlabHomeScreen
import com.xsun.foldlauncher.ui.screens.FlexTabletopHomeScreen
import com.xsun.foldlauncher.ui.screens.LauncherSettingsSheet
import com.xsun.foldlauncher.ui.screens.OneUiMinimalAppSearchOverlay
import com.xsun.foldlauncher.ui.screens.UnfoldedInnerDashboardScreen
import com.xsun.foldlauncher.ui.theme.FoldLauncherTheme
import com.xsun.foldlauncher.viewmodel.LauncherViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // As a Home Launcher, Back closes active sheets/search and stays on the Home workspace
            viewModel.handleBackOrHomePress()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        requestPostNotificationsIfNeeded()
        handleIntent(intent)

        setContent {
            val hideStatusBar by viewModel.hideSystemStatusBar.collectAsState()

            LaunchedEffect(hideStatusBar) {
                applySystemBarVisibility(hideStatusBar)
            }

            FoldLauncherTheme {
                FoldLauncherRoot(
                    activity = this@MainActivity,
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SPOTLIGHT, false) == true ||
            intent?.getBooleanExtra("EXTRA_OPEN_SPOTLIGHT", false) == true
        ) {
            intent.removeExtra(EXTRA_OPEN_SPOTLIGHT)
            viewModel.openSearch()
        } else if (intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Pressing the Home button/gesture while already on the launcher dismisses open sheets/search
            viewModel.handleBackOrHomePress()
        }
        viewModel.refreshSystemCompanionState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onLauncherResumed()
        applySystemBarVisibility(viewModel.hideSystemStatusBar.value)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        applySystemBarVisibility(viewModel.hideSystemStatusBar.value)
        if (hasFocus) {
            viewModel.captureClipboardOnResume()
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }
    }

    private fun applySystemBarVisibility(hideStatusBar: Boolean) {
        applySystemBarsToWindow(window, hideStatusBar)
    }
}

fun applySystemBarsToWindow(targetWindow: Window?, hideSystemBars: Boolean) {
    val win = targetWindow ?: return
    val decor = win.decorView
    val controller = WindowCompat.getInsetsController(win, decor)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (hideSystemBars) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun FoldLauncherRoot(
    activity: MainActivity,
    viewModel: LauncherViewModel
) {
    val postureOverride by viewModel.postureOverride.collectAsState()
    val postureState = rememberPostureState(activity = activity, overrideMode = postureOverride)
    val workspaceProfile by viewModel.workspaceProfile.collectAsState()
    val isTorchEnabled by viewModel.isTorchEnabled.collectAsState()
    val isDualScreenPresentationActive by viewModel.isDualScreenPresentationActive.collectAsState()
    val isKeyguardLocked by viewModel.isKeyguardLocked.collectAsState()
    val pinnedDockPackages by viewModel.pinnedDockPackages.collectAsState()
    val hingeHapticsEnabled by viewModel.hingeHapticsEnabled.collectAsState()
    val foldCloseModeLabel by viewModel.foldCloseModeLabel.collectAsState()
    val unfoldModeLabel by viewModel.unfoldModeLabel.collectAsState()
    val isWirelessAdbEnabled by viewModel.isWirelessAdbEnabled.collectAsState()
    val wirelessAdbPort by viewModel.wirelessAdbPort.collectAsState()
    val tailscaleIp by viewModel.tailscaleIp.collectAsState()
    val lanIp by viewModel.lanIp.collectAsState()

    val allApps by viewModel.allApps.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val pinnedCoverApps by viewModel.pinnedCoverApps.collectAsState()
    val pinnedInnerApps by viewModel.pinnedInnerApps.collectAsState()
    val mainPageApps by viewModel.mainPageApps.collectAsState()
    val workPageApps by viewModel.workPageApps.collectAsState()
    val devPageApps by viewModel.devPageApps.collectAsState()
    val dockApps by viewModel.dockApps.collectAsState()
    val recentApps by viewModel.recentApps.collectAsState()
    val appPairs by viewModel.appPairs.collectAsState()
    val folders by viewModel.folders.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val rankedSearchApps by viewModel.rankedSearchApps.collectAsState()
    val inlineMathResult by viewModel.inlineMathResult.collectAsState()
    val clipboardItems by viewModel.clipboardItems.collectAsState()

    val isSearchOpen by viewModel.isSearchOpen.collectAsState()
    val selectedContextMenuApp by viewModel.selectedContextMenuApp.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()

    val hideSystemStatusBar by viewModel.hideSystemStatusBar.collectAsState()
    val iconShapePreset by viewModel.iconShapePreset.collectAsState()
    val quickNoteText by viewModel.quickNoteText.collectAsState()
    val nextEventText by viewModel.nextEventText.collectAsState()

    val batteryPct by viewModel.batteryPct.collectAsState()
    val isCharging by viewModel.isCharging.collectAsState()
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsState()
    val isAccessibilityActive by viewModel.isAccessibilityActive.collectAsState()
    val isWriteSecureSettingsGranted by viewModel.isWriteSecureSettingsGranted.collectAsState()
    val lockScreenRotationEnabled by viewModel.lockScreenRotationEnabled.collectAsState()
    val isDeXConnected by viewModel.isDeXConnected.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.16f))
    ) {
        PostureAnimatedContainer(
            postureState = postureState,
            modifier = Modifier.fillMaxSize()
        ) { currentPosture ->
            when (currentPosture.posture) {
                DevicePosture.COMPACT -> {
                    CoverOrSlabHomeScreen(
                        postureState = currentPosture,
                        postureOverride = postureOverride,
                        onCyclePostureOverride = viewModel::cyclePostureOverride,
                        onSetPostureOverride = viewModel::setPostureOverride,
                        workspaceProfile = workspaceProfile,
                        onSelectWorkspaceProfile = viewModel::setWorkspaceProfile,
                        onCycleWorkspaceProfile = viewModel::cycleWorkspaceProfile,
                        isTorchEnabled = isTorchEnabled,
                        onToggleTorch = viewModel::toggleTorch,
                        isDualScreenPresentationActive = isDualScreenPresentationActive,
                        onToggleDualScreenPresentation = viewModel::toggleDualScreenPresentation,
                        isKeyguardLocked = isKeyguardLocked,
                        onUnlockAndSplitVcTermux = { viewModel.unlockAndSplitVcTermux(activity) },
                        pinnedDockPackages = pinnedDockPackages,
                        onTogglePinDockPackage = viewModel::togglePinAppToDock,
                        hingeHapticsEnabled = hingeHapticsEnabled,
                        onToggleHingeHaptics = viewModel::toggleHingeHaptics,
                        foldCloseModeLabel = foldCloseModeLabel,
                        onCycleFoldCloseActionMode = viewModel::cycleFoldCloseActionMode,
                        unfoldModeLabel = unfoldModeLabel,
                        onCycleUnfoldActionMode = viewModel::cycleUnfoldActionMode,
                        isWirelessAdbEnabled = isWirelessAdbEnabled,
                        wirelessAdbPort = wirelessAdbPort,
                        onToggleWirelessAdb = viewModel::toggleWirelessAdb,
                        onOpenWirelessAdbSettings = viewModel::openWirelessAdbSettings,
                        onToggleTailscaleVpn = viewModel::toggleTailscaleVpn,
                        onOpenTailscaleApp = viewModel::openTailscaleApp,
                        tailscaleIp = tailscaleIp,
                        lanIp = lanIp,
                        allApps = allApps,
                        pinnedCoverApps = pinnedCoverApps,
                        dockApps = dockApps,
                        recentApps = recentApps,
                        appPairs = appPairs,
                        folders = folders,
                        mainPageApps = mainPageApps,
                        workPageApps = workPageApps,
                        devPageApps = devPageApps,
                        onTogglePageApp = viewModel::togglePageApp,
                        hideSystemStatusBar = hideSystemStatusBar,
                        iconShapePreset = iconShapePreset,
                        batteryPct = batteryPct,
                        isCharging = isCharging,
                        nextEventText = nextEventText,
                        isDeXConnected = isDeXConnected,
                        isDefaultLauncher = isDefaultLauncher,
                        isAccessibilityActive = isAccessibilityActive,
                        isWriteSecureSettingsGranted = isWriteSecureSettingsGranted,
                        lockScreenRotationEnabled = lockScreenRotationEnabled,
                        quickNoteText = quickNoteText,
                        onQuickNoteChange = viewModel::updateQuickNote,
                        onAppClick = { app -> viewModel.launchApp(app) },
                        onAppLongClick = viewModel::openContextMenu,
                        onLaunchAppPair = viewModel::launchAppPair,
                        onOpenSearch = viewModel::openSearch,
                        onExpandNotifications = viewModel::expandNotificationsPanel,
                        onDoubleTapLock = viewModel::lockScreenNow,
                        onOpenSettings = viewModel::openSettings,
                        onClockClick = viewModel::openClockApp,
                        onToggleLockRotation = viewModel::toggleLockScreenRotation,
                        onEnforceGboardDeX = viewModel::enforceGboardInDeX,
                        onLockScreenNow = viewModel::lockScreenNow,
                        onOpenWallpaperPicker = viewModel::openWallpaperPicker,
                        onOpenDefaultLauncherPrompt = {
                            viewModel.requestDefaultLauncherRole(activity)
                        }
                    )
                }

                DevicePosture.FLEX_TABLETOP -> {
                    FlexTabletopHomeScreen(
                        postureState = currentPosture,
                        postureOverride = postureOverride,
                        onCyclePostureOverride = viewModel::cyclePostureOverride,
                        onSetPostureOverride = viewModel::setPostureOverride,
                        workspaceProfile = workspaceProfile,
                        onSelectWorkspaceProfile = viewModel::setWorkspaceProfile,
                        onCycleWorkspaceProfile = viewModel::cycleWorkspaceProfile,
                        isTorchEnabled = isTorchEnabled,
                        onToggleTorch = viewModel::toggleTorch,
                        isDualScreenPresentationActive = isDualScreenPresentationActive,
                        onToggleDualScreenPresentation = viewModel::toggleDualScreenPresentation,
                        isKeyguardLocked = isKeyguardLocked,
                        onUnlockAndSplitVcTermux = { viewModel.unlockAndSplitVcTermux(activity) },
                        pinnedDockPackages = pinnedDockPackages,
                        onTogglePinDockPackage = viewModel::togglePinAppToDock,
                        hingeHapticsEnabled = hingeHapticsEnabled,
                        onToggleHingeHaptics = viewModel::toggleHingeHaptics,
                        foldCloseModeLabel = foldCloseModeLabel,
                        onCycleFoldCloseActionMode = viewModel::cycleFoldCloseActionMode,
                        unfoldModeLabel = unfoldModeLabel,
                        onCycleUnfoldActionMode = viewModel::cycleUnfoldActionMode,
                        isWirelessAdbEnabled = isWirelessAdbEnabled,
                        wirelessAdbPort = wirelessAdbPort,
                        onToggleWirelessAdb = viewModel::toggleWirelessAdb,
                        onOpenWirelessAdbSettings = viewModel::openWirelessAdbSettings,
                        onToggleTailscaleVpn = viewModel::toggleTailscaleVpn,
                        onOpenTailscaleApp = viewModel::openTailscaleApp,
                        tailscaleIp = tailscaleIp,
                        lanIp = lanIp,
                        allApps = allApps,
                        favoriteApps = pinnedInnerApps,
                        dockApps = dockApps,
                        recentApps = recentApps,
                        appPairs = appPairs,
                        mainPageApps = mainPageApps,
                        workPageApps = workPageApps,
                        devPageApps = devPageApps,
                        onTogglePageApp = viewModel::togglePageApp,
                        hideSystemStatusBar = hideSystemStatusBar,
                        iconShapePreset = iconShapePreset,
                        batteryPct = batteryPct,
                        isCharging = isCharging,
                        nextEventText = nextEventText,
                        isDeXConnected = isDeXConnected,
                        isDefaultLauncher = isDefaultLauncher,
                        isAccessibilityActive = isAccessibilityActive,
                        isWriteSecureSettingsGranted = isWriteSecureSettingsGranted,
                        lockScreenRotationEnabled = lockScreenRotationEnabled,
                        quickNoteText = quickNoteText,
                        onQuickNoteChange = viewModel::updateQuickNote,
                        onAppClick = { app -> viewModel.launchApp(app) },
                        onAppLongClick = viewModel::openContextMenu,
                        onLaunchAppPair = viewModel::launchAppPair,
                        onOpenSearch = viewModel::openSearch,
                        onExpandNotifications = viewModel::expandNotificationsPanel,
                        onDoubleTapLock = viewModel::lockScreenNow,
                        onOpenSettings = viewModel::openSettings,
                        onClockClick = viewModel::openClockApp,
                        onToggleLockRotation = viewModel::toggleLockScreenRotation,
                        onEnforceGboardDeX = viewModel::enforceGboardInDeX,
                        onLockScreenNow = viewModel::lockScreenNow,
                        onOpenWallpaperPicker = viewModel::openWallpaperPicker,
                        onOpenDefaultLauncherPrompt = {
                            viewModel.requestDefaultLauncherRole(activity)
                        }
                    )
                }

                DevicePosture.EXPANDED -> {
                    UnfoldedInnerDashboardScreen(
                        postureState = currentPosture,
                        postureOverride = postureOverride,
                        onCyclePostureOverride = viewModel::cyclePostureOverride,
                        onSetPostureOverride = viewModel::setPostureOverride,
                        workspaceProfile = workspaceProfile,
                        onSelectWorkspaceProfile = viewModel::setWorkspaceProfile,
                        onCycleWorkspaceProfile = viewModel::cycleWorkspaceProfile,
                        isTorchEnabled = isTorchEnabled,
                        onToggleTorch = viewModel::toggleTorch,
                        isDualScreenPresentationActive = isDualScreenPresentationActive,
                        onToggleDualScreenPresentation = viewModel::toggleDualScreenPresentation,
                        isKeyguardLocked = isKeyguardLocked,
                        onUnlockAndSplitVcTermux = { viewModel.unlockAndSplitVcTermux(activity) },
                        pinnedDockPackages = pinnedDockPackages,
                        onTogglePinDockPackage = viewModel::togglePinAppToDock,
                        hingeHapticsEnabled = hingeHapticsEnabled,
                        onToggleHingeHaptics = viewModel::toggleHingeHaptics,
                        foldCloseModeLabel = foldCloseModeLabel,
                        onCycleFoldCloseActionMode = viewModel::cycleFoldCloseActionMode,
                        unfoldModeLabel = unfoldModeLabel,
                        onCycleUnfoldActionMode = viewModel::cycleUnfoldActionMode,
                        isWirelessAdbEnabled = isWirelessAdbEnabled,
                        wirelessAdbPort = wirelessAdbPort,
                        onToggleWirelessAdb = viewModel::toggleWirelessAdb,
                        onOpenWirelessAdbSettings = viewModel::openWirelessAdbSettings,
                        onToggleTailscaleVpn = viewModel::toggleTailscaleVpn,
                        onOpenTailscaleApp = viewModel::openTailscaleApp,
                        tailscaleIp = tailscaleIp,
                        lanIp = lanIp,
                        allApps = allApps,
                        pinnedInnerApps = pinnedInnerApps,
                        dockApps = dockApps,
                        recentApps = recentApps,
                        appPairs = appPairs,
                        folders = folders,
                        mainPageApps = mainPageApps,
                        workPageApps = workPageApps,
                        devPageApps = devPageApps,
                        onTogglePageApp = viewModel::togglePageApp,
                        hideSystemStatusBar = hideSystemStatusBar,
                        iconShapePreset = iconShapePreset,
                        batteryPct = batteryPct,
                        isCharging = isCharging,
                        nextEventText = nextEventText,
                        isDeXConnected = isDeXConnected,
                        isDefaultLauncher = isDefaultLauncher,
                        isAccessibilityActive = isAccessibilityActive,
                        isWriteSecureSettingsGranted = isWriteSecureSettingsGranted,
                        lockScreenRotationEnabled = lockScreenRotationEnabled,
                        quickNoteText = quickNoteText,
                        onQuickNoteChange = viewModel::updateQuickNote,
                        onAppClick = { app -> viewModel.launchApp(app) },
                        onAppLongClick = viewModel::openContextMenu,
                        onLaunchAppPair = viewModel::launchAppPair,
                        onCreateSampleAppPair = viewModel::createQuickAppPairFromTopApps,
                        onOpenSearch = viewModel::openSearch,
                        onExpandNotifications = viewModel::expandNotificationsPanel,
                        onDoubleTapLock = viewModel::lockScreenNow,
                        onOpenSettings = viewModel::openSettings,
                        onClockClick = viewModel::openClockApp,
                        onToggleLockRotation = viewModel::toggleLockScreenRotation,
                        onEnforceGboardDeX = viewModel::enforceGboardInDeX,
                        onLockScreenNow = viewModel::lockScreenNow,
                        onOpenWallpaperPicker = viewModel::openWallpaperPicker,
                        onOpenDefaultLauncherPrompt = {
                            viewModel.requestDefaultLauncherRole(activity)
                        }
                    )
                }
            }
        }

        if (BuildConfig.IS_MAX_EDITION) {
            SpotlightSearchOverlay(
                visible = isSearchOpen,
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                rankedApps = rankedSearchApps,
                inlineMathResult = inlineMathResult,
                iconShapePreset = iconShapePreset,
                onLaunchApp = { app -> viewModel.launchApp(app) },
                onAppLongClick = viewModel::openContextMenu,
                onWebSearch = viewModel::launchWebSearch,
                onDismiss = viewModel::closeSearch,
                allApps = allApps,
                clipboardItems = clipboardItems,
                onExecuteOmniAction = viewModel::onOmniActionFromSpotlight,
                onToggleThumbArc = viewModel::toggleRadialThumbArc
            )
        } else {
            // v1.0.0 Bottom-Keyboard Search Overlay (Scrollable Recent App Thumbnails on top + 2-row Suggested Matches on bottom when typing)
            OneUiMinimalAppSearchOverlay(
                visible = isSearchOpen,
                hideSystemStatusBar = hideSystemStatusBar,
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                recentApps = recentApps,
                rankedApps = rankedSearchApps,
                iconShapePreset = iconShapePreset,
                columns = if (postureState.posture == DevicePosture.COMPACT) 4 else 6,
                onLaunchApp = { app -> viewModel.launchApp(app) },
                onAppLongClick = viewModel::openContextMenu,
                onDismiss = viewModel::closeSearch
            )
        }

        // Long-Press App Context Menu Sheet
        val contextApp = selectedContextMenuApp
        if (contextApp != null) {
            AppContextMenuSheet(
                app = contextApp,
                hideSystemStatusBar = hideSystemStatusBar,
                onDismiss = viewModel::dismissContextMenu,
                onLaunch = { app -> viewModel.launchApp(app) },
                onLaunchAdjacentSplit = viewModel::launchSplitScreenSingle,
                onTogglePinCover = viewModel::togglePinCover,
                onTogglePinInner = viewModel::togglePinInner,
                onTogglePinDock = viewModel::togglePinDock,
                onLaunchShortcut = viewModel::launchShortcut,
                onOpenAppInfo = viewModel::openAppInfo,
                onUninstallApp = viewModel::uninstallApp,
                onHideApp = viewModel::hideApp
            )
        }

        // Launcher Settings & Fold8 Companion Sheet
        LauncherSettingsSheet(
            visible = isSettingsOpen,
            hideSystemStatusBar = hideSystemStatusBar,
            onToggleHideSystemStatusBar = viewModel::setHideSystemStatusBar,
            iconShapePreset = iconShapePreset,
            onSelectIconShape = viewModel::setIconShapePreset,
            hiddenApps = hiddenApps,
            onUnhideApp = viewModel::unhideApp,
            isAccessibilityActive = isAccessibilityActive,
            isWriteSecureSettingsGranted = isWriteSecureSettingsGranted,
            onOpenAccessibilitySettings = viewModel::openAccessibilitySettings,
            onDismiss = viewModel::closeSettings
        )
    }
}
