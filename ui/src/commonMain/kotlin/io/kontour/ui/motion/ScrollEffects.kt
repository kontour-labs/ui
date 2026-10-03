package io.kontour.ui.motion

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.math.tanh
import kotlin.time.Duration

/** How a [revealOnScroll] element arrives. */
enum class RevealVariant { FadeUp, FadeScale, FadeStart, FadeEnd }

/**
 * Fades content in the first time it scrolls into view.
 *
 * ```kotlin
 * Card(Modifier.revealOnScroll()) { … }
 * Card(Modifier.revealOnScroll(delay = 80.milliseconds)) { … }
 * ```
 *
 * A port of the marketing site's `reveal.ts`, and it keeps that action's two
 * decisions:
 *
 * - **Once only.** An element that re-animates every time it scrolls back into
 *   view turns a page into a slot machine.
 * - **A visibility threshold, not a boundary crossing.** It fires when a tenth
 *   of the element is showing, so something taller than the viewport does not
 *   wait until it is fully on screen — which would be never.
 *
 * Where the web uses an `IntersectionObserver`, this uses
 * `onGloballyPositioned` against the root's bounds. Same question, and the only
 * one Compose can answer without a scroll state.
 *
 * **Use it sparingly, and never on the first screenful.** Content that fades in
 * as you arrive is content you cannot read yet, and on a slow device the fade
 * lands after the user has already looked. It is for a long marketing page, not
 * for a list of departures.
 *
 * A no-op under reduced motion: the element is simply there.
 */
@Composable
fun Modifier.revealOnScroll(
    variant: RevealVariant = RevealVariant.FadeUp,
    threshold: Float = ScrollEffectDefaults.RevealThreshold,
    delay: Duration = Duration.ZERO,
    distance: Dp = ScrollEffectDefaults.RevealDistance,
): Modifier {
    val motion = Theme.motion
    if (motion.reduceMotion) return this

    var revealed by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible && !revealed) {
            if (delay > Duration.ZERO) delay(delay)
            revealed = true
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springGentle),
        label = "reveal",
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val distancePx = with(density) { distance.toPx() }
    // The largest shadow in the scale, so whatever this is put on keeps its own.
    val roomPx = with(density) { Theme.elevation.overlay.bleed.toPx() }
    val fade = remember { Paint() }
    // Start and end are the line's: in right to left, "from the start" is from the right.
    val towardsEnd = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    return this
        .onGloballyPositioned { coordinates ->
            if (revealed || !coordinates.isAttached) return@onGloballyPositioned
            val root = coordinates.findRootCoordinates()
            val bounds = coordinates.boundsInRoot()
            val height = coordinates.size.height.toFloat()
            if (height <= 0f) return@onGloballyPositioned

            // How much of it is inside the window right now.
            val shown = (minOf(bounds.bottom, root.size.height.toFloat()) -
                maxOf(bounds.top, 0f)).coerceAtLeast(0f)
            if (shown / height >= threshold) visible = true
        }
        // Moves only. An `alpha` here would composite into a buffer the size of
        // the element, and a card's shadow lies outside the card: for the whole
        // fade the shadow was cut off at the card's edge, then came back in one
        // frame as the fade ended. `overlayAppearance` has the full analysis.
        .graphicsLayer {
            val remaining = 1f - progress
            when (variant) {
                RevealVariant.FadeUp -> translationY = distancePx * remaining
                RevealVariant.FadeScale -> {
                    scaleX = 0.96f + 0.04f * progress
                    scaleY = 0.96f + 0.04f * progress
                }

                RevealVariant.FadeStart -> translationX = -distancePx * remaining * towardsEnd
                RevealVariant.FadeEnd -> translationX = distancePx * remaining * towardsEnd
            }
        }
        // The fade, in a buffer with room around the element for its shadow, so
        // the shadow fades with the card rather than vanishing under it. Only
        // while it is arriving: at rest nothing is composited at all.
        .drawWithContent {
            val alpha = progress
            when {
                alpha >= 1f -> drawContent()
                alpha > 0f -> drawIntoCanvas { canvas ->
                    fade.alpha = alpha
                    canvas.saveLayer(Rect(-roomPx, -roomPx, size.width + roomPx, size.height + roomPx), fade)
                    drawContent()
                    canvas.restore()
                }
            }
        }
}

/**
 * Drifts content vertically as the page scrolls past it.
 *
 * ```kotlin
 * Image(hero, Modifier.parallax())
 * ```
 *
 * The offset is a share of how far the element's **centre** is from the
 * window's centre, so it drifts both ways around the midpoint rather than only
 * downward, and it eases off towards [ParallaxReach] rather than growing with
 * the window: a small difference of speed, which reads as depth.
 *
 * **It moves with the page, and stops with it.** It was spring-smoothed, after
 * the site's `parallax.svelte.ts`, and the spring was started over on nearly
 * every frame of a scroll: the content moved rigidly with the page while it
 * scrolled, then glided on by itself for half a second after it stopped, by as
 * much as 30dp near the window's edges — *"the "drift" scroll effect feels like
 * it's trying too hard. it needs to be a bit subtler and smoother"*. Now the
 * offset is where the element is, every frame, and changes only what is drawn.
 *
 * A no-op under reduced motion. Parallax is one of the few effects that reliably
 * makes people motion-sick, so this is not a nicety.
 *
 * @param strength Fraction of the distance from centre. `0.05`, the default, is
 *   about as much as is pleasant; past `0.2` the element visibly detaches.
 */
@Composable
fun Modifier.parallax(strength: Float = ScrollEffectDefaults.ParallaxStrength): Modifier {
    if (Theme.motion.reduceMotion) return this
    val reach = with(LocalDensity.current) { ParallaxReach.toPx() }
    val offset = remember { mutableFloatStateOf(0f) }

    return this
        // Before the layer, so the position read is where the element is laid
        // out, not where it was last drawn: no reading feeds the next.
        .onGloballyPositioned { coordinates ->
            if (!coordinates.isAttached) return@onGloballyPositioned
            val root = coordinates.findRootCoordinates()
            val bounds = coordinates.boundsInRoot()
            val fromMiddle = root.size.height / 2f - (bounds.top + bounds.height / 2f)
            // Eased off towards the reach rather than cut at it, so an element
            // nearing the window's edge slows rather than stops.
            offset.floatValue = reach * tanh(fromMiddle * strength / reach)
        }
        // Read in the layer, so a scroll redraws it and recomposes nothing.
        .graphicsLayer { translationY = offset.floatValue }
}

/** How far at most a [parallax] layer drifts from its place, near the window's edges. */
private val ParallaxReach = 16.dp

/** What the scroll-driven modifiers take by default. */
object ScrollEffectDefaults {
    /** How much of an element has to be on screen before [revealOnScroll] plays. */
    const val RevealThreshold: Float = 0.1f

    /** How far a [revealOnScroll] travels as it arrives. */
    val RevealDistance: Dp = 24.dp

    /** How far a [parallax] layer moves, as a fraction of its distance from the centre. */
    const val ParallaxStrength: Float = 0.05f
}
