package com.xsun.foldlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xsun.foldlauncher.hud.EXTRA_OPEN_SPOTLIGHT
import com.xsun.foldlauncher.posture.DevicePosture
import com.xsun.foldlauncher.posture.PostureOverride
import com.xsun.foldlauncher.posture.rememberPostureState
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.ui.animation.PostureAnimatedContainer
import com.xsun.foldlauncher.ui.components.AppContextMenuSheet
import com.xsun.foldlauncher.ui.components.SpotlightSearchOverlay
import com.xsun.foldlauncher.ui.screens.CoverOrSlabHomeScreen
import com.xsun.foldlauncher.ui.screens.FlexTabletopHomeScreen
import com.xsun.foldlauncher.ui.screens.LauncherSettingsSheet
import com.xsun.foldlauncher.ui.screens.UnfoldedInnerDashboardScreen
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.ui.theme.FoldLauncherTheme
import com.xsun.foldlauncher.viewmodel.LauncherViewModel
import com.xsun.foldlauncher.viewmodel.WorkspaceProfile

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            val isLockScreenCockpit =
                intent?.getBooleanExtra("com.xsun.foldlauncher.extra.LOCK_SCREEN_COCKPIT", false) == true ||
                    intent?.getBooleanExtra(SystemTweaker.EXTRA_LOCK_SCREEN_COCKPIT, false) == true
            if (isLockScreenCockpit) {
                setTurnScreenOn(true)
            }
        }

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
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
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
    val tailscaleIp by viewModel.tailscaleIp.collectAsState()
    val lanIp by viewModel.lanIp.collectAsState()

    val allApps by viewModel.allApps.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val pinnedCoverApps by viewModel.pinnedCoverApps.collectAsState()
    val pinnedInnerApps by viewModel.pinnedInnerApps.collectAsState()
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

    val rootBackgroundBrush = if (workspaceProfile == WorkspaceProfile.OLED_BLACK) {
        SolidColor(FoldLauncherColors.OledBlack)
    } else {
        Brush.verticalGradient(
            colors = listOf(
                FoldLauncherColors.DeepVoid.copy(alpha = 0.78f),
                FoldLauncherColors.OledBlack.copy(alpha = 0.92f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(rootBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
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
                        tailscaleIp = tailscaleIp,
                        lanIp = lanIp,
                        allApps = allApps,
                        pinnedCoverApps = pinnedCoverApps,
                        dockApps = dockApps,
                        recentApps = recentApps,
                        appPairs = appPairs,
                        folders = folders,
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
                        tailscaleIp = tailscaleIp,
                        lanIp = lanIp,
                        allApps = allApps,
                        favoriteApps = pinnedInnerApps,
                        dockApps = dockApps,
                        recentApps = recentApps,
                        appPairs = appPairs,
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
                        tailscaleIp = tailscaleIp,
                        lanIp = lanIp,
                        allApps = allApps,
                        pinnedInnerApps = pinnedInnerApps,
                        dockApps = dockApps,
                        recentApps = recentApps,
                        appPairs = appPairs,
                        folders = folders,
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

        // Instant 0ms Spotlight Search + Omni-Command Palette + Inline Math Overlay
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

        // Long-Press App Context Menu Sheet
        val contextApp = selectedContextMenuApp
        if (contextApp != null) {
            AppContextMenuSheet(
                app = contextApp,
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
