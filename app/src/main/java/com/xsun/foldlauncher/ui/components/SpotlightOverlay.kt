package com.xsun.foldlauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.hud.FloatingHudController
import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.omni.ClipboardItem
import com.xsun.foldlauncher.omni.ClipboardVault
import com.xsun.foldlauncher.omni.OmniAction
import com.xsun.foldlauncher.omni.OmniCommandEngine
import com.xsun.foldlauncher.omni.OmniCommandResultsCard
import com.xsun.foldlauncher.ui.animation.LauncherSpringSpecs
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.ui.theme.IconShapePreset

@Composable
fun SpotlightSearchOverlay(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    rankedApps: List<AppEntry>,
    inlineMathResult: String?,
    iconShapePreset: IconShapePreset,
    onLaunchApp: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onWebSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    allApps: List<AppEntry> = rankedApps,
    clipboardItems: List<ClipboardItem>? = null,
    onExecuteOmniAction: ((OmniAction) -> Unit)? = null,
    onToggleThumbArc: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current

    val localVault = remember(context) { ClipboardVault(context) }
    val vaultClips by localVault.items.collectAsState()
    val effectiveClips = clipboardItems ?: vaultClips
    val candidateApps = if (allApps.isNotEmpty()) allApps else rankedApps

    val omniActions = remember(query, candidateApps, effectiveClips) {
        OmniCommandEngine.evaluate(
            rawQuery = query,
            installedApps = candidateApps,
            clipboardItems = effectiveClips
        )
    }

    LaunchedEffect(visible) {
        if (visible) {
            localVault.captureFromSystemClipboard(context)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = LauncherSpringSpecs.SearchSheetSpring) +
            slideInVertically(
                animationSpec = LauncherSpringSpecs.Snappy120HzIntOffset,
                initialOffsetY = { -it / 12 }
            ),
        exit = fadeOut(animationSpec = LauncherSpringSpecs.SearchSheetSpring) +
            slideOutVertically(
                animationSpec = LauncherSpringSpecs.Snappy120HzIntOffset,
                targetOffsetY = { -it / 12 }
            )
    ) {
        val backdropInteraction = remember { MutableInteractionSource() }
        val cardInteraction = remember { MutableInteractionSource() }
        val topHit = rankedApps.firstOrNull()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FoldLauncherColors.OledBlack.copy(alpha = 0.88f))
                .clickable(
                    interactionSource = backdropInteraction,
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                        onClick = {}
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Instant 0ms Spotlight + Omni-Command Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    FoldLauncherColors.GlassSurfaceHighlight,
                                    FoldLauncherColors.GlassSurfaceElevated
                                )
                            )
                        )
                        .border(1.5.dp, FoldLauncherColors.ElectricCyan, RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchMagnifierGlyph(
                        color = FoldLauncherColors.ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Apps, split: a+b, > dex, vc: prompt, clip:, math, hex/dpi…",
                                fontSize = 14.sp,
                                color = FoldLauncherColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FoldLauncherColors.TextPrimary
                            ),
                            cursorBrush = SolidColor(FoldLauncherColors.ElectricCyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val primaryOmni = omniActions.firstOrNull()
                                    if (primaryOmni != null &&
                                        (primaryOmni is OmniAction.LaunchAdHocSplit ||
                                            primaryOmni is OmniAction.SystemCommand ||
                                            primaryOmni is OmniAction.SendToVc)
                                    ) {
                                        OmniCommandEngine.execute(
                                            context = context,
                                            action = primaryOmni,
                                            clipboardVault = localVault,
                                            onToggleFloatingHud = {
                                                val current = FloatingHudController.state.value.isEnabled
                                                FloatingHudController.setHudEnabled(context, !current)
                                            },
                                            onToggleThumbArc = onToggleThumbArc
                                        )
                                        onExecuteOmniAction?.invoke(primaryOmni)
                                        onDismiss()
                                    } else if (topHit != null) {
                                        onLaunchApp(topHit)
                                    } else if (query.isNotBlank()) {
                                        onWebSearch(query)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    if (query.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(FoldLauncherColors.GlassSurface)
                                .clickable { onQueryChange("") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FoldLauncherColors.TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ESC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(FoldLauncherColors.GlassSurface)
                            .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(10.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 2. Omni-Command Palette & Clipboard Vault Results Card
                // (Ad-hoc split: app1 + app2, > dex/rotation/hud/arc commands, vc: dispatch, hex/dpi/epoch, clip: vault)
                OmniCommandResultsCard(
                    actions = omniActions,
                    query = query,
                    clipboardItems = effectiveClips,
                    onExecuteAction = { action ->
                        onExecuteOmniAction?.invoke(action)
                        if (action is OmniAction.LaunchAdHocSplit ||
                            action is OmniAction.SendToVc ||
                            action is OmniAction.SystemCommand
                        ) {
                            onDismiss()
                        }
                    },
                    onSelectQuerySuggestion = { suggestedPrefix ->
                        onQueryChange(suggestedPrefix)
                    },
                    onTogglePinClip = { clipId ->
                        localVault.togglePin(clipId)
                    },
                    onCopyClip = { item ->
                        localVault.addOrBumpClip(
                            text = item.text,
                            label = item.label,
                            isPinned = item.isPinned,
                            category = item.category
                        )
                    },
                    showQuickCommandPillsWhenIdle = true
                )

                // 3. Inline Calculator Card (when query evaluates to a math result)
                if (inlineMathResult != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        FoldLauncherColors.CyanGlow,
                                        FoldLauncherColors.GlassSurfaceElevated
                                    )
                                )
                            )
                            .border(1.dp, FoldLauncherColors.ElectricCyan, RoundedCornerShape(20.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(inlineMathResult))
                                localVault.addOrBumpClip(
                                    text = inlineMathResult,
                                    label = "Math: ${query.trim()}",
                                    category = "Calculator"
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "INLINE CALCULATOR · TAP TO COPY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FoldLauncherColors.CyanBright
                            )
                            Text(
                                text = "${query.trim()} = $inlineMathResult",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FoldLauncherColors.TextPrimary
                            )
                        }
                        Text(
                            text = "Copy",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoldLauncherColors.ElectricCyan
                        )
                    }
                }

                // 4. Top Hit Instant Launch Banner (when user has typed a query and a top hit exists)
                if (query.isNotBlank() && topHit != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(FoldLauncherColors.GlassSurfaceElevated)
                            .border(1.dp, FoldLauncherColors.GlassBorderStrong, RoundedCornerShape(20.dp))
                            .clickable { onLaunchApp(topHit) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIconTile(
                                app = topHit,
                                iconShapePreset = iconShapePreset,
                                showLabel = false,
                                compact = true,
                                onClick = { onLaunchApp(topHit) },
                                onLongClick = { onAppLongClick(topHit) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "TOP HIT · PRESS GO TO LAUNCH",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FoldLauncherColors.ElectricCyan
                                )
                                Text(
                                    text = topHit.label,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FoldLauncherColors.TextPrimary
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(FoldLauncherColors.ElectricCyan)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Open ↵",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF02131D)
                            )
                        }
                    }
                }

                // 5. Web Search Action Row
                if (query.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(FoldLauncherColors.GlassSurface)
                            .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(16.dp))
                            .clickable { onWebSearch(query) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🌐 Search Web for \"${query.trim()}\"",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FoldLauncherColors.CyanBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "↗",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoldLauncherColors.ElectricCyan
                        )
                    }
                }

                // 6. Ranked Apps Grid
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(FoldLauncherColors.GlassSurface)
                        .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(24.dp))
                        .padding(12.dp)
                ) {
                    if (rankedApps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching apps installed · Use Omni commands or search the web above",
                                fontSize = 13.sp,
                                color = FoldLauncherColors.TextSecondary
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 76.dp),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = rankedApps,
                                key = { "${it.packageName}/${it.activityName}" }
                            ) { app ->
                                AppIconTile(
                                    app = app,
                                    iconShapePreset = iconShapePreset,
                                    showLabel = true,
                                    compact = false,
                                    onClick = { onLaunchApp(app) },
                                    onLongClick = { onAppLongClick(app) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
