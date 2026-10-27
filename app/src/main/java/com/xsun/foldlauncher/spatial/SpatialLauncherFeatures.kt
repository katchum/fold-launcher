package com.xsun.foldlauncher.spatial

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.ui.animation.LauncherSpringSpecs
import com.xsun.foldlauncher.ui.animation.RadialTriggerCorner
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.ui.theme.IconShapePreset
import com.xsun.foldlauncher.ui.theme.iconShapeForPreset
import com.xsun.foldlauncher.vc.VcBackgroundMonitor
import com.xsun.foldlauncher.vc.VcLiveSummary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Normalized 120Hz spatial device tilt & hinge state derived from [Sensor.TYPE_GAME_ROTATION_VECTOR]
 * (or [Sensor.TYPE_ACCELEROMETER] fallback) and hardware [Sensor.TYPE_HINGE_ANGLE] (`36`)
 * with low-pass exponential smoothing.
 *
 * @property pitch Front-to-back tilt normalized to `-1f..1f`
 * @property roll Left-to-right tilt normalized to `-1f..1f`
 * @property hingeAngleFactor Optional Z Fold 8 hinge posture depth factor (`0f..1f`)
 * @property isSensorActive True when hardware motion sensors are actively streaming
 * @property hingeAngleDegrees Continuous physical hinge angle (`0f..180f`) from `Sensor.TYPE_HINGE_ANGLE` when available
 */
@Immutable
data class SpatialTiltState(
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val hingeAngleFactor: Float = 0f,
    val isSensorActive: Boolean = false,
    val hingeAngleDegrees: Float? = null
) {
    val magnitude: Float
        get() = hypot(pitch, roll).coerceIn(0f, 1f)
}

/**
 * Pure 3D parallax transformation metrics computed by [SpatialMath.computeParallaxOffset].
 */
@Immutable
data class ParallaxOffset(
    val rotationXDeg: Float,
    val rotationYDeg: Float,
    val translationXPx: Float,
    val translationYPx: Float,
    val specularShift: Float,
    val hingeAngleDegrees: Float? = null,
    val hingeSpecularShift: Float = 0f
) {
    val offsetX: Float get() = translationXPx
    val offsetY: Float get() = translationYPx
}

/**
 * Polar coordinate and layout metrics for an item in the One-Handed Radial Thumb Arc ("Arc Deck").
 */
@Immutable
data class ArcItemPosition(
    val index: Int,
    val xDp: Float,
    val yDp: Float,
    val angleDeg: Float,
    val radiusDp: Float
) {
    val x: Float get() = xDp
    val y: Float get() = yDp
}

/**
 * Pure mathematical helper functions for 120Hz Spatial Depth Parallax, Hardware Hinge-Angle
 * Specular Shift, Radial Thumb Arc geometry, and Flex-Mode Virtual Trackpad cursor projection.
 *
 * Designed with zero Android runtime dependencies so all math can be unit-tested directly on JVM.
 */
object SpatialMath {

    /** Hardware `android.sensor.hinge_angle` (`Sensor.TYPE_HINGE_ANGLE`) constant (`36`). */
    const val TYPE_HINGE_ANGLE_SENSOR: Int = 36

    /**
     * Exponential low-pass filter to eliminate high-frequency MEMS sensor noise while keeping
     * 120Hz sub-frame responsiveness.
     */
    fun lowPassFilter(current: Float, target: Float, alpha: Float = 0.18f): Float {
        val clampedAlpha = alpha.coerceIn(0.01f, 1f)
        return current + clampedAlpha * (target - current)
    }

    /**
     * Normalizes raw sensor tilt (in radians or m/s^2 gravity projection) into `-1f..1f`.
     */
    fun normalizeTilt(rawValue: Float, maxRange: Float = 0.65f): Float {
        if (maxRange <= 0.0001f) return 0f
        return (rawValue / maxRange).coerceIn(-1f, 1f)
    }

    /**
     * Computes a normalized specular light shift (`-0.35f..0.35f`) driven by the physical
     * Z Fold 8 hinge angle (`0°..180°`). Sweeps smoothly across `90°` half-folded Flex posture.
     */
    fun computeHingeSpecularShift(hingeAngleDegrees: Float?): Float {
        if (hingeAngleDegrees == null) return 0f
        val clamped = hingeAngleDegrees.coerceIn(0f, 180f)
        return (((clamped - 90f) / 90f) * 0.35f).coerceIn(-0.35f, 0.35f)
    }

    /**
     * Formats a live hinge angle (`0°..180°`) into a concise hardware posture telemetry badge
     * (e.g., `"48° TENT"`, `"115° FLEX"`, `"178° FLAT"`, `"0° CLOSED"`).
     */
    fun formatHingePostureBadge(hingeAngleDegrees: Float): String {
        val coerced = hingeAngleDegrees.coerceIn(0f, 180f)
        val rounded = coerced.roundToInt()
        val modeLabel = when {
            coerced < 15f -> "CLOSED"
            coerced <= 65f -> "TENT"
            coerced <= 150f -> "FLEX"
            else -> "FLAT"
        }
        return "${rounded}° $modeLabel"
    }

    /**
     * Computes 3D rotation angles (degrees), pixel translations, and specular highlight shift
     * from normalized [pitch] (`-1f..1f`), [roll] (`-1f..1f`), [depthLayer], and optional
     * physical [hingeAngleDegrees] (`0°..180°`).
     */
    fun computeParallaxOffset(
        pitch: Float,
        roll: Float,
        depthLayer: Float = 1f,
        maxTranslationPx: Float = 14f,
        maxRotationDeg: Float = 4.5f,
        hingeAngleDegrees: Float? = null
    ): ParallaxOffset {
        val clampedPitch = pitch.coerceIn(-1f, 1f)
        val clampedRoll = roll.coerceIn(-1f, 1f)
        val clampedDepth = depthLayer.coerceIn(0f, 3f)
        val coercedHinge = hingeAngleDegrees?.coerceIn(0f, 180f)
        val hingeShift = computeHingeSpecularShift(coercedHinge)

        val rotationXDeg = -clampedPitch * maxRotationDeg * clampedDepth
        val rotationYDeg = clampedRoll * maxRotationDeg * clampedDepth
        val translationXPx = clampedRoll * maxTranslationPx * clampedDepth
        val translationYPx = clampedPitch * maxTranslationPx * clampedDepth
        val baseSpecular = (clampedRoll - clampedPitch) * 0.5f
        val specularShift = (baseSpecular + hingeShift).coerceIn(-1f, 1f)

        return ParallaxOffset(
            rotationXDeg = rotationXDeg,
            rotationYDeg = rotationYDeg,
            translationXPx = translationXPx,
            translationYPx = translationYPx,
            specularShift = specularShift,
            hingeAngleDegrees = coercedHinge,
            hingeSpecularShift = hingeShift
        )
    }

    /**
     * Computes the polar (xDp, yDp) offset relative to the thumb corner anchor for item [index]
     * out of [totalCount] apps on the One-Handed Radial Thumb Arc ("Arc Deck").
     *
     * - For Right-Handed mode (`isRightHanded = true`), the anchor is at the bottom-right corner
     *   and items fan out across the upper-left quadrant (`190°..260°` in screen coordinates where
     *   `0°` is right, `90°` is down, `180°` is left, `270°` is up).
     * - For Left-Handed mode (`isRightHanded = false`), the anchor is at the bottom-left corner
     *   and items fan out across the upper-right quadrant (`350°..280°`).
     */
    fun computeArcItemPosition(
        index: Int,
        totalCount: Int,
        radiusDp: Float = 142f,
        isRightHanded: Boolean = true,
        fanProgress: Float = 1f
    ): ArcItemPosition {
        val safeTotal = totalCount.coerceAtLeast(1)
        val safeIndex = index.coerceIn(0, safeTotal - 1)
        val clampedProgress = fanProgress.coerceIn(0f, 1.2f)

        // Screen-space angles: 180 deg = West (left), 270 deg = North (up), 360/0 deg = East (right)
        val startAngleDeg = if (isRightHanded) 190f else 350f
        val endAngleDeg = if (isRightHanded) 260f else 280f

        val fraction = if (safeTotal == 1) {
            0.5f
        } else {
            safeIndex.toFloat() / (safeTotal - 1).toFloat()
        }

        val angleDeg = startAngleDeg + (endAngleDeg - startAngleDeg) * fraction
        val angleRad = angleDeg * (PI.toFloat() / 180f)
        val effectiveRadius = radiusDp * clampedProgress

        val xDp = cos(angleRad) * effectiveRadius
        val yDp = sin(angleRad) * effectiveRadius

        return ArcItemPosition(
            index = safeIndex,
            xDp = xDp,
            yDp = yDp,
            angleDeg = angleDeg,
            radiusDp = effectiveRadius
        )
    }

    /**
     * Computes the polar angle in degrees (`0f..360f`) for a thumb drag vector `(dxPx, dyPx)`
     * relative to the anchor origin.
     */
    fun computeThumbAngleDeg(dxPx: Float, dyPx: Float): Float {
        val rawDeg = atan2(dyPx, dxPx) * (180f / PI.toFloat())
        return if (rawDeg < 0f) rawDeg + 360f else rawDeg
    }

    /**
     * Resolves which arc item index (`0 until totalCount`) is closest to a user's thumb drag
     * vector `(dxPx, dyPx)` relative to the thumb anchor origin, or `-1` if the thumb is too
     * close to the center hub or outside the active radial ring.
     */
    fun resolveHoveredArcIndex(
        dxPx: Float,
        dyPx: Float,
        totalCount: Int,
        minRadiusPx: Float,
        maxRadiusPx: Float,
        isRightHanded: Boolean
    ): Int {
        if (totalCount <= 0) return -1
        val distance = hypot(dxPx, dyPx)
        if (distance < minRadiusPx || distance > maxRadiusPx) return -1

        var bestIndex = -1
        var minDistanceSq = Float.MAX_VALUE
        val targetRadius = (minRadiusPx + maxRadiusPx) * 0.5f

        for (i in 0 until totalCount) {
            val pos = computeArcItemPosition(
                index = i,
                totalCount = totalCount,
                radiusDp = targetRadius,
                isRightHanded = isRightHanded,
                fanProgress = 1f
            )
            val diffX = dxPx - pos.xDp
            val diffY = dyPx - pos.yDp
            val distSq = diffX * diffX + diffY * diffY
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                bestIndex = i
            }
        }
        return bestIndex
    }

    /**
     * Updates normalized trackpad cursor coordinates (`0f..1f`, `0f..1f`) given a finger delta
     * in pixels and trackpad dimensions.
     */
    fun updateTrackpadCursor(
        currentNormalizedX: Float,
        currentNormalizedY: Float,
        deltaXPx: Float,
        deltaYPx: Float,
        padWidthPx: Float,
        padHeightPx: Float,
        sensitivity: Float = 1.35f
    ): Pair<Float, Float> {
        val safeW = padWidthPx.coerceAtLeast(1f)
        val safeH = padHeightPx.coerceAtLeast(1f)
        val nextX = (currentNormalizedX + (deltaXPx / safeW) * sensitivity).coerceIn(0f, 1f)
        val nextY = (currentNormalizedY + (deltaYPx / safeH) * sensitivity).coerceIn(0f, 1f)
        return nextX to nextY
    }

    /**
     * Computes a deterministic 2D Lissajous pixel-shift offset `(dxDp, dyDp)` within
     * `[-maxRadiusDp, +maxRadiusDp]` from [minuteOfDay] (`0..1439`) to prevent static
     * AMOLED burn-in on the Galaxy Z Fold 8 and S23 Ultra during Tent Mode or Lock-Screen Cockpit standby.
     */
    fun computeOledPixelShiftOffset(
        minuteOfDay: Int,
        maxRadiusDp: Float = 6f
    ): Pair<Float, Float> {
        if (maxRadiusDp <= 0f) return 0f to 0f
        val safeRadius = maxRadiusDp.coerceAtMost(24f)
        val normalizedMinute = ((minuteOfDay % 1440) + 1440) % 1440
        val baseAngleRad = (normalizedMinute.toFloat() / 60f) * (2f * PI.toFloat())
        // 3:2 harmonic Lissajous curve with phase offset so pixels trace a smooth 2D orbit
        val dxDp = (sin(baseAngleRad * 3f) * safeRadius).coerceIn(-safeRadius, safeRadius)
        val dyDp = (cos(baseAngleRad * 2f) * safeRadius).coerceIn(-safeRadius, safeRadius)
        return dxDp to dyDp
    }
}

/**
 * Top-level pure function for AMOLED Burn-In Protection Lissajous Pixel-Shift (`[-maxRadiusDp, +maxRadiusDp]`).
 * Delegates to [SpatialMath.computeOledPixelShiftOffset] so both top-level and `SpatialMath` callers work seamlessly.
 */
fun computeOledPixelShiftOffset(
    minuteOfDay: Int,
    maxRadiusDp: Float = 6f
): Pair<Float, Float> = SpatialMath.computeOledPixelShiftOffset(
    minuteOfDay = minuteOfDay,
    maxRadiusDp = maxRadiusDp
)

/**
 * Subscribes to Android's [SensorManager] (`TYPE_GAME_ROTATION_VECTOR` or `TYPE_ACCELEROMETER` fallback,
 * plus hardware `Sensor.TYPE_HINGE_ANGLE = 36` on Galaxy Z Fold 8) at 120Hz-friendly `SENSOR_DELAY_GAME`
 * and returns a smoothed [SpatialTiltState].
 */
@Composable
fun rememberSpatialTiltState(
    enabled: Boolean = true,
    hingeAngleFactor: Float = 0f,
    smoothingAlpha: Float = 0.18f
): SpatialTiltState {
    val context = LocalContext.current
    var pitch by remember { mutableFloatStateOf(0f) }
    var roll by remember { mutableFloatStateOf(0f) }
    var hardwareHingeDegrees by remember { mutableStateOf<Float?>(null) }
    var sensorActive by remember { mutableStateOf(false) }

    DisposableEffect(context, enabled, smoothingAlpha) {
        if (!enabled) {
            sensorActive = false
            return@DisposableEffect onDispose {}
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            sensorActive = false
            return@DisposableEffect onDispose {}
        }

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = if (rotationSensor == null) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } else {
            null
        }
        val activeMotionSensor = rotationSensor ?: accelSensor
        val hingeSensor = sensorManager.getDefaultSensor(SpatialMath.TYPE_HINGE_ANGLE_SENSOR)

        if (activeMotionSensor == null && hingeSensor == null) {
            sensorActive = false
            return@DisposableEffect onDispose {}
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor.type) {
                    Sensor.TYPE_GAME_ROTATION_VECTOR,
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        // orientationAngles[1] = pitch (radians), orientationAngles[2] = roll (radians)
                        // Center pitch around natural ~25 deg (0.44 rad) viewing hold angle
                        val centeredPitchRad = orientationAngles[1] + 0.42f
                        val rawRollRad = orientationAngles[2]
                        val targetPitch = SpatialMath.normalizeTilt(centeredPitchRad, maxRange = 0.65f)
                        val targetRoll = SpatialMath.normalizeTilt(rawRollRad, maxRange = 0.65f)
                        pitch = SpatialMath.lowPassFilter(pitch, targetPitch, smoothingAlpha)
                        roll = SpatialMath.lowPassFilter(roll, targetRoll, smoothingAlpha)
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        if (event.values.size >= 2) {
                            val gx = -event.values[0]
                            val gy = event.values[1] - 4.2f
                            val targetRoll = SpatialMath.normalizeTilt(gx, maxRange = 6.5f)
                            val targetPitch = SpatialMath.normalizeTilt(gy, maxRange = 6.5f)
                            pitch = SpatialMath.lowPassFilter(pitch, targetPitch, smoothingAlpha)
                            roll = SpatialMath.lowPassFilter(roll, targetRoll, smoothingAlpha)
                        }
                    }

                    SpatialMath.TYPE_HINGE_ANGLE_SENSOR -> {
                        val rawDeg = event.values.firstOrNull()?.coerceIn(0f, 180f) ?: return
                        val currentDeg = hardwareHingeDegrees
                        hardwareHingeDegrees = if (currentDeg == null) {
                            rawDeg
                        } else {
                            SpatialMath.lowPassFilter(currentDeg, rawDeg, alpha = 0.35f)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        var anyRegistered = false
        if (activeMotionSensor != null) {
            try {
                anyRegistered = sensorManager.registerListener(
                    listener,
                    activeMotionSensor,
                    SensorManager.SENSOR_DELAY_GAME
                ) || anyRegistered
            } catch (_: Exception) {
            }
        }
        if (hingeSensor != null) {
            try {
                anyRegistered = sensorManager.registerListener(
                    listener,
                    hingeSensor,
                    SensorManager.SENSOR_DELAY_GAME
                ) || anyRegistered
            } catch (_: Exception) {
            }
        }
        sensorActive = anyRegistered

        onDispose {
            try {
                sensorManager.unregisterListener(listener)
            } catch (_: Exception) {
            }
        }
    }

    val dynamicHingeFactor = hardwareHingeDegrees?.let { deg ->
        ((180f - deg.coerceIn(0f, 180f)) / 180f).coerceIn(0f, 1f)
    } ?: hingeAngleFactor.coerceIn(0f, 1f)

    return SpatialTiltState(
        pitch = pitch,
        roll = roll,
        hingeAngleFactor = dynamicHingeFactor,
        isSensorActive = sensorActive,
        hingeAngleDegrees = hardwareHingeDegrees
    )
}

/**
 * Convenience Composable that subscribes to 120Hz gyroscope + hardware hinge angle (`Sensor.TYPE_HINGE_ANGLE = 36`)
 * and returns a ready-to-render [ParallaxOffset] (including `hingeAngleDegrees` and `hingeSpecularShift`).
 */
@Composable
fun rememberGyroParallaxOffset(
    enabled: Boolean = true,
    depthLayer: Float = 1f,
    smoothingAlpha: Float = 0.18f
): ParallaxOffset {
    val tilt = rememberSpatialTiltState(
        enabled = enabled,
        smoothingAlpha = smoothingAlpha
    )
    val density = LocalDensity.current
    val maxTranslationPx = remember(density) { with(density) { 10.dp.toPx() } }

    return remember(tilt.pitch, tilt.roll, tilt.hingeAngleFactor, tilt.hingeAngleDegrees, depthLayer, maxTranslationPx) {
        val effectivePitch = (tilt.pitch + tilt.hingeAngleFactor * 0.2f).coerceIn(-1f, 1f)
        SpatialMath.computeParallaxOffset(
            pitch = effectivePitch,
            roll = tilt.roll,
            depthLayer = depthLayer,
            maxTranslationPx = maxTranslationPx,
            maxRotationDeg = 4.2f,
            hingeAngleDegrees = tilt.hingeAngleDegrees
        )
    }
}

/**
 * Applies 120Hz 3D depth parallax (`rotationX`, `rotationY`, `translationX`, `translationY`)
 * and a dynamic specular glass edge sheen (including hardware Z Fold 8 hinge-angle specular sweep)
 * that reacts in real time as you tilt or fold/unfold the Galaxy Z Fold 8 or S23 Ultra.
 */
fun Modifier.spatialParallaxCard(
    tilt: SpatialTiltState,
    depthLayer: Float = 1f,
    cornerRadius: Dp = 22.dp,
    enableSpecularSheen: Boolean = true
): Modifier = composed {
    val density = LocalDensity.current
    val maxTranslationPx = remember(density) { with(density) { 10.dp.toPx() } }

    val transform = remember(
        tilt.pitch,
        tilt.roll,
        tilt.hingeAngleFactor,
        tilt.hingeAngleDegrees,
        depthLayer,
        maxTranslationPx
    ) {
        val effectivePitch = (tilt.pitch + tilt.hingeAngleFactor * 0.2f).coerceIn(-1f, 1f)
        SpatialMath.computeParallaxOffset(
            pitch = effectivePitch,
            roll = tilt.roll,
            depthLayer = depthLayer,
            maxTranslationPx = maxTranslationPx,
            maxRotationDeg = 4.2f,
            hingeAngleDegrees = tilt.hingeAngleDegrees
        )
    }

    val animatedRotX by animateFloatAsState(
        targetValue = transform.rotationXDeg,
        animationSpec = LauncherSpringSpecs.SpatialParallaxSpring,
        label = "SpatialRotX"
    )
    val animatedRotY by animateFloatAsState(
        targetValue = transform.rotationYDeg,
        animationSpec = LauncherSpringSpecs.SpatialParallaxSpring,
        label = "SpatialRotY"
    )
    val animatedTransX by animateFloatAsState(
        targetValue = transform.translationXPx,
        animationSpec = LauncherSpringSpecs.SpatialParallaxSpring,
        label = "SpatialTransX"
    )
    val animatedTransY by animateFloatAsState(
        targetValue = transform.translationYPx,
        animationSpec = LauncherSpringSpecs.SpatialParallaxSpring,
        label = "SpatialTransY"
    )

    this
        .graphicsLayer {
            rotationX = animatedRotX
            rotationY = animatedRotY
            translationX = animatedTransX
            translationY = animatedTransY
            cameraDistance = 16f * density.density
        }
        .drawWithContent {
            drawContent()
            val hasTiltOrHinge = abs(tilt.roll) > 0.01f ||
                abs(tilt.pitch) > 0.01f ||
                tilt.hingeAngleDegrees != null
            if (enableSpecularSheen && hasTiltOrHinge) {
                val shift = transform.specularShift
                val sheenStart = Offset(
                    x = size.width * (0.2f + shift * 0.35f),
                    y = 0f
                )
                val sheenEnd = Offset(
                    x = size.width * (0.8f + shift * 0.35f),
                    y = size.height
                )
                val hingeBoost = abs(transform.hingeSpecularShift) * 0.12f
                val sheenAlpha = (0.04f + tilt.magnitude * 0.10f + hingeBoost).coerceIn(0.03f, 0.16f)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            FoldLauncherColors.CyanBright.copy(alpha = sheenAlpha),
                            Color.White.copy(alpha = sheenAlpha * 0.7f),
                            Color.Transparent
                        ),
                        start = sheenStart,
                        end = sheenEnd
                    )
                )
            }
        }
}

/**
 * Z Fold 8 Tent Mode Standby HUD (`hingeAngle in 15°..65°` or manual `PostureOverride.TENT_MODE`).
 *
 * When the Galaxy Z Fold 8 (`SM-F971U1`) is propped up on a desk like a tent or bedside/desk display,
 * renders a low-power, high-contrast OLED Ambient Desk HUD featuring:
 * - Oversized OLED Desk Clock & Date
 * - Live Hardware Hinge Angle Gauge (`e.g. 48° TENT` / `115° FLEX`)
 * - Live VC Agent Telemetry Ticker (active agents, pending approvals, 1-tap `Talk to VC`)
 * - Quick Media Transport & `Exit Tent Mode` controls
 */
@Composable
fun TentModeStandbyHud(
    hingeAngleDegrees: Float? = null,
    batteryPct: Int = 100,
    isCharging: Boolean = false,
    nextEventText: String = "",
    vcSummary: VcLiveSummary? = null,
    onTalkToVc: (() -> Unit)? = null,
    onOpenVcApp: (() -> Unit)? = null,
    onMediaPrev: (() -> Unit)? = null,
    onMediaPlayPause: (() -> Unit)? = null,
    onMediaNext: (() -> Unit)? = null,
    onExitTentMode: () -> Unit = {},
    isTorchOn: Boolean = false,
    onToggleTorch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val vcMonitor = remember(context) { VcBackgroundMonitor.getInstance(context) }
    val monitorSummary by vcMonitor.liveSummary.collectAsState()
    val effectiveSummary = vcSummary ?: monitorSummary

    val spatialTilt = rememberSpatialTiltState(enabled = true)
    val effectiveHingeDeg = hingeAngleDegrees ?: spatialTilt.hingeAngleDegrees ?: 48f
    val hingeBadgeText = remember(effectiveHingeDeg) {
        SpatialMath.formatHingePostureBadge(effectiveHingeDeg)
    }

    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(10_000L)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val formattedTime = remember(currentTimeMillis) { timeFormat.format(Date(currentTimeMillis)) }
    val formattedDate = remember(currentTimeMillis) { dateFormat.format(Date(currentTimeMillis)).uppercase() }
    val minuteOfDay = remember(currentTimeMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
    val (pixelShiftX, pixelShiftY) = remember(minuteOfDay) {
        computeOledPixelShiftOffset(minuteOfDay = minuteOfDay, maxRadiusDp = 6f)
    }

    fun dispatchSystemMediaKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val now = SystemClock.uptimeMillis()
        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        } catch (_: Exception) {
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .spatialParallaxCard(tilt = spatialTilt, depthLayer = 0.75f)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        FoldLauncherColors.OledBlack,
                        FoldLauncherColors.DeepVoid,
                        FoldLauncherColors.GlassSurface
                    )
                )
            )
            .border(1.5.dp, FoldLauncherColors.GlassBorderStrong, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Row: Live Hinge Angle Gauge Pill + Battery & Standby Telemetry
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mini Radial Hinge Angle Arc Gauge
                Canvas(modifier = Modifier.size(28.dp)) {
                    val strokeWidth = 3.dp.toPx()
                    drawArc(
                        color = FoldLauncherColors.GlassBorder,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                    val normalizedSweep = (effectiveHingeDeg.coerceIn(0f, 180f) / 180f) * 180f
                    drawArc(
                        color = FoldLauncherColors.ElectricCyan,
                        startAngle = 180f,
                        sweepAngle = normalizedSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                }

                Column {
                    Text(
                        text = "Z FOLD 8 TENT STANDBY HUD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.ElectricCyan
                    )
                    Text(
                        text = "$hingeBadgeText · 120Hz OLED LOW-POWER DECK",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.CyanBright
                    )
                }
            }

            // Battery & Charging Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(FoldLauncherColors.GlassSurfaceElevated)
                    .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isCharging) "⚡ $batteryPct%" else "🔋 $batteryPct%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCharging) FoldLauncherColors.EmeraldSuccess else FoldLauncherColors.TextPrimary
                )
            }
        }

        // 2. Center Row: Ultra-Crisp OLED Desk Clock & Date (with Lissajous Burn-In Pixel Shift) + Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .offset(x = pixelShiftX.dp, y = pixelShiftY.dp)
            ) {
                Text(
                    text = formattedTime,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.TextPrimary,
                    letterSpacing = (-1.5).sp
                )
                Text(
                    text = formattedDate + if (nextEventText.isNotBlank()) " · $nextEventText" else "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FoldLauncherColors.CyanBright,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1-Tap Bedside Torch Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isTorchOn) {
                                FoldLauncherColors.AmberWarning.copy(alpha = 0.24f)
                            } else {
                                FoldLauncherColors.GlassSurfaceElevated
                            }
                        )
                        .border(
                            width = 1.2.dp,
                            color = if (isTorchOn) FoldLauncherColors.AmberWarning else FoldLauncherColors.GlassBorder,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleTorch()
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isTorchOn) "🔦 Torch ON" else "🔦 Torch",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isTorchOn) FoldLauncherColors.AmberWarning else FoldLauncherColors.TextPrimary
                        )
                        Text(
                            text = if (isTorchOn) "Tap to Off" else "Bedside LED",
                            fontSize = 10.sp,
                            color = FoldLauncherColors.TextSecondary
                        )
                    }
                }

                // 1-Tap Talk to VC Voice Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(FoldLauncherColors.CyanGlow)
                        .border(1.2.dp, FoldLauncherColors.ElectricCyan, RoundedCornerShape(18.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (onTalkToVc != null) {
                                onTalkToVc()
                            } else {
                                vcMonitor.launchVcVoiceTalk()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🎙 Talk to VC",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FoldLauncherColors.CyanBright
                        )
                        Text(
                            text = "Instant Voice Turn",
                            fontSize = 10.sp,
                            color = FoldLauncherColors.TextSecondary
                        )
                    }
                }
            }
        }

        // 3. Live VC Agent Telemetry Ticker
        val workingCount = effectiveSummary.workingCount
        val approvalCount = effectiveSummary.approvalCount
        val activeAgents = effectiveSummary.activeAgents
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FoldLauncherColors.GlassSurfaceElevated)
                .border(
                    width = 1.dp,
                    color = if (approvalCount > 0) FoldLauncherColors.AmberWarning else FoldLauncherColors.GlassBorder,
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (onOpenVcApp != null) {
                        onOpenVcApp()
                    } else {
                        vcMonitor.launchVc(null)
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE VC AGENT TELEMETRY TICKER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.ElectricCyan
                )
                Text(
                    text = when {
                        approvalCount > 0 -> "⚠ $approvalCount APPROVAL NEEDED"
                        workingCount > 0 -> "● $workingCount WORKING"
                        else -> "✓ ${activeAgents.size} AGENTS READY"
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        approvalCount > 0 -> FoldLauncherColors.AmberWarning
                        workingCount > 0 -> FoldLauncherColors.EmeraldSuccess
                        else -> FoldLauncherColors.CyanBright
                    }
                )
            }

            val tickerDetails = remember(effectiveSummary) {
                if (activeAgents.isNotEmpty()) {
                    activeAgents.take(4).joinToString("  •  ") { agent ->
                        "${agent.name} (${agent.status.uppercase()})"
                    }
                } else {
                    effectiveSummary.headlineSummary
                }
            }
            Text(
                text = tickerDetails,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = FoldLauncherColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 4. Quick Media Transport & Exit Tent Mode Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackpadDeckButton(
                label = "⏮ Prev",
                accent = FoldLauncherColors.TextPrimary,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (onMediaPrev != null) onMediaPrev() else dispatchSystemMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                },
                modifier = Modifier.weight(1f)
            )
            TrackpadDeckButton(
                label = "⏯ Play/Pause",
                accent = FoldLauncherColors.CyanBright,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (onMediaPlayPause != null) onMediaPlayPause() else dispatchSystemMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                },
                modifier = Modifier.weight(1.25f)
            )
            TrackpadDeckButton(
                label = "⏭ Next",
                accent = FoldLauncherColors.TextPrimary,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (onMediaNext != null) onMediaNext() else dispatchSystemMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                },
                modifier = Modifier.weight(1f)
            )
            TrackpadDeckButton(
                label = "✕ Exit Tent",
                accent = FoldLauncherColors.CoralDanger,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onExitTentMode()
                },
                modifier = Modifier.weight(1.15f)
            )
        }
    }
}

/**
 * One-Handed Radial Thumb Arc ("Arc Deck") overlay for tall slab screens (Galaxy S23 Ultra)
 * and narrow Z Fold 8 cover screens.
 *
 * Anchors a compact thumb trigger handle to the lower screen edge and fans out up to 8 favorite
 * or recent apps along an ergonomic polar thumb arc with 120Hz spring physics, continuous
 * thumb-drag scrubbing, and instant left/right-hand reversal.
 */
@Composable
fun RadialThumbArcOverlay(
    apps: List<AppEntry>,
    iconShapePreset: IconShapePreset = IconShapePreset.SQUIRCLE,
    onAppClick: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit = {},
    onOpenSearch: (() -> Unit)? = null,
    initialRightHanded: Boolean = true,
    initiallyExpanded: Boolean = false,
    triggerCorner: RadialTriggerCorner? = null,
    onDismissRequest: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentOnAppClick by rememberUpdatedState(onAppClick)
    val currentOnAppLongClick by rememberUpdatedState(onAppLongClick)
    val currentOnOpenSearch by rememberUpdatedState(onOpenSearch)
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)

    val arcApps = remember(apps) { apps.take(8) }
    var isExpanded by remember(initiallyExpanded, triggerCorner) {
        mutableStateOf(initiallyExpanded || triggerCorner != null)
    }
    var isRightHanded by remember(initialRightHanded, triggerCorner) {
        mutableStateOf(
            when (triggerCorner) {
                RadialTriggerCorner.BOTTOM_LEFT -> false
                RadialTriggerCorner.BOTTOM_RIGHT -> true
                null -> initialRightHanded
            }
        )
    }
    var hoveredIndex by remember { mutableIntStateOf(-1) }

    val fanProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = LauncherSpringSpecs.RadialArcFanOutSpring,
        label = "ArcDeckFanProgress"
    )

    val outerRadiusDp = 152f
    val innerRadiusDp = 82f
    val minScrubRadiusPx = remember(density) { with(density) { 46.dp.toPx() } }
    val maxScrubRadiusPx = remember(density) { with(density) { 215.dp.toPx() } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (isRightHanded) Alignment.BottomEnd else Alignment.BottomStart
    ) {
        // Dimmed backdrop when the Arc Deck is fanned open
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = LauncherSpringSpecs.Snappy120HzFloat),
            exit = fadeOut(animationSpec = LauncherSpringSpecs.Snappy120HzFloat)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FoldLauncherColors.OledBlack.copy(alpha = 0.68f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            hoveredIndex = -1
                            isExpanded = false
                            currentOnDismiss?.invoke()
                        })
                    }
            )
        }

        // Polar Arc Deck Canvas + Interactive Radial App Nodes
        if (isExpanded || fanProgress > 0.02f) {
            val anchorInsetDp = 42.dp
            Box(
                modifier = Modifier
                    .size(310.dp)
                    .align(if (isRightHanded) Alignment.BottomEnd else Alignment.BottomStart)
                    .padding(bottom = 72.dp, start = 8.dp, end = 8.dp)
                    .pointerInput(arcApps, isRightHanded) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val anchorX = if (isRightHanded) {
                                    size.width - anchorInsetDp.toPx()
                                } else {
                                    anchorInsetDp.toPx()
                                }
                                val anchorY = size.height - anchorInsetDp.toPx()
                                val idx = SpatialMath.resolveHoveredArcIndex(
                                    dxPx = offset.x - anchorX,
                                    dyPx = offset.y - anchorY,
                                    totalCount = arcApps.size,
                                    minRadiusPx = minScrubRadiusPx,
                                    maxRadiusPx = maxScrubRadiusPx,
                                    isRightHanded = isRightHanded
                                )
                                if (idx != hoveredIndex) {
                                    hoveredIndex = idx
                                    if (idx >= 0) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val anchorX = if (isRightHanded) {
                                    size.width - anchorInsetDp.toPx()
                                } else {
                                    anchorInsetDp.toPx()
                                }
                                val anchorY = size.height - anchorInsetDp.toPx()
                                val idx = SpatialMath.resolveHoveredArcIndex(
                                    dxPx = change.position.x - anchorX,
                                    dyPx = change.position.y - anchorY,
                                    totalCount = arcApps.size,
                                    minRadiusPx = minScrubRadiusPx,
                                    maxRadiusPx = maxScrubRadiusPx,
                                    isRightHanded = isRightHanded
                                )
                                if (idx != hoveredIndex) {
                                    hoveredIndex = idx
                                    if (idx >= 0) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                            onDragEnd = {
                                val selected = arcApps.getOrNull(hoveredIndex)
                                hoveredIndex = -1
                                if (selected != null) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isExpanded = false
                                    currentOnAppClick(selected)
                                }
                            },
                            onDragCancel = {
                                hoveredIndex = -1
                            }
                        )
                    }
            ) {
                // Polar guide rings and rays
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val anchor = Offset(
                        x = if (isRightHanded) size.width - anchorInsetDp.toPx() else anchorInsetDp.toPx(),
                        y = size.height - anchorInsetDp.toPx()
                    )
                    val outerRadiusPx = outerRadiusDp.dp.toPx() * fanProgress
                    val innerRadiusPx = innerRadiusDp.dp.toPx() * fanProgress

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                FoldLauncherColors.ElectricCyan.copy(alpha = 0.22f * fanProgress),
                                FoldLauncherColors.IndigoAccent.copy(alpha = 0.10f * fanProgress),
                                Color.Transparent
                            ),
                            center = anchor,
                            radius = (outerRadiusPx + 48.dp.toPx()).coerceAtLeast(1f)
                        ),
                        center = anchor,
                        radius = (outerRadiusPx + 48.dp.toPx()).coerceAtLeast(1f)
                    )

                    drawCircle(
                        color = FoldLauncherColors.ElectricCyan.copy(alpha = 0.28f * fanProgress),
                        center = anchor,
                        radius = outerRadiusPx.coerceAtLeast(1f),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                    )

                    drawCircle(
                        color = FoldLauncherColors.GlassBorderStrong.copy(alpha = 0.22f * fanProgress),
                        center = anchor,
                        radius = innerRadiusPx.coerceAtLeast(1f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Hovered or active Arc Deck header HUD
                val hoveredApp = arcApps.getOrNull(hoveredIndex)
                Column(
                    modifier = Modifier
                        .align(if (isRightHanded) Alignment.TopEnd else Alignment.TopStart)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(FoldLauncherColors.GlassSurfaceElevated)
                        .border(1.dp, FoldLauncherColors.ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = if (isRightHanded) Alignment.End else Alignment.Start
                ) {
                    Text(
                        text = hoveredApp?.label ?: "ONE-HANDED THUMB ARC",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (hoveredApp != null) FoldLauncherColors.CyanBright else FoldLauncherColors.ElectricCyan
                    )
                    Text(
                        text = if (hoveredApp != null) {
                            "Release thumb to launch ${hoveredApp.label}"
                        } else {
                            "Drag thumb along arc or tap any app · ${if (isRightHanded) "Right" else "Left"} Hand"
                        },
                        fontSize = 10.sp,
                        color = FoldLauncherColors.TextSecondary
                    )
                }

                // Fan out up to 8 apps along the polar arc
                val shape = remember(iconShapePreset) { iconShapeForPreset(iconShapePreset) }
                arcApps.forEachIndexed { index, app ->
                    val pos = remember(index, arcApps.size, isRightHanded, fanProgress) {
                        SpatialMath.computeArcItemPosition(
                            index = index,
                            totalCount = arcApps.size,
                            radiusDp = outerRadiusDp,
                            isRightHanded = isRightHanded,
                            fanProgress = fanProgress
                        )
                    }
                    val isHovered = index == hoveredIndex
                    val itemScale by animateFloatAsState(
                        targetValue = if (isHovered) 1.28f else 1.0f,
                        animationSpec = LauncherSpringSpecs.IconBounceSpring,
                        label = "ArcNodeScale"
                    )

                    val offsetX = with(density) { pos.xDp.dp.roundToPx() }
                    val offsetY = with(density) { pos.yDp.dp.roundToPx() }

                    Column(
                        modifier = Modifier
                            .align(if (isRightHanded) Alignment.BottomEnd else Alignment.BottomStart)
                            .padding(
                                bottom = 18.dp,
                                end = if (isRightHanded) 18.dp else 0.dp,
                                start = if (!isRightHanded) 18.dp else 0.dp
                            )
                            .offset { IntOffset(offsetX, offsetY) }
                            .graphicsLayer {
                                scaleX = itemScale * fanProgress.coerceIn(0f, 1f)
                                scaleY = itemScale * fanProgress.coerceIn(0f, 1f)
                                alpha = fanProgress.coerceIn(0f, 1f)
                            }
                            .pointerInput(app) {
                                detectTapGestures(
                                    onTap = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isExpanded = false
                                        currentOnAppClick(app)
                                    },
                                    onLongPress = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isExpanded = false
                                        currentOnAppLongClick(app)
                                    }
                                )
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(shape)
                                .background(
                                    if (isHovered) {
                                        FoldLauncherColors.CyanGlow
                                    } else {
                                        FoldLauncherColors.GlassSurfaceElevated
                                    }
                                )
                                .border(
                                    width = if (isHovered) 1.8.dp else 1.dp,
                                    color = if (isHovered) {
                                        FoldLauncherColors.ElectricCyan
                                    } else {
                                        FoldLauncherColors.GlassBorder
                                    },
                                    shape = shape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val bitmap = app.icon
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = app.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = app.acronym.uppercase().take(2).ifEmpty { app.firstLetter.toString() },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isHovered) FoldLauncherColors.CyanBright else FoldLauncherColors.TextPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = app.label,
                            fontSize = 10.sp,
                            fontWeight = if (isHovered) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isHovered) FoldLauncherColors.CyanBright else FoldLauncherColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(58.dp)
                        )
                    }
                }
            }
        }

        // Thumb-Corner Trigger Pill + Handedness Switcher
        Row(
            modifier = Modifier
                .align(if (isRightHanded) Alignment.BottomEnd else Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 82.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isExpanded) {
                // Handedness flip button (Left <-> Right thumb corner)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(FoldLauncherColors.GlassSurfaceElevated)
                        .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isRightHanded = !isRightHanded
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isRightHanded) "🫲 Left Hand" else "Right Hand 🫱",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.CyanBright
                    )
                }

                if (currentOnOpenSearch != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(FoldLauncherColors.GlassSurfaceElevated)
                            .border(1.dp, FoldLauncherColors.ElectricCyan, RoundedCornerShape(16.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isExpanded = false
                                currentOnOpenSearch?.invoke()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "⌕ Spotlight",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoldLauncherColors.ElectricCyan
                        )
                    }
                }
            }

            // Primary Radial Thumb Arc Toggle Button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isExpanded) {
                            FoldLauncherColors.ElectricCyan
                        } else {
                            FoldLauncherColors.GlassSurfaceElevated
                        }
                    )
                    .border(
                        width = 1.2.dp,
                        color = FoldLauncherColors.ElectricCyan,
                        shape = CircleShape
                    )
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        hoveredIndex = -1
                        val nextExpanded = !isExpanded
                        isExpanded = nextExpanded
                        if (!nextExpanded) {
                            currentOnDismiss?.invoke()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isExpanded) "✕ Close Arc" else "◎ Arc Deck",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isExpanded) {
                        FoldLauncherColors.OledBlack
                    } else {
                        FoldLauncherColors.ElectricCyan
                    }
                )
            }
        }
    }
}

/**
 * Z Fold 8 Half-Folded Flex-Mode Virtual Trackpad & Laser Cursor Deck (`FlexTrackpadPanel` / `FlexVirtualTrackpadCard`).
 *
 * Designed for `FLEX_TABLETOP` posture (or toggled on as a split control deck): renders a
 * multi-touch 120Hz glassmorphic trackpad surface on the bottom half with:
 * - Live Hinge Angle Telemetry Badge (`e.g. 118° HINGE`) in the header when non-null
 * - Simulated cursor coordinates & 120Hz laser crosshair HUD
 * - Right-edge **2-Finger Scroll / Page Up / Page Down Quick Strip** for effortless navigation of
 *   long VC agent transcripts or Termux logs on the top half of the Z Fold 8 screen
 * - 1-tap Left/Right click buttons, Spotlight trigger, VC Deck quick-launch, and Split-Screen trigger.
 */
@Composable
fun FlexTrackpadPanel(
    favoriteApps: List<AppEntry> = emptyList(),
    hingeAngleDegrees: Float? = null,
    onLaunchApp: (AppEntry) -> Unit = {},
    onContextApp: (AppEntry) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onTriggerSplitScreen: () -> Unit = {},
    onQuickLaunchVc: (() -> Unit)? = null,
    onCursorPositionChanged: ((Float, Float) -> Unit)? = null,
    onScrollDelta: ((Float) -> Unit)? = null,
    onPageUp: (() -> Unit)? = null,
    onPageDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    FlexVirtualTrackpadCard(
        favoriteApps = favoriteApps,
        hingeAngleDegrees = hingeAngleDegrees,
        onLaunchApp = onLaunchApp,
        onContextApp = onContextApp,
        onOpenSearch = onOpenSearch,
        onTriggerSplitScreen = onTriggerSplitScreen,
        onQuickLaunchVc = onQuickLaunchVc,
        onCursorPositionChanged = onCursorPositionChanged,
        onScrollDelta = onScrollDelta,
        onPageUp = onPageUp,
        onPageDown = onPageDown,
        modifier = modifier
    )
}

@Composable
fun FlexVirtualTrackpadCard(
    favoriteApps: List<AppEntry> = emptyList(),
    hingeAngleDegrees: Float? = null,
    onLaunchApp: (AppEntry) -> Unit = {},
    onContextApp: (AppEntry) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onTriggerSplitScreen: () -> Unit = {},
    onQuickLaunchVc: (() -> Unit)? = null,
    onCursorPositionChanged: ((Float, Float) -> Unit)? = null,
    onScrollDelta: ((Float) -> Unit)? = null,
    onPageUp: (() -> Unit)? = null,
    onPageDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val spatialTilt = rememberSpatialTiltState(enabled = hingeAngleDegrees == null)
    val effectiveHingeDegrees = hingeAngleDegrees ?: spatialTilt.hingeAngleDegrees

    val currentOnLaunchApp by rememberUpdatedState(onLaunchApp)
    val currentOnContextApp by rememberUpdatedState(onContextApp)
    val currentOnOpenSearch by rememberUpdatedState(onOpenSearch)
    val currentOnTriggerSplit by rememberUpdatedState(onTriggerSplitScreen)
    val currentOnQuickVc by rememberUpdatedState(onQuickLaunchVc)
    val currentOnCursorChanged by rememberUpdatedState(onCursorPositionChanged)
    val currentOnScrollDelta by rememberUpdatedState(onScrollDelta)
    val currentOnPageUp by rememberUpdatedState(onPageUp)
    val currentOnPageDown by rememberUpdatedState(onPageDown)

    var cursorNormX by remember { mutableFloatStateOf(0.5f) }
    var cursorNormY by remember { mutableFloatStateOf(0.5f) }
    var scrollThumbNormY by remember { mutableFloatStateOf(0.5f) }
    val animatedCursorX by animateFloatAsState(
        targetValue = cursorNormX,
        animationSpec = LauncherSpringSpecs.TrackpadCursorSpring,
        label = "TrackpadCursorX"
    )
    val animatedCursorY by animateFloatAsState(
        targetValue = cursorNormY,
        animationSpec = LauncherSpringSpecs.TrackpadCursorSpring,
        label = "TrackpadCursorY"
    )
    var padWidthPx by remember { mutableFloatStateOf(1f) }
    var padHeightPx by remember { mutableFloatStateOf(1f) }
    var isTouchingPad by remember { mutableStateOf(false) }
    var highSensitivity by remember { mutableStateOf(true) }
    var lastActionStatus by remember { mutableStateOf("120Hz Laser Cursor & 2F Scroll Ready") }

    val targetApps = remember(favoriteApps) { favoriteApps.take(5) }
    val targetedAppIndex = remember(cursorNormX, targetApps.size) {
        if (targetApps.isEmpty()) {
            -1
        } else {
            (cursorNormX * targetApps.size).toInt().coerceIn(0, targetApps.lastIndex)
        }
    }
    val targetedApp = targetApps.getOrNull(targetedAppIndex)

    fun performLeftClick() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (targetedApp != null) {
            lastActionStatus = "Left-Click → Launched ${targetedApp.label}"
            currentOnLaunchApp(targetedApp)
        } else {
            lastActionStatus = "Left-Click @ (${(cursorNormX * 100).roundToInt()}%, ${(cursorNormY * 100).roundToInt()}%)"
        }
    }

    fun performRightClick() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (targetedApp != null) {
            lastActionStatus = "Right-Click → Inspector for ${targetedApp.label}"
            currentOnContextApp(targetedApp)
        } else {
            lastActionStatus = "Right-Click Context Menu @ (${(cursorNormX * 100).roundToInt()}%, ${(cursorNormY * 100).roundToInt()}%)"
        }
    }

    fun triggerPageUp() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scrollThumbNormY = (scrollThumbNormY - 0.25f).coerceIn(0f, 1f)
        cursorNormY = (cursorNormY - 0.20f).coerceIn(0f, 1f)
        lastActionStatus = "▲ Page Up · Top Hemisphere Scroll"
        currentOnPageUp?.invoke()
        currentOnScrollDelta?.invoke(-1f)
    }

    fun triggerPageDown() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scrollThumbNormY = (scrollThumbNormY + 0.25f).coerceIn(0f, 1f)
        cursorNormY = (cursorNormY + 0.20f).coerceIn(0f, 1f)
        lastActionStatus = "▼ Page Down · Top Hemisphere Scroll"
        currentOnPageDown?.invoke()
        currentOnScrollDelta?.invoke(1f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(FoldLauncherColors.GlassSurface)
            .border(1.dp, FoldLauncherColors.GlassBorderStrong, RoundedCornerShape(22.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header Row: Trackpad Title + Hinge Angle Telemetry Badge + Coordinate Telemetry + Sensitivity Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FLEX-MODE VIRTUAL TRACKPAD & LASER DECK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.ElectricCyan
                )
                Text(
                    text = lastActionStatus,
                    fontSize = 10.sp,
                    color = FoldLauncherColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (effectiveHingeDegrees != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(FoldLauncherColors.CyanGlow)
                            .border(1.dp, FoldLauncherColors.ElectricCyan, RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${effectiveHingeDegrees.roundToInt()}° HINGE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = FoldLauncherColors.CyanBright
                        )
                    }
                }

                Text(
                    text = "X:${(cursorNormX * 100).roundToInt()}% Y:${(cursorNormY * 100).roundToInt()}%",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.CyanBright
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(FoldLauncherColors.GlassSurfaceHighlight)
                        .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(10.dp))
                        .clickable { highSensitivity = !highSensitivity }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (highSensitivity) "1.6x Fast" else "1.0x Fine",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.ElectricCyan
                    )
                }
            }
        }

        // Upper-Hemisphere Cursor Target Strip (lets cursor aim at favorite apps hands-free)
        if (targetApps.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                targetApps.forEachIndexed { idx, app ->
                    val isTargeted = idx == targetedAppIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isTargeted) FoldLauncherColors.CyanGlow
                                else FoldLauncherColors.GlassSurfaceHighlight
                            )
                            .border(
                                width = 1.dp,
                                color = if (isTargeted) FoldLauncherColors.ElectricCyan else FoldLauncherColors.GlassBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                cursorNormX = (idx + 0.5f) / targetApps.size.toFloat()
                                performLeftClick()
                            }
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isTargeted) "◎ ${app.label}" else app.label,
                            fontSize = 10.sp,
                            fontWeight = if (isTargeted) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isTargeted) FoldLauncherColors.CyanBright else FoldLauncherColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Interactive Multi-Touch Trackpad Surface + Right-Edge 2-Finger Scroll / PgUp / PgDn Strip
        val sensitivity = if (highSensitivity) 1.6f else 1.0f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.85f))
                    .border(
                        width = if (isTouchingPad) 1.5.dp else 1.dp,
                        color = if (isTouchingPad) FoldLauncherColors.ElectricCyan else FoldLauncherColors.GlassBorder,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .onSizeChanged { size ->
                        padWidthPx = size.width.coerceAtLeast(1).toFloat()
                        padHeightPx = size.height.coerceAtLeast(1).toFloat()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { performLeftClick() },
                            onDoubleTap = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnOpenSearch()
                            },
                            onLongPress = { performRightClick() }
                        )
                    }
                    .pointerInput(sensitivity) {
                        detectDragGestures(
                            onDragStart = {
                                isTouchingPad = true
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDragEnd = {
                                isTouchingPad = false
                            },
                            onDragCancel = {
                                isTouchingPad = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val (nextX, nextY) = SpatialMath.updateTrackpadCursor(
                                    currentNormalizedX = cursorNormX,
                                    currentNormalizedY = cursorNormY,
                                    deltaXPx = dragAmount.x,
                                    deltaYPx = dragAmount.y,
                                    padWidthPx = padWidthPx,
                                    padHeightPx = padHeightPx,
                                    sensitivity = sensitivity
                                )
                                cursorNormX = nextX
                                cursorNormY = nextY
                                currentOnCursorChanged?.invoke(nextX, nextY)
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Subtle precision grid lines
                    val cols = 6
                    val rows = 3
                    for (c in 1 until cols) {
                        val x = size.width * (c.toFloat() / cols)
                        drawLine(
                            color = FoldLauncherColors.GlassBorder.copy(alpha = 0.14f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    for (r in 1 until rows) {
                        val y = size.height * (r.toFloat() / rows)
                        drawLine(
                            color = FoldLauncherColors.GlassBorder.copy(alpha = 0.14f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Laser Cursor Crosshairs & Glowing Reticle
                    val cursorOffset = Offset(
                        x = (animatedCursorX * size.width).coerceIn(10f, (size.width - 10f).coerceAtLeast(10f)),
                        y = (animatedCursorY * size.height).coerceIn(10f, (size.height - 10f).coerceAtLeast(10f))
                    )

                    drawLine(
                        color = FoldLauncherColors.ElectricCyan.copy(alpha = if (isTouchingPad) 0.42f else 0.20f),
                        start = Offset(cursorOffset.x, 0f),
                        end = Offset(cursorOffset.x, size.height),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = FoldLauncherColors.ElectricCyan.copy(alpha = if (isTouchingPad) 0.42f else 0.20f),
                        start = Offset(0f, cursorOffset.y),
                        end = Offset(size.width, cursorOffset.y),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                FoldLauncherColors.CyanBright.copy(alpha = 0.55f),
                                FoldLauncherColors.ElectricCyan.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = cursorOffset,
                            radius = 22.dp.toPx()
                        ),
                        center = cursorOffset,
                        radius = 22.dp.toPx()
                    )

                    drawCircle(
                        color = FoldLauncherColors.CyanBright,
                        center = cursorOffset,
                        radius = if (isTouchingPad) 6.dp.toPx() else 4.5.dp.toPx()
                    )
                }

                if (targetedApp != null) {
                    Text(
                        text = "Target: ${targetedApp.label}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FoldLauncherColors.CyanBright.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Right-Edge 2-Finger Scroll / Page Up / Page Down Quick Strip
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FoldLauncherColors.GlassSurfaceElevated)
                    .border(1.dp, FoldLauncherColors.GlassBorderStrong, RoundedCornerShape(16.dp))
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FoldLauncherColors.GlassSurfaceHighlight)
                        .clickable { triggerPageUp() }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▲ PgUp",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.CyanBright
                    )
                }

                // Continuous 2-Finger / Thumb Vertical Scroll Rail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.7f))
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val h = size.height.coerceAtLeast(1).toFloat()
                                    val normDelta = dragAmount / h
                                    scrollThumbNormY = (scrollThumbNormY + normDelta).coerceIn(0f, 1f)
                                    lastActionStatus = "2F Scroll · ${(scrollThumbNormY * 100).roundToInt()}%"
                                    currentOnScrollDelta?.invoke(normDelta)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val tickCount = 5
                        for (i in 1..tickCount) {
                            val y = size.height * (i.toFloat() / (tickCount + 1))
                            drawLine(
                                color = FoldLauncherColors.GlassBorder,
                                start = Offset(size.width * 0.25f, y),
                                end = Offset(size.width * 0.75f, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        val thumbY = (scrollThumbNormY * size.height)
                            .coerceIn(6.dp.toPx(), (size.height - 6.dp.toPx()).coerceAtLeast(6.dp.toPx()))
                        drawCircle(
                            color = FoldLauncherColors.ElectricCyan,
                            center = Offset(size.width * 0.5f, thumbY),
                            radius = 4.dp.toPx()
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FoldLauncherColors.GlassSurfaceHighlight)
                        .clickable { triggerPageDown() }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▼ PgDn",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.CyanBright
                    )
                }
            }
        }

        // Bottom Tactile Hardware-Style Trackpad Action Bar:
        // Left Click | Right Click | Spotlight | VC Deck | Split-Screen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackpadDeckButton(
                label = "Left Click",
                accent = FoldLauncherColors.ElectricCyan,
                onClick = ::performLeftClick,
                modifier = Modifier.weight(1.15f)
            )
            TrackpadDeckButton(
                label = "Right Click",
                accent = FoldLauncherColors.TextPrimary,
                onClick = ::performRightClick,
                modifier = Modifier.weight(1.15f)
            )
            TrackpadDeckButton(
                label = "⌕ Spotlight",
                accent = FoldLauncherColors.CyanBright,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastActionStatus = "Opened 0ms Spotlight Search"
                    currentOnOpenSearch()
                },
                modifier = Modifier.weight(1f)
            )
            TrackpadDeckButton(
                label = "⚡ VC Deck",
                accent = FoldLauncherColors.EmeraldSuccess,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastActionStatus = "Triggered VC Command Deck"
                    if (currentOnQuickVc != null) {
                        currentOnQuickVc?.invoke()
                    } else if (targetedApp != null) {
                        currentOnLaunchApp(targetedApp)
                    } else {
                        currentOnOpenSearch()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            TrackpadDeckButton(
                label = "▣ Split",
                accent = FoldLauncherColors.IndigoAccent,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    lastActionStatus = "Triggered Fold8 Split-Screen Pair"
                    currentOnTriggerSplit()
                },
                modifier = Modifier.weight(0.9f)
            )
        }
    }
}

@Composable
private fun TrackpadDeckButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FoldLauncherColors.GlassSurfaceElevated)
            .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
