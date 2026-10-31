package com.xsun.foldlauncher.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.xsun.foldlauncher.applySystemBarsToWindow
import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.model.AppShortcutItem
import com.xsun.foldlauncher.ui.animation.iconPressPhysics
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.ui.theme.IconShapePreset
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor

// Strict 6-Bit RGB222 (64-Color) MIP Hardware Palette (2 bits per R, G, B channel: 0x00, 0x55, 0xAA, 0xFF)
private val MIP_BLACK = Color(0xFF000000)
private val MIP_DARK_BLUE = Color(0xFF000055)
private val MIP_DARK_TEAL = Color(0xFF005555)
private val MIP_DARK_GRAY = Color(0xFF555555)
private val MIP_SILVER = Color(0xFFAAAAAA)
private val MIP_WHITE = Color(0xFFFFFFFF)
private val MIP_CYAN = Color(0xFF00FFFF)
private val MIP_GREEN = Color(0xFF00FF55)

private const val MIP_ICON_GRID_RES = 20
private val mipQuantizedIconCache = ConcurrentHashMap<String, IntArray>()

/**
 * Downsamples a high-DPI truecolor Android app icon into a `20x20` low-resolution (~200 PPI)
 * pixel matrix and quantizes every pixel to the hardware 6-bit `RGB222` (64-color) MIP palette
 * (`0x00`, `0x55`, `0xAA`, `0xFF` per channel) using 2x2 ordered Bayer dithering.
 */
private fun getOrBuildMipRgb222Cells(cacheKey: String, imageBitmap: ImageBitmap): IntArray {
    mipQuantizedIconCache[cacheKey]?.let { return it }
    val out = IntArray(MIP_ICON_GRID_RES * MIP_ICON_GRID_RES)
    runCatching {
        val srcBitmap = imageBitmap.asAndroidBitmap()
        val softBitmap = if (srcBitmap.config == Bitmap.Config.HARDWARE) {
            srcBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            srcBitmap
        }
        val scaled = Bitmap.createScaledBitmap(softBitmap, MIP_ICON_GRID_RES, MIP_ICON_GRID_RES, true)
        val rawPixels = IntArray(MIP_ICON_GRID_RES * MIP_ICON_GRID_RES)
        scaled.getPixels(rawPixels, 0, MIP_ICON_GRID_RES, 0, 0, MIP_ICON_GRID_RES, MIP_ICON_GRID_RES)

        // 2x2 Bayer ordered dither thresholds (-24, +8, +24, -8) for 6-bit RGB222 quantization
        val bayer2x2 = intArrayOf(-24, 8, 24, -8)

        for (y in 0 until MIP_ICON_GRID_RES) {
            for (x in 0 until MIP_ICON_GRID_RES) {
                val idx = y * MIP_ICON_GRID_RES + x
                val argb = rawPixels[idx]
                val a = (argb ushr 24) and 0xFF
                if (a < 96) {
                    // Transparent cell -> MIP reflective black/dark-blue checkerboard cell
                    out[idx] = if ((x + y) % 2 == 0) 0xFF000000.toInt() else 0xFF000055.toInt()
                } else {
                    val dither = bayer2x2[((y and 1) shl 1) or (x and 1)]
                    val r8 = (((argb ushr 16) and 0xFF) + dither).coerceIn(0, 255)
                    val g8 = (((argb ushr 8) and 0xFF) + dither).coerceIn(0, 255)
                    val b8 = ((argb and 0xFF) + dither).coerceIn(0, 255)

                    // Quantize 8-bit channel (0..255) -> 2-bit MIP channel (0..3) -> replicate (0x00, 0x55, 0xAA, 0xFF)
                    val r2 = (r8 ushr 6) * 0x55
                    val g2 = (g8 ushr 6) * 0x55
                    val b2 = (b8 ushr 6) * 0x55
                    out[idx] = (0xFF shl 24) or (r2 shl 16) or (g2 shl 8) or b2
                }
            }
        }
    }.onFailure {
        for (i in out.indices) {
            out[i] = 0xFF005555.toInt()
        }
    }
    mipQuantizedIconCache[cacheKey] = out
    return out
}

@Composable
fun AppIconTile(
    app: AppEntry,
    iconShapePreset: IconShapePreset = IconShapePreset.SQUIRCLE,
    showLabel: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Low-resolution MIP chamfered pixel frame (stepped 3.dp cut corners instead of smooth high-DPI curves)
    val mipShape = remember { CutCornerShape(3.dp) }
    val iconSize = if (compact) 46.dp else 52.dp

    Column(
        modifier = modifier
            .iconPressPhysics(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = if (compact) 3.dp else 4.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(iconSize),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(mipShape)
                    .background(MIP_BLACK)
                    .border(
                        width = if (app.isWorkProfile) 1.5.dp else 1.dp,
                        color = if (app.isWorkProfile) MIP_CYAN else MIP_DARK_GRAY,
                        shape = mipShape
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = app.icon
                if (bitmap != null) {
                    val mipCells = remember(app.uniqueKey, bitmap) {
                        getOrBuildMipRgb222Cells(app.uniqueKey, bitmap)
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val grid = MIP_ICON_GRID_RES
                        val cellW = size.width / grid
                        val cellH = size.height / grid
                        val gap = 0.75f
                        for (gy in 0 until grid) {
                            for (gx in 0 until grid) {
                                val c = Color(mipCells[gy * grid + gx])
                                drawRect(
                                    color = c,
                                    topLeft = Offset(gx * cellW, gy * cellH),
                                    size = Size(
                                        (cellW - gap).coerceAtLeast(1f),
                                        (cellH - gap).coerceAtLeast(1f)
                                    )
                                )
                            }
                        }
                    }
                } else {
                    val baseColor = remember(app.packageName) {
                        mipPaletteColorForPackage(app.packageName)
                    }
                    val monogramText = remember(app.label, app.acronym) {
                        app.acronym.uppercase(Locale.US).take(2).ifEmpty {
                            app.firstLetter.toString()
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(baseColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = monogramText,
                            fontSize = if (compact) 14.sp else 17.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = MIP_WHITE
                        )
                    }
                }
            }

            if (app.isWorkProfile) {
                OneUiWorkBriefcaseBadge(
                    compact = compact,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                )
            }
        }

        if (showLabel) {
            Spacer(modifier = Modifier.height(if (compact) 3.dp else 4.dp))
            Text(
                text = app.label.uppercase(Locale.US),
                fontSize = if (compact) 9.5.sp else 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                color = MIP_WHITE,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(MIP_BLACK, CutCornerShape(1.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )
        }
    }
}

/**
 * Minimal 9x9 MIP Pixel-Matrix Briefcase Badge (`profile 10` / managed work profile):
 * Strict 6-bit `RGB222` `#000000` (`MIP_BLACK`) with `1.dp` `#00FFFF` (`MIP_CYAN`) chamfered border
 * and integer-snapped `drawRect` pixel blocks forming a 9x9 pixel-art briefcase.
 */
@Composable
fun OneUiWorkBriefcaseBadge(
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val badgeSize = if (compact) 14.dp else 16.dp
    val badgeShape = CutCornerShape(2.dp)

    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(badgeShape)
            .background(MIP_BLACK)
            .border(1.dp, MIP_CYAN, badgeShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val grid = 9
            val cell = floor(size.width / grid).coerceAtLeast(1f)
            val total = cell * grid
            val startX = floor((size.width - total) / 2f)
            val startY = floor((size.height - total) / 2f)

            fun px(gx: Int, gy: Int) {
                drawRect(
                    color = MIP_CYAN,
                    topLeft = Offset(startX + gx * cell, startY + gy * cell),
                    size = Size(cell, cell)
                )
            }

            // 3x1 top handle bar (y = 1, x = 3..5) + handle posts (y = 2, x = 3, 5)
            px(3, 1)
            px(4, 1)
            px(5, 1)
            px(3, 2)
            px(5, 2)

            // 7x4 pixel-matrix briefcase body (x = 1..7, y = 3..6) with 1x1 center clasp cutout at (4, 4)
            for (gy in 3..6) {
                for (gx in 1..7) {
                    if (gx == 4 && gy == 4) continue
                    px(gx, gy)
                }
            }
        }
    }
}

private fun mipPaletteColorForPackage(packageName: String): Color {
    // 6-bit RGB222 quantized colors only
    val palettes = listOf(
        Color(0xFF0055AA),
        Color(0xFF5500AA),
        Color(0xFF005555),
        Color(0xFFAA0055),
        Color(0xFF00AA55),
        Color(0xFF555500)
    )
    val index = abs(packageName.hashCode()) % palettes.size
    return palettes[index]
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppContextMenuSheet(
    app: AppEntry,
    hideSystemStatusBar: Boolean = false,
    onDismiss: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLaunchAdjacentSplit: (AppEntry) -> Unit,
    onTogglePinCover: (AppEntry) -> Unit,
    onTogglePinInner: (AppEntry) -> Unit,
    onTogglePinDock: (AppEntry) -> Unit,
    onLaunchShortcut: (AppShortcutItem) -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    onUninstallApp: (AppEntry) -> Unit,
    onHideApp: (AppEntry) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FoldLauncherColors.GlassSurfaceElevated,
        contentColor = FoldLauncherColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        val sheetView = LocalView.current
        LaunchedEffect(hideSystemStatusBar) {
            val dialogWin = (sheetView.parent as? DialogWindowProvider)?.window
            val actWin = (sheetView.context as? android.app.Activity)?.window
            applySystemBarsToWindow(dialogWin, hideSystemStatusBar)
            applySystemBarsToWindow(actWin, hideSystemStatusBar)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppIconTile(
                    app = app,
                    showLabel = false,
                    compact = true,
                    onClick = {
                        onLaunch(app)
                        onDismiss()
                    },
                    onLongClick = {}
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.TextPrimary
                    )
                    Text(
                        text = "${app.packageName} · ${app.category}",
                        fontSize = 12.sp,
                        color = FoldLauncherColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Dynamic shortcuts section if available
            if (app.shortcuts.isNotEmpty()) {
                HorizontalDivider(color = FoldLauncherColors.GlassBorder)
                Text(
                    text = "APP SHORTCUTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.ElectricCyan
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.shortcuts.take(4).forEach { shortcut ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(FoldLauncherColors.GlassSurfaceHighlight)
                                .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    onLaunchShortcut(shortcut)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "↗",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = FoldLauncherColors.ElectricCyan
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = shortcut.longLabel.ifBlank { shortcut.shortLabel },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = FoldLauncherColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = FoldLauncherColors.GlassBorder)

            // Posture pinning chips
            Text(
                text = "POSTURE & DOCK PINNING",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.ElectricCyan
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ContextActionChip(
                    label = if (app.isPinnedCover) "★ Cover Pinned" else "☆ Pin to Cover",
                    active = app.isPinnedCover,
                    onClick = {
                        onTogglePinCover(app)
                        onDismiss()
                    }
                )
                ContextActionChip(
                    label = if (app.isPinnedInner) "★ Inner Pinned" else "☆ Pin to Inner",
                    active = app.isPinnedInner,
                    onClick = {
                        onTogglePinInner(app)
                        onDismiss()
                    }
                )
                ContextActionChip(
                    label = if (app.isDockPinned) "★ Dock Pinned" else "☆ Pin to Dock",
                    active = app.isDockPinned,
                    onClick = {
                        onTogglePinDock(app)
                        onDismiss()
                    }
                )
            }

            HorizontalDivider(color = FoldLauncherColors.GlassBorder)

            // Multi-window & system actions
            ContextActionRow(
                glyph = "▣",
                title = "Open in Split-Screen / Multi-Window",
                subtitle = "Launch adjacent on Z Fold 8 inner screen or DeX",
                accent = FoldLauncherColors.ElectricCyan,
                onClick = {
                    onLaunchAdjacentSplit(app)
                    onDismiss()
                }
            )
            ContextActionRow(
                glyph = "ℹ",
                title = "App Info & Permissions",
                subtitle = "Inspect system settings for ${app.label}",
                accent = FoldLauncherColors.TextSecondary,
                onClick = {
                    onOpenAppInfo(app)
                    onDismiss()
                }
            )
            ContextActionRow(
                glyph = "◌",
                title = "Hide from Launcher",
                subtitle = "Keep installed but hide from home & drawer",
                accent = FoldLauncherColors.AmberWarning,
                onClick = {
                    onHideApp(app)
                    onDismiss()
                }
            )
            ContextActionRow(
                glyph = "✕",
                title = "Uninstall App",
                subtitle = "Remove ${app.packageName} from device",
                accent = FoldLauncherColors.CoralDanger,
                onClick = {
                    onUninstallApp(app)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ContextActionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (active) FoldLauncherColors.CyanGlow else FoldLauncherColors.GlassSurfaceHighlight
            )
            .border(
                1.dp,
                if (active) FoldLauncherColors.ElectricCyan else FoldLauncherColors.GlassBorder,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) FoldLauncherColors.CyanBright else FoldLauncherColors.TextPrimary
        )
    }
}

@Composable
private fun ContextActionRow(
    glyph: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FoldLauncherColors.GlassSurface)
            .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = glyph,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.width(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = FoldLauncherColors.TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = FoldLauncherColors.TextSecondary
            )
        }
    }
}
