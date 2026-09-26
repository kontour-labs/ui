package io.kontour.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import io.kontour.ui.platform.platformDeviceCorners
import io.kontour.ui.theme.Motion
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.concentricWith
import kotlin.math.roundToInt

/**
 * How pages move when one is pushed, popped, or dragged back — in the
 * [BackStyle] they were made for.
 *
 * Three transforms for whatever runs an `AnimatedContent` between pages: a
 * Navigation 3 `NavDisplay` (which `:ui-nav3`'s page strategy wires up for you),
 * or an app's own. Pair each page with [pageEffects] for the parts a transform
 * cannot draw — the dim beneath and the shadow above under
 * [BackStyle.Swipe], the rounded corners of a page being lifted off under
 * [BackStyle.Predictive].
 *
 * | | Push and pop | A back gesture |
 * |---|---|---|
 * | [BackStyle.Predictive] | the shared axis: a third of the width and a fade | the page scales to nine tenths and drifts with the finger; the one beneath fades in from a little larger |
 * | [BackStyle.Swipe] | the new page slides over the old, which moves a third of the way and dims | the top page follows the finger one to one; the one beneath slides in under it |
 *
 * With reduced motion every transform is a crossfade, and a gesture only seeks
 * the fade.
 */
@Immutable
class PageMotion internal constructor(
    /** The feel these transforms were made for. */
    val style: BackStyle,
    private val motion: Motion,
    private val density: Density,
    private val layoutDirection: LayoutDirection,
) {
    private val reduced get() = motion.reduceMotion

    /** Which way "forward" is, in pixels: to the left in a left-to-right layout. */
    private val forwardSign get() = if (layoutDirection == LayoutDirection.Ltr) -1 else 1

    /** A new page going on top of the stack. */
    fun push(): ContentTransform = when {
        reduced -> crossfade()
        style == BackStyle.Swipe -> {
            val spec = tween<IntOffset>(SwipeDuration, easing = motion.standard)
            slideInHorizontally(spec) { w -> -forwardSign * w } togetherWith
                slideOutHorizontally(spec) { w -> (forwardSign * w * Parallax).roundToInt() }
        }
        else -> sharedAxis(forward = true)
    }

    /** The top page going away, by a button, a key or a gesture let go at once. */
    fun pop(): ContentTransform = when {
        reduced -> crossfade()
        style == BackStyle.Swipe -> swipePop(motion.standard)
        else -> sharedAxis(forward = false)
    }

    /**
     * The top page going away under a back gesture, seeked by its progress and
     * finished from wherever the hand let go.
     *
     * @param swipeEdge Which edge the gesture came from — `NavigationEvent`'s
     *   `EDGE_LEFT`, `EDGE_RIGHT` or `EDGE_NONE`, the `Int` a `NavDisplay`
     *   passes. Predictive back drifts away from it; swipe back ignores it,
     *   because iOS only ever goes back from the leading edge.
     */
    fun predictivePop(swipeEdge: Int): ContentTransform = when {
        reduced -> crossfade(LinearEasing)
        // Linear, so a seek to half the gesture is half the travel: the page is
        // under the finger, not ahead of it or behind.
        style == BackStyle.Swipe -> swipePop(LinearEasing)
        else -> {
            val duration = motion.slow
            // Decelerating, as Material asks: the gesture is most visible early.
            val gesture = CubicBezierEasing(0f, 0f, 0f, 1f)
            val drift = with(density) { 8.dp.roundToPx() }
            val direction = when (swipeEdge) {
                NavigationEvent.EDGE_LEFT -> 1
                NavigationEvent.EDGE_RIGHT -> -1
                else -> -forwardSign
            }
            val fadeOutBy = (duration * FadeThrough).roundToInt()
            val incoming = fadeIn(tween(duration - fadeOutBy, delayMillis = fadeOutBy, easing = LinearEasing)) +
                scaleIn(tween(duration, easing = gesture), initialScale = 1.1f)
            val outgoing = fadeOut(tween(fadeOutBy, easing = LinearEasing)) +
                scaleOut(tween(duration, easing = gesture), targetScale = 0.9f) +
                slideOutHorizontally(tween(duration, easing = gesture)) { w ->
                    // (width / 20) − 8dp, Material's reach for a drifting page.
                    direction * (w / 20 - drift).coerceAtLeast(0)
                }
            (incoming togetherWith outgoing).apply { targetContentZIndex = -1f }
        }
    }

    private fun swipePop(easing: Easing): ContentTransform {
        val spec = tween<IntOffset>(SwipeDuration, easing = easing)
        return (
            slideInHorizontally(spec) { w -> (forwardSign * w * Parallax).roundToInt() } togetherWith
                slideOutHorizontally(spec) { w -> -forwardSign * w }
            ).apply {
            // The page returned to goes *under* the one leaving it.
            targetContentZIndex = -1f
        }
    }

    private fun sharedAxis(forward: Boolean): ContentTransform {
        val third = { w: Int -> w / 3 }
        val sign = if (forward) -forwardSign else forwardSign
        return (
            slideInHorizontally(motion.tweenDefault()) { w -> sign * third(w) } + fadeIn(motion.tweenFast())
            ) togetherWith (
            slideOutHorizontally(motion.tweenDefault()) { w -> -sign * third(w) } + fadeOut(motion.tweenFast())
            )
    }

    private fun crossfade(easing: Easing = motion.standard): ContentTransform =
        fadeIn(tween(motion.fast, easing = easing)) togetherWith fadeOut(tween(motion.fast, easing = easing))

    internal val shadowWidthPx: Float get() = with(density) { 16.dp.toPx() }

    private companion object {
        /** UIKit's: the page beneath starts a third of the way across. */
        const val Parallax = 0.3f

        /** UIKit's push and pop, near enough — a little over a third of a second. */
        const val SwipeDuration = 350

        /** Material's: the outgoing page is gone by 35% and the incoming appears after. */
        const val FadeThrough = 0.35f
    }
}

/**
 * The [PageMotion] for [style], from the theme's motion, the density and the
 * layout direction here.
 */
@Composable
fun rememberPageMotion(style: BackStyle = LocalBackStyle.current): PageMotion {
    val motion = Theme.motion
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    return remember(style, motion, density, direction) { PageMotion(style, motion, density, direction) }
}

/**
 * What a page transform cannot draw, drawn on each page of an `AnimatedContent`.
 *
 * Under [BackStyle.Swipe]: the page going under the other one dims — a tenth
 * of black at most, gone when it is at rest — and the page on top casts a soft
 * shadow from its leading edge. Under [BackStyle.Predictive]: a page lifted by
 * a gesture takes rounded corners. Nothing at all under reduced motion, and
 * nothing while the page is at rest.
 *
 * @param scope The `AnimatedVisibilityScope` the page is in — what
 *   `AnimatedContent`'s content lambda receives.
 * @param isPop Whether the change under way goes back. Read while the
 *   transition runs, so a lambda rather than a value.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Modifier.pageEffects(motion: PageMotion, scope: AnimatedVisibilityScope, isPop: () -> Boolean): Modifier {
    if (Theme.motion.reduceMotion) return this
    val transition = scope.transition
    // How far this page is from rest, 0 to 1, driven by the same transition
    // the transform is — so a seek moves it too, and it needs no clock of its
    // own.
    val away by transition.animateFloat(
        transitionSpec = { tween(PageEffectDuration, easing = LinearEasing) },
        label = "page away from rest",
    ) { state -> if (state == EnterExitState.Visible) 0f else 1f }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return when (motion.style) {
        BackStyle.Swipe -> this.drawWithContent {
            drawContent()
            if (away == 0f) return@drawWithContent
            // Underneath: the page leaving on a push, or returning on a pop.
            val entering = transition.targetState == EnterExitState.Visible
            val underneath = if (isPop()) entering else !entering
            if (underneath) {
                drawRect(Color.Black.copy(alpha = MaxDim * away))
            } else {
                // On top: a shadow cast past the leading edge onto the page
                // beneath. Outside the bounds, so it needs no room of its own.
                val width = motion.shadowWidthPx
                val x = if (rtl) size.width else -width
                val edge = if (rtl) Offset(size.width, 0f) else Offset(0f, 0f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = if (rtl) {
                            listOf(Color.Black.copy(alpha = ShadowAlpha), Color.Transparent)
                        } else {
                            listOf(Color.Transparent, Color.Black.copy(alpha = ShadowAlpha))
                        },
                        startX = if (rtl) edge.x else x,
                        endX = if (rtl) edge.x + width else edge.x,
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(width, size.height),
                )
            }
        }
        BackStyle.Predictive -> {
            // The display's own corner where the platform says what it is, so
            // a page lifted off the screen keeps the shape it had on it; the
            // theme's largest corner, its guess at that radius, elsewhere.
            val largest = Theme.shapes.extraLarge
            val device = platformDeviceCorners()
            val direction = LocalLayoutDirection.current
            val corner = remember(largest, device, direction) { largest.concentricWith(device, direction = direction) }
            this.graphicsLayer {
                // Only the page being lifted off by a pop, and only while it is.
                val leaving = transition.targetState != EnterExitState.Visible
                if (isPop() && leaving && away > 0f) {
                    shape = corner
                    clip = true
                } else {
                    shape = RectangleShape
                    clip = false
                }
            }
        }
    }
}

/** How long the dim and the shadow take to go, when nothing seeks them. */
private const val PageEffectDuration = 350

/** UIKit's dim over the page beneath, at its darkest. */
private const val MaxDim = 0.1f

/** The shadow the top page casts, at its darkest. */
private const val ShadowAlpha = 0.12f

