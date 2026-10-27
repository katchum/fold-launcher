package com.xsun.foldlauncher.ui.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
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
    modifier: Modifier = Modifier
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

    val postureLabel = when {
        isDeXConnected -> "DeX READY"
        postureState.posture == DevicePosture.EXPANDED && postureState.isBookPosture -> "BOOK POSTURE"
        postureState.posture == DevicePosture.EXPANDED -> "INNER 120Hz"
        postureState.posture == DevicePosture.FLEX_TABLETOP -> "FLEX MODE"
        else -> "COVER / SLAB"
    }

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
            .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left cluster: Posture/DeX status chip, Spotlight pill, Settings button, and next event
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Posture / DeX Indicator Chip
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(
                        if (isDeXConnected) {
                            FoldLauncherColors.IndigoAccent.copy(alpha = 0.28f)
                        } else {
                            FoldLauncherColors.CyanGlow
                        }
                    )
                    .border(
                        1.dp,
                        if (isDeXConnected) FoldLauncherColors.IndigoAccent else FoldLauncherColors.GlassBorderStrong,
                        pillShape
                    )
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
                                if (isDeXConnected) FoldLauncherColors.EmeraldSuccess
                                else FoldLauncherColors.ElectricCyan
                            )
                    )
                    Text(
                        text = postureLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.TextPrimary,
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
