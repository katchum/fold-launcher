package com.xsun.foldlauncher.posture

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlin.math.abs
import kotlin.math.roundToInt

enum class DevicePosture {
    /** Narrow Z Fold 8 cover screen (portrait or landscape) or standard Android slab phone */
    COMPACT,

    /** Z Fold 8 half-opened horizontal Flex / Tabletop or Tent posture */
    FLEX_TABLETOP,

    /** Z Fold 8 7.6"+ inner display unfolded flat or vertical Book posture */
    EXPANDED;

    companion object {
        val COVER_OR_SLAB = COMPACT
        val TABLETOP = FLEX_TABLETOP
        val BOOK = EXPANDED
        val UNFOLDED_TABLET = EXPANDED
    }
}

typealias PostureMode = DevicePosture

enum class PostureOverride(val displayName: String, val shortLabel: String) {
    AUTO("Sensor Auto", "AUTO"),
    FORCE_COVER("Cover Mode", "COVER"),
    FORCE_FLEX("Flex Tabletop", "FLEX"),
    FORCE_INNER("Inner Unfolded", "INNER"),
    TENT_MODE("Tent Standby", "TENT")
}

/**
 * Mechanical watch-crown haptic detent tick classifications when sweeping the Z Fold 8 hinge (`0°..180°`).
 */
enum class HingeDetentTick(val label: String, val isMajorPostureThreshold: Boolean) {
    NONE("None", false),
    MINOR_15_DEG("15° Micro-Tick", false),
    MAJOR_POSTURE_THRESHOLD("Posture Threshold Click", true)
}

/**
 * Configurable behavior executed when the Z Fold 8 hinge closes (`> 8° -> <= 8°`).
 */
enum class FoldCloseActionMode(val label: String, val badge: String, val description: String) {
    STAY_AWAKE_COVER("Keep Cover Awake", "📱", "Continue seamlessly on Z Fold 8 Cover Screen"),
    AUTO_VC_VOICE("Auto-Talk to VC", "🎙️", "Launch VC Hands-Free Voice when folded shut"),
    TENT_STANDBY("OLED Standby", "⛺", "Show low-power AMOLED clock HUD"),
    INSTANT_LOCK("Instant Lock", "🔒", "Lock device immediately on fold close");

    fun next(): FoldCloseActionMode {
        val entries = values()
        return entries[(ordinal + 1) % entries.size]
    }

    companion object {
        fun fromKey(key: String?): FoldCloseActionMode {
            if (key.isNullOrBlank()) return STAY_AWAKE_COVER
            return values().firstOrNull { it.name.equals(key, ignoreCase = true) } ?: STAY_AWAKE_COVER
        }
    }
}

/**
 * Configurable continuity action executed when unfolding the Z Fold 8 inner display (`< 155° -> >= 155°`).
 */
enum class UnfoldActionMode(
    val key: String,
    val label: String,
    val badge: String,
    val description: String
) {
    INNER_DASHBOARD(
        "dashboard",
        "Inner Cockpit",
        "🖥️",
        "Open 2-column Fold8 Inner Dashboard"
    ),
    AUTO_SPLIT_VC_TERMUX(
        "vc_termux",
        "Auto-Split VC + Termux",
        "⚡",
        "Unfolding automatically stages VC + Termux side-by-side"
    ),
    AUTO_SPLIT_PROD_COMPARE(
        "prod_compare",
        "Auto-Split Prod + Compare",
        "🔀",
        "Unfolding automatically stages VC Prod + VC Compare"
    ),
    AUTO_SPLIT_VC_BROWSER(
        "vc_browser",
        "Auto-Split VC + Browser",
        "🌐",
        "Unfolding automatically stages VC + Browser"
    );

    fun next(): UnfoldActionMode {
        val entries = values()
        return entries[(ordinal + 1) % entries.size]
    }

    companion object {
        fun fromKey(key: String?): UnfoldActionMode {
            if (key.isNullOrBlank()) return INNER_DASHBOARD
            return values().firstOrNull {
                it.key.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true)
            } ?: INNER_DASHBOARD
        }
    }
}

data class FoldingStateSnapshot(
    val isHalfOpened: Boolean = false,
    val isHorizontalHinge: Boolean = true,
    val isSeparating: Boolean = false,
    val hingeTopDp: Int? = null,
    val hingeBottomDp: Int? = null,
    val hingeLeftDp: Int? = null,
    val hingeRightDp: Int? = null
)

data class PostureInfo(
    val posture: DevicePosture,
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int = minOf(widthDp, heightDp),
    val isBookPosture: Boolean = false,
    val isTentMode: Boolean = false,
    val isSeparatingHinge: Boolean = false,
    val hingeTopDp: Int? = null,
    val hingeBottomDp: Int? = null,
    val hingeLeftDp: Int? = null,
    val hingeRightDp: Int? = null,
    val hingeAngleDegrees: Float? = null,
    val postureOverride: PostureOverride = PostureOverride.AUTO
) {
    /** Alias for compatibility with callers reading .mode */
    val mode: DevicePosture
        get() = posture

    val isExpanded: Boolean
        get() = posture == DevicePosture.EXPANDED

    val isUnfoldedInner: Boolean
        get() = posture == DevicePosture.EXPANDED

    val isFlexTabletop: Boolean
        get() = posture == DevicePosture.FLEX_TABLETOP

    val isTabletop: Boolean
        get() = posture == DevicePosture.FLEX_TABLETOP

    val isCompact: Boolean
        get() = posture == DevicePosture.COMPACT

    /** Recommended grid columns based on current Z Fold 8 / Slab screen state */
    val recommendedGridColumns: Int
        get() = when (posture) {
            DevicePosture.COMPACT -> if (widthDp > heightDp) 5 else 4
            DevicePosture.FLEX_TABLETOP -> 5
            DevicePosture.EXPANDED -> if (widthDp >= 840) 6 else 5
        }

    /** Hardware-aware posture & hinge telemetry badge for TopStatusBar and Micro-HUD */
    val statusBadgeText: String
        get() {
            val angleSuffix = hingeAngleDegrees?.let { " ${it.roundToInt()}°" } ?: ""
            return when {
                isTentMode -> "TENT$angleSuffix"
                posture == DevicePosture.FLEX_TABLETOP -> "FLEX$angleSuffix"
                posture == DevicePosture.EXPANDED && isBookPosture -> "BOOK$angleSuffix"
                posture == DevicePosture.EXPANDED ->
                    if (hingeAngleDegrees != null) "INNER$angleSuffix · 120Hz" else "INNER 120Hz"
                widthDp > heightDp -> "COVER LANDSCAPE"
                else -> "COVER / SLAB"
            }
        }
}

typealias PostureState = PostureInfo
typealias DevicePostureState = PostureInfo

object PostureClassifier {
    const val UNFOLDED_MIN_SMALLEST_WIDTH_DP = 580
    const val UNFOLDED_MIN_WIDTH_DP = 600
    const val TYPE_HINGE_ANGLE_SENSOR = 36

    private val MAJOR_POSTURE_THRESHOLDS_DEG = floatArrayOf(15f, 75f, 155f)
    private const val DETENT_HYSTERESIS_DEG = 1.5f

    /**
     * Pure mechanical watch-crown detent detector for the Z Fold 8 hinge (`0°..180°`).
     *
     * - Returns [HingeDetentTick.MAJOR_POSTURE_THRESHOLD] when crossing `15°` (Fold/Tent),
     *   `75°` (Tent/Flex), or `155°` (Flex/Expanded) with at least `1.5°` delta.
     * - Returns [HingeDetentTick.MINOR_15_DEG] when crossing any other `stepDegrees` (`15°`)
     *   multiple (`30°`, `45°`, `60°`, `90°`, `105°`, `120°`, `135°`, `150°`, `165°`).
     * - Returns [HingeDetentTick.NONE] otherwise.
     */
    fun computeHingeDetentCrossed(
        previousAngleDeg: Float,
        currentAngleDeg: Float,
        stepDegrees: Int = 15
    ): HingeDetentTick {
        if (stepDegrees <= 0) return HingeDetentTick.NONE
        val prev = previousAngleDeg.coerceIn(0f, 180f)
        val curr = currentAngleDeg.coerceIn(0f, 180f)
        if (abs(curr - prev) < DETENT_HYSTERESIS_DEG) return HingeDetentTick.NONE

        for (majorThreshold in MAJOR_POSTURE_THRESHOLDS_DEG) {
            if (crossedThreshold(prev, curr, majorThreshold)) {
                return HingeDetentTick.MAJOR_POSTURE_THRESHOLD
            }
        }

        var angle = stepDegrees
        while (angle < 180) {
            val threshold = angle.toFloat()
            if (MAJOR_POSTURE_THRESHOLDS_DEG.none { abs(it - threshold) < 0.1f }) {
                if (crossedThreshold(prev, curr, threshold)) {
                    return HingeDetentTick.MINOR_15_DEG
                }
            }
            angle += stepDegrees
        }

        return HingeDetentTick.NONE
    }

    /**
     * Returns true when the hinge closes across `closeThresholdDeg` (default `8°`).
     */
    fun isFoldCloseTransition(
        previousAngleDeg: Float,
        currentAngleDeg: Float,
        closeThresholdDeg: Float = 8f
    ): Boolean {
        return previousAngleDeg > closeThresholdDeg &&
            currentAngleDeg <= closeThresholdDeg &&
            (previousAngleDeg - currentAngleDeg) >= DETENT_HYSTERESIS_DEG
    }

    /**
     * Returns true when unfolding upward across `unfoldThresholdDeg` (default `155°`)
     * with at least `2.0°` positive hinge movement.
     */
    fun isUnfoldOpenTransition(
        previousAngleDeg: Float,
        currentAngleDeg: Float,
        unfoldThresholdDeg: Float = 155f
    ): Boolean {
        return previousAngleDeg < unfoldThresholdDeg &&
            currentAngleDeg >= unfoldThresholdDeg &&
            (currentAngleDeg - previousAngleDeg) >= 2.0f
    }

    private fun crossedThreshold(prev: Float, curr: Float, threshold: Float): Boolean {
        return (prev < threshold && curr >= threshold) ||
            (prev > threshold && curr <= threshold)
    }

    /**
     * Snapshot-based classification overload supporting hardware hinge sensor (`0°..180°`)
     * and manual posture overrides (`AUTO`, `FORCE_COVER`, `FORCE_FLEX`, `FORCE_INNER`, `TENT_MODE`).
     */
    fun classify(
        widthDp: Int,
        heightDp: Int,
        foldingState: FoldingStateSnapshot?,
        hingeAngleDegrees: Float? = null,
        overrideMode: PostureOverride = PostureOverride.AUTO
    ): PostureInfo {
        return classify(
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = minOf(widthDp, heightDp),
            isHalfOpened = foldingState?.isHalfOpened ?: false,
            isHorizontalHinge = foldingState?.isHorizontalHinge ?: true,
            isSeparating = foldingState?.isSeparating ?: false,
            hingeTopDp = foldingState?.hingeTopDp,
            hingeBottomDp = foldingState?.hingeBottomDp,
            hingeLeftDp = foldingState?.hingeLeftDp,
            hingeRightDp = foldingState?.hingeRightDp,
            hingeAngleDegrees = hingeAngleDegrees,
            overrideMode = overrideMode
        )
    }

    /**
     * Pure classification function for screen dimensions + optional WindowManager hinge bounds +
     * hardware hinge angle sensor (`Sensor.TYPE_HINGE_ANGLE = 36`) + manual `PostureOverride`.
     *
     * Fixes the Z Fold 8 (`SM-F971U1`) landscape Cover Screen bug (`475dp x 751dp`) by requiring
     * BOTH `widthDp >= 600` AND `smallestWidthDp >= 580` for flat `EXPANDED` mode.
     */
    fun classify(
        widthDp: Int,
        heightDp: Int,
        smallestWidthDp: Int = minOf(widthDp, heightDp),
        isHalfOpened: Boolean = false,
        isHorizontalHinge: Boolean = false,
        isSeparating: Boolean = false,
        hingeTopDp: Int? = null,
        hingeBottomDp: Int? = null,
        hingeLeftDp: Int? = null,
        hingeRightDp: Int? = null,
        hingeAngleDegrees: Float? = null,
        overrideMode: PostureOverride = PostureOverride.AUTO
    ): PostureInfo {
        val defaultMidHingeTop = hingeTopDp ?: (heightDp / 2 - 4).coerceAtLeast(0)
        val defaultMidHingeBottom = hingeBottomDp ?: (heightDp / 2 + 4).coerceAtMost(heightDp)

        when (overrideMode) {
            PostureOverride.FORCE_COVER -> {
                return PostureInfo(
                    posture = DevicePosture.COMPACT,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    smallestWidthDp = smallestWidthDp,
                    hingeAngleDegrees = hingeAngleDegrees,
                    postureOverride = overrideMode
                )
            }
            PostureOverride.FORCE_FLEX -> {
                return PostureInfo(
                    posture = DevicePosture.FLEX_TABLETOP,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    smallestWidthDp = smallestWidthDp,
                    isSeparatingHinge = true,
                    hingeTopDp = defaultMidHingeTop,
                    hingeBottomDp = defaultMidHingeBottom,
                    hingeAngleDegrees = hingeAngleDegrees,
                    postureOverride = overrideMode
                )
            }
            PostureOverride.FORCE_INNER -> {
                return PostureInfo(
                    posture = DevicePosture.EXPANDED,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    smallestWidthDp = smallestWidthDp,
                    hingeAngleDegrees = hingeAngleDegrees,
                    postureOverride = overrideMode
                )
            }
            PostureOverride.TENT_MODE -> {
                return PostureInfo(
                    posture = DevicePosture.FLEX_TABLETOP,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    smallestWidthDp = smallestWidthDp,
                    isTentMode = true,
                    isSeparatingHinge = true,
                    hingeTopDp = defaultMidHingeTop,
                    hingeBottomDp = defaultMidHingeBottom,
                    hingeAngleDegrees = hingeAngleDegrees,
                    postureOverride = overrideMode
                )
            }
            PostureOverride.AUTO -> Unit
        }

        val isTentBySensor = hingeAngleDegrees != null &&
            hingeAngleDegrees in 15f..65f &&
            smallestWidthDp < UNFOLDED_MIN_SMALLEST_WIDTH_DP

        if (isHalfOpened && isHorizontalHinge) {
            return PostureInfo(
                posture = DevicePosture.FLEX_TABLETOP,
                widthDp = widthDp,
                heightDp = heightDp,
                smallestWidthDp = smallestWidthDp,
                isTentMode = isTentBySensor,
                isSeparatingHinge = isSeparating,
                hingeTopDp = hingeTopDp ?: defaultMidHingeTop,
                hingeBottomDp = hingeBottomDp ?: defaultMidHingeBottom,
                hingeAngleDegrees = hingeAngleDegrees,
                postureOverride = overrideMode
            )
        }

        if (isHalfOpened && !isHorizontalHinge) {
            return PostureInfo(
                posture = DevicePosture.EXPANDED,
                widthDp = widthDp,
                heightDp = heightDp,
                smallestWidthDp = smallestWidthDp,
                isBookPosture = true,
                isSeparatingHinge = isSeparating,
                hingeLeftDp = hingeLeftDp,
                hingeRightDp = hingeRightDp,
                hingeAngleDegrees = hingeAngleDegrees,
                postureOverride = overrideMode
            )
        }

        // Hardware hinge sensor classification when WindowInfoTracker does not emit HALF_OPENED
        if (isTentBySensor) {
            return PostureInfo(
                posture = DevicePosture.FLEX_TABLETOP,
                widthDp = widthDp,
                heightDp = heightDp,
                smallestWidthDp = smallestWidthDp,
                isTentMode = true,
                isSeparatingHinge = true,
                hingeTopDp = defaultMidHingeTop,
                hingeBottomDp = defaultMidHingeBottom,
                hingeAngleDegrees = hingeAngleDegrees,
                postureOverride = overrideMode
            )
        }

        if (hingeAngleDegrees != null &&
            hingeAngleDegrees in 70f..150f &&
            smallestWidthDp >= UNFOLDED_MIN_SMALLEST_WIDTH_DP
        ) {
            return if (widthDp >= heightDp) {
                PostureInfo(
                    posture = DevicePosture.FLEX_TABLETOP,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    smallestWidthDp = smallestWidthDp,
                    isSeparatingHinge = true,
                    hingeTopDp = defaultMidHingeTop,
                    hingeBottomDp = defaultMidHingeBottom,
                    hingeAngleDegrees = hingeAngleDegrees,
                    postureOverride = overrideMode
                )
            } else {
                PostureInfo(
                    posture = DevicePosture.EXPANDED,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    smallestWidthDp = smallestWidthDp,
                    isBookPosture = true,
                    isSeparatingHinge = true,
                    hingeLeftDp = hingeLeftDp ?: (widthDp / 2 - 4).coerceAtLeast(0),
                    hingeRightDp = hingeRightDp ?: (widthDp / 2 + 4).coerceAtMost(widthDp),
                    hingeAngleDegrees = hingeAngleDegrees,
                    postureOverride = overrideMode
                )
            }
        }

        // Require BOTH widthDp >= 600 AND smallestWidthDp >= 580 so that Z Fold 8's Cover Display
        // in landscape (751dp x 475dp, smallestWidthDp = 475dp) stays COMPACT instead of EXPANDED.
        val isExpanded = widthDp >= UNFOLDED_MIN_WIDTH_DP &&
            smallestWidthDp >= UNFOLDED_MIN_SMALLEST_WIDTH_DP

        return PostureInfo(
            posture = if (isExpanded) DevicePosture.EXPANDED else DevicePosture.COMPACT,
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = smallestWidthDp,
            isSeparatingHinge = isSeparating,
            hingeTopDp = hingeTopDp,
            hingeBottomDp = hingeBottomDp,
            hingeLeftDp = hingeLeftDp,
            hingeRightDp = hingeRightDp,
            hingeAngleDegrees = hingeAngleDegrees,
            postureOverride = overrideMode
        )
    }
}

fun computeHingeDetentCrossed(
    previousAngleDeg: Float,
    currentAngleDeg: Float,
    stepDegrees: Int = 15
): HingeDetentTick = PostureClassifier.computeHingeDetentCrossed(
    previousAngleDeg = previousAngleDeg,
    currentAngleDeg = currentAngleDeg,
    stepDegrees = stepDegrees
)

fun isFoldCloseTransition(
    previousAngleDeg: Float,
    currentAngleDeg: Float,
    closeThresholdDeg: Float = 8f
): Boolean = PostureClassifier.isFoldCloseTransition(
    previousAngleDeg = previousAngleDeg,
    currentAngleDeg = currentAngleDeg,
    closeThresholdDeg = closeThresholdDeg
)

fun isUnfoldOpenTransition(
    previousAngleDeg: Float,
    currentAngleDeg: Float,
    unfoldThresholdDeg: Float = 155f
): Boolean = PostureClassifier.isUnfoldOpenTransition(
    previousAngleDeg = previousAngleDeg,
    currentAngleDeg = currentAngleDeg,
    unfoldThresholdDeg = unfoldThresholdDeg
)

@Composable
fun rememberHingeAngleDegrees(): Float? {
    val context = LocalContext.current
    var angleDegrees by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hingeSensor: Sensor? = sensorManager?.getDefaultSensor(PostureClassifier.TYPE_HINGE_ANGLE_SENSOR)
        if (sensorManager == null || hingeSensor == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val value = event?.values?.firstOrNull() ?: return
                    val coerced = value.coerceIn(0f, 180f)
                    val prev = angleDegrees
                    if (prev == null || abs(coerced - prev) >= 0.5f) {
                        angleDegrees = coerced
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            try {
                sensorManager.registerListener(listener, hingeSensor, SensorManager.SENSOR_DELAY_UI)
            } catch (_: Exception) {
            }
            onDispose {
                try {
                    sensorManager.unregisterListener(listener)
                } catch (_: Exception) {
                }
            }
        }
    }

    return angleDegrees
}

@Composable
fun rememberPostureInfo(
    activity: Activity? = null,
    overrideMode: PostureOverride = PostureOverride.AUTO
): PostureInfo {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val hingeAngleDegrees = rememberHingeAngleDegrees()

    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val smallestWidthDp = configuration.smallestScreenWidthDp

    if (activity == null) {
        return PostureClassifier.classify(
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = smallestWidthDp,
            hingeAngleDegrees = hingeAngleDegrees,
            overrideMode = overrideMode
        )
    }

    val layoutInfoFlow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
    }
    val windowLayoutInfo by layoutInfoFlow.collectAsState(initial = null)

    val foldingFeature = windowLayoutInfo
        ?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull()

    if (foldingFeature != null) {
        val bounds = foldingFeature.bounds
        val hingeTopDp = with(density) { bounds.top.toDp().value.toInt() }
        val hingeBottomDp = with(density) { bounds.bottom.toDp().value.toInt() }
        val hingeLeftDp = with(density) { bounds.left.toDp().value.toInt() }
        val hingeRightDp = with(density) { bounds.right.toDp().value.toInt() }

        return PostureClassifier.classify(
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = smallestWidthDp,
            isHalfOpened = foldingFeature.state == FoldingFeature.State.HALF_OPENED,
            isHorizontalHinge = foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL,
            isSeparating = foldingFeature.isSeparating,
            hingeTopDp = hingeTopDp,
            hingeBottomDp = hingeBottomDp,
            hingeLeftDp = hingeLeftDp,
            hingeRightDp = hingeRightDp,
            hingeAngleDegrees = hingeAngleDegrees,
            overrideMode = overrideMode
        )
    }

    return PostureClassifier.classify(
        widthDp = widthDp,
        heightDp = heightDp,
        smallestWidthDp = smallestWidthDp,
        hingeAngleDegrees = hingeAngleDegrees,
        overrideMode = overrideMode
    )
}

@Composable
fun rememberDevicePosture(
    activity: Activity? = null,
    overrideMode: PostureOverride = PostureOverride.AUTO
): PostureInfo = rememberPostureInfo(activity = activity, overrideMode = overrideMode)

@Composable
fun rememberPostureState(
    activity: Activity? = null,
    overrideMode: PostureOverride = PostureOverride.AUTO
): PostureInfo = rememberPostureInfo(activity = activity, overrideMode = overrideMode)
