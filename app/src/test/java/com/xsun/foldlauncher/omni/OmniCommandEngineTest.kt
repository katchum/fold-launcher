package com.xsun.foldlauncher.omni

import com.xsun.foldlauncher.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniCommandEngineTest {

    private val sampleApps = listOf(
        AppEntry(
            label = "YouTube",
            packageName = "com.google.android.youtube",
            activityName = ".HomeActivity",
            launchCount = 20
        ),
        AppEntry(
            label = "Chrome",
            packageName = "com.android.chrome",
            activityName = "com.google.android.apps.chrome.Main",
            launchCount = 25
        ),
        AppEntry(
            label = "Settings",
            packageName = "com.android.settings",
            activityName = ".Settings",
            launchCount = 10
        ),
        AppEntry(
            label = "Google Maps",
            packageName = "com.google.android.apps.maps",
            activityName = "com.google.android.maps.MapsActivity",
            launchCount = 12
        ),
        AppEntry(
            label = "Termux",
            packageName = "com.termux",
            activityName = ".app.TermuxActivity",
            launchCount = 18
        )
    )

    @Test
    fun parseSplitCommand_plusSyntaxAndAcronyms_resolvesPrimaryAndSecondaryApps() {
        val action = OmniCommandEngine.parseSplitCommand("split: yt + chrome", sampleApps)
        assertNotNull(action)
        assertEquals("com.google.android.youtube", action!!.primaryApp.packageName)
        assertEquals("com.android.chrome", action.secondaryApp.packageName)
        assertEquals("YouTube + Chrome", action.title)
        assertEquals("SPLIT 50/50", action.badge)
    }

    @Test
    fun parseSplitCommand_pipeAndNaturalKeywords_resolvesAppsCleanly() {
        val pipeSplit = OmniCommandEngine.parseSplitCommand("youtube | settings", sampleApps)
        assertNotNull(pipeSplit)
        assertEquals("com.google.android.youtube", pipeSplit!!.primaryApp.packageName)
        assertEquals("com.android.settings", pipeSplit.secondaryApp.packageName)

        val withSplit = OmniCommandEngine.parseSplitCommand("split termux with gm", sampleApps)
        assertNotNull(withSplit)
        assertEquals("com.termux", withSplit!!.primaryApp.packageName)
        assertEquals("com.google.android.apps.maps", withSplit.secondaryApp.packageName)
    }

    @Test
    fun parseSplitCommand_invalidOrSingleApp_returnsNull() {
        assertNull(OmniCommandEngine.parseSplitCommand("split: youtube", sampleApps))
        assertNull(OmniCommandEngine.parseSplitCommand("youtube", sampleApps))
        assertNull(OmniCommandEngine.parseSplitCommand("split: yt + chrome", sampleApps.take(1)))
    }

    @Test
    fun parseSystemCommands_dexGboardAndRotationCommands_returnExpectedTargets() {
        val dexCmds = OmniCommandEngine.parseSystemCommands("> dex gboard")
        assertEquals(1, dexCmds.size)
        assertEquals(SystemToggleTarget.DEX_GBOARD, dexCmds.first().target)

        val rotOnCmds = OmniCommandEngine.parseSystemCommands("> rotation on")
        assertEquals(1, rotOnCmds.size)
        assertEquals(SystemToggleTarget.ROTATION_ON, rotOnCmds.first().target)

        val rotOffCmds = OmniCommandEngine.parseSystemCommands("> rotation off")
        assertEquals(1, rotOffCmds.size)
        assertEquals(SystemToggleTarget.ROTATION_OFF, rotOffCmds.first().target)

        val rotBothCmds = OmniCommandEngine.parseSystemCommands("> rotation")
        assertEquals(2, rotBothCmds.size)
        assertTrue(rotBothCmds.any { it.target == SystemToggleTarget.ROTATION_ON })
        assertTrue(rotBothCmds.any { it.target == SystemToggleTarget.ROTATION_OFF })

        val hudCmds = OmniCommandEngine.parseSystemCommands("> hud")
        assertEquals(1, hudCmds.size)
        assertEquals(SystemToggleTarget.FLOATING_HUD, hudCmds.first().target)

        val arcCmds = OmniCommandEngine.parseSystemCommands("> arc")
        assertEquals(1, arcCmds.size)
        assertEquals(SystemToggleTarget.THUMB_ARC, arcCmds.first().target)

        val allCmds = OmniCommandEngine.parseSystemCommands(">")
        assertEquals(7, allCmds.size)
    }

    @Test
    fun parseSplitCommand_builtInFold8VcAliases_andPostureSimulatorCommands() {
        val vcCompareSplit = OmniCommandEngine.parseSplitCommand("split: vc + compare", emptyList())
        assertNotNull(vcCompareSplit)
        assertEquals(OmniCommandEngine.VC_PROD_PACKAGE_Z_FOLD8, vcCompareSplit!!.primaryApp.packageName)
        assertEquals(OmniCommandEngine.VC_COMPARE_PACKAGE_Z_FOLD8, vcCompareSplit.secondaryApp.packageName)

        val vcTermuxSplit = OmniCommandEngine.parseSplitCommand("split: vc + termux", emptyList())
        assertNotNull(vcTermuxSplit)
        assertEquals(OmniCommandEngine.VC_PROD_PACKAGE_Z_FOLD8, vcTermuxSplit!!.primaryApp.packageName)
        assertEquals(OmniCommandEngine.TERMUX_PACKAGE, vcTermuxSplit.secondaryApp.packageName)

        val prodPreviewSplit = OmniCommandEngine.parseSplitCommand("split: prod + preview", emptyList())
        assertNotNull(prodPreviewSplit)
        assertEquals(OmniCommandEngine.VC_PROD_PACKAGE_Z_FOLD8, prodPreviewSplit!!.primaryApp.packageName)
        assertEquals(OmniCommandEngine.VC_PREVIEW_PACKAGE_Z_FOLD8, prodPreviewSplit.secondaryApp.packageName)

        val flexCmds = OmniCommandEngine.parseSystemCommands("> posture flex")
        assertEquals(1, flexCmds.size)
        assertEquals(SystemToggleTarget.POSTURE_FLEX, flexCmds.first().target)

        val tentCmds = OmniCommandEngine.parseSystemCommands("> tent")
        assertEquals(1, tentCmds.size)
        assertEquals(SystemToggleTarget.POSTURE_TENT, tentCmds.first().target)

        val innerCmds = OmniCommandEngine.parseSystemCommands("posture: inner")
        assertEquals(1, innerCmds.size)
        assertEquals(SystemToggleTarget.POSTURE_INNER, innerCmds.first().target)
    }

    @Test
    fun parseSystemCommands_torchDualPresentationAndWorkspaceProfiles_resolveExpectedActionTypes() {
        // 1. Hardware Torch / Flashlight Commands
        for (query in listOf("> torch", "> flashlight", "> light on", "> light off")) {
            val cmds = OmniCommandEngine.parseSystemCommands(query)
            assertEquals("Expected 1 command for $query", 1, cmds.size)
            assertEquals(SystemActionType.TOGGLE_TORCH, cmds.first().target)
            assertEquals(SystemActionType.TOGGLE_TORCH, cmds.first().actionType)
        }
        assertEquals(true, OmniCommandEngine.parseSystemCommands("> light on").first().desiredTorchState)
        assertEquals(false, OmniCommandEngine.parseSystemCommands("> light off").first().desiredTorchState)

        // 2. Z Fold 8 Dual-Screen Cover Presentation Commands
        for (query in listOf("> dual", "> cover hud", "> presentation")) {
            val cmds = OmniCommandEngine.parseSystemCommands(query)
            assertEquals("Expected 1 command for $query", 1, cmds.size)
            assertEquals(SystemActionType.TOGGLE_COVER_PRESENTATION, cmds.first().target)
            assertEquals("toggle_cover_presentation", cmds.first().actionId)
        }

        // 3. Workspace Profile Switcher Commands
        val expectedProfiles = mapOf(
            "> profile all" to "all",
            "> profile vc" to "vc",
            "> profile focus" to "focus",
            "> profile oled" to "oled"
        )
        for ((query, expectedProfileKey) in expectedProfiles) {
            val cmds = OmniCommandEngine.parseSystemCommands(query)
            assertEquals("Expected 1 command for $query", 1, cmds.size)
            val cmd = cmds.first()
            assertEquals(SystemActionType.SWITCH_WORKSPACE_PROFILE, cmd.target)
            assertEquals(expectedProfileKey, cmd.profileKey)
            assertEquals("profile_$expectedProfileKey", cmd.actionId)
        }
    }

    @Test
    fun parseVcCommand_quickMacros_expandsStatusApproveShipAndTestPrompts() {
        for (macroKey in listOf("status", "approve", "ship", "test")) {
            val parsed = OmniCommandEngine.parseVcCommand("vc: $macroKey")
            assertNotNull("Expected VcQuickMacro for vc: $macroKey", parsed)
            assertTrue(parsed is OmniCommandResult.VcQuickMacro)
            val macro = parsed as OmniCommandResult.VcQuickMacro
            assertEquals(macroKey, macro.macroKey)
            assertTrue(macro.promptText.isNotBlank())
            assertEquals(macro.promptText, macro.prompt)

            val evaluated = OmniCommandEngine.evaluatePrimary("vc: $macroKey")
            assertTrue(evaluated is OmniCommandResult.VcQuickMacro)
            assertEquals(macroKey, (evaluated as OmniCommandResult.VcQuickMacro).macroKey)
        }
    }

    @Test
    fun parseVcCommand_standardVoiceAndSplitModifiers_parsesPromptAndFlags() {
        val standard = OmniCommandEngine.parseVcCommand("vc: check fold8 build status")
        assertNotNull(standard)
        assertEquals("check fold8 build status", standard!!.prompt)
        assertEquals(false, standard.startVoiceTalk)
        assertEquals(false, standard.adjacentSplitScreen)

        val atMention = OmniCommandEngine.parseVcCommand("@vc deploy release apk")
        assertNotNull(atMention)
        assertEquals("deploy release apk", atMention!!.prompt)

        val voiceFlag = OmniCommandEngine.parseVcCommand("vc: --voice summarize attention")
        assertNotNull(voiceFlag)
        assertEquals("summarize attention", voiceFlag!!.prompt)
        assertTrue(voiceFlag.startVoiceTalk)

        val splitFlag = OmniCommandEngine.parseVcCommand("vc: --split inspect logs")
        assertNotNull(splitFlag)
        assertEquals("inspect logs", splitFlag!!.prompt)
        assertTrue(splitFlag.adjacentSplitScreen)
    }

    @Test
    fun evaluateDeveloperQuery_hexBinaryDecimalAndColorConversions_computeAccurately() {
        val hexToDec = OmniCommandEngine.evaluateDeveloperQuery("0xFF")
        assertNotNull(hexToDec)
        assertEquals("255", hexToDec!!.primaryOutput)

        val hexToBin = OmniCommandEngine.evaluateDeveloperQuery("0xFF in bin")
        assertNotNull(hexToBin)
        assertEquals("0b11111111", hexToBin!!.primaryOutput)

        val binToHex = OmniCommandEngine.evaluateDeveloperQuery("0b1010 to hex")
        assertNotNull(binToHex)
        assertEquals("0xA", binToHex!!.primaryOutput)

        val decToHex = OmniCommandEngine.evaluateDeveloperQuery("255 in hex")
        assertNotNull(decToHex)
        assertEquals("0xFF", decToHex!!.primaryOutput)

        val colorHex = OmniCommandEngine.evaluateDeveloperQuery("#1DC323")
        assertNotNull(colorHex)
        assertEquals("rgb(29, 195, 35)", colorHex!!.primaryOutput)
        assertEquals(0xFF1DC323L, colorHex.swatchColorHex)
    }

    @Test
    fun evaluateDeveloperQuery_androidDpiConversions_convertsPxAndDpCleanly() {
        val pxToDp = OmniCommandEngine.evaluateDeveloperQuery("1080px at 450dpi")
        assertNotNull(pxToDp)
        assertEquals("ANDROID DPI", pxToDp!!.category)
        assertEquals("384dp", pxToDp.primaryOutput)

        val dpToPx = OmniCommandEngine.evaluateDeveloperQuery("384dp at 450dpi")
        assertNotNull(dpToPx)
        assertEquals("1080px", dpToPx!!.primaryOutput)

        val atSignPx = OmniCommandEngine.evaluateDeveloperQuery("144px @ 480dpi")
        assertNotNull(atSignPx)
        assertEquals("48dp", atSignPx!!.primaryOutput)

        val fold8Res = OmniCommandEngine.evaluateDeveloperQuery("2448x1848 @ 420dpi")
        assertNotNull(fold8Res)
        assertEquals("ANDROID DPI", fold8Res!!.category)
        assertEquals("932.57x704dp", fold8Res.primaryOutput)
    }

    @Test
    fun evaluateDeveloperQuery_epochAndIsoTimestamps_roundTripCleanly() {
        val epochToIso = OmniCommandEngine.evaluateDeveloperQuery("1793065200")
        assertNotNull(epochToIso)
        assertEquals("2026-10-27T01:40:00Z", epochToIso!!.primaryOutput)

        val isoToEpoch = OmniCommandEngine.evaluateDeveloperQuery("2026-10-27T01:40:00Z")
        assertNotNull(isoToEpoch)
        assertEquals("1793065200", isoToEpoch!!.primaryOutput)

        val nowEval = OmniCommandEngine.evaluateDeveloperQuery("now", nowEpochMs = 1793065200000L)
        assertNotNull(nowEval)
        assertEquals("1793065200", nowEval!!.primaryOutput)
    }

    @Test
    fun evaluateDeveloperQuery_unitAndBase64Conversions_computeExpectedOutputs() {
        val tempCtoF = OmniCommandEngine.evaluateDeveloperQuery("100c in f")
        assertNotNull(tempCtoF)
        assertEquals("212°F", tempCtoF!!.primaryOutput)

        val tempFtoC = OmniCommandEngine.evaluateDeveloperQuery("212f in c")
        assertNotNull(tempFtoC)
        assertEquals("100°C", tempFtoC!!.primaryOutput)

        val kmToMi = OmniCommandEngine.evaluateDeveloperQuery("10km in mi")
        assertNotNull(kmToMi)
        assertEquals("6.21 mi", kmToMi!!.primaryOutput)

        val kgToLb = OmniCommandEngine.evaluateDeveloperQuery("10kg in lb")
        assertNotNull(kgToLb)
        assertEquals("22.05 lb", kgToLb!!.primaryOutput)

        val inToCm = OmniCommandEngine.evaluateDeveloperQuery("10in in cm")
        assertNotNull(inToCm)
        assertEquals("25.4 cm", inToCm!!.primaryOutput)

        val b64Encode = OmniCommandEngine.evaluateDeveloperQuery("b64: fold8")
        assertNotNull(b64Encode)
        assertEquals("Zm9sZDg=", b64Encode!!.primaryOutput)

        val b64Decode = OmniCommandEngine.evaluateDeveloperQuery("b64d: Zm9sZDg=")
        assertNotNull(b64Decode)
        assertEquals("fold8", b64Decode!!.primaryOutput)
    }

    @Test
    fun clipboardVault_inMemoryDeduplicationAndSearch_preservesPinnedPriority() {
        val vault = ClipboardVault(context = null)
        vault.addOrBumpClip("custom snippet alpha", label = "Alpha", isPinned = false, timestampMs = 1000L)
        vault.addOrBumpClip("custom snippet beta", label = "Beta", isPinned = false, timestampMs = 2000L)
        // Re-adding "custom snippet alpha" with newer timestamp bumps it above Beta without duplicating
        vault.addOrBumpClip("custom snippet alpha", label = "Alpha Updated", isPinned = false, timestampMs = 3000L)

        val matches = vault.search("custom snippet")
        assertEquals(2, matches.size)
        assertEquals("custom snippet alpha", matches[0].text)
        assertEquals("Alpha Updated", matches[0].label)
        assertEquals("custom snippet beta", matches[1].text)
    }
}
