package io.kontour.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay

/** How a [revealOnScroll] element arrives. */
enum class RevealVariant { FadeUp, FadeScale, FadeStart, FadeEnd }

/**
 * Fades content in the first time it scrolls into view.
 *
 * ```kotlin
 * Card(Modifier.revealOnScroll()) { … }
 * Card(Modifier.revealOnScroll(delayMillis = 80)) { … }
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
    threshold: Float = 0.1f,
    delayMillis: Long = 0,
    distance: Dp = 24.dp,
): Modifier {
    val motion = Theme.motion
    if (motion.reduceMotion) return this

    var revealed by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible && !revealed) {
            if (delayMillis > 0) delay(delayMillis)
            revealed = true
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springGentle),
        label = "reveal",
    )
    val distancePx = with(androidx.compose.ui.platform.LocalDensity.current) { distance.toPx() }

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
        .graphicsLayer {
            alpha = progress
            val remaining = 1f - progress
            when (variant) {
                RevealVariant.FadeUp -> translationY = distancePx * remaining
                RevealVariant.FadeScale -> {
                    scaleX = 0.96f + 0.04f * progress
                    scaleY = 0.96f + 0.04f * progress
                }

                RevealVariant.FadeStart -> translationX = -distancePx * remaining
                RevealVariant.FadeEnd -> translationX = distancePx * remaining
            }
        }
}

/**
 * Drifts content vertically as the page scrolls past it.
 *
 * ```kotlin
 * Image(hero, Modifier.parallax(strength = 0.08f))
 * ```
 *
 * A port of the site's `parallax.svelte.ts`, including its two important
 * details: the offset is derived from how far the element's **centre** is from
 * the viewport's centre — so it drifts both ways around the midpoint rather than
 * only downward — and it is spring-smoothed rather than tracking scroll 1:1,
 * which is what makes it read as drift instead of as a second scroll speed.
 *
 * A no-op under reduced motion. Parallax is one of the few effects that reliably
 * makes people motion-sick, so this is not a nicety.
 *
 * @param strength Fraction of the distance from centre. `0.08` is the site's
 *   value and is about as much as is pleasant; past `0.2` the element visibly
 *   detaches from the page.
 */
@Composable
fun Modifier.parallax(strength: Float = 0.08f): Modifier {
    val motion = Theme.motion
    if (motion.reduceMotion) return this

    val offset = remember { Animatable(0f) }
    var target by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(target) {
        offset.animateTo(target, motion.springGentle.spec())
    }

    return this
        .onGloballyPositioned { coordinates ->
            if (!coordinates.isAttached) return@onGloballyPositioned
            val root = coordinates.findRootCoordinates()
            val bounds = coordinates.boundsInRoot()
            val viewportMid = root.size.height / 2f
            val nodeMid = bounds.top + bounds.height / 2f
            val next = (viewportMid - nodeMid) * strength

            // `boundsInRoot` includes the translation this modifier applied, so
            // each reading feeds the next. It converges — the fixed point is
            // `d · strength / (1 + strength)`, and the web original settles the
            // same way — but the last few pixels of that convergence would keep
            // scheduling layout passes for nothing. Stop once it stops moving.
            if (kotlin.math.abs(next - target) > 0.5f) target = next
        }
        .graphicsLayer { translationY = offset.value }
}
