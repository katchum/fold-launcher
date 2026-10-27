package com.xsun.foldlauncher.posture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
