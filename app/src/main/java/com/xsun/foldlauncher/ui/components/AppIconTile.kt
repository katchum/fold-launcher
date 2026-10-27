package com.xsun.foldlauncher.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.model.AppShortcutItem
import com.xsun.foldlauncher.ui.animation.iconPressPhysics
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.ui.theme.IconShapePreset
import com.xsun.foldlauncher.ui.theme.iconShapeForPreset
import kotlin.math.abs

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
    val shape = remember(iconShapePreset) { iconShapeForPreset(iconShapePreset) }
    val iconSize = if (compact) 46.dp else 56.dp

    Column(
        modifier = modifier
            .iconPressPhysics(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = if (compact) 3.dp else 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(shape)
                .background(FoldLauncherColors.GlassSurfaceElevated)
                .border(0.75.dp, FoldLauncherColors.GlassBorder, shape),
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
                val gradientColors = remember(app.packageName) {
                    monogramGradientForPackage(app.packageName)
                }
                val monogramText = remember(app.label, app.acronym) {
                    app.acronym.uppercase().take(2).ifEmpty {
                        app.firstLetter.toString()
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = monogramText,
                        fontSize = if (compact) 16.sp else 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.TextPrimary
                    )
                }
            }
        }

        if (showLabel) {
            Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))
            Text(
                text = app.label,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                color = FoldLauncherColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun monogramGradientForPackage(packageName: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF0EA5E9), Color(0xFF312E81)),
        listOf(Color(0xFF6366F1), Color(0xFF701A75)),
        listOf(Color(0xFF14B8A6), Color(0xFF0F172A)),
        listOf(Color(0xFFF43F5E), Color(0xFF4C1D95)),
        listOf(Color(0xFF8B5CF6), Color(0xFF1E3A8A)),
        listOf(Color(0xFF10B981), Color(0xFF064E3B))
    )
    val index = abs(packageName.hashCode()) % palettes.size
    return palettes[index]
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppContextMenuSheet(
    app: AppEntry,
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
