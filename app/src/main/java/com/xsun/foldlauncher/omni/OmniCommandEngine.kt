package com.xsun.foldlauncher.omni

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.model.AppEntry
import com.xsun.foldlauncher.search.SearchEngine
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.ui.theme.FoldLauncherColors
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Target system, hardware torch, dual-screen presentation, workspace profile, posture simulator,
 * hinge haptics, fold-close action, or launcher toggle triggered by a `> ...` or `posture: ...` Omni-Command.
 */
enum class SystemToggleTarget(val defaultActionId: String) {
    DEX_GBOARD("dex_gboard"),
    ROTATION_ON("rotation_on"),
    ROTATION_OFF("rotation_off"),
    LOCK_SCREEN("lock_screen"),
    FLOATING_HUD("floating_hud"),
    THUMB_ARC("thumb_arc"),
    NOTIFICATIONS("notifications"),
    POSTURE_FLEX("posture_flex"),
    POSTURE_INNER("posture_inner"),
    POSTURE_COVER("posture_cover"),
    POSTURE_TENT("posture_tent"),
    POSTURE_AUTO("posture_auto"),
    TOGGLE_TORCH("toggle_torch"),
    TOGGLE_COVER_PRESENTATION("toggle_cover_presentation"),
    SWITCH_WORKSPACE_PROFILE("switch_workspace_profile"),
    TOGGLE_HINGE_HAPTICS("toggle_hinge_haptics"),
    SET_FOLD_CLOSE_ACTION("set_fold_close_action")
}

/**
 * Type alias so callers referencing `SystemActionType` or `SystemToggleTarget` share the exact same enum.
 */
typealias SystemActionType = SystemToggleTarget

/**
 * Type alias so callers referencing `OmniCommandResult` or `OmniAction` share the exact same sealed hierarchy.
 */
typealias OmniCommandResult = OmniAction

/**
 * Represents a captured clipboard entry or a pinned developer snippet in [ClipboardVault].
 */
data class ClipboardItem(
    val id: String,
    val text: String,
    val label: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val category: String = "Clipboard"
)

/**
 * Structured action produced by [OmniCommandEngine] when parsing a Spotlight query.
 */
sealed class OmniAction {
    abstract val title: String
    abstract val subtitle: String
    abstract val badge: String
    open val copyText: String? = null
    open val actionId: String = ""
    val id: String get() = actionId
    val commandId: String get() = actionId

    /**
     * Launches two apps side-by-side in multi-window split-screen mode on the fly
     * without requiring a pre-saved App Pair (`split: yt + chrome`, `split: vc + compare`, or `youtube | settings`).
     */
    data class LaunchAdHocSplit(
        val primaryApp: AppEntry,
        val secondaryApp: AppEntry,
        val leftQuery: String = "",
        val rightQuery: String = ""
    ) : OmniAction() {
        override val title: String = "${primaryApp.label} + ${secondaryApp.label}"
        override val subtitle: String =
            "Ad-Hoc Split-Screen · ${primaryApp.packageName} | ${secondaryApp.packageName}"
        override val badge: String = "SPLIT 50/50"
        override val actionId: String = "split_${primaryApp.packageName}_${secondaryApp.packageName}"
    }

    /**
     * Executes a live system/DeX setting tweak, hardware torch toggle, Z Fold 8 dual-screen cover presentation,
     * workspace profile switch, hinge haptics toggle, fold-close action, posture simulator override, or launcher overlay toggle.
     */
    data class SystemCommand(
        val target: SystemToggleTarget,
        val triggerCommand: String,
        override val title: String,
        override val subtitle: String,
        override val badge: String = "SYSTEM",
        override val actionId: String = target.defaultActionId,
        val parameter: String = ""
    ) : OmniAction() {
        val actionType: SystemActionType get() = target
        val type: SystemActionType get() = target
        val profileKey: String get() = parameter
        val foldCloseMode: String get() = parameter
        val desiredTorchState: Boolean?
            get() = when (parameter.lowercase(Locale.US)) {
                "on", "true", "1" -> true
                "off", "false", "0" -> false
                else -> null
            }
    }

    /**
     * Dispatches a shell command snippet to Termux (`termux: <cmd>`, `sh: <cmd>`, or `termux-split: <cmd>`),
     * copying [shellCommand] to the clipboard and launching `com.termux` (or `VC + Termux` split-screen when [openSplitWithVc] is true).
     */
    data class TermuxCommand(
        val shellCommand: String,
        val openSplitWithVc: Boolean = false
    ) : OmniAction() {
        val command: String get() = shellCommand
        override val title: String =
            if (shellCommand.isBlank()) {
                if (openSplitWithVc) "Launch VC + Termux Split-Screen" else "Open Termux Console"
            } else {
                if (openSplitWithVc) "Termux Split (VC + Termux): $shellCommand"
                else "Termux Shell: $shellCommand"
            }
        override val subtitle: String =
            if (openSplitWithVc) "Copies command to clipboard & launches VC + Termux (${OmniCommandEngine.TERMUX_PACKAGE}) 50/50"
            else "Copies command to clipboard & launches Termux (${OmniCommandEngine.TERMUX_PACKAGE})"
        override val badge: String = if (openSplitWithVc) "TERMUX SPLIT" else "TERMUX SHELL"
        override val copyText: String = shellCommand
        override val actionId: String = if (openSplitWithVc) "termux_split_command" else "termux_command"
    }

    /**
     * Dispatches a prompt directly to the VC agent app (`vc: <prompt>` or `@vc <prompt>`).
     * Extended by [VcQuickMacro] for instant macro expansion (`vc: status`, `vc: approve`, `vc: ship`, `vc: test`).
     */
    open class SendToVc(
        val prompt: String,
        val startVoiceTalk: Boolean = false,
        val adjacentSplitScreen: Boolean = false,
        open val macroKey: String? = null
    ) : OmniAction() {
        open val promptText: String get() = prompt
        val isQuickMacro: Boolean get() = !macroKey.isNullOrBlank()

        override val title: String = when {
            !macroKey.isNullOrBlank() -> "VC Macro [$macroKey]: \"$prompt\""
            prompt.isBlank() -> "Open VC Agent Console"
            else -> "VC Dispatch: \"$prompt\""
        }
        override val subtitle: String =
            "Copy prompt & launch VC (${OmniCommandEngine.VC_PROD_PACKAGE_Z_FOLD8})"
        override val badge: String = if (!macroKey.isNullOrBlank()) "VC MACRO" else "VC AGENT"
        override val copyText: String = prompt
        override val actionId: String = if (!macroKey.isNullOrBlank()) "vc_macro_$macroKey" else "vc_dispatch"

        operator fun component1(): String = prompt
        operator fun component2(): Boolean = startVoiceTalk
        operator fun component3(): Boolean = adjacentSplitScreen
        operator fun component4(): String? = macroKey

        fun copy(
            prompt: String = this.prompt,
            startVoiceTalk: Boolean = this.startVoiceTalk,
            adjacentSplitScreen: Boolean = this.adjacentSplitScreen,
            macroKey: String? = this.macroKey
        ): SendToVc = SendToVc(
            prompt = prompt,
            startVoiceTalk = startVoiceTalk,
            adjacentSplitScreen = adjacentSplitScreen,
            macroKey = macroKey
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SendToVc) return false
            return prompt == other.prompt &&
                startVoiceTalk == other.startVoiceTalk &&
                adjacentSplitScreen == other.adjacentSplitScreen &&
                macroKey == other.macroKey
        }

        override fun hashCode(): Int {
            var result = prompt.hashCode()
            result = 31 * result + startVoiceTalk.hashCode()
            result = 31 * result + adjacentSplitScreen.hashCode()
            result = 31 * result + (macroKey?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String =
            "SendToVc(prompt=$prompt, startVoiceTalk=$startVoiceTalk, adjacentSplitScreen=$adjacentSplitScreen, macroKey=$macroKey)"
    }

    /**
     * Quick VC Macro Command (`vc: status`, `vc: approve`, `vc: ship`, `vc: test`).
     * Subclasses [SendToVc] so existing `is OmniAction.SendToVc` handlers automatically dispatch [promptText]
     * while callers matching `is OmniAction.VcQuickMacro` can inspect [macroKey].
     */
    class VcQuickMacro(
        override val macroKey: String,
        override val promptText: String
    ) : SendToVc(
        prompt = promptText,
        startVoiceTalk = false,
        adjacentSplitScreen = false,
        macroKey = macroKey
    ) {
        override val badge: String = "VC MACRO · ${macroKey.uppercase(Locale.US)}"
        override val actionId: String = "vc_macro_${macroKey.lowercase(Locale.US)}"

        fun copy(
            macroKey: String = this.macroKey,
            promptText: String = this.promptText
        ): VcQuickMacro = VcQuickMacro(macroKey = macroKey, promptText = promptText)

        override fun toString(): String =
            "VcQuickMacro(macroKey=$macroKey, promptText=$promptText)"
    }

    /**
     * Instant developer evaluation or unit conversion (Hex/Dec/Bin/RGB, Unix Epoch/ISO-8601, px->dp, km/mi, kg/lb, C/F, Base64).
     */
    data class DeveloperEval(
        val category: String,
        val inputExpression: String,
        val primaryOutput: String,
        val secondaryDetails: String,
        val swatchColorHex: Long? = null,
        override val copyText: String = primaryOutput
    ) : OmniAction() {
        override val title: String = primaryOutput
        override val subtitle: String = secondaryDetails
        override val badge: String = category
        override val actionId: String = "dev_eval"
    }

    /**
     * Filtered results from the Clipboard History & Pinned Snippets Vault (`clip: <query>`).
     */
    data class ClipboardMatch(
        val items: List<ClipboardItem>,
        val filterQuery: String
    ) : OmniAction() {
        override val title: String =
            if (filterQuery.isBlank()) "Clipboard & Pinned Vault (${items.size})"
            else "Clipboard matches for \"$filterQuery\" (${items.size})"
        override val subtitle: String =
            items.firstOrNull()?.text?.replace('\n', ' ')?.take(72) ?: "No matching snippets"
        override val badge: String = "VAULT"
        override val copyText: String? = items.firstOrNull()?.text
        override val actionId: String = "clipboard_match"
    }
}

/**
 * Pure, fast, unit-testable parser, evaluator, and executor for Fold8 Launcher's Omni-Command Palette.
 */
object OmniCommandEngine {

    /**
     * Default display density (420 dpi) for the Samsung Galaxy Z Fold 8 (`SM-F971U1`).
     */
    const val DEFAULT_Z_FOLD8_DPI = 420.0

    /**
     * Built-in Z Fold 8 Multi-VC, Termux, and Chrome package constants.
     */
    const val VC_PROD_PACKAGE_Z_FOLD8 = "dev.agentconversation"
    const val VC_PREVIEW_PACKAGE_Z_FOLD8 = "dev.agentconversation.preview"
    const val VC_COMPARE_PACKAGE_Z_FOLD8 = "dev.agentconversation.compare"
    const val TERMUX_PACKAGE = "com.termux"
    const val CHROME_PACKAGE = "com.android.chrome"

    /**
     * Built-in VC Quick Macro definitions (`vc: status`, `vc: approve`, `vc: ship`, `vc: test`).
     */
    val VC_QUICK_MACROS: Map<String, String> = mapOf(
        "status" to "Status check: summarize active workers, CI status, and any pending blockers.",
        "approve" to "Approved. Proceed with the current plan and ship the changes.",
        "ship" to "Run full verification, commit, push, and merge to main.",
        "test" to "Run ./gradlew testDebugUnitTest lintDebug assembleDebug and report results."
    )

    @Volatile
    private var torchEnabledState: Boolean = false

    private data class BuiltInAliasSpec(
        val packageName: String,
        val defaultLabel: String,
        val defaultActivity: String,
        val aliases: Set<String>,
        val isFold8SpecialAlias: Boolean
    )

    private val BUILT_IN_ALIAS_SPECS = listOf(
        BuiltInAliasSpec(
            packageName = VC_PROD_PACKAGE_Z_FOLD8,
            defaultLabel = "VC",
            defaultActivity = ".MainActivity",
            aliases = setOf("vc", "prod", "vcprod", "vc prod", "vci", "dev.agentconversation"),
            isFold8SpecialAlias = true
        ),
        BuiltInAliasSpec(
            packageName = VC_PREVIEW_PACKAGE_Z_FOLD8,
            defaultLabel = "VC Preview",
            defaultActivity = ".MainActivity",
            aliases = setOf("vcprev", "preview", "vc preview", "vc-preview", "dev.agentconversation.preview"),
            isFold8SpecialAlias = true
        ),
        BuiltInAliasSpec(
            packageName = VC_COMPARE_PACKAGE_Z_FOLD8,
            defaultLabel = "VC Compare",
            defaultActivity = ".MainActivity",
            aliases = setOf("vccomp", "compare", "vc compare", "vc-compare", "dev.agentconversation.compare"),
            isFold8SpecialAlias = true
        ),
        BuiltInAliasSpec(
            packageName = TERMUX_PACKAGE,
            defaultLabel = "Termux",
            defaultActivity = ".app.TermuxActivity",
            aliases = setOf("termux", "tty", "sh", "com.termux"),
            isFold8SpecialAlias = true
        ),
        BuiltInAliasSpec(
            packageName = CHROME_PACKAGE,
            defaultLabel = "Chrome",
            defaultActivity = "com.google.android.apps.chrome.Main",
            aliases = setOf("chrome", "web", "com.android.chrome"),
            isFold8SpecialAlias = false
        )
    )

    private val ISO_UTC_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .withZone(ZoneOffset.UTC)

    private val READABLE_LOCAL_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.US)

    private val SPLIT_PREFIX_REGEX =
        Regex("^(?:split\\s*:\\s*|split\\s+)(.+?)\\s*(?:\\+|\\||,|\\band\\b|\\bwith\\b)\\s*(.+)$", RegexOption.IGNORE_CASE)

    private val PIPE_SPLIT_REGEX =
        Regex("^([^|>]+?)\\s*\\|\\s*([^|>]+)$")

    private val POSTURE_PREFIX_REGEX =
        Regex("^posture(?:\\s*:\\s*|\\s+)(.*)$", RegexOption.IGNORE_CASE)

    private val PROFILE_PREFIX_REGEX =
        Regex("^profile(?:\\s*:\\s*|\\s+)(.*)$", RegexOption.IGNORE_CASE)

    private val FOLD_PREFIX_REGEX =
        Regex("^fold(?:\\s*:\\s*|\\s+)(stay|voice|tent|lock)$", RegexOption.IGNORE_CASE)

    private val TERMUX_COMMAND_REGEX =
        Regex("^(termux-split|sh-split|tty-split|termux|sh|tty)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)

    private val VC_COMMAND_REGEX =
        Regex("^(?:vc\\s*:\\s*|@vc\\s*:?\\s*|vc\\s+)(.*)$", RegexOption.IGNORE_CASE)

    private val CLIP_COMMAND_REGEX =
        Regex("^(?:clip|cb|vault|paste)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)

    private val BASE64_ENCODE_REGEX =
        Regex("^(?:b64|base64)(?:\\s+encode)?\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)

    private val BASE64_DECODE_REGEX =
        Regex("^(?:b64d|base64d|b64\\s+decode|base64\\s+decode)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)

    private val HEX_LITERAL_REGEX =
        Regex("^0x([0-9a-fA-F]{1,16})(?:\\s+(?:in|to)\\s+(dec|decimal|bin|binary|hex))?$", RegexOption.IGNORE_CASE)

    private val BIN_LITERAL_REGEX =
        Regex("^0b([01]{1,63})(?:\\s+(?:in|to)\\s+(dec|decimal|hex|bin|binary))?$", RegexOption.IGNORE_CASE)

    private val DEC_CONVERSION_REGEX =
        Regex("^(-?\\d+)\\s+(?:in|to)\\s+(hex|hexadecimal|bin|binary|oct|octal|dec|decimal)$", RegexOption.IGNORE_CASE)

    private val COLOR_HEX_REGEX =
        Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    private val PX_AT_DPI_REGEX =
        Regex(
            "^(\\d+(?:\\.\\d+)?)\\s*(px|dp)(?:\\s*(?:at|@)\\s*(\\d+(?:\\.\\d+)?)\\s*dpi)?(?:\\s+(?:in|to|->)\\s*(dp|px))?(?:\\s*(?:at|@)\\s*(\\d+(?:\\.\\d+)?)\\s*dpi)?$",
            RegexOption.IGNORE_CASE
        )

    private val RESOLUTION_AT_DPI_REGEX =
        Regex(
            "^(\\d+(?:\\.\\d+)?)\\s*[x×]\\s*(\\d+(?:\\.\\d+)?)\\s*(?:px)?(?:\\s*(?:at|@)\\s*(\\d+(?:\\.\\d+)?)\\s*dpi)?(?:\\s+(?:in|to|->)\\s*dp)?$",
            RegexOption.IGNORE_CASE
        )

    private val UNIT_CONVERSION_REGEX =
        Regex(
            "^([+-]?\\d+(?:\\.\\d+)?)\\s*([a-zA-Z°]+)(?:\\s+(?:in|to|->)\\s*([a-zA-Z°]+))?$",
            RegexOption.IGNORE_CASE
        )

    private val EPOCH_PREFIX_REGEX =
        Regex("^(?:epoch|ts|timestamp|unix)\\s*:?\\s*(\\d{9,14})$", RegexOption.IGNORE_CASE)

    private val RAW_EPOCH_REGEX =
        Regex("^(1\\d{9}|2\\d{9}|1\\d{12}|2\\d{12})$")

    /**
     * Evaluates a raw Spotlight query into an ordered list of [OmniAction] items.
     */
    fun evaluate(
        rawQuery: String,
        installedApps: List<AppEntry> = emptyList(),
        clipboardItems: List<ClipboardItem> = ClipboardVault.defaultPinnedSnippets(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<OmniAction> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<OmniAction>()

        // 1. Live System, DeX Tweaker, Torch, Dual-Screen Presentation, Workspace Profile, Hinge Haptics, Fold-Close & Posture Commands
        if (trimmed.startsWith(">") ||
            POSTURE_PREFIX_REGEX.matches(trimmed) ||
            PROFILE_PREFIX_REGEX.matches(trimmed) ||
            FOLD_PREFIX_REGEX.matches(trimmed)
        ) {
            results.addAll(parseSystemCommands(trimmed))
            return results
        }

        // 2. Termux / ADB Shell Snippet Dispatcher (`termux: <cmd>`, `sh: <cmd>`, `termux-split: <cmd>`)
        parseTermuxCommand(trimmed)?.let { termuxAction ->
            results.add(termuxAction)
            return results
        }

        // 3. Ad-Hoc Split-Screen App Pair Command (`split: <app1> + <app2>` or `<app1> | <app2>`)
        parseSplitCommand(trimmed, installedApps)?.let { splitAction ->
            results.add(splitAction)
            return results
        }

        // 4. VC Agent Direct Dispatch & VC Quick Macros (`vc: status`, `vc: approve`, `vc: ship`, `vc: test`, `vc: <prompt>`)
        parseVcCommand(trimmed)?.let { vcAction ->
            results.add(vcAction)
            return results
        }

        // 5. Clipboard Vault Filter (`clip: <search>`)
        parseClipboardQuery(trimmed, clipboardItems)?.let { clipAction ->
            results.add(clipAction)
            return results
        }

        // 6. Developer & Unit Conversions (Hex/Dec/Bin/RGB, Epoch, px@dpi / 2448x1848@420dpi, km/mi, kg/lb, C/F, Base64)
        evaluateDeveloperQuery(trimmed, nowEpochMs)?.let { devAction ->
            results.add(devAction)
        }

        return results
    }

    /**
     * Convenience helper returning the highest-priority [OmniAction] for a query, or `null` if none matched.
     */
    fun evaluatePrimary(
        rawQuery: String,
        installedApps: List<AppEntry> = emptyList(),
        clipboardItems: List<ClipboardItem> = ClipboardVault.defaultPinnedSnippets(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): OmniAction? = evaluate(rawQuery, installedApps, clipboardItems, nowEpochMs).firstOrNull()

    /**
     * Resolves built-in Z Fold 8 Multi-VC (`vc`/`prod`, `vcprev`/`preview`, `vccomp`/`compare`),
     * Termux (`termux`/`tty`/`sh`), and Chrome (`chrome`/`web`) aliases, preferring a matching
     * [AppEntry] from [installedApps] when present and synthesizing one when absent.
     */
    fun resolveAppAlias(query: String, installedApps: List<AppEntry> = emptyList()): AppEntry? {
        val normalized = query.trim().lowercase(Locale.US)
        if (normalized.isEmpty()) return null
        val spec = BUILT_IN_ALIAS_SPECS.firstOrNull { normalized in it.aliases } ?: return null
        return installedApps.firstOrNull { it.packageName.equals(spec.packageName, ignoreCase = true) }
            ?: AppEntry(
                label = spec.defaultLabel,
                packageName = spec.packageName,
                activityName = spec.defaultActivity
            )
    }

    private fun isFold8SpecialAliasToken(query: String): Boolean {
        val normalized = query.trim().lowercase(Locale.US)
        return BUILT_IN_ALIAS_SPECS.any { it.isFold8SpecialAlias && normalized in it.aliases }
    }

    /**
     * Parses ad-hoc split-screen queries like `split: yt + chrome`, `split: vc + compare`,
     * `split: vc + preview`, `split: vc + termux`, `split: prod + preview`, or `youtube | settings`
     * against `apps` + built-in Z Fold 8 Multi-VC/Termux package aliases.
     */
    fun parseSplitCommand(rawQuery: String, apps: List<AppEntry> = emptyList()): OmniAction.LaunchAdHocSplit? {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return null

        val pairTokens: Pair<String, String> = when {
            SPLIT_PREFIX_REGEX.matches(trimmed) -> {
                val match = SPLIT_PREFIX_REGEX.matchEntire(trimmed) ?: return null
                match.groupValues[1].trim() to match.groupValues[2].trim()
            }
            PIPE_SPLIT_REGEX.matches(trimmed) -> {
                val match = PIPE_SPLIT_REGEX.matchEntire(trimmed) ?: return null
                match.groupValues[1].trim() to match.groupValues[2].trim()
            }
            else -> return null
        }

        val leftQuery = pairTokens.first
        val rightQuery = pairTokens.second
        if (leftQuery.isEmpty() || rightQuery.isEmpty()) return null

        val hasFold8SpecialAlias = isFold8SpecialAliasToken(leftQuery) || isFold8SpecialAliasToken(rightQuery)
        if (apps.size < 2 && !hasFold8SpecialAlias) return null

        val primaryApp = resolveSplitSideApp(leftQuery, apps, excludePackage = null) ?: return null
        val secondaryApp = resolveSplitSideApp(rightQuery, apps, excludePackage = primaryApp.packageName)
            ?: return null

        if (primaryApp.packageName == secondaryApp.packageName && apps.size < 2) return null

        return OmniAction.LaunchAdHocSplit(
            primaryApp = primaryApp,
            secondaryApp = secondaryApp,
            leftQuery = leftQuery,
            rightQuery = rightQuery
        )
    }

    private fun resolveSplitSideApp(
        token: String,
        apps: List<AppEntry>,
        excludePackage: String?
    ): AppEntry? {
        val aliasApp = resolveAppAlias(token, apps)
        if (aliasApp != null && (excludePackage == null || aliasApp.packageName != excludePackage)) {
            return aliasApp
        }

        if (apps.isNotEmpty()) {
            val candidates = SearchEngine.rankApps(apps, token, limit = 5)
            val chosen = if (excludePackage != null) {
                candidates.firstOrNull { it.packageName != excludePackage } ?: candidates.firstOrNull()
            } else {
                candidates.firstOrNull()
            }
            if (chosen != null) return chosen
        }

        return aliasApp
    }

    /**
     * Parses `> ...` live system & DeX tweaker commands, hardware torch (`> torch`, `> flashlight`,
     * `> light on`, `> light off`), Z Fold 8 dual-screen cover presentation (`> dual`, `> cover hud`,
     * `> presentation`), workspace profile switcher (`> profile all`, `> profile vc`, `> profile focus`,
     * `> profile oled`), Z Fold 8 hinge haptics (`> haptics`, `> hinge haptics`, `> detents`),
     * fold-close actions (`> fold stay`, `> fold voice`, `> fold tent`, `> fold lock`),
     * and posture simulator commands (`> posture ...` / `posture: ...`).
     */
    fun parseSystemCommands(rawQuery: String): List<OmniAction.SystemCommand> {
        val trimmed = rawQuery.trim()
        val isChevron = trimmed.startsWith(">")
        val posturePrefixMatch = POSTURE_PREFIX_REGEX.matchEntire(trimmed)
        val profilePrefixMatch = PROFILE_PREFIX_REGEX.matchEntire(trimmed)
        val foldPrefixMatch = FOLD_PREFIX_REGEX.matchEntire(trimmed)
        if (!isChevron && posturePrefixMatch == null && profilePrefixMatch == null && foldPrefixMatch == null) {
            return emptyList()
        }

        val coreSystemCommands = listOf(
            OmniAction.SystemCommand(
                target = SystemToggleTarget.DEX_GBOARD,
                triggerCommand = "> dex gboard",
                title = "Enforce Gboard in Samsung DeX",
                subtitle = "Overrides Samsung Honeyboard & routes DeX IME via WRITE_SECURE_SETTINGS",
                badge = "DEX IME",
                actionId = "dex_gboard"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.ROTATION_ON,
                triggerCommand = "> rotation on",
                title = "Enable Lock & Home Landscape Rotation",
                subtitle = "Sets lock_screen_allow_rotation=1 & accelerometer_rotation=1",
                badge = "ROTATE ON",
                actionId = "rotation_on"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.ROTATION_OFF,
                triggerCommand = "> rotation off",
                title = "Disable Lock & Home Landscape Rotation",
                subtitle = "Locks orientation to portrait (lock_screen_allow_rotation=0)",
                badge = "ROTATE OFF",
                actionId = "rotation_off"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.LOCK_SCREEN,
                triggerCommand = "> lock",
                title = "Lock Screen Immediately",
                subtitle = "Triggers Accessibility GLOBAL_ACTION_LOCK_SCREEN without power button wear",
                badge = "LOCK",
                actionId = "lock_screen"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.FLOATING_HUD,
                triggerCommand = "> hud",
                title = "Toggle Floating Top-Right Micro-HUD Pill",
                subtitle = "Shows/hides system-wide battery, posture & DeX status pill",
                badge = "HUD PILL",
                actionId = "floating_hud"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.THUMB_ARC,
                triggerCommand = "> arc",
                title = "Toggle One-Handed Radial Thumb Arc",
                subtitle = "Opens/closes quick thumb-zone radial launcher overlay",
                badge = "THUMB ARC",
                actionId = "thumb_arc"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.NOTIFICATIONS,
                triggerCommand = "> shade",
                title = "Expand System Notification Shade",
                subtitle = "Pulls down status bar notifications panel",
                badge = "SHADE",
                actionId = "notifications"
            )
        )

        val postureCommands = listOf(
            OmniAction.SystemCommand(
                target = SystemToggleTarget.POSTURE_FLEX,
                triggerCommand = "> posture flex",
                title = "Switch Launcher to Flex / Tabletop Mode 115°",
                subtitle = "Simulates half-opened Z Fold 8 Flex posture (action: posture_flex)",
                badge = "POSTURE FLEX",
                actionId = "posture_flex",
                parameter = "flex"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.POSTURE_INNER,
                triggerCommand = "> posture inner",
                title = "Force Z Fold 8 Inner 2-Column Canvas",
                subtitle = "Overrides posture to unfolded 7.6\"+ EXPANDED tablet canvas (action: posture_inner)",
                badge = "POSTURE INNER",
                actionId = "posture_inner",
                parameter = "inner"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.POSTURE_COVER,
                triggerCommand = "> posture cover",
                title = "Force Z Fold 8 Cover Compact Mode",
                subtitle = "Overrides posture to folded cover screen COMPACT layout (action: posture_cover)",
                badge = "POSTURE COVER",
                actionId = "posture_cover",
                parameter = "cover"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.POSTURE_TENT,
                triggerCommand = "> posture tent",
                title = "Enter Z Fold 8 Tent Mode Standby HUD",
                subtitle = "Switches to low-power OLED Tent Mode Standby HUD (action: posture_tent)",
                badge = "POSTURE TENT",
                actionId = "posture_tent",
                parameter = "tent"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.POSTURE_AUTO,
                triggerCommand = "> posture auto",
                title = "Restore Hardware Sensor Auto-Posture",
                subtitle = "Clears manual posture override & resumes live hinge sensor tracking (action: posture_auto)",
                badge = "POSTURE AUTO",
                actionId = "posture_auto",
                parameter = "auto"
            )
        )

        val torchToggleCmd = OmniAction.SystemCommand(
            target = SystemToggleTarget.TOGGLE_TORCH,
            triggerCommand = "> torch",
            title = "Toggle Hardware Flashlight / Torch",
            subtitle = "Toggles rear camera LED flashlight via CameraManager.setTorchMode",
            badge = "TORCH",
            actionId = "toggle_torch",
            parameter = "toggle"
        )
        val torchOnCmd = OmniAction.SystemCommand(
            target = SystemToggleTarget.TOGGLE_TORCH,
            triggerCommand = "> light on",
            title = "Turn Hardware Flashlight / Torch ON",
            subtitle = "Enables rear camera LED torch immediately",
            badge = "TORCH ON",
            actionId = "torch_on",
            parameter = "on"
        )
        val torchOffCmd = OmniAction.SystemCommand(
            target = SystemToggleTarget.TOGGLE_TORCH,
            triggerCommand = "> light off",
            title = "Turn Hardware Flashlight / Torch OFF",
            subtitle = "Disables rear camera LED torch immediately",
            badge = "TORCH OFF",
            actionId = "torch_off",
            parameter = "off"
        )

        val coverPresentationCmd = OmniAction.SystemCommand(
            target = SystemToggleTarget.TOGGLE_COVER_PRESENTATION,
            triggerCommand = "> dual",
            title = "Toggle Z Fold 8 Dual-Screen Cover Presentation HUD",
            subtitle = "Projects live VC telemetry & status HUD onto the outer Cover Screen while unfolded",
            badge = "DUAL SCREEN",
            actionId = "toggle_cover_presentation",
            parameter = "toggle"
        )

        val workspaceProfileCommands = listOf(
            OmniAction.SystemCommand(
                target = SystemToggleTarget.SWITCH_WORKSPACE_PROFILE,
                triggerCommand = "> profile all",
                title = "Switch Workspace Profile: All Apps",
                subtitle = "Shows full app catalog & all dashboard widgets (profile: all)",
                badge = "PROFILE ALL",
                actionId = "profile_all",
                parameter = "all"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.SWITCH_WORKSPACE_PROFILE,
                triggerCommand = "> profile vc",
                title = "Switch Workspace Profile: VC Agent Ops",
                subtitle = "Prioritizes VC Prod, VC Preview, VC Compare, Termux & GitHub tools (profile: vc)",
                badge = "PROFILE VC",
                actionId = "profile_vc",
                parameter = "vc"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.SWITCH_WORKSPACE_PROFILE,
                triggerCommand = "> profile focus",
                title = "Switch Workspace Profile: Deep Focus",
                subtitle = "Hides distractions and keeps productivity & terminal apps only (profile: focus)",
                badge = "PROFILE FOCUS",
                actionId = "profile_focus",
                parameter = "focus"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.SWITCH_WORKSPACE_PROFILE,
                triggerCommand = "> profile oled",
                title = "Switch Workspace Profile: True OLED Minimal",
                subtitle = "Zero-chrome pure #000000 OLED canvas for maximum battery & focus (profile: oled)",
                badge = "PROFILE OLED",
                actionId = "profile_oled",
                parameter = "oled"
            )
        )

        val hingeHapticsCmd = OmniAction.SystemCommand(
            target = SystemToggleTarget.TOGGLE_HINGE_HAPTICS,
            triggerCommand = "> haptics",
            title = "Toggle Z Fold 8 Hinge Angle Haptic Detents",
            subtitle = "Fires crisp tactile haptic clicks when crossing 90°, 115° Flex, and 180° Flat detents",
            badge = "HINGE HAPTICS",
            actionId = "toggle_hinge_haptics",
            parameter = "toggle"
        )

        val foldCloseActionCommands = listOf(
            OmniAction.SystemCommand(
                target = SystemToggleTarget.SET_FOLD_CLOSE_ACTION,
                triggerCommand = "> fold stay",
                title = "Fold-Close Action: Keep Cover Screen Awake",
                subtitle = "Continuity onto outer Cover Screen when folding Z Fold 8 shut (fold_close_stay)",
                badge = "FOLD STAY",
                actionId = "fold_close_stay",
                parameter = "stay"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.SET_FOLD_CLOSE_ACTION,
                triggerCommand = "> fold voice",
                title = "Fold-Close Action: Launch VC Voice Talk on Fold",
                subtitle = "Automatically starts hands-free VC Voice Talk when folding Z Fold 8 shut (fold_close_voice)",
                badge = "FOLD VOICE",
                actionId = "fold_close_voice",
                parameter = "voice"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.SET_FOLD_CLOSE_ACTION,
                triggerCommand = "> fold tent",
                title = "Fold-Close Action: Enter Tent Standby HUD",
                subtitle = "Switches to low-power OLED Tent Mode Standby HUD when folding (fold_close_tent)",
                badge = "FOLD TENT",
                actionId = "fold_close_tent",
                parameter = "tent"
            ),
            OmniAction.SystemCommand(
                target = SystemToggleTarget.SET_FOLD_CLOSE_ACTION,
                triggerCommand = "> fold lock",
                title = "Fold-Close Action: Lock Screen Immediately",
                subtitle = "Locks the device immediately when folding Z Fold 8 shut (fold_close_lock)",
                badge = "FOLD LOCK",
                actionId = "fold_close_lock",
                parameter = "lock"
            )
        )

        if (posturePrefixMatch != null) {
            val postureArg = posturePrefixMatch.groupValues[1].trim().lowercase(Locale.US)
            if (postureArg.isEmpty()) return postureCommands
            return matchPostureCommands(postureArg, postureCommands)
        }

        if (profilePrefixMatch != null) {
            val profileArg = profilePrefixMatch.groupValues[1].trim().lowercase(Locale.US)
            if (profileArg.isEmpty()) return workspaceProfileCommands
            return matchWorkspaceProfileCommands(profileArg, workspaceProfileCommands)
        }

        if (foldPrefixMatch != null) {
            val foldArg = foldPrefixMatch.groupValues[1].trim().lowercase(Locale.US)
            return matchFoldCloseCommands(foldArg, foldCloseActionCommands)
        }

        val body = trimmed.removePrefix(">").trim().lowercase(Locale.US)
        if (body.isEmpty()) return coreSystemCommands

        // 1. Hardware Torch / Flashlight Commands (`> torch`, `> flashlight`, `> light on`, `> light off`)
        when (body) {
            "torch", "flashlight", "light", "flash" -> return listOf(torchToggleCmd)
            "light on", "torch on", "flashlight on" -> return listOf(torchOnCmd)
            "light off", "torch off", "flashlight off" -> return listOf(torchOffCmd)
        }

        // 2. Z Fold 8 Dual-Screen Cover Presentation Command (`> dual`, `> cover hud`, `> presentation`)
        if (body == "dual" ||
            body == "cover hud" ||
            body == "presentation" ||
            body == "dual screen" ||
            body == "cover presentation" ||
            body == "sub display"
        ) {
            return listOf(coverPresentationCmd)
        }

        // 3. Z Fold 8 Hinge Haptics & Detents Command (`> haptics`, `> hinge haptics`, `> detents`)
        if (body == "haptics" ||
            body == "hinge haptics" ||
            body == "detents" ||
            body == "hinge detents" ||
            body == "haptic"
        ) {
            return listOf(hingeHapticsCmd)
        }

        // 4. Z Fold 8 Fold-Close Action Commands (`> fold stay`, `> fold voice`, `> fold tent`, `> fold lock`)
        if (body.startsWith("fold ") || body.startsWith("fold:")) {
            val sub = body.removePrefix("fold").removePrefix(":").trim()
            if (sub.isEmpty()) return foldCloseActionCommands
            val matchedFold = matchFoldCloseCommands(sub, foldCloseActionCommands)
            if (matchedFold.isNotEmpty()) return matchedFold
        }

        // 5. Workspace Profile Switcher Commands (`> profile all`, `> profile vc`, `> profile focus`, `> profile oled`)
        if (body == "profile" || body == "profile:" || body == "profiles") {
            return workspaceProfileCommands
        }
        if (body.startsWith("profile ") || body.startsWith("profile:")) {
            val sub = body.removePrefix("profile").removePrefix(":").trim()
            if (sub.isEmpty()) return workspaceProfileCommands
            val matchedProfile = matchWorkspaceProfileCommands(sub, workspaceProfileCommands)
            if (matchedProfile.isNotEmpty()) return matchedProfile
        }

        // 6. Posture Simulator Commands (`> posture ...`, `> flex`, `> unfold`, `> tent`, `> cover`)
        if (body == "posture" || body == "posture:") {
            return postureCommands
        }
        if (body.startsWith("posture ") || body.startsWith("posture:")) {
            val sub = body.removePrefix("posture").removePrefix(":").trim()
            if (sub.isEmpty()) return postureCommands
            val matchedPosture = matchPostureCommands(sub, postureCommands)
            if (matchedPosture.isNotEmpty()) return matchedPosture
        }

        val directPostureMatch = when (body) {
            "flex", "tabletop" ->
                postureCommands.filter { it.target == SystemToggleTarget.POSTURE_FLEX }
            "unfold", "inner", "expanded" ->
                postureCommands.filter { it.target == SystemToggleTarget.POSTURE_INNER }
            "cover", "fold", "folded", "compact" ->
                postureCommands.filter { it.target == SystemToggleTarget.POSTURE_COVER }
            "tent" ->
                postureCommands.filter { it.target == SystemToggleTarget.POSTURE_TENT }
            "auto", "posture auto", "sensor" ->
                postureCommands.filter { it.target == SystemToggleTarget.POSTURE_AUTO }
            else -> emptyList()
        }
        if (directPostureMatch.isNotEmpty()) return directPostureMatch

        // 7. Core System & DeX Tweaker Commands
        val exactFiltered = when {
            body == "dex gboard" || body == "gboard" || body == "dex ime" || body == "dex" || body == "ime" ->
                coreSystemCommands.filter { it.target == SystemToggleTarget.DEX_GBOARD }
            body == "rotation on" || body == "rotate on" || body == "autorotate on" ->
                coreSystemCommands.filter { it.target == SystemToggleTarget.ROTATION_ON }
            body == "rotation off" || body == "rotate off" || body == "autorotate off" ->
                coreSystemCommands.filter { it.target == SystemToggleTarget.ROTATION_OFF }
            body == "rotation" || body == "rotate" ->
                coreSystemCommands.filter {
                    it.target == SystemToggleTarget.ROTATION_ON || it.target == SystemToggleTarget.ROTATION_OFF
                }
            body == "lock" || body == "lock screen" || body == "sleep" ->
                coreSystemCommands.filter { it.target == SystemToggleTarget.LOCK_SCREEN }
            body == "hud" || body == "pill" || body == "micro hud" || body.startsWith("hud ") ->
                coreSystemCommands.filter { it.target == SystemToggleTarget.FLOATING_HUD }
            body == "arc" || body == "thumb" || body == "thumb arc" || body == "radial" ->
                coreSystemCommands.filter { it.target == SystemToggleTarget.THUMB_ARC }
            body == "shade" || body == "notifications" || body == "notif" ->
                coreSystemCommands.filter { it.target == SystemToggleTarget.NOTIFICATIONS }
            else -> emptyList()
        }

        if (exactFiltered.isNotEmpty()) return exactFiltered

        val allCommands = coreSystemCommands +
            listOf(torchToggleCmd, torchOnCmd, torchOffCmd, coverPresentationCmd, hingeHapticsCmd) +
            foldCloseActionCommands +
            workspaceProfileCommands +
            postureCommands

        return allCommands.filter { cmd ->
            cmd.triggerCommand.lowercase(Locale.US).contains(body) ||
                cmd.title.lowercase(Locale.US).contains(body) ||
                cmd.badge.lowercase(Locale.US).contains(body) ||
                cmd.actionId.lowercase(Locale.US).contains(body)
        }
    }

    private fun matchFoldCloseCommands(
        query: String,
        foldCommands: List<OmniAction.SystemCommand>
    ): List<OmniAction.SystemCommand> {
        val exact = when (query) {
            "stay", "awake", "continue", "continuity" -> foldCommands.filter { it.parameter == "stay" }
            "voice", "talk", "vc", "mic" -> foldCommands.filter { it.parameter == "voice" }
            "tent", "standby", "hud" -> foldCommands.filter { it.parameter == "tent" }
            "lock", "sleep", "off" -> foldCommands.filter { it.parameter == "lock" }
            else -> emptyList()
        }
        if (exact.isNotEmpty()) return exact
        return foldCommands.filter { cmd ->
            cmd.parameter.contains(query) ||
                cmd.title.lowercase(Locale.US).contains(query) ||
                cmd.actionId.lowercase(Locale.US).contains(query)
        }
    }

    private fun matchWorkspaceProfileCommands(
        query: String,
        profileCommands: List<OmniAction.SystemCommand>
    ): List<OmniAction.SystemCommand> {
        val exact = when (query) {
            "all", "default", "full" -> profileCommands.filter { it.parameter == "all" }
            "vc", "agent", "ops", "dev" -> profileCommands.filter { it.parameter == "vc" }
            "focus", "work", "deep" -> profileCommands.filter { it.parameter == "focus" }
            "oled", "minimal", "dark", "battery" -> profileCommands.filter { it.parameter == "oled" }
            else -> emptyList()
        }
        if (exact.isNotEmpty()) return exact
        return profileCommands.filter { cmd ->
            cmd.parameter.contains(query) ||
                cmd.title.lowercase(Locale.US).contains(query) ||
                cmd.actionId.lowercase(Locale.US).contains(query)
        }
    }

    private fun matchPostureCommands(
        query: String,
        postureCommands: List<OmniAction.SystemCommand>
    ): List<OmniAction.SystemCommand> {
        val exact = when (query) {
            "flex", "tabletop", "115", "115°" ->
                postureCommands.filter { it.target == SystemToggleTarget.POSTURE_FLEX }
            "inner", "unfold", "unfolded", "expanded", "tablet" ->
                postureCommands.filter { it.target == SystemToggleTarget.POSTURE_INNER }
            "cover", "fold", "folded", "compact", "outer" ->
                postureCommands.filter { it.target == SystemToggleTarget.POSTURE_COVER }
            "tent", "standby" ->
                postureCommands.filter { it.target == SystemToggleTarget.POSTURE_TENT }
            "auto", "reset", "sensor", "default", "hardware" ->
                postureCommands.filter { it.target == SystemToggleTarget.POSTURE_AUTO }
            else -> emptyList()
        }
        if (exact.isNotEmpty()) return exact
        return postureCommands.filter { cmd ->
            cmd.triggerCommand.lowercase(Locale.US).contains(query) ||
                cmd.title.lowercase(Locale.US).contains(query) ||
                cmd.actionId.lowercase(Locale.US).contains(query)
        }
    }

    /**
     * Parses `termux: <cmd>`, `sh: <cmd>`, `tty: <cmd>`, or `termux-split: <cmd>` into [OmniAction.TermuxCommand].
     */
    fun parseTermuxCommand(rawQuery: String): OmniAction.TermuxCommand? {
        val trimmed = rawQuery.trim()
        val match = TERMUX_COMMAND_REGEX.matchEntire(trimmed) ?: return null
        val prefix = match.groupValues[1].lowercase(Locale.US)
        val rawCmd = match.groupValues[2].trim()
        val splitViaFlag = rawCmd.startsWith("--split ") || rawCmd.startsWith("!split ")
        val openSplitWithVc = prefix.endsWith("-split") || splitViaFlag
        val cleanedCmd = rawCmd
            .removePrefix("--split ")
            .removePrefix("!split ")
            .trim()
        return OmniAction.TermuxCommand(
            shellCommand = cleanedCmd,
            openSplitWithVc = openSplitWithVc
        )
    }

    /**
     * Parses `vc: status`, `vc: approve`, `vc: ship`, `vc: test` into [OmniAction.VcQuickMacro],
     * or general `vc: <prompt>` / `@vc <prompt>` into [OmniAction.SendToVc].
     */
    fun parseVcCommand(rawQuery: String): OmniAction.SendToVc? {
        val trimmed = rawQuery.trim()
        if (trimmed.equals("vc:", ignoreCase = true) || trimmed.equals("@vc", ignoreCase = true)) {
            return OmniAction.SendToVc(prompt = "")
        }
        val match = VC_COMMAND_REGEX.matchEntire(trimmed) ?: return null
        val rawPrompt = match.groupValues[1].trim()

        val macroKeyCandidate = rawPrompt.lowercase(Locale.US)
        val macroPrompt = VC_QUICK_MACROS[macroKeyCandidate]
        if (macroPrompt != null) {
            return OmniAction.VcQuickMacro(
                macroKey = macroKeyCandidate,
                promptText = macroPrompt
            )
        }

        val voice = rawPrompt.startsWith("--voice ") || rawPrompt.startsWith("!voice ")
        val split = rawPrompt.startsWith("--split ") || rawPrompt.startsWith("!split ")
        val cleanedPrompt = rawPrompt
            .removePrefix("--voice ")
            .removePrefix("!voice ")
            .removePrefix("--split ")
            .removePrefix("!split ")
            .trim()
        return OmniAction.SendToVc(
            prompt = cleanedPrompt,
            startVoiceTalk = voice,
            adjacentSplitScreen = split
        )
    }

    /**
     * Parses a VC Quick Macro command (`vc: status`, `vc: approve`, `vc: ship`, `vc: test`) specifically,
     * returning `null` if the query is not one of the quick macros.
     */
    fun parseVcQuickMacro(rawQuery: String): OmniAction.VcQuickMacro? {
        return parseVcCommand(rawQuery) as? OmniAction.VcQuickMacro
    }

    /**
     * Parses `clip: <search>` queries against the provided [ClipboardItem] list.
     */
    fun parseClipboardQuery(
        rawQuery: String,
        items: List<ClipboardItem>
    ): OmniAction.ClipboardMatch? {
        val trimmed = rawQuery.trim()
        if (trimmed.equals("clip", ignoreCase = true) ||
            trimmed.equals("clip:", ignoreCase = true) ||
            trimmed.equals("vault", ignoreCase = true) ||
            trimmed.equals("vault:", ignoreCase = true)
        ) {
            val sorted = items.sortedWith(
                compareByDescending<ClipboardItem> { it.isPinned }.thenByDescending { it.timestampMs }
            )
            return OmniAction.ClipboardMatch(items = sorted, filterQuery = "")
        }

        val match = CLIP_COMMAND_REGEX.matchEntire(trimmed) ?: return null
        val filter = match.groupValues[1].trim()
        val filtered = filterClipboardItems(items, filter)
        return OmniAction.ClipboardMatch(items = filtered, filterQuery = filter)
    }

    internal fun filterClipboardItems(items: List<ClipboardItem>, query: String): List<ClipboardItem> {
        val q = query.trim().lowercase(Locale.US)
        val matching = if (q.isEmpty()) {
            items
        } else {
            items.filter { item ->
                item.text.lowercase(Locale.US).contains(q) ||
                    item.label.lowercase(Locale.US).contains(q) ||
                    item.category.lowercase(Locale.US).contains(q)
            }
        }
        return matching.sortedWith(
            compareByDescending<ClipboardItem> { it.isPinned }.thenByDescending { it.timestampMs }
        )
    }

    /**
     * Evaluates developer expressions & unit conversions:
     * - Hex <-> Decimal <-> Binary (`0xFF`, `255 in hex`, `0b1010`, `#1DC323` RGB breakdown)
     * - Unix Epoch Timestamp <-> ISO-8601 (`1793065200`, `now`)
     * - Android px <-> dp at DPI (`1080px at 450dpi` -> `384dp`, `2448px @ 420dpi`, `1248px to dp`, `2448x1848 @ 420dpi`)
     * - Physical units (`km` <-> `mi`, `kg` <-> `lb`, `c` <-> `f`)
     * - Base64 encode/decode (`b64: ...`, `b64d: ...`)
     */
    fun evaluateDeveloperQuery(
        rawQuery: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): OmniAction.DeveloperEval? {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return null

        evaluateBase64(trimmed)?.let { return it }
        evaluateColorHex(trimmed)?.let { return it }
        evaluateRadixConversion(trimmed)?.let { return it }
        evaluateEpochTimestamp(trimmed, nowEpochMs)?.let { return it }
        evaluateUnitConversion(trimmed)?.let { return it }

        return null
    }

    private fun evaluateBase64(trimmed: String): OmniAction.DeveloperEval? {
        BASE64_DECODE_REGEX.matchEntire(trimmed)?.let { match ->
            val payload = match.groupValues[1].trim()
            if (payload.isEmpty()) return null
            val decoded = tryDecodeBase64Utf8(payload) ?: return null
            return OmniAction.DeveloperEval(
                category = "BASE64 DECODE",
                inputExpression = payload,
                primaryOutput = decoded,
                secondaryDetails = "Decoded UTF-8 (${decoded.length} chars) from Base64",
                copyText = decoded
            )
        }

        BASE64_ENCODE_REGEX.matchEntire(trimmed)?.let { match ->
            val payload = match.groupValues[1].trim()
            if (payload.isEmpty()) return null
            val encoded = Base64.getEncoder().encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
            val decodedCandidate = tryDecodeBase64Utf8(payload)
            if (decodedCandidate != null && payload.length >= 4 && payload.endsWith("=")) {
                return OmniAction.DeveloperEval(
                    category = "BASE64",
                    inputExpression = payload,
                    primaryOutput = decodedCandidate,
                    secondaryDetails = "Decoded: \"$decodedCandidate\" · Encoded: $encoded",
                    copyText = decodedCandidate
                )
            }
            return OmniAction.DeveloperEval(
                category = "BASE64 ENCODE",
                inputExpression = payload,
                primaryOutput = encoded,
                secondaryDetails = if (decodedCandidate != null) {
                    "UTF-8 -> Base64 · (Decodes as: \"$decodedCandidate\")"
                } else {
                    "UTF-8 (${payload.toByteArray(StandardCharsets.UTF_8).size} bytes) -> Base64"
                },
                copyText = encoded
            )
        }

        return null
    }

    private fun tryDecodeBase64Utf8(input: String): String? {
        return try {
            val cleaned = input.trim()
            if (cleaned.isEmpty() || !cleaned.all { it.isLetterOrDigit() || it in "+/=-_" }) return null
            val bytes = try {
                Base64.getDecoder().decode(cleaned)
            } catch (_: IllegalArgumentException) {
                Base64.getUrlDecoder().decode(cleaned)
            }
            if (bytes.isEmpty()) return null
            val text = String(bytes, StandardCharsets.UTF_8)
            if (text.all { it == '\n' || it == '\r' || it == '\t' || (!it.isISOControl() && it.code != 0xFFFD) }) {
                text
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun evaluateColorHex(trimmed: String): OmniAction.DeveloperEval? {
        val match = COLOR_HEX_REGEX.matchEntire(trimmed) ?: return null
        val hexDigits = match.groupValues[1].uppercase(Locale.US)
        val argbLong: Long
        val alpha: Int
        val red: Int
        val green: Int
        val blue: Int

        if (hexDigits.length == 6) {
            alpha = 255
            red = hexDigits.substring(0, 2).toInt(16)
            green = hexDigits.substring(2, 4).toInt(16)
            blue = hexDigits.substring(4, 6).toInt(16)
            argbLong = 0xFF000000L or hexDigits.toLong(16)
        } else {
            alpha = hexDigits.substring(0, 2).toInt(16)
            red = hexDigits.substring(2, 4).toInt(16)
            green = hexDigits.substring(4, 6).toInt(16)
            blue = hexDigits.substring(6, 8).toInt(16)
            argbLong = hexDigits.toLong(16)
        }

        val alphaPct = ((alpha / 255.0) * 100.0).roundToInt()
        val rgbText = if (hexDigits.length == 6) {
            "rgb($red, $green, $blue)"
        } else {
            "rgba($red, $green, $blue, $alphaPct%)"
        }
        val composeHex = "0x${argbLong.toString(16).uppercase(Locale.US).padStart(8, '0')}"

        return OmniAction.DeveloperEval(
            category = "COLOR RGB",
            inputExpression = "#$hexDigits",
            primaryOutput = rgbText,
            secondaryDetails = "R:$red G:$green B:$blue A:$alpha ($alphaPct%) · Compose Color($composeHex)",
            swatchColorHex = argbLong,
            copyText = rgbText
        )
    }

    private fun evaluateRadixConversion(trimmed: String): OmniAction.DeveloperEval? {
        HEX_LITERAL_REGEX.matchEntire(trimmed)?.let { match ->
            val hexPart = match.groupValues[1].uppercase(Locale.US)
            val targetUnit = match.groupValues[2].lowercase(Locale.US)
            val value = hexPart.toULongOrNull(16) ?: return null
            val decStr = value.toString(10)
            val binStr = "0b${value.toString(2)}"
            val hexStr = "0x$hexPart"

            val primary = when {
                targetUnit.startsWith("bin") -> binStr
                else -> decStr
            }
            return OmniAction.DeveloperEval(
                category = "HEX / RADIX",
                inputExpression = hexStr,
                primaryOutput = primary,
                secondaryDetails = "Dec: $decStr · Hex: $hexStr · Bin: $binStr",
                copyText = primary
            )
        }

        BIN_LITERAL_REGEX.matchEntire(trimmed)?.let { match ->
            val binPart = match.groupValues[1]
            val targetUnit = match.groupValues[2].lowercase(Locale.US)
            val value = binPart.toULongOrNull(2) ?: return null
            val decStr = value.toString(10)
            val hexStr = "0x${value.toString(16).uppercase(Locale.US)}"
            val binStr = "0b$binPart"

            val primary = when {
                targetUnit.startsWith("hex") -> hexStr
                else -> decStr
            }
            return OmniAction.DeveloperEval(
                category = "BINARY / RADIX",
                inputExpression = binStr,
                primaryOutput = primary,
                secondaryDetails = "Dec: $decStr · Hex: $hexStr · Bin: $binStr",
                copyText = primary
            )
        }

        DEC_CONVERSION_REGEX.matchEntire(trimmed)?.let { match ->
            val decValue = match.groupValues[1].toLongOrNull() ?: return null
            val target = match.groupValues[2].lowercase(Locale.US)
            val absVal = abs(decValue)
            val sign = if (decValue < 0) "-" else ""
            val hexStr = "${sign}0x${absVal.toString(16).uppercase(Locale.US)}"
            val binStr = "${sign}0b${absVal.toString(2)}"
            val octStr = "${sign}0o${absVal.toString(8)}"

            val primary = when {
                target.startsWith("hex") -> hexStr
                target.startsWith("bin") -> binStr
                target.startsWith("oct") -> octStr
                else -> decValue.toString()
            }
            return OmniAction.DeveloperEval(
                category = "RADIX CONVERT",
                inputExpression = trimmed,
                primaryOutput = primary,
                secondaryDetails = "Hex: $hexStr · Bin: $binStr · Oct: $octStr · Dec: $decValue",
                copyText = primary
            )
        }

        return null
    }

    private fun evaluateEpochTimestamp(
        trimmed: String,
        nowEpochMs: Long
    ): OmniAction.DeveloperEval? {
        val lower = trimmed.lowercase(Locale.US)
        if (lower == "now" || lower == "epoch" || lower == "timestamp" || lower == "unix") {
            val epochSec = nowEpochMs / 1000L
            val instant = Instant.ofEpochMilli(nowEpochMs)
            val isoUtc = ISO_UTC_FORMATTER.format(instant)
            return OmniAction.DeveloperEval(
                category = "UNIX EPOCH",
                inputExpression = trimmed,
                primaryOutput = epochSec.toString(),
                secondaryDetails = "Sec: $epochSec · Ms: $nowEpochMs · UTC: $isoUtc",
                copyText = epochSec.toString()
            )
        }

        val epochDigits = EPOCH_PREFIX_REGEX.matchEntire(trimmed)?.groupValues?.get(1)
            ?: RAW_EPOCH_REGEX.matchEntire(trimmed)?.groupValues?.get(1)

        if (epochDigits != null) {
            val rawNum = epochDigits.toLongOrNull() ?: return null
            val isMillis = epochDigits.length >= 12
            val epochMs = if (isMillis) rawNum else rawNum * 1000L
            val epochSec = if (isMillis) rawNum / 1000L else rawNum
            val instant = Instant.ofEpochMilli(epochMs)
            val isoUtc = ISO_UTC_FORMATTER.format(instant)
            val localStr = READABLE_LOCAL_FORMATTER
                .withZone(ZoneId.systemDefault())
                .format(instant)

            return OmniAction.DeveloperEval(
                category = "EPOCH -> ISO-8601",
                inputExpression = epochDigits,
                primaryOutput = isoUtc,
                secondaryDetails = "Local: $localStr · Sec: $epochSec · Ms: $epochMs",
                copyText = isoUtc
            )
        }

        if (trimmed.length >= 19 && trimmed[4] == '-' && trimmed[7] == '-' && (trimmed[10] == 'T' || trimmed[10] == ' ')) {
            return try {
                val normalizedIso = if (trimmed[10] == ' ') {
                    trimmed.substring(0, 10) + "T" + trimmed.substring(11)
                } else {
                    trimmed
                }
                val withZone = if (normalizedIso.endsWith("Z", ignoreCase = true) ||
                    normalizedIso.drop(10).contains("+") ||
                    normalizedIso.drop(10).contains("-")
                ) {
                    normalizedIso
                } else {
                    "${normalizedIso}Z"
                }
                val instant = Instant.parse(withZone)
                val sec = instant.epochSecond
                val ms = instant.toEpochMilli()
                OmniAction.DeveloperEval(
                    category = "ISO-8601 -> EPOCH",
                    inputExpression = trimmed,
                    primaryOutput = sec.toString(),
                    secondaryDetails = "Epoch Sec: $sec · Epoch Ms: $ms · UTC: ${ISO_UTC_FORMATTER.format(instant)}",
                    copyText = sec.toString()
                )
            } catch (_: Exception) {
                null
            }
        }

        return null
    }

    private fun evaluateUnitConversion(trimmed: String): OmniAction.DeveloperEval? {
        // 1A. 2D Screen Resolution (`2448x1848 @ 420dpi`, `2448x1848 to dp`, `2448x1848px @ 420dpi`)
        RESOLUTION_AT_DPI_REGEX.matchEntire(trimmed)?.let { match ->
            val widthPx = match.groupValues[1].toDoubleOrNull() ?: return null
            val heightPx = match.groupValues[2].toDoubleOrNull() ?: return null
            val explicitDpi = match.groupValues[3].takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val dpi = explicitDpi ?: DEFAULT_Z_FOLD8_DPI
            if (dpi <= 0.0) return null

            val widthDp = widthPx * 160.0 / dpi
            val heightDp = heightPx * 160.0 / dpi
            val smallestDp = min(widthDp, heightDp)
            val densityScale = dpi / 160.0
            val postureLabel = classifyFold8PostureForDp(smallestDp)
            val formattedPrimary = "${formatNumber(widthDp)}x${formatNumber(heightDp)}dp"
            val roundedPair = "${widthDp.roundToInt()}x${heightDp.roundToInt()}dp"

            return OmniAction.DeveloperEval(
                category = "ANDROID DPI",
                inputExpression = trimmed,
                primaryOutput = formattedPrimary,
                secondaryDetails = "${formatNumber(widthPx)}x${formatNumber(heightPx)}px @ ${formatNumber(dpi)}dpi (${formatNumber(densityScale)}x) = $formattedPrimary (~$roundedPair) · Z Fold 8: $postureLabel",
                copyText = formattedPrimary
            )
        }

        // 1B. Single-dimension Android Display Density (`1080px at 450dpi` -> `384dp`, `2448px @ 420dpi`, `1248px to dp`)
        PX_AT_DPI_REGEX.matchEntire(trimmed)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return null
            val unit = match.groupValues[2].lowercase(Locale.US)
            val dpiBeforeTarget = match.groupValues[3].takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val targetUnit = match.groupValues[4].lowercase(Locale.US)
            val dpiAfterTarget = match.groupValues[5].takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            if (dpiBeforeTarget == null && dpiAfterTarget == null && targetUnit.isEmpty()) return null

            val dpi = dpiBeforeTarget ?: dpiAfterTarget ?: DEFAULT_Z_FOLD8_DPI
            if (dpi <= 0.0) return null

            val densityScale = dpi / 160.0
            return if (unit == "px") {
                val dp = value * 160.0 / dpi
                val formattedDp = "${formatNumber(dp)}dp"
                val postureLabel = classifyFold8PostureForDp(dp)
                OmniAction.DeveloperEval(
                    category = "ANDROID DPI",
                    inputExpression = trimmed,
                    primaryOutput = formattedDp,
                    secondaryDetails = "${formatNumber(value)}px @ ${formatNumber(dpi)}dpi (${formatNumber(densityScale)}x density) = $formattedDp (~${dp.roundToInt()}dp) · Z Fold 8: $postureLabel",
                    copyText = formattedDp
                )
            } else {
                val px = value * dpi / 160.0
                val formattedPx = "${formatNumber(px)}px"
                val postureLabel = classifyFold8PostureForDp(value)
                OmniAction.DeveloperEval(
                    category = "ANDROID DPI",
                    inputExpression = trimmed,
                    primaryOutput = formattedPx,
                    secondaryDetails = "${formatNumber(value)}dp @ ${formatNumber(dpi)}dpi (${formatNumber(densityScale)}x density) = $formattedPx · Z Fold 8: $postureLabel",
                    copyText = formattedPx
                )
            }
        }

        // 2. General Physical Unit Conversions (km<->mi, m<->ft, cm<->in, kg<->lb, g<->oz, c<->f)
        val match = UNIT_CONVERSION_REGEX.matchEntire(trimmed) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val fromRaw = match.groupValues[2].lowercase(Locale.US).removePrefix("°")
        val toRaw = match.groupValues[3].lowercase(Locale.US).removePrefix("°")

        return when (fromRaw) {
            "km", "kilometer", "kilometers" -> {
                if (toRaw.isNotEmpty() && toRaw !in setOf("mi", "mile", "miles", "m")) return null
                if (toRaw == "m") {
                    val meters = value * 1000.0
                    val out = "${formatNumber(meters)} m"
                    OmniAction.DeveloperEval("UNIT CONVERT", trimmed, out, "${formatNumber(value)} km = $out")
                } else {
                    val miles = value * 0.6213711922
                    val out = "${formatNumber(miles)} mi"
                    OmniAction.DeveloperEval("UNIT CONVERT", trimmed, out, "${formatNumber(value)} km = $out")
                }
            }
            "mi", "mile", "miles" -> {
                if (toRaw.isNotEmpty() && toRaw !in setOf("km", "kilometer", "kilometers")) return null
                val km = value / 0.6213711922
                val out = "${formatNumber(km)} km"
                OmniAction.DeveloperEval("UNIT CONVERT", trimmed, out, "${formatNumber(value)} mi = $out")
            }
            "kg", "kilogram", "kilograms" -> {
                if (toRaw.isNotEmpty() && toRaw !in setOf("lb", "lbs", "pound", "pounds")) return null
                val lbs = value * 2.2046226218
                val out = "${formatNumber(lbs)} lb"
                OmniAction.DeveloperEval("UNIT CONVERT", trimmed, out, "${formatNumber(value)} kg = $out")
            }
            "lb", "lbs", "pound", "pounds" -> {
                if (toRaw.isNotEmpty() && toRaw !in setOf("kg", "kilogram", "kilograms")) return null
                val kg = value / 2.2046226218
                val out = "${formatNumber(kg)} kg"
                OmniAction.DeveloperEval("UNIT CONVERT", trimmed, out, "${formatNumber(value)} lb = $out")
            }
            "c", "celsius" -> {
                if (toRaw.isNotEmpty() && toRaw !in setOf("f", "fahrenheit")) return null
                val f = (value * 9.0 / 5.0) + 32.0
                val out = "${formatNumber(f)}°F"
                OmniAction.DeveloperEval("TEMP CONVERT", trimmed, out, "${formatNumber(value)}°C = $out")
            }
            "f", "fahrenheit" -> {
                if (toRaw.isNotEmpty() && toRaw !in setOf("c", "celsius")) return null
                val c = (value - 32.0) * 5.0 / 9.0
                val out = "${formatNumber(c)}°C"
                OmniAction.DeveloperEval("TEMP CONVERT", trimmed, out, "${formatNumber(value)}°F = $out")
            }
            "m", "meter", "meters" -> {
                if (toRaw.isNotEmpty() && toRaw !in setOf("ft", "feet", "foot")) return null
                val ft = value * 3.280839895
                val out = "${formatNumber(ft)} ft"
                OmniAction.DeveloperEval("UNIT CONVERT", trimmed, out, "${formatNumber(value)} m = $out")
            }
            "ft", "feet", "foot" -> {
                if (toRaw.isNotEmpty() && toRaw !in setOf("m", "meter", "meters")) return null
                val m = value / 3.280839895
                val out = "${formatNumber(m)} m"
                OmniAction.DeveloperEval("UNIT CONVERT", trimmed, out, "${formatNumber(value)} ft = $out")
            }
            "cm" -> {
                if (toRaw.isNotEmpty() && toRaw !in setOf("in", "inch", "inches")) return null
                val inches = value / 2.54
                val out = "${formatNumber(inches)} in"
                OmniAction.DeveloperEval("UNIT CONVERT", trimmed, out, "${formatNumber(value)} cm = $out")
            }
            "in", "inch", "inches" -> {
                if (toRaw.isNotEmpty() && toRaw !in setOf("cm")) return null
                val cm = value * 2.54
                val out = "${formatNumber(cm)} cm"
                OmniAction.DeveloperEval("UNIT CONVERT", trimmed, out, "${formatNumber(value)} in = $out")
            }
            else -> null
        }
    }

    private fun classifyFold8PostureForDp(dpValue: Double): String {
        return when {
            dpValue >= 840.0 -> "EXPANDED (Inner Landscape / DeX >= 840dp)"
            dpValue >= 600.0 -> "EXPANDED (Unfolded Inner Canvas >= 600dp)"
            else -> "COMPACT (Cover Screen < 600dp)"
        }
    }

    internal fun formatNumber(value: Double): String {
        val roundedLong = value.roundToInt().toLong()
        if (abs(value - roundedLong.toDouble()) < 1e-6) {
            return roundedLong.toString()
        }
        return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }

    /**
     * Toggles or sets the device's rear LED camera torch using Android's [CameraManager].
     */
    fun setHardwareTorch(context: Context, desiredState: Boolean? = null): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            val targetCameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val isBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                hasFlash && isBack
            } ?: cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false

            val nextState = desiredState ?: !torchEnabledState
            cm.setTorchMode(targetCameraId, nextState)
            torchEnabledState = nextState
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes an [OmniAction] on the device using [SystemTweaker], [ClipboardVault], or launcher callbacks.
     */
    fun execute(
        context: Context,
        action: OmniAction,
        clipboardVault: ClipboardVault? = null,
        onToggleFloatingHud: (() -> Unit)? = null,
        onToggleThumbArc: (() -> Unit)? = null,
        onStatusMessage: ((String) -> Unit)? = null,
        onPostureCommand: ((String) -> Unit)? = null,
        onToggleTorch: ((Boolean?) -> Unit)? = null,
        onToggleCoverPresentation: (() -> Unit)? = null,
        onSwitchWorkspaceProfile: ((String) -> Unit)? = null,
        onToggleHingeHaptics: (() -> Unit)? = null,
        onSetFoldCloseAction: ((String) -> Unit)? = null
    ): Boolean {
        return when (action) {
            is OmniAction.LaunchAdHocSplit -> {
                val launched = SystemTweaker.launchSplitScreenPair(
                    context = context,
                    primaryPackage = action.primaryApp.packageName,
                    secondaryPackage = action.secondaryApp.packageName
                )
                onStatusMessage?.invoke(
                    if (launched) "Launched ${action.primaryApp.label} + ${action.secondaryApp.label} in Split-Screen"
                    else "Unable to launch split-screen pair"
                )
                launched
            }
            is OmniAction.SystemCommand -> {
                when (action.target) {
                    SystemToggleTarget.DEX_GBOARD -> {
                        val ok = SystemTweaker.enforcePreferredIme(context)
                        onStatusMessage?.invoke(
                            if (ok) "Gboard enforced for Samsung DeX & Default IME"
                            else "Requires WRITE_SECURE_SETTINGS via ADB"
                        )
                        ok
                    }
                    SystemToggleTarget.ROTATION_ON -> {
                        val ok = SystemTweaker.setLockScreenRotation(context, true)
                        onStatusMessage?.invoke(
                            if (ok) "Lock & Home landscape rotation enabled"
                            else "Requires WRITE_SECURE_SETTINGS via ADB"
                        )
                        ok
                    }
                    SystemToggleTarget.ROTATION_OFF -> {
                        val ok = SystemTweaker.setLockScreenRotation(context, false)
                        onStatusMessage?.invoke(
                            if (ok) "Lock & Home landscape rotation disabled"
                            else "Requires WRITE_SECURE_SETTINGS via ADB"
                        )
                        ok
                    }
                    SystemToggleTarget.LOCK_SCREEN -> {
                        val ok = SystemTweaker.lockScreenNow()
                        if (!ok) {
                            SystemTweaker.openAccessibilitySettings(context)
                            onStatusMessage?.invoke("Enable Fold8 Accessibility Service to lock screen")
                        }
                        ok
                    }
                    SystemToggleTarget.FLOATING_HUD -> {
                        onToggleFloatingHud?.invoke()
                        onStatusMessage?.invoke("Toggled Floating Top-Right Micro-HUD Pill")
                        true
                    }
                    SystemToggleTarget.THUMB_ARC -> {
                        onToggleThumbArc?.invoke()
                        onStatusMessage?.invoke("Toggled One-Handed Radial Thumb Arc")
                        true
                    }
                    SystemToggleTarget.NOTIFICATIONS -> {
                        SystemTweaker.expandNotificationsPanel(context)
                    }
                    SystemToggleTarget.POSTURE_FLEX,
                    SystemToggleTarget.POSTURE_INNER,
                    SystemToggleTarget.POSTURE_COVER,
                    SystemToggleTarget.POSTURE_TENT,
                    SystemToggleTarget.POSTURE_AUTO -> {
                        onPostureCommand?.invoke(action.actionId)
                        onStatusMessage?.invoke("${action.title} (${action.actionId})")
                        true
                    }
                    SystemToggleTarget.TOGGLE_TORCH -> {
                        val desired = action.desiredTorchState
                        onToggleTorch?.invoke(desired)
                        val ok = setHardwareTorch(context, desired)
                        onStatusMessage?.invoke(
                            when (desired) {
                                true -> "Hardware Torch ON"
                                false -> "Hardware Torch OFF"
                                null -> if (torchEnabledState) "Hardware Torch ON" else "Hardware Torch OFF"
                            }
                        )
                        ok || onToggleTorch != null
                    }
                    SystemToggleTarget.TOGGLE_COVER_PRESENTATION -> {
                        onToggleCoverPresentation?.invoke()
                        onPostureCommand?.invoke(action.actionId)
                        onStatusMessage?.invoke("Toggled Z Fold 8 Dual-Screen Cover Presentation HUD")
                        true
                    }
                    SystemToggleTarget.SWITCH_WORKSPACE_PROFILE -> {
                        val profileKey = action.profileKey.ifBlank { "all" }
                        onSwitchWorkspaceProfile?.invoke(profileKey)
                        onPostureCommand?.invoke(action.actionId)
                        onStatusMessage?.invoke("Switched Workspace Profile: ${profileKey.uppercase(Locale.US)}")
                        true
                    }
                    SystemToggleTarget.TOGGLE_HINGE_HAPTICS -> {
                        onToggleHingeHaptics?.invoke()
                        onPostureCommand?.invoke(action.actionId)
                        onStatusMessage?.invoke("Toggled Z Fold 8 Hinge Angle Haptic Detents")
                        true
                    }
                    SystemToggleTarget.SET_FOLD_CLOSE_ACTION -> {
                        val mode = action.foldCloseMode.ifBlank { "stay" }
                        if (mode == "stay") {
                            SystemTweaker.setFoldCloseStayAwake(context, true)
                        } else if (mode == "lock") {
                            SystemTweaker.setFoldCloseStayAwake(context, false)
                        }
                        onSetFoldCloseAction?.invoke(mode)
                        onPostureCommand?.invoke(action.actionId)
                        onStatusMessage?.invoke("Fold-Close Action set to: ${mode.uppercase(Locale.US)}")
                        true
                    }
                }
            }
            is OmniAction.TermuxCommand -> {
                if (action.shellCommand.isNotBlank()) {
                    ClipboardVault.copyToSystemClipboard(context, action.shellCommand, "Termux Command")
                    clipboardVault?.addOrBumpClip(
                        text = action.shellCommand,
                        label = "Termux Shell Snippet",
                        category = "Termux"
                    )
                }
                if (action.openSplitWithVc) {
                    val vcPkg = SystemTweaker.findInstalledVcPackage(context) ?: VC_PROD_PACKAGE_Z_FOLD8
                    val launched = SystemTweaker.launchSplitScreenPair(
                        context = context,
                        primaryPackage = vcPkg,
                        secondaryPackage = TERMUX_PACKAGE
                    )
                    onStatusMessage?.invoke(
                        if (launched) "Copied command & launched VC + Termux in Split-Screen"
                        else "Copied command to clipboard"
                    )
                    launched
                } else {
                    val termuxIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (termuxIntent != null) {
                        try {
                            context.startActivity(termuxIntent)
                            onStatusMessage?.invoke("Copied command & opened Termux")
                            true
                        } catch (_: Exception) {
                            onStatusMessage?.invoke("Copied command to clipboard")
                            false
                        }
                    } else {
                        onStatusMessage?.invoke("Copied command to clipboard (Termux not installed)")
                        false
                    }
                }
            }
            is OmniAction.SendToVc -> {
                if (action.prompt.isNotBlank()) {
                    ClipboardVault.copyToSystemClipboard(context, action.prompt, "VC Prompt")
                    clipboardVault?.addOrBumpClip(
                        text = action.prompt,
                        label = if (action.isQuickMacro) "VC Macro (${action.macroKey})" else "VC Dispatch Prompt",
                        category = "VC Prompt"
                    )
                }
                val vcPkg = SystemTweaker.findInstalledVcPackage(context)
                if (vcPkg != null) {
                    val intent = context.packageManager.getLaunchIntentForPackage(vcPkg)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (action.adjacentSplitScreen) {
                            addFlags(
                                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                            )
                        }
                        if (action.startVoiceTalk) {
                            this.action = SystemTweaker.VC_TALK_ACTION
                        }
                        if (action.prompt.isNotBlank()) {
                            putExtra(Intent.EXTRA_TEXT, action.prompt)
                            putExtra("prompt", action.prompt)
                            putExtra("initialPrompt", action.prompt)
                        }
                    }
                    if (intent != null) {
                        return try {
                            context.startActivity(intent)
                            onStatusMessage?.invoke("Dispatched to VC (prompt copied to clipboard)")
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
                val launched = SystemTweaker.launchVc(
                    context = context,
                    startVoiceTalk = action.startVoiceTalk,
                    adjacentSplitScreen = action.adjacentSplitScreen
                )
                onStatusMessage?.invoke("Opened VC Web Console (prompt copied to clipboard)")
                launched
            }
            is OmniAction.DeveloperEval -> {
                ClipboardVault.copyToSystemClipboard(context, action.copyText, action.category)
                clipboardVault?.addOrBumpClip(
                    text = action.copyText,
                    label = "${action.category}: ${action.inputExpression}",
                    category = "Dev Eval"
                )
                onStatusMessage?.invoke("Copied ${action.copyText} to clipboard")
                true
            }
            is OmniAction.ClipboardMatch -> {
                val top = action.items.firstOrNull() ?: return false
                ClipboardVault.copyToSystemClipboard(context, top.text, top.label.ifBlank { "Vault Snippet" })
                onStatusMessage?.invoke("Copied snippet to clipboard")
                true
            }
        }
    }
}

/**
 * Deduplicated Clipboard History & Pinned Developer Snippets Vault.
 *
 * Captures system clipboard text when the launcher gains focus (`captureFromSystemClipboard`),
 * persists items to `SharedPreferences` when a [Context] is provided, and works in-memory for
 * pure JVM unit tests when `context = null`.
 */
class ClipboardVault(
    private val context: Context? = null,
    private val maxHistorySize: Int = 32
) {
    private val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(loadInitialItems())
    val items: StateFlow<List<ClipboardItem>> = _items.asStateFlow()

    private fun loadInitialItems(): List<ClipboardItem> {
        val p = prefs ?: return defaultPinnedSnippets()
        val rawJson = p.getString(KEY_VAULT_ITEMS_JSON, null)
        if (rawJson.isNullOrBlank()) {
            return defaultPinnedSnippets()
        }
        return try {
            val arr = JSONArray(rawJson)
            val parsed = mutableListOf<ClipboardItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val text = obj.optString("text", "").trim()
                if (text.isEmpty()) continue
                parsed.add(
                    ClipboardItem(
                        id = obj.optString("id", "clip_$i"),
                        text = text,
                        label = obj.optString("label", ""),
                        timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                        isPinned = obj.optBoolean("isPinned", false),
                        category = obj.optString("category", "Clipboard")
                    )
                )
            }
            if (parsed.none { it.isPinned }) {
                (defaultPinnedSnippets() + parsed).distinctBy { it.text }.take(maxHistorySize)
            } else {
                sortVaultItems(parsed).take(maxHistorySize)
            }
        } catch (_: Exception) {
            defaultPinnedSnippets()
        }
    }

    private fun persistItems(list: List<ClipboardItem>) {
        val p = prefs ?: return
        try {
            val arr = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("label", item.label)
                    put("timestampMs", item.timestampMs)
                    put("isPinned", item.isPinned)
                    put("category", item.category)
                }
                arr.put(obj)
            }
            p.edit().putString(KEY_VAULT_ITEMS_JSON, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Reads the current primary clip from Android's [ClipboardManager] when the launcher window
     * gains focus and records it into the deduplicated vault.
     */
    fun captureFromSystemClipboard(targetContext: Context? = context): ClipboardItem? {
        val ctx = targetContext ?: return null
        return try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
            if (!cm.hasPrimaryClip()) return null
            val clip: ClipData = cm.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            val rawText = clip.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim() ?: return null
            if (rawText.isEmpty()) return null
            val label = clip.description?.label?.toString()?.takeIf { it.isNotBlank() } ?: "Captured Clip"
            addOrBumpClip(
                text = rawText,
                label = label,
                isPinned = false,
                category = "Clipboard"
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Inserts a new clipboard snippet or bumps an existing matching snippet to the top (deduplicated).
     */
    fun addOrBumpClip(
        text: String,
        label: String = "",
        isPinned: Boolean = false,
        category: String = "Clipboard",
        timestampMs: Long = System.currentTimeMillis()
    ): ClipboardItem? {
        val normalized = text.trim()
        if (normalized.isEmpty()) return null

        val current = _items.value
        val existing = current.firstOrNull { it.text == normalized }
        val updatedItem = if (existing != null) {
            existing.copy(
                label = if (label.isNotBlank()) label else existing.label,
                timestampMs = timestampMs,
                isPinned = existing.isPinned || isPinned,
                category = if (existing.isPinned) existing.category else category
            )
        } else {
            ClipboardItem(
                id = "clip_${timestampMs}_${normalized.hashCode().toUInt().toString(16)}",
                text = normalized,
                label = label.ifBlank { inferLabelForSnippet(normalized) },
                timestampMs = timestampMs,
                isPinned = isPinned,
                category = category
            )
        }

        val remaining = current.filterNot { it.text == normalized }
        val merged = sortVaultItems(listOf(updatedItem) + remaining).take(maxHistorySize)
        _items.value = merged
        persistItems(merged)
        return updatedItem
    }

    /**
     * Toggles the pinned status of a snippet in the vault.
     */
    fun togglePin(itemId: String) {
        val updated = sortVaultItems(
            _items.value.map { item ->
                if (item.id == itemId) item.copy(isPinned = !item.isPinned) else item
            }
        )
        _items.value = updated
        persistItems(updated)
    }

    /**
     * Removes a snippet by ID from the vault.
     */
    fun removeClip(itemId: String) {
        val updated = _items.value.filterNot { it.id == itemId }
        _items.value = updated
        persistItems(updated)
    }

    /**
     * Clears all unpinned clipboard history items while keeping pinned developer snippets intact.
     */
    fun clearUnpinned() {
        val pinnedOnly = _items.value.filter { it.isPinned }
        _items.value = pinnedOnly
        persistItems(pinnedOnly)
    }

    /**
     * Filters vault items by `query` (pinned items ranked first, then newest first).
     */
    fun search(query: String): List<ClipboardItem> {
        return OmniCommandEngine.filterClipboardItems(_items.value, query)
    }

    private fun sortVaultItems(list: List<ClipboardItem>): List<ClipboardItem> {
        return list.sortedWith(
            compareByDescending<ClipboardItem> { it.isPinned }.thenByDescending { it.timestampMs }
        )
    }

    private fun inferLabelForSnippet(text: String): String {
        return when {
            text.startsWith("adb ") -> "ADB Shell Command"
            text.startsWith("http://") || text.startsWith("https://") -> "URL Link"
            text.startsWith("vc:") || text.startsWith("@vc") -> "VC Prompt"
            text.startsWith("#") && text.length in 7..9 -> "Hex Color"
            else -> "Clipboard Snippet"
        }
    }

    companion object {
        private const val PREFS_NAME = "fold_launcher_clipboard_vault"
        private const val KEY_VAULT_ITEMS_JSON = "vault_items_json"

        /**
         * Default pinned developer & Z Fold 8 (`SM-F971U1`) power-user snippets preloaded in the Vault.
         */
        fun defaultPinnedSnippets(): List<ClipboardItem> = listOf(
            ClipboardItem(
                id = "pin_adb_grant",
                text = "adb shell pm grant com.xsun.foldlauncher android.permission.WRITE_SECURE_SETTINGS",
                label = "Grant WRITE_SECURE_SETTINGS (ADB)",
                timestampMs = 1793000004000L,
                isPinned = true,
                category = "ADB Setup"
            ),
            ClipboardItem(
                id = "pin_adb_wireless",
                text = "adb connect 100.101.43.86:42595",
                label = "Z Fold 8 (SM-F971U1) Wireless ADB",
                timestampMs = 1793000003000L,
                isPinned = true,
                category = "Tailscale"
            ),
            ClipboardItem(
                id = "pin_vc_host",
                text = SystemTweaker.DEFAULT_VC_HOST_URL,
                label = "VC Agent Host URL (Tailscale)",
                timestampMs = 1793000002000L,
                isPinned = true,
                category = "VC Host"
            ),
            ClipboardItem(
                id = "pin_dex_gboard",
                text = "adb shell settings put secure default_input_method ${SystemTweaker.GBOARD_IME_ID}",
                label = "Force Gboard IME in Samsung DeX",
                timestampMs = 1793000001000L,
                isPinned = true,
                category = "DeX Tweak"
            )
        )

        /**
         * Copies text to Android's system clipboard safely.
         */
        fun copyToSystemClipboard(
            context: Context,
            text: String,
            label: String = "FoldLauncher Omni"
        ): Boolean {
            return try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
                cm.setPrimaryClip(ClipData.newPlainText(label, text))
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}

/**
 * Renders Omni-Command Palette cards (Ad-Hoc Split-Screen, `>` System/DeX/Torch/Dual/Profile/Haptics/Fold/Posture Tweaker commands,
 * `termux:` / `sh:` Shell Dispatcher, `vc:` Agent Dispatch & Quick Macros, Developer/Unit Conversions, and `clip:` Clipboard Vault results)
 * for Spotlight Search and the Home Dashboard.
 */
@Composable
fun OmniCommandResultsCard(
    query: String,
    installedApps: List<AppEntry>,
    clipboardItems: List<ClipboardItem> = ClipboardVault.defaultPinnedSnippets(),
    onExecuteAction: (OmniAction) -> Unit = {},
    onSelectQuerySuggestion: (String) -> Unit = {},
    onTogglePinClip: (String) -> Unit = {},
    onCopyClip: (ClipboardItem) -> Unit = {},
    showQuickCommandPillsWhenIdle: Boolean = true,
    modifier: Modifier = Modifier
) {
    val actions = remember(query, installedApps, clipboardItems) {
        OmniCommandEngine.evaluate(
            rawQuery = query,
            installedApps = installedApps,
            clipboardItems = clipboardItems
        )
    }

    OmniCommandResultsCard(
        actions = actions,
        query = query,
        clipboardItems = clipboardItems,
        onExecuteAction = onExecuteAction,
        onSelectQuerySuggestion = onSelectQuerySuggestion,
        onTogglePinClip = onTogglePinClip,
        onCopyClip = onCopyClip,
        showQuickCommandPillsWhenIdle = showQuickCommandPillsWhenIdle,
        modifier = modifier
    )
}

/**
 * Overload of [OmniCommandResultsCard] that accepts pre-evaluated [OmniAction] items directly.
 */
@Composable
fun OmniCommandResultsCard(
    actions: List<OmniAction>,
    query: String = "",
    clipboardItems: List<ClipboardItem> = ClipboardVault.defaultPinnedSnippets(),
    onExecuteAction: (OmniAction) -> Unit = {},
    onSelectQuerySuggestion: (String) -> Unit = {},
    onTogglePinClip: (String) -> Unit = {},
    onCopyClip: (ClipboardItem) -> Unit = {},
    showQuickCommandPillsWhenIdle: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val composeClipboard = LocalClipboardManager.current

    if (actions.isEmpty() && !showQuickCommandPillsWhenIdle) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (actions.isEmpty() && query.isBlank() && showQuickCommandPillsWhenIdle) {
            OmniQuickSyntaxStrip(
                clipboardCount = clipboardItems.size,
                onSelectQuerySuggestion = onSelectQuerySuggestion
            )
        }

        actions.forEach { action ->
            when (action) {
                is OmniAction.LaunchAdHocSplit -> {
                    AdHocSplitActionCard(
                        action = action,
                        onClick = {
                            OmniCommandEngine.execute(context, action)
                            onExecuteAction(action)
                        }
                    )
                }
                is OmniAction.SystemCommand -> {
                    SystemCommandActionRow(
                        action = action,
                        onClick = {
                            OmniCommandEngine.execute(context, action)
                            onExecuteAction(action)
                        }
                    )
                }
                is OmniAction.TermuxCommand -> {
                    TermuxCommandActionCard(
                        action = action,
                        onClick = {
                            if (action.shellCommand.isNotBlank()) {
                                composeClipboard.setText(AnnotatedString(action.shellCommand))
                            }
                            OmniCommandEngine.execute(context, action)
                            onExecuteAction(action)
                        }
                    )
                }
                is OmniAction.SendToVc -> {
                    VcDispatchActionCard(
                        action = action,
                        onClick = {
                            if (action.prompt.isNotBlank()) {
                                composeClipboard.setText(AnnotatedString(action.prompt))
                            }
                            OmniCommandEngine.execute(context, action)
                            onExecuteAction(action)
                        }
                    )
                }
                is OmniAction.DeveloperEval -> {
                    DeveloperEvalActionCard(
                        action = action,
                        onClick = {
                            composeClipboard.setText(AnnotatedString(action.copyText))
                            OmniCommandEngine.execute(context, action)
                            onExecuteAction(action)
                        }
                    )
                }
                is OmniAction.ClipboardMatch -> {
                    ClipboardVaultMatchCard(
                        action = action,
                        onCopyItem = { item ->
                            composeClipboard.setText(AnnotatedString(item.text))
                            ClipboardVault.copyToSystemClipboard(context, item.text, item.label)
                            onCopyClip(item)
                            onExecuteAction(action)
                        },
                        onTogglePin = onTogglePinClip
                    )
                }
            }
        }
    }
}

@Composable
private fun OmniQuickSyntaxStrip(
    clipboardCount: Int,
    onSelectQuerySuggestion: (String) -> Unit
) {
    val suggestions = remember(clipboardCount) {
        listOf(
            "split: vc + compare" to "⚡ vc + compare",
            "vc: ship" to "🚀 vc: ship",
            "termux: " to "💻 termux: cmd",
            "> haptics" to "📳 > haptics",
            "> fold voice" to "🎙 > fold voice",
            "> torch" to "🔦 > torch",
            "> dual" to "🪞 > dual HUD",
            "> profile vc" to "🗂 > profile vc",
            "clip: " to "📋 clip: vault ($clipboardCount)"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        suggestions.forEach { (prefix, label) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(FoldLauncherColors.GlassSurfaceElevated)
                    .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(14.dp))
                    .clickable { onSelectQuerySuggestion(prefix) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FoldLauncherColors.CyanBright
                )
            }
        }
    }
}

@Composable
private fun AdHocSplitActionCard(
    action: OmniAction.LaunchAdHocSplit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        FoldLauncherColors.CyanGlow,
                        FoldLauncherColors.GlassSurfaceHighlight
                    )
                )
            )
            .border(1.5.dp, FoldLauncherColors.ElectricCyan, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OmniBadgePill(text = action.badge, color = FoldLauncherColors.ElectricCyan)
                Text(
                    text = "INSTANT AD-HOC MULTI-WINDOW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.CyanBright
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${action.primaryApp.label}  ⚡  ${action.secondaryApp.label}",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${action.primaryApp.packageName} + ${action.secondaryApp.packageName}",
                fontSize = 11.sp,
                color = FoldLauncherColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(FoldLauncherColors.ElectricCyan)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Split ↵",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF02131D)
            )
        }
    }
}

@Composable
private fun SystemCommandActionRow(
    action: OmniAction.SystemCommand,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FoldLauncherColors.GlassSurfaceElevated)
            .border(1.dp, FoldLauncherColors.GlassBorderStrong, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OmniBadgePill(text = action.badge, color = FoldLauncherColors.EmeraldSuccess)
                Text(
                    text = action.triggerCommand,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.CyanBright
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = action.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.TextPrimary
            )
            Text(
                text = action.subtitle,
                fontSize = 11.sp,
                color = FoldLauncherColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Run ↵",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = FoldLauncherColors.EmeraldSuccess
        )
    }
}

@Composable
private fun TermuxCommandActionCard(
    action: OmniAction.TermuxCommand,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        FoldLauncherColors.EmeraldSuccess.copy(alpha = 0.24f),
                        FoldLauncherColors.GlassSurfaceElevated
                    )
                )
            )
            .border(1.5.dp, FoldLauncherColors.EmeraldSuccess, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OmniBadgePill(text = action.badge, color = FoldLauncherColors.EmeraldSuccess)
                Text(
                    text = if (action.openSplitWithVc) "VC + TERMUX SPLIT DISPATCH" else "TERMUX SHELL SNIPPET",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.CyanBright
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (action.shellCommand.isBlank()) "Type shell command after termux: …" else "$ ${action.shellCommand}",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = action.subtitle,
                fontSize = 11.sp,
                color = FoldLauncherColors.TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(FoldLauncherColors.EmeraldSuccess)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (action.openSplitWithVc) "Split ↵" else "Termux ↗",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF02131D)
            )
        }
    }
}

@Composable
private fun VcDispatchActionCard(
    action: OmniAction.SendToVc,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        FoldLauncherColors.IndigoAccent.copy(alpha = 0.28f),
                        FoldLauncherColors.GlassSurfaceElevated
                    )
                )
            )
            .border(1.5.dp, FoldLauncherColors.IndigoAccent, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OmniBadgePill(text = action.badge, color = FoldLauncherColors.VioletAccent)
                Text(
                    text = if (action.isQuickMacro) "INSTANT VC MACRO DISPATCH" else "DIRECT VC AGENT DISPATCH",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.CyanBright
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (action.prompt.isBlank()) "Type prompt after vc: to dispatch…" else action.prompt,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = action.subtitle,
                fontSize = 11.sp,
                color = FoldLauncherColors.TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(FoldLauncherColors.IndigoAccent)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Send ↗",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoldLauncherColors.TextPrimary
            )
        }
    }
}

@Composable
private fun DeveloperEvalActionCard(
    action: OmniAction.DeveloperEval,
    onClick: () -> Unit
) {
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
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (action.swatchColorHex != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(action.swatchColorHex))
                        .border(1.5.dp, FoldLauncherColors.TextPrimary, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OmniBadgePill(text = action.badge, color = FoldLauncherColors.ElectricCyan)
                    Text(
                        text = "TAP TO COPY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoldLauncherColors.CyanBright
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = action.primaryOutput,
                    fontSize = 19.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = action.secondaryDetails,
                    fontSize = 11.sp,
                    color = FoldLauncherColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Copy",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = FoldLauncherColors.ElectricCyan
        )
    }
}

@Composable
private fun ClipboardVaultMatchCard(
    action: OmniAction.ClipboardMatch,
    onCopyItem: (ClipboardItem) -> Unit,
    onTogglePin: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FoldLauncherColors.GlassSurfaceElevated)
            .border(1.dp, FoldLauncherColors.GlassBorderStrong, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OmniBadgePill(text = action.badge, color = FoldLauncherColors.AmberWarning)
                Text(
                    text = action.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoldLauncherColors.TextPrimary
                )
            }
            Text(
                text = "1-Tap Copy",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = FoldLauncherColors.CyanBright
            )
        }

        if (action.items.isEmpty()) {
            Text(
                text = "No clipboard snippets matching \"${action.filterQuery}\"",
                fontSize = 12.sp,
                color = FoldLauncherColors.TextSecondary
            )
        } else {
            action.items.take(5).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FoldLauncherColors.GlassSurface)
                        .border(
                            width = 1.dp,
                            color = if (item.isPinned) FoldLauncherColors.AmberWarning.copy(alpha = 0.55f)
                            else FoldLauncherColors.GlassBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onCopyItem(item) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (item.isPinned) {
                                Text(
                                    text = "📌 PINNED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FoldLauncherColors.AmberWarning
                                )
                            }
                            if (item.label.isNotBlank()) {
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FoldLauncherColors.CyanBright,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = item.text,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = FoldLauncherColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (item.isPinned) "Unpin" else "Pin",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoldLauncherColors.TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(FoldLauncherColors.GlassSurfaceElevated)
                                .clickable { onTogglePin(item.id) }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                        Text(
                            text = "Copy",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FoldLauncherColors.ElectricCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OmniBadgePill(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

/**
 * Compact Home Dashboard card for the Clipboard History & Pinned Snippets Vault
 * with 1-tap copy, pin/unpin toggle, and quick Spotlight `clip:` filter button.
 */
@Composable
fun ClipboardVaultDashboardCard(
    items: List<ClipboardItem>,
    onCopyItem: (ClipboardItem) -> Unit = {},
    onTogglePin: (String) -> Unit = {},
    onClearUnpinned: () -> Unit = {},
    onOpenVaultSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val composeClipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(FoldLauncherColors.GlassSurfaceElevated)
            .border(1.dp, FoldLauncherColors.GlassBorder, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OmniBadgePill(text = "CLIP VAULT", color = FoldLauncherColors.AmberWarning)
                Text(
                    text = "Clipboard & Pinned Snippets (${items.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.TextPrimary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (items.any { !it.isPinned }) {
                    Text(
                        text = "Clear Recent",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FoldLauncherColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(FoldLauncherColors.GlassSurface)
                            .clickable(onClick = onClearUnpinned)
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "clip: 🔎",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = FoldLauncherColors.ElectricCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(FoldLauncherColors.GlassSurface)
                        .border(1.dp, FoldLauncherColors.GlassBorderStrong, RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenVaultSearch)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        items.take(4).forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FoldLauncherColors.GlassSurface)
                    .border(
                        width = 1.dp,
                        color = if (item.isPinned) FoldLauncherColors.AmberWarning.copy(alpha = 0.45f)
                        else FoldLauncherColors.GlassBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        composeClipboard.setText(AnnotatedString(item.text))
                        ClipboardVault.copyToSystemClipboard(context, item.text, item.label)
                        onCopyItem(item)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.isPinned) {
                            Text(
                                text = "📌",
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = item.label.ifBlank { item.category },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoldLauncherColors.CyanBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = item.text,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = FoldLauncherColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (item.isPinned) "Unpin" else "Pin",
                        fontSize = 10.sp,
                        color = FoldLauncherColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(FoldLauncherColors.GlassSurfaceElevated)
                            .clickable { onTogglePin(item.id) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                    Text(
                        text = "Copy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoldLauncherColors.ElectricCyan
                    )
                }
            }
        }
    }
}
