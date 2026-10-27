package com.xsun.foldlauncher.ui.components

import android.app.KeyguardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.posture.DevicePosture
import com.xsun.foldlauncher.posture.DevicePostureState
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val POSTURE_CYCLE_MODES = listOf("AUTO", "FLEX", "INNER", "COVER", "TENT")

/**
 * Reads the physical Galaxy Z Fold 8 hinge angle sensor (`Sensor.TYPE_HINGE_ANGLE` = 36)
 * when available, returning `null` on slab devices or when no hinge sensor is present.
 */
@Composable
internal fun rememberHardwareHingeAngleDegrees(): Float? {
    val context = LocalContext.current
    var angleDeg by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hingeSensor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        } else {
            sensorManager?.getDefaultSensor(36)
        }

        if (sensorManager == null || hingeSensor == null) {
            return@DisposableEffect onDispose {}
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val raw = event?.values?.firstOrNull() ?: return
                if (!raw.isNaN() && raw in 0f..180f) {
                    angleDeg = raw
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(
            listener,
            hingeSensor,
            SensorManager.SENSOR_DELAY_UI
        )

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return angleDeg
}

@Composable
fun TopStatusBar(
    postureState: DevicePostureState,
    batteryPct: Int,
    isCharging: Boolean,
    nextEventText: String,
    isDeXConnected: Boolean,
    onClockClick: () -> Unit,
    onSearchPillClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    hingeAngleDegrees: Float? = null,
    postureOverrideLabel: String? = null,
    onCyclePostureOverride: (() -> Unit)? = null,
    isKeyguardLocked: Boolean? = null,
    isTorchOn: Boolean = false,
    onToggleTorch: (() -> Unit)? = null,
    hingeHapticsEnabled: Boolean = true,
    foldCloseModeLabel: String? = null,
    onToggleHingeHaptics: (() -> Unit)? = null,
    onCycleFoldCloseMode: (() -> Unit)? = null,
    unfoldModeLabel: String? = null,
    onCycleUnfoldMode: (() -> Unit)? = null,
    tailscaleIp: String? = null,
    lanIp: String? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var localCycleIndex by remember { mutableIntStateOf(0) }

    val sensorHingeAngle = rememberHardwareHingeAngleDegrees()
    val effectiveHingeAngle = hingeAngleDegrees ?: sensorHingeAngle

    val effectiveKeyguardLocked = remember(context, nowMillis, isKeyguardLocked) {
        isKeyguardLocked ?: run {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.isKeyguardLocked == true
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(10_000L)
        }
    }

    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val timeString = remember(nowMillis, is24Hour) {
        val pattern = if (is24Hour) "HH:mm" else "h:mm"
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(nowMillis))
    }
    val amPmString = remember(nowMillis, is24Hour) {
        if (is24Hour) "" else SimpleDateFormat("a", Locale.getDefault()).format(Date(nowMillis))
    }
    val dateString = remember(nowMillis) {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(nowMillis))
    }

    val activeOverrideTag = postureOverrideLabel ?: POSTURE_CYCLE_MODES[localCycleIndex]
    val isManualOverrideActive = !activeOverrideTag.equals("AUTO", ignoreCase = true)

    val anglePrefix = effectiveHingeAngle?.let { "${it.roundToInt()}° " } ?: when (postureState.posture) {
        DevicePosture.EXPANDED -> if (postureState.isBookPosture) "135° " else "180° "
        DevicePosture.FLEX_TABLETOP -> "118° "
        DevicePosture.COMPACT -> ""
    }

    val basePostureLabel = when {
        isDeXConnected -> "DeX READY"
        activeOverrideTag.equals("TENT", ignoreCase = true) -> "${effectiveHingeAngle?.roundToInt() ?: 48}° TENT"
        activeOverrideTag.equals("FLEX", ignoreCase = true) -> "${effectiveHingeAngle?.roundToInt() ?: 118}° FLEX"
        activeOverrideTag.equals("INNER", ignoreCase = true) -> "${effectiveHingeAngle?.roundToInt() ?: 180}° UNFOLDED"
        activeOverrideTag.equals("COVER", ignoreCase = true) -> "COVER"
        effectiveHingeAngle != null && effectiveHingeAngle in 25f..75f -> "${effectiveHingeAngle.roundToInt()}° TENT"
        postureState.posture == DevicePosture.EXPANDED && postureState.isBookPosture -> "${anglePrefix}BOOK"
        postureState.posture == DevicePosture.EXPANDED -> "${anglePrefix}UNFOLDED"
        postureState.posture == DevicePosture.FLEX_TABLETOP -> "${anglePrefix}FLEX"
        else -> "COVER"
    }

    val switcherHint = "$activeOverrideTag ↻"
    val pillShape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        FoldLauncherColors.GlassSurfaceElevated,
                        FoldLauncherColors.GlassSurface
                    )
                )
            )
            .border(
                1.dp,
                if (effectiveKeyguardLocked) FoldLauncherColors.AmberWarning.copy(alpha = 0.65f)
                else FoldLauncherColors.GlassBorder,
                RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left cluster: Interactive Hinge Angle & Posture Switcher Pill, Keyguard/Torch badges, Spotlight pill, Settings button
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Interactive Z Fold 8 Hinge Angle & Posture Switcher Pill
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(
                        when {
                            isDeXConnected -> FoldLauncherColors.IndigoAccent.copy(alpha = 0.28f)
                            isManualOverrideActive -> FoldLauncherColors.AmberWarning.copy(alpha = 0.20f)
                            else -> FoldLauncherColors.CyanGlow
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            isDeXConnected -> FoldLauncherColors.IndigoAccent
                            isManualOverrideActive -> FoldLauncherColors.AmberWarning
                            onCyclePostureOverride != null -> FoldLauncherColors.ElectricCyan
                            else -> FoldLauncherColors.GlassBorderStrong
                        },
                        shape = pillShape
                    )
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (onCyclePostureOverride != null) {
                            onCyclePostureOverride()
                        } else {
                            localCycleIndex = (localCycleIndex + 1) % POSTURE_CYCLE_MODES.size
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isDeXConnected -> FoldLauncherColors.EmeraldSuccess
                                    isManualOverrideActive -> FoldLauncherColors.AmberWarning
                                    else -> FoldLauncherColors.ElectricCyan
                                }
                            )
                    )
                    Text(
                        text = basePostureLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = switcherHint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isManualOverrideActive) {
                            FoldLauncherColors.AmberWarning
                        } else {
                            FoldLauncherColors.CyanBright
                        },
                        maxLines = 1
                    )
                }
            }

            if (effectiveKeyguardLocked) {
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(FoldLauncherColors.AmberWarning.copy(alpha = 0.22f))
                        .border(1.dp, FoldLauncherColors.AmberWarning, pillShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🔒 KEYGUARD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.AmberWarning,
                        maxLines = 1
                    )
                }
            }

            if (isTorchOn || onToggleTorch != null) {
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(
                            if (isTorchOn) FoldLauncherColors.AmberWarning.copy(alpha = 0.25f)
                            else FoldLauncherColors.GlassSurfaceHighlight
                        )
                        .border(
                            1.dp,
                            if (isTorchOn) FoldLauncherColors.AmberWarning else FoldLauncherColors.GlassBorder,
                            pillShape
                        )
                        .let { base ->
                            if (onToggleTorch != null) base.clickable(onClick = onToggleTorch) else base
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isTorchOn) "🔦 ON" else "🔦",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTorchOn) FoldLauncherColors.AmberWarning else FoldLauncherColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }

            // Quick Spotlight Search Pill
            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .background(FoldLauncherColors.GlassSurfaceHighlight)
                    .border(1.dp, FoldLauncherColors.GlassBorder, pillShape)
                    .clickable(onClick = onSearchPillClick)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SearchMagnifierGlyph(
                    color = FoldLauncherColors.ElectricCyan,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (postureState.isCompact) "Search" else "Spotlight · Math · Web",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FoldLauncherColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick Settings Gear Pill
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(FoldLauncherColors.GlassSurfaceHighlight)
                    .border(1.dp, FoldLauncherColors.GlassBorder, CircleShape)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚙",
                    fontSize = 15.sp,
                    color = FoldLauncherColors.TextSecondary
                )
            }

            if (!postureState.isCompact && nextEventText.isNotBlank()) {
                Text(
                    text = nextEventText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = FoldLauncherColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Right cluster: Battery pill + Oversized High-Visibility Top-Right Clock (26.sp+ tabular figures)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BatteryStatusPill(
                batteryPct = batteryPct,
                isCharging = isCharging
            )

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onClockClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timeString,
                        style = TextStyle(
                            fontSize = if (postureState.isExpanded) 29.sp else 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FoldLauncherColors.TextPrimary,
                            letterSpacing = (-0.5).sp,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1
                    )
                    if (amPmString.isNotEmpty()) {
                        Text(
                            text = amPmString,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoldLauncherColors.ElectricCyan,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                Text(
                    text = if (postureState.isCompact && nextEventText.isNotBlank()) {
                        "$dateString · $nextEventText"
                    } else {
                        dateString
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FoldLauncherColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BatteryStatusPill(
    batteryPct: Int,
    isCharging: Boolean
) {
    val clamped = batteryPct.coerceIn(0, 100)
    val fillColor = when {
        isCharging -> FoldLauncherColors.EmeraldSuccess
        clamped <= 15 -> FoldLauncherColors.CoralDanger
        clamped <= 30 -> FoldLauncherColors.AmberWarning
        else -> FoldLauncherColors.ElectricCyan
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FoldLauncherColors.GlassSurfaceHighlight)
            .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 18.dp, height = 10.dp)) {
            val bodyWidth = size.width - 3.dp.toPx()
            val strokePx = 1.3.dp.toPx()
            drawRoundRect(
                color = FoldLauncherColors.TextSecondary,
                topLeft = Offset.Zero,
                size = Size(bodyWidth, size.height),
                cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                style = Stroke(width = strokePx)
            )
            // Battery terminal nub
            drawRoundRect(
                color = FoldLauncherColors.TextSecondary,
                topLeft = Offset(bodyWidth + 1.dp.toPx(), size.height * 0.28f),
                size = Size(2.dp.toPx(), size.height * 0.44f),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
            // Fill bar
            val inset = 2.dp.toPx()
            val maxFillWidth = (bodyWidth - inset * 2).coerceAtLeast(0f)
            val fillWidth = maxFillWidth * (clamped / 100f)
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(inset, inset),
                size = Size(fillWidth, (size.height - inset * 2).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        }
        Text(
            text = if (isCharging) "⚡$clamped%" else "$clamped%",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.TextPrimary,
                fontFeatureSettings = "tnum"
            )
        )
    }
}

@Composable
internal fun SearchMagnifierGlyph(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        val radius = size.minDimension * 0.36f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = color,
            start = Offset(center.x + radius * 0.72f, center.y + radius * 0.72f),
            end = Offset(size.width * 0.9f, size.height * 0.9f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
