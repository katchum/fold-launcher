package com.xsun.foldlauncher.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class SpatialMathTest {

    @Test
    fun computeArcItemPosition_rightHanded_placesItemsInUpperLeftQuadrantAlongRadius() {
        val count = 6
        val radius = 150f
        val positions = (0 until count).map { idx ->
            SpatialMath.computeArcItemPosition(
                index = idx,
                totalCount = count,
                radiusDp = radius,
                isRightHanded = true,
                fanProgress = 1f
            )
        }

        // First item starts at 190 deg, last item ends at 260 deg
        assertEquals(190f, positions.first().angleDeg, 0.001f)
        assertEquals(260f, positions.last().angleDeg, 0.001f)

        positions.forEach { pos ->
            // In screen coordinates (190..260 deg), both xDp (left of anchor) and yDp (above anchor) are negative
            assertTrue("Expected xDp < 0 for right-handed arc, got ${pos.xDp}", pos.xDp < 0f)
            assertTrue("Expected yDp < 0 for right-handed arc, got ${pos.yDp}", pos.yDp < 0f)
            val dist = hypot(pos.xDp, pos.yDp)
            assertEquals(radius, dist, 0.05f)
        }

        // Angle increases monotonically from 190 to 260 degrees
        for (i in 1 until positions.size) {
            assertTrue(positions[i].angleDeg > positions[i - 1].angleDeg)
        }
    }

    @Test
    fun computeArcItemPosition_leftHanded_placesItemsInUpperRightQuadrant() {
        val count = 5
        val radius = 140f
        val positions = (0 until count).map { idx ->
            SpatialMath.computeArcItemPosition(
                index = idx,
                totalCount = count,
                radiusDp = radius,
                isRightHanded = false,
                fanProgress = 1f
            )
        }

        assertEquals(350f, positions.first().angleDeg, 0.001f)
        assertEquals(280f, positions.last().angleDeg, 0.001f)

        positions.forEach { pos ->
            // In screen coordinates (280..350 deg), xDp is positive (right of left-edge anchor) and yDp is negative (up)
            assertTrue("Expected xDp > 0 for left-handed arc, got ${pos.xDp}", pos.xDp > 0f)
            assertTrue("Expected yDp < 0 for left-handed arc, got ${pos.yDp}", pos.yDp < 0f)
            val dist = hypot(pos.xDp, pos.yDp)
            assertEquals(radius, dist, 0.05f)
        }
    }

    @Test
    fun computeArcItemPosition_fanProgressAndSingleItem_scalesRadiusAndCentersAngle() {
        val collapsed = SpatialMath.computeArcItemPosition(
            index = 0,
            totalCount = 4,
            radiusDp = 150f,
            isRightHanded = true,
            fanProgress = 0f
        )
        assertEquals(0f, collapsed.radiusDp, 0.001f)
        assertEquals(0f, collapsed.xDp, 0.001f)
        assertEquals(0f, collapsed.yDp, 0.001f)

        val singleRight = SpatialMath.computeArcItemPosition(
            index = 0,
            totalCount = 1,
            radiusDp = 120f,
            isRightHanded = true,
            fanProgress = 1f
        )
        // Midpoint between 190 and 260 is 225 degrees
        assertEquals(225f, singleRight.angleDeg, 0.001f)
        assertEquals(abs(singleRight.xDp), abs(singleRight.yDp), 0.05f)
    }

    @Test
    fun resolveHoveredArcIndex_identifiesNearestRadialSlotAndRejectsOutOfBounds() {
        val count = 6
        val targetPos = SpatialMath.computeArcItemPosition(
            index = 3,
            totalCount = count,
            radiusDp = 130f,
            isRightHanded = true,
            fanProgress = 1f
        )

        val hovered = SpatialMath.resolveHoveredArcIndex(
            dxPx = targetPos.xDp,
            dyPx = targetPos.yDp,
            totalCount = count,
            minRadiusPx = 50f,
            maxRadiusPx = 210f,
            isRightHanded = true
        )
        assertEquals(3, hovered)

        // Too close to origin hub (< minRadiusPx) returns -1
        val tooClose = SpatialMath.resolveHoveredArcIndex(
            dxPx = -10f,
            dyPx = -10f,
            totalCount = count,
            minRadiusPx = 50f,
            maxRadiusPx = 210f,
            isRightHanded = true
        )
        assertEquals(-1, tooClose)

        // Too far outside arc (> maxRadiusPx) returns -1
        val tooFar = SpatialMath.resolveHoveredArcIndex(
            dxPx = -250f,
            dyPx = -250f,
            totalCount = count,
            minRadiusPx = 50f,
            maxRadiusPx = 210f,
            isRightHanded = true
        )
        assertEquals(-1, tooFar)
    }

    @Test
    fun computeParallaxOffset_clampsExtremeInputsAndScalesByDepth() {
        val extreme = SpatialMath.computeParallaxOffset(
            pitch = 4.5f,
            roll = -3.2f,
            depthLayer = 5.0f,
            maxTranslationPx = 12f,
            maxRotationDeg = 4f
        )

        // pitch clamped to +1f, roll clamped to -1f, depthLayer clamped to 3f
        assertEquals(-12f, extreme.rotationXDeg, 0.001f)
        assertEquals(-12f, extreme.rotationYDeg, 0.001f)
        assertEquals(-36f, extreme.translationXPx, 0.001f)
        assertEquals(36f, extreme.translationYPx, 0.001f)
        assertEquals(-1f, extreme.specularShift, 0.001f)

        val zeroDepth = SpatialMath.computeParallaxOffset(
            pitch = 0.8f,
            roll = 0.6f,
            depthLayer = -1f,
            maxTranslationPx = 14f,
            maxRotationDeg = 4.5f
        )
        assertEquals(0f, zeroDepth.rotationXDeg, 0.001f)
        assertEquals(0f, zeroDepth.rotationYDeg, 0.001f)
        assertEquals(0f, zeroDepth.translationXPx, 0.001f)
        assertEquals(0f, zeroDepth.translationYPx, 0.001f)
    }

    @Test
    fun normalizeTilt_lowPassFilter_andTrackpadCursor_clampWithinBounds() {
        assertEquals(1f, SpatialMath.normalizeTilt(2.0f, maxRange = 0.65f), 0.001f)
        assertEquals(-1f, SpatialMath.normalizeTilt(-2.0f, maxRange = 0.65f), 0.001f)
        assertEquals(0f, SpatialMath.normalizeTilt(1.0f, maxRange = 0f), 0.001f)

        val filtered = SpatialMath.lowPassFilter(current = 0f, target = 1f, alpha = 0.25f)
        assertEquals(0.25f, filtered, 0.001f)

        val (cursorX, cursorY) = SpatialMath.updateTrackpadCursor(
            currentNormalizedX = 0.5f,
            currentNormalizedY = 0.5f,
            deltaXPx = 500f,
            deltaYPx = -500f,
            padWidthPx = 200f,
            padHeightPx = 200f,
            sensitivity = 1.5f
        )
        assertEquals(1f, cursorX, 0.001f)
        assertEquals(0f, cursorY, 0.001f)
    }
}
