package com.xsun.foldlauncher.hud

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as ComposeCanvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.MainActivity
import com.xsun.foldlauncher.service.FoldAccessibilityService
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * =================================================================================================
 * SYSTEM-WIDE FLOATING MICRO-HUD PILL & ANYWHERE-SPOTLIGHT TRIGGER (`com.xsun.foldlauncher.hud`)
 * =================================================================================================
 *
 * ### Why this is IMPOSSIBLE on Stock Samsung One UI Home:
 * 1. **Stock Launchers Disappear Inside Apps & Samsung DeX**: When you open any full-screen app,
 *    browser, terminal, or game on Galaxy Z Fold 8, stock One UI Home is pushed to the background.
 *    You are left with Samsung's tiny status-bar clock (or no status bar at all in immersive apps),
 *    zero battery thermal visibility, and no live indicator of your background VC coding agents.
 * 2. **Zero-Latency Anywhere-Spotlight Trigger**: On stock One UI Home, searching for an app,
 *    evaluating inline math, or switching to a VC agent requires swiping home, waiting for the
 *    launcher transition animation, and tapping the finder bar. Our `TYPE_ACCESSIBILITY_OVERLAY`
 *    Micro-HUD Pill floats persistently at the top-right edge across *every* app and DeX window:
 *    - **Single Tap**: Immediately brings `MainActivity` to the front with
 *      `EXTRA_OPEN_SPOTLIGHT = true` for **0ms Anywhere-Spotlight Search** from inside any app!
 *    - **Long Press**: Launches/switches directly to the VC Agent app
 *      (`dev.agentconversation.vci` / `com.example.agentconversation` / VC web UI) or triggers
 *      instant Split-Screen (`FoldAccessibilityService.instance?.toggleSplitScreen()`).
 *    - **Horizontal Drag**: Smoothly slides the Micro-HUD Pill along the top screen edge and
 *      persists its exact X offset in `SharedPreferences`.
 * 3. **Zero-Overhead Custom `Canvas` View**: Built as a hardware-accelerated custom `View` attached
 *    directly to `FoldAccessibilityService`'s `WindowManager` (`TYPE_ACCESSIBILITY_OVERLAY`),
 *    requiring zero `Activity` lifecycle overhead or extra permissions beyond the companion
 *    accessibility service.
 */

const val EXTRA_OPEN_SPOTLIGHT = "EXTRA_OPEN_SPOTLIGHT"

/**
 * Live VC Agent status states rendered as a high-contrast pulsing status dot on the Micro-HUD Pill.
 */
enum class VcHudAgentStatus(
    val label: String,
    val shortBadge: String,
    val colorArgb: Int,
    val composeColor: Color,
    val pulses: Boolean
) {
    WORKING(
        label = "VC Working (Pulsing Cyan)",
        shortBadge = "WORKING",
        colorArgb = 0xFF38BDF8.toInt(),
        composeColor = FoldLauncherColors.ElectricCyan,
        pulses = true
    ),
    APPROVAL_NEEDED(
        label = "Approval Needed (Amber)",
        shortBadge = "APPROVAL",
        colorArgb = 0xFFFBBF24.toInt(),
        composeColor = FoldLauncherColors.AmberWarning,
        pulses = true
    ),
    ERROR(
        label = "Agent Error (Coral Red)",
        shortBadge = "ERROR",
        colorArgb = 0xFFF87171.toInt(),
        composeColor = FoldLauncherColors.CoralDanger,
        pulses = true
    ),
    IDLE_READY(
        label = "Idle / Ready (Emerald)",
        shortBadge = "READY",
        colorArgb = 0xFF34D399.toInt(),
        composeColor = FoldLauncherColors.EmeraldSuccess,
        pulses = false
    )
}

/**
 * Action executed when long-pressing the System-Wide Floating Micro-HUD Pill.
 */
enum class HudLongPressAction(val label: String, val description: String) {
    LAUNCH_VC_OR_SPLIT(
        label = "VC App / Split-Screen",
        description = "Switches to VC Agent app (or toggles Split-Screen if already in VC)"
    ),
    TOGGLE_SPLIT_SCREEN(
        label = "Toggle Split-Screen",
        description = "Immediately docks the current foreground app into Fold 8 Split-Screen"
    ),
    OPEN_VC_DIRECT(
        label = "Open VC Console",
        description = "Launches VC Agent app or falls back to VC Web UI"
    )
}

/**
 * Snapshot of live telemetry & user configuration for the System-Wide Micro-HUD Pill.
 */
data class FloatingHudUiState(
    val isEnabled: Boolean = false,
    val isOverlayAttached: Boolean = false,
    val opacity: Float = 0.92f,
    val scaleFactor: Float = 1.0f,
    val offsetXPx: Int = 24,
    val offsetYPx: Int = 14,
    val batteryPct: Int = 88,
    val batteryTempCelsius: Float = 31.0f,
    val isCharging: Boolean = false,
    val vcStatus: VcHudAgentStatus = VcHudAgentStatus.WORKING,
    val longPressAction: HudLongPressAction = HudLongPressAction.LAUNCH_VC_OR_SPLIT
)

/**
 * System-Wide `TYPE_ACCESSIBILITY_OVERLAY` Micro-HUD Controller (`FloatingHudController`).
 *
 * Attaches a sleek, compact, draggable top-right pill view (`FloatingMicroHudPillView`) using
 * `WindowManager` from `FoldAccessibilityService` (`WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`,
 * `FLAG_NOT_FOCUSABLE`, `FLAG_LAYOUT_IN_SCREEN`, `PixelFormat.TRANSLUCENT`, `Gravity.TOP or Gravity.END`).
 */
object FloatingHudController {

    private const val PREFS_NAME = "fold_launcher_hud_prefs"
    private const val KEY_HUD_ENABLED = "hud_enabled"
    private const val KEY_HUD_OPACITY = "hud_opacity"
    private const val KEY_HUD_SCALE = "hud_scale"
    private const val KEY_HUD_OFFSET_X = "hud_offset_x_px"
    private const val KEY_HUD_OFFSET_Y = "hud_offset_y_px"
    private const val KEY_HUD_VC_STATUS = "hud_vc_status"
    private const val KEY_HUD_LONG_PRESS = "hud_long_press_action"

    private const val DEFAULT_VC_WEB_URL = "http://127.0.0.1:8080"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null

    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var pillView: FloatingMicroHudPillView? = null
    private var attachedServiceContext: Context? = null

    private var telemetryRegistered = false
    private var telemetryContext: Context? = null

    private val _state = MutableStateFlow(FloatingHudUiState())
    val state: StateFlow<FloatingHudUiState> = _state.asStateFlow()

    private val clockTickRunnable = object : Runnable {
        override fun run() {
            pillView?.invalidate()
            mainHandler.postDelayed(this, 10_000L)
        }
    }

    private val batteryThermalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) {
                ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
            } else {
                _state.value.batteryPct
            }

            // BatteryManager.EXTRA_TEMPERATURE is in tenths of a degree Celsius (e.g., 312 -> 31.2°C)
            val rawTenthsTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 310)
            val tempC = if (rawTenthsTemp in 50..850) {
                rawTenthsTemp / 10f
            } else {
                31.0f
            }

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            _state.value = _state.value.copy(
                batteryPct = pct,
                batteryTempCelsius = tempC,
                isCharging = charging
            )
            pillView?.updateTelemetry(
                batteryPct = pct,
                tempCelsius = tempC,
                isCharging = charging
            )
        }
    }

    /**
     * Initializes preferences and battery/thermal telemetry readers. Safe to call multiple times.
     */
    fun ensureInitialized(context: Context) {
        val appCtx = context.applicationContext
        if (prefs == null) {
            val sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = sp
            val savedEnabled = sp.getBoolean(KEY_HUD_ENABLED, false)
            val savedOpacity = sp.getFloat(KEY_HUD_OPACITY, 0.92f).coerceIn(0.45f, 1.0f)
            val savedScale = sp.getFloat(KEY_HUD_SCALE, 1.0f).coerceIn(0.80f, 1.35f)
            val savedOffsetX = sp.getInt(KEY_HUD_OFFSET_X, dpToPx(appCtx, 18f))
            val savedOffsetY = sp.getInt(KEY_HUD_OFFSET_Y, dpToPx(appCtx, 10f))
            val savedStatus = runCatching {
                VcHudAgentStatus.valueOf(
                    sp.getString(KEY_HUD_VC_STATUS, VcHudAgentStatus.WORKING.name)
                        ?: VcHudAgentStatus.WORKING.name
                )
            }.getOrDefault(VcHudAgentStatus.WORKING)
            val savedLongPress = runCatching {
                HudLongPressAction.valueOf(
                    sp.getString(KEY_HUD_LONG_PRESS, HudLongPressAction.LAUNCH_VC_OR_SPLIT.name)
                        ?: HudLongPressAction.LAUNCH_VC_OR_SPLIT.name
                )
            }.getOrDefault(HudLongPressAction.LAUNCH_VC_OR_SPLIT)

            _state.value = _state.value.copy(
                isEnabled = savedEnabled,
                opacity = savedOpacity,
                scaleFactor = savedScale,
                offsetXPx = savedOffsetX,
                offsetYPx = savedOffsetY,
                vcStatus = savedStatus,
                longPressAction = savedLongPress
            )
        }

        if (!telemetryRegistered) {
            try {
                val sticky = appCtx.registerReceiver(
                    batteryThermalReceiver,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                if (sticky != null) {
                    batteryThermalReceiver.onReceive(appCtx, sticky)
                }
                telemetryContext = appCtx
                telemetryRegistered = true
            } catch (_: Exception) {
            }
        }

        if (_state.value.isEnabled && pillView == null) {
            syncOverlayWithService()
        }
    }

    /**
     * Enables or disables the System-Wide `TYPE_ACCESSIBILITY_OVERLAY` Top-Right Micro-HUD Pill.
     * Returns `true` if the overlay is now attached (or disabled cleanly), or `false` if enabling
     * required `FoldAccessibilityService` which is not currently active.
     */
    fun setHudEnabled(context: Context, enabled: Boolean): Boolean {
        ensureInitialized(context)
        prefs?.edit()?.putBoolean(KEY_HUD_ENABLED, enabled)?.apply()
        _state.value = _state.value.copy(isEnabled = enabled)

        return if (enabled) {
            attachOverlayIfPossible()
        } else {
            detachOverlay()
            true
        }
    }

    fun setOpacity(context: Context, opacity: Float) {
        ensureInitialized(context)
        val clamped = opacity.coerceIn(0.45f, 1.0f)
        prefs?.edit()?.putFloat(KEY_HUD_OPACITY, clamped)?.apply()
        _state.value = _state.value.copy(opacity = clamped)
        pillView?.updateAppearance(opacity = clamped, scaleFactor = _state.value.scaleFactor)
        updateWindowLayoutIfAttached()
    }

    fun setScaleFactor(context: Context, scale: Float) {
        ensureInitialized(context)
        val clamped = scale.coerceIn(0.80f, 1.35f)
        prefs?.edit()?.putFloat(KEY_HUD_SCALE, clamped)?.apply()
        _state.value = _state.value.copy(scaleFactor = clamped)
        pillView?.updateAppearance(opacity = _state.value.opacity, scaleFactor = clamped)
        updateWindowLayoutIfAttached()
    }

    fun setVcAgentStatus(context: Context, status: VcHudAgentStatus) {
        ensureInitialized(context)
        prefs?.edit()?.putString(KEY_HUD_VC_STATUS, status.name)?.apply()
        _state.value = _state.value.copy(vcStatus = status)
        pillView?.updateVcStatus(status)
    }

    fun setLongPressAction(context: Context, action: HudLongPressAction) {
        ensureInitialized(context)
        prefs?.edit()?.putString(KEY_HUD_LONG_PRESS, action.name)?.apply()
        _state.value = _state.value.copy(longPressAction = action)
    }

    fun resetPillPosition(context: Context) {
        ensureInitialized(context)
        val defaultX = dpToPx(context, 18f)
        val defaultY = dpToPx(context, 10f)
        savePillOffsets(defaultX, defaultY)
        layoutParams?.let { lp ->
            lp.x = defaultX
            lp.y = defaultY
            updateWindowLayoutIfAttached()
        }
    }

    /**
     * Synchronizes the overlay attachment with `FoldAccessibilityService.instance`.
     * Can be called from `FoldAccessibilityService` or from the Launcher UI.
     */
    fun syncOverlayWithService(service: AccessibilityService? = FoldAccessibilityService.instance): Boolean {
        val activeService = service ?: FoldAccessibilityService.instance
        if (activeService == null) {
            detachOverlay()
            return false
        }
        ensureInitialized(activeService)
        return if (_state.value.isEnabled) {
            attachOverlayIfPossible(activeService)
        } else {
            detachOverlay()
            false
        }
    }

    /**
     * Attaches the `TYPE_ACCESSIBILITY_OVERLAY` window to `FoldAccessibilityService`'s `WindowManager`.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachOverlayIfPossible(
        service: AccessibilityService? = FoldAccessibilityService.instance
    ): Boolean {
        val hostService = service ?: FoldAccessibilityService.instance ?: run {
            _state.value = _state.value.copy(isOverlayAttached = false)
            return false
        }

        ensureInitialized(hostService)

        if (pillView != null && attachedServiceContext === hostService) {
            _state.value = _state.value.copy(isOverlayAttached = true)
            return true
        }

        detachOverlay()

        return try {
            val wm = hostService.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val current = _state.value

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = current.offsetXPx
                y = current.offsetYPx
                title = "Fold8FloatingMicroHudPill"
            }

            val view = FloatingMicroHudPillView(
                context = hostService,
                initialState = current,
                onSingleTap = {
                    triggerAnywhereSpotlight(hostService)
                },
                onLongPress = {
                    triggerLongPressAction(hostService)
                },
                onHorizontalDrag = { deltaXPx ->
                    // With Gravity.END, x is distance from right edge: dragging left increases x
                    val displayWidth = hostService.resources.displayMetrics.widthPixels
                    val maxOffset = (displayWidth - (pillView?.measuredWidth ?: dpToPx(hostService, 140f)))
                        .coerceAtLeast(dpToPx(hostService, 8f))
                    val updatedX = (lp.x - deltaXPx).roundToInt().coerceIn(0, maxOffset)
                    if (updatedX != lp.x) {
                        lp.x = updatedX
                        try {
                            wm.updateViewLayout(pillView, lp)
                        } catch (_: Exception) {
                        }
                        savePillOffsets(lp.x, lp.y)
                    }
                }
            )

            wm.addView(view, lp)
            windowManager = wm
            layoutParams = lp
            pillView = view
            attachedServiceContext = hostService
            _state.value = _state.value.copy(isOverlayAttached = true)

            mainHandler.removeCallbacks(clockTickRunnable)
            mainHandler.post(clockTickRunnable)
            true
        } catch (_: Exception) {
            _state.value = _state.value.copy(isOverlayAttached = false)
            false
        }
    }

    /**
     * Cleanly removes the overlay view from `WindowManager`.
     */
    fun detachOverlay() {
        mainHandler.removeCallbacks(clockTickRunnable)
        val view = pillView
        val wm = windowManager
        if (view != null && wm != null) {
            try {
                view.stopPulseAnimation()
                wm.removeViewImmediate(view)
            } catch (_: Exception) {
                try {
                    wm.removeView(view)
                } catch (_: Exception) {
                }
            }
        }
        pillView = null
        windowManager = null
        layoutParams = null
        attachedServiceContext = null
        _state.value = _state.value.copy(isOverlayAttached = false)
    }

    /**
     * **Single Tap on System-Wide Pill**:
     * Brings `MainActivity` to the front with `intent.putExtra("EXTRA_OPEN_SPOTLIGHT", true)`
     * so the user gets **0ms Spotlight Search from inside ANY app or Samsung DeX**!
     */
    fun triggerAnywhereSpotlight(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                putExtra(EXTRA_OPEN_SPOTLIGHT, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /**
     * **Long Press on System-Wide Pill**:
     * Launches or switches directly to the VC app (`com.example.agentconversation` /
     * `dev.agentconversation.vci` / `dev.agentconversation.preview` / VC web UI) or toggles
     * Split-Screen (`FoldAccessibilityService.instance?.toggleSplitScreen()`).
     */
    fun triggerLongPressAction(context: Context) {
        when (_state.value.longPressAction) {
            HudLongPressAction.TOGGLE_SPLIT_SCREEN -> {
                val toggled = FoldAccessibilityService.instance?.toggleSplitScreen() ?: false
                if (!toggled) {
                    launchOrSwitchToVcApp(context)
                }
            }

            HudLongPressAction.OPEN_VC_DIRECT -> {
                launchOrSwitchToVcApp(context)
            }

            HudLongPressAction.LAUNCH_VC_OR_SPLIT -> {
                val isAlreadyInVc = FoldAccessibilityService.isVcInForeground.value
                if (isAlreadyInVc) {
                    val splitOk = FoldAccessibilityService.instance?.toggleSplitScreen() ?: false
                    if (!splitOk) {
                        launchOrSwitchToVcApp(context)
                    }
                } else {
                    val launchedVc = launchOrSwitchToVcApp(context)
                    if (!launchedVc) {
                        FoldAccessibilityService.instance?.toggleSplitScreen()
                    }
                }
            }
        }
    }

    /**
     * Attempts to launch installed VC Agent packages (`com.example.agentconversation`,
     * `dev.agentconversation.vci`, `dev.agentconversation.preview`), or falls back to opening
     * the VC Web UI (`http://127.0.0.1:8080`).
     */
    fun launchOrSwitchToVcApp(context: Context): Boolean {
        val pm = context.packageManager
        val candidatePackages = listOf(
            "com.example.agentconversation",
            "dev.agentconversation.vci",
            "dev.agentconversation.preview"
        )
        for (pkg in candidatePackages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                return try {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    context.startActivity(launchIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        // Fallback to VC Web UI in browser
        return try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(DEFAULT_VC_WEB_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun savePillOffsets(xPx: Int, yPx: Int) {
        prefs?.edit()
            ?.putInt(KEY_HUD_OFFSET_X, xPx)
            ?.putInt(KEY_HUD_OFFSET_Y, yPx)
            ?.apply()
        _state.value = _state.value.copy(offsetXPx = xPx, offsetYPx = yPx)
    }

    private fun updateWindowLayoutIfAttached() {
        val view = pillView ?: return
        val wm = windowManager ?: return
        val lp = layoutParams ?: return
        try {
            view.requestLayout()
            wm.updateViewLayout(view, lp)
        } catch (_: Exception) {
        }
    }

    internal fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).roundToInt()
    }
}

/**
 * Zero-overhead custom `Canvas`/`View` that renders the System-Wide Top-Right Micro-HUD Pill
 * inside `FoldAccessibilityService` without requiring an `Activity` or `LifecycleOwner`.
 *
 * Displays:
 * - **Live VC Agent Status Dot** (pulsing Cyan when VC agents are working, Amber when approval
 *   is needed, Red on error, Emerald when idle/ready)
 * - **High-Contrast Tabular Clock** (e.g., `18:40`)
 * - **Live Battery & Thermal Readout** (e.g., `88% · 31°C`)
 * - **Subtle Spotlight Search Magnifier Glyph** indicating 1-tap Anywhere-Spotlight
 */
internal class FloatingMicroHudPillView(
    context: Context,
    initialState: FloatingHudUiState,
    private val onSingleTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onHorizontalDrag: (deltaXPx: Float) -> Unit
) : View(context) {

    private var pillOpacity: Float = initialState.opacity
    private var pillScale: Float = initialState.scaleFactor
    private var batteryPct: Int = initialState.batteryPct
    private var tempCelsius: Float = initialState.batteryTempCelsius
    private var isCharging: Boolean = initialState.isCharging
    private var vcStatus: VcHudAgentStatus = initialState.vcStatus

    // Pulse animation phase [0f..1f] for the VC Agent Status Dot
    private var pulsePhase: Float = 0.5f
    private var pulseAnimator: ValueAnimator? = null

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()

    private val pillRect = RectF()
    private val borderRect = RectF()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = 0x5538BDF8
    }

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
        color = 0x33CBD5E1
    }

    private val dotCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF8FAFC.toInt()
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        fontFeatureSettings = "tnum"
    }

    private val telemetryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCBD5E1.toInt()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        fontFeatureSettings = "tnum"
    }

    private val magnifierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38BDF8.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var isDragging = false
    private var longPressTriggered = false

    private val longPressRunnable = Runnable {
        if (!isDragging) {
            longPressTriggered = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress()
        }
    }

    init {
        isClickable = true
        isLongClickable = true
        updatePaintsForScale()
        syncPulseAnimator()
    }

    fun updateAppearance(opacity: Float, scaleFactor: Float) {
        pillOpacity = opacity.coerceIn(0.45f, 1.0f)
        pillScale = scaleFactor.coerceIn(0.80f, 1.35f)
        updatePaintsForScale()
        requestLayout()
        invalidate()
    }

    fun updateTelemetry(batteryPct: Int, tempCelsius: Float, isCharging: Boolean) {
        this.batteryPct = batteryPct
        this.tempCelsius = tempCelsius
        this.isCharging = isCharging
        requestLayout()
        invalidate()
    }

    fun updateVcStatus(status: VcHudAgentStatus) {
        this.vcStatus = status
        syncPulseAnimator()
        invalidate()
    }

    fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    private fun updatePaintsForScale() {
        clockPaint.textSize = 13.5f * scaledDensity * pillScale
        telemetryPaint.textSize = 11.5f * scaledDensity * pillScale
        magnifierPaint.strokeWidth = 1.6f * density * pillScale
        borderPaint.strokeWidth = 1.15f * density * pillScale
    }

    private fun syncPulseAnimator() {
        pulseAnimator?.cancel()
        if (vcStatus.pulses) {
            pulseAnimator = ValueAnimator.ofFloat(0.2f, 1.0f).apply {
                duration = 1150L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    pulsePhase = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            pulsePhase = 0.6f
        }
    }

    private fun formatClockText(): String {
        val is24h = DateFormat.is24HourFormat(context)
        val pattern = if (is24h) "HH:mm" else "h:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }

    private fun formatTelemetryText(): String {
        val tempInt = tempCelsius.roundToInt()
        val chargePrefix = if (isCharging) "⚡" else ""
        return "$chargePrefix${batteryPct}% · ${tempInt}°C"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updatePaintsForScale()
        val clockStr = formatClockText()
        val telemetryStr = formatTelemetryText()

        val padH = 11f * density * pillScale
        val padV = 6f * density * pillScale
        val dotRegionW = 14f * density * pillScale
        val gap = 7f * density * pillScale
        val dividerGap = 8f * density * pillScale
        val searchIconW = 13f * density * pillScale

        val clockW = clockPaint.measureText(clockStr)
        val telemetryW = telemetryPaint.measureText(telemetryStr)

        val totalWidth = (padH * 2f + dotRegionW + gap + clockW + dividerGap + telemetryW + gap + searchIconW)
            .roundToInt()
            .coerceAtLeast((136f * density * pillScale).roundToInt())
        val totalHeight = (padV * 2f + 16f * density * pillScale)
            .roundToInt()
            .coerceAtLeast((28f * density * pillScale).roundToInt())

        setMeasuredDimension(totalWidth, totalHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = borderPaint.strokeWidth * 0.5f
        pillRect.set(0f, 0f, w.toFloat(), h.toFloat())
        borderRect.set(inset, inset, w - inset, h - inset)

        bgPaint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            h.toFloat(),
            intArrayOf(
                0xEE0B101D.toInt(),
                0xE6141C2E.toInt()
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cornerRadius = h * 0.5f
        val alphaInt = (pillOpacity * 255f).roundToInt().coerceIn(100, 255)

        bgPaint.alpha = alphaInt
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, bgPaint)

        borderPaint.color = if (vcStatus == VcHudAgentStatus.ERROR) {
            0x88F87171.toInt()
        } else if (vcStatus == VcHudAgentStatus.APPROVAL_NEEDED) {
            0x88FBBF24.toInt()
        } else {
            0x6638BDF8
        }
        borderPaint.alpha = alphaInt
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

        val centerY = h * 0.5f
        val padH = 11f * density * pillScale
        val gap = 7f * density * pillScale
        val dividerGap = 8f * density * pillScale

        // 1. Live VC Agent Status Dot (pulsing halo + crisp core)
        val dotCenterX = padH + 5.5f * density * pillScale
        val baseDotRadius = 3.8f * density * pillScale
        val haloRadius = baseDotRadius * (1.25f + 0.85f * pulsePhase)

        val statusRgb = vcStatus.colorArgb and 0x00FFFFFF
        val haloAlpha = ((0.18f + 0.30f * pulsePhase) * pillOpacity * 255f).roundToInt().coerceIn(0, 255)
        dotHaloPaint.color = (haloAlpha shl 24) or statusRgb
        canvas.drawCircle(dotCenterX, centerY, haloRadius, dotHaloPaint)

        dotCorePaint.color = (alphaInt shl 24) or statusRgb
        canvas.drawCircle(dotCenterX, centerY, baseDotRadius, dotCorePaint)

        var cursorX = padH + 13f * density * pillScale + gap

        // 2. High-Contrast Tabular Clock (e.g., "18:40")
        val clockStr = formatClockText()
        clockPaint.alpha = alphaInt
        val clockMetrics = clockPaint.fontMetrics
        val clockBaseline = centerY - (clockMetrics.ascent + clockMetrics.descent) * 0.5f
        canvas.drawText(clockStr, cursorX, clockBaseline, clockPaint)
        cursorX += clockPaint.measureText(clockStr) + dividerGap * 0.5f

        // Subtle vertical divider
        val divTop = h * 0.26f
        val divBottom = h * 0.74f
        dividerPaint.alpha = (alphaInt * 0.45f).roundToInt()
        canvas.drawLine(cursorX, divTop, cursorX, divBottom, dividerPaint)
        cursorX += dividerGap * 0.5f

        // 3. Live Battery & Thermal Readout (e.g., "88% · 31°C")
        val telemetryStr = formatTelemetryText()
        telemetryPaint.color = when {
            tempCelsius >= 41.0f || batteryPct <= 15 -> 0xFFF87171.toInt()
            tempCelsius >= 37.5f || batteryPct <= 25 -> 0xFFFBBF24.toInt()
            isCharging -> 0xFF6EE7B7.toInt()
            else -> 0xFFCBD5E1.toInt()
        }
        telemetryPaint.alpha = alphaInt
        val teleMetrics = telemetryPaint.fontMetrics
        val teleBaseline = centerY - (teleMetrics.ascent + teleMetrics.descent) * 0.5f
        canvas.drawText(telemetryStr, cursorX, teleBaseline, telemetryPaint)
        cursorX += telemetryPaint.measureText(telemetryStr) + gap

        // 4. Mini Spotlight Magnifier Glyph on the right edge
        val iconRadius = 3.8f * density * pillScale
        val iconCenterX = (w - padH - iconRadius * 1.1f).coerceAtLeast(cursorX + iconRadius)
        val iconCenterY = centerY - 0.8f * density * pillScale
        magnifierPaint.color = AndroidColor.argb(alphaInt, 0x38, 0xBD, 0xF8)
        canvas.drawCircle(iconCenterX, iconCenterY, iconRadius, magnifierPaint)
        val handleStartOffset = iconRadius * 0.72f
        val handleEndOffset = iconRadius * 1.55f
        canvas.drawLine(
            iconCenterX + handleStartOffset,
            iconCenterY + handleStartOffset,
            iconCenterX + handleEndOffset,
            iconCenterY + handleEndOffset,
            magnifierPaint
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                isDragging = false
                longPressTriggered = false
                handler?.postDelayed(longPressRunnable, longPressTimeoutMs)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val totalDx = abs(event.rawX - downRawX)
                val totalDy = abs(event.rawY - downRawY)
                if (!isDragging && (totalDx > touchSlop || totalDy > touchSlop)) {
                    isDragging = true
                    handler?.removeCallbacks(longPressRunnable)
                }
                if (isDragging) {
                    val deltaX = event.rawX - lastRawX
                    lastRawX = event.rawX
                    onHorizontalDrag(deltaX)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler?.removeCallbacks(longPressRunnable)
                if (!isDragging && !longPressTriggered) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSingleTap()
                }
                isDragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler?.removeCallbacks(longPressRunnable)
                isDragging = false
                longPressTriggered = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        handler?.removeCallbacks(longPressRunnable)
        stopPulseAnimation()
        super.onDetachedFromWindow()
    }
}

/**
 * =================================================================================================
 * IN-LAUNCHER PREVIEW & CONTROL CARD (`@Composable fun FloatingHudControlCard(...)`)
 * =================================================================================================
 *
 * A Jetpack Compose card that can be rendered inside our Launcher's dashboard or settings sheet to:
 * 1. Toggle the System-Wide Floating Micro-HUD Pill (`TYPE_ACCESSIBILITY_OVERLAY`) on/off.
 * 2. Preview how the Top-Right Micro-HUD Pill looks with live tabular clock, battery/thermal
 *    telemetry (`88% · 31°C`), and pulsing VC Agent status dot.
 * 3. Configure pill opacity (`45%–100%`), scale (`80%–135%`), VC agent status simulation, and
 *    long-press behavior.
 * 4. Test the **0ms Anywhere-Spotlight Trigger** and **VC / Split-Screen** trigger right from the card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingHudControlCard(
    modifier: Modifier = Modifier,
    isAccessibilityServiceActive: Boolean = FoldAccessibilityService.instance != null,
    onOpenAccessibilitySettings: (() -> Unit)? = null,
    onTestAnywhereSpotlight: (() -> Unit)? = null
) {
    val context = LocalContext.current

    LaunchedEffect(context) {
        FloatingHudController.ensureInitialized(context)
    }

    DisposableEffect(isAccessibilityServiceActive) {
        if (isAccessibilityServiceActive) {
            FloatingHudController.syncOverlayWithService()
        }
        onDispose { }
    }

    val hudState by FloatingHudController.state.collectAsState()
    val serviceConnected = isAccessibilityServiceActive || FoldAccessibilityService.instance != null

    val cardShape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        FoldLauncherColors.GlassSurfaceElevated,
                        FoldLauncherColors.GlassSurface
                    )
                )
            )
            .border(1.dp, FoldLauncherColors.GlassBorderStrong, cardShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row: Title, Impossible-on-OneUI badge, and Master Toggle Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SYSTEM-WIDE FLOATING MICRO-HUD PILL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.ElectricCyan
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (hudState.isOverlayAttached) {
                                    FoldLauncherColors.EmeraldSuccess.copy(alpha = 0.2f)
                                } else {
                                    FoldLauncherColors.IndigoAccent.copy(alpha = 0.25f)
                                }
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (hudState.isOverlayAttached) "LIVE OVERLAY" else "A11Y OVERLAY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (hudState.isOverlayAttached) {
                                FoldLauncherColors.EmeraldSuccess
                            } else {
                                FoldLauncherColors.CyanBright
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Impossible on Stock One UI: Persistent top-right clock, battery thermal & VC agent status dot + 1-tap 0ms Spotlight inside ANY app or DeX.",
                    fontSize = 12.sp,
                    color = FoldLauncherColors.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Switch(
                checked = hudState.isEnabled,
                onCheckedChange = { enabled ->
                    val attached = FloatingHudController.setHudEnabled(context, enabled)
                    if (enabled && !attached && onOpenAccessibilitySettings != null) {
                        onOpenAccessibilitySettings()
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF02131D),
                    checkedTrackColor = FoldLauncherColors.ElectricCyan,
                    uncheckedThumbColor = FoldLauncherColors.TextSecondary,
                    uncheckedTrackColor = FoldLauncherColors.DeepVoid
                )
            )
        }

        // Accessibility Service warning banner if user enables HUD while service is off
        if (!serviceConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FoldLauncherColors.AmberWarning.copy(alpha = 0.14f))
                    .border(
                        1.dp,
                        FoldLauncherColors.AmberWarning.copy(alpha = 0.55f),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(enabled = onOpenAccessibilitySettings != null) {
                        onOpenAccessibilitySettings?.invoke()
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Enable FoldAccessibilityService to project the TYPE_ACCESSIBILITY_OVERLAY pill over other apps & DeX.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = FoldLauncherColors.AmberWarning,
                    modifier = Modifier.weight(1f)
                )
                if (onOpenAccessibilitySettings != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enable →",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.TextPrimary
                    )
                }
            }
        }

        // Live Interactive In-Launcher Preview of the Top-Right Micro-HUD Pill
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.82f))
                .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TOP-RIGHT MICRO-HUD PILL PREVIEW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.TextMuted
                )
                Text(
                    text = "Tap pill: Spotlight · Long-press: VC / Split",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = FoldLauncherColors.CyanBright
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                FloatingHudPillComposePreview(
                    state = hudState,
                    onClick = {
                        if (onTestAnywhereSpotlight != null) {
                            onTestAnywhereSpotlight()
                        } else {
                            FloatingHudController.triggerAnywhereSpotlight(context)
                        }
                    }
                )
            }
        }

        // Opacity & Scale Sliders
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pill Opacity",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FoldLauncherColors.TextSecondary
                )
                Text(
                    text = "${(hudState.opacity * 100).roundToInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.TextPrimary
                )
            }
            Slider(
                value = hudState.opacity,
                onValueChange = { FloatingHudController.setOpacity(context, it) },
                valueRange = 0.45f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = FoldLauncherColors.ElectricCyan,
                    activeTrackColor = FoldLauncherColors.ElectricCyan,
                    inactiveTrackColor = FoldLauncherColors.DeepVoid
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pill Scale",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FoldLauncherColors.TextSecondary
                )
                Text(
                    text = "${(hudState.scaleFactor * 100).roundToInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.TextPrimary
                )
            }
            Slider(
                value = hudState.scaleFactor,
                onValueChange = { FloatingHudController.setScaleFactor(context, it) },
                valueRange = 0.80f..1.35f,
                colors = SliderDefaults.colors(
                    thumbColor = FoldLauncherColors.ElectricCyan,
                    activeTrackColor = FoldLauncherColors.ElectricCyan,
                    inactiveTrackColor = FoldLauncherColors.DeepVoid
                )
            )
        }

        // VC Agent Status Dot Selector (Cyan Working / Amber Approval / Red Error / Emerald Ready)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "LIVE VC AGENT STATUS DOT STATE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.TextMuted
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VcHudAgentStatus.entries.forEach { status ->
                    val selected = hudState.vcStatus == status
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) {
                                    status.composeColor.copy(alpha = 0.22f)
                                } else {
                                    FoldLauncherColors.GlassSurfaceHighlight
                                }
                            )
                            .border(
                                1.dp,
                                if (selected) status.composeColor else FoldLauncherColors.GlassBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                FloatingHudController.setVcAgentStatus(context, status)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(status.composeColor)
                        )
                        Text(
                            text = status.shortBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) FoldLauncherColors.TextPrimary else FoldLauncherColors.TextSecondary
                        )
                    }
                }
            }
        }

        // Quick Test & Position Reset Actions
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HudActionPillButton(
                label = "🔍 Test Anywhere-Spotlight",
                accent = true,
                onClick = {
                    if (onTestAnywhereSpotlight != null) {
                        onTestAnywhereSpotlight()
                    } else {
                        FloatingHudController.triggerAnywhereSpotlight(context)
                    }
                }
            )
            HudActionPillButton(
                label = "⚡ Test VC / Split-Screen",
                accent = false,
                onClick = {
                    FloatingHudController.triggerLongPressAction(context)
                }
            )
            HudActionPillButton(
                label = "↺ Reset Top-Right Offset",
                accent = false,
                onClick = {
                    FloatingHudController.resetPillPosition(context)
                }
            )
        }
    }
}

@Composable
private fun FloatingHudPillComposePreview(
    state: FloatingHudUiState,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(10_000L)
        }
    }

    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val clockString = remember(nowMillis, is24Hour) {
        val pattern = if (is24Hour) "HH:mm" else "h:mm"
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(nowMillis))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "vcHudDotPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = if (state.vcStatus.pulses) 1.45f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vcDotHaloScale"
    )

    val tempInt = state.batteryTempCelsius.roundToInt()
    val telemetryText = "${if (state.isCharging) "⚡" else ""}${state.batteryPct}% · ${tempInt}°C"

    Row(
        modifier = Modifier
            .scale(state.scaleFactor)
            .alpha(state.opacity)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xEE0B101D),
                        Color(0xE6141C2E)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = when (state.vcStatus) {
                    VcHudAgentStatus.ERROR -> FoldLauncherColors.CoralDanger.copy(alpha = 0.65f)
                    VcHudAgentStatus.APPROVAL_NEEDED -> FoldLauncherColors.AmberWarning.copy(alpha = 0.65f)
                    else -> FoldLauncherColors.ElectricCyan.copy(alpha = 0.48f)
                },
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Live VC Agent Status Dot
        Box(
            modifier = Modifier.size(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(state.vcStatus.composeColor.copy(alpha = 0.32f))
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(state.vcStatus.composeColor)
            )
        }

        // High-Contrast Tabular Clock
        Text(
            text = clockString,
            style = TextStyle(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.TextPrimary,
                fontFeatureSettings = "tnum"
            )
        )

        // Subtle Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(12.dp)
                .background(FoldLauncherColors.TextSecondary.copy(alpha = 0.30f))
        )

        // Live Battery & Thermal Readout
        Text(
            text = telemetryText,
            style = TextStyle(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    tempInt >= 41 || state.batteryPct <= 15 -> FoldLauncherColors.CoralDanger
                    tempInt >= 38 || state.batteryPct <= 25 -> FoldLauncherColors.AmberWarning
                    state.isCharging -> FoldLauncherColors.EmeraldSuccess
                    else -> FoldLauncherColors.TextSecondary
                },
                fontFeatureSettings = "tnum"
            )
        )

        // Mini Spotlight Magnifier Glyph
        ComposeCanvas(modifier = Modifier.size(12.dp)) {
            val strokePx = 1.6.dp.toPx()
            val r = size.minDimension * 0.35f
            val center = Offset(size.width * 0.42f, size.height * 0.42f)
            drawCircle(
                color = FoldLauncherColors.ElectricCyan,
                radius = r,
                center = center,
                style = Stroke(width = strokePx)
            )
            drawLine(
                color = FoldLauncherColors.ElectricCyan,
                start = Offset(center.x + r * 0.72f, center.y + r * 0.72f),
                end = Offset(size.width * 0.90f, size.height * 0.90f),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun HudActionPillButton(
    label: String,
    accent: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (accent) FoldLauncherColors.CyanGlow else FoldLauncherColors.GlassSurfaceHighlight
            )
            .border(
                1.dp,
                if (accent) FoldLauncherColors.GlassBorderStrong else FoldLauncherColors.GlassBorder,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (accent) FoldLauncherColors.CyanBright else FoldLauncherColors.TextPrimary
        )
    }
}
