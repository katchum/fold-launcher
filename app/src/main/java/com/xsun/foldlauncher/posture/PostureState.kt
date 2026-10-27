package com.xsun.foldlauncher.posture

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

enum class DevicePosture {
    /** Narrow Z Fold 8 cover screen or standard Android slab phone */
    COMPACT,

    /** Z Fold 8 half-opened horizontal Flex / Tabletop posture */
    FLEX_TABLETOP,

    /** Z Fold 8 7.6"+ inner display unfolded flat (or wide tablet/DeX) */
    EXPANDED;

    companion object {
        val COVER_OR_SLAB = COMPACT
        val UNFOLDED_TABLET = EXPANDED
    }
}

typealias PostureMode = DevicePosture

data class PostureInfo(
    val posture: DevicePosture,
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int = minOf(widthDp, heightDp),
    val isBookPosture: Boolean = false,
    val isSeparatingHinge: Boolean = false,
    val hingeTopDp: Int? = null,
    val hingeBottomDp: Int? = null,
    val hingeLeftDp: Int? = null,
    val hingeRightDp: Int? = null
) {
    /** Alias for compatibility with callers reading .mode */
    val mode: DevicePosture
        get() = posture

    val isExpanded: Boolean
        get() = posture == DevicePosture.EXPANDED

    val isFlexTabletop: Boolean
        get() = posture == DevicePosture.FLEX_TABLETOP

    val isCompact: Boolean
        get() = posture == DevicePosture.COMPACT

    /** Recommended grid columns based on current Z Fold 8 / Slab screen state */
    val recommendedGridColumns: Int
        get() = when (posture) {
            DevicePosture.COMPACT -> 4
            DevicePosture.FLEX_TABLETOP -> 5
            DevicePosture.EXPANDED -> if (widthDp >= 840) 6 else 5
        }
}

typealias DevicePostureState = PostureInfo

object PostureClassifier {
    const val UNFOLDED_MIN_SMALLEST_WIDTH_DP = 580
    const val UNFOLDED_MIN_WIDTH_DP = 600

    /**
     * Pure classification function for screen dimensions + optional hinge state.
     * Easily unit-tested without Android framework mocks.
     */
    fun classify(
        widthDp: Int,
        heightDp: Int,
        smallestWidthDp: Int = minOf(widthDp, heightDp),
        isHalfOpened: Boolean = false,
        isHorizontalHinge: Boolean = false,
        isSeparating: Boolean = false,
        hingeTopDp: Int? = null,
        hingeBottomDp: Int? = null,
        hingeLeftDp: Int? = null,
        hingeRightDp: Int? = null
    ): PostureInfo {
        if (isHalfOpened && isHorizontalHinge) {
            return PostureInfo(
                posture = DevicePosture.FLEX_TABLETOP,
                widthDp = widthDp,
                heightDp = heightDp,
                smallestWidthDp = smallestWidthDp,
                isSeparatingHinge = isSeparating,
                hingeTopDp = hingeTopDp,
                hingeBottomDp = hingeBottomDp
            )
        }

        if (isHalfOpened && !isHorizontalHinge) {
            return PostureInfo(
                posture = DevicePosture.EXPANDED,
                widthDp = widthDp,
                heightDp = heightDp,
                smallestWidthDp = smallestWidthDp,
                isBookPosture = true,
                isSeparatingHinge = isSeparating,
                hingeLeftDp = hingeLeftDp,
                hingeRightDp = hingeRightDp
            )
        }

        val isExpanded = smallestWidthDp >= UNFOLDED_MIN_SMALLEST_WIDTH_DP ||
            widthDp >= UNFOLDED_MIN_WIDTH_DP

        return PostureInfo(
            posture = if (isExpanded) DevicePosture.EXPANDED else DevicePosture.COMPACT,
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = smallestWidthDp,
            isSeparatingHinge = isSeparating,
            hingeTopDp = hingeTopDp,
            hingeBottomDp = hingeBottomDp,
            hingeLeftDp = hingeLeftDp,
            hingeRightDp = hingeRightDp
        )
    }
}

@Composable
fun rememberPostureInfo(activity: Activity? = null): PostureInfo {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val smallestWidthDp = configuration.smallestScreenWidthDp

    if (activity == null) {
        return PostureClassifier.classify(
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = smallestWidthDp
        )
    }

    val layoutInfoFlow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
    }
    val windowLayoutInfo by layoutInfoFlow.collectAsState(initial = null)

    val foldingFeature = windowLayoutInfo
        ?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull()

    if (foldingFeature != null) {
        val bounds = foldingFeature.bounds
        val hingeTopDp = with(density) { bounds.top.toDp().value.toInt() }
        val hingeBottomDp = with(density) { bounds.bottom.toDp().value.toInt() }
        val hingeLeftDp = with(density) { bounds.left.toDp().value.toInt() }
        val hingeRightDp = with(density) { bounds.right.toDp().value.toInt() }

        return PostureClassifier.classify(
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = smallestWidthDp,
            isHalfOpened = foldingFeature.state == FoldingFeature.State.HALF_OPENED,
            isHorizontalHinge = foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL,
            isSeparating = foldingFeature.isSeparating,
            hingeTopDp = hingeTopDp,
            hingeBottomDp = hingeBottomDp,
            hingeLeftDp = hingeLeftDp,
            hingeRightDp = hingeRightDp
        )
    }

    return PostureClassifier.classify(
        widthDp = widthDp,
        heightDp = heightDp,
        smallestWidthDp = smallestWidthDp
    )
}

@Composable
fun rememberDevicePosture(activity: Activity? = null): PostureInfo = rememberPostureInfo(activity)
