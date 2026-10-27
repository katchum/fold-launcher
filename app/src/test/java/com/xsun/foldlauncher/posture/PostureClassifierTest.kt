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
}
