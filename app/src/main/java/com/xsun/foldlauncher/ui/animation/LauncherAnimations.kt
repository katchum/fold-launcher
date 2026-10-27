package com.xsun.foldlauncher.ui.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsun.foldlauncher.posture.DevicePostureState
import com.xsun.foldlauncher.posture.PostureMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Which lower thumb corner triggered the One-Handed Radial Thumb Arc ("Arc Deck").
 */
enum class RadialTriggerCorner {
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

/**
 * Pure, deterministic animation & gesture physics math helpers for 120Hz launcher interactions.
 * Designed for direct JUnit4 unit testing without Android framework mocks.
 */
object AnimationPhysicsMath {
    /**
     * Returns an expansion rank for posture transitions:
     * `0` = [PostureMode.COVER_OR_SLAB], `1` = [PostureMode.FLEX_TABLETOP], `2` = [PostureMode.UNFOLDED_TABLET].
     */
    fun postureExpansionRank(mode: PostureMode): Int = when (mode) {
        PostureMode.COVER_OR_SLAB -> 0
        PostureMode.FLEX_TABLETOP -> 1
        PostureMode.UNFOLDED_TABLET -> 2
        else -> 0
    }

    /**
     * Computes Gaussian wave intensity (`0f..1f`) for an item at [index] relative to [centerIndex].
     */
    fun computeGaussianWaveIntensity(
        index: Int,
        centerIndex: Float,
        maxRadiusSlots: Float = 4.0f,
        variance: Float = 2.2f
    ): Float {
        if (centerIndex < 0f || variance <= 0f) return 0f
        val distance = abs(index.toFloat() - centerIndex)
        if (distance >= maxRadiusSlots) return 0f
        return exp(-(distance * distance) / variance).coerceIn(0f, 1f)
    }

    /**
     * Maps a vertical touch position [yPx] within [heightPx] to a clamped index in `0 until itemCount`.
     */
    fun resolveScrubberLetterIndex(
        yPx: Float,
        heightPx: Float,
        itemCount: Int
    ): Int {
        if (itemCount <= 0) return -1
        if (heightPx <= 0f) return 0
        val clampedFraction = (yPx / heightPx).coerceIn(0f, 1f)
        return (clampedFraction * itemCount).toInt().coerceIn(0, itemCount - 1)
    }

    /**
     * Evaluates whether a drag starting at ([startX], [startY]) with displacement ([dragDx], [dragDy])
     * inside a container of size ([widthPx], [heightPx]) constitutes a diagonal inward corner pull
     * that should summon the One-Handed Radial Thumb Arc.
     */
    fun detectCornerArcTrigger(
        startX: Float,
        startY: Float,
        dragDx: Float,
        dragDy: Float,
        widthPx: Float,
        heightPx: Float,
        cornerZoneFraction: Float = 0.28f,
        minSwipeDistancePx: Float = 42f
    ): RadialTriggerCorner? {
        if (widthPx <= 0f || heightPx <= 0f) return null
        val inBottomZone = startY >= heightPx * (1f - cornerZoneFraction)
        if (!inBottomZone) return null

        val distance = hypot(dragDx, dragDy)
        if (distance < minSwipeDistancePx || dragDy >= -minSwipeDistancePx * 0.35f) return null

        val inLeftCorner = startX <= widthPx * cornerZoneFraction
        val inRightCorner = startX >= widthPx * (1f - cornerZoneFraction)

        return when {
            inLeftCorner && dragDx > 0f -> RadialTriggerCorner.BOTTOM_LEFT
            inRightCorner && dragDx < 0f -> RadialTriggerCorner.BOTTOM_RIGHT
            else -> null
        }
    }
}

/**
 * 120Hz-tuned spring specifications for Fold8 & Slab Launcher animations.
 * Critically/under-damped curves avoid sluggish easing tails and respond immediately
 * to mid-flight gesture interruptions or fold/unfold posture changes.
 */
object LauncherSpringSpecs {
    val Snappy120HzFloat: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 520f
    )

    val Snappy120HzIntOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.84f,
        stiffness = 500f,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )

    val SearchSheetSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.86f,
        stiffness = 620f
    )

    val IconBounceSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Spring for Radial Thumb Arc ("Arc Deck") fan-out and collapse at 120Hz */
    val RadialArcFanOutSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.76f,
        stiffness = 580f
    )

    /** Critically-damped spring for 120Hz gyroscope/hinge 3D parallax card tilt */
    val SpatialParallaxSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.88f,
        stiffness = 440f
    )

    /** High-stiffness spring for Flex-Mode virtual trackpad laser cursor tracking */
    val TrackpadCursorSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.90f,
        stiffness = 780f
    )
}

/**
 * Posture-aware animated container that morphs smoothly at 120Hz when folding/unfolding
 * between [PostureMode.COVER_OR_SLAB], [PostureMode.FLEX_TABLETOP], and [PostureMode.UNFOLDED_TABLET].
 */
@Composable
fun PostureAnimatedContainer(
    postureState: DevicePostureState,
    modifier: Modifier = Modifier,
    content: @Composable (DevicePostureState) -> Unit
) {
    AnimatedContent(
        targetState = postureState,
        modifier = modifier,
        contentKey = { it.mode },
        transitionSpec = {
            val initialRank = AnimationPhysicsMath.postureExpansionRank(initialState.mode)
            val targetRank = AnimationPhysicsMath.postureExpansionRank(targetState.mode)
            val isUnfolding = targetRank >= initialRank
            val involvesFlex = initialState.mode == PostureMode.FLEX_TABLETOP ||
                targetState.mode == PostureMode.FLEX_TABLETOP

            val slideEnter = if (involvesFlex) {
                slideInVertically(
                    animationSpec = LauncherSpringSpecs.Snappy120HzIntOffset,
                    initialOffsetY = { fullHeight -> if (isUnfolding) fullHeight / 10 else -fullHeight / 10 }
                )
            } else {
                slideInHorizontally(
                    animationSpec = LauncherSpringSpecs.Snappy120HzIntOffset,
                    initialOffsetX = { fullWidth -> if (isUnfolding) fullWidth / 12 else -fullWidth / 12 }
                )
            }

            val slideExit = if (involvesFlex) {
                slideOutVertically(
                    animationSpec = LauncherSpringSpecs.Snappy120HzIntOffset,
                    targetOffsetY = { fullHeight -> if (isUnfolding) -fullHeight / 12 else fullHeight / 12 }
                )
            } else {
                slideOutHorizontally(
                    animationSpec = LauncherSpringSpecs.Snappy120HzIntOffset,
                    targetOffsetX = { fullWidth -> if (isUnfolding) -fullWidth / 14 else fullWidth / 14 }
                )
            }

            val enterScale = if (isUnfolding) 0.94f else 1.05f
            val exitScale = if (isUnfolding) 1.04f else 0.95f

            (fadeIn(animationSpec = LauncherSpringSpecs.Snappy120HzFloat) +
                scaleIn(
                    initialScale = enterScale,
                    animationSpec = LauncherSpringSpecs.Snappy120HzFloat
                ) +
                slideEnter)
                .togetherWith(
                    fadeOut(animationSpec = LauncherSpringSpecs.Snappy120HzFloat) +
                        scaleOut(
                            targetScale = exitScale,
                            animationSpec = LauncherSpringSpecs.Snappy120HzFloat
                        ) +
                        slideExit
                )
                .using(SizeTransform(clip = false))
        },
        label = "PostureMorphTransition"
    ) { state ->
        content(state)
    }
}

/**
 * Home workspace gesture modifier supporting:
 * - Instant 0ms Spotlight search invocation on deliberate upward swipe (> 28dp)
 * - Notification shade expansion on downward swipe (> 36dp)
 * - Double-tap anywhere on empty workspace to lock screen
 * - Long-press on workspace to open home settings
 * - Optional diagonal bottom-corner swipe to summon the One-Handed Radial Thumb Arc
 */
fun Modifier.launcherGestures(
    onSwipeUpSearch: () -> Unit,
    onSwipeDownNotifications: () -> Unit,
    onDoubleTapLock: () -> Unit,
    onLongPressHomeSettings: () -> Unit = {},
    onCornerSwipeRadialArc: ((RadialTriggerCorner) -> Unit)? = null
): Modifier = composed {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val currentSwipeUp by rememberUpdatedState(onSwipeUpSearch)
    val currentSwipeDown by rememberUpdatedState(onSwipeDownNotifications)
    val currentDoubleTap by rememberUpdatedState(onDoubleTapLock)
    val currentLongPress by rememberUpdatedState(onLongPressHomeSettings)
    val currentCornerArc by rememberUpdatedState(onCornerSwipeRadialArc)

    val swipeUpThresholdPx = remember(density) { with(density) { 28.dp.toPx() } }
    val swipeDownThresholdPx = remember(density) { with(density) { 36.dp.toPx() } }
    val cornerArcThresholdPx = remember(density) { with(density) { 38.dp.toPx() } }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    this
        .onSizeChanged { containerSize = it }
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentDoubleTap()
                },
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentLongPress()
                }
            )
        }
        .pointerInput(swipeUpThresholdPx, swipeDownThresholdPx, cornerArcThresholdPx) {
            var startOffset = Offset.Zero
            var totalDx = 0f
            var totalVerticalDrag = 0f
            var gestureTriggered = false

            detectVerticalDragGestures(
                onDragStart = { offset ->
                    startOffset = offset
                    totalDx = 0f
                    totalVerticalDrag = 0f
                    gestureTriggered = false
                },
                onDragEnd = {
                    totalDx = 0f
                    totalVerticalDrag = 0f
                    gestureTriggered = false
                },
                onDragCancel = {
                    totalDx = 0f
                    totalVerticalDrag = 0f
                    gestureTriggered = false
                },
                onVerticalDrag = { change, dragAmount ->
                    if (!gestureTriggered) {
                        totalDx += change.position.x - change.previousPosition.x
                        totalVerticalDrag += dragAmount

                        val cornerHandler = currentCornerArc
                        if (cornerHandler != null && containerSize.width > 0 && containerSize.height > 0) {
                            val corner = AnimationPhysicsMath.detectCornerArcTrigger(
                                startX = startOffset.x,
                                startY = startOffset.y,
                                dragDx = totalDx,
                                dragDy = totalVerticalDrag,
                                widthPx = containerSize.width.toFloat(),
                                heightPx = containerSize.height.toFloat(),
                                minSwipeDistancePx = cornerArcThresholdPx
                            )
                            if (corner != null) {
                                gestureTriggered = true
                                change.consume()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                cornerHandler(corner)
                                return@detectVerticalDragGestures
                            }
                        }

                        if (totalVerticalDrag < -swipeUpThresholdPx) {
                            gestureTriggered = true
                            change.consume()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentSwipeUp()
                        } else if (totalVerticalDrag > swipeDownThresholdPx) {
                            gestureTriggered = true
                            change.consume()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentSwipeDown()
                        }
                    }
                }
            )
        }
}

/**
 * Dedicated modifier for detecting diagonal inward corner swipes from the lower-left or lower-right
 * thumb zone to summon the One-Handed Radial Thumb Arc ("Arc Deck").
 */
fun Modifier.cornerRadialArcTrigger(
    onTriggerArc: (RadialTriggerCorner) -> Unit
): Modifier = composed {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentOnTrigger by rememberUpdatedState(onTriggerArc)
    val minSwipePx = remember(density) { with(density) { 38.dp.toPx() } }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    this
        .onSizeChanged { containerSize = it }
        .pointerInput(minSwipePx) {
            var startOffset = Offset.Zero
            var accumulatedDx = 0f
            var accumulatedDy = 0f
            var fired = false

            detectDragGestures(
                onDragStart = { offset ->
                    startOffset = offset
                    accumulatedDx = 0f
                    accumulatedDy = 0f
                    fired = false
                },
                onDragEnd = { fired = false },
                onDragCancel = { fired = false },
                onDrag = { change, dragAmount ->
                    if (!fired && containerSize.width > 0 && containerSize.height > 0) {
                        accumulatedDx += dragAmount.x
                        accumulatedDy += dragAmount.y
                        val corner = AnimationPhysicsMath.detectCornerArcTrigger(
                            startX = startOffset.x,
                            startY = startOffset.y,
                            dragDx = accumulatedDx,
                            dragDy = accumulatedDy,
                            widthPx = containerSize.width.toFloat(),
                            heightPx = containerSize.height.toFloat(),
                            minSwipeDistancePx = minSwipePx
                        )
                        if (corner != null) {
                            fired = true
                            change.consume()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnTrigger(corner)
                        }
                    }
                }
            )
        }
}

/**
 * Tactile 120Hz icon physics modifier with spring scale-down (`0.90f` while pressed,
 * bouncing back via [LauncherSpringSpecs.IconBounceSpring]), long-press haptics,
 * and optional vertical swipe-up shortcut gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.iconPressPhysics(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeUpAction: (() -> Unit)? = null
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isTouchDown by remember { mutableStateOf(false) }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUpAction)

    val scale by animateFloatAsState(
        targetValue = if (isPressed || isTouchDown) 0.90f else 1.0f,
        animationSpec = LauncherSpringSpecs.IconBounceSpring,
        label = "IconPressScale"
    )

    val swipeThresholdPx = remember(density) { with(density) { 24.dp.toPx() } }

    val swipeModifier = if (onSwipeUpAction != null) {
        Modifier.pointerInput(swipeThresholdPx) {
            var accumulatedDy = 0f
            var triggered = false
            detectVerticalDragGestures(
                onDragStart = {
                    accumulatedDy = 0f
                    triggered = false
                    isTouchDown = true
                },
                onDragEnd = {
                    accumulatedDy = 0f
                    triggered = false
                    isTouchDown = false
                },
                onDragCancel = {
                    accumulatedDy = 0f
                    triggered = false
                    isTouchDown = false
                },
                onVerticalDrag = { change, dragAmount ->
                    if (!triggered) {
                        accumulatedDy += dragAmount
                        if (accumulatedDy < -swipeThresholdPx) {
                            triggered = true
                            isTouchDown = false
                            change.consume()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnSwipeUp?.invoke()
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(swipeModifier)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { currentOnClick() },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLongClick()
            }
        )
}

/**
 * One-handed vertical alphabetical wave fast-scrubber for Cover/Slab screens.
 * Tracks vertical touch/drag along the rail, invokes [onLetterSelected] with tick haptics
 * when crossing into a new letter slot, and animates a smooth Gaussian wave horizontal
 * offset and scale bulge around the active thumb position.
 */
@Composable
fun AlphabetWaveScrubber(
    letters: List<Char> = ('A'..'Z').toList() + '#',
    activeLetter: Char?,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    if (letters.isEmpty()) return

    val haptics = LocalHapticFeedback.current
    val currentOnLetterSelected by rememberUpdatedState(onLetterSelected)

    var scrubberHeightPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var touchFraction by remember { mutableFloatStateOf(-1f) }
    var lastSelectedLetter by remember { mutableStateOf<Char?>(null) }

    val activeIndex = remember(activeLetter, letters) {
        activeLetter?.let { letters.indexOf(it) } ?: -1
    }

    val effectiveCenterIndex = when {
        isDragging && touchFraction in 0f..1f -> touchFraction * (letters.size - 1).coerceAtLeast(1)
        activeIndex >= 0 -> activeIndex.toFloat()
        else -> -10f
    }

    fun resolveAndSelectLetter(yPx: Float) {
        val index = AnimationPhysicsMath.resolveScrubberLetterIndex(
            yPx = yPx,
            heightPx = scrubberHeightPx,
            itemCount = letters.size
        )
        if (index < 0) return
        touchFraction = (yPx / scrubberHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val letter = letters[index]
        if (letter != lastSelectedLetter) {
            lastSelectedLetter = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnLetterSelected(letter)
        }
    }

    Column(
        modifier = modifier
            .width(32.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isDragging) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                } else {
                    Color.Transparent
                }
            )
            .padding(vertical = 8.dp)
            .onSizeChanged { size ->
                scrubberHeightPx = size.height.coerceAtLeast(1).toFloat()
            }
            .pointerInput(letters) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        resolveAndSelectLetter(offset.y)
                        tryAwaitRelease()
                        isDragging = false
                    }
                )
            }
            .pointerInput(letters) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        resolveAndSelectLetter(offset.y)
                    },
                    onDragEnd = {
                        isDragging = false
                        lastSelectedLetter = null
                    },
                    onDragCancel = {
                        isDragging = false
                        lastSelectedLetter = null
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        resolveAndSelectLetter(change.position.y)
                    }
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEachIndexed { index, letter ->
            val distance = abs(index.toFloat() - effectiveCenterIndex)
            val waveIntensity = if (isDragging || activeIndex >= 0) {
                AnimationPhysicsMath.computeGaussianWaveIntensity(
                    index = index,
                    centerIndex = effectiveCenterIndex
                )
            } else {
                0f
            }

            val targetOffsetX = (-22f * waveIntensity).dp
            val targetScale = 1f + (0.45f * waveIntensity)
            val isHighlighted = letter == activeLetter || (isDragging && distance < 0.55f)

            val animatedOffsetX by animateDpAsState(
                targetValue = targetOffsetX,
                animationSpec = spring(
                    dampingRatio = 0.80f,
                    stiffness = 560f
                ),
                label = "WaveScrubberOffsetX"
            )
            val animatedScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = LauncherSpringSpecs.Snappy120HzFloat,
                label = "WaveScrubberScale"
            )

            Box(
                modifier = Modifier
                    .offset(x = animatedOffsetX)
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    fontSize = 11.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                    color = if (isHighlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
