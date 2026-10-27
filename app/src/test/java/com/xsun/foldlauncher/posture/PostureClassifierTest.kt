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
    fun classify_standardSlabLandscape_withSmallSmallestWidth_remainsCompactUnlessWide() {
        val info = PostureClassifier.classify(
            widthDp = 732,
            heightDp = 412,
            smallestWidthDp = 412
        )
        assertEquals(DevicePosture.EXPANDED, info.posture)
    }

    @Test
    fun classify_zFold8InnerUnfoldedScreen_returnsExpanded() {
        val info = PostureClassifier.classify(
            widthDp = 708,
            heightDp = 841,
            smallestWidthDp = 708
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
}
