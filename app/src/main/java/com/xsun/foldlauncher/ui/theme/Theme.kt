package com.xsun.foldlauncher.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class IconShapePreset(val label: String) {
    SQUIRCLE("One UI Squircle"),
    CIRCLE("Pixel Circle"),
    ROUNDED_SQUARE("iOS Rounded"),
    TEARDROP("Teardrop")
}

private val OneUiSquircleShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val c = 0.14f
    moveTo(0f, h * 0.5f)
    cubicTo(0f, h * c, w * c, 0f, w * 0.5f, 0f)
    cubicTo(w * (1f - c), 0f, w, h * c, w, h * 0.5f)
    cubicTo(w, h * (1f - c), w * (1f - c), h, w * 0.5f, h)
    cubicTo(w * c, h, 0f, h * (1f - c), 0f, h * 0.5f)
    close()
}

fun iconShapeForPreset(preset: IconShapePreset): Shape = when (preset) {
    IconShapePreset.SQUIRCLE -> OneUiSquircleShape
    IconShapePreset.CIRCLE -> CircleShape
    IconShapePreset.ROUNDED_SQUARE -> RoundedCornerShape(percent = 24)
    IconShapePreset.TEARDROP -> RoundedCornerShape(
        topStartPercent = 50,
        topEndPercent = 50,
        bottomStartPercent = 50,
        bottomEndPercent = 16
    )
}

object FoldLauncherColors {
    // True OLED & Deep Space Surfaces
    val OledBlack = Color(0xFF000000)
    val DeepVoid = Color(0xFF05070E)
    val GlassSurface = Color(0xCC0D121F)
    val GlassSurfaceElevated = Color(0xD9151C2E)
    val GlassSurfaceHighlight = Color(0xE61E2842)
    val DockGlassSurface = Color(0xE60B101D)

    // Subtle Specular Glass Borders
    val GlassBorder = Color(0x29FFFFFF)
    val GlassBorderStrong = Color(0x475CE1E6)
    val HingeGuideTint = Color(0x2238BDF8)

    // Vibrant Electric Cyan / Indigo Accents
    val ElectricCyan = Color(0xFF38BDF8)
    val CyanBright = Color(0xFF67E8F9)
    val CyanGlow = Color(0x3338BDF8)
    val IndigoAccent = Color(0xFF6366F1)
    val VioletAccent = Color(0xFF8B5CF6)

    // Status & Semantic Tones
    val EmeraldSuccess = Color(0xFF34D399)
    val AmberWarning = Color(0xFFFBBF24)
    val CoralDanger = Color(0xFFF87171)
    val CoralAlert = CoralDanger

    // High-Contrast Typography
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFCBD5E1)
    val TextMuted = Color(0xFF64748B)
}

private val FoldDarkColorScheme = darkColorScheme(
    primary = FoldLauncherColors.ElectricCyan,
    onPrimary = Color(0xFF02131D),
    primaryContainer = Color(0xFF0C2D48),
    onPrimaryContainer = FoldLauncherColors.CyanBright,
    secondary = FoldLauncherColors.IndigoAccent,
    onSecondary = FoldLauncherColors.TextPrimary,
    secondaryContainer = Color(0xFF1E1B4B),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = FoldLauncherColors.VioletAccent,
    background = FoldLauncherColors.OledBlack,
    onBackground = FoldLauncherColors.TextPrimary,
    surface = FoldLauncherColors.GlassSurface,
    onSurface = FoldLauncherColors.TextPrimary,
    surfaceVariant = FoldLauncherColors.GlassSurfaceElevated,
    onSurfaceVariant = FoldLauncherColors.TextSecondary,
    outline = FoldLauncherColors.GlassBorder,
    error = FoldLauncherColors.CoralDanger
)

val FoldLauncherTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
        color = FoldLauncherColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
        color = FoldLauncherColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = FoldLauncherColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = FoldLauncherColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = FoldLauncherColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = FoldLauncherColors.TextSecondary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
        color = FoldLauncherColors.TextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
        color = FoldLauncherColors.TextSecondary
    )
)

@Composable
fun FoldLauncherTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FoldDarkColorScheme,
        typography = FoldLauncherTypography,
        content = content
    )
}
