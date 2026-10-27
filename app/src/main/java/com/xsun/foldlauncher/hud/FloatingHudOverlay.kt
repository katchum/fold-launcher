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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.mutableStateOf
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
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.vc.VcAttentionKind
import com.xsun.foldlauncher.vc.VcBackgroundMonitor
import com.xsun.foldlauncher.vc.VcPromptMacro
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
 * 1. **Stock Launchers Disappear Inside Apps, Samsung DeX & Lock Screen**: When you open any
 *    full-screen app, browser, terminal, or game on Galaxy Z Fold 8 (`SM-F971U1`)—or when the
 *    device is on the Lock Screen—stock One UI Home is pushed to the background. You are left with
 *    Samsung's tiny status-bar clock, zero battery thermal or hinge angle visibility, and no live
 *    indicator of your background VC coding agents.
 * 2. **Lock-Screen & System-Wide `TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED`**:
 *    Because `TYPE_ACCESSIBILITY_OVERLAY` combined with `FLAG_SHOW_WHEN_LOCKED` is permitted by
 *    Android's `WindowManagerService` over the Keyguard, our Floating Micro-HUD Pill (with
 *    oversized Clock, Battery/Temp, live Z Fold 8 Hinge Angle, and Live VC Agent status dot)
 *    renders **right on top of the Samsung Z Fold 8 Lock Screen** in both Portrait and Landscape
 *    as well as over every app and DeX workspace!
 * 3. **Zero-Latency Anywhere-Spotlight, Multi-VC Split, 1-Tap Torch & Quick-Approve VC (`v0.3.0`)**:
 *    - **Single Tap**: Immediately brings `MainActivity` to the front with
 *      `EXTRA_OPEN_SPOTLIGHT = true` for **0ms Anywhere-Spotlight Search** from inside any app!
 *    - **Expand Chevron / Expanded HUD Panel (`FloatingHudExpandedPanel`)**: Expands the HUD Pill
 *      in-place to reveal:
 *      - Live Z Fold 8 hinge posture telemetry (`180°` / `118° FLEX`)
 *      - **1-Tap `🔦 Torch` Toggle Chip** (glowing Amber when `isTorchOn == true`)
 *      - **Glowing 1-Tap `✅ Approve VC` Chip** when `state.vcPendingApprovals > 0`
 *      - **Z Fold 8 Power Split Row**: `VC + Termux` (`dev.agentconversation` + `com.termux`) and
 *        `Prod + Compare` (`dev.agentconversation` + `dev.agentconversation.compare`)
 *    - **Long Press**: Launches/switches directly to the VC Agent app
 *      (`dev.agentconversation` / `dev.agentconversation.compare` / `com.example.agentconversation`)
 *      or triggers instant Split-Screen (`FoldAccessibilityService.instance?.toggleSplitScreen()`).
 *    - **Horizontal Drag**: Smoothly slides the Micro-HUD Pill along the top screen edge and
 *      persists its exact X offset in `SharedPreferences`.
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
 * Snapshot of live telemetry & user configuration for the System-Wide Micro-HUD Pill (`v0.3.0`).
 *
 * Includes optional [hingeAngleDegrees], [postureLabel], [vcPendingApprovals], and [isTorchOn]
 * (all with defaults so existing `v0.1.0` and `v0.2.0` callers compile unchanged).
 */
data class HudTelemetryState(
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
    val longPressAction: HudLongPressAction = HudLongPressAction.LAUNCH_VC_OR_SPLIT,
    val hingeAngleDegrees: Float? = null,
    val postureLabel: String? = null,
    val vcPendingApprovals: Int = 0,
    val isTorchOn: Boolean = false
) {
    /**
     * Formatted compact hinge angle badge for the collapsed and expanded Micro-HUD Pill
     * (e.g., `"180°"`, `"118° FLEX"`, `"0° COVER"`), or `null` when no hinge sensor is present.
     */
    val formattedHingeBadge: String?
        get() = formatHingeReadout(hingeAngleDegrees, postureLabel)

    companion object {
        fun formatHingeReadout(hingeAngleDegrees: Float?, postureLabel: String? = null): String? {
            val angle = hingeAngleDegrees ?: return null
            val degInt = angle.roundToInt().coerceIn(0, 180)
            val explicitLabel = postureLabel?.trim()?.uppercase(Locale.US).orEmpty()
            val resolvedPosture = when {
                explicitLabel.isNotEmpty() -> explicitLabel
                degInt in 75..155 -> "FLEX"
                degInt <= 25 -> "COVER"
                else -> ""
            }
            return if (resolvedPosture.isNotEmpty() && resolvedPosture != "FLAT" && resolvedPosture != "180°") {
                "${degInt}° $resolvedPosture"
            } else {
                "${degInt}°"
            }
        }
    }
}

/**
 * Backward-compatible typealias so all `v0.1.0` / `v0.2.0` callers referencing `FloatingHudUiState`
 * continue to compile and interoperate seamlessly with `HudTelemetryState`.
 */
typealias FloatingHudUiState = HudTelemetryState

/**
 * System-Wide `TYPE_ACCESSIBILITY_OVERLAY` Micro-HUD Controller (`FloatingHudController`).
 *
 * Attaches a sleek, compact, draggable top-right pill view (`FloatingMicroHudPillView`) using
 * `WindowManager` from `FoldAccessibilityService` (`WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`,
 * `FLAG_NOT_FOCUSABLE`, `FLAG_LAYOUT_IN_SCREEN`, `FLAG_NOT_TOUCH_MODAL`, `FLAG_SHOW_WHEN_LOCKED`,
 * `PixelFormat.TRANSLUCENT`, `Gravity.TOP or Gravity.END`).
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

    const val PKG_VC_PROD = "dev.agentconversation"
    const val PKG_VC_PREVIEW = "dev.agentconversation.preview"
    const val PKG_VC_COMPARE = "dev.agentconversation.compare"
    const val PKG_VC_VCI = "dev.agentconversation.vci"
    const val PKG_VC_LEGACY = "com.example.agentconversation"
    const val PKG_TERMUX = "com.termux"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null

    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var pillView: FloatingMicroHudPillView? = null
    private var attachedServiceContext: Context? = null

    private var telemetryRegistered = false
    private var telemetryContext: Context? = null

    private var hingeSensorRegistered = false
    private var sensorManager: SensorManager? = null

    private val _state = MutableStateFlow(HudTelemetryState())
    val state: StateFlow<HudTelemetryState> = _state.asStateFlow()

    private val clockTickRunnable = object : Runnable {
        override fun run() {
            syncServiceHingeAngleIfPresent()
            syncTorchAndVcApprovalTelemetry()
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
                isCharging = charging,
                hingeAngleDegrees = _state.value.hingeAngleDegrees,
                postureLabel = _state.value.postureLabel,
                vcPendingApprovals = _state.value.vcPendingApprovals,
                isTorchOn = _state.value.isTorchOn
            )
        }
    }

    private val hingeSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val values = event?.values ?: return
            if (values.isEmpty()) return
            val rawAngle = values[0].coerceIn(0f, 180f)
            val derivedLabel = derivePostureLabel(rawAngle)
            updateHingeTelemetry(
                hingeAngleDegrees = rawAngle,
                postureLabel = derivedLabel
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // No-op
        }
    }

    private fun derivePostureLabel(angleDegrees: Float): String? {
        return when {
            angleDegrees in 75f..155f -> "FLEX"
            angleDegrees < 25f -> "COVER"
            else -> null
        }
    }

    private fun syncServiceHingeAngleIfPresent() {
        val serviceAngle = FoldAccessibilityService.hingeAngleFlow.value ?: return
        if (_state.value.hingeAngleDegrees == null) {
            updateHingeTelemetry(
                hingeAngleDegrees = serviceAngle,
                postureLabel = derivePostureLabel(serviceAngle)
            )
        }
    }

    private fun syncTorchAndVcApprovalTelemetry() {
        val ctx = attachedServiceContext ?: telemetryContext ?: return
        val torchActive = SystemTweaker.isTorchActive.value
        val monitorApprovalCount = runCatching {
            VcBackgroundMonitor.getInstance(ctx).liveSummary.value.approvalCount
        }.getOrDefault(0)
        val effectiveApprovals = when {
            monitorApprovalCount > 0 -> monitorApprovalCount
            _state.value.vcStatus == VcHudAgentStatus.APPROVAL_NEEDED ->
                _state.value.vcPendingApprovals.coerceAtLeast(1)
            else -> _state.value.vcPendingApprovals
        }

        if (_state.value.isTorchOn != torchActive || _state.value.vcPendingApprovals != effectiveApprovals) {
            _state.value = _state.value.copy(
                isTorchOn = torchActive,
                vcPendingApprovals = effectiveApprovals
            )
            pillView?.updateQuickActionsState(
                isTorchOn = torchActive,
                vcPendingApprovals = effectiveApprovals
            )
            updateWindowLayoutIfAttached()
        }
    }

    /**
     * Initializes preferences, battery/thermal telemetry, and Z Fold 8 `Sensor.TYPE_HINGE_ANGLE`
     * listener. Safe to call multiple times.
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
                longPressAction = savedLongPress,
                vcPendingApprovals = if (savedStatus == VcHudAgentStatus.APPROVAL_NEEDED) 1 else 0,
                isTorchOn = SystemTweaker.isTorchActive.value
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

        registerHingeAngleSensorIfAvailable(appCtx)
        syncServiceHingeAngleIfPresent()
        syncTorchAndVcApprovalTelemetry()

        if (_state.value.isEnabled && pillView == null) {
            syncOverlayWithService()
        }
    }

    private fun registerHingeAngleSensorIfAvailable(context: Context) {
        if (hingeSensorRegistered) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            val hingeSensor = sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return
            val ok = sm.registerListener(
                hingeSensorListener,
                hingeSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            if (ok) {
                sensorManager = sm
                hingeSensorRegistered = true
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Updates the live Z Fold 8 hinge angle telemetry (`hingeAngleDegrees` and optional
     * `postureLabel`, e.g., `180°` or `118° FLEX`) on both the state flow and the overlay pill.
     */
    fun updateHingeTelemetry(
        hingeAngleDegrees: Float?,
        postureLabel: String? = null
    ) {
        val current = _state.value
        val previousRounded = current.hingeAngleDegrees?.roundToInt()
        val nextRounded = hingeAngleDegrees?.roundToInt()
        if (previousRounded == nextRounded && current.postureLabel == postureLabel) {
            return
        }
        _state.value = current.copy(
            hingeAngleDegrees = hingeAngleDegrees,
            postureLabel = postureLabel
        )
        pillView?.updateTelemetry(
            batteryPct = _state.value.batteryPct,
            tempCelsius = _state.value.batteryTempCelsius,
            isCharging = _state.value.isCharging,
            hingeAngleDegrees = hingeAngleDegrees,
            postureLabel = postureLabel,
            vcPendingApprovals = _state.value.vcPendingApprovals,
            isTorchOn = _state.value.isTorchOn
        )
        updateWindowLayoutIfAttached()
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

    fun setVcAgentStatus(context: Context, status: VcHudAgentStatus, pendingApprovals: Int? = null) {
        ensureInitialized(context)
        prefs?.edit()?.putString(KEY_HUD_VC_STATUS, status.name)?.apply()
        val resolvedApprovals = pendingApprovals ?: when (status) {
            VcHudAgentStatus.APPROVAL_NEEDED -> {
                val fromMonitor = runCatching {
                    VcBackgroundMonitor.getInstance(context).liveSummary.value.approvalCount
                }.getOrDefault(0)
                if (fromMonitor > 0) fromMonitor else _state.value.vcPendingApprovals.coerceAtLeast(1)
            }
            else -> 0
        }
        _state.value = _state.value.copy(
            vcStatus = status,
            vcPendingApprovals = resolvedApprovals
        )
        pillView?.updateVcStatus(status)
        pillView?.updateQuickActionsState(
            isTorchOn = _state.value.isTorchOn,
            vcPendingApprovals = resolvedApprovals
        )
        updateWindowLayoutIfAttached()
    }

    fun setVcPendingApprovals(count: Int) {
        val clamped = count.coerceAtLeast(0)
        val nextStatus = if (clamped > 0) {
            VcHudAgentStatus.APPROVAL_NEEDED
        } else if (_state.value.vcStatus == VcHudAgentStatus.APPROVAL_NEEDED) {
            VcHudAgentStatus.WORKING
        } else {
            _state.value.vcStatus
        }
        _state.value = _state.value.copy(
            vcPendingApprovals = clamped,
            vcStatus = nextStatus
        )
        pillView?.updateVcStatus(nextStatus)
        pillView?.updateQuickActionsState(
            isTorchOn = _state.value.isTorchOn,
            vcPendingApprovals = clamped
        )
        updateWindowLayoutIfAttached()
    }

    fun setTorchState(isTorchOn: Boolean) {
        _state.value = _state.value.copy(isTorchOn = isTorchOn)
        pillView?.updateQuickActionsState(
            isTorchOn = isTorchOn,
            vcPendingApprovals = _state.value.vcPendingApprovals
        )
        updateWindowLayoutIfAttached()
    }

    /**
     * Toggles the Z Fold 8 hardware camera flashlight / torch directly from the Floating Micro-HUD
     * Pill (`CameraManager.setTorchMode`) and updates the glowing amber `🔦 Torch` chip state.
     */
    fun toggleTorchFromHud(context: Context): Boolean {
        val next = SystemTweaker.toggleTorch(context, _state.value.isTorchOn)
        setTorchState(next)
        return next
    }

    /**
     * Dispatches a 1-tap `✅ Approve VC` action directly from the Floating Micro-HUD Pill:
     * sends `VcPromptMacro.APPROVE_CONTINUE` to the active/pending VC agent session and marks
     * pending approval attention events as read.
     */
    fun quickApproveVcFromHud(context: Context) {
        try {
            val monitor = VcBackgroundMonitor.getInstance(context)
            val summary = monitor.liveSummary.value
            val targetSessionId = summary.attentionItems
                .firstOrNull { it.kind == VcAttentionKind.APPROVAL && !it.read }
                ?.sessionId
                ?: summary.activeAgents.firstOrNull { it.needsApproval }?.sessionId
                ?: summary.pinnedSessionId

            monitor.sendPromptMacro(
                macro = VcPromptMacro.APPROVE_CONTINUE,
                sessionId = targetSessionId,
                steerIfWorking = true
            )
            monitor.markAttentionRead(markAll = true)
        } catch (_: Exception) {
        }
        setVcPendingApprovals(0)
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
     * Builds the `WindowManager.LayoutParams` for the System-Wide `TYPE_ACCESSIBILITY_OVERLAY`
     * Micro-HUD Pill, including `FLAG_SHOW_WHEN_LOCKED` so the pill renders right on top of the
     * Samsung Galaxy Z Fold 8 Lock Screen in both Portrait and Landscape as well as over any app.
     */
    @Suppress("DEPRECATION")
    internal fun createOverlayLayoutParams(
        offsetXPx: Int,
        offsetYPx: Int
    ): WindowManager.LayoutParams {
        val overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            overlayFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = offsetXPx
            y = offsetYPx
            title = "Fold8FloatingMicroHudPill"
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

            val lp = createOverlayLayoutParams(
                offsetXPx = current.offsetXPx,
                offsetYPx = current.offsetYPx
            )

            val view = FloatingMicroHudPillView(
                context = hostService,
                initialState = current,
                onSingleTap = {
                    triggerAnywhereSpotlight(hostService)
                },
                onLongPress = {
                    triggerLongPressAction(hostService)
                },
                onLaunchVcPlusTermux = {
                    launchVcPlusTermuxSplit(hostService)
                },
                onLaunchProdPlusCompare = {
                    launchProdPlusCompareSplit(hostService)
                },
                onToggleTorch = {
                    toggleTorchFromHud(hostService)
                },
                onQuickApproveVc = {
                    quickApproveVcFromHud(hostService)
                },
                onExpandedChanged = {
                    updateWindowLayoutIfAttached()
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
     * Launches or switches directly to the VC app (`dev.agentconversation` /
     * `com.example.agentconversation` / `dev.agentconversation.vci` / `dev.agentconversation.preview`
     * / VC web UI) or toggles Split-Screen (`FoldAccessibilityService.instance?.toggleSplitScreen()`).
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
     * Attempts to launch installed VC Agent packages (`dev.agentconversation`,
     * `dev.agentconversation.preview`, `dev.agentconversation.compare`,
     * `com.example.agentconversation`, `dev.agentconversation.vci`), or falls back to opening
     * the VC Web UI (`http://127.0.0.1:8080`).
     */
    fun launchOrSwitchToVcApp(context: Context): Boolean {
        val pm = context.packageManager
        val candidatePackages = listOf(
            PKG_VC_PROD,
            PKG_VC_PREVIEW,
            PKG_VC_COMPARE,
            PKG_VC_LEGACY,
            PKG_VC_VCI
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

    /**
     * **Z Fold 8 Power Split Action 1 (`VC + Termux`)**:
     * Launches `dev.agentconversation` (falling back to any installed VC package) + `com.termux`
     * side-by-side in split-screen multi-window mode on the Z Fold 8 inner display.
     */
    fun launchVcPlusTermuxSplit(context: Context): Boolean {
        return launchSplitScreenPair(
            context = context,
            primaryCandidates = listOf(
                PKG_VC_PROD,
                PKG_VC_PREVIEW,
                PKG_VC_COMPARE,
                PKG_VC_VCI,
                PKG_VC_LEGACY
            ),
            secondaryCandidates = listOf(
                PKG_TERMUX,
                "com.termux.api",
                "com.android.chrome"
            )
        )
    }

    /**
     * **Z Fold 8 Power Split Action 2 (`Prod + Compare`)**:
     * Launches `dev.agentconversation` (VC Prod) + `dev.agentconversation.compare` (VC Compare,
     * falling back to `dev.agentconversation.preview`) side-by-side in split-screen mode on the
     * Z Fold 8 inner display.
     */
    fun launchProdPlusCompareSplit(context: Context): Boolean {
        return launchSplitScreenPair(
            context = context,
            primaryCandidates = listOf(
                PKG_VC_PROD,
                PKG_VC_VCI,
                PKG_VC_LEGACY
            ),
            secondaryCandidates = listOf(
                PKG_VC_COMPARE,
                PKG_VC_PREVIEW,
                PKG_TERMUX
            )
        )
    }

    /**
     * Launches a primary app followed 140ms later by an adjacent split-screen secondary app
     * (`FLAG_ACTIVITY_LAUNCH_ADJACENT or FLAG_ACTIVITY_MULTIPLE_TASK`).
     */
    fun launchSplitScreenPair(
        context: Context,
        primaryCandidates: List<String>,
        secondaryCandidates: List<String>
    ): Boolean {
        val pm = context.packageManager
        var chosenPrimaryPkg: String? = null
        var primaryIntent: Intent? = null

        for (pkg in primaryCandidates) {
            val intent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
            if (intent != null) {
                chosenPrimaryPkg = pkg
                primaryIntent = intent.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                break
            }
        }

        var secondaryIntent: Intent? = null
        for (pkg in secondaryCandidates) {
            if (pkg == chosenPrimaryPkg) continue
            val intent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
            if (intent != null) {
                secondaryIntent = intent.apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    )
                }
                break
            }
        }

        val primaryLaunched = if (primaryIntent != null) {
            try {
                context.startActivity(primaryIntent)
                true
            } catch (_: Exception) {
                false
            }
        } else {
            launchOrSwitchToVcApp(context)
        }

        if (secondaryIntent != null) {
            mainHandler.postDelayed({
                try {
                    context.startActivity(secondaryIntent)
                } catch (_: Exception) {
                }
            }, 140L)
            return true
        } else {
            // If only one app is installed, also trigger accessibility split-screen toggle if available
            FoldAccessibilityService.instance?.toggleSplitScreen()
        }

        return primaryLaunched
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
 * (both Collapsed Pill and Expanded HUD Panel with **Z Fold 8 Power Split Row**, **1-Tap `🔦 Torch`**,
 * and **Glowing 1-Tap `✅ Approve VC`**) inside `FoldAccessibilityService` without requiring an
 * `Activity` or `LifecycleOwner`.
 *
 * Displays:
 * - **Live VC Agent Status Dot** (pulsing Cyan when VC agents are working, Amber when approval
 *   is needed, Red on error, Emerald when idle/ready)
 * - **High-Contrast Tabular Clock** (e.g., `18:40`)
 * - **Live Battery & Thermal Readout** (e.g., `88% · 31°C`)
 * - **Live Z Fold 8 Hinge Angle Readout** when `hingeAngleDegrees != null` (e.g., `180°` or `118° FLEX`)
 * - **Spotlight Search Magnifier Glyph** & **Expand/Collapse Toggle**
 * - **Expanded HUD Panel (`isExpanded = true`)**:
 *   - **Z Fold 8 Power Split Row**: 1-tap `⚡ VC + Termux` (`dev.agentconversation` + `com.termux`)
 *     and `⊞ Prod + Compare` (`dev.agentconversation` + `dev.agentconversation.compare`) buttons
 *   - **Quick Hardware & VC Action Row (`v0.3.0`)**:
 *     - 1-tap `🔦 Torch` toggle chip (glowing Amber when `isTorchOn == true`)
 *     - Glowing 1-tap `✅ Approve VC` chip when `vcPendingApprovals > 0`
 */
internal class FloatingMicroHudPillView(
    context: Context,
    initialState: HudTelemetryState,
    private val onSingleTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onLaunchVcPlusTermux: () -> Unit = {},
    private val onLaunchProdPlusCompare: () -> Unit = {},
    private val isTorchOnInitial: Boolean = initialState.isTorchOn,
    private val onToggleTorch: () -> Unit = {},
    private val onQuickApproveVc: () -> Unit = {},
    private val onExpandedChanged: (Boolean) -> Unit = {},
    private val onHorizontalDrag: (deltaXPx: Float) -> Unit
) : View(context) {

    private var pillOpacity: Float = initialState.opacity
    private var pillScale: Float = initialState.scaleFactor
    private var batteryPct: Int = initialState.batteryPct
    private var tempCelsius: Float = initialState.batteryTempCelsius
    private var isCharging: Boolean = initialState.isCharging
    private var vcStatus: VcHudAgentStatus = initialState.vcStatus
    private var hingeAngleDegrees: Float? = initialState.hingeAngleDegrees
    private var postureLabel: String? = initialState.postureLabel
    private var vcPendingApprovals: Int = initialState.vcPendingApprovals
    private var isTorchOn: Boolean = isTorchOnInitial
    private var isExpanded: Boolean = false

    // Pulse animation phase [0f..1f] for the VC Agent Status Dot
    private var pulsePhase: Float = 0.5f
    private var pulseAnimator: ValueAnimator? = null

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()

    private val pillRect = RectF()
    private val borderRect = RectF()
    private val expandHitRect = RectF()
    private val vcTermuxBtnRect = RectF()
    private val prodCompareBtnRect = RectF()
    private val torchBtnRect = RectF()
    private val approveVcBtnRect = RectF()

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

    private val hingePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF67E8F9.toInt()
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        fontFeatureSettings = "tnum"
    }

    private val buttonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x2E38BDF8
    }

    private val buttonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
        color = 0x6638BDF8
    }

    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF67E8F9.toInt()
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val magnifierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38BDF8.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var downLocalX = 0f
    private var downLocalY = 0f
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

    fun updateTelemetry(
        batteryPct: Int,
        tempCelsius: Float,
        isCharging: Boolean,
        hingeAngleDegrees: Float? = this.hingeAngleDegrees,
        postureLabel: String? = this.postureLabel,
        vcPendingApprovals: Int = this.vcPendingApprovals,
        isTorchOn: Boolean = this.isTorchOn
    ) {
        this.batteryPct = batteryPct
        this.tempCelsius = tempCelsius
        this.isCharging = isCharging
        this.hingeAngleDegrees = hingeAngleDegrees
        this.postureLabel = postureLabel
        this.vcPendingApprovals = vcPendingApprovals
        this.isTorchOn = isTorchOn
        requestLayout()
        invalidate()
    }

    fun updateQuickActionsState(isTorchOn: Boolean, vcPendingApprovals: Int) {
        this.isTorchOn = isTorchOn
        this.vcPendingApprovals = vcPendingApprovals
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
        hingePaint.textSize = 11.0f * scaledDensity * pillScale
        buttonTextPaint.textSize = 10.5f * scaledDensity * pillScale
        magnifierPaint.strokeWidth = 1.6f * density * pillScale
        borderPaint.strokeWidth = 1.15f * density * pillScale
        buttonBorderPaint.strokeWidth = 1.0f * density * pillScale
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

    private fun formatHingeText(): String? {
        return HudTelemetryState.formatHingeReadout(hingeAngleDegrees, postureLabel)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updatePaintsForScale()
        val clockStr = formatClockText()
        val telemetryStr = formatTelemetryText()
        val hingeStr = formatHingeText()

        val padH = 11f * density * pillScale
        val padV = 6f * density * pillScale
        val dotRegionW = 14f * density * pillScale
        val gap = 7f * density * pillScale
        val dividerGap = 8f * density * pillScale
        val searchIconW = 13f * density * pillScale
        val chevronW = 14f * density * pillScale

        val clockW = clockPaint.measureText(clockStr)
        val telemetryW = telemetryPaint.measureText(telemetryStr)
        val hingeW = if (hingeStr != null) {
            dividerGap + hingePaint.measureText(hingeStr)
        } else {
            0f
        }

        val collapsedWidth = (
            padH * 2f + dotRegionW + gap + clockW + dividerGap +
                telemetryW + hingeW + gap + searchIconW + gap * 0.6f + chevronW
            ).roundToInt().coerceAtLeast((152f * density * pillScale).roundToInt())

        val collapsedHeight = (padV * 2f + 16f * density * pillScale)
            .roundToInt()
            .coerceAtLeast((28f * density * pillScale).roundToInt())

        if (!isExpanded) {
            setMeasuredDimension(collapsedWidth, collapsedHeight)
        } else {
            val minExpandedW = (252f * density * pillScale).roundToInt()
            val expandedWidth = maxOf(collapsedWidth, minExpandedW)
            val rowHeight = (30f * density * pillScale).roundToInt()
            val rowGap = (6f * density * pillScale).roundToInt()
            // Row 1: Power Split Row (VC + Termux, Prod + Compare)
            // Row 2: Quick Hardware & VC Action Row (🔦 Torch, and ✅ Approve VC when vcPendingApprovals > 0)
            val expandedHeight = collapsedHeight + rowHeight * 2 + rowGap * 2 + (4f * density * pillScale).roundToInt()
            setMeasuredDimension(expandedWidth, expandedHeight)
        }
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

        val padH = 11f * density * pillScale
        val padV = 6f * density * pillScale
        val topRowHeight = (padV * 2f + 16f * density * pillScale).coerceAtLeast(28f * density * pillScale)
        val cornerRadius = if (isExpanded) 16f * density * pillScale else h * 0.5f
        val alphaInt = (pillOpacity * 255f).roundToInt().coerceIn(100, 255)

        bgPaint.alpha = alphaInt
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, bgPaint)

        borderPaint.color = when {
            vcStatus == VcHudAgentStatus.ERROR -> 0x88F87171.toInt()
            vcStatus == VcHudAgentStatus.APPROVAL_NEEDED || vcPendingApprovals > 0 -> 0x99FBBF24.toInt()
            isTorchOn -> 0x99FBBF24.toInt()
            else -> 0x6638BDF8
        }
        borderPaint.alpha = alphaInt
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

        val centerY = if (isExpanded) topRowHeight * 0.5f else h * 0.5f
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
        val divTop = centerY - 6f * density * pillScale
        val divBottom = centerY + 6f * density * pillScale
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
        cursorX += telemetryPaint.measureText(telemetryStr)

        // 4. Live Z Fold 8 Hinge Angle Readout when hingeAngleDegrees != null (e.g., "180°" or "118° FLEX")
        val hingeStr = formatHingeText()
        if (hingeStr != null) {
            cursorX += dividerGap * 0.5f
            canvas.drawLine(cursorX, divTop, cursorX, divBottom, dividerPaint)
            cursorX += dividerGap * 0.5f

            val deg = hingeAngleDegrees?.roundToInt() ?: 180
            hingePaint.color = if (deg in 75..155) {
                0xFFFBBF24.toInt() // Amber highlight in Flex mode
            } else {
                0xFF67E8F9.toInt() // CyanBright when flat/cover
            }
            hingePaint.alpha = alphaInt
            val hingeMetrics = hingePaint.fontMetrics
            val hingeBaseline = centerY - (hingeMetrics.ascent + hingeMetrics.descent) * 0.5f
            canvas.drawText(hingeStr, cursorX, hingeBaseline, hingePaint)
            cursorX += hingePaint.measureText(hingeStr)
        }

        cursorX += gap

        // 5. Mini Spotlight Magnifier Glyph + Expand/Collapse Chevron on right edge
        val chevronW = 14f * density * pillScale
        val chevronCenterX = w - padH - chevronW * 0.35f
        expandHitRect.set(w - padH - chevronW * 1.4f, 0f, w, topRowHeight)

        val iconRadius = 3.8f * density * pillScale
        val iconCenterX = (chevronCenterX - chevronW * 0.85f - iconRadius).coerceAtLeast(cursorX + iconRadius)
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

        // Draw subtle chevron (▾ when collapsed, ▴ when expanded)
        val chevHalfW = 3.2f * density * pillScale
        val chevHalfH = 2.0f * density * pillScale
        if (isExpanded) {
            canvas.drawLine(
                chevronCenterX - chevHalfW,
                centerY + chevHalfH,
                chevronCenterX,
                centerY - chevHalfH,
                magnifierPaint
            )
            canvas.drawLine(
                chevronCenterX,
                centerY - chevHalfH,
                chevronCenterX + chevHalfW,
                centerY + chevHalfH,
                magnifierPaint
            )
        } else {
            canvas.drawLine(
                chevronCenterX - chevHalfW,
                centerY - chevHalfH,
                chevronCenterX,
                centerY + chevHalfH,
                magnifierPaint
            )
            canvas.drawLine(
                chevronCenterX,
                centerY + chevHalfH,
                chevronCenterX + chevHalfW,
                centerY - chevHalfH,
                magnifierPaint
            )
        }

        // 6. Expanded HUD Panel:
        //    - Row 1: Z Fold 8 Power Split Row (`⚡ VC + Termux` & `⊞ Prod + Compare`)
        //    - Row 2 (`v0.3.0`): 1-Tap `🔦 Torch` chip (glowing Amber when `isTorchOn == true`) +
        //      glowing 1-Tap `✅ Approve VC` chip when `vcPendingApprovals > 0`
        if (isExpanded) {
            val btnGap = 6f * density * pillScale
            val btnWidth = (w - padH * 2f - btnGap) * 0.5f
            val btnRadius = 9f * density * pillScale
            val rowHeight = 28f * density * pillScale
            val btnMetrics = buttonTextPaint.fontMetrics

            // Row 1: Z Fold 8 Power Split Row
            val row1Top = topRowHeight + 3f * density * pillScale
            val row1Bottom = row1Top + rowHeight
            vcTermuxBtnRect.set(padH, row1Top, padH + btnWidth, row1Bottom)
            prodCompareBtnRect.set(padH + btnWidth + btnGap, row1Top, w - padH, row1Bottom)

            buttonBgPaint.color = 0x2E38BDF8
            buttonBgPaint.alpha = (alphaInt * 0.35f).roundToInt()
            buttonBorderPaint.color = 0x6638BDF8
            buttonBorderPaint.alpha = (alphaInt * 0.75f).roundToInt()
            buttonTextPaint.color = 0xFF67E8F9.toInt()
            buttonTextPaint.alpha = alphaInt

            canvas.drawRoundRect(vcTermuxBtnRect, btnRadius, btnRadius, buttonBgPaint)
            canvas.drawRoundRect(vcTermuxBtnRect, btnRadius, btnRadius, buttonBorderPaint)
            canvas.drawRoundRect(prodCompareBtnRect, btnRadius, btnRadius, buttonBgPaint)
            canvas.drawRoundRect(prodCompareBtnRect, btnRadius, btnRadius, buttonBorderPaint)

            val row1TextY = (row1Top + row1Bottom) * 0.5f - (btnMetrics.ascent + btnMetrics.descent) * 0.5f
            canvas.drawText("⚡ VC + Termux", vcTermuxBtnRect.centerX(), row1TextY, buttonTextPaint)
            canvas.drawText("⊞ Prod + Compare", prodCompareBtnRect.centerX(), row1TextY, buttonTextPaint)

            // Row 2: 1-Tap `🔦 Torch` toggle chip + conditional glowing `✅ Approve VC` chip
            val row2Top = row1Bottom + 6f * density * pillScale
            val row2Bottom = (row2Top + rowHeight).coerceAtMost(h - 5f * density * pillScale)
            val showApproveChip = vcPendingApprovals > 0

            if (showApproveChip) {
                torchBtnRect.set(padH, row2Top, padH + btnWidth, row2Bottom)
                approveVcBtnRect.set(padH + btnWidth + btnGap, row2Top, w - padH, row2Bottom)
            } else {
                torchBtnRect.set(padH, row2Top, w - padH, row2Bottom)
                approveVcBtnRect.setEmpty()
            }

            // Draw `🔦 Torch` chip (glowing amber when isTorchOn == true)
            if (isTorchOn) {
                buttonBgPaint.color = 0x55FBBF24
                buttonBorderPaint.color = 0xEEFBBF24.toInt()
                buttonTextPaint.color = 0xFFFDE68A.toInt()
            } else {
                buttonBgPaint.color = 0x261E293B
                buttonBorderPaint.color = 0x5594A3B8
                buttonTextPaint.color = 0xFFE2E8F0.toInt()
            }
            canvas.drawRoundRect(torchBtnRect, btnRadius, btnRadius, buttonBgPaint)
            canvas.drawRoundRect(torchBtnRect, btnRadius, btnRadius, buttonBorderPaint)

            val row2TextY = (row2Top + row2Bottom) * 0.5f - (btnMetrics.ascent + btnMetrics.descent) * 0.5f
            val torchLabel = if (isTorchOn) "🔦 Torch ON" else "🔦 Torch"
            canvas.drawText(torchLabel, torchBtnRect.centerX(), row2TextY, buttonTextPaint)

            // Draw glowing `✅ Approve VC` chip when vcPendingApprovals > 0
            if (showApproveChip) {
                buttonBgPaint.color = 0x4D34D399
                buttonBorderPaint.color = 0xEE34D399.toInt()
                buttonTextPaint.color = 0xFF6EE7B7.toInt()
                canvas.drawRoundRect(approveVcBtnRect, btnRadius, btnRadius, buttonBgPaint)
                canvas.drawRoundRect(approveVcBtnRect, btnRadius, btnRadius, buttonBorderPaint)
                val approveLabel = if (vcPendingApprovals > 1) {
                    "✅ Approve VC ($vcPendingApprovals)"
                } else {
                    "✅ Approve VC"
                }
                canvas.drawText(approveLabel, approveVcBtnRect.centerX(), row2TextY, buttonTextPaint)
            }
        } else {
            vcTermuxBtnRect.setEmpty()
            prodCompareBtnRect.setEmpty()
            torchBtnRect.setEmpty()
            approveVcBtnRect.setEmpty()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downLocalX = event.x
                downLocalY = event.y
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
                    when {
                        isExpanded && torchBtnRect.contains(downLocalX, downLocalY) -> {
                            onToggleTorch()
                            invalidate()
                        }

                        isExpanded && !approveVcBtnRect.isEmpty &&
                            approveVcBtnRect.contains(downLocalX, downLocalY) -> {
                            onQuickApproveVc()
                            invalidate()
                        }

                        isExpanded && vcTermuxBtnRect.contains(downLocalX, downLocalY) -> {
                            isExpanded = false
                            requestLayout()
                            invalidate()
                            onExpandedChanged(false)
                            onLaunchVcPlusTermux()
                        }

                        isExpanded && prodCompareBtnRect.contains(downLocalX, downLocalY) -> {
                            isExpanded = false
                            requestLayout()
                            invalidate()
                            onExpandedChanged(false)
                            onLaunchProdPlusCompare()
                        }

                        expandHitRect.contains(downLocalX, downLocalY) -> {
                            isExpanded = !isExpanded
                            requestLayout()
                            invalidate()
                            onExpandedChanged(isExpanded)
                        }

                        else -> {
                            onSingleTap()
                        }
                    }
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
 * FLOATING MICRO-HUD OVERLAY & EXPANDED PANEL COMPOSABLES (`v0.3.0`)
 * (`@Composable fun FloatingHudOverlay(...)` & `@Composable fun FloatingHudExpandedPanel(...)`)
 * =================================================================================================
 */

/**
 * System-Wide / Lock-Screen Floating Micro-HUD Pill & Expandable Panel Composable (`v0.3.0`).
 *
 * Supports:
 * - Collapsed high-contrast top-right pill with live VC Agent status dot, tabular clock,
 *   battery/thermal telemetry (`88% · 31°C`), and Z Fold 8 hinge angle (`180°` / `118° FLEX`).
 * - Expandable panel ([`FloatingHudExpandedPanel`]) with:
 *   - 1-tap `🔦 Torch` toggle chip (glowing Amber when [isTorchOn] is `true`)
 *   - Glowing 1-tap `✅ Approve VC` chip when `state.vcPendingApprovals > 0`
 *   - 1-tap `⚡ VC + Termux` and `⊞ Prod + Compare` Z Fold 8 Power Split buttons
 */
@Composable
fun FloatingHudOverlay(
    modifier: Modifier = Modifier,
    state: HudTelemetryState? = null,
    isExpanded: Boolean = true,
    onToggleExpand: (() -> Unit)? = null,
    onSingleTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onLaunchVcPlusTermux: (() -> Unit)? = null,
    onLaunchProdPlusCompare: (() -> Unit)? = null,
    isTorchOn: Boolean = false,
    onToggleTorch: () -> Unit = {},
    onQuickApproveVc: () -> Unit = {}
) {
    val context = LocalContext.current
    val controllerState by FloatingHudController.state.collectAsState()
    val effectiveState = state ?: controllerState

    var internalExpanded by remember { mutableStateOf(isExpanded) }
    val resolvedExpanded = if (onToggleExpand != null) isExpanded else internalExpanded

    FloatingHudPillComposePreview(
        state = effectiveState,
        isExpanded = resolvedExpanded,
        onToggleExpand = {
            if (onToggleExpand != null) {
                onToggleExpand()
            } else {
                internalExpanded = !internalExpanded
            }
        },
        onClick = {
            if (onSingleTap != null) {
                onSingleTap()
            } else {
                FloatingHudController.triggerAnywhereSpotlight(context)
            }
        },
        onLaunchVcPlusTermux = onLaunchVcPlusTermux,
        onLaunchProdPlusCompare = onLaunchProdPlusCompare,
        isTorchOn = isTorchOn || effectiveState.isTorchOn,
        onToggleTorch = {
            onToggleTorch()
            FloatingHudController.toggleTorchFromHud(context)
        },
        onQuickApproveVc = {
            onQuickApproveVc()
            FloatingHudController.quickApproveVcFromHud(context)
        },
        modifier = modifier
    )
}

/**
 * Expanded Floating Micro-HUD Panel (`v0.3.0`) rendered beneath the Micro-HUD Pill inside the
 * system-wide / lock-screen overlay and in-launcher preview.
 *
 * Includes:
 * - **1-Tap `🔦 Torch` Toggle Chip**: Glows Amber when [isTorchOn] is `true`.
 * - **Glowing 1-Tap `✅ Approve VC` Chip**: Rendered automatically when `state.vcPendingApprovals > 0`.
 * - **Z Fold 8 Power Split Row**: 1-tap `⚡ VC + Termux` and `⊞ Prod + Compare` multi-window launchers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingHudExpandedPanel(
    state: HudTelemetryState = FloatingHudController.state.value,
    modifier: Modifier = Modifier,
    onLaunchVcPlusTermux: (() -> Unit)? = null,
    onLaunchProdPlusCompare: (() -> Unit)? = null,
    isTorchOn: Boolean = false,
    onToggleTorch: () -> Unit = {},
    onQuickApproveVc: () -> Unit = {}
) {
    val context = LocalContext.current
    val effectiveTorchOn = isTorchOn || state.isTorchOn
    val hingeBadge = state.formattedHingeBadge
    val pendingApprovals = state.vcPendingApprovals

    val infiniteTransition = rememberInfiniteTransition(label = "hudExpandedGlow")
    val approveGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "approveVcChipGlow"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF00B101D),
                        Color(0xE8141C2E)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = when {
                    pendingApprovals > 0 -> FoldLauncherColors.AmberWarning.copy(alpha = 0.72f)
                    effectiveTorchOn -> FoldLauncherColors.AmberWarning.copy(alpha = 0.65f)
                    else -> FoldLauncherColors.GlassBorderStrong
                },
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: 1-Tap `🔦 Torch` toggle chip + conditional glowing `✅ Approve VC` chip + Hinge Badge
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (hingeBadge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(FoldLauncherColors.GlassSurfaceHighlight)
                        .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Hinge $hingeBadge",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.CyanBright
                    )
                }
            }

            // 1-Tap `🔦 Torch` Toggle Chip (glowing Amber when isTorchOn == true)
            val torchShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .clip(torchShape)
                    .background(
                        if (effectiveTorchOn) {
                            FoldLauncherColors.AmberWarning.copy(alpha = 0.30f)
                        } else {
                            FoldLauncherColors.GlassSurfaceHighlight
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (effectiveTorchOn) {
                            FoldLauncherColors.AmberWarning
                        } else {
                            FoldLauncherColors.GlassBorder
                        },
                        shape = torchShape
                    )
                    .clickable {
                        onToggleTorch()
                    }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (effectiveTorchOn) "🔦 Torch ON" else "🔦 Torch",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (effectiveTorchOn) {
                        FoldLauncherColors.AmberWarning
                    } else {
                        FoldLauncherColors.TextPrimary
                    }
                )
            }

            // Glowing 1-Tap `✅ Approve VC` Chip when state.vcPendingApprovals > 0
            if (pendingApprovals > 0) {
                val approveShape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .clip(approveShape)
                        .background(
                            FoldLauncherColors.EmeraldSuccess.copy(alpha = approveGlowAlpha)
                        )
                        .border(
                            width = 1.dp,
                            color = FoldLauncherColors.EmeraldSuccess,
                            shape = approveShape
                        )
                        .clickable {
                            onQuickApproveVc()
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (pendingApprovals > 1) {
                            "✅ Approve VC ($pendingApprovals)"
                        } else {
                            "✅ Approve VC"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.EmeraldSuccess
                    )
                }
            }
        }

        // Row 2: Z Fold 8 Power Split Row (`⚡ VC + Termux` & `⊞ Prod + Compare`)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HingePresetChip(
                label = "⚡ VC + Termux",
                selected = true,
                onClick = {
                    if (onLaunchVcPlusTermux != null) {
                        onLaunchVcPlusTermux()
                    } else {
                        FloatingHudController.launchVcPlusTermuxSplit(context)
                    }
                }
            )
            HingePresetChip(
                label = "⊞ Prod + Compare",
                selected = true,
                onClick = {
                    if (onLaunchProdPlusCompare != null) {
                        onLaunchProdPlusCompare()
                    } else {
                        FloatingHudController.launchProdPlusCompareSplit(context)
                    }
                }
            )
        }
    }
}

/**
 * =================================================================================================
 * IN-LAUNCHER & EXPANDED HUD PREVIEW / CONTROL CARD
 * (`@Composable fun FloatingHudControlCard(...)` & `@Composable fun FloatingHudComposeCard(...)`)
 * =================================================================================================
 *
 * A Jetpack Compose card that can be rendered inside our Launcher's dashboard, settings sheet, or
 * expanded HUD overlay to:
 * 1. Toggle the System-Wide Floating Micro-HUD Pill (`TYPE_ACCESSIBILITY_OVERLAY` +
 *    `FLAG_SHOW_WHEN_LOCKED`) on/off.
 * 2. Preview how the Top-Right Micro-HUD Pill looks in both **collapsed** and **expanded** modes
 *    with live tabular clock, battery/thermal telemetry (`88% · 31°C`), live Z Fold 8 Hinge Angle
 *    (`180°` / `118° FLEX`), pulsing VC Agent status dot, 1-tap `🔦 Torch`, and glowing 1-tap
 *    `✅ Approve VC` chip when `state.vcPendingApprovals > 0`.
 * 3. Launch **Z Fold 8 Power Split Row** 1-tap multi-window pairs:
 *    - `VC + Termux` (`dev.agentconversation` + `com.termux`)
 *    - `Prod + Compare` (`dev.agentconversation` + `dev.agentconversation.compare`)
 * 4. Configure pill opacity (`45%–100%`), scale (`80%–135%`), VC agent status simulation, hinge
 *    angle preview, and test the **0ms Anywhere-Spotlight Trigger**.
 */
@Composable
fun FloatingHudComposeCard(
    modifier: Modifier = Modifier,
    isAccessibilityServiceActive: Boolean = FoldAccessibilityService.instance != null,
    onOpenAccessibilitySettings: (() -> Unit)? = null,
    onTestAnywhereSpotlight: (() -> Unit)? = null,
    onLaunchVcPlusTermux: (() -> Unit)? = null,
    onLaunchProdPlusCompare: (() -> Unit)? = null,
    isTorchOn: Boolean = false,
    onToggleTorch: () -> Unit = {},
    onQuickApproveVc: () -> Unit = {}
) {
    FloatingHudControlCard(
        modifier = modifier,
        isAccessibilityServiceActive = isAccessibilityServiceActive,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onTestAnywhereSpotlight = onTestAnywhereSpotlight,
        onLaunchVcPlusTermux = onLaunchVcPlusTermux,
        onLaunchProdPlusCompare = onLaunchProdPlusCompare,
        isTorchOn = isTorchOn,
        onToggleTorch = onToggleTorch,
        onQuickApproveVc = onQuickApproveVc
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingHudControlCard(
    modifier: Modifier = Modifier,
    isAccessibilityServiceActive: Boolean = FoldAccessibilityService.instance != null,
    onOpenAccessibilitySettings: (() -> Unit)? = null,
    onTestAnywhereSpotlight: (() -> Unit)? = null,
    onLaunchVcPlusTermux: (() -> Unit)? = null,
    onLaunchProdPlusCompare: (() -> Unit)? = null,
    isTorchOn: Boolean = false,
    onToggleTorch: () -> Unit = {},
    onQuickApproveVc: () -> Unit = {}
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
    val serviceHingeAngle by FoldAccessibilityService.hingeAngleFlow.collectAsState()
    val systemTorchActive by SystemTweaker.isTorchActive.collectAsState()

    val vcMonitor = remember(context) { VcBackgroundMonitor.getInstance(context) }
    val vcSummary by vcMonitor.liveSummary.collectAsState()

    LaunchedEffect(serviceHingeAngle) {
        if (serviceHingeAngle != null) {
            val deg = serviceHingeAngle!!
            val posture = when {
                deg in 75f..155f -> "FLEX"
                deg < 25f -> "COVER"
                else -> null
            }
            FloatingHudController.updateHingeTelemetry(deg, posture)
        }
    }

    LaunchedEffect(vcSummary.approvalCount) {
        if (vcSummary.approvalCount > 0) {
            FloatingHudController.setVcPendingApprovals(vcSummary.approvalCount)
        }
    }

    val effectiveTorchOn = isTorchOn || systemTorchActive || hudState.isTorchOn
    val serviceConnected = isAccessibilityServiceActive || FoldAccessibilityService.instance != null
    var previewExpanded by remember { mutableStateOf(true) }

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
        // Header Row: Title, Impossible-on-OneUI / Lock-Screen badge, and Master Toggle Switch
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
                        text = "SYSTEM-WIDE & LOCK-SCREEN MICRO-HUD",
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
                            text = if (hudState.isOverlayAttached) "LIVE + LOCK SCREEN" else "A11Y + KEYGUARD",
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
                    text = "Impossible on Stock One UI: Persistent top-right clock, battery/thermal, Z Fold 8 hinge angle, 1-tap Torch & VC Approve over Lock Screen, apps & DeX.",
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
                    text = "Enable FoldAccessibilityService to project the TYPE_ACCESSIBILITY_OVERLAY + FLAG_SHOW_WHEN_LOCKED pill over Lock Screen, apps & DeX.",
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

        // Live Interactive In-Launcher Preview of the Top-Right Micro-HUD Pill (Collapsed + Expanded)
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
                val hingeBadge = hudState.formattedHingeBadge
                Text(
                    text = if (hingeBadge != null) {
                        "Hinge: $hingeBadge · Tap: Spotlight"
                    } else {
                        "Tap pill: Spotlight · Long-press: VC / Split"
                    },
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
                    state = hudState.copy(isTorchOn = effectiveTorchOn),
                    isExpanded = previewExpanded,
                    onToggleExpand = { previewExpanded = !previewExpanded },
                    onClick = {
                        if (onTestAnywhereSpotlight != null) {
                            onTestAnywhereSpotlight()
                        } else {
                            FloatingHudController.triggerAnywhereSpotlight(context)
                        }
                    },
                    onLaunchVcPlusTermux = onLaunchVcPlusTermux,
                    onLaunchProdPlusCompare = onLaunchProdPlusCompare,
                    isTorchOn = effectiveTorchOn,
                    onToggleTorch = {
                        onToggleTorch()
                        FloatingHudController.toggleTorchFromHud(context)
                    },
                    onQuickApproveVc = {
                        onQuickApproveVc()
                        FloatingHudController.quickApproveVcFromHud(context)
                    }
                )
            }

            // Z Fold 8 Hinge Angle Telemetry Simulation / Status Strip inside Expanded HUD Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Z Fold 8 Hinge Sensor:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FoldLauncherColors.TextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HingePresetChip(
                        label = "180°",
                        selected = hudState.hingeAngleDegrees?.roundToInt() == 180,
                        onClick = {
                            FloatingHudController.updateHingeTelemetry(180f, null)
                        }
                    )
                    HingePresetChip(
                        label = "118° FLEX",
                        selected = hudState.hingeAngleDegrees?.roundToInt() == 118,
                        onClick = {
                            FloatingHudController.updateHingeTelemetry(118f, "FLEX")
                        }
                    )
                    HingePresetChip(
                        label = "Auto",
                        selected = hudState.hingeAngleDegrees == null,
                        onClick = {
                            FloatingHudController.updateHingeTelemetry(null, null)
                        }
                    )
                }
            }
        }

        // Z Fold 8 Power Split Row (Multi-VC & Termux Quick-Split Actions)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.68f))
                .border(1.dp, FoldLauncherColors.GlassBorderStrong, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Z FOLD 8 POWER SPLIT ROW · 1-TAP MULTI-WINDOW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.CyanBright
                )
                Text(
                    text = hudState.formattedHingeBadge?.let { "Posture: $it" } ?: "Inner 120Hz Split",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FoldLauncherColors.TextMuted
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudActionPillButton(
                    label = "⚡ VC + Termux",
                    accent = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (onLaunchVcPlusTermux != null) {
                            onLaunchVcPlusTermux()
                        } else {
                            FloatingHudController.launchVcPlusTermuxSplit(context)
                        }
                    }
                )
                HudActionPillButton(
                    label = "⊞ Prod + Compare",
                    accent = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (onLaunchProdPlusCompare != null) {
                            onLaunchProdPlusCompare()
                        } else {
                            FloatingHudController.launchProdPlusCompareSplit(context)
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
                label = if (effectiveTorchOn) "🔦 Torch ON" else "🔦 Toggle Torch",
                accent = effectiveTorchOn,
                onClick = {
                    onToggleTorch()
                    FloatingHudController.toggleTorchFromHud(context)
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
private fun HingePresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) FoldLauncherColors.CyanGlow else FoldLauncherColors.GlassSurfaceHighlight
            )
            .border(
                1.dp,
                if (selected) FoldLauncherColors.ElectricCyan else FoldLauncherColors.GlassBorder,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) FoldLauncherColors.CyanBright else FoldLauncherColors.TextSecondary
        )
    }
}

@Composable
private fun FloatingHudPillComposePreview(
    state: HudTelemetryState,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLaunchVcPlusTermux: (() -> Unit)? = null,
    onLaunchProdPlusCompare: (() -> Unit)? = null,
    isTorchOn: Boolean = false,
    onToggleTorch: () -> Unit = {},
    onQuickApproveVc: () -> Unit = {}
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
    val hingeBadge = state.formattedHingeBadge
    val effectiveTorchOn = isTorchOn || state.isTorchOn

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
                    color = when {
                        state.vcStatus == VcHudAgentStatus.ERROR ->
                            FoldLauncherColors.CoralDanger.copy(alpha = 0.65f)
                        state.vcStatus == VcHudAgentStatus.APPROVAL_NEEDED || state.vcPendingApprovals > 0 ->
                            FoldLauncherColors.AmberWarning.copy(alpha = 0.72f)
                        effectiveTorchOn ->
                            FoldLauncherColors.AmberWarning.copy(alpha = 0.68f)
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

            // Live Z Fold 8 Hinge Angle Readout (when hingeAngleDegrees != null)
            if (hingeBadge != null) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(12.dp)
                        .background(FoldLauncherColors.TextSecondary.copy(alpha = 0.30f))
                )
                val deg = state.hingeAngleDegrees?.roundToInt() ?: 180
                Text(
                    text = hingeBadge,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (deg in 75..155) {
                            FoldLauncherColors.AmberWarning
                        } else {
                            FoldLauncherColors.CyanBright
                        },
                        fontFeatureSettings = "tnum"
                    )
                )
            }

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

            // Expand / Collapse Pill Preview Toggle
            Text(
                text = if (isExpanded) "▴" else "▾",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.CyanBright,
                modifier = Modifier
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 2.dp)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            FloatingHudExpandedPanel(
                state = state,
                onLaunchVcPlusTermux = onLaunchVcPlusTermux,
                onLaunchProdPlusCompare = onLaunchProdPlusCompare,
                isTorchOn = effectiveTorchOn,
                onToggleTorch = onToggleTorch,
                onQuickApproveVc = onQuickApproveVc
            )
        }
    }
}

@Composable
private fun HudActionPillButton(
    label: String,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
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
            .padding(horizontal = 11.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (accent) FoldLauncherColors.CyanBright else FoldLauncherColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
