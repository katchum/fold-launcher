package com.xsun.foldlauncher.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.xsun.foldlauncher.display.DualScreenPresentationController
import com.xsun.foldlauncher.posture.PostureClassifier
import com.xsun.foldlauncher.vc.VcBackgroundMonitor
import com.xsun.foldlauncher.vc.VcPackages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lightweight, zero-bloat companion AccessibilityService for Galaxy Z Fold 8 & slab phones.
 *
 * Capabilities:
 * 1. Tracks the active foreground package (`foregroundPackage` & `isVcInForeground`) so the
 *    launcher's VC background monitor knows when `dev.agentconversation.vci` or
 *    `dev.agentconversation.preview` is in the foreground vs. background.
 * 2. Streams the Z Fold 8 hardware hinge angle sensor (`Sensor.TYPE_HINGE_ANGLE = 36`, `0°..180°`)
 *    into `hingeAngleFlow` for the Floating Micro-HUD Pill and posture engine.
 * 3. Renders a high-contrast `TYPE_ACCESSIBILITY_OVERLAY` Lock-Screen HUD Pill with
 *    `FLAG_SHOW_WHEN_LOCKED` on `ACTION_SCREEN_ON` when `KeyguardManager.isKeyguardLocked` is true
 *    (or launches `MainActivity` in Show-When-Locked Cockpit mode if `autoCockpitOnWake` is enabled).
 * 4. Tracks secondary displays (`secondaryDisplayCountFlow`) and refreshes `DualScreenPresentationController`.
 * 5. Watches `Settings.Secure.DEFAULT_INPUT_METHOD` and configuration changes (such as Samsung DeX
 *    connecting/disconnecting) and automatically restores Gboard if One UI forces Samsung Keyboard.
 * 6. Performs global actions on behalf of the launcher:
 *    - Double-tap home screen to lock (`GLOBAL_ACTION_LOCK_SCREEN`)
 *    - Swipe down on home screen to pull down notifications (`GLOBAL_ACTION_NOTIFICATIONS`)
 *    - Toggle split-screen multitasking (`GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN`)
 */
class FoldAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var imeObserver: ContentObserver? = null
    private var sensorManager: SensorManager? = null
    private var hingeSensorListener: SensorEventListener? = null
    private var screenLockReceiver: BroadcastReceiver? = null
    private var displayManager: DisplayManager? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    private var windowManager: WindowManager? = null
    private var lockHudView: View? = null
    private var lockHudClockText: TextView? = null
    private var lockHudMetaText: TextView? = null

    var autoGuardDexKeyboard: Boolean = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        loadLockHudPreferences(this)
        registerImeGuardObserver()
        registerHingeAngleSensor()
        registerScreenLockReceiver()
        registerSecondaryDisplayListener()
        refreshKeyguardAndHudState()
        if (autoGuardDexKeyboard && SystemTweaker.isSamsungKeyboardActive(this)) {
            SystemTweaker.enforcePreferredIme(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // When Samsung DeX connects or display posture changes, guard against Samsung Keyboard hijack
        if (autoGuardDexKeyboard && SystemTweaker.isSamsungKeyboardActive(this)) {
            SystemTweaker.enforcePreferredIme(this)
        }
        refreshKeyguardAndHudState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            if (autoGuardDexKeyboard && pkg == SystemTweaker.SAMSUNG_HONEYBOARD_PACKAGE) {
                SystemTweaker.enforcePreferredIme(this)
                return
            }

            refreshKeyguardAndHudState()

            // Ignore transient system overlays (status bar / volume / keyboard) when tracking foreground app
            if (!IGNORED_OVERLAY_PACKAGES.contains(pkg)) {
                _foregroundPackage.value = pkg
                val vcActive = VcPackages.isVcPackage(pkg) || VC_PACKAGES.contains(pkg)
                if (_isVcInForeground.value != vcActive) {
                    _isVcInForeground.value = vcActive
                    _lastVcStateChangeMs.value = System.currentTimeMillis()
                }
                VcBackgroundMonitor.getInstance(this).onForegroundPackageChanged(pkg)
            }
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        DualScreenPresentationController.dismissSecondaryPresentation()
        removeLockHudOverlay()
        unregisterSecondaryDisplayListener()
        unregisterScreenLockReceiver()
        unregisterHingeAngleSensor()
        unregisterImeGuardObserver()
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun lockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    fun openNotificationShade(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun openQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun toggleSplitScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        } else {
            false
        }
    }

    private fun registerSecondaryDisplayListener() {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                updateSecondaryDisplayState()
            }

            override fun onDisplayRemoved(displayId: Int) {
                updateSecondaryDisplayState()
            }

            override fun onDisplayChanged(displayId: Int) {
                updateSecondaryDisplayState()
            }
        }
        try {
            dm.registerDisplayListener(listener, mainHandler)
            displayManager = dm
            displayListener = listener
            updateSecondaryDisplayState()
        } catch (_: Exception) {
        }
    }

    private fun unregisterSecondaryDisplayListener() {
        val dm = displayManager
        val listener = displayListener
        if (dm != null && listener != null) {
            try {
                dm.unregisterDisplayListener(listener)
            } catch (_: Exception) {
            }
        }
        displayManager = null
        displayListener = null
    }

    private fun updateSecondaryDisplayState() {
        val secondaries = DualScreenPresentationController.getSecondaryDisplays(this)
        _secondaryDisplayCountFlow.value = secondaries.size
        if (secondaries.isEmpty() && DualScreenPresentationController.isShowing()) {
            DualScreenPresentationController.dismissSecondaryPresentation()
        } else if (DualScreenPresentationController.isShowing()) {
            DualScreenPresentationController.refreshActivePresentation()
        }
    }

    private fun registerScreenLockReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_TICK)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                refreshKeyguardAndHudState()
                DualScreenPresentationController.refreshActivePresentation()
                if (action == Intent.ACTION_SCREEN_ON) {
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (km?.isKeyguardLocked == true && _autoCockpitOnWake.value) {
                        SystemTweaker.launchLockScreenCockpit(this@FoldAccessibilityService)
                    }
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            screenLockReceiver = receiver
        } catch (_: Exception) {
        }
    }

    private fun unregisterScreenLockReceiver() {
        val receiver = screenLockReceiver ?: return
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        screenLockReceiver = null
    }

    internal fun refreshKeyguardAndHudState() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val locked = km?.isKeyguardLocked == true
        _isKeyguardLocked.value = locked

        val shouldShowOverlay = _alwaysShowMicroHud.value || (locked && _lockScreenHudEnabled.value)
        if (shouldShowOverlay) {
            ensureLockHudOverlay()
            updateLockHudText()
        } else {
            removeLockHudOverlay()
        }
    }

    @Suppress("DEPRECATION")
    private fun ensureLockHudOverlay() {
        if (lockHudView != null) return
        val wm = (windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
        windowManager = wm

        val density = resources.displayMetrics.density
        val padH = (14 * density).toInt()
        val padV = (6 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(Color.parseColor("#E611171C"))
                setStroke((1.2f * density).toInt().coerceAtLeast(1), Color.parseColor("#66B9E6C8"))
            }
            setOnClickListener {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (km?.isKeyguardLocked == true) {
                    SystemTweaker.launchLockScreenCockpit(this@FoldAccessibilityService)
                } else {
                    SystemTweaker.launchVc(this@FoldAccessibilityService)
                }
            }
        }

        val metaTv = TextView(this).apply {
            setTextColor(Color.parseColor("#FFB9E6C8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, 0, (10 * density).toInt(), 0)
        }

        val clockTv = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        container.addView(metaTv)
        container.addView(clockTv)

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt()
            y = (10 * density).toInt()
            title = "Fold8LockScreenMicroHud"
        }

        try {
            wm.addView(container, lp)
            lockHudView = container
            lockHudClockText = clockTv
            lockHudMetaText = metaTv
        } catch (_: Exception) {
        }
    }

    private fun updateLockHudText() {
        val clockTv = lockHudClockText ?: return
        val metaTv = lockHudMetaText ?: return

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        clockTv.text = timeStr

        val anglePart = _hingeAngleFlow.value?.let { "${it.roundToInt()}°" } ?: "FOLD8"
        val vcState = try {
            VcBackgroundMonitor.getInstance(this).uiState.value
        } catch (_: Exception) {
            null
        }
        val vcPart = when {
            vcState == null -> "VC"
            vcState.approvalWaitingCount > 0 -> "VC !${vcState.approvalWaitingCount}"
            vcState.workingCount > 0 -> "VC ⚙${vcState.workingCount}"
            vcState.unreadUpdateCount > 0 -> "VC +${vcState.unreadUpdateCount}"
            else -> "VC OK"
        }
        metaTv.text = "$anglePart · $vcPart"
    }

    private fun removeLockHudOverlay() {
        val view = lockHudView ?: return
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
        try {
            wm?.removeView(view)
        } catch (_: Exception) {
        }
        lockHudView = null
        lockHudClockText = null
        lockHudMetaText = null
    }

    private fun registerHingeAngleSensor() {
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(PostureClassifier.TYPE_HINGE_ANGLE_SENSOR) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val raw = event?.values?.firstOrNull() ?: return
                val clamped = raw.coerceIn(0f, 180f)
                val current = _hingeAngleFlow.value
                if (current == null || abs(clamped - current) >= 0.5f) {
                    _hingeAngleFlow.value = clamped
                    if (lockHudView != null) {
                        updateLockHudText()
                    }
                    DualScreenPresentationController.refreshActivePresentation()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        try {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager = sm
            hingeSensorListener = listener
        } catch (_: Exception) {
        }
    }

    private fun unregisterHingeAngleSensor() {
        val sm = sensorManager
        val listener = hingeSensorListener
        if (sm != null && listener != null) {
            try {
                sm.unregisterListener(listener)
            } catch (_: Exception) {
            }
        }
        sensorManager = null
        hingeSensorListener = null
    }

    private fun registerImeGuardObserver() {
        val uri: Uri = Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD) ?: return
        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (autoGuardDexKeyboard && SystemTweaker.isSamsungKeyboardActive(this@FoldAccessibilityService)) {
                    SystemTweaker.enforcePreferredIme(this@FoldAccessibilityService)
                }
            }
        }
        try {
            contentResolver.registerContentObserver(uri, false, observer)
            imeObserver = observer
        } catch (_: Exception) {
        }
    }

    private fun unregisterImeGuardObserver() {
        val observer = imeObserver ?: return
        try {
            contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
        }
        imeObserver = null
    }

    companion object {
        private const val PREFS_NAME = "fold8_lock_hud_prefs"
        private const val KEY_LOCK_HUD_ENABLED = "lock_screen_hud_enabled"
        private const val KEY_ALWAYS_MICRO_HUD = "always_show_micro_hud"
        private const val KEY_AUTO_COCKPIT_ON_WAKE = "auto_cockpit_on_wake"

        val VC_PACKAGES = setOf(
            "dev.agentconversation",
            "dev.agentconversation.preview",
            "dev.agentconversation.preview.preflight",
            "dev.agentconversation.vci",
            "com.example.agentconversation"
        )

        private val IGNORED_OVERLAY_PACKAGES = setOf(
            "com.android.systemui",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin",
            "com.touchtype.swiftkey",
            "android"
        )

        @Volatile
        var instance: FoldAccessibilityService? = null
            private set

        private val _foregroundPackage = MutableStateFlow("")
        val foregroundPackage: StateFlow<String> = _foregroundPackage.asStateFlow()

        private val _isVcInForeground = MutableStateFlow(false)
        val isVcInForeground: StateFlow<Boolean> = _isVcInForeground.asStateFlow()

        private val _lastVcStateChangeMs = MutableStateFlow(0L)
        val lastVcStateChangeMs: StateFlow<Long> = _lastVcStateChangeMs.asStateFlow()

        private val _hingeAngleFlow = MutableStateFlow<Float?>(null)
        val hingeAngleFlow: StateFlow<Float?> = _hingeAngleFlow.asStateFlow()

        private val _secondaryDisplayCountFlow = MutableStateFlow(0)
        val secondaryDisplayCountFlow: StateFlow<Int> = _secondaryDisplayCountFlow.asStateFlow()

        private val _isKeyguardLocked = MutableStateFlow(false)
        val isKeyguardLocked: StateFlow<Boolean> = _isKeyguardLocked.asStateFlow()

        private val _lockScreenHudEnabled = MutableStateFlow(true)
        val lockScreenHudEnabled: StateFlow<Boolean> = _lockScreenHudEnabled.asStateFlow()

        private val _alwaysShowMicroHud = MutableStateFlow(false)
        val alwaysShowMicroHud: StateFlow<Boolean> = _alwaysShowMicroHud.asStateFlow()

        private val _autoCockpitOnWake = MutableStateFlow(false)
        val autoCockpitOnWake: StateFlow<Boolean> = _autoCockpitOnWake.asStateFlow()

        fun loadLockHudPreferences(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _lockScreenHudEnabled.value = prefs.getBoolean(KEY_LOCK_HUD_ENABLED, true)
            _alwaysShowMicroHud.value = prefs.getBoolean(KEY_ALWAYS_MICRO_HUD, false)
            _autoCockpitOnWake.value = prefs.getBoolean(KEY_AUTO_COCKPIT_ON_WAKE, false)
        }

        fun setLockScreenHudEnabled(context: Context, enabled: Boolean) {
            _lockScreenHudEnabled.value = enabled
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LOCK_HUD_ENABLED, enabled)
                .apply()
            instance?.refreshKeyguardAndHudState()
        }

        fun setAlwaysShowMicroHud(context: Context, enabled: Boolean) {
            _alwaysShowMicroHud.value = enabled
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ALWAYS_MICRO_HUD, enabled)
                .apply()
            instance?.refreshKeyguardAndHudState()
        }

        fun setAutoCockpitOnWake(context: Context, enabled: Boolean) {
            _autoCockpitOnWake.value = enabled
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_COCKPIT_ON_WAKE, enabled)
                .apply()
        }

        fun notifyLauncherResumed(packageName: String) {
            _foregroundPackage.value = packageName
            if (_isVcInForeground.value) {
                _isVcInForeground.value = false
                _lastVcStateChangeMs.value = System.currentTimeMillis()
            }
            instance?.refreshKeyguardAndHudState()
        }
    }
}
