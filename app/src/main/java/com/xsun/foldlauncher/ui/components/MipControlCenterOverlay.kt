package com.xsun.foldlauncher.ui.components

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.service.FoldAccessibilityService
import com.xsun.foldlauncher.service.FoldNotificationListenerService
import com.xsun.foldlauncher.service.MipNotificationItem
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.ui.animation.LauncherSpringSpecs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Strict 6-Bit RGB222 (64-Color) 16x-DPI MIP Hardware Palette
private val MipBlack = Color(0xFF000000)
private val MipDarkNavy = Color(0xFF000055)
private val MipTeal = Color(0xFF005555)
private val MipGrid = Color(0xFF555555)
private val MipMuted = Color(0xFFAAAAAA)
private val MipCyan = Color(0xFF00FFFF)
private val MipGreen = Color(0xFF00FF55)
private val MipYellow = Color(0xFFFFFF00)
private val MipRed = Color(0xFFFF5555)
private val MipWhite = Color(0xFFFFFFFF)

private enum class MipShadeTab(val label: String) {
    SPLIT_VIEW("SPLIT VIEW"),
    NOTIFICATIONS("NOTIFS"),
    QUICK_SETTINGS("QUICK SETTINGS")
}

/**
 * v0.13.0 In-Launcher 16x-DPI MIP Notification Shade + Quick Settings Control Center (`MipControlCenterOverlay`).
 *
 * Replaces the stock system swipe-down shade with a custom 16x-DPI Memory-In-Pixel (MIP) Terminal
 * Notification Center & Hardware Quick Settings deck:
 * - Adaptive Dual-Pane side-by-side layout on Unfolded Z Fold 8 (`isExpanded == true` or `maxWidth >= 580.dp`).
 * - Tabbed / stacked view (`[SPLIT VIEW]`, `[NOTIFS (N)]`, `[QUICK SETTINGS]`) on Cover & Slab screens.
 * - Live Notification feed via [FoldNotificationListenerService] with `80x80` `RGB222` MIP app icons,
 *   inline action buttons, `[OPEN ->]`, `[DISMISS]`, and `[CLEAR ALL]`.
 * - Full MIP Hardware Quick Settings deck: Wi-Fi & Bluetooth hero tiles, 4x3 (12-tile) hardware toggle matrix,
 *   16-segment interactive Brightness (`BRT`) & Volume (`VOL`) bars, and Media Transport controls.
 */
@Composable
fun MipControlCenterOverlay(
    visible: Boolean,
    isExpanded: Boolean = false,
    installedApps: List<AppEntry> = emptyList(),
    isWirelessAdbEnabled: Boolean = false,
    wirelessAdbPort: Int? = null,
    tailscaleIp: String? = null,
    lanIp: String? = null,
    wallpaperBadge: String = "TEAL",
    onToggleWirelessAdb: (() -> Unit)? = null,
    onToggleTailscale: (() -> Unit)? = null,
    onSplitTopTwoRecentApps: (() -> Unit)? = null,
    onOpenRecents: (() -> Unit)? = null,
    onCycleWallpaperPreset: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val notifications by FoldNotificationListenerService.activeNotificationsFlow.collectAsState()
    val listenerConnected by FoldNotificationListenerService.isListenerConnectedFlow.collectAsState()
    val torchActive by SystemTweaker.isTorchActive.collectAsState()

    var isListenerGranted by remember {
        mutableStateOf(FoldNotificationListenerService.isNotificationListenerEnabled(context))
    }
    var selectedTab by remember { mutableStateOf(MipShadeTab.SPLIT_VIEW) }

    var clockText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var brightnessPct by remember { mutableIntStateOf(SystemTweaker.getBrightnessPercent(context)) }
    var volumePct by remember { mutableIntStateOf(SystemTweaker.getMediaVolumePercent(context)) }
    var ringerLabel by remember { mutableStateOf(SystemTweaker.getRingerModeLabel(context)) }
    var autoRotateOn by remember { mutableStateOf(SystemTweaker.isLockScreenRotationEnabled(context)) }
    var locationOn by remember { mutableStateOf(SystemTweaker.isLocationEnabled(context)) }
    var airplaneOn by remember { mutableStateOf(SystemTweaker.isAirplaneModeEnabled(context)) }
    var bluetoothOn by remember { mutableStateOf(isBluetoothEnabledSafe(context)) }
    var netSnapshot by remember { mutableStateOf(SystemTweaker.readNetworkTelemetry(context)) }

    val appsByPkg = remember(installedApps) {
        installedApps.associateBy { it.packageName }
    }

    LaunchedEffect(visible) {
        if (visible) {
            FoldNotificationListenerService.refreshNotifications()
            while (true) {
                val now = Date()
                clockText = SimpleDateFormat("HH:mm:ss", Locale.US).format(now)
                dateText = SimpleDateFormat("EEE dd MMM", Locale.US).format(now).uppercase(Locale.US)
                isListenerGranted = FoldNotificationListenerService.isNotificationListenerEnabled(context)
                brightnessPct = SystemTweaker.getBrightnessPercent(context)
                volumePct = SystemTweaker.getMediaVolumePercent(context)
                ringerLabel = SystemTweaker.getRingerModeLabel(context)
                autoRotateOn = SystemTweaker.isLockScreenRotationEnabled(context)
                locationOn = SystemTweaker.isLocationEnabled(context)
                airplaneOn = SystemTweaker.isAirplaneModeEnabled(context)
                bluetoothOn = isBluetoothEnabledSafe(context)
                netSnapshot = SystemTweaker.readNetworkTelemetry(context)
                delay(1_500L)
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = LauncherSpringSpecs.SearchSheetSpring) +
            slideInVertically(
                animationSpec = LauncherSpringSpecs.Snappy120HzIntOffset,
                initialOffsetY = { -it / 6 }
            ),
        exit = fadeOut(animationSpec = LauncherSpringSpecs.SearchSheetSpring) +
            slideOutVertically(
                animationSpec = LauncherSpringSpecs.Snappy120HzIntOffset,
                targetOffsetY = { -it / 6 }
            )
    ) {
        val headerShape = CutCornerShape(4.dp)
        val btnShape = CutCornerShape(2.dp)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MipBlack.copy(alpha = 0.96f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            val useDualColumns = isExpanded || maxWidth >= 580.dp

            // 16x-DPI MIP 1.dp dot-matrix backdrop
            Mip16xDpiMatrixBackdrop(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(headerShape)
                        .background(MipDarkNavy)
                        .border(1.dp, MipCyan, headerShape)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = clockText.ifEmpty { "00:00:00" },
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = MipCyan
                            )
                            Text(
                                text = dateText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MipMuted
                            )
                        }
                        Text(
                            text = "[MIP SHADE // NOTIFS + CONTROL CENTER]",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = MipWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(btnShape)
                                .background(MipBlack)
                                .border(1.dp, MipGrid, btnShape)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDismiss()
                                    SystemTweaker.expandNotificationsPanel(context)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "[SYS SHADE ▾]",
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = MipMuted
                            )
                        }

                        if (notifications.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(btnShape)
                                    .background(MipBlack)
                                    .border(1.dp, MipYellow, btnShape)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        FoldNotificationListenerService.dismissAllNotifications()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "[CLEAR ALL]",
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MipYellow
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(btnShape)
                                .background(MipCyan)
                                .border(1.dp, MipWhite, btnShape)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDismiss()
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "[CLOSE ✕]",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = MipBlack
                            )
                        }
                    }
                }

                if (useDualColumns) {
                    // Adaptive Side-by-Side 2-Column Layout on Unfolded Z Fold 8
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.05f)
                                .fillMaxHeight()
                        ) {
                            MipNotificationShadePane(
                                notifications = notifications,
                                isListenerEnabled = isListenerGranted || listenerConnected,
                                appsByPkg = appsByPkg,
                                onDismissOverlay = onDismiss,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(0.95f)
                                .fillMaxHeight()
                        ) {
                            MipQuickSettingsControlPane(
                                isWifiConnected = netSnapshot.isWifiConnected || !lanIp.isNullOrBlank(),
                                lanIp = lanIp ?: netSnapshot.lanIp,
                                tailscaleIp = tailscaleIp ?: netSnapshot.tailscaleIp,
                                isBluetoothEnabled = bluetoothOn,
                                isTorchEnabled = torchActive,
                                isAutoRotateEnabled = autoRotateOn,
                                ringerLabel = ringerLabel,
                                isWirelessAdbEnabled = isWirelessAdbEnabled,
                                wirelessAdbPort = wirelessAdbPort,
                                isLocationEnabled = locationOn,
                                isAirplaneEnabled = airplaneOn,
                                wallpaperBadge = wallpaperBadge,
                                brightnessPct = brightnessPct,
                                volumePct = volumePct,
                                onUpdateBrightness = { newPct ->
                                    brightnessPct = newPct
                                    SystemTweaker.setBrightnessPercent(context, newPct)
                                },
                                onUpdateVolume = { newPct ->
                                    volumePct = newPct
                                    SystemTweaker.setMediaVolumePercent(context, newPct)
                                },
                                onRefreshToggles = {
                                    autoRotateOn = SystemTweaker.isLockScreenRotationEnabled(context)
                                    ringerLabel = SystemTweaker.getRingerModeLabel(context)
                                    locationOn = SystemTweaker.isLocationEnabled(context)
                                    airplaneOn = SystemTweaker.isAirplaneModeEnabled(context)
                                    bluetoothOn = isBluetoothEnabledSafe(context)
                                    netSnapshot = SystemTweaker.readNetworkTelemetry(context)
                                },
                                onToggleWirelessAdb = onToggleWirelessAdb,
                                onToggleTailscale = onToggleTailscale,
                                onSplitTopTwoRecentApps = {
                                    onDismiss()
                                    onSplitTopTwoRecentApps?.invoke()
                                },
                                onOpenRecents = {
                                    onDismiss()
                                    onOpenRecents?.invoke()
                                },
                                onCycleWallpaperPreset = onCycleWallpaperPreset,
                                onDismissOverlay = onDismiss,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    // Cover or Slab Screen: Top Tab Selector (`[SPLIT VIEW]`, `[NOTIFS (N)]`, `[QUICK SETTINGS]`)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MipShadeTab.entries.forEach { tab ->
                            val active = selectedTab == tab
                            val label = if (tab == MipShadeTab.NOTIFICATIONS) {
                                "NOTIFS (${notifications.size})"
                            } else {
                                tab.label
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(btnShape)
                                    .background(if (active) MipCyan else MipDarkNavy)
                                    .border(
                                        width = 1.dp,
                                        color = if (active) MipWhite else MipGrid,
                                        shape = btnShape
                                    )
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedTab = tab
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (active) "[$label]" else label,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (active) MipBlack else MipWhite,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    when (selectedTab) {
                        MipShadeTab.NOTIFICATIONS -> {
                            MipNotificationShadePane(
                                notifications = notifications,
                                isListenerEnabled = isListenerGranted || listenerConnected,
                                appsByPkg = appsByPkg,
                                onDismissOverlay = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }

                        MipShadeTab.QUICK_SETTINGS -> {
                            MipQuickSettingsControlPane(
                                isWifiConnected = netSnapshot.isWifiConnected || !lanIp.isNullOrBlank(),
                                lanIp = lanIp ?: netSnapshot.lanIp,
                                tailscaleIp = tailscaleIp ?: netSnapshot.tailscaleIp,
                                isBluetoothEnabled = bluetoothOn,
                                isTorchEnabled = torchActive,
                                isAutoRotateEnabled = autoRotateOn,
                                ringerLabel = ringerLabel,
                                isWirelessAdbEnabled = isWirelessAdbEnabled,
                                wirelessAdbPort = wirelessAdbPort,
                                isLocationEnabled = locationOn,
                                isAirplaneEnabled = airplaneOn,
                                wallpaperBadge = wallpaperBadge,
                                brightnessPct = brightnessPct,
                                volumePct = volumePct,
                                onUpdateBrightness = { newPct ->
                                    brightnessPct = newPct
                                    SystemTweaker.setBrightnessPercent(context, newPct)
                                },
                                onUpdateVolume = { newPct ->
                                    volumePct = newPct
                                    SystemTweaker.setMediaVolumePercent(context, newPct)
                                },
                                onRefreshToggles = {
                                    autoRotateOn = SystemTweaker.isLockScreenRotationEnabled(context)
                                    ringerLabel = SystemTweaker.getRingerModeLabel(context)
                                    locationOn = SystemTweaker.isLocationEnabled(context)
                                    airplaneOn = SystemTweaker.isAirplaneModeEnabled(context)
                                    bluetoothOn = isBluetoothEnabledSafe(context)
                                    netSnapshot = SystemTweaker.readNetworkTelemetry(context)
                                },
                                onToggleWirelessAdb = onToggleWirelessAdb,
                                onToggleTailscale = onToggleTailscale,
                                onSplitTopTwoRecentApps = {
                                    onDismiss()
                                    onSplitTopTwoRecentApps?.invoke()
                                },
                                onOpenRecents = {
                                    onDismiss()
                                    onOpenRecents?.invoke()
                                },
                                onCycleWallpaperPreset = onCycleWallpaperPreset,
                                onDismissOverlay = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }

                        MipShadeTab.SPLIT_VIEW -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MipQuickSettingsControlPane(
                                    isWifiConnected = netSnapshot.isWifiConnected || !lanIp.isNullOrBlank(),
                                    lanIp = lanIp ?: netSnapshot.lanIp,
                                    tailscaleIp = tailscaleIp ?: netSnapshot.tailscaleIp,
                                    isBluetoothEnabled = bluetoothOn,
                                    isTorchEnabled = torchActive,
                                    isAutoRotateEnabled = autoRotateOn,
                                    ringerLabel = ringerLabel,
                                    isWirelessAdbEnabled = isWirelessAdbEnabled,
                                    wirelessAdbPort = wirelessAdbPort,
                                    isLocationEnabled = locationOn,
                                    isAirplaneEnabled = airplaneOn,
                                    wallpaperBadge = wallpaperBadge,
                                    brightnessPct = brightnessPct,
                                    volumePct = volumePct,
                                    onUpdateBrightness = { newPct ->
                                        brightnessPct = newPct
                                        SystemTweaker.setBrightnessPercent(context, newPct)
                                    },
                                    onUpdateVolume = { newPct ->
                                        volumePct = newPct
                                        SystemTweaker.setMediaVolumePercent(context, newPct)
                                    },
                                    onRefreshToggles = {
                                        autoRotateOn = SystemTweaker.isLockScreenRotationEnabled(context)
                                        ringerLabel = SystemTweaker.getRingerModeLabel(context)
                                        locationOn = SystemTweaker.isLocationEnabled(context)
                                        airplaneOn = SystemTweaker.isAirplaneModeEnabled(context)
                                        bluetoothOn = isBluetoothEnabledSafe(context)
                                        netSnapshot = SystemTweaker.readNetworkTelemetry(context)
                                    },
                                    onToggleWirelessAdb = onToggleWirelessAdb,
                                    onToggleTailscale = onToggleTailscale,
                                    onSplitTopTwoRecentApps = {
                                        onDismiss()
                                        onSplitTopTwoRecentApps?.invoke()
                                    },
                                    onOpenRecents = {
                                        onDismiss()
                                        onOpenRecents?.invoke()
                                    },
                                    onCycleWallpaperPreset = onCycleWallpaperPreset,
                                    onDismissOverlay = onDismiss,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(0.54f)
                                )

                                MipNotificationShadePane(
                                    notifications = notifications,
                                    isListenerEnabled = isListenerGranted || listenerConnected,
                                    appsByPkg = appsByPkg,
                                    onDismissOverlay = onDismiss,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(0.46f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MipNotificationShadePane(
    notifications: List<MipNotificationItem>,
    isListenerEnabled: Boolean,
    appsByPkg: Map<String, AppEntry>,
    onDismissOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val paneShape = CutCornerShape(4.dp)
    val bannerShape = CutCornerShape(3.dp)

    Column(
        modifier = modifier
            .clip(paneShape)
            .background(MipBlack)
            .border(1.dp, MipGrid, paneShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "[NOTIFICATIONS // ${notifications.size} ACTIVE]",
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = MipCyan
            )
            Text(
                text = if (isListenerEnabled) "LIVE_FEED: ON" else "LIVE_FEED: AUTH_REQ",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isListenerEnabled) MipGreen else MipYellow
            )
        }

        if (!isListenerEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(bannerShape)
                    .background(MipDarkNavy)
                    .border(1.dp, MipYellow, bannerShape)
                    .clickable {
                        FoldNotificationListenerService.openNotificationListenerSettings(context)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "[!] NOTIFICATION ACCESS REQUIRED FOR LIVE MIP FEED  [ENABLE ACCESS ->]",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = MipYellow
                )
            }
        }

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MipGrid, bannerShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[NO ACTIVE NOTIFICATIONS // ALL CLEAR]",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MipMuted
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                notifications.forEach { item ->
                    MipNotificationCard(
                        item = item,
                        appEntry = appsByPkg[item.packageName],
                        onOpenNotification = {
                            FoldNotificationListenerService.fireNotificationIntent(context, item)
                            onDismissOverlay()
                        },
                        onDismissNotification = {
                            FoldNotificationListenerService.dismissNotification(item.key)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MipNotificationCard(
    item: MipNotificationItem,
    appEntry: AppEntry?,
    onOpenNotification: () -> Unit,
    onDismissNotification: () -> Unit
) {
    val context = LocalContext.current
    val cardShape = CutCornerShape(4.dp)
    val btnShape = CutCornerShape(2.dp)
    val formattedTime = remember(item.postTime) {
        SimpleDateFormat("HH:mm", Locale.US).format(Date(item.postTime))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MipDarkNavy)
            .border(
                width = 1.dp,
                color = if (item.isOngoing) MipCyan else MipGrid,
                shape = cardShape
            )
            .clickable(onClick = onOpenNotification)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header Row: 80x80 RGB222 MIP app icon + appName.uppercase() + HH:mm + count badge + [✕]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(btnShape)
                        .background(MipBlack)
                        .border(1.dp, MipCyan, btnShape)
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val iconBitmap = appEntry?.icon
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = item.appName,
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = item.appName.take(2).uppercase(Locale.US),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = MipCyan
                        )
                    }
                }

                Text(
                    text = item.appName.uppercase(Locale.US),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = MipCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = formattedTime,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MipMuted
                )

                if (item.count > 1) {
                    Box(
                        modifier = Modifier
                            .clip(btnShape)
                            .background(MipCyan)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "x${item.count}",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = MipBlack
                        )
                    }
                }
            }

            if (item.isClearable) {
                Box(
                    modifier = Modifier
                        .clip(btnShape)
                        .background(MipBlack)
                        .border(1.dp, MipGrid, btnShape)
                        .clickable(onClick = onDismissNotification)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[✕]",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = MipMuted
                    )
                }
            }
        }

        // Content: Title + 2-3 lines of text + optional subText
        if (item.title.isNotBlank()) {
            Text(
                text = item.title,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = MipWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.text.isNotBlank()) {
            Text(
                text = item.text,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MipMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!item.subText.isNullOrBlank()) {
            Text(
                text = item.subText,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                color = MipCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Footer Action Row: [OPEN ->] + up to 3 item.actions + [DISMISS]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(btnShape)
                    .background(MipBlack)
                    .border(1.dp, MipCyan, btnShape)
                    .clickable(onClick = onOpenNotification)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[OPEN ->]",
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = MipCyan
                )
            }

            item.actions.take(3).forEach { act ->
                Box(
                    modifier = Modifier
                        .clip(btnShape)
                        .background(MipBlack)
                        .border(1.dp, MipWhite, btnShape)
                        .clickable {
                            FoldNotificationListenerService.fireActionIntent(context, act)
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[${act.title.uppercase(Locale.US).take(12)}]",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MipWhite,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (item.isClearable) {
                Box(
                    modifier = Modifier
                        .clip(btnShape)
                        .background(MipBlack)
                        .border(1.dp, MipRed, btnShape)
                        .clickable(onClick = onDismissNotification)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[DISMISS]",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MipRed
                    )
                }
            }
        }
    }
}

@Composable
private fun MipQuickSettingsControlPane(
    isWifiConnected: Boolean,
    lanIp: String?,
    tailscaleIp: String?,
    isBluetoothEnabled: Boolean,
    isTorchEnabled: Boolean,
    isAutoRotateEnabled: Boolean,
    ringerLabel: String,
    isWirelessAdbEnabled: Boolean,
    wirelessAdbPort: Int?,
    isLocationEnabled: Boolean,
    isAirplaneEnabled: Boolean,
    wallpaperBadge: String,
    brightnessPct: Int,
    volumePct: Int,
    onUpdateBrightness: (Int) -> Unit,
    onUpdateVolume: (Int) -> Unit,
    onRefreshToggles: () -> Unit,
    onToggleWirelessAdb: (() -> Unit)?,
    onToggleTailscale: (() -> Unit)?,
    onSplitTopTwoRecentApps: () -> Unit,
    onOpenRecents: () -> Unit,
    onCycleWallpaperPreset: (() -> Unit)?,
    onDismissOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val paneShape = CutCornerShape(4.dp)
    val tileShape = CutCornerShape(3.dp)

    Column(
        modifier = modifier
            .clip(paneShape)
            .background(MipBlack)
            .border(1.dp, MipGrid, paneShape)
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "[QUICK SETTINGS // HARDWARE]",
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = MipCyan
            )
            Text(
                text = "16X_MIP_CTRL",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MipMuted
            )
        }

        // Top Hero Row (2 Large Tiles: Wi-Fi & Bluetooth)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(tileShape)
                    .background(if (isWifiConnected) MipTeal else MipDarkNavy)
                    .border(
                        width = 1.dp,
                        color = if (isWifiConnected) MipCyan else MipGrid,
                        shape = tileShape
                    )
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        openWifiPanelOrSettings(context)
                    }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isWifiConnected) "[WI-FI // CONNECTED]" else "[WI-FI // OFFLINE]",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isWifiConnected) MipCyan else MipWhite,
                        maxLines = 1
                    )
                    Text(
                        text = lanIp ?: "TAP FOR WI-FI PANEL",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MipMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(tileShape)
                    .background(if (isBluetoothEnabled) MipTeal else MipDarkNavy)
                    .border(
                        width = 1.dp,
                        color = if (isBluetoothEnabled) MipCyan else MipGrid,
                        shape = tileShape
                    )
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismissOverlay()
                        SystemTweaker.openBluetoothSettings(context)
                    }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isBluetoothEnabled) "[BLUETOOTH // ACTIVE]" else "[BLUETOOTH // OFF]",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isBluetoothEnabled) MipCyan else MipWhite,
                        maxLines = 1
                    )
                    Text(
                        text = if (isBluetoothEnabled) "AUDIO / BLE READY" else "OPEN BT SETTINGS",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MipMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 4x3 MIP Hardware Toggles Grid (12 Toggles)
        val tailscaleActive = !tailscaleIp.isNullOrBlank()
        val ringerActive = ringerLabel != "SILENT"
        val toggles = listOf(
            MipHardwareToggleSpec(
                title = "TORCH",
                subtitle = if (isTorchEnabled) "ON" else "OFF",
                active = isTorchEnabled,
                onClick = {
                    SystemTweaker.toggleTorch(context, isTorchEnabled)
                    onRefreshToggles()
                }
            ),
            MipHardwareToggleSpec(
                title = "ROTATE",
                subtitle = if (isAutoRotateEnabled) "AUTO" else "LOCKED",
                active = isAutoRotateEnabled,
                onClick = {
                    SystemTweaker.setLockScreenRotation(context, !isAutoRotateEnabled)
                    onRefreshToggles()
                }
            ),
            MipHardwareToggleSpec(
                title = "RINGER",
                subtitle = ringerLabel,
                active = ringerActive,
                onClick = {
                    SystemTweaker.cycleRingerMode(context)
                    onRefreshToggles()
                }
            ),
            MipHardwareToggleSpec(
                title = "TAILSCALE",
                subtitle = tailscaleIp ?: "OFF",
                active = tailscaleActive,
                onClick = {
                    if (onToggleTailscale != null) {
                        onToggleTailscale()
                    } else {
                        SystemTweaker.toggleTailscaleVpn(context, tailscaleActive)
                    }
                    onRefreshToggles()
                }
            ),
            MipHardwareToggleSpec(
                title = "W-ADB",
                subtitle = if (isWirelessAdbEnabled) ":${wirelessAdbPort ?: 5555}" else "OFF",
                active = isWirelessAdbEnabled,
                onClick = {
                    if (onToggleWirelessAdb != null) {
                        onToggleWirelessAdb()
                    } else {
                        SystemTweaker.toggleWirelessAdb(context)
                    }
                    onRefreshToggles()
                }
            ),
            MipHardwareToggleSpec(
                title = "SPLIT TOP 2",
                subtitle = "PAIR ◫",
                active = true,
                onClick = onSplitTopTwoRecentApps
            ),
            MipHardwareToggleSpec(
                title = "RECENTS",
                subtitle = "DECK",
                active = true,
                onClick = onOpenRecents
            ),
            MipHardwareToggleSpec(
                title = "LOCATION",
                subtitle = if (isLocationEnabled) "ON" else "OFF",
                active = isLocationEnabled,
                onClick = {
                    SystemTweaker.openLocationSettings(context)
                    onRefreshToggles()
                }
            ),
            MipHardwareToggleSpec(
                title = "HOTSPOT",
                subtitle = "TETHER",
                active = false,
                onClick = {
                    onDismissOverlay()
                    SystemTweaker.openHotspotSettings(context)
                }
            ),
            MipHardwareToggleSpec(
                title = "AIRPLANE",
                subtitle = if (isAirplaneEnabled) "ON" else "OFF",
                active = isAirplaneEnabled,
                onClick = {
                    onDismissOverlay()
                    SystemTweaker.openAirplaneSettings(context)
                }
            ),
            MipHardwareToggleSpec(
                title = "LOCK SCR",
                subtitle = "NOW",
                active = false,
                onClick = {
                    onDismissOverlay()
                    FoldAccessibilityService.instance?.lockScreen() ?: SystemTweaker.lockScreenNow()
                }
            ),
            MipHardwareToggleSpec(
                title = "MIP THEME",
                subtitle = wallpaperBadge,
                active = true,
                onClick = {
                    onCycleWallpaperPreset?.invoke()
                }
            )
        )

        toggles.chunked(4).forEach { rowSpecs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowSpecs.forEach { spec ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(tileShape)
                            .background(if (spec.active) MipDarkNavy else MipBlack)
                            .border(
                                width = 1.dp,
                                color = if (spec.active) MipCyan else MipGrid,
                                shape = tileShape
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                spec.onClick()
                            }
                            .padding(horizontal = 4.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = spec.title,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (spec.active) MipCyan else MipWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = spec.subtitle,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (spec.active) MipGreen else MipMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 16-Segment Interactive MIP Brightness (`BRT`) & Volume (`VOL`) Bars
        MipSegmentedSliderRow(
            label = "BRT",
            percent = brightnessPct,
            accentColor = MipYellow,
            onPercentChange = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onUpdateBrightness(it)
            }
        )

        MipSegmentedSliderRow(
            label = "VOL",
            percent = volumePct,
            accentColor = MipCyan,
            onPercentChange = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onUpdateVolume(it)
            }
        )

        // MIP Media Transport Deck: `[◄◄ PREV]` `[►/❚❚ PLAY]` `[►► NEXT]`
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MipMediaTransportButton(
                label = "[◄◄ PREV]",
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    SystemTweaker.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
            )
            MipMediaTransportButton(
                label = "[►/❚❚ PLAY]",
                highlighted = true,
                modifier = Modifier.weight(1.2f),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    SystemTweaker.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
            )
            MipMediaTransportButton(
                label = "[►► NEXT]",
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    SystemTweaker.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                }
            )
        }
    }
}

private data class MipHardwareToggleSpec(
    val title: String,
    val subtitle: String,
    val active: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun MipSegmentedSliderRow(
    label: String,
    percent: Int,
    accentColor: Color,
    onPercentChange: (Int) -> Unit
) {
    val rowShape = CutCornerShape(3.dp)
    val btnShape = CutCornerShape(2.dp)
    val totalSegments = 16
    val activeSegments = ((percent.coerceIn(0, 100) / 100f) * totalSegments).roundToInt().coerceIn(0, totalSegments)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(MipDarkNavy)
            .border(1.dp, MipGrid, rowShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = accentColor,
            modifier = Modifier.width(28.dp)
        )

        Box(
            modifier = Modifier
                .clip(btnShape)
                .background(MipBlack)
                .border(1.dp, MipGrid, btnShape)
                .clickable { onPercentChange((percent - 7).coerceIn(0, 100)) }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[-]",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = MipWhite
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (seg in 1..totalSegments) {
                val filled = seg <= activeSegments
                val targetPct = ((seg / totalSegments.toFloat()) * 100f).roundToInt()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .background(if (filled) accentColor else MipBlack)
                        .border(0.5.dp, if (filled) MipWhite else MipGrid)
                        .clickable { onPercentChange(targetPct) }
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(btnShape)
                .background(MipBlack)
                .border(1.dp, MipGrid, btnShape)
                .clickable { onPercentChange((percent + 7).coerceIn(0, 100)) }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[+]",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = MipWhite
            )
        }

        Text(
            text = "%3d%%".format(percent.coerceIn(0, 100)),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = MipWhite,
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun MipMediaTransportButton(
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btnShape = CutCornerShape(3.dp)
    Box(
        modifier = modifier
            .clip(btnShape)
            .background(if (highlighted) MipTeal else MipDarkNavy)
            .border(1.dp, if (highlighted) MipCyan else MipGrid, btnShape)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = if (highlighted) MipCyan else MipWhite
        )
    }
}

@Composable
private fun Mip16xDpiMatrixBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cellPx = floor(1.dp.toPx()).coerceAtLeast(2f)
        val gapPx = 0.75f
        var x = cellPx - gapPx
        while (x < size.width) {
            drawRect(
                color = MipDarkNavy.copy(alpha = 0.32f),
                topLeft = Offset(x, 0f),
                size = Size(gapPx, size.height)
            )
            x += cellPx
        }
        var y = cellPx - gapPx
        while (y < size.height) {
            drawRect(
                color = MipDarkNavy.copy(alpha = 0.32f),
                topLeft = Offset(0f, y),
                size = Size(size.width, gapPx)
            )
            y += cellPx
        }
    }
}

private fun isBluetoothEnabledSafe(context: Context): Boolean {
    return try {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }
}

private fun openWifiPanelOrSettings(context: Context) {
    try {
        val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(panelIntent)
    } catch (_: Exception) {
        try {
            val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(wifiIntent)
        } catch (_: Exception) {
        }
    }
}
