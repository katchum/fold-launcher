package com.xsun.foldlauncher.viewmodel

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Rect
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xsun.foldlauncher.BuildConfig
import com.xsun.foldlauncher.data.AppRepository
import com.xsun.foldlauncher.data.LauncherPreferences
import com.xsun.foldlauncher.hud.FloatingHudController
import com.xsun.foldlauncher.hud.FloatingHudUiState
import com.xsun.foldlauncher.hud.VcHudAgentStatus
import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.model.AppPair
import com.xsun.foldlauncher.model.AppShortcutItem
import com.xsun.foldlauncher.model.FolderItem
import com.xsun.foldlauncher.omni.ClipboardItem
import com.xsun.foldlauncher.omni.ClipboardVault
import com.xsun.foldlauncher.omni.OmniAction
import com.xsun.foldlauncher.omni.OmniCommandEngine
import com.xsun.foldlauncher.posture.PostureOverride
import com.xsun.foldlauncher.posture.UnfoldActionMode
import com.xsun.foldlauncher.search.SearchEngine
import com.xsun.foldlauncher.service.FoldAccessibilityService
import com.xsun.foldlauncher.service.HardwareTelemetrySnapshot
import com.xsun.foldlauncher.service.NetworkTelemetrySnapshot
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.ui.theme.IconShapePreset
import com.xsun.foldlauncher.vc.VcBackgroundMonitor
import com.xsun.foldlauncher.vc.VcConnectionConfig
import com.xsun.foldlauncher.vc.VcLiveSummary
import com.xsun.foldlauncher.vc.VcPackages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Z Fold 8 & Slab Home Workspace Modes for v0.3.0:
 * - [ALL]: Full 120Hz companion deck, VC Command Center, system telemetry, and app matrix
 * - [VC_DEV]: Prioritizes VC Command Center, Split-Screen Power Pairs, and Terminal/Dev tools
 * - [FOCUS]: Distraction-free minimal clock + live VC status view (hides secondary widgets)
 * - [OLED_BLACK]: Pure `#000000` zero-power AMOLED black canvas for maximum Z Fold 8 battery savings
 */
enum class WorkspaceProfile(val label: String, val badge: String) {
    ALL("All Panels", "🌐"),
    VC_DEV("VC & Dev", "⚡"),
    FOCUS("Minimal Focus", "🎯"),
    OLED_BLACK("Pure OLED", "🌑")
}

/**
 * Consolidated v0.6.0 UI state snapshot for workspace profile, posture override,
 * custom dock pinning, hinge detent haptics, fold-close & unfold-open action modes,
 * Wireless ADB & Tailscale/LAN network telemetry, hardware torch, dual-screen presentation, and lock-screen keyguard state.
 */
data class LauncherUiState(
    val workspaceProfile: WorkspaceProfile = WorkspaceProfile.ALL,
    val postureOverride: PostureOverride = PostureOverride.AUTO,
    val pinnedDockPackages: Set<String> = emptySet(),
    val mainPagePackages: Set<String> = emptySet(),
    val workPagePackages: Set<String> = emptySet(),
    val devPagePackages: Set<String> = emptySet(),
    val hingeHapticsEnabled: Boolean = true,
    val foldCloseModeLabel: String = "📱 Keep Cover Awake",
    val unfoldModeLabel: String = "🖥️ Inner Cockpit",
    val isWirelessAdbEnabled: Boolean = false,
    val wirelessAdbPort: Int? = null,
    val tailscaleIp: String? = null,
    val lanIp: String? = null,
    val networkTelemetry: NetworkTelemetrySnapshot = NetworkTelemetrySnapshot(),
    val isTorchEnabled: Boolean = false,
    val isDualScreenPresentationActive: Boolean = false,
    val isKeyguardLocked: Boolean = false,
    val hardwareTelemetry: HardwareTelemetrySnapshot = HardwareTelemetrySnapshot(),
    val isSearchOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val batteryPct: Int = 100,
    val isCharging: Boolean = false,
    val isDeXConnected: Boolean = false,
    val isAccessibilityActive: Boolean = false,
    val isWriteSecureSettingsGranted: Boolean = false,
    val lockScreenRotationEnabled: Boolean = false
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "fold_launcher_prefs"
        private const val PREFS_SCRATCHPAD = "fold_scratchpad_prefs"
        private const val KEY_SCRATCHPAD_TEXT = "scratchpad_text"
        private const val KEY_PINNED_DOCK = "pinned_dock_packages"
        private const val KEY_MAIN_PAGE_PACKAGES = "main_page_packages"
        private const val KEY_WORK_PAGE_PACKAGES = "work_page_packages"
        private const val KEY_DEV_PAGE_PACKAGES = "dev_page_packages"
        private const val KEY_HINGE_HAPTICS = "hinge_haptics_enabled"
        private const val KEY_FOLD_CLOSE_MODE = "fold_close_action_mode"
        private const val KEY_UNFOLD_MODE = "unfold_action_mode"

        val DEFAULT_MAIN_PAGE_PACKAGES: Set<String> = linkedSetOf(
            "com.samsung.android.dialer",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.chrome",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.camera",
            "com.android.camera2",
            "com.google.android.apps.maps",
            "com.google.android.youtube",
            "com.google.android.apps.photos",
            "com.sec.android.gallery3d",
            "com.sec.android.app.clockpackage",
            "com.google.android.deskclock",
            "com.android.vending"
        )

        val DEFAULT_WORK_PAGE_PACKAGES: Set<String> = linkedSetOf(
            "com.google.android.gm",
            "com.samsung.android.email.provider",
            "com.microsoft.office.outlook",
            "com.google.android.calendar",
            "com.samsung.android.calendar",
            "com.samsung.android.app.notes",
            "com.google.android.keep",
            "com.google.android.apps.docs",
            "com.sec.android.app.myfiles",
            "com.google.android.apps.nbu.files",
            "com.android.documentsui",
            "com.samsung.android.app.contacts",
            "com.google.android.contacts",
            "com.Slack",
            "com.microsoft.teams",
            "com.google.android.apps.tachyon",
            "us.zoom.videomeetings"
        )

        val DEFAULT_DEV_PAGE_PACKAGES: Set<String> = linkedSetOf(
            "dev.agentconversation.vci",
            "dev.agentconversation.preview",
            "dev.agentconversation",
            "com.termux",
            "com.tailscale.ipn",
            "com.github.android",
            "com.android.settings",
            "com.android.chrome",
            "com.google.android.inputmethod.latin"
        )

        val FOLD_CLOSE_MODES: List<String> = listOf(
            "📱 Keep Cover Awake",
            "🎙️ Auto-Talk to VC",
            "⛺ OLED Standby",
            "🔒 Instant Lock"
        )

        val UNFOLD_OPEN_MODES: List<String> = listOf(
            "🖥️ Inner Cockpit",
            "⚡ Auto-Split VC + Termux",
            "🔀 Auto-Split Prod + Compare",
            "🌐 Auto-Split VC + Browser"
        )
    }

    private val appContext: Context = application.applicationContext
    private val rawPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scratchpadPrefs = appContext.getSharedPreferences(PREFS_SCRATCHPAD, Context.MODE_PRIVATE)
    val preferences = LauncherPreferences(appContext)
    val repository = AppRepository(appContext, preferences)
    val clipboardVault = ClipboardVault(appContext)
    val vcMonitor = VcBackgroundMonitor.getInstance(appContext)

    private val _allInstalledApps = MutableStateFlow<List<AppEntry>>(emptyList())

    private val _hiddenPackages = MutableStateFlow(preferences.hiddenPackages)
    val hiddenPackages: StateFlow<Set<String>> = _hiddenPackages.asStateFlow()

    // v0.4.0 Interactive Custom Dock Pinning State (`"pinned_dock_packages"`)
    private val _pinnedDockPackages = MutableStateFlow(preferences.pinnedDockPackages)
    val pinnedDockPackages: StateFlow<Set<String>> = _pinnedDockPackages.asStateFlow()

    // v0.8.0 Separate 3-Page App Sets (`"main_page_packages"`, `"work_page_packages"`, `"dev_page_packages"`)
    private val _mainPagePackages = MutableStateFlow(
        rawPrefs.getStringSet(KEY_MAIN_PAGE_PACKAGES, null)?.toSet() ?: DEFAULT_MAIN_PAGE_PACKAGES
    )
    val mainPagePackages: StateFlow<Set<String>> = _mainPagePackages.asStateFlow()

    private val _workPagePackages = MutableStateFlow(
        rawPrefs.getStringSet(KEY_WORK_PAGE_PACKAGES, null)?.toSet() ?: DEFAULT_WORK_PAGE_PACKAGES
    )
    val workPagePackages: StateFlow<Set<String>> = _workPagePackages.asStateFlow()

    private val _devPagePackages = MutableStateFlow(
        rawPrefs.getStringSet(KEY_DEV_PAGE_PACKAGES, null)?.toSet() ?: DEFAULT_DEV_PAGE_PACKAGES
    )
    val devPagePackages: StateFlow<Set<String>> = _devPagePackages.asStateFlow()

    val allApps: StateFlow<List<AppEntry>> = combine(
        _allInstalledApps,
        _hiddenPackages
    ) { installed, hidden ->
        installed.filter { it.uniqueKey !in hidden && it.packageName !in hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val mainPageApps: StateFlow<List<AppEntry>> = combine(
        allApps,
        _mainPagePackages
    ) { apps, pkgs ->
        val matched = apps.filter { app ->
            app.uniqueKey in pkgs || (!app.isWorkProfile && app.packageName in pkgs)
        }
        if (matched.isNotEmpty() || rawPrefs.contains(KEY_MAIN_PAGE_PACKAGES)) {
            matched
        } else {
            apps.filter {
                !it.isWorkProfile &&
                    it.packageName !in DEFAULT_WORK_PAGE_PACKAGES &&
                    it.packageName !in DEFAULT_DEV_PAGE_PACKAGES
            }.ifEmpty { apps.filter { !it.isWorkProfile } }.take(8)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val workPageApps: StateFlow<List<AppEntry>> = combine(
        allApps,
        _workPagePackages,
        _mainPagePackages
    ) { apps, workPkgs, mainPkgs ->
        val workProfileApps = apps.filter { it.isWorkProfile }
        val isCustomizedV2 = rawPrefs.getBoolean("work_page_customized_v2", false)

        if (!isCustomizedV2 && workProfileApps.isNotEmpty()) {
            // Dynamically populate Work tab with all launchable Work Profile (profile 10) apps by default
            workProfileApps
        } else if (isCustomizedV2) {
            apps.filter { app ->
                app.uniqueKey in workPkgs || (!app.isWorkProfile && app.packageName in workPkgs)
            }
        } else {
            // Device has no Work Profile: gracefully fall back to configured workPagePackages
            val matched = apps.filter { app ->
                app.uniqueKey in workPkgs || app.packageName in workPkgs
            }
            if (matched.isNotEmpty() || rawPrefs.contains(KEY_WORK_PAGE_PACKAGES)) {
                matched
            } else {
                apps.filterNot { it.packageName in mainPkgs || it.packageName in DEFAULT_DEV_PAGE_PACKAGES }
                    .ifEmpty { apps.drop(4) }
                    .take(8)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val devPageApps: StateFlow<List<AppEntry>> = combine(
        allApps,
        _devPagePackages
    ) { apps, devPkgs ->
        val matched = apps.filter { app ->
            app.uniqueKey in devPkgs || (!app.isWorkProfile && app.packageName in devPkgs)
        }
        if (matched.isNotEmpty() || rawPrefs.contains(KEY_DEV_PAGE_PACKAGES)) {
            matched
        } else {
            apps.filter { app ->
                !app.isWorkProfile && run {
                    val pkg = app.packageName.lowercase()
                    pkg.startsWith("dev.agentconversation") ||
                        pkg.startsWith("com.termux") ||
                        pkg.contains("tailscale") ||
                        pkg.contains("github") ||
                        pkg == "com.android.settings" ||
                        pkg.contains("chrome")
                }
            }.ifEmpty { apps.filter { !it.isWorkProfile }.takeLast(6) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenApps: StateFlow<List<AppEntry>> = combine(
        _allInstalledApps,
        _hiddenPackages
    ) { installed, hidden ->
        installed.filter { it.uniqueKey in hidden || it.packageName in hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allAppsMap: StateFlow<Map<String, AppEntry>> = _allInstalledApps
        .map { list -> list.associateBy { it.packageName } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val pinnedCoverApps: StateFlow<List<AppEntry>> = allApps
        .map { apps ->
            val explicit = apps.filter { it.isPinnedCover }
            if (explicit.isNotEmpty()) explicit else apps.take(8)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pinnedInnerApps: StateFlow<List<AppEntry>> = allApps
        .map { apps ->
            val explicit = apps.filter { it.isPinnedInner }
            if (explicit.isNotEmpty()) explicit else apps.take(12)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dockApps: StateFlow<List<AppEntry>> = combine(
        allApps,
        _pinnedDockPackages
    ) { apps, pinnedPkgs ->
        val explicit = apps.filter { it.isDockPinned || it.packageName in pinnedPkgs }
        if (explicit.isNotEmpty()) explicit.take(7) else apps.take(5)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _recentSessionPackages = MutableStateFlow<List<String>>(emptyList())

    val recentApps: StateFlow<List<AppEntry>> = combine(
        allApps,
        _recentSessionPackages
    ) { apps, sessionOrder ->
        val byPkg = apps.associateBy { it.packageName }
        val fromSession = sessionOrder.mapNotNull { byPkg[it] }
        val fromCounts = apps
            .filter { it.launchCount > 0 && it.packageName !in sessionOrder }
            .sortedByDescending { it.launchCount }
        (fromSession + fromCounts).take(8)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _folders = MutableStateFlow(preferences.folders)
    val folders: StateFlow<List<FolderItem>> = _folders.asStateFlow()

    private val _appPairs = MutableStateFlow(preferences.appPairs)
    val appPairs: StateFlow<List<AppPair>> = _appPairs.asStateFlow()

    // Search, Inline Math, Omni-Command Engine & Clipboard Vault
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val rankedSearchApps: StateFlow<List<AppEntry>> = combine(
        allApps,
        _searchQuery
    ) { apps, query ->
        SearchEngine.rankApps(apps, query, limit = 30)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val inlineMathResult: StateFlow<String?> = _searchQuery
        .map { SearchEngine.evaluateInlineMath(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val clipboardItems: StateFlow<List<ClipboardItem>> = clipboardVault.items

    val omniActions: StateFlow<List<OmniAction>> = combine(
        _searchQuery,
        allApps,
        clipboardItems
    ) { query, apps, clips ->
        OmniCommandEngine.evaluate(
            rawQuery = query,
            installedApps = apps,
            clipboardItems = clips
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val primaryOmniAction: StateFlow<OmniAction?> = omniActions
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _omniStatusMessage = MutableStateFlow<String?>(null)
    val omniStatusMessage: StateFlow<String?> = _omniStatusMessage.asStateFlow()

    // System-Wide Floating Micro-HUD Pill state
    val floatingHudState: StateFlow<FloatingHudUiState> = FloatingHudController.state

    val floatingHudEnabled: StateFlow<Boolean> = FloatingHudController.state
        .map { it.isEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferences.floatingHudEnabled)

    // Spatial Depth Parallax & One-Handed Radial Thumb Arc preferences
    private val _radialArcEnabled = MutableStateFlow(preferences.radialArcEnabled)
    val radialArcEnabled: StateFlow<Boolean> = _radialArcEnabled.asStateFlow()

    private val _radialArcRightHanded = MutableStateFlow(preferences.radialArcRightHanded)
    val radialArcRightHanded: StateFlow<Boolean> = _radialArcRightHanded.asStateFlow()

    private val _spatialParallaxEnabled = MutableStateFlow(preferences.spatialParallaxEnabled)
    val spatialParallaxEnabled: StateFlow<Boolean> = _spatialParallaxEnabled.asStateFlow()

    // Z Fold 8 Live Posture Simulator & Manual Override state
    private val _postureOverride = MutableStateFlow(PostureOverride.AUTO)
    val postureOverride: StateFlow<PostureOverride> = _postureOverride.asStateFlow()

    // v0.3.0 Workspace Profile state (ALL, VC_DEV, FOCUS, OLED_BLACK)
    private val _workspaceProfile = MutableStateFlow(WorkspaceProfile.ALL)
    val workspaceProfile: StateFlow<WorkspaceProfile> = _workspaceProfile.asStateFlow()

    // v0.4.0 Hinge Detent Haptics & Fold-Close Action Mode state (persisted in SharedPreferences)
    private val _hingeHapticsEnabled = MutableStateFlow(rawPrefs.getBoolean(KEY_HINGE_HAPTICS, true))
    val hingeHapticsEnabled: StateFlow<Boolean> = _hingeHapticsEnabled.asStateFlow()

    private val _foldCloseModeLabel = MutableStateFlow(
        rawPrefs.getString(KEY_FOLD_CLOSE_MODE, FOLD_CLOSE_MODES.first()) ?: FOLD_CLOSE_MODES.first()
    )
    val foldCloseModeLabel: StateFlow<String> = _foldCloseModeLabel.asStateFlow()

    // v0.5.0 / v0.6.0 Unfold-Open Action Mode (`"unfold_action_mode"`), Wireless ADB & Live Tailscale / LAN Network Telemetry
    private val _unfoldModeLabel = MutableStateFlow(
        rawPrefs.getString(KEY_UNFOLD_MODE, UNFOLD_OPEN_MODES.first()) ?: UNFOLD_OPEN_MODES.first()
    )
    val unfoldModeLabel: StateFlow<String> = _unfoldModeLabel.asStateFlow()

    private val _isWirelessAdbEnabled = MutableStateFlow(false)
    val isWirelessAdbEnabled: StateFlow<Boolean> = _isWirelessAdbEnabled.asStateFlow()

    private val _wirelessAdbPort = MutableStateFlow<Int?>(null)
    val wirelessAdbPort: StateFlow<Int?> = _wirelessAdbPort.asStateFlow()

    private val _tailscaleIp = MutableStateFlow<String?>(null)
    val tailscaleIp: StateFlow<String?> = _tailscaleIp.asStateFlow()

    private val _lanIp = MutableStateFlow<String?>(null)
    val lanIp: StateFlow<String?> = _lanIp.asStateFlow()

    private val _networkTelemetry = MutableStateFlow(NetworkTelemetrySnapshot())
    val networkTelemetry: StateFlow<NetworkTelemetrySnapshot> = _networkTelemetry.asStateFlow()

    // v0.3.0 Hardware Torch, Dual-Screen Cover Presentation & Lock-Screen Keyguard Telemetry
    private val _isTorchEnabled = MutableStateFlow(SystemTweaker.isTorchEnabled())
    val isTorchEnabled: StateFlow<Boolean> = _isTorchEnabled.asStateFlow()

    private val _isDualScreenPresentationActive = MutableStateFlow(false)
    val isDualScreenPresentationActive: StateFlow<Boolean> = _isDualScreenPresentationActive.asStateFlow()

    private val _isKeyguardLocked = MutableStateFlow(false)
    val isKeyguardLocked: StateFlow<Boolean> = _isKeyguardLocked.asStateFlow()

    private val _hardwareTelemetry = MutableStateFlow(HardwareTelemetrySnapshot())
    val hardwareTelemetry: StateFlow<HardwareTelemetrySnapshot> = _hardwareTelemetry.asStateFlow()

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    // VC Background Monitor & Command Center states
    val vcConfig: StateFlow<VcConnectionConfig> = vcMonitor.config
    val vcLiveSummary: StateFlow<VcLiveSummary> = vcMonitor.liveSummary
    val vcLastSendFeedback: StateFlow<String?> = vcMonitor.lastSendFeedback

    // Overlay / Sheet UI states
    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _selectedContextMenuApp = MutableStateFlow<AppEntry?>(null)
    val selectedContextMenuApp: StateFlow<AppEntry?> = _selectedContextMenuApp.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    // User preferences states (v0.6.0 Option A: keep native top status bar visible by default)
    private val _hideSystemStatusBar = MutableStateFlow(false)
    val hideSystemStatusBar: StateFlow<Boolean> = _hideSystemStatusBar.asStateFlow()

    private val _iconShapePreset = MutableStateFlow(resolveIconShapePreset(preferences.iconShapePresetName))
    val iconShapePreset: StateFlow<IconShapePreset> = _iconShapePreset.asStateFlow()

    private val _quickNoteText = MutableStateFlow(preferences.quickNoteText)
    val quickNoteText: StateFlow<String> = _quickNoteText.asStateFlow()

    private val _nextEventText = MutableStateFlow(preferences.nextEventText)
    val nextEventText: StateFlow<String> = _nextEventText.asStateFlow()

    // Battery & System Companion states
    private val _batteryPct = MutableStateFlow(100)
    val batteryPct: StateFlow<Int> = _batteryPct.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _isDefaultLauncher = MutableStateFlow(false)
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()

    private val _isAccessibilityActive = MutableStateFlow(false)
    val isAccessibilityActive: StateFlow<Boolean> = _isAccessibilityActive.asStateFlow()

    private val _isWriteSecureSettingsGranted = MutableStateFlow(false)
    val isWriteSecureSettingsGranted: StateFlow<Boolean> = _isWriteSecureSettingsGranted.asStateFlow()

    private val _lockScreenRotationEnabled = MutableStateFlow(false)
    val lockScreenRotationEnabled: StateFlow<Boolean> = _lockScreenRotationEnabled.asStateFlow()

    private val _isDeXConnected = MutableStateFlow(false)
    val isDeXConnected: StateFlow<Boolean> = _isDeXConnected.asStateFlow()

    val adbGrantCommand: String
        get() = SystemTweaker.getAdbGrantCommand(appContext)

    private var batteryReceiverRegistered = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                _batteryPct.value = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            }
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }
    }

    private var wirelessAdbObserverRegistered = false
    private val wirelessAdbContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            refreshWirelessAdbState()
            syncUiStateSnapshot()
        }
    }

    init {
        if (BuildConfig.IS_MAX_EDITION) {
            FloatingHudController.ensureInitialized(appContext)
        }
        registerBatteryMonitor()
        registerWirelessAdbObserver()
        repository.startObservingPackages {
            refreshApps()
        }
        observeForegroundAndVcTelemetry()
        refreshAll()
    }

    private fun observeForegroundAndVcTelemetry() {
        // Bridge FoldAccessibilityService foreground package detection to VcBackgroundMonitor
        viewModelScope.launch {
            FoldAccessibilityService.foregroundPackage.collect { pkg ->
                if (pkg.isNotBlank()) {
                    vcMonitor.onForegroundPackageChanged(pkg)
                }
            }
        }

        if (BuildConfig.IS_MAX_EDITION) {
            // Sync live VC summary status with the System-Wide Floating Micro-HUD Pill dot in Max Edition
            viewModelScope.launch {
                vcMonitor.liveSummary.collect { summary ->
                    val hudDotStatus = when {
                        summary.approvalCount > 0 -> VcHudAgentStatus.APPROVAL_NEEDED
                        summary.failedCount > 0 -> VcHudAgentStatus.ERROR
                        summary.workingCount > 0 || summary.runningCount > 0 -> VcHudAgentStatus.WORKING
                        else -> VcHudAgentStatus.IDLE_READY
                    }
                    FloatingHudController.setVcAgentStatus(appContext, hudDotStatus)
                }
            }
        }

        // Sync live hardware torch state from SystemTweaker
        viewModelScope.launch {
            SystemTweaker.isTorchActive.collect { active ->
                _isTorchEnabled.value = active
                syncUiStateSnapshot()
            }
        }
    }

    private fun syncUiStateSnapshot() {
        _uiState.value = LauncherUiState(
            workspaceProfile = _workspaceProfile.value,
            postureOverride = _postureOverride.value,
            pinnedDockPackages = _pinnedDockPackages.value,
            mainPagePackages = _mainPagePackages.value,
            workPagePackages = _workPagePackages.value,
            devPagePackages = _devPagePackages.value,
            hingeHapticsEnabled = _hingeHapticsEnabled.value,
            foldCloseModeLabel = _foldCloseModeLabel.value,
            unfoldModeLabel = _unfoldModeLabel.value,
            isWirelessAdbEnabled = _isWirelessAdbEnabled.value,
            wirelessAdbPort = _wirelessAdbPort.value,
            tailscaleIp = _tailscaleIp.value,
            lanIp = _lanIp.value,
            networkTelemetry = _networkTelemetry.value,
            isTorchEnabled = _isTorchEnabled.value,
            isDualScreenPresentationActive = _isDualScreenPresentationActive.value,
            isKeyguardLocked = _isKeyguardLocked.value,
            hardwareTelemetry = _hardwareTelemetry.value,
            isSearchOpen = _isSearchOpen.value,
            isSettingsOpen = _isSettingsOpen.value,
            batteryPct = _batteryPct.value,
            isCharging = _isCharging.value,
            isDeXConnected = _isDeXConnected.value,
            isAccessibilityActive = _isAccessibilityActive.value,
            isWriteSecureSettingsGranted = _isWriteSecureSettingsGranted.value,
            lockScreenRotationEnabled = _lockScreenRotationEnabled.value
        )
    }

    /**
     * v0.9.0 Multi-page (`0 = Main`, `1 = Work`, `2 = Development`) app membership toggle.
     * Supports both `app.uniqueKey` (distinguishing Work Profile `pkg#work_10` from Personal `pkg`) and `app.packageName`.
     */
    fun togglePageApp(pageIndex: Int, packageName: String) {
        if (packageName.isBlank()) return
        val plainPkg = packageName.substringBefore("#work_")
        when (pageIndex) {
            0 -> {
                val base = if (rawPrefs.contains(KEY_MAIN_PAGE_PACKAGES)) {
                    _mainPagePackages.value.toMutableSet()
                } else {
                    mainPageApps.value.map { it.uniqueKey }.toMutableSet()
                }
                if (packageName in base) {
                    base.remove(packageName)
                } else if (!packageName.contains("#work_") && plainPkg in base) {
                    base.remove(plainPkg)
                } else {
                    base.add(packageName)
                }
                val updated = base.toSet()
                _mainPagePackages.value = updated
                rawPrefs.edit().putStringSet(KEY_MAIN_PAGE_PACKAGES, updated).apply()
            }
            1 -> {
                val isCustomizedV2 = rawPrefs.getBoolean("work_page_customized_v2", false)
                val base = if (isCustomizedV2 && rawPrefs.contains(KEY_WORK_PAGE_PACKAGES)) {
                    _workPagePackages.value.toMutableSet()
                } else {
                    workPageApps.value.map { it.uniqueKey }.toMutableSet()
                }
                if (packageName in base) {
                    base.remove(packageName)
                } else if (!packageName.contains("#work_") && plainPkg in base) {
                    base.remove(plainPkg)
                } else {
                    base.add(packageName)
                }
                val updated = base.toSet()
                _workPagePackages.value = updated
                rawPrefs.edit()
                    .putStringSet(KEY_WORK_PAGE_PACKAGES, updated)
                    .putBoolean("work_page_customized_v2", true)
                    .apply()
            }
            else -> {
                val base = if (rawPrefs.contains(KEY_DEV_PAGE_PACKAGES)) {
                    _devPagePackages.value.toMutableSet()
                } else {
                    devPageApps.value.map { it.uniqueKey }.toMutableSet()
                }
                if (packageName in base) {
                    base.remove(packageName)
                } else if (!packageName.contains("#work_") && plainPkg in base) {
                    base.remove(plainPkg)
                } else {
                    base.add(packageName)
                }
                val updated = base.toSet()
                _devPagePackages.value = updated
                rawPrefs.edit().putStringSet(KEY_DEV_PAGE_PACKAGES, updated).apply()
            }
        }
        syncUiStateSnapshot()
    }

    fun setPageApps(pageIndex: Int, packages: Set<String>) {
        val updated = packages.toSet()
        when (pageIndex) {
            0 -> {
                _mainPagePackages.value = updated
                rawPrefs.edit().putStringSet(KEY_MAIN_PAGE_PACKAGES, updated).apply()
            }
            1 -> {
                _workPagePackages.value = updated
                rawPrefs.edit()
                    .putStringSet(KEY_WORK_PAGE_PACKAGES, updated)
                    .putBoolean("work_page_customized_v2", true)
                    .apply()
            }
            else -> {
                _devPagePackages.value = updated
                rawPrefs.edit().putStringSet(KEY_DEV_PAGE_PACKAGES, updated).apply()
            }
        }
        syncUiStateSnapshot()
    }

    fun refreshAll() {
        refreshSystemCompanionState()
        refreshApps()
    }

    fun refreshSystemCompanionState() {
        SystemTweaker.loadHingePreferences(appContext)
        SystemTweaker.ensureDeXGboardPreferred(appContext)
        _isDefaultLauncher.value = repository.isDefaultLauncher()
        val a11yEnabled = SystemTweaker.isAccessibilityServiceEnabled(appContext)
        _isAccessibilityActive.value = a11yEnabled
        _isWriteSecureSettingsGranted.value = SystemTweaker.hasWriteSecureSettings(appContext)
        _lockScreenRotationEnabled.value = SystemTweaker.isLockScreenRotationEnabled(appContext)
        _isDeXConnected.value = SystemTweaker.isDexDesktopModeActive(appContext)
        val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        _isKeyguardLocked.value = km?.isKeyguardLocked == true
        _isTorchEnabled.value = SystemTweaker.isTorchEnabled()
        _hardwareTelemetry.value = SystemTweaker.readHardwareTelemetry(appContext)
        val netSnapshot = SystemTweaker.readNetworkTelemetry(appContext)
        _networkTelemetry.value = netSnapshot
        _tailscaleIp.value = netSnapshot.tailscaleIp
        _lanIp.value = netSnapshot.lanIp
        refreshWirelessAdbState()
        val scratchpadSaved = scratchpadPrefs.getString(KEY_SCRATCHPAD_TEXT, null)
        if (!scratchpadSaved.isNullOrBlank() && _quickNoteText.value != scratchpadSaved) {
            _quickNoteText.value = scratchpadSaved
            preferences.quickNoteText = scratchpadSaved
        }
        if (BuildConfig.IS_MAX_EDITION && a11yEnabled) {
            FloatingHudController.syncOverlayWithService()
        }
        syncUiStateSnapshot()
    }

    fun onLauncherResumed() {
        FoldAccessibilityService.notifyLauncherResumed(appContext.packageName)
        vcMonitor.onLauncherResumed()
        captureClipboardOnResume()
        refreshSystemCompanionState()
    }

    fun refreshApps() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                repository.loadInstalledApps(includeHidden = true)
            }
            _allInstalledApps.value = loaded
            _hiddenPackages.value = preferences.hiddenPackages
            _pinnedDockPackages.value = preferences.pinnedDockPackages
            syncUiStateSnapshot()
        }
    }

    private fun syncAppFlagsInMemory() {
        val pinnedCover = preferences.pinnedCoverPackages
        val pinnedInner = preferences.pinnedInnerPackages
        val pinnedDock = preferences.pinnedDockPackages
        val counts = preferences.launchCounts
        _hiddenPackages.value = preferences.hiddenPackages
        _pinnedDockPackages.value = pinnedDock

        _allInstalledApps.value = _allInstalledApps.value.map { entry ->
            entry.copy(
                launchCount = counts[entry.packageName] ?: entry.launchCount,
                isPinnedCover = entry.packageName in pinnedCover,
                isPinnedInner = entry.packageName in pinnedInner,
                isDockPinned = entry.packageName in pinnedDock
            )
        }
        syncUiStateSnapshot()
    }

    // Search & Sheet Controls
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun openSearch() {
        _isSearchOpen.value = true
    }

    fun closeSearch() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
    }

    fun setSearchOpen(open: Boolean) {
        if (open) openSearch() else closeSearch()
    }

    fun openSettings() {
        refreshSystemCompanionState()
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun setSettingsOpen(open: Boolean) {
        if (open) openSettings() else closeSettings()
    }

    fun openContextMenu(app: AppEntry) {
        _selectedContextMenuApp.value = app
    }

    fun dismissContextMenu() {
        _selectedContextMenuApp.value = null
    }

    /**
     * Handles system Back key or Home gesture while on the launcher.
     * Closes any active sheet/overlay and returns `true` if an overlay was dismissed.
     */
    fun handleBackOrHomePress(): Boolean {
        if (_selectedContextMenuApp.value != null) {
            _selectedContextMenuApp.value = null
            return true
        }
        if (_isSettingsOpen.value) {
            _isSettingsOpen.value = false
            return true
        }
        if (_isSearchOpen.value) {
            closeSearch()
            return true
        }
        return false
    }

    // Live Posture Override Controls (AUTO -> FORCE_FLEX -> FORCE_INNER -> FORCE_COVER -> TENT_MODE -> AUTO)
    fun setPostureOverride(mode: PostureOverride) {
        _postureOverride.value = mode
        syncUiStateSnapshot()
        val feedback = "Posture: ${mode.displayName} (${mode.shortLabel})"
        _omniStatusMessage.value = feedback
        try {
            Toast.makeText(appContext, feedback, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun cyclePostureOverride() {
        val next = when (_postureOverride.value) {
            PostureOverride.AUTO -> PostureOverride.FORCE_FLEX
            PostureOverride.FORCE_FLEX -> PostureOverride.FORCE_INNER
            PostureOverride.FORCE_INNER -> PostureOverride.FORCE_COVER
            PostureOverride.FORCE_COVER -> PostureOverride.TENT_MODE
            PostureOverride.TENT_MODE -> PostureOverride.AUTO
        }
        setPostureOverride(next)
    }

    // v0.3.0 Workspace Profile Controls (ALL -> VC_DEV -> FOCUS -> OLED_BLACK -> ALL)
    fun setWorkspaceProfile(profile: WorkspaceProfile) {
        _workspaceProfile.value = profile
        syncUiStateSnapshot()
        val feedback = "${profile.badge} Workspace: ${profile.label}"
        _omniStatusMessage.value = feedback
        try {
            Toast.makeText(appContext, feedback, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun cycleWorkspaceProfile() {
        val next = when (_workspaceProfile.value) {
            WorkspaceProfile.ALL -> WorkspaceProfile.VC_DEV
            WorkspaceProfile.VC_DEV -> WorkspaceProfile.FOCUS
            WorkspaceProfile.FOCUS -> WorkspaceProfile.OLED_BLACK
            WorkspaceProfile.OLED_BLACK -> WorkspaceProfile.ALL
        }
        setWorkspaceProfile(next)
    }

    // v0.4.0 Hinge Detent Haptics & Fold-Close Action Mode Controls
    fun setHingeHapticsEnabled(enabled: Boolean) {
        _hingeHapticsEnabled.value = enabled
        rawPrefs.edit().putBoolean(KEY_HINGE_HAPTICS, enabled).apply()
        syncUiStateSnapshot()
        val feedback = if (enabled) "📳 Hinge Detent Haptics: ON" else "📳 Hinge Detent Haptics: OFF"
        _omniStatusMessage.value = feedback
        try {
            Toast.makeText(appContext, feedback, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun toggleHingeHaptics() {
        setHingeHapticsEnabled(!_hingeHapticsEnabled.value)
    }

    fun setFoldCloseActionMode(modeOrQuery: String) {
        val trimmed = modeOrQuery.trim()
        val resolved = when {
            trimmed in FOLD_CLOSE_MODES -> trimmed
            trimmed.contains("talk", ignoreCase = true) || trimmed.contains("vc", ignoreCase = true) ->
                "🎙️ Auto-Talk to VC"
            trimmed.contains("tent", ignoreCase = true) || trimmed.contains("oled", ignoreCase = true) ||
                trimmed.contains("standby", ignoreCase = true) -> "⛺ OLED Standby"
            trimmed.contains("lock", ignoreCase = true) -> "🔒 Instant Lock"
            trimmed.contains("awake", ignoreCase = true) || trimmed.contains("cover", ignoreCase = true) ->
                "📱 Keep Cover Awake"
            else -> {
                val currentIdx = FOLD_CLOSE_MODES.indexOf(_foldCloseModeLabel.value)
                FOLD_CLOSE_MODES[(if (currentIdx >= 0) currentIdx + 1 else 0) % FOLD_CLOSE_MODES.size]
            }
        }
        _foldCloseModeLabel.value = resolved
        rawPrefs.edit().putString(KEY_FOLD_CLOSE_MODE, resolved).apply()
        val keepCoverAwake = resolved.contains("Keep Cover Awake", ignoreCase = true) ||
            resolved.contains("Auto-Talk", ignoreCase = true)
        SystemTweaker.setFoldCloseStayAwake(appContext, keepCoverAwake)
        syncUiStateSnapshot()
        val feedback = "Fold-Close Mode: $resolved"
        _omniStatusMessage.value = feedback
        try {
            Toast.makeText(appContext, feedback, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun cycleFoldCloseActionMode() {
        val currentIdx = FOLD_CLOSE_MODES.indexOf(_foldCloseModeLabel.value)
        val next = FOLD_CLOSE_MODES[(if (currentIdx >= 0) currentIdx + 1 else 0) % FOLD_CLOSE_MODES.size]
        setFoldCloseActionMode(next)
    }

    // v0.5.0 Unfold-Open Action Mode Controls (`"unfold_action_mode"`)
    fun setUnfoldActionMode(modeOrQuery: String) {
        val trimmed = modeOrQuery.trim()
        val resolvedMode: UnfoldActionMode = when {
            trimmed.contains("termux", ignoreCase = true) || trimmed.equals("vc_termux", ignoreCase = true) ->
                UnfoldActionMode.AUTO_SPLIT_VC_TERMUX
            trimmed.contains("compare", ignoreCase = true) || trimmed.contains("prod", ignoreCase = true) ||
                trimmed.equals("prod_compare", ignoreCase = true) ->
                UnfoldActionMode.AUTO_SPLIT_PROD_COMPARE
            trimmed.contains("browser", ignoreCase = true) || trimmed.contains("chrome", ignoreCase = true) ||
                trimmed.equals("vc_browser", ignoreCase = true) ->
                UnfoldActionMode.AUTO_SPLIT_VC_BROWSER
            trimmed.contains("cockpit", ignoreCase = true) || trimmed.contains("dashboard", ignoreCase = true) ||
                trimmed.contains("inner", ignoreCase = true) ->
                UnfoldActionMode.INNER_DASHBOARD
            else -> {
                val currentIdx = UNFOLD_OPEN_MODES.indexOf(_unfoldModeLabel.value)
                val nextIdx = (if (currentIdx >= 0) currentIdx + 1 else 0) % UnfoldActionMode.values().size
                UnfoldActionMode.values()[nextIdx]
            }
        }
        val resolvedLabel = "${resolvedMode.badge} ${resolvedMode.label}"
        _unfoldModeLabel.value = resolvedLabel
        rawPrefs.edit().putString(KEY_UNFOLD_MODE, resolvedLabel).apply()
        SystemTweaker.setUnfoldActionMode(appContext, resolvedMode)
        syncUiStateSnapshot()
        val feedback = "Unfold-Open Mode: $resolvedLabel"
        _omniStatusMessage.value = feedback
        try {
            Toast.makeText(appContext, feedback, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun cycleUnfoldActionMode() {
        val currentIdx = UNFOLD_OPEN_MODES.indexOf(_unfoldModeLabel.value)
        val next = UNFOLD_OPEN_MODES[(if (currentIdx >= 0) currentIdx + 1 else 0) % UNFOLD_OPEN_MODES.size]
        setUnfoldActionMode(next)
    }

    // v0.3.0 Hardware Torch, Dual-Screen Presentation & Lock-Screen Cockpit Actions
    fun toggleTorch() {
        val next = SystemTweaker.toggleTorch(appContext, _isTorchEnabled.value)
        _isTorchEnabled.value = next
        syncUiStateSnapshot()
        val feedback = if (next) "🔦 Hardware Torch: ON" else "🔦 Hardware Torch: OFF"
        _omniStatusMessage.value = feedback
        try {
            Toast.makeText(appContext, feedback, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun setDualScreenPresentationActive(active: Boolean) {
        _isDualScreenPresentationActive.value = active
        syncUiStateSnapshot()
        val feedback = if (active) "🪞 Cover Display Mirror: ON" else "🪞 Cover Display Mirror: OFF"
        _omniStatusMessage.value = feedback
        try {
            Toast.makeText(appContext, feedback, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun toggleDualScreenPresentation() {
        setDualScreenPresentationActive(!_isDualScreenPresentationActive.value)
    }

    /**
     * Dismisses the Keyguard (`KeyguardManager.requestDismissKeyguard`) if locked and immediately
     * launches VC + Termux side-by-side on the Z Fold 8 display.
     */
    fun unlockAndSplitVcTermux(activity: Activity? = null) {
        val launchSplitAction = {
            val vcPkg = SystemTweaker.findInstalledVcPackage(appContext) ?: VcPackages.PREVIEW
            val launched = SystemTweaker.launchSplitScreenPair(
                context = appContext,
                primaryPackage = vcPkg,
                secondaryPackage = VcPackages.TERMUX_PACKAGE
            )
            if (!launched) {
                vcMonitor.launchVcSplitScreen(preferredSecondaryPackage = VcPackages.TERMUX_PACKAGE)
            }
            refreshSystemCompanionState()
        }

        val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (activity != null && km != null && km.isKeyguardLocked) {
            km.requestDismissKeyguard(
                activity,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        _isKeyguardLocked.value = false
                        syncUiStateSnapshot()
                        launchSplitAction()
                    }

                    override fun onDismissCancelled() {
                        refreshSystemCompanionState()
                    }

                    override fun onDismissError() {
                        launchSplitAction()
                    }
                }
            )
        } else {
            launchSplitAction()
        }
    }

    private fun handlePostureCommandId(actionId: String): Boolean {
        return when (actionId.lowercase()) {
            "posture_flex" -> {
                setPostureOverride(PostureOverride.FORCE_FLEX)
                true
            }
            "posture_inner" -> {
                setPostureOverride(PostureOverride.FORCE_INNER)
                true
            }
            "posture_cover" -> {
                setPostureOverride(PostureOverride.FORCE_COVER)
                true
            }
            "posture_tent" -> {
                setPostureOverride(PostureOverride.TENT_MODE)
                true
            }
            "posture_auto" -> {
                setPostureOverride(PostureOverride.AUTO)
                true
            }
            "torch", "system_torch", "toggle_torch" -> {
                toggleTorch()
                true
            }
            "dual_screen", "cover_mirror" -> {
                toggleDualScreenPresentation()
                true
            }
            "profile_all" -> {
                setWorkspaceProfile(WorkspaceProfile.ALL)
                true
            }
            "profile_vc_dev", "profile_vc" -> {
                setWorkspaceProfile(WorkspaceProfile.VC_DEV)
                true
            }
            "profile_focus" -> {
                setWorkspaceProfile(WorkspaceProfile.FOCUS)
                true
            }
            "profile_oled", "profile_oled_black" -> {
                setWorkspaceProfile(WorkspaceProfile.OLED_BLACK)
                true
            }
            "toggle_hinge_haptics", "hinge_haptics", "haptics" -> {
                toggleHingeHaptics()
                true
            }
            "hinge_haptics_on" -> {
                setHingeHapticsEnabled(true)
                true
            }
            "hinge_haptics_off" -> {
                setHingeHapticsEnabled(false)
                true
            }
            "set_fold_close_action", "fold_close_action", "cycle_fold_close" -> {
                cycleFoldCloseActionMode()
                true
            }
            "fold_close_awake", "fold_close_cover" -> {
                setFoldCloseActionMode("📱 Keep Cover Awake")
                true
            }
            "fold_close_vc", "fold_close_talk" -> {
                setFoldCloseActionMode("🎙️ Auto-Talk to VC")
                true
            }
            "fold_close_standby", "fold_close_oled" -> {
                setFoldCloseActionMode("⛺ OLED Standby")
                true
            }
            "fold_close_lock" -> {
                setFoldCloseActionMode("🔒 Instant Lock")
                true
            }
            "set_unfold_action", "unfold_action", "cycle_unfold" -> {
                cycleUnfoldActionMode()
                true
            }
            "unfold_dashboard", "unfold_cockpit", "unfold_inner" -> {
                setUnfoldActionMode("🖥️ Inner Cockpit")
                true
            }
            "unfold_vc_termux", "unfold_termux" -> {
                setUnfoldActionMode("⚡ Auto-Split VC + Termux")
                true
            }
            "unfold_prod_compare", "unfold_compare" -> {
                setUnfoldActionMode("🔀 Auto-Split Prod + Compare")
                true
            }
            "unfold_vc_browser", "unfold_browser" -> {
                setUnfoldActionMode("🌐 Auto-Split VC + Browser")
                true
            }
            else -> false
        }
    }

    private fun handleSystemOrTermuxOmniAction(action: OmniAction) {
        when (action) {
            is OmniAction.SystemCommand -> {
                when (action.target.name) {
                    "TOGGLE_HINGE_HAPTICS" -> {
                        when (action.parameter.lowercase()) {
                            "on", "true", "1" -> setHingeHapticsEnabled(true)
                            "off", "false", "0" -> setHingeHapticsEnabled(false)
                            else -> toggleHingeHaptics()
                        }
                    }
                    "SET_FOLD_CLOSE_ACTION" -> {
                        if (action.parameter.isNotBlank()) {
                            setFoldCloseActionMode(action.parameter)
                        } else {
                            cycleFoldCloseActionMode()
                        }
                    }
                    "SET_UNFOLD_ACTION" -> {
                        if (action.parameter.isNotBlank()) {
                            setUnfoldActionMode(action.parameter)
                        } else {
                            cycleUnfoldActionMode()
                        }
                    }
                }
            }
            is OmniAction.ScratchpadNote -> {
                if (action.noteText.isNotBlank()) {
                    updateQuickNote(action.noteText)
                    if (action.sendToVcImmediately && vcMonitor.config.value.isConfigured) {
                        vcMonitor.sendQuickMessage(
                            sessionId = null,
                            text = action.noteText,
                            steerIfWorking = true
                        )
                    }
                }
            }
            is OmniAction.TermuxCommand -> {
                preferences.incrementLaunchCount(VcPackages.TERMUX_PACKAGE)
                syncAppFlagsInMemory()
            }
            else -> {}
        }
    }

    // Omni-Command & Clipboard Vault Operations
    fun executeOmniAction(action: OmniAction) {
        handleSystemOrTermuxOmniAction(action)
        when (action) {
            is OmniAction.LaunchAdHocSplit -> {
                preferences.incrementLaunchCount(action.primaryApp.packageName)
                preferences.incrementLaunchCount(action.secondaryApp.packageName)
                syncAppFlagsInMemory()
                closeSearch()
            }
            is OmniAction.SendToVc -> {
                if (action.prompt.isNotBlank() && vcMonitor.config.value.isConfigured) {
                    vcMonitor.sendQuickMessage(
                        sessionId = null,
                        text = action.prompt,
                        steerIfWorking = true
                    )
                }
                closeSearch()
            }
            is OmniAction.ScratchpadNote, is OmniAction.TermuxCommand -> {
                closeSearch()
            }
            else -> {}
        }

        OmniCommandEngine.execute(
            context = appContext,
            action = action,
            clipboardVault = clipboardVault,
            onToggleFloatingHud = ::toggleFloatingHud,
            onToggleThumbArc = ::toggleRadialThumbArc,
            onStatusMessage = { msg ->
                _omniStatusMessage.value = msg
            },
            onPostureCommand = { actionId ->
                handlePostureCommandId(actionId)
            }
        )

        if (action is OmniAction.SystemCommand) {
            refreshSystemCompanionState()
        }
    }

    /**
     * Synchronizes ViewModel state (app launch counts, live VC prompt dispatch, posture overrides, and companion state)
     * when [SpotlightSearchOverlay] or [OmniCommandResultsCard] executes an [OmniAction].
     */
    fun onOmniActionFromSpotlight(action: OmniAction) {
        handlePostureCommandId(action.actionId)
        handleSystemOrTermuxOmniAction(action)
        when (action) {
            is OmniAction.LaunchAdHocSplit -> {
                preferences.incrementLaunchCount(action.primaryApp.packageName)
                preferences.incrementLaunchCount(action.secondaryApp.packageName)
                syncAppFlagsInMemory()
                closeSearch()
            }
            is OmniAction.SendToVc -> {
                if (action.prompt.isNotBlank() && vcMonitor.config.value.isConfigured) {
                    vcMonitor.sendQuickMessage(
                        sessionId = null,
                        text = action.prompt,
                        steerIfWorking = true
                    )
                }
                closeSearch()
            }
            is OmniAction.SystemCommand -> {
                refreshSystemCompanionState()
                closeSearch()
            }
            is OmniAction.ScratchpadNote, is OmniAction.TermuxCommand -> {
                closeSearch()
            }
            else -> {}
        }
    }

    fun captureClipboardOnResume() {
        clipboardVault.captureFromSystemClipboard(appContext)
    }

    fun togglePinClipboardItem(itemId: String) {
        clipboardVault.togglePin(itemId)
    }

    fun copyClipboardItem(item: ClipboardItem) {
        ClipboardVault.copyToSystemClipboard(appContext, item.text, item.label.ifBlank { "Vault Snippet" })
        clipboardVault.addOrBumpClip(
            text = item.text,
            label = item.label,
            isPinned = item.isPinned,
            category = item.category
        )
        _omniStatusMessage.value = "Copied snippet to clipboard"
    }

    fun removeClipboardItem(itemId: String) {
        clipboardVault.removeClip(itemId)
    }

    fun clearUnpinnedClipboard() {
        clipboardVault.clearUnpinned()
    }

    // Floating Micro-HUD Pill Controls
    fun setFloatingHudEnabled(enabled: Boolean): Boolean {
        preferences.floatingHudEnabled = enabled
        return FloatingHudController.setHudEnabled(appContext, enabled)
    }

    fun toggleFloatingHud() {
        val next = !FloatingHudController.state.value.isEnabled
        setFloatingHudEnabled(next)
    }

    // Radial Thumb Arc & Spatial Parallax Controls
    fun setRadialArcEnabled(enabled: Boolean) {
        preferences.radialArcEnabled = enabled
        _radialArcEnabled.value = enabled
    }

    fun toggleRadialThumbArc() {
        setRadialArcEnabled(!_radialArcEnabled.value)
    }

    fun setRadialArcRightHanded(rightHanded: Boolean) {
        preferences.radialArcRightHanded = rightHanded
        _radialArcRightHanded.value = rightHanded
    }

    fun toggleRadialArcHandedness() {
        setRadialArcRightHanded(!_radialArcRightHanded.value)
    }

    fun setSpatialParallaxEnabled(enabled: Boolean) {
        preferences.spatialParallaxEnabled = enabled
        _spatialParallaxEnabled.value = enabled
    }

    fun toggleSpatialParallax() {
        setSpatialParallaxEnabled(!_spatialParallaxEnabled.value)
    }

    // VC Background Monitor & Command Center Actions
    fun updateVcConfig(
        serverUrl: String,
        authToken: String = vcMonitor.config.value.authToken,
        monitorEnabled: Boolean = vcMonitor.config.value.monitorEnabled,
        notifyWhenBackgrounded: Boolean = vcMonitor.config.value.notifyWhenBackgrounded
    ) {
        vcMonitor.updateConfig(
            serverUrl = serverUrl,
            authToken = authToken,
            monitorEnabled = monitorEnabled,
            notifyWhenBackgrounded = notifyWhenBackgrounded
        )
    }

    fun refreshVcNow() {
        vcMonitor.refreshNow()
    }

    fun sendVcQuickMessage(
        sessionId: String?,
        text: String,
        steerIfWorking: Boolean = true,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        vcMonitor.sendQuickMessage(
            sessionId = sessionId,
            text = text,
            steerIfWorking = steerIfWorking,
            onComplete = onComplete
        )
    }

    fun markVcAttentionRead(eventId: String? = null, markAll: Boolean = false) {
        vcMonitor.markAttentionRead(eventId = eventId, markAll = markAll)
    }

    fun launchVcApp(sessionId: String? = null): Boolean {
        return vcMonitor.launchVc(sessionId = sessionId)
    }

    fun launchVcSplitScreen(preferredSecondaryPackage: String? = null): Boolean {
        return vcMonitor.launchVcSplitScreen(preferredSecondaryPackage = preferredSecondaryPackage)
    }

    // Core Launcher Operations
    fun launchApp(app: AppEntry, sourceBounds: Rect? = null) {
        preferences.incrementLaunchCount(app.uniqueKey)
        if (app.uniqueKey != app.packageName) {
            preferences.incrementLaunchCount(app.packageName)
        }
        _recentSessionPackages.value =
            (listOf(app.packageName) + _recentSessionPackages.value.filter { it != app.packageName }).take(10)
        syncAppFlagsInMemory()
        if (_isSearchOpen.value) {
            closeSearch()
        }
        repository.launchApp(app, sourceBounds)
    }

    fun launchSplitScreenSingle(app: AppEntry) {
        preferences.incrementLaunchCount(app.packageName)
        syncAppFlagsInMemory()
        repository.launchSplitScreenSingle(app)
    }

    fun launchAppPair(pair: AppPair) {
        preferences.incrementLaunchCount(pair.primaryPackage)
        preferences.incrementLaunchCount(pair.secondaryPackage)
        syncAppFlagsInMemory()
        repository.launchAppPair(pair)
    }

    fun addAppPair(title: String, primaryPackage: String, secondaryPackage: String) {
        if (primaryPackage.isBlank() || secondaryPackage.isBlank()) return
        val newPair = AppPair(
            id = "pair_${System.currentTimeMillis()}",
            title = title.ifBlank { "Split Pair" },
            primaryPackage = primaryPackage,
            secondaryPackage = secondaryPackage
        )
        val updated = _appPairs.value + newPair
        preferences.appPairs = updated
        _appPairs.value = updated
    }

    fun createQuickAppPairFromTopApps() {
        val candidates = pinnedInnerApps.value.ifEmpty { allApps.value }
        if (candidates.size < 2) return
        val existingKeys = _appPairs.value.map { "${it.primaryPackage}|${it.secondaryPackage}" }.toSet()
        for (i in 0 until candidates.size) {
            for (j in i + 1 until candidates.size) {
                val first = candidates[i]
                val second = candidates[j]
                val key = "${first.packageName}|${second.packageName}"
                if (key !in existingKeys) {
                    addAppPair(
                        title = "${first.label.take(10)} + ${second.label.take(10)}",
                        primaryPackage = first.packageName,
                        secondaryPackage = second.packageName
                    )
                    return
                }
            }
        }
    }

    fun removeAppPair(pairId: String) {
        val updated = _appPairs.value.filterNot { it.id == pairId }
        preferences.appPairs = updated
        _appPairs.value = updated
    }

    fun createFolder(name: String, packageNames: List<String>) {
        if (name.isBlank()) return
        val newFolder = FolderItem(
            id = "folder_${System.currentTimeMillis()}",
            name = name.trim(),
            packageNames = packageNames
        )
        val updated = _folders.value + newFolder
        preferences.folders = updated
        _folders.value = updated
    }

    fun removeFolder(folderId: String) {
        val updated = _folders.value.filterNot { it.id == folderId }
        preferences.folders = updated
        _folders.value = updated
    }

    fun launchShortcut(shortcut: AppShortcutItem) {
        repository.launchShortcut(shortcut)
    }

    fun togglePinCover(app: AppEntry) {
        val current = preferences.pinnedCoverPackages.toMutableSet()
        if (app.packageName in current) {
            current.remove(app.packageName)
        } else {
            current.add(app.packageName)
        }
        preferences.pinnedCoverPackages = current
        syncAppFlagsInMemory()
    }

    fun togglePinInner(app: AppEntry) {
        val current = preferences.pinnedInnerPackages.toMutableSet()
        if (app.packageName in current) {
            current.remove(app.packageName)
        } else {
            current.add(app.packageName)
        }
        preferences.pinnedInnerPackages = current
        syncAppFlagsInMemory()
    }

    /**
     * v0.4.0 Interactive Custom Dock Pinning (`"pinned_dock_packages"` in SharedPreferences).
     * Allows 1-tap pinning/unpinning of any app to the persistent Z Fold 8 / S23 Ultra Home Dock.
     */
    fun togglePinAppToDock(packageName: String) {
        if (packageName.isBlank()) return
        val current = preferences.pinnedDockPackages.toMutableSet()
        val nowPinned = if (packageName in current) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            true
        }
        preferences.pinnedDockPackages = current
        rawPrefs.edit().putStringSet(KEY_PINNED_DOCK, current.toSet()).apply()
        _pinnedDockPackages.value = current.toSet()
        syncAppFlagsInMemory()
        val label = _allInstalledApps.value.firstOrNull { it.packageName == packageName }?.label ?: packageName
        val feedback = if (nowPinned) "📌 Pinned $label to Dock" else "📍 Unpinned $label from Dock"
        _omniStatusMessage.value = feedback
        try {
            Toast.makeText(appContext, feedback, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun togglePinDock(app: AppEntry) {
        togglePinAppToDock(app.packageName)
    }

    fun hideApp(app: AppEntry) {
        val current = preferences.hiddenPackages.toMutableSet()
        current.add(app.uniqueKey)
        preferences.hiddenPackages = current
        syncAppFlagsInMemory()
    }

    fun unhideApp(packageName: String) {
        val current = preferences.hiddenPackages.toMutableSet()
        current.remove(packageName)
        current.removeAll { it.startsWith("$packageName#") }
        preferences.hiddenPackages = current
        syncAppFlagsInMemory()
    }

    fun unhideAllApps() {
        preferences.hiddenPackages = emptySet()
        syncAppFlagsInMemory()
    }

    fun openAppInfo(app: AppEntry) {
        repository.openAppInfo(app)
    }

    fun openAppInfo(packageName: String) {
        repository.openAppInfo(packageName)
    }

    fun uninstallApp(app: AppEntry) {
        repository.requestUninstall(app.packageName)
    }

    fun uninstallApp(packageName: String) {
        repository.requestUninstall(packageName)
    }

    fun launchWebSearch(query: String) {
        closeSearch()
        repository.launchWebSearch(query)
    }

    fun openWallpaperPicker() {
        repository.openWallpaperPicker()
    }

    fun openClockApp() {
        repository.openClockApp()
    }

    fun requestDefaultLauncherRole(activity: Activity) {
        repository.requestDefaultLauncherRole(activity)
    }

    // Settings & System Tweaks
    fun setHideSystemStatusBar(hide: Boolean) {
        preferences.hideSystemStatusBar = hide
        _hideSystemStatusBar.value = hide
    }

    fun toggleHideSystemStatusBar() {
        setHideSystemStatusBar(!_hideSystemStatusBar.value)
    }

    fun setIconShapePreset(preset: IconShapePreset) {
        preferences.iconShapePresetName = preset.name
        _iconShapePreset.value = preset
    }

    fun updateQuickNote(text: String) {
        preferences.quickNoteText = text
        scratchpadPrefs.edit().putString(KEY_SCRATCHPAD_TEXT, text).apply()
        _quickNoteText.value = text
    }

    fun toggleLockScreenRotation() {
        val target = !_lockScreenRotationEnabled.value
        if (SystemTweaker.setLockScreenRotation(appContext, target)) {
            _lockScreenRotationEnabled.value = target
        } else {
            _lockScreenRotationEnabled.value = SystemTweaker.isLockScreenRotationEnabled(appContext)
        }
    }

    fun enforceGboardInDeX() {
        SystemTweaker.enforcePreferredIme(appContext)
        refreshSystemCompanionState()
    }

    fun lockScreenNow() {
        if (!SystemTweaker.lockScreenNow()) {
            SystemTweaker.openAccessibilitySettings(appContext)
        }
    }

    fun expandNotificationsPanel() {
        SystemTweaker.expandNotificationsPanel(appContext)
    }

    fun openAccessibilitySettings() {
        SystemTweaker.openAccessibilitySettings(appContext)
    }

    /**
     * v0.6.0 Reads live Wireless Debugging (`adb_wifi_enabled`) state and active TCP/TLS port
     * via `SystemTweaker.readWirelessAdbSnapshot(appContext)` (when available) + `Settings.Global`
     * and `android.os.SystemProperties`.
     */
    private fun refreshWirelessAdbState() {
        var enabled = try {
            Settings.Global.getInt(appContext.contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (_: Exception) {
            false
        }
        var port: Int? = null

        // 1. Query SystemTweaker.readWirelessAdbSnapshot(appContext) if present
        try {
            val readMethod = SystemTweaker::class.java.methods.firstOrNull {
                it.name == "readWirelessAdbSnapshot" && it.parameterCount == 1
            }
            val snap = readMethod?.invoke(SystemTweaker, appContext)
            if (snap != null) {
                val snapClass = snap.javaClass
                val enabledVal = snapClass.methods.firstOrNull {
                    it.name == "isEnabled" || it.name == "getEnabled" || it.name == "getIsEnabled"
                }?.invoke(snap) as? Boolean
                if (enabledVal != null) {
                    enabled = enabledVal
                }
                val portVal = (snapClass.methods.firstOrNull {
                    it.name == "getPort" || it.name == "getTcpPort" || it.name == "getTlsPort"
                }?.invoke(snap) as? Number)?.toInt()
                if (portVal != null && portVal > 0) {
                    port = portVal
                }
            }
        } catch (_: Exception) {
        }

        // 2. Also inspect system properties (`service.adb.tls.port` / `service.adb.tcp.port`)
        if (port == null) {
            try {
                val sysProps = Class.forName("android.os.SystemProperties")
                val getIntMethod = sysProps.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
                val tlsPort = (getIntMethod.invoke(null, "service.adb.tls.port", -1) as? Int) ?: -1
                val tcpPort = (getIntMethod.invoke(null, "service.adb.tcp.port", -1) as? Int) ?: -1
                port = when {
                    tlsPort > 0 -> tlsPort
                    tcpPort > 0 -> tcpPort
                    enabled -> 5555
                    else -> null
                }
            } catch (_: Exception) {
                if (enabled) port = 5555
            }
        }

        _isWirelessAdbEnabled.value = enabled
        _wirelessAdbPort.value = if (enabled) port else null
    }

    private fun registerWirelessAdbObserver() {
        if (wirelessAdbObserverRegistered) return
        try {
            val cr = appContext.contentResolver
            val wifiAdbUri = Settings.Global.getUriFor("adb_wifi_enabled")
            if (wifiAdbUri != null) {
                cr.registerContentObserver(wifiAdbUri, false, wirelessAdbContentObserver)
            }
            val usbAdbUri = Settings.Global.getUriFor(Settings.Global.ADB_ENABLED)
            if (usbAdbUri != null) {
                cr.registerContentObserver(usbAdbUri, false, wirelessAdbContentObserver)
            }
            wirelessAdbObserverRegistered = true
        } catch (_: Exception) {
        }
    }

    /**
     * v0.6.0 1-Tap Wireless Debugging Quick-Toggle (`adb_wifi_enabled`).
     * Uses `WRITE_SECURE_SETTINGS` when granted; otherwise opens Wireless Debugging settings.
     */
    fun toggleWirelessAdb() {
        val target = !_isWirelessAdbEnabled.value
        var toggled = false

        // 1. Try SystemTweaker.toggleWirelessAdb / setWirelessAdbEnabled if available
        try {
            val setMethod = SystemTweaker::class.java.methods.firstOrNull {
                it.name == "setWirelessAdbEnabled" && it.parameterCount == 2
            }
            val toggleMethod = SystemTweaker::class.java.methods.firstOrNull {
                it.name == "toggleWirelessAdb" && it.parameterCount >= 1
            }
            val result = when {
                setMethod != null -> setMethod.invoke(SystemTweaker, appContext, target) as? Boolean
                toggleMethod != null && toggleMethod.parameterCount == 1 ->
                    toggleMethod.invoke(SystemTweaker, appContext) as? Boolean
                toggleMethod != null && toggleMethod.parameterCount == 2 ->
                    toggleMethod.invoke(SystemTweaker, appContext, _isWirelessAdbEnabled.value) as? Boolean
                else -> null
            }
            if (result == true) {
                toggled = true
            }
        } catch (_: Exception) {
        }

        // 2. Direct Settings.Global write (`adb_wifi_enabled`)
        if (!toggled && SystemTweaker.hasWriteSecureSettings(appContext)) {
            try {
                val cr = appContext.contentResolver
                if (target) {
                    Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                }
                toggled = Settings.Global.putInt(cr, "adb_wifi_enabled", if (target) 1 else 0)
            } catch (_: Exception) {
                toggled = false
            }
        }

        refreshWirelessAdbState()
        syncUiStateSnapshot()

        if (toggled || _isWirelessAdbEnabled.value == target) {
            val portSuffix = _wirelessAdbPort.value?.let { " (:$it)" } ?: ""
            val msg = if (_isWirelessAdbEnabled.value) {
                "Wireless ADB: ON$portSuffix"
            } else {
                "Wireless ADB: OFF"
            }
            _omniStatusMessage.value = msg
            try {
                Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        } else {
            openWirelessAdbSettings()
        }
    }

    /**
     * v0.6.0 Opens Android Developer Options / Wireless Debugging settings screen.
     */
    fun openWirelessAdbSettings() {
        try {
            val openMethod = SystemTweaker::class.java.methods.firstOrNull {
                it.name == "openWirelessAdbSettings" && it.parameterCount == 1
            }
            if (openMethod != null) {
                openMethod.invoke(SystemTweaker, appContext)
                return
            }
        } catch (_: Exception) {
        }

        try {
            val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(devIntent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(fallback)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * v0.6.0 1-Tap Tailscale Mesh VPN Quick-Toggle (`com.tailscale.ipn.CONNECT_VPN` / `DISCONNECT_VPN`),
     * falling back to opening the Tailscale app if direct broadcast requires interactive session approval.
     */
    fun toggleTailscaleVpn() {
        val currentlyConnected = !_tailscaleIp.value.isNullOrBlank()
        var handled = false

        try {
            val toggleMethod = SystemTweaker::class.java.methods.firstOrNull {
                it.name == "toggleTailscaleVpn" && it.parameterCount >= 1
            }
            if (toggleMethod != null) {
                val res = if (toggleMethod.parameterCount == 1) {
                    toggleMethod.invoke(SystemTweaker, appContext) as? Boolean
                } else {
                    toggleMethod.invoke(SystemTweaker, appContext, currentlyConnected) as? Boolean
                }
                if (res == true) handled = true
            }
        } catch (_: Exception) {
        }

        if (!handled) {
            try {
                val tailscalePkg = "com.tailscale.ipn"
                val hasTailscale = appContext.packageManager.getLaunchIntentForPackage(tailscalePkg) != null
                if (hasTailscale) {
                    val action = if (currentlyConnected) {
                        "com.tailscale.ipn.DISCONNECT_VPN"
                    } else {
                        "com.tailscale.ipn.CONNECT_VPN"
                    }
                    val broadcastIntent = Intent(action).apply {
                        setPackage(tailscalePkg)
                        setClassName(tailscalePkg, "com.tailscale.ipn.IPNReceiver")
                    }
                    appContext.sendBroadcast(broadcastIntent)
                    handled = true
                }
            } catch (_: Exception) {
            }
        }

        if (!handled) {
            openTailscaleApp()
            return
        }

        val targetMsg = if (currentlyConnected) "Tailscale VPN: Disconnecting…" else "Tailscale VPN: Connecting…"
        _omniStatusMessage.value = targetMsg
        try {
            Toast.makeText(appContext, targetMsg, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }

        viewModelScope.launch {
            delay(900L)
            refreshSystemCompanionState()
            delay(1600L)
            refreshSystemCompanionState()
        }
    }

    /**
     * v0.6.0 Opens the Tailscale app (`com.tailscale.ipn`) or Android VPN Settings if not installed.
     */
    fun openTailscaleApp() {
        try {
            val openMethod = SystemTweaker::class.java.methods.firstOrNull {
                it.name == "openTailscaleApp" && it.parameterCount == 1
            }
            if (openMethod != null) {
                val res = openMethod.invoke(SystemTweaker, appContext) as? Boolean
                if (res == true) return
            }
        } catch (_: Exception) {
        }

        try {
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage("com.tailscale.ipn")?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                appContext.startActivity(launchIntent)
                return
            }
        } catch (_: Exception) {
        }

        try {
            val vpnSettings = Intent(Settings.ACTION_VPN_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(vpnSettings)
        } catch (_: Exception) {
        }
    }

    private fun registerBatteryMonitor() {
        if (batteryReceiverRegistered) return
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = ContextCompat.registerReceiver(
                appContext,
                batteryReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            sticky?.let { batteryReceiver.onReceive(appContext, it) }
            batteryReceiverRegistered = true
        } catch (_: Exception) {
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val cap = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            _batteryPct.value = cap.coerceIn(0, 100)
        }
    }

    override fun onCleared() {
        repository.stopObservingPackages()
        if (wirelessAdbObserverRegistered) {
            try {
                appContext.contentResolver.unregisterContentObserver(wirelessAdbContentObserver)
            } catch (_: Exception) {
            }
            wirelessAdbObserverRegistered = false
        }
        if (batteryReceiverRegistered) {
            try {
                appContext.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            batteryReceiverRegistered = false
        }
        super.onCleared()
    }

    private fun resolveIconShapePreset(name: String): IconShapePreset {
        return IconShapePreset.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: IconShapePreset.SQUIRCLE
    }
}
