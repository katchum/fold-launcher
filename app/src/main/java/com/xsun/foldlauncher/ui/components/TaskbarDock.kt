package com.xsun.foldlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.model.AppPair
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.ui.theme.IconShapePreset

@Composable
fun PersistentTaskbarDock(
    dockApps: List<AppEntry>,
    recentApps: List<AppEntry>,
    appPairs: List<AppPair>,
    allAppsMap: Map<String, AppEntry>,
    iconShapePreset: IconShapePreset,
    onAppClick: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onLaunchAppPair: (AppPair) -> Unit,
    onOpenAllAppsSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockShape = RoundedCornerShape(28.dp)
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(dockShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            FoldLauncherColors.GlassSurfaceElevated,
                            FoldLauncherColors.DockGlassSurface
                        )
                    )
                )
                .border(1.dp, FoldLauncherColors.GlassBorderStrong, dockShape)
                .horizontalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. All Apps / Spotlight Launcher Button
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(FoldLauncherColors.CyanGlow)
                    .border(1.dp, FoldLauncherColors.ElectricCyan, RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenAllAppsSearch)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "⊞",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.CyanBright
                )
                Text(
                    text = "Apps",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.TextPrimary
                )
            }

            // 2. Pinned Dock Apps
            dockApps.forEach { app ->
                AppIconTile(
                    app = app,
                    iconShapePreset = iconShapePreset,
                    showLabel = false,
                    compact = true,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongClick(app) }
                )
            }

            // 3. Split-Screen App Pair Chips (if any)
            if (appPairs.isNotEmpty()) {
                TaskbarDivider()
                appPairs.forEach { pair ->
                    val primaryLabel = allAppsMap[pair.primaryPackage]?.label ?: pair.title
                    val secondaryLabel = allAppsMap[pair.secondaryPackage]?.label ?: ""
                    AppPairChip(
                        pair = pair,
                        subtitle = if (secondaryLabel.isNotEmpty()) {
                            "$primaryLabel + $secondaryLabel"
                        } else {
                            pair.title
                        },
                        onClick = { onLaunchAppPair(pair) }
                    )
                }
            }

            // 4. Recent Apps (filtered to avoid duplicating pinned dock items)
            val dockPackageSet = dockApps.map { it.packageName }.toSet()
            val filteredRecents = recentApps
                .filter { it.packageName !in dockPackageSet }
                .take(3)

            if (filteredRecents.isNotEmpty()) {
                TaskbarDivider()
                filteredRecents.forEach { recent ->
                    Box(contentAlignment = Alignment.BottomCenter) {
                        AppIconTile(
                            app = recent,
                            iconShapePreset = iconShapePreset,
                            showLabel = false,
                            compact = true,
                            onClick = { onAppClick(recent) },
                            onLongClick = { onAppLongClick(recent) }
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(FoldLauncherColors.ElectricCyan)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskbarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(34.dp)
            .background(FoldLauncherColors.GlassBorder)
    )
}

@Composable
private fun AppPairChip(
    pair: AppPair,
    subtitle: String,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(FoldLauncherColors.GlassSurfaceHighlight)
            .border(1.dp, FoldLauncherColors.IndigoAccent.copy(alpha = 0.65f), chipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "◫",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = FoldLauncherColors.CyanBright
        )
        Column {
            Text(
                text = pair.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = FoldLauncherColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
    }
}
