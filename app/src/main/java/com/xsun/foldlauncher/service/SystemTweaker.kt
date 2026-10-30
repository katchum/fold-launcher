package com.xsun.foldlauncher.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Display
import android.view.Surface
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.xsun.foldlauncher.posture.FoldCloseActionMode as PostureFoldCloseActionMode
import com.xsun.foldlauncher.posture.HingeDetentTick as PostureHingeDetentTick
import com.xsun.foldlauncher.posture.UnfoldActionMode as PostureUnfoldActionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

typealias FoldCloseActionMode = PostureFoldCloseActionMode
typealias HingeDetentTick = PostureHingeDetentTick
typealias UnfoldActionMode = PostureUnfoldActionMode

data class LockScreenStatus(
    val isKeyguardLocked: Boolean,
    val rotationAllowed: Boolean,
    val faceUnlockInstantBypass: Boolean,
    val foldCloseStayAwake: Boolean,
    val lockShortcutsConfiguredForVc: Boolean,
    val lockScreenHudOverlayEnabled: Boolean,
    val autoCockpitOnWakeEnabled: Boolean
)

data class HardwareTelemetrySnapshot(
    val batteryTempCelsius: Float = 0f,
    val batteryCurrentMa: Int = 0,
    val isCharging: Boolean = false,
    val availableRamMb: Long = 0L,
    val totalRamMb: Long = 0L,
    val secondaryDisplayCount: Int = 0
)

data class NetworkTelemetrySnapshot(
    val tailscaleIp: String? = null,
    val lanIp: String? = null,
    val isVpnActive: Boolean = false,
    val isWifiConnected: Boolean = false,
    val downstreamMbps: Int = 0
) {
    val isTailscaleConnected: Boolean
        get() = !tailscaleIp.isNullOrBlank()

    val primaryIp: String?
        get() = tailscaleIp ?: lanIp

    val statusBadge: String
        get() = when {
            !tailscaleIp.isNullOrBlank() -> "TS $tailscaleIp"
            !lanIp.isNullOrBlank() -> "LAN $lanIp"
            isVpnActive -> "VPN ACTIVE"
            isWifiConnected -> "WIFI ONLINE"
            else -> "OFFLINE"
        }
}

data class WirelessAdbSnapshot(
    val isEnabled: Boolean = false,
    val port: Int? = null,
    val ipAddress: String? = null
) {
    val connectAddress: String?
        get() = if (!ipAddress.isNullOrBlank() && port != null && port > 0) {
            "$ipAddress:$port"
        } else {
            ipAddress
        }

    val statusLabel: String
        get() = if (isEnabled) {
            if (port != null && port > 0) "On • :$port" else "On"
        } else {
            "Off"
        }
}

object SystemTweaker {

    const val SAMSUNG_HONEYBOARD_PACKAGE = "com.samsung.android.honeyboard"
    const val GBOARD_IME_ID = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
    const val SWIFTKEY_IME_ID = "com.touchtype.swiftkey/com.touchtype.KeyboardService"

    const val VC_PROD_PACKAGE = "dev.agentconversation.vci"
    const val VC_PREVIEW_PACKAGE = "dev.agentconversation.preview"
    const val VC_TALK_ACTION = "dev.agentconversation.widget.TALK"
    const val DEFAULT_VC_HOST_URL = "http://100.96.188.90:8080"

    const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    const val TAILSCALE_RECEIVER = "com.tailscale.ipn.IPNReceiver"
    const val TAILSCALE_ACTION_CONNECT = "com.tailscale.ipn.CONNECT_VPN"
    const val TAILSCALE_ACTION_DISCONNECT = "com.tailscale.ipn.DISCONNECT_VPN"

    const val EXTRA_LOCK_SCREEN_COCKPIT = "com.xsun.foldlauncher.EXTRA_LOCK_SCREEN_COCKPIT"

    private const val PREFS_HINGE_TWEAKS = "fold8_hinge_tweaks_prefs"
    private const val KEY_HINGE_DETENT_HAPTICS = "hinge_detent_haptics_enabled"
    private const val KEY_FOLD_CLOSE_ACTION_MODE = "fold_close_action_mode"
    private const val KEY_UNFOLD_ACTION_MODE = "unfold_action_mode"

    const val TERMUX_PACKAGE = "com.termux"
    private val PREFERRED_BROWSER_PACKAGES = listOf(
        "com.sec.android.app.sbrowser",
        "com.android.chrome",
        "com.brave.browser",
        "org.mozilla.firefox"
    )

    private const val KEY_ADB_WIFI_ENABLED = "adb_wifi_enabled"
    private const val KEY_LOCK_SCREEN_ALLOW_ROTATION = "lock_screen_allow_rotation"
    private const val KEY_SUB_DISPLAY_ROTATION_LOCK = "sub_display_accelerometer_rotation"
    private const val KEY_DEX_ON_SCREEN_KEYBOARD_LOCATION = "dex_on_screen_keyboard_location"
    private const val KEY_FACE_STAY_ON_LOCK_SCREEN = "face_stay_on_lock_screen"
    private const val KEY_FOLD_LOCK_BEHAVIOR = "fold_lock_behavior_setting"
    private const val KEY_LOCK_APPLICATION_SHORTCUT = "lock_application_shortcut"
    private const val KEY_AOD_TAP_TO_SHOW_MODE = "aod_tap_to_show_mode"

    const val FOLD_STAY_AWAKE_VALUE = "stay_awake_on_fold_key"
    const val FOLD_SLEEP_VALUE = "sleep_on_fold_key"

    private val _isTorchActive = MutableStateFlow(false)
    val isTorchActive: StateFlow<Boolean> = _isTorchActive.asStateFlow()

    private val _wirelessAdbFlow = MutableStateFlow(false)
    val wirelessAdbFlow: StateFlow<Boolean> = _wirelessAdbFlow.asStateFlow()

    private val _hingeDetentHapticsEnabled = MutableStateFlow(true)
    val hingeDetentHapticsEnabled: StateFlow<Boolean> = _hingeDetentHapticsEnabled.asStateFlow()

    private val _foldCloseActionMode = MutableStateFlow(FoldCloseActionMode.STAY_AWAKE_COVER)
    val foldCloseActionMode: StateFlow<FoldCloseActionMode> = _foldCloseActionMode.asStateFlow()

    private val _unfoldActionMode = MutableStateFlow(UnfoldActionMode.INNER_DASHBOARD)
    val unfoldActionMode: StateFlow<UnfoldActionMode> = _unfoldActionMode.asStateFlow()

    fun loadHingePreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_HINGE_TWEAKS, Context.MODE_PRIVATE)
        _hingeDetentHapticsEnabled.value = prefs.getBoolean(KEY_HINGE_DETENT_HAPTICS, true)
        val rawMode = prefs.getString(KEY_FOLD_CLOSE_ACTION_MODE, FoldCloseActionMode.STAY_AWAKE_COVER.name)
        val parsedMode = FoldCloseActionMode.values().firstOrNull { it.name == rawMode }
            ?: FoldCloseActionMode.STAY_AWAKE_COVER
        _foldCloseActionMode.value = parsedMode

        val rawUnfold = prefs.getString(KEY_UNFOLD_ACTION_MODE, UnfoldActionMode.INNER_DASHBOARD.key)
        _unfoldActionMode.value = UnfoldActionMode.fromKey(rawUnfold)
        _wirelessAdbFlow.value = isWirelessAdbEnabled(context)
    }

    fun setHingeDetentHapticsEnabled(context: Context, enabled: Boolean) {
        _hingeDetentHapticsEnabled.value = enabled
        context.getSharedPreferences(PREFS_HINGE_TWEAKS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HINGE_DETENT_HAPTICS, enabled)
            .apply()
    }

    fun toggleHingeDetentHaptics(context: Context): Boolean {
        val next = !_hingeDetentHapticsEnabled.value
        setHingeDetentHapticsEnabled(context, next)
        return next
    }

    fun setFoldCloseActionMode(context: Context, mode: FoldCloseActionMode) {
        _foldCloseActionMode.value = mode
        context.getSharedPreferences(PREFS_HINGE_TWEAKS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLD_CLOSE_ACTION_MODE, mode.name)
            .apply()
        when (mode) {
            FoldCloseActionMode.STAY_AWAKE_COVER,
            FoldCloseActionMode.AUTO_VC_VOICE,
            FoldCloseActionMode.TENT_STANDBY -> setFoldCloseStayAwake(context, true)
            FoldCloseActionMode.INSTANT_LOCK -> setFoldCloseStayAwake(context, false)
        }
    }

    fun cycleFoldCloseActionMode(context: Context): FoldCloseActionMode {
        val next = _foldCloseActionMode.value.next()
        setFoldCloseActionMode(context, next)
        return next
    }

    fun setUnfoldActionMode(context: Context, mode: UnfoldActionMode) {
        _unfoldActionMode.value = mode
        context.getSharedPreferences(PREFS_HINGE_TWEAKS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UNFOLD_ACTION_MODE, mode.key)
            .apply()
    }

    fun cycleUnfoldActionMode(context: Context): UnfoldActionMode {
        val next = _unfoldActionMode.value.next()
        setUnfoldActionMode(context, next)
        return next
    }

    /**
     * Executes the user's configured [UnfoldActionMode] when unfolding the Z Fold 8 across `155°`.
     */
    fun executeUnfoldAction(
        context: Context,
        mode: UnfoldActionMode = _unfoldActionMode.value
    ): Boolean {
        return when (mode) {
            UnfoldActionMode.INNER_DASHBOARD -> {
                try {
                    val intent = Intent().apply {
                        setClassName(context.packageName, "com.xsun.foldlauncher.MainActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
            UnfoldActionMode.AUTO_SPLIT_VC_TERMUX -> {
                val vcPkg = findInstalledVcPackage(context) ?: VC_PROD_PACKAGE
                if (launchSplitScreenPair(context, vcPkg, TERMUX_PACKAGE)) {
                    true
                } else {
                    launchVc(context, adjacentSplitScreen = true)
                }
            }
            UnfoldActionMode.AUTO_SPLIT_PROD_COMPARE -> {
                if (launchSplitScreenPair(context, VC_PROD_PACKAGE, VC_PREVIEW_PACKAGE)) {
                    true
                } else {
                    launchVc(context, adjacentSplitScreen = true)
                }
            }
            UnfoldActionMode.AUTO_SPLIT_VC_BROWSER -> {
                val vcPkg = findInstalledVcPackage(context) ?: VC_PROD_PACKAGE
                val pm = context.packageManager
                val browserPkg = PREFERRED_BROWSER_PACKAGES.firstOrNull {
                    pm.getLaunchIntentForPackage(it) != null
                }
                if (browserPkg != null && launchSplitScreenPair(context, vcPkg, browserPkg)) {
                    true
                } else {
                    launchVc(context, adjacentSplitScreen = true)
                }
            }
        }
    }

    /**
     * Fires a crisp mechanical watch-crown haptic click/tick on the Z Fold 8 linear actuator
     * when sweeping across `15°` hinge detents (`EFFECT_CLICK` for major posture thresholds,
     * `EFFECT_TICK` for minor `15°` steps).
     */
    @Suppress("DEPRECATION")
    fun triggerHingeDetentHaptic(context: Context, tick: HingeDetentTick) {
        if (tick == HingeDetentTick.NONE || !_hingeDetentHapticsEnabled.value) return
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vibrator.hasVibrator()) return
            val effectId = if (tick.isMajorPostureThreshold) {
                VibrationEffect.EFFECT_CLICK
            } else {
                VibrationEffect.EFFECT_TICK
            }
            vibrator.vibrate(VibrationEffect.createPredefined(effectId))
        } catch (_: Exception) {
        }
    }

    /**
     * Executes the user's configured [FoldCloseActionMode] when the Z Fold 8 hinge closes shut.
     */
    fun executeFoldCloseAction(
        context: Context,
        mode: FoldCloseActionMode = _foldCloseActionMode.value
    ): Boolean {
        return when (mode) {
            FoldCloseActionMode.STAY_AWAKE_COVER -> {
                setFoldCloseStayAwake(context, true)
            }
            FoldCloseActionMode.AUTO_VC_VOICE -> {
                setFoldCloseStayAwake(context, true)
                launchVc(context, startVoiceTalk = true)
            }
            FoldCloseActionMode.TENT_STANDBY -> {
                setFoldCloseStayAwake(context, true)
                launchLockScreenCockpit(context)
            }
            FoldCloseActionMode.INSTANT_LOCK -> {
                setFoldCloseStayAwake(context, false)
                lockScreenNow()
            }
        }
    }

    fun isTorchEnabled(): Boolean = _isTorchActive.value

    /**
     * Toggles the hardware camera flash / torch using `CameraManager.setTorchMode`
     * (requires zero runtime permissions on Android 10+).
     */
    fun setTorchEnabled(context: Context, enabled: Boolean): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        return try {
            val cameraId = findFlashCameraId(cm) ?: return false
            cm.setTorchMode(cameraId, enabled)
            _isTorchActive.value = enabled
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Toggles hardware torch state and returns the new enabled state (or `currentlyEnabled` on error).
     */
    fun toggleTorch(context: Context, currentlyEnabled: Boolean = _isTorchActive.value): Boolean {
        val target = !currentlyEnabled
        return if (setTorchEnabled(context, target)) {
            target
        } else {
            currentlyEnabled
        }
    }

    private fun findFlashCameraId(cm: CameraManager): String? {
        var fallbackFlashId: String? = null
        for (id in cm.cameraIdList) {
            val chars = cm.getCameraCharacteristics(id)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            if (!hasFlash) continue
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
            if (fallbackFlashId == null) {
                fallbackFlashId = id
            }
        }
        return fallbackFlashId
    }

    /**
     * Reads live Z Fold 8 / Slab hardware telemetry: battery temperature (°C), instantaneous
     * current (mA), charging state, available/total RAM (MB), and secondary display count.
     */
    fun readHardwareTelemetry(context: Context): HardwareTelemetrySnapshot {
        var tempCelsius = 0f
        var isCharging = false
        try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            if (batteryIntent != null) {
                val rawTemp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                if (rawTemp > 0) {
                    tempCelsius = rawTemp / 10f
                }
                val status = batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN
                )
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (_: Exception) {
        }

        var currentMa = 0
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val rawMicroAmps = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            // Samsung devices report either microamps (|v| > 10_000) or milliamps directly
            currentMa = if (kotlin.math.abs(rawMicroAmps) > 10_000) {
                rawMicroAmps / 1000
            } else {
                rawMicroAmps
            }
        } catch (_: Exception) {
        }

        var availRamMb = 0L
        var totalRamMb = 0L
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                availRamMb = memInfo.availMem / (1024L * 1024L)
                totalRamMb = memInfo.totalMem / (1024L * 1024L)
            }
        } catch (_: Exception) {
        }

        var secondaryCount = 0
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val displays = dm?.displays ?: emptyArray()
            secondaryCount = displays.count { it.displayId != Display.DEFAULT_DISPLAY }
        } catch (_: Exception) {
        }

        return HardwareTelemetrySnapshot(
            batteryTempCelsius = tempCelsius,
            batteryCurrentMa = currentMa,
            isCharging = isCharging,
            availableRamMb = availRamMb,
            totalRamMb = totalRamMb,
            secondaryDisplayCount = secondaryCount
        )
    }

    /**
     * Reads live Tailscale Mesh VPN (`100.64.0.0/10` CGNAT, e.g. `100.101.43.86` on Z Fold 8 and
     * `100.68.53.123` on S23 Ultra) and private LAN IPv4 telemetry, plus active VPN/Wi-Fi transport
     * and downstream bandwidth (Mbps). Safe to call without runtime permissions.
     */
    fun readNetworkTelemetry(context: Context): NetworkTelemetrySnapshot {
        var tailscaleIp: String? = null
        var lanIp: String? = null

        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (nif in Collections.list(interfaces)) {
                    if (!nif.isUp || nif.isLoopback) continue
                    val addresses = nif.inetAddresses ?: continue
                    for (addr in Collections.list(addresses)) {
                        if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                        val hostAddr = addr.hostAddress ?: continue
                        if (tailscaleIp == null && isTailscaleCgnatIpv4(hostAddr)) {
                            tailscaleIp = hostAddr
                        } else if (lanIp == null && isPrivateLanIpv4(hostAddr)) {
                            lanIp = hostAddr
                        }
                    }
                }
            }
        }

        var isVpnActive = !tailscaleIp.isNullOrBlank()
        var isWifiConnected = !lanIp.isNullOrBlank()
        var downstreamMbps = 0

        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            val caps = if (activeNet != null) cm.getNetworkCapabilities(activeNet) else null
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    isVpnActive = true
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    isWifiConnected = true
                }
                val kbps = caps.linkDownstreamBandwidthKbps
                if (kbps > 0) {
                    downstreamMbps = (kbps / 1000).coerceAtLeast(1)
                }
            }
        }

        return NetworkTelemetrySnapshot(
            tailscaleIp = tailscaleIp,
            lanIp = lanIp,
            isVpnActive = isVpnActive,
            isWifiConnected = isWifiConnected,
            downstreamMbps = downstreamMbps
        )
    }

    /**
     * Returns true if `ip` is in the Tailscale CGNAT IPv4 range (`100.64.0.0/10`, i.e., `100.64..127.x.y`).
     */
    fun isTailscaleCgnatIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 100 && second in 64..127
    }

    /**
     * Returns true if `ip` is in RFC1918 private LAN space (`192.168.x.y`, `10.x.y.z`, or `172.16..31.x.y`).
     */
    fun isPrivateLanIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return when (first) {
            10 -> true
            192 -> second == 168
            172 -> second in 16..31
            else -> false
        }
    }

    /**
     * Checks whether Android Wireless Debugging (`adb_wifi_enabled`) is currently active.
     */
    fun isWirelessAdbEnabled(context: Context): Boolean {
        val enabled = try {
            Settings.Global.getInt(context.contentResolver, KEY_ADB_WIFI_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }
        _wirelessAdbFlow.value = enabled
        return enabled
    }

    /**
     * Enables or disables Android Wireless Debugging (`adb_wifi_enabled`) via `WRITE_SECURE_SETTINGS`.
     */
    fun setWirelessAdbEnabled(context: Context, enabled: Boolean): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                KEY_ADB_WIFI_ENABLED,
                if (enabled) 1 else 0
            )
            _wirelessAdbFlow.value = enabled
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Toggles Android Wireless Debugging (`adb_wifi_enabled`) and returns the resulting state.
     */
    fun toggleWirelessAdb(context: Context): Boolean {
        val current = isWirelessAdbEnabled(context)
        val target = !current
        return if (setWirelessAdbEnabled(context, target)) {
            target
        } else {
            current
        }
    }

    /**
     * Reads the active Wireless ADB TLS/TCP port (`service.adb.tls.port` or `service.adb.tcp.port`)
     * via `android.os.SystemProperties`.
     */
    @SuppressLint("PrivateApi")
    fun readWirelessAdbPort(): Int? {
        return try {
            val sysPropClass = Class.forName("android.os.SystemProperties")
            val getMethod = sysPropClass.getMethod("get", String::class.java, String::class.java)
            for (prop in listOf("service.adb.tls.port", "service.adb.tcp.port")) {
                val raw = (getMethod.invoke(null, prop, "") as? String)?.trim()
                val port = raw?.toIntOrNull()
                if (port != null && port > 0) {
                    return port
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Builds a live [WirelessAdbSnapshot] combining `adb_wifi_enabled`, TLS/TCP port, and Tailscale/LAN IP.
     */
    fun readWirelessAdbSnapshot(context: Context): WirelessAdbSnapshot {
        val enabled = isWirelessAdbEnabled(context)
        val port = if (enabled) readWirelessAdbPort() else null
        val net = readNetworkTelemetry(context)
        return WirelessAdbSnapshot(
            isEnabled = enabled,
            port = port,
            ipAddress = net.primaryIp
        )
    }

    /**
     * Opens Developer Options / Wireless Debugging settings screen.
     */
    fun openWirelessDebuggingSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /**
     * Toggles Tailscale (`com.tailscale.ipn`) VPN connection via its explicit `IPNReceiver` broadcast
     * (`CONNECT_VPN` / `DISCONNECT_VPN`), falling back to opening the Tailscale app if not installed
     * or if broadcast dispatch fails.
     */
    fun toggleTailscaleVpn(context: Context, currentlyConnected: Boolean): Boolean {
        val action = if (currentlyConnected) TAILSCALE_ACTION_DISCONNECT else TAILSCALE_ACTION_CONNECT
        return try {
            val broadcastIntent = Intent(action).apply {
                setClassName(TAILSCALE_PACKAGE, TAILSCALE_RECEIVER)
            }
            context.sendBroadcast(broadcastIntent)
            true
        } catch (_: Exception) {
            openTailscaleApp(context)
        }
    }

    /**
     * Launches the installed Tailscale app (`com.tailscale.ipn`).
     */
    fun openTailscaleApp(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: return false
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasWriteSecureSettings(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (FoldAccessibilityService.instance != null) return true
        val expectedComponent = "${context.packageName}/${FoldAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }

    fun getCurrentImeId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
    }

    fun isSamsungKeyboardActive(context: Context): Boolean {
        return getCurrentImeId(context).startsWith(SAMSUNG_HONEYBOARD_PACKAGE)
    }

    /**
     * Resolves the best non-Samsung keyboard installed on the device (preferring Gboard, then SwiftKey).
     */
    fun findPreferredNonSamsungIme(context: Context): String? {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return GBOARD_IME_ID
        val enabledList = imm.enabledInputMethodList
        val gboard = enabledList.firstOrNull {
            it.id.equals(GBOARD_IME_ID, ignoreCase = true) ||
                it.packageName.contains("google.android.inputmethod", ignoreCase = true)
        }
        if (gboard != null) return gboard.id

        val swiftKey = enabledList.firstOrNull {
            it.id.equals(SWIFTKEY_IME_ID, ignoreCase = true) ||
                it.packageName.contains("swiftkey", ignoreCase = true)
        }
        if (swiftKey != null) return swiftKey.id

        return enabledList.firstOrNull {
            !it.packageName.equals(SAMSUNG_HONEYBOARD_PACKAGE, ignoreCase = true)
        }?.id
    }

    /**
     * Prevents Samsung DeX from locking the user into Samsung Keyboard (Honeyboard).
     * Requires `android.permission.WRITE_SECURE_SETTINGS` granted once via ADB.
     */
    fun enforcePreferredIme(
        context: Context,
        preferredImeId: String? = null
    ): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        val targetIme = preferredImeId ?: findPreferredNonSamsungIme(context) ?: GBOARD_IME_ID
        return try {
            val cr = context.contentResolver
            Settings.Secure.putString(cr, Settings.Secure.DEFAULT_INPUT_METHOD, targetIme)
            // Also route DeX virtual keyboard to phone screen touchpad mode (1) so One UI doesn't override IME
            Settings.Global.putInt(cr, KEY_DEX_ON_SCREEN_KEYBOARD_LOCATION, 1)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isLockScreenRotationEnabled(context: Context): Boolean {
        return try {
            val cr = context.contentResolver
            val sysVal = Settings.System.getInt(cr, KEY_LOCK_SCREEN_ALLOW_ROTATION, -1)
            if (sysVal != -1) return sysVal == 1
            Settings.Secure.getInt(cr, KEY_LOCK_SCREEN_ALLOW_ROTATION, 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Enables or disables Lock Screen + Home Screen landscape rotation on Samsung One UI / Z Fold 8.
     */
    fun setLockScreenRotation(context: Context, enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        var updated = false
        val cr = context.contentResolver
        try {
            if (Settings.System.canWrite(context) || hasWriteSecureSettings(context)) {
                Settings.System.putInt(cr, KEY_LOCK_SCREEN_ALLOW_ROTATION, value)
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, value)
                updated = true
            }
        } catch (_: Exception) {
        }
        try {
            if (hasWriteSecureSettings(context)) {
                Settings.Secure.putInt(cr, KEY_LOCK_SCREEN_ALLOW_ROTATION, value)
                Settings.Global.putInt(cr, KEY_LOCK_SCREEN_ALLOW_ROTATION, value)
                updated = true
            }
        } catch (_: Exception) {
        }
        return updated
    }

    /**
     * One UI 9.0 Face Unlock instant bypass (`face_stay_on_lock_screen = 0`).
     * When `bypassSwipe == true`, unlocking with Face Recognition immediately enters the home screen
     * or active app without requiring a manual swipe on the lock screen.
     */
    fun setFaceUnlockInstantBypass(context: Context, bypassSwipe: Boolean = true): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        val stayOnLockValue = if (bypassSwipe) 0 else 1
        return try {
            Settings.Secure.putInt(context.contentResolver, KEY_FACE_STAY_ON_LOCK_SCREEN, stayOnLockValue)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isFaceUnlockInstantBypassEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, KEY_FACE_STAY_ON_LOCK_SCREEN, 1) == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * One UI 9.0 Z Fold 8 fold-close continuity (`fold_lock_behavior_setting = stay_awake_on_fold_key`).
     * Keeps the Cover Screen awake when folding the Z Fold 8 shut instead of locking immediately.
     */
    fun setFoldCloseStayAwake(context: Context, stayAwake: Boolean = true): Boolean {
        val cr = context.contentResolver
        val value = if (stayAwake) FOLD_STAY_AWAKE_VALUE else FOLD_SLEEP_VALUE
        var updated = false
        try {
            if (Settings.System.canWrite(context) || hasWriteSecureSettings(context)) {
                Settings.System.putString(cr, KEY_FOLD_LOCK_BEHAVIOR, value)
                updated = true
            }
        } catch (_: Exception) {
        }
        try {
            if (hasWriteSecureSettings(context)) {
                Settings.Secure.putString(cr, KEY_FOLD_LOCK_BEHAVIOR, value)
                Settings.Global.putString(cr, KEY_FOLD_LOCK_BEHAVIOR, value)
                updated = true
            }
        } catch (_: Exception) {
        }
        return updated
    }

    fun isFoldCloseStayAwakeEnabled(context: Context): Boolean {
        return try {
            val cr = context.contentResolver
            val sysVal = Settings.System.getString(cr, KEY_FOLD_LOCK_BEHAVIOR)
                ?: Settings.Secure.getString(cr, KEY_FOLD_LOCK_BEHAVIOR)
                ?: Settings.Global.getString(cr, KEY_FOLD_LOCK_BEHAVIOR)
            sysVal == FOLD_STAY_AWAKE_VALUE
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Rebinds Samsung One UI's native bottom Lock Screen shortcuts (`lock_application_shortcut`)
     * to VC (`dev.agentconversation.vci`) on the left and Fold8 Launcher on the right, plus enables
     * Lock Screen landscape rotation and AOD tap-to-show.
     */
    fun setLockScreenShortcutsToVcAndLauncher(context: Context): Boolean {
        val cr = context.contentResolver
        val vcPkg = findInstalledVcPackage(context) ?: VC_PROD_PACKAGE
        val shortcutSpec = "1;$vcPkg/dev.agentconversation.MainActivity;1;${context.packageName}/com.xsun.foldlauncher.MainActivity;"
        var updated = false
        try {
            if (Settings.System.canWrite(context) || hasWriteSecureSettings(context)) {
                Settings.System.putString(cr, KEY_LOCK_APPLICATION_SHORTCUT, shortcutSpec)
                Settings.System.putInt(cr, KEY_AOD_TAP_TO_SHOW_MODE, 1)
                updated = true
            }
        } catch (_: Exception) {
        }
        try {
            if (hasWriteSecureSettings(context)) {
                Settings.Secure.putString(cr, KEY_LOCK_APPLICATION_SHORTCUT, shortcutSpec)
                updated = true
            }
        } catch (_: Exception) {
        }
        setLockScreenRotation(context, true)
        return updated
    }

    fun isLockScreenShortcutsConfiguredForVc(context: Context): Boolean {
        return try {
            val cr = context.contentResolver
            val raw = Settings.System.getString(cr, KEY_LOCK_APPLICATION_SHORTCUT)
                ?: Settings.Secure.getString(cr, KEY_LOCK_APPLICATION_SHORTCUT)
                ?: ""
            raw.contains("agentconversation") || raw.contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    fun getLockScreenStatus(context: Context): LockScreenStatus {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return LockScreenStatus(
            isKeyguardLocked = km?.isKeyguardLocked == true,
            rotationAllowed = isLockScreenRotationEnabled(context),
            faceUnlockInstantBypass = isFaceUnlockInstantBypassEnabled(context),
            foldCloseStayAwake = isFoldCloseStayAwakeEnabled(context),
            lockShortcutsConfiguredForVc = isLockScreenShortcutsConfiguredForVc(context),
            lockScreenHudOverlayEnabled = FoldAccessibilityService.lockScreenHudEnabled.value,
            autoCockpitOnWakeEnabled = FoldAccessibilityService.autoCockpitOnWake.value
        )
    }

    /**
     * Applies all Z Fold 8 Lock Screen & Continuity power-user overrides in one call:
     * 1. Instant Face Unlock bypass (`face_stay_on_lock_screen = 0`)
     * 2. Keep Cover Screen awake when folding shut (`stay_awake_on_fold_key`)
     * 3. Lock Screen landscape auto-rotation (`lock_screen_allow_rotation = 1`)
     * 4. Native Lock Screen corner shortcuts bound to VC + Fold8 Launcher
     * 5. Enables the Lock-Screen HUD Overlay pill (`TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED`)
     */
    fun applyLockScreenPowerPreset(context: Context): Boolean {
        val r1 = setFaceUnlockInstantBypass(context, true)
        val r2 = setFoldCloseStayAwake(context, true)
        val r3 = setLockScreenRotation(context, true)
        val r4 = setLockScreenShortcutsToVcAndLauncher(context)
        FoldAccessibilityService.setLockScreenHudEnabled(context, false)
        return r1 || r2 || r3 || r4
    }

    /**
     * Applies all recommended Z Fold 8 One UI 9.0 system tweaks (Gboard in DeX + Lock Screen Power Preset).
     */
    fun applyRecommendedFold8Tweaks(context: Context): Boolean {
        val imeOk = enforcePreferredIme(context)
        val lockOk = applyLockScreenPowerPreset(context)
        return imeOk || lockOk
    }

    /**
     * Launches `MainActivity` in Show-When-Locked Cockpit Mode right on top of the Lock Screen.
     */
    fun launchLockScreenCockpit(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, "com.xsun.foldlauncher.MainActivity")
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                putExtra(EXTRA_LOCK_SCREEN_COCKPIT, true)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Controls Cover Screen (`sub_display`) orientation lock on Galaxy Z Fold 8 (`SM-F971U1`)
     * independently of the Inner Display when supported by One UI 9.0.
     */
    fun setCoverScreenRotationLock(context: Context, lockedToPortrait: Boolean): Boolean {
        val cr = context.contentResolver
        val autoRotateValue = if (lockedToPortrait) 0 else 1
        var updated = false
        try {
            if (Settings.System.canWrite(context) || hasWriteSecureSettings(context)) {
                Settings.System.putInt(cr, KEY_SUB_DISPLAY_ROTATION_LOCK, autoRotateValue)
                if (lockedToPortrait) {
                    Settings.System.putInt(cr, Settings.System.USER_ROTATION, Surface.ROTATION_0)
                }
                updated = true
            }
        } catch (_: Exception) {
        }
        return updated
    }

    fun isCoverScreenRotationLocked(context: Context): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, KEY_SUB_DISPLAY_ROTATION_LOCK, 1) == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Expands the system notification shade when swiping down anywhere on the home screen.
     */
    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotificationsPanel(context: Context): Boolean {
        val a11y = FoldAccessibilityService.instance
        if (a11y != null && a11y.openNotificationShade()) {
            return true
        }
        return try {
            val statusBarService = context.getSystemService("statusbar") ?: return false
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Locks the screen immediately without pressing the physical power button (requires AccessibilityService).
     */
    fun lockScreenNow(): Boolean {
        return FoldAccessibilityService.instance?.lockScreen() ?: false
    }

    /**
     * Triggers Android split-screen mode via AccessibilityService.
     */
    fun toggleSplitScreen(): Boolean {
        return FoldAccessibilityService.instance?.toggleSplitScreen() ?: false
    }

    /**
     * Launches two apps side-by-side in multi-window / split-screen mode on the Z Fold 8 inner display.
     */
    fun launchSplitScreenPair(
        context: Context,
        primaryPackage: String,
        secondaryPackage: String
    ): Boolean {
        val pm = context.packageManager
        val primaryIntent = pm.getLaunchIntentForPackage(primaryPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false

        val secondaryIntent = pm.getLaunchIntentForPackage(secondaryPackage)?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
        } ?: return false

        return try {
            context.startActivity(primaryIntent)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    context.startActivity(secondaryIntent)
                } catch (_: Exception) {
                }
            }, 180L)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Alias for Z Fold 8 (`SM-F971U1`) dual-pane split-screen launching.
     */
    fun launchDualAppSplit(
        context: Context,
        primaryPackage: String,
        secondaryPackage: String
    ): Boolean = launchSplitScreenPair(context, primaryPackage, secondaryPackage)

    /**
     * Finds the installed VC package (`dev.agentconversation.vci` or `dev.agentconversation.preview`).
     */
    fun findInstalledVcPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in listOf(VC_PROD_PACKAGE, VC_PREVIEW_PACKAGE)) {
            if (pm.getLaunchIntentForPackage(pkg) != null) return pkg
        }
        return null
    }

    /**
     * Launches the installed VC app (`dev.agentconversation.vci` or `dev.agentconversation.preview`),
     * optionally passing session/project extras, or falls back to the VC web UI URL.
     */
    fun launchVc(
        context: Context,
        sessionId: String? = null,
        projectId: String? = null,
        startVoiceTalk: Boolean = false,
        adjacentSplitScreen: Boolean = false
    ): Boolean {
        val vcPkg = findInstalledVcPackage(context)
        if (vcPkg != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(vcPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (adjacentSplitScreen) {
                    addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                if (startVoiceTalk) {
                    action = VC_TALK_ACTION
                }
                if (!sessionId.isNullOrBlank()) {
                    putExtra("sessionId", sessionId)
                    putExtra("focusSession", sessionId)
                }
                if (!projectId.isNullOrBlank()) {
                    putExtra("projectId", projectId)
                }
            }
            if (intent != null) {
                return try {
                    context.startActivity(intent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        return try {
            val url = if (!sessionId.isNullOrBlank()) {
                "$DEFAULT_VC_HOST_URL/?session=$sessionId"
            } else {
                DEFAULT_VC_HOST_URL
            }
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getAdbGrantCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun isDexDesktopModeActive(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_TYPE_MASK
        if (uiMode == android.content.res.Configuration.UI_MODE_TYPE_DESK) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.resources.configuration.smallestScreenWidthDp >= 900
    }
}
