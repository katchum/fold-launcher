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
