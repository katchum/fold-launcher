package com.xsun.foldlauncher.ui.components

import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.hud.FloatingHudControlCard
import com.xsun.foldlauncher.hud.FloatingHudController
import com.xsun.foldlauncher.hud.VcHudAgentStatus
import com.xsun.foldlauncher.model.AppPair
import com.xsun.foldlauncher.posture.DevicePostureState
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.spatial.SpatialTiltState
import com.xsun.foldlauncher.spatial.rememberSpatialTiltState
import com.xsun.foldlauncher.spatial.spatialParallaxCard
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import com.xsun.foldlauncher.vc.VcBackgroundMonitor
import com.xsun.foldlauncher.vc.VcCommandCenterCard
import com.xsun.foldlauncher.vc.VcConnectionConfig
import com.xsun.foldlauncher.vc.VcLiveSummary
import com.xsun.foldlauncher.vc.VcPackages
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Built-in Z Fold 8 Multi-Window Power Presets (1-tap side-by-side split-screen).
 */
val DefaultZFold8AppPairPresets: List<AppPair> = listOf(
    AppPair(
        id = "preset_vc_termux",
        title = "VC + Termux",
        primaryPackage = VcPackages.PREVIEW,
        secondaryPackage = VcPackages.TERMUX_PACKAGE
    ),
    AppPair(
        id = "preset_vc_prod_compare",
        title = "VC Prod + Compare",
        primaryPackage = VcPackages.RELEASE,
        secondaryPackage = VcPackages.PREVIEW
    ),
    AppPair(
        id = "preset_vc_chrome",
        title = "VC + Chrome",
        primaryPackage = VcPackages.PREVIEW,
        secondaryPackage = VcPackages.CHROME_PACKAGE
    )
)

/**
 * Z Fold 8 Fold-Close Behavior Cycler Modes (`v0.4.0`):
 * Cycles through:
 * 1. `📱 Keep Cover Awake`
 * 2. `🎙️ Auto-Talk to VC`
 * 3. `⛺ OLED Standby`
 * 4. `🔒 Instant Lock`
 */
val DefaultZFold8FoldCloseModes: List<String> = listOf(
    "📱 Keep Cover Awake",
    "🎙️ Auto-Talk to VC",
    "⛺ OLED Standby",
    "🔒 Instant Lock"
)

/**
 * Z Fold 8 Unfold-Open Action Cycler Modes (`v0.5.0`):
 * Cycles through:
 * 1. `🖥️ Inner Cockpit`
 * 2. `⚡ Auto-Split VC + Termux`
 * 3. `🔀 Auto-Split Prod + Compare`
 * 4. `🌐 Auto-Split VC + Browser`
 */
val DefaultZFold8UnfoldOpenModes: List<String> = listOf(
    "🖥️ Inner Cockpit",
    "⚡ Auto-Split VC + Termux",
    "🔀 Auto-Split Prod + Compare",
    "🌐 Auto-Split VC + Browser"
)

private data class LiveNetworkMeshTelemetry(
    val tailscaleIp: String? = null,
    val lanIp: String? = null
)

private fun resolveNetworkTelemetry(context: Context): LiveNetworkMeshTelemetry {
    var tsIp: String? = null
    var localLanIp: String? = null

    // 1. Try SystemTweaker.readNetworkTelemetry(context) if available
    try {
        val method = SystemTweaker::class.java.methods.firstOrNull {
            it.name == "readNetworkTelemetry"
        }
        val snapshot = method?.invoke(SystemTweaker, context)
        if (snapshot != null) {
            val snapClass = snapshot.javaClass
            val tsProp = snapClass.methods.firstOrNull {
                it.name == "getTailscaleIp" || it.name == "getTailscaleIpv4"
            }?.invoke(snapshot) as? String
            val lanProp = snapClass.methods.firstOrNull {
                it.name == "getLanIp" || it.name == "getWifiLanIp" || it.name == "getLocalIp"
            }?.invoke(snapshot) as? String
            if (!tsProp.isNullOrBlank()) tsIp = tsProp
            if (!lanProp.isNullOrBlank()) localLanIp = lanProp
        }
    } catch (_: Exception) {
    }

    // 2. Scan active NetworkInterfaces for Tailscale (100.64.0.0/10 / tun0 / tailscale0) & LAN IPv4
    if (tsIp.isNullOrBlank() || localLanIp.isNullOrBlank()) {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val ifaceName = iface.name.lowercase(Locale.US)
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val octets = ip.split('.')
                        val firstOctet = octets.getOrNull(0)?.toIntOrNull() ?: 0
                        val secondOctet = octets.getOrNull(1)?.toIntOrNull() ?: 0
                        val isTailscaleRange = firstOctet == 100 && secondOctet in 64..127
                        if (isTailscaleRange || ifaceName.contains("tailscale") || ifaceName.startsWith("tun")) {
                            if (tsIp.isNullOrBlank()) tsIp = ip
                        } else if (firstOctet == 192 || firstOctet == 10 || (firstOctet == 172 && secondOctet in 16..31)) {
                            if (localLanIp.isNullOrBlank()) localLanIp = ip
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    return LiveNetworkMeshTelemetry(
        tailscaleIp = tsIp,
        lanIp = localLanIp
    )
}

@Composable
private fun rememberLiveNetworkMeshTelemetry(
    overrideTailscaleIp: String? = null,
    overrideLanIp: String? = null
): LiveNetworkMeshTelemetry {
    val context = LocalContext.current
    var telemetry by remember { mutableStateOf(resolveNetworkTelemetry(context)) }

    LaunchedEffect(context) {
        while (true) {
            telemetry = resolveNetworkTelemetry(context)
            delay(10_000L)
        }
    }

    return LiveNetworkMeshTelemetry(
        tailscaleIp = overrideTailscaleIp?.takeIf { it.isNotBlank() } ?: telemetry.tailscaleIp,
        lanIp = overrideLanIp?.takeIf { it.isNotBlank() } ?: telemetry.lanIp
    )
}

/**
 * Interactive Tailscale Mesh & LAN Telemetry Pill (`🔗 Tailscale: 100.101.43.86` or LAN IP)
 * with 1-tap copy to `ClipboardManager`.
 */
@Composable
fun TailscaleMeshTelemetryPill(
    tailscaleIp: String? = null,
    lanIp: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val liveNet = rememberLiveNetworkMeshTelemetry(
        overrideTailscaleIp = tailscaleIp,
        overrideLanIp = lanIp
    )
    var copiedFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback != null) {
            delay(1_800L)
            copiedFeedback = null
        }
    }

    val ts = liveNet.tailscaleIp
    val lan = liveNet.lanIp
    val primaryIp = ts ?: lan ?: "100.101.43.86"
    val isTailscale = ts != null || (lan == null)
    val displayLabel = when {
        copiedFeedback != null -> "✓ Copied $copiedFeedback"
        ts != null && lan != null -> "🔗 Tailscale: $ts · LAN $lan"
        isTailscale -> "🔗 Tailscale: $primaryIp"
        else -> "🌐 LAN: $primaryIp"
    }

    val pillShape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(pillShape)
            .background(
                if (copiedFeedback != null) {
                    FoldLauncherColors.EmeraldSuccess.copy(alpha = 0.22f)
                } else {
                    FoldLauncherColors.DeepVoid.copy(alpha = 0.76f)
                }
            )
            .border(
                1.dp,
                if (copiedFeedback != null) {
                    FoldLauncherColors.EmeraldSuccess
                } else {
                    FoldLauncherColors.ElectricCyan.copy(alpha = 0.65f)
                },
                pillShape
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                try {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("Z Fold 8 IP", primaryIp))
                    copiedFeedback = primaryIp
                } catch (_: Exception) {
                    copiedFeedback = primaryIp
                }
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (isTailscale) FoldLauncherColors.EmeraldSuccess
                    else FoldLauncherColors.ElectricCyan
                )
        )
        Text(
            text = displayLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (copiedFeedback != null) {
                FoldLauncherColors.EmeraldSuccess
            } else {
                FoldLauncherColors.CyanBright
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "📋",
            fontSize = 10.sp,
            color = FoldLauncherColors.TextSecondary
        )
    }
}

/**
 * Minimal, Ultra-Legible One UI 9.0 Connectivity Card (`v0.7.0`).
 * Renders a sleek One UI 9.0 frosted dark-glass container (`RoundedCornerShape(26.dp)`, `Color(0xD917181E)`,
 * subtle `1.dp` border `Color.White.copy(alpha = 0.10f)`) containing two clean, legible rows separated by a
 * subtle hairline divider (`HorizontalDivider(color = Color.White.copy(alpha = 0.07f))`):
 * 1. Row 1 — Tailscale (emerald `#34D399` dot, `"Tailscale"`, monospace IP with 1-tap copy `"Copied IP"`, Material3 `Switch`)
 * 2. Row 2 — Wireless Debugging (sky-blue `#38BDF8` dot, `"Wireless Debugging"`, monospace `<ip>:<port>` with 1-tap copy `"Copied ADB address"`, Material3 `Switch`)
 */
@Composable
fun OneUiConnectivityPillsRow(
    isWirelessAdbEnabled: Boolean = false,
    wirelessAdbPort: Int? = null,
    tailscaleIp: String? = null,
    lanIp: String? = null,
    isTailscaleConnected: Boolean = !tailscaleIp.isNullOrBlank(),
    onToggleWirelessAdb: () -> Unit = {},
    onOpenWirelessAdbSettings: () -> Unit = {},
    onToggleTailscale: () -> Unit = {},
    onOpenTailscaleApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var tailscaleCopied by remember { mutableStateOf(false) }
    var adbCopied by remember { mutableStateOf(false) }

    LaunchedEffect(tailscaleCopied) {
        if (tailscaleCopied) {
            delay(1_600L)
            tailscaleCopied = false
        }
    }
    LaunchedEffect(adbCopied) {
        if (adbCopied) {
            delay(1_600L)
            adbCopied = false
        }
    }

    val resolvedTailscaleIp = tailscaleIp?.takeIf { it.isNotBlank() }
    val resolvedLanIp = lanIp?.takeIf { it.isNotBlank() }

    val tailscaleAddressText = resolvedTailscaleIp ?: if (isTailscaleConnected) "Connected" else "Off"
    val tailscaleDisplaySubtitle = if (tailscaleCopied) "Copied IP" else tailscaleAddressText

    val rawAdbEndpoint = "${resolvedTailscaleIp ?: resolvedLanIp ?: "On"}${
        if (wirelessAdbPort != null && wirelessAdbPort > 0) ":$wirelessAdbPort" else ""
    }"
    val adbAddressText = if (isWirelessAdbEnabled) rawAdbEndpoint else "Off"
    val adbDisplaySubtitle = if (adbCopied) "Copied ADB" else adbAddressText

    val pillShape = RoundedCornerShape(18.dp)
    val pillBackground = Color(0xD917181E)
    val pillBorder = Color.White.copy(alpha = 0.10f)
    val mutedDotColor = Color(0xFF6B7280)
    val tailscaleConnectedDot = Color(0xFF34D399)
    val wirelessAdbEnabledDot = Color(0xFF38BDF8)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Compact Pill — Tailscale
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(pillShape)
                .background(pillBackground)
                .border(1.dp, pillBorder, pillShape)
                .padding(start = 11.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenTailscaleApp()
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isTailscaleConnected) tailscaleConnectedDot else mutedDotColor)
                    )
                    Text(
                        text = "Tailscale",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = tailscaleDisplaySubtitle,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        tailscaleCopied -> tailscaleConnectedDot
                        isTailscaleConnected -> Color(0xFF93C5FD)
                        else -> Color(0xFF9CA3AF)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val ipToCopy = resolvedTailscaleIp ?: resolvedLanIp
                            if (!ipToCopy.isNullOrBlank()) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                try {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Tailscale IP", ipToCopy))
                                } catch (_: Exception) {
                                }
                                tailscaleCopied = true
                            } else {
                                onOpenTailscaleApp()
                            }
                        }
                )
            }

            Switch(
                checked = isTailscaleConnected,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleTailscale()
                },
                modifier = Modifier.scale(0.76f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = tailscaleConnectedDot,
                    uncheckedThumbColor = Color(0xFFD1D5DB),
                    uncheckedTrackColor = Color(0xFF2A2D37)
                )
            )
        }

        // Right Compact Pill — Wireless ADB
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(pillShape)
                .background(pillBackground)
                .border(1.dp, pillBorder, pillShape)
                .padding(start = 11.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenWirelessAdbSettings()
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isWirelessAdbEnabled) wirelessAdbEnabledDot else mutedDotColor)
                    )
                    Text(
                        text = "Wireless ADB",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = adbDisplaySubtitle,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        adbCopied -> tailscaleConnectedDot
                        isWirelessAdbEnabled -> Color(0xFF7DD3FC)
                        else -> Color(0xFF9CA3AF)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val host = resolvedTailscaleIp ?: resolvedLanIp
                            if (host != null || (wirelessAdbPort != null && wirelessAdbPort > 0)) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val copyTarget = "${host ?: "127.0.0.1"}${
                                    if (wirelessAdbPort != null && wirelessAdbPort > 0) ":$wirelessAdbPort" else ""
                                }"
                                try {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Wireless ADB", copyTarget))
                                } catch (_: Exception) {
                                }
                                adbCopied = true
                            } else {
                                onOpenWirelessAdbSettings()
                            }
                        }
                )
            }

            Switch(
                checked = isWirelessAdbEnabled,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleWirelessAdb()
                },
                modifier = Modifier.scale(0.76f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = wirelessAdbEnabledDot,
                    uncheckedThumbColor = Color(0xFFD1D5DB),
                    uncheckedTrackColor = Color(0xFF2A2D37)
                )
            )
        }
    }
}

private data class HardwareSystemTelemetry(
    val batteryTempCelsius: Float = 31.5f,
    val availableRamGb: Float = 9.4f,
    val totalRamGb: Float = 16.0f,
    val isKeyguardLocked: Boolean = false
)

@Composable
private fun rememberHardwareTorchState(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var torchOn by remember { mutableStateOf(false) }
    var torchCameraId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            return@DisposableEffect onDispose {}
        }

        val resolvedId = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
        torchCameraId = resolvedId

        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == resolvedId || resolvedId == null) {
                    torchOn = enabled
                }
            }
        }

        try {
            cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
        }

        onDispose {
            try {
                cameraManager.unregisterTorchCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    val toggleTorch: () -> Unit = {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val camId = torchCameraId
        if (cameraManager != null && camId != null) {
            try {
                val target = !torchOn
                cameraManager.setTorchMode(camId, target)
                torchOn = target
            } catch (_: Exception) {
                torchOn = !torchOn
            }
        } else {
            torchOn = !torchOn
        }
    }

    return torchOn to toggleTorch
}

@Composable
private fun rememberHardwareSystemTelemetry(
    defaultTempCelsius: Float = 31.5f,
    defaultAvailRamGb: Float = 9.4f,
    defaultTotalRamGb: Float = 16.0f
): HardwareSystemTelemetry {
    val context = LocalContext.current
    var tempC by remember { mutableFloatStateOf(defaultTempCelsius) }
    var availGb by remember { mutableFloatStateOf(defaultAvailRamGb) }
    var totalGb by remember { mutableFloatStateOf(defaultTotalRamGb) }
    var keyguardLocked by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        while (true) {
            try {
                val batteryIntent = context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                val rawTempDeciC = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
                if (rawTempDeciC in 50..800) {
                    tempC = rawTempDeciC / 10f
                }
            } catch (_: Exception) {
            }

            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (am != null) {
                    val memInfo = ActivityManager.MemoryInfo()
                    am.getMemoryInfo(memInfo)
                    val bytesPerGb = 1024f * 1024f * 1024f
                    if (memInfo.totalMem > 0L) {
                        availGb = (memInfo.availMem / bytesPerGb).coerceAtLeast(0.5f)
                        totalGb = (memInfo.totalMem / bytesPerGb).coerceAtLeast(4.0f)
                    }
                }
            } catch (_: Exception) {
            }

            try {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                keyguardLocked = km?.isKeyguardLocked == true
            } catch (_: Exception) {
            }

            delay(8_000L)
        }
    }

    return HardwareSystemTelemetry(
        batteryTempCelsius = tempC,
        availableRamGb = availGb,
        totalRamGb = totalGb,
        isKeyguardLocked = keyguardLocked
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NativeDashboardCards(
    postureState: DevicePostureState,
    batteryPct: Int,
    isCharging: Boolean,
    isAccessibilityActive: Boolean,
    isWriteSecureSettingsGranted: Boolean,
    lockScreenRotationEnabled: Boolean,
    onToggleLockRotation: () -> Unit,
    onEnforceGboardDeX: () -> Unit,
    onLockScreenNow: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onOpenDefaultLauncherPrompt: () -> Unit,
    isDefaultLauncher: Boolean,
    quickNoteText: String,
    onQuickNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    spatialTiltState: SpatialTiltState? = null,
    vcSummaryOverride: VcLiveSummary? = null,
    vcConfigOverride: VcConnectionConfig? = null,
    onOpenSpotlight: (() -> Unit)? = null,
    onToggleRadialArc: (() -> Unit)? = null,
    hingeAngleDegrees: Float? = null,
    customAppPairs: List<AppPair> = emptyList(),
    onLaunchAppPair: ((AppPair) -> Unit)? = null,
    isKeyguardLocked: Boolean? = null,
    isTorchOn: Boolean? = null,
    batteryTempCelsius: Float? = null,
    availableRamGb: Float? = null,
    totalRamGb: Float? = null,
    onToggleTorch: (() -> Unit)? = null,
    onUnlockAndSplitVcTermux: (() -> Unit)? = null,
    onQuickApproveVc: (() -> Unit)? = null,
    onToggleDualScreenCompanion: (() -> Unit)? = null,
    hingeHapticsEnabled: Boolean = true,
    foldCloseModeLabel: String = "📱 Keep Cover Awake",
    onToggleHingeHaptics: () -> Unit = {},
    onCycleFoldCloseMode: () -> Unit = {},
    unfoldModeLabel: String = "🖥️ Inner Cockpit",
    onCycleUnfoldMode: () -> Unit = {},
    tailscaleIp: String? = null,
    lanIp: String? = null,
    isWirelessAdbEnabled: Boolean = false,
    wirelessAdbPort: Int? = null,
    isTailscaleConnected: Boolean = !tailscaleIp.isNullOrBlank(),
    onToggleWirelessAdb: () -> Unit = {},
    onOpenWirelessAdbSettings: () -> Unit = {},
    onToggleTailscale: () -> Unit = {},
    onOpenTailscaleApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val vcMonitor = remember(context) { VcBackgroundMonitor.getInstance(context) }
    val monitorSummary by vcMonitor.liveSummary.collectAsState()
    val monitorConfig by vcMonitor.config.collectAsState()
    val lastSendFeedback by vcMonitor.lastSendFeedback.collectAsState()

    var localHingeHaptics by remember(hingeHapticsEnabled) { mutableStateOf(hingeHapticsEnabled) }
    var localFoldCloseIndex by remember(foldCloseModeLabel) {
        val idx = DefaultZFold8FoldCloseModes.indexOfFirst {
            it.equals(foldCloseModeLabel, ignoreCase = true)
        }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }
    val effectiveFoldCloseLabel = DefaultZFold8FoldCloseModes.getOrElse(localFoldCloseIndex) {
        foldCloseModeLabel
    }

    var localUnfoldIndex by remember(unfoldModeLabel) {
        val idx = DefaultZFold8UnfoldOpenModes.indexOfFirst {
            it.equals(unfoldModeLabel, ignoreCase = true)
        }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }
    val effectiveUnfoldLabel = DefaultZFold8UnfoldOpenModes.getOrElse(localUnfoldIndex) {
        unfoldModeLabel
    }

    val effectiveVcSummary = vcSummaryOverride ?: monitorSummary
    val effectiveVcConfig = vcConfigOverride ?: monitorConfig

    val sensorHingeAngle = rememberHardwareHingeAngleDegrees()
    val effectiveHingeAngle = hingeAngleDegrees ?: sensorHingeAngle ?: when {
        postureState.isTentMode -> 48f
        postureState.isFlexTabletop -> 118f
        postureState.isBookPosture -> 135f
        postureState.isUnfoldedInner -> 180f
        else -> 0f
    }

    val (hwTorchOn, hwToggleTorch) = rememberHardwareTorchState()
    val hwTelemetry = rememberHardwareSystemTelemetry()

    val effectiveTorchOn = isTorchOn ?: hwTorchOn
    val effectiveToggleTorch: () -> Unit = {
        hwToggleTorch()
        onToggleTorch?.invoke()
    }
    val effectiveTempC = batteryTempCelsius ?: hwTelemetry.batteryTempCelsius
    val effectiveAvailRamGb = availableRamGb ?: hwTelemetry.availableRamGb
    val effectiveTotalRamGb = totalRamGb ?: hwTelemetry.totalRamGb
    val effectiveKeyguardLocked = isKeyguardLocked ?: hwTelemetry.isKeyguardLocked

    val resolvedPostureLabel = when {
        postureState.isTentMode -> "TENT"
        postureState.isFlexTabletop -> "FLEX"
        postureState.isBookPosture -> "BOOK"
        postureState.isUnfoldedInner -> "UNFOLDED"
        else -> "COVER"
    }

    val hingeFactor = when {
        postureState.isFlexTabletop -> 0.55f
        postureState.isBookPosture -> 0.35f
        else -> 0f
    }
    val tilt = spatialTiltState ?: rememberSpatialTiltState(
        enabled = true,
        hingeAngleFactor = hingeFactor
    )

    var showFloatingHudConfigurator by remember { mutableStateOf(false) }

    LaunchedEffect(
        effectiveVcSummary.approvalCount,
        effectiveVcSummary.failedCount,
        effectiveVcSummary.workingCount
    ) {
        val mappedStatus = when {
            effectiveVcSummary.approvalCount > 0 -> VcHudAgentStatus.APPROVAL_NEEDED
            effectiveVcSummary.failedCount > 0 -> VcHudAgentStatus.ERROR
            effectiveVcSummary.workingCount > 0 -> VcHudAgentStatus.WORKING
            else -> VcHudAgentStatus.IDLE_READY
        }
        FloatingHudController.setVcAgentStatus(context, mappedStatus)
    }

    val cardShape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Default Home Role Banner (only when not currently default launcher)
        if (!isDefaultLauncher) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .spatialParallaxCard(tilt = tilt, depthLayer = 0.8f)
                    .clip(cardShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                FoldLauncherColors.IndigoAccent.copy(alpha = 0.35f),
                                FoldLauncherColors.GlassSurfaceElevated
                            )
                        )
                    )
                    .border(1.dp, FoldLauncherColors.ElectricCyan, cardShape)
                    .clickable(onClick = onOpenDefaultLauncherPrompt)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SET AS DEFAULT FOLD8 HOME LAUNCHER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.CyanBright
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Replace One UI Home for 120Hz zero-bloat home gesture & inner taskbar",
                        fontSize = 12.sp,
                        color = FoldLauncherColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(FoldLauncherColors.ElectricCyan)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Activate",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF02131D)
                    )
                }
            }
        }

        // 2. Dedicated Lock-Screen Ambient Cockpit Card (ShowWhenLocked + Keyguard Overlay)
        LockScreenCockpitCard(
            isKeyguardLocked = effectiveKeyguardLocked,
            hingeAngleDegrees = effectiveHingeAngle,
            postureLabel = resolvedPostureLabel,
            activeVcCount = effectiveVcSummary.workingCount + effectiveVcSummary.totalSessions,
            pendingApprovals = effectiveVcSummary.approvalCount,
            isTorchOn = effectiveTorchOn,
            onUnlockAndSplitVcTermux = {
                if (onUnlockAndSplitVcTermux != null) {
                    onUnlockAndSplitVcTermux()
                } else {
                    val activity = context as? Activity
                    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        activity != null &&
                        km?.isKeyguardLocked == true
                    ) {
                        km.requestDismissKeyguard(
                            activity,
                            object : KeyguardManager.KeyguardDismissCallback() {
                                override fun onDismissSucceeded() {
                                    vcMonitor.launchVcSplitScreen()
                                }
                            }
                        )
                    } else {
                        vcMonitor.launchVcSplitScreen()
                    }
                }
            },
            onToggleTorch = effectiveToggleTorch,
            onQuickApproveVc = {
                if (onQuickApproveVc != null) {
                    onQuickApproveVc()
                } else {
                    val targetSession = effectiveVcSummary.attentionItems.firstOrNull()?.sessionId
                        ?: effectiveVcSummary.sessions.firstOrNull()?.sessionId
                    if (!targetSession.isNullOrBlank()) {
                        vcMonitor.sendQuickMessage(
                            sessionId = targetSession,
                            text = "Approved — proceed with execution.",
                            steerIfWorking = true
                        )
                        vcMonitor.markAttentionRead(null, true)
                    } else {
                        vcMonitor.launchVc(null)
                    }
                }
            },
            onToggleDualScreenCompanion = {
                if (onToggleDualScreenCompanion != null) {
                    onToggleDualScreenCompanion()
                } else {
                    val currentStayAwake = SystemTweaker.isFoldCloseStayAwakeEnabled(context)
                    SystemTweaker.setFoldCloseStayAwake(context, !currentStayAwake)
                    SystemTweaker.applyLockScreenPowerPreset(context)
                }
            },
            modifier = Modifier.spatialParallaxCard(tilt = tilt, depthLayer = 1.2f)
        )

        // 3. Live 120Hz VC Command Center & Background Bridge Card
        VcCommandCenterCard(
            summary = effectiveVcSummary,
            config = effectiveVcConfig,
            lastSendFeedback = lastSendFeedback,
            onOpenVc = { sessionId -> vcMonitor.launchVc(sessionId) },
            onSplitScreenVc = { vcMonitor.launchVcSplitScreen() },
            onRefreshVc = { vcMonitor.refreshNow() },
            onSendQuickMessage = { sessionId, text, steerIfWorking ->
                vcMonitor.sendQuickMessage(sessionId, text, steerIfWorking)
            },
            onMarkAttentionRead = { eventId, markAll ->
                vcMonitor.markAttentionRead(eventId, markAll)
            },
            onUpdateConfig = { serverUrl, authToken, monitorEnabled, notifyBackground ->
                vcMonitor.updateConfig(serverUrl, authToken, monitorEnabled, notifyBackground)
            },
            modifier = Modifier.spatialParallaxCard(tilt = tilt, depthLayer = 1.15f)
        )

        // 4. Z Fold 8 System Dashboard Card (120Hz VRR & Hinge Angle Telemetry + Thermal/RAM + Torch + Companion Pills)
        SystemDashboardCard(
            postureState = postureState,
            batteryPct = batteryPct,
            isCharging = isCharging,
            isAccessibilityActive = isAccessibilityActive,
            isWriteSecureSettingsGranted = isWriteSecureSettingsGranted,
            lockScreenRotationEnabled = lockScreenRotationEnabled,
            onToggleLockRotation = onToggleLockRotation,
            onEnforceGboardDeX = onEnforceGboardDeX,
            onLockScreenNow = onLockScreenNow,
            onOpenWallpaperPicker = onOpenWallpaperPicker,
            hingeAngleDegrees = effectiveHingeAngle,
            showFloatingHudConfigurator = showFloatingHudConfigurator,
            onToggleFloatingHudConfigurator = {
                showFloatingHudConfigurator = !showFloatingHudConfigurator
            },
            onToggleRadialArc = onToggleRadialArc,
            isTorchOn = effectiveTorchOn,
            batteryTempCelsius = effectiveTempC,
            availableRamGb = effectiveAvailRamGb,
            totalRamGb = effectiveTotalRamGb,
            onToggleTorch = effectiveToggleTorch,
            hingeHapticsEnabled = localHingeHaptics,
            foldCloseModeLabel = effectiveFoldCloseLabel,
            onToggleHingeHaptics = {
                localHingeHaptics = !localHingeHaptics
                if (localHingeHaptics) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onToggleHingeHaptics()
            },
            onCycleFoldCloseMode = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                localFoldCloseIndex = (localFoldCloseIndex + 1) % DefaultZFold8FoldCloseModes.size
                val nextMode = DefaultZFold8FoldCloseModes[localFoldCloseIndex]
                if (nextMode.contains("Keep Cover Awake", ignoreCase = true) ||
                    nextMode.contains("Auto-Talk", ignoreCase = true) ||
                    nextMode.contains("Standby", ignoreCase = true)
                ) {
                    SystemTweaker.setFoldCloseStayAwake(context, true)
                } else if (nextMode.contains("Instant Lock", ignoreCase = true)) {
                    SystemTweaker.setFoldCloseStayAwake(context, false)
                }
                onCycleFoldCloseMode()
            },
            unfoldModeLabel = effectiveUnfoldLabel,
            onCycleUnfoldMode = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                localUnfoldIndex = (localUnfoldIndex + 1) % DefaultZFold8UnfoldOpenModes.size
                onCycleUnfoldMode()
            },
            tailscaleIp = tailscaleIp,
            lanIp = lanIp,
            isWirelessAdbEnabled = isWirelessAdbEnabled,
            wirelessAdbPort = wirelessAdbPort,
            isTailscaleConnected = isTailscaleConnected,
            onToggleWirelessAdb = onToggleWirelessAdb,
            onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
            onToggleTailscale = onToggleTailscale,
            onOpenTailscaleApp = onOpenTailscaleApp,
            modifier = Modifier.spatialParallaxCard(tilt = tilt, depthLayer = 1.0f)
        )

        // Expandable System-Wide Floating Micro-HUD Pill Studio
        if (showFloatingHudConfigurator) {
            FloatingHudControlCard(
                isAccessibilityServiceActive = isAccessibilityActive,
                onOpenAccessibilitySettings = { SystemTweaker.openAccessibilitySettings(context) },
                onTestAnywhereSpotlight = onOpenSpotlight,
                modifier = Modifier.spatialParallaxCard(tilt = tilt, depthLayer = 1.1f)
            )
        }

        // 5. Z Fold 8 Multi-App Pair Presets Card (1-Tap Split-Screen Power Pairs)
        AppPairsQuickCard(
            customAppPairs = customAppPairs,
            onLaunchAppPair = onLaunchAppPair,
            modifier = Modifier.spatialParallaxCard(tilt = tilt, depthLayer = 0.95f)
        )

        // 6. Inline Interactive 120Hz Quick Scratchpad Card (Zero-IPC BasicTextField right on Home Screen)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .spatialParallaxCard(tilt = tilt, depthLayer = 0.9f)
                .clip(cardShape)
                .background(FoldLauncherColors.GlassSurface)
                .border(1.dp, FoldLauncherColors.GlassBorder, cardShape)
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "QUICK SCRATCHPAD · INSTANT NOTE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.CyanBright
                )
                if (quickNoteText.isNotEmpty()) {
                    Text(
                        text = "Clear",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FoldLauncherColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onQuickNoteChange("") }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp, max = 88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.65f))
                    .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopStart
            ) {
                if (quickNoteText.isEmpty()) {
                    Text(
                        text = "Tap to jot a quick note, ADB snippet, or reminder right on the home screen…",
                        fontSize = 13.sp,
                        color = FoldLauncherColors.TextMuted
                    )
                }
                BasicTextField(
                    value = quickNoteText,
                    onValueChange = onQuickNoteChange,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = FoldLauncherColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(FoldLauncherColors.ElectricCyan),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Dedicated Lock-Screen Ambient Cockpit Card (`setShowWhenLocked(true)` + Keyguard overlay).
 * Features:
 * - Glowing cyber-emerald/amber header (`🔒 LOCK-SCREEN COCKPIT • OVERLAYING ONE UI KEYGUARD` when locked,
 *   or `🛡️ LOCK-SCREEN COCKPIT READY` when unlocked)
 * - Live hinge angle (`180°`) & posture badge
 * - 1-tap `🔦 Torch`, 1-tap `⚡ Unlock & Split VC+Termux`, 1-tap `✅ Approve VC`, and 1-tap `🖥️ Cover Companion`
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockScreenCockpitCard(
    isKeyguardLocked: Boolean = false,
    hingeAngleDegrees: Float = 180f,
    postureLabel: String = "UNFOLDED",
    activeVcCount: Int = 0,
    pendingApprovals: Int = 0,
    isTorchOn: Boolean = false,
    onUnlockAndSplitVcTermux: () -> Unit = {},
    onToggleTorch: () -> Unit = {},
    onQuickApproveVc: () -> Unit = {},
    onToggleDualScreenCompanion: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var coverCompanionEnabled by remember {
        mutableStateOf(SystemTweaker.isFoldCloseStayAwakeEnabled(context))
    }

    val roundedAngle = hingeAngleDegrees.coerceIn(0f, 180f).roundToInt()
    val cardShape = RoundedCornerShape(22.dp)
    val accentColor = if (isKeyguardLocked) {
        FoldLauncherColors.AmberWarning
    } else {
        FoldLauncherColors.EmeraldSuccess
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isKeyguardLocked) 0.18f else 0.12f),
                        FoldLauncherColors.GlassSurfaceElevated,
                        FoldLauncherColors.GlassSurface
                    )
                )
            )
            .border(
                width = 1.dp,
                color = if (isKeyguardLocked) {
                    FoldLauncherColors.AmberWarning.copy(alpha = 0.78f)
                } else {
                    FoldLauncherColors.EmeraldSuccess.copy(alpha = 0.55f)
                },
                shape = cardShape
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Glowing Cyber-Emerald / Amber Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = if (isKeyguardLocked) {
                        "🔒 LOCK-SCREEN COCKPIT • OVERLAYING ONE UI KEYGUARD"
                    } else {
                        "🛡️ LOCK-SCREEN COCKPIT READY"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Live Hinge Angle + Posture Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.72f))
                    .border(1.dp, FoldLauncherColors.GlassBorderStrong, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "${roundedAngle}°",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.CyanBright
                )
                Text(
                    text = postureLabel.uppercase(Locale.getDefault()),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.TextSecondary
                )
            }
        }

        // Subtitle telemetry row: VC active sessions, pending approvals, ShowWhenLocked status
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ShowWhenLocked + Keyguard Bypass · VC Active: $activeVcCount",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = FoldLauncherColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (pendingApprovals > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(FoldLauncherColors.AmberWarning.copy(alpha = 0.22f))
                        .border(1.dp, FoldLauncherColors.AmberWarning, RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$pendingApprovals Approval${if (pendingApprovals == 1) "" else "s"} Pending",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.AmberWarning
                    )
                }
            }
        }

        // 4 One-Tap Lock-Screen Cockpit Action Pills
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CockpitActionPill(
                title = if (isTorchOn) "🔦 Torch: ON" else "🔦 Torch",
                highlighted = isTorchOn,
                accentColor = FoldLauncherColors.AmberWarning,
                onClick = onToggleTorch
            )
            CockpitActionPill(
                title = "⚡ Unlock & Split VC+Termux",
                highlighted = true,
                accentColor = FoldLauncherColors.ElectricCyan,
                onClick = onUnlockAndSplitVcTermux
            )
            CockpitActionPill(
                title = if (pendingApprovals > 0) {
                    "✅ Approve VC ($pendingApprovals)"
                } else {
                    "✅ Approve VC"
                },
                highlighted = pendingApprovals > 0,
                accentColor = FoldLauncherColors.EmeraldSuccess,
                onClick = onQuickApproveVc
            )
            CockpitActionPill(
                title = if (coverCompanionEnabled) {
                    "🖥️ Cover Companion: ON"
                } else {
                    "🖥️ Cover Companion"
                },
                highlighted = coverCompanionEnabled,
                accentColor = FoldLauncherColors.IndigoAccent,
                onClick = {
                    coverCompanionEnabled = !coverCompanionEnabled
                    onToggleDualScreenCompanion()
                }
            )
        }
    }
}

@Composable
private fun CockpitActionPill(
    title: String,
    highlighted: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(pillShape)
            .background(
                if (highlighted) accentColor.copy(alpha = 0.22f)
                else FoldLauncherColors.GlassSurfaceHighlight
            )
            .border(
                width = 1.dp,
                color = if (highlighted) accentColor else FoldLauncherColors.GlassBorder,
                shape = pillShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) FoldLauncherColors.TextPrimary else FoldLauncherColors.TextSecondary
        )
    }
}

/**
 * Interactive Z Fold 8 Hinge Mechanics Strip (`v0.5.0`):
 * - 1-tap `⚙️ Hinge Detents: ON/OFF` chip (toggles mechanical watch-crown 15° hinge haptics)
 * - 1-tap `📐 On Fold Close: 📱 Keep Cover Awake` cycler pill
 *   (`📱 Keep Cover Awake` -> `🎙️ Auto-Talk to VC` -> `⛺ OLED Standby` -> `🔒 Instant Lock`)
 * - 1-tap `📖 On Unfold: 🖥️ Inner Cockpit` cycler pill
 *   (`🖥️ Inner Cockpit` -> `⚡ Auto-Split VC + Termux` -> `🔀 Auto-Split Prod + Compare` -> `🌐 Auto-Split VC + Browser`)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HingeMechanicsStrip(
    hingeHapticsEnabled: Boolean = true,
    foldCloseModeLabel: String = "📱 Keep Cover Awake",
    onToggleHingeHaptics: () -> Unit = {},
    onCycleFoldCloseMode: () -> Unit = {},
    unfoldModeLabel: String = "🖥️ Inner Cockpit",
    onCycleUnfoldMode: () -> Unit = {},
    tailscaleIp: String? = null,
    lanIp: String? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val stripShape = RoundedCornerShape(14.dp)
    val pillShape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(stripShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        FoldLauncherColors.IndigoAccent.copy(alpha = 0.18f),
                        FoldLauncherColors.DeepVoid.copy(alpha = 0.72f)
                    )
                )
            )
            .border(1.dp, FoldLauncherColors.GlassBorderStrong, stripShape)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "HINGE MECHANICS · 15° DETENTS · FOLD & UNFOLD",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.CyanBright
            )
            Text(
                text = "TAP TO CYCLE ↻",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.TextSecondary
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. 1-Tap Mechanical Watch-Crown 15° Hinge Detents Chip
            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .background(
                        if (hingeHapticsEnabled) FoldLauncherColors.CyanGlow
                        else FoldLauncherColors.GlassSurfaceHighlight
                    )
                    .border(
                        1.dp,
                        if (hingeHapticsEnabled) FoldLauncherColors.ElectricCyan
                        else FoldLauncherColors.GlassBorder,
                        pillShape
                    )
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleHingeHaptics()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (hingeHapticsEnabled) FoldLauncherColors.EmeraldSuccess
                            else FoldLauncherColors.TextMuted
                        )
                )
                Text(
                    text = if (hingeHapticsEnabled) "⚙️ Hinge Detents: ON" else "⚙️ Hinge Detents: OFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hingeHapticsEnabled) FoldLauncherColors.CyanBright else FoldLauncherColors.TextSecondary
                )
            }

            // 2. 1-Tap Fold-Close Behavior Cycler Pill
            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .background(FoldLauncherColors.IndigoAccent.copy(alpha = 0.24f))
                    .border(1.dp, FoldLauncherColors.IndigoAccent, pillShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCycleFoldCloseMode()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "📐 On Fold Close: $foldCloseModeLabel",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "↻",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.ElectricCyan
                )
            }

            // 3. 1-Tap Unfold-Open Action Cycler Pill (v0.5.0)
            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .background(FoldLauncherColors.ElectricCyan.copy(alpha = 0.16f))
                    .border(1.dp, FoldLauncherColors.ElectricCyan, pillShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCycleUnfoldMode()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "📖 On Unfold: $unfoldModeLabel",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.CyanBright,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "↻",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.EmeraldSuccess
                )
            }
        }
    }
}

/**
 * Enhanced `SystemQuickTileWidget` for v0.5.0:
 * Displays a live Thermal / RAM pill (`31.5°C • 9.4 / 16 GB RAM`), a live Tailscale Mesh & LAN Telemetry Pill
 * (`🔗 Tailscale: 100.101.43.86` with 1-tap Clipboard copy), a 1-tap `🔦 Torch` quick-tile button,
 * and the interactive Z Fold 8 Hinge Mechanics Strip (`⚙️ Hinge Detents: ON/OFF` + `📐 On Fold Close` + `📖 On Unfold`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemQuickTileWidget(
    modifier: Modifier = Modifier,
    isTorchOn: Boolean = false,
    batteryTempCelsius: Float = 31.5f,
    availableRamGb: Float = 9.4f,
    totalRamGb: Float = 16.0f,
    onToggleTorch: () -> Unit = {},
    hingeHapticsEnabled: Boolean = true,
    foldCloseModeLabel: String = "📱 Keep Cover Awake",
    onToggleHingeHaptics: () -> Unit = {},
    onCycleFoldCloseMode: () -> Unit = {},
    unfoldModeLabel: String = "🖥️ Inner Cockpit",
    onCycleUnfoldMode: () -> Unit = {},
    tailscaleIp: String? = null,
    lanIp: String? = null,
    hingeAngleDegrees: Float? = null,
    isAccessibilityActive: Boolean = false,
    isWriteSecureSettingsGranted: Boolean = false,
    isDefaultLauncher: Boolean = true,
    lockScreenRotationEnabled: Boolean = false,
    onToggleLockRotation: (() -> Unit)? = null,
    onEnforceGboardDeX: (() -> Unit)? = null,
    onLockScreenNow: (() -> Unit)? = null,
    onOpenWallpaperPicker: (() -> Unit)? = null,
    onOpenDefaultLauncherPrompt: (() -> Unit)? = null,
    isWirelessAdbEnabled: Boolean = false,
    wirelessAdbPort: Int? = null,
    isTailscaleConnected: Boolean = !tailscaleIp.isNullOrBlank(),
    onToggleWirelessAdb: () -> Unit = {},
    onOpenWirelessAdbSettings: () -> Unit = {},
    onToggleTailscale: () -> Unit = {},
    onOpenTailscaleApp: () -> Unit = {}
) {
    val tempFormatted = remember(batteryTempCelsius) {
        String.format(Locale.US, "%.1f°C", batteryTempCelsius)
    }
    val availRamFormatted = remember(availableRamGb) {
        String.format(Locale.US, "%.1f", availableRamGb)
    }
    val totalRamFormatted = remember(totalRamGb) {
        if (totalRamGb % 1f == 0f) {
            totalRamGb.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", totalRamGb)
        }
    }
    val thermalRamLabel = "$tempFormatted • $availRamFormatted / $totalRamFormatted GB RAM"
    val isWarm = batteryTempCelsius >= 38.0f

    val cardShape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(FoldLauncherColors.GlassSurface)
            .border(1.dp, FoldLauncherColors.GlassBorder, cardShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SYSTEM TELEMETRY · QUICK TILES",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.ElectricCyan
            )

            // Live Thermal / RAM Pill ("31.5°C • 9.4 / 16 GB RAM")
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isWarm) FoldLauncherColors.AmberWarning.copy(alpha = 0.18f)
                        else FoldLauncherColors.DeepVoid.copy(alpha = 0.72f)
                    )
                    .border(
                        1.dp,
                        if (isWarm) FoldLauncherColors.AmberWarning else FoldLauncherColors.GlassBorderStrong,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isWarm) FoldLauncherColors.AmberWarning
                            else FoldLauncherColors.EmeraldSuccess
                        )
                )
                Text(
                    text = thermalRamLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.TextPrimary
                )
            }
        }

        // Samsung One UI 9.0 Wireless Debugging & Tailscale Quick-Toggle Pills Row (v0.6.0)
        OneUiConnectivityPillsRow(
            isWirelessAdbEnabled = isWirelessAdbEnabled,
            wirelessAdbPort = wirelessAdbPort,
            tailscaleIp = tailscaleIp,
            lanIp = lanIp,
            isTailscaleConnected = isTailscaleConnected,
            onToggleWirelessAdb = onToggleWirelessAdb,
            onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
            onToggleTailscale = onToggleTailscale,
            onOpenTailscaleApp = onOpenTailscaleApp
        )

        // Live Tailscale Mesh & LAN Telemetry Pill with 1-tap Clipboard copy
        TailscaleMeshTelemetryPill(
            tailscaleIp = tailscaleIp,
            lanIp = lanIp
        )

        // Interactive Z Fold 8 Hinge Mechanics Strip (Detents + Fold-Close + Unfold-Open Cyclers)
        HingeMechanicsStrip(
            hingeHapticsEnabled = hingeHapticsEnabled,
            foldCloseModeLabel = foldCloseModeLabel,
            onToggleHingeHaptics = onToggleHingeHaptics,
            onCycleFoldCloseMode = onCycleFoldCloseMode,
            unfoldModeLabel = unfoldModeLabel,
            onCycleUnfoldMode = onCycleUnfoldMode,
            tailscaleIp = tailscaleIp,
            lanIp = lanIp
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickTweakPill(
                title = if (isTorchOn) "🔦 Torch: ON" else "🔦 Torch",
                active = isTorchOn,
                onClick = onToggleTorch
            )
            if (hingeAngleDegrees != null) {
                QuickTweakPill(
                    title = "120Hz VRR • Hinge ${hingeAngleDegrees.roundToInt()}°",
                    active = true,
                    onClick = {}
                )
            }
            if (onToggleLockRotation != null) {
                QuickTweakPill(
                    title = if (lockScreenRotationEnabled) "⟳ Lock Rotation: ON" else "⟳ Lock Rotation: OFF",
                    active = lockScreenRotationEnabled,
                    onClick = onToggleLockRotation
                )
            }
            if (onEnforceGboardDeX != null) {
                QuickTweakPill(
                    title = "⌨ Enforce Gboard in DeX",
                    active = isWriteSecureSettingsGranted,
                    onClick = onEnforceGboardDeX
                )
            }
            if (onLockScreenNow != null) {
                QuickTweakPill(
                    title = "🔒 Lock Screen",
                    active = isAccessibilityActive,
                    onClick = onLockScreenNow
                )
            }
            if (onOpenWallpaperPicker != null) {
                QuickTweakPill(
                    title = "✦ Wallpaper",
                    active = false,
                    onClick = onOpenWallpaperPicker
                )
            }
            if (!isDefaultLauncher && onOpenDefaultLauncherPrompt != null) {
                QuickTweakPill(
                    title = "★ Set Default Home",
                    active = false,
                    onClick = onOpenDefaultLauncherPrompt
                )
            }
        }
    }
}

/**
 * Z Fold 8 System Dashboard Card with live 120Hz VRR & Hinge Angle Sensor Telemetry Gauge
 * (`120Hz VRR • Hinge 180°`), live Thermal / RAM pill (`31.5°C • 9.4 / 16 GB RAM`),
 * live Tailscale Mesh & LAN Telemetry Pill (`🔗 Tailscale: 100.101.43.86`),
 * interactive Hinge Mechanics Strip (`⚙️ Hinge Detents: ON/OFF` + `📐 On Fold Close` + `📖 On Unfold`),
 * 1-tap `🔦 Torch` quick-tile button, and quick hardware/DeX/HUD controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemDashboardCard(
    postureState: DevicePostureState,
    batteryPct: Int,
    isCharging: Boolean,
    isAccessibilityActive: Boolean,
    isWriteSecureSettingsGranted: Boolean,
    lockScreenRotationEnabled: Boolean,
    onToggleLockRotation: () -> Unit,
    onEnforceGboardDeX: () -> Unit,
    onLockScreenNow: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    modifier: Modifier = Modifier,
    hingeAngleDegrees: Float? = null,
    showFloatingHudConfigurator: Boolean = false,
    onToggleFloatingHudConfigurator: (() -> Unit)? = null,
    onToggleRadialArc: (() -> Unit)? = null,
    isTorchOn: Boolean = false,
    batteryTempCelsius: Float = 31.5f,
    availableRamGb: Float = 9.4f,
    totalRamGb: Float = 16.0f,
    onToggleTorch: () -> Unit = {},
    hingeHapticsEnabled: Boolean = true,
    foldCloseModeLabel: String = "📱 Keep Cover Awake",
    onToggleHingeHaptics: () -> Unit = {},
    onCycleFoldCloseMode: () -> Unit = {},
    unfoldModeLabel: String = "🖥️ Inner Cockpit",
    onCycleUnfoldMode: () -> Unit = {},
    tailscaleIp: String? = null,
    lanIp: String? = null,
    isWirelessAdbEnabled: Boolean = false,
    wirelessAdbPort: Int? = null,
    isTailscaleConnected: Boolean = !tailscaleIp.isNullOrBlank(),
    onToggleWirelessAdb: () -> Unit = {},
    onOpenWirelessAdbSettings: () -> Unit = {},
    onToggleTailscale: () -> Unit = {},
    onOpenTailscaleApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val hudState by FloatingHudController.state.collectAsState()
    val sensorHingeAngle = rememberHardwareHingeAngleDegrees()

    val resolvedHingeDeg = (hingeAngleDegrees ?: sensorHingeAngle ?: when {
        postureState.isFlexTabletop -> 118f
        postureState.isBookPosture -> 135f
        postureState.isUnfoldedInner -> 180f
        else -> 0f
    }).coerceIn(0f, 180f)

    val roundedHingeDeg = resolvedHingeDeg.roundToInt()
    val hingeProgress = (resolvedHingeDeg / 180f).coerceIn(0.04f, 1f)
    val postureModeText = when {
        roundedHingeDeg in 25..74 -> "TENT"
        postureState.isFlexTabletop || roundedHingeDeg in 75..145 -> "FLEX"
        postureState.isBookPosture -> "BOOK"
        postureState.isUnfoldedInner || roundedHingeDeg >= 146 -> "UNFOLDED"
        else -> "COVER"
    }

    val tempFormatted = remember(batteryTempCelsius) {
        String.format(Locale.US, "%.1f°C", batteryTempCelsius)
    }
    val availRamFormatted = remember(availableRamGb) {
        String.format(Locale.US, "%.1f", availableRamGb)
    }
    val totalRamFormatted = remember(totalRamGb) {
        if (totalRamGb % 1f == 0f) {
            totalRamGb.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", totalRamGb)
        }
    }
    val thermalRamLabel = "$tempFormatted • $availRamFormatted / $totalRamFormatted GB RAM"

    val cardShape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(FoldLauncherColors.GlassSurface)
            .border(1.dp, FoldLauncherColors.GlassBorder, cardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Z FOLD 8 COMPANION · DeX · HUD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.ElectricCyan
                )
                StatusBadgeDot(
                    label = if (isWriteSecureSettingsGranted) "ADB OK" else "ADB Required",
                    ok = isWriteSecureSettingsGranted
                )
                StatusBadgeDot(
                    label = if (isAccessibilityActive) "A11y OK" else "A11y Off",
                    ok = isAccessibilityActive
                )
            }
            Text(
                text = "${postureState.widthDp}×${postureState.heightDp}dp · ${if (isCharging) "⚡" else ""}$batteryPct%",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = FoldLauncherColors.TextSecondary
            )
        }

        // Live Z Fold 8 Hinge & Display VRR Telemetry Gauge + Thermal / RAM Pill
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(FoldLauncherColors.DeepVoid.copy(alpha = 0.65f))
                .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(FoldLauncherColors.EmeraldSuccess)
                    )
                    Text(
                        text = "120Hz VRR • Hinge ${roundedHingeDeg}°",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.CyanBright
                    )
                }

                // Live Thermal / RAM Pill ("31.5°C • 9.4 / 16 GB RAM")
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(FoldLauncherColors.GlassSurfaceHighlight)
                        .border(1.dp, FoldLauncherColors.GlassBorderStrong, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = thermalRamLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.TextPrimary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$postureModeText · LTPO 1–120Hz",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.TextSecondary
                )
                Text(
                    text = if (isTorchOn) "🔦 Torch Active" else "🔦 Torch Ready",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isTorchOn) FoldLauncherColors.AmberWarning else FoldLauncherColors.TextMuted
                )
            }

            // Visual Hinge Angle Bar (0° Closed Cover -> 90° Flex -> 180° Flat Unfolded)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(FoldLauncherColors.GlassSurfaceHighlight)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(hingeProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    FoldLauncherColors.IndigoAccent,
                                    FoldLauncherColors.ElectricCyan,
                                    FoldLauncherColors.EmeraldSuccess
                                )
                            )
                        )
                )
            }
        }

        // Samsung One UI 9.0 Wireless Debugging & Tailscale Quick-Toggle Pills Row (v0.6.0)
        OneUiConnectivityPillsRow(
            isWirelessAdbEnabled = isWirelessAdbEnabled,
            wirelessAdbPort = wirelessAdbPort,
            tailscaleIp = tailscaleIp,
            lanIp = lanIp,
            isTailscaleConnected = isTailscaleConnected,
            onToggleWirelessAdb = onToggleWirelessAdb,
            onOpenWirelessAdbSettings = onOpenWirelessAdbSettings,
            onToggleTailscale = onToggleTailscale,
            onOpenTailscaleApp = onOpenTailscaleApp
        )

        // Live Tailscale Mesh & LAN Telemetry Pill with 1-tap Clipboard copy
        TailscaleMeshTelemetryPill(
            tailscaleIp = tailscaleIp,
            lanIp = lanIp
        )

        // Interactive Z Fold 8 Hinge Mechanics Strip (15° Watch-Crown Detents + Fold-Close + Unfold-Open Cyclers)
        HingeMechanicsStrip(
            hingeHapticsEnabled = hingeHapticsEnabled,
            foldCloseModeLabel = foldCloseModeLabel,
            onToggleHingeHaptics = onToggleHingeHaptics,
            onCycleFoldCloseMode = onCycleFoldCloseMode,
            unfoldModeLabel = unfoldModeLabel,
            onCycleUnfoldMode = onCycleUnfoldMode,
            tailscaleIp = tailscaleIp,
            lanIp = lanIp
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickTweakPill(
                title = if (isTorchOn) "🔦 Torch: ON" else "🔦 Torch",
                active = isTorchOn,
                onClick = onToggleTorch
            )
            QuickTweakPill(
                title = if (hudState.isEnabled) "◉ Micro-HUD Pill: ON" else "◎ Micro-HUD Pill: OFF",
                active = hudState.isEnabled,
                onClick = {
                    val attached = FloatingHudController.setHudEnabled(context, !hudState.isEnabled)
                    if (!hudState.isEnabled && !attached) {
                        SystemTweaker.openAccessibilitySettings(context)
                    }
                }
            )
            if (onToggleFloatingHudConfigurator != null) {
                QuickTweakPill(
                    title = if (showFloatingHudConfigurator) "▴ Hide HUD Studio" else "▾ Configure HUD",
                    active = showFloatingHudConfigurator,
                    onClick = onToggleFloatingHudConfigurator
                )
            }
            if (onToggleRadialArc != null) {
                QuickTweakPill(
                    title = "◡ One-Handed Arc Deck",
                    active = false,
                    onClick = onToggleRadialArc
                )
            }
            QuickTweakPill(
                title = if (lockScreenRotationEnabled) "⟳ Lock Rotation: ON" else "⟳ Lock Rotation: OFF",
                active = lockScreenRotationEnabled,
                onClick = onToggleLockRotation
            )
            QuickTweakPill(
                title = "⌨ Enforce Gboard in DeX",
                active = isWriteSecureSettingsGranted,
                onClick = onEnforceGboardDeX
            )
            QuickTweakPill(
                title = "🔒 Lock Screen",
                active = isAccessibilityActive,
                onClick = onLockScreenNow
            )
            QuickTweakPill(
                title = "✦ Wallpaper",
                active = false,
                onClick = onOpenWallpaperPicker
            )
        }
    }
}

/**
 * 1-Tap Z Fold 8 Multi-Window App Pair Launcher Card with built-in power presets:
 * - `VC + Termux`
 * - `VC Prod + Compare`
 * - `VC + Chrome`
 * Plus any user-customized AppPairs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppPairsQuickCard(
    modifier: Modifier = Modifier,
    customAppPairs: List<AppPair> = emptyList(),
    onLaunchAppPair: ((AppPair) -> Unit)? = null
) {
    val context = LocalContext.current
    val effectivePairs = remember(customAppPairs) {
        if (customAppPairs.isEmpty()) {
            DefaultZFold8AppPairPresets
        } else {
            (customAppPairs + DefaultZFold8AppPairPresets).distinctBy {
                "${it.primaryPackage}|${it.secondaryPackage}"
            }
        }
    }

    val cardShape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(FoldLauncherColors.GlassSurface)
            .border(1.dp, FoldLauncherColors.GlassBorder, cardShape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Z FOLD 8 SPLIT-SCREEN POWER PAIRS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.CyanBright
            )
            Text(
                text = "1-TAP MULTI-WINDOW",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.TextSecondary
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            effectivePairs.forEach { pair ->
                val chipShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .clip(chipShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    FoldLauncherColors.IndigoAccent.copy(alpha = 0.28f),
                                    FoldLauncherColors.GlassSurfaceHighlight
                                )
                            )
                        )
                        .border(1.dp, FoldLauncherColors.GlassBorderStrong, chipShape)
                        .clickable {
                            if (onLaunchAppPair != null) {
                                onLaunchAppPair(pair)
                            } else {
                                SystemTweaker.launchSplitScreenPair(
                                    context = context,
                                    primaryPackage = pair.primaryPackage,
                                    secondaryPackage = pair.secondaryPackage
                                )
                            }
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⊞",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.ElectricCyan
                    )
                    Text(
                        text = pair.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadgeDot(
    label: String,
    ok: Boolean
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(FoldLauncherColors.GlassSurfaceHighlight)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (ok) FoldLauncherColors.EmeraldSuccess else FoldLauncherColors.AmberWarning
                )
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = FoldLauncherColors.TextSecondary
        )
    }
}

@Composable
private fun QuickTweakPill(
    title: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(pillShape)
            .background(
                if (active) FoldLauncherColors.CyanGlow else FoldLauncherColors.GlassSurfaceHighlight
            )
            .border(
                1.dp,
                if (active) FoldLauncherColors.GlassBorderStrong else FoldLauncherColors.GlassBorder,
                pillShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) FoldLauncherColors.CyanBright else FoldLauncherColors.TextPrimary
        )
    }
}
