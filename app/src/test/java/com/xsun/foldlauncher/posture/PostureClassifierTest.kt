package com.xsun.foldlauncher.posture

import com.xsun.foldlauncher.service.NetworkTelemetrySnapshot
import com.xsun.foldlauncher.service.SystemTweaker
import com.xsun.foldlauncher.service.WirelessAdbSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostureClassifierTest {

    @Test
    fun classify_zFoldCoverScreen_returnsCompact() {
        val info = PostureClassifier.classify(
            widthDp = 384,
            heightDp = 914,
            smallestWidthDp = 384
        )
        assertEquals(DevicePosture.COMPACT, info.posture)
        assertTrue(info.isCompact)
        assertFalse(info.isExpanded)
        assertEquals(4, info.recommendedGridColumns)
    }

    @Test
    fun classify_zFold8CoverScreenLandscape_remainsCompactNotExpanded() {
        // Z Fold 8 (SM-F971U1) outer cover display is 1248x1972 @ 420dpi = 475dp x 751dp.
        // In landscape, widthDp = 751 while smallestWidthDp = 475. Must remain COMPACT!
        val info = PostureClassifier.classify(
            widthDp = 751,
            heightDp = 475,
            smallestWidthDp = 475
        )
        assertEquals(DevicePosture.COMPACT, info.posture)
        assertTrue(info.isCompact)
        assertFalse(info.isExpanded)
        assertEquals("COVER LANDSCAPE", info.statusBadgeText)
    }

    @Test
    fun classify_zFold8InnerUnfoldedScreen_returnsExpanded() {
        // Z Fold 8 (SM-F971U1) inner display is 2448x1848 @ 420dpi = 874dp x 704dp
        val info = PostureClassifier.classify(
            widthDp = 704,
            heightDp = 874,
            smallestWidthDp = 704
        )
        assertEquals(DevicePosture.EXPANDED, info.posture)
        assertTrue(info.isExpanded)
        assertEquals(5, info.recommendedGridColumns)
    }

    @Test
    fun classify_halfOpenedHorizontalHinge_returnsFlexTabletop() {
        val info = PostureClassifier.classify(
            widthDp = 841,
            heightDp = 708,
            smallestWidthDp = 708,
            isHalfOpened = true,
            isHorizontalHinge = true,
            isSeparating = true,
            hingeTopDp = 350,
            hingeBottomDp = 358
        )
        assertEquals(DevicePosture.FLEX_TABLETOP, info.posture)
        assertTrue(info.isFlexTabletop)
        assertEquals(350, info.hingeTopDp)
        assertEquals(358, info.hingeBottomDp)
    }

    @Test
    fun classify_halfOpenedVerticalHinge_returnsExpandedBookMode() {
        val info = PostureClassifier.classify(
            widthDp = 708,
            heightDp = 841,
            smallestWidthDp = 708,
            isHalfOpened = true,
            isHorizontalHinge = false,
            isSeparating = true,
            hingeLeftDp = 350,
            hingeRightDp = 358
        )
        assertEquals(DevicePosture.EXPANDED, info.posture)
        assertTrue(info.isBookPosture)
        assertEquals(350, info.hingeLeftDp)
    }

    @Test
    fun classify_hingeSensorTentAngle_activatesTentMode() {
        val info = PostureClassifier.classify(
            widthDp = 751,
            heightDp = 475,
            foldingState = null,
            hingeAngleDegrees = 42f,
            overrideMode = PostureOverride.AUTO
        )
        assertEquals(DevicePosture.FLEX_TABLETOP, info.posture)
        assertTrue(info.isTentMode)
        assertEquals("TENT 42°", info.statusBadgeText)
    }

    @Test
    fun classify_hingeSensorFlexAngleOnInnerDisplay_activatesFlexTabletop() {
        val info = PostureClassifier.classify(
            widthDp = 874,
            heightDp = 704,
            foldingState = null,
            hingeAngleDegrees = 110f,
            overrideMode = PostureOverride.AUTO
        )
        assertEquals(DevicePosture.FLEX_TABLETOP, info.posture)
        assertFalse(info.isTentMode)
        assertEquals("FLEX 110°", info.statusBadgeText)
    }

    @Test
    fun classify_manualPostureOverrides_forceTargetPosture() {
        val forcedCover = PostureClassifier.classify(
            widthDp = 874,
            heightDp = 704,
            foldingState = null,
            hingeAngleDegrees = 180f,
            overrideMode = PostureOverride.FORCE_COVER
        )
        assertEquals(DevicePosture.COMPACT, forcedCover.posture)

        val forcedTent = PostureClassifier.classify(
            widthDp = 475,
            heightDp = 751,
            foldingState = null,
            hingeAngleDegrees = 0f,
            overrideMode = PostureOverride.TENT_MODE
        )
        assertEquals(DevicePosture.FLEX_TABLETOP, forcedTent.posture)
        assertTrue(forcedTent.isTentMode)
    }

    @Test
    fun computeHingeDetentCrossed_majorPostureThresholds_returnsMajorTick() {
        // Crossing 15° (Closed -> Tent)
        assertEquals(
            HingeDetentTick.MAJOR_POSTURE_THRESHOLD,
            PostureClassifier.computeHingeDetentCrossed(12f, 18f)
        )
        // Crossing 75° (Tent -> Flex Tabletop)
        assertEquals(
            HingeDetentTick.MAJOR_POSTURE_THRESHOLD,
            PostureClassifier.computeHingeDetentCrossed(71f, 78f)
        )
        // Crossing 155° (Flex Tabletop -> Flat Expanded)
        assertEquals(
            HingeDetentTick.MAJOR_POSTURE_THRESHOLD,
            PostureClassifier.computeHingeDetentCrossed(152f, 158f)
        )
        // Reverse closing across 75°
        assertEquals(
            HingeDetentTick.MAJOR_POSTURE_THRESHOLD,
            PostureClassifier.computeHingeDetentCrossed(80f, 72f)
        )
    }

    @Test
    fun computeHingeDetentCrossed_minor15DegreeDetents_returnsMinorTick() {
        // Crossing 30°, 45°, 60°, 90°, 120°
        assertEquals(
            HingeDetentTick.MINOR_15_DEG,
            PostureClassifier.computeHingeDetentCrossed(28f, 32f)
        )
        assertEquals(
            HingeDetentTick.MINOR_15_DEG,
            PostureClassifier.computeHingeDetentCrossed(43f, 47f)
        )
        assertEquals(
            HingeDetentTick.MINOR_15_DEG,
            PostureClassifier.computeHingeDetentCrossed(88f, 93f)
        )
        assertEquals(
            HingeDetentTick.MINOR_15_DEG,
            PostureClassifier.computeHingeDetentCrossed(123f, 118f)
        )
    }

    @Test
    fun computeHingeDetentCrossed_jitterOrWithinSameBucket_returnsNone() {
        // Sub-1.5° jitter across 45° should be ignored
        assertEquals(
            HingeDetentTick.NONE,
            PostureClassifier.computeHingeDetentCrossed(44.5f, 45.2f)
        )
        // Movement within 30°..44° without crossing a boundary
        assertEquals(
            HingeDetentTick.NONE,
            PostureClassifier.computeHingeDetentCrossed(32f, 41f)
        )
    }

    @Test
    fun isFoldCloseTransition_detectsClosingBelowThreshold() {
        assertTrue(PostureClassifier.isFoldCloseTransition(previousAngleDeg = 22f, currentAngleDeg = 4f))
        assertFalse(PostureClassifier.isFoldCloseTransition(previousAngleDeg = 4f, currentAngleDeg = 22f))
        assertFalse(PostureClassifier.isFoldCloseTransition(previousAngleDeg = 8.4f, currentAngleDeg = 7.6f))
    }

    @Test
    fun foldCloseActionMode_cyclesThroughAllFourModes() {
        val start = FoldCloseActionMode.STAY_AWAKE_COVER
        val second = start.next()
        val third = second.next()
        val fourth = third.next()
        val wrapped = fourth.next()

        assertEquals(FoldCloseActionMode.AUTO_VC_VOICE, second)
        assertEquals(FoldCloseActionMode.TENT_STANDBY, third)
        assertEquals(FoldCloseActionMode.INSTANT_LOCK, fourth)
        assertEquals(FoldCloseActionMode.STAY_AWAKE_COVER, wrapped)
    }

    @Test
    fun isUnfoldOpenTransition_detectsUnfoldingUpwardAcross155Degrees() {
        assertTrue(PostureClassifier.isUnfoldOpenTransition(previousAngleDeg = 148f, currentAngleDeg = 160f))
        assertTrue(isUnfoldOpenTransition(previousAngleDeg = 120f, currentAngleDeg = 155f))
        // Closing downward across 155° must not trigger unfold
        assertFalse(PostureClassifier.isUnfoldOpenTransition(previousAngleDeg = 165f, currentAngleDeg = 140f))
        // Sub-2.0° jitter across 155° must be ignored
        assertFalse(PostureClassifier.isUnfoldOpenTransition(previousAngleDeg = 154.2f, currentAngleDeg = 155.4f))
    }

    @Test
    fun unfoldActionMode_cyclesAndParsesKeys() {
        val start = UnfoldActionMode.INNER_DASHBOARD
        val second = start.next()
        val third = second.next()
        val fourth = third.next()
        val wrapped = fourth.next()

        assertEquals(UnfoldActionMode.AUTO_SPLIT_VC_TERMUX, second)
        assertEquals(UnfoldActionMode.AUTO_SPLIT_PROD_COMPARE, third)
        assertEquals(UnfoldActionMode.AUTO_SPLIT_VC_BROWSER, fourth)
        assertEquals(UnfoldActionMode.INNER_DASHBOARD, wrapped)

        assertEquals(UnfoldActionMode.AUTO_SPLIT_VC_TERMUX, UnfoldActionMode.fromKey("vc_termux"))
        assertEquals(UnfoldActionMode.AUTO_SPLIT_PROD_COMPARE, UnfoldActionMode.fromKey("prod_compare"))
        assertEquals(UnfoldActionMode.AUTO_SPLIT_VC_BROWSER, UnfoldActionMode.fromKey("vc_browser"))
        assertEquals(UnfoldActionMode.INNER_DASHBOARD, UnfoldActionMode.fromKey(null))
    }

    @Test
    fun networkTelemetry_detectsTailscaleCgnatAndLanAddresses() {
        // Z Fold 8 (100.101.43.86) and S23 Ultra (100.68.53.123) Tailscale IPs
        assertTrue(SystemTweaker.isTailscaleCgnatIpv4("100.101.43.86"))
        assertTrue(SystemTweaker.isTailscaleCgnatIpv4("100.68.53.123"))
        assertTrue(SystemTweaker.isTailscaleCgnatIpv4("100.96.188.90"))
        assertFalse(SystemTweaker.isTailscaleCgnatIpv4("100.63.255.1"))
        assertFalse(SystemTweaker.isTailscaleCgnatIpv4("192.168.1.50"))

        assertTrue(SystemTweaker.isPrivateLanIpv4("192.168.1.50"))
        assertTrue(SystemTweaker.isPrivateLanIpv4("10.0.0.12"))
        assertFalse(SystemTweaker.isPrivateLanIpv4("100.101.43.86"))

        val tsSnapshot = NetworkTelemetrySnapshot(
            tailscaleIp = "100.101.43.86",
            lanIp = "192.168.1.42",
            isVpnActive = true,
            isWifiConnected = true,
            downstreamMbps = 320
        )
        assertTrue(tsSnapshot.isTailscaleConnected)
        assertEquals("TS 100.101.43.86", tsSnapshot.statusBadge)
    }

    @Test
    fun wirelessAdbSnapshot_formatsConnectAddressAndStatusLabel() {
        val activeWithPort = WirelessAdbSnapshot(
            isEnabled = true,
            port = 42595,
            ipAddress = "100.101.43.86"
        )
        assertEquals("100.101.43.86:42595", activeWithPort.connectAddress)
        assertEquals("On • :42595", activeWithPort.statusLabel)

        val activeWithoutPort = WirelessAdbSnapshot(
            isEnabled = true,
            port = null,
            ipAddress = "100.68.53.123"
        )
        assertEquals("100.68.53.123", activeWithoutPort.connectAddress)
        assertEquals("On", activeWithoutPort.statusLabel)

        val disabled = WirelessAdbSnapshot(
            isEnabled = false,
            port = null,
            ipAddress = null
        )
        assertNull(disabled.connectAddress)
        assertEquals("Off", disabled.statusLabel)
    }
}
