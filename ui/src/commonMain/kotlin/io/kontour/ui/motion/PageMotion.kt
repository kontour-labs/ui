package io.kontour.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import io.kontour.ui.theme.Motion
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * How pages move when one is pushed, popped, or dragged back — in the
 * [BackStyle] they were made for.
 *
 * Three transforms for whatever runs an `AnimatedContent` between pages: a
 * Navigation 3 `NavDisplay` (which `:ui-nav3`'s page strategy wires up for you),
 * or an app's own. Pair each page with [pageEffects] for the parts a transform
 * cannot draw — the dim beneath and the shadow above, while one page slides
 * over another.
 *
 * | | Push and pop | A back gesture |
 * |---|---|---|
 * | [BackStyle.Predictive] | the shared axis: a third of the width and a fade | the page follows the finger one to one, towards whichever edge the gesture came from; the one beneath slides in under it |
 * | [BackStyle.Swipe] | the new page slides over the old, which moves a third of the way and dims | the page follows the finger one to one; the one beneath slides in under it |
 *
 * Under both, a gesture moves the page and nothing else about it: it does not
 * shrink, fade or round its corners.
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
     * The page follows the finger one to one — linear, so a seek to half the
     * gesture is half the travel, the page under the finger rather than ahead of
     * it or behind — and the page beneath slides in under it from a third of the
     * way across.
     *
     * @param swipeEdge Which edge the gesture came from — `NavigationEvent`'s
     *   `EDGE_LEFT`, `EDGE_RIGHT` or `EDGE_NONE`, the `Int` a `NavDisplay`
     *   passes. Under predictive back the page moves away from it, which is the
     *   way the finger goes: Android goes back from either edge. Swipe back
     *   ignores it, because iOS only ever goes back from the leading edge.
     */
    fun predictivePop(swipeEdge: Int): ContentTransform = when {
        reduced -> crossfade(LinearEasing)
        style == BackStyle.Swipe -> swipePop(LinearEasing)
        else -> {
            // +1 when the page leaves to the right.
            val away = when (swipeEdge) {
                NavigationEvent.EDGE_LEFT -> 1
                NavigationEvent.EDGE_RIGHT -> -1
                else -> -forwardSign
            }
            val spec = tween<IntOffset>(SwipeDuration, easing = LinearEasing)
            (
                slideInHorizontally(spec) { w -> (-away * w * Parallax).roundToInt() } togetherWith
                    slideOutHorizontally(spec) { w -> away * w }
                ).apply { targetContentZIndex = -1f }
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
 * While one page slides over another, the page going under dims — a tenth of
 * black at most, gone when it is at rest — and the page on top casts a soft
 * shadow from the edge that is over the other one. Under [BackStyle.Swipe] that
 * is every push and pop, as on iOS. Under [BackStyle.Predictive] it is only a
 * back gesture, the one time a page slides over another there; a push or a pop
 * by a button is the shared axis, with nothing to draw. Nothing at all under
 * reduced motion, and nothing while the page is at rest.
 *
 * @param scope The `AnimatedVisibilityScope` the page is in — what
 *   `AnimatedContent`'s content lambda receives.
 * @param isPop Whether the change under way goes back. Read while the
 *   transition runs, so a lambda rather than a value.
 * @param gestureEdge The edge the back gesture under way came from —
 *   `NavigationEvent`'s `EDGE_LEFT`, `EDGE_RIGHT` or `EDGE_NONE` — or null when
 *   the change is not a gesture. The shadow goes on the side the page is
 *   leaving from, and under [BackStyle.Predictive] nothing is drawn while it is
 *   null.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Modifier.pageEffects(
    motion: PageMotion,
    scope: AnimatedVisibilityScope,
    isPop: () -> Boolean,
    gestureEdge: () -> Int? = { null },
): Modifier {
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
    return this.drawWithContent {
        drawContent()
        if (away == 0f) return@drawWithContent
        val edge = gestureEdge()
        if (motion.style == BackStyle.Predictive && edge == null) return@drawWithContent
        // Underneath: the page leaving on a push, or returning on a pop.
        val entering = transition.targetState == EnterExitState.Visible
        val underneath = if (isPop()) entering else !entering
        if (underneath) {
            drawRect(Color.Black.copy(alpha = MaxDim * away))
            return@drawWithContent
        }
        // On top: a shadow cast past the edge over the page beneath. Outside the
        // bounds, so it needs no room of its own. That edge is the leading one
        // under swipe back; under a predictive gesture it is the one the page
        // is moving away from, which is the one the gesture came from.
        val shadowOnRight = when {
            motion.style == BackStyle.Swipe -> rtl
            edge == NavigationEvent.EDGE_RIGHT -> true
            edge == NavigationEvent.EDGE_LEFT -> false
            else -> rtl
        }
        val width = motion.shadowWidthPx
        val shade = Color.Black.copy(alpha = ShadowAlpha)
        if (shadowOnRight) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(shade, Color.Transparent), startX = size.width, endX = size.width + width),
                topLeft = Offset(size.width, 0f),
                size = Size(width, size.height),
            )
        } else {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, shade), startX = -width, endX = 0f),
                topLeft = Offset(-width, 0f),
                size = Size(width, size.height),
            )
        }
    }
}

/** How long the dim and the shadow take to go, when nothing seeks them. */
private const val PageEffectDuration = 350

/** UIKit's dim over the page beneath, at its darkest. */
private const val MaxDim = 0.1f

/** The shadow the top page casts, at its darkest. */
private const val ShadowAlpha = 0.12f

