package com.xsun.foldlauncher.display

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.xsun.foldlauncher.service.FoldAccessibilityService
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.vc.VcBackgroundMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Secondary-display OLED companion view (`android.app.Presentation`) for Galaxy Z Fold 8
 * concurrent dual-screen states (`CONCURRENT_INNER_DEFAULT` / `CONCURRENT_OUTER_DEFAULT`)
 * and external DeX / secondary displays.
 *
 * Renders an ultra-clean, zero-overdraw `#090D10` OLED status cockpit on the secondary display
 * with oversized clock, live hinge angle (`0°..180°`), VC agent telemetry, and thermal/RAM status.
 */
class CoverScreenPresentation @JvmOverloads constructor(
    outerContext: Context,
    display: Display,
    private val titleOverride: String? = null,
    private val onPrimaryAction: (() -> Unit)? = null
) : Presentation(outerContext, display) {

    private var clockTextView: TextView? = null
    private var dateTextView: TextView? = null
    private var vcStatusTextView: TextView? = null
    private var hardwareMetricsTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = context.resources.displayMetrics.density
        val padOuter = (24 * density).toInt()
        val padCard = (20 * density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FF090D10"))
            setPadding(padOuter, padOuter, padOuter, padOuter)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padCard, padCard, padCard, padCard)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f * density
                setColor(Color.parseColor("#FF11171C"))
                setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.parseColor("#66B9E6C8"))
            }
            setOnClickListener {
                onPrimaryAction?.invoke() ?: SystemTweaker.launchVc(context)
            }
        }

        val badgeTv = TextView(context).apply {
            text = titleOverride ?: "FOLD8 DUAL-DISPLAY COMPANION"
            setTextColor(Color.parseColor("#FFB9E6C8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val clockTv = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
            typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
        }

        val dateTv = TextView(context).apply {
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (14 * density).toInt())
        }

        val vcPillTv = TextView(context).apply {
            setTextColor(Color.parseColor("#FFB9E6C8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            val pillH = (16 * density).toInt()
            val pillV = (8 * density).toInt()
            setPadding(pillH, pillV, pillH, pillV)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(Color.parseColor("#FF172D28"))
                setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#80B9E6C8"))
            }
        }

        val hwTv = TextView(context).apply {
            setTextColor(Color.parseColor("#99FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, (14 * density).toInt(), 0, 0)
        }

        card.addView(badgeTv)
        card.addView(clockTv)
        card.addView(dateTv)
        card.addView(vcPillTv)
        card.addView(hwTv)
        root.addView(card)

        clockTextView = clockTv
        dateTextView = dateTv
        vcStatusTextView = vcPillTv
        hardwareMetricsTextView = hwTv

        setContentView(root)
        refreshTelemetry()
    }

    fun refreshTelemetry() {
        val now = Date()
        clockTextView?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        dateTextView?.text = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)

        val vcState = try {
            VcBackgroundMonitor.getInstance(context).uiState.value
        } catch (_: Exception) {
            null
        }
        vcStatusTextView?.text = when {
            vcState == null -> "VC · STANDBY"
            vcState.approvalWaitingCount > 0 ->
                "VC · ${vcState.approvalWaitingCount} APPROVAL WAITING"
            vcState.workingCount > 0 ->
                "VC · ${vcState.workingCount} AGENT WORKING"
            vcState.unreadUpdateCount > 0 ->
                "VC · +${vcState.unreadUpdateCount} UPDATED"
            else -> "VC · ALL CAUGHT UP"
        }

        val hw = SystemTweaker.readHardwareTelemetry(context)
        val angleStr = FoldAccessibilityService.hingeAngleFlow.value?.let { "${it.roundToInt()}°" } ?: "FLAT"
        val tempStr = if (hw.batteryTempCelsius > 0f) "${"%.1f".format(hw.batteryTempCelsius)}°C" else "--°C"
        val ramStr = if (hw.totalRamMb > 0L) "${hw.availableRamMb}/${hw.totalRamMb}MB FREE" else ""
        hardwareMetricsTextView?.text = listOfNotNull(
            "HINGE $angleStr",
            "BAT $tempStr (${hw.batteryCurrentMa}mA)",
            ramStr.takeIf { it.isNotEmpty() }
        ).joinToString(" · ")
    }

    companion object {
        fun findSecondaryDisplays(context: Context): List<Display> {
            return DualScreenPresentationController.getSecondaryDisplays(context)
        }

        fun showOnAvailableSecondaryDisplay(
            context: Context,
            onPrimaryAction: (() -> Unit)? = null
        ): Boolean {
            return DualScreenPresentationController.showOnSecondaryDisplay(context, onPrimaryAction)
        }

        fun dismissActivePresentation() {
            DualScreenPresentationController.dismissSecondaryPresentation()
        }
    }
}

object DualScreenPresentationController {

    private var activePresentation: CoverScreenPresentation? = null

    private val _isShowingFlow = MutableStateFlow(false)
    val isShowingFlow: StateFlow<Boolean> = _isShowingFlow.asStateFlow()

    fun isShowing(): Boolean = _isShowingFlow.value

    fun getSecondaryDisplays(context: Context): List<Display> {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                ?: return emptyList()
            dm.displays.filter {
                it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasSecondaryDisplay(context: Context): Boolean {
        return getSecondaryDisplays(context).isNotEmpty()
    }

    fun showOnSecondaryDisplay(
        context: Context,
        onPrimaryAction: (() -> Unit)? = null
    ): Boolean {
        val targetDisplay = getSecondaryDisplays(context).firstOrNull() ?: return false
        val existing = activePresentation
        if (existing != null && existing.isShowing && existing.display.displayId == targetDisplay.displayId) {
            existing.refreshTelemetry()
            _isShowingFlow.value = true
            return true
        }

        dismissSecondaryPresentation()
        return try {
            val presentation = CoverScreenPresentation(
                outerContext = context,
                display = targetDisplay,
                onPrimaryAction = onPrimaryAction
            )
            presentation.show()
            activePresentation = presentation
            _isShowingFlow.value = true
            true
        } catch (_: Exception) {
            activePresentation = null
            _isShowingFlow.value = false
            false
        }
    }

    fun refreshActivePresentation() {
        try {
            activePresentation?.refreshTelemetry()
        } catch (_: Exception) {
        }
    }

    fun dismissSecondaryPresentation() {
        val existing = activePresentation
        activePresentation = null
        _isShowingFlow.value = false
        if (existing != null) {
            try {
                if (existing.isShowing) {
                    existing.dismiss()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun toggleSecondaryPresentation(
        context: Context,
        onPrimaryAction: (() -> Unit)? = null
    ): Boolean {
        return if (_isShowingFlow.value) {
            dismissSecondaryPresentation()
            false
        } else {
            showOnSecondaryDisplay(context, onPrimaryAction)
        }
    }
}
