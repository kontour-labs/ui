package io.kontour.ui.motion

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Timing for [Modifier.marquee]. */
object MarqueeDefaults {

    /**
     * How long it waits before setting off, and between passes.
     *
     * Long enough to read the beginning standing still. A label that starts
     * moving the moment it appears is one the eye never gets a fixed look at,
     * and the first word is usually the one that identifies the thing —
     * "Elizabeth Quay Bus Station" is answered by the first two words.
     */
    val Pause: Duration = 1600.milliseconds

    /** Reading pace, not conveyor-belt pace. */
    val Velocity: Dp = 40.dp

    /** The gap between the end of one pass and the start of the next. */
    val Gap: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.marqueeGap
}

/**
 * Scrolls this content sideways when it is too wide, and does nothing when it fits.
 *
 * ```
 * Text(
 *     text = stop.name,
 *     maxLines = 1,
 *     modifier = Modifier.marquee(),
 * )
 * ```
 *
 * For the label that is *usually* short and occasionally is not — a stop name, a
 * route headsign, a now-playing line. Safe to apply unconditionally: it measures
 * first and animates only when the content is wider than the space, so the
 * common case costs a comparison.
 *
 * Pair it with `maxLines = 1`. Without that the text wraps instead of
 * overflowing, there is nothing to scroll, and the modifier sits there doing
 * nothing forever.
 *
 * ### It is off under reduced motion, and that is not a degradation
 *
 * Everything else in this library softens under `reduceMotion` — a spring
 * becomes a tween, a slide becomes a fade. This one stops entirely, because it
 * is the one animation in the library that never ends. Perpetual motion in the
 * corner of the eye is the specific thing that preference exists to stop, and
 * there is no gentler version of "forever". The text truncates instead, which is
 * what it would have done without this modifier at all.
 *
 * ### Why it wraps foundation's rather than replacing it
 *
 * `basicMarquee` already measures, already stops when the content fits, already
 * handles right-to-left and already does the right thing when the node leaves
 * the composition. What it does not have is this library's opinion about pace,
 * or any knowledge of `reduceMotion`. Those are the two things worth owning, and
 * they are both a handful of lines — reimplementing the rest would be a second
 * copy of a solved problem, to be kept in step with the first.
 *
 * The one exception is [draggable]. Pulling the text needs the offset, which
 * `basicMarquee` keeps to itself, so a draggable marquee measures, scrolls and
 * draws on its own. It is opt-in, so every marquee that does not ask for it is
 * still foundation's.
 *
 * ### Dragging it
 *
 * With `draggable = true`, a finger or a mouse can pull the text sideways and
 * read it at its own pace instead of waiting for the next pass. Holding it
 * stops it. Let go and it coasts, then, after [pause], carries on from where it
 * was left and finishes the pass it was in. With no passes left it goes back
 * the shorter way to its start, so it never settles part-way through the text.
 *
 * Under reduced motion it still drags, because motion the user makes is not
 * motion done to them, but it never sets off by itself. Until it is first
 * pulled, the text is truncated as it would be without the modifier.
 *
 * It takes a sideways drag only when the text is too wide. Then, within the
 * label, it wins over anything around it that also swipes sideways, such as a
 * pager or a row's swipe actions. That is why it is off by default.
 *
 * @param iterations How many passes before it settles. `Int.MAX_VALUE` for a
 *   ticker that never stops — appropriate for a live status line, and not for a
 *   list of labels, where a dozen rows all scrolling at once is a screen nobody
 *   can read.
 * @param draggable Whether the text can be pulled sideways to read it by hand.
 *   See **Dragging it**.
 */
@Composable
fun Modifier.marquee(
    iterations: Int = 3,
    pause: Duration = MarqueeDefaults.Pause,
    velocity: Dp = MarqueeDefaults.Velocity,
    gap: Dp = MarqueeDefaults.Gap,
    draggable: Boolean = false,
): Modifier = if (draggable) {
    this.draggableMarquee(iterations, pause, velocity, gap, runs = !Theme.motion.reduceMotion)
} else if (Theme.motion.reduceMotion) {
    this
} else {
    this.basicMarquee(
        iterations = iterations,
        repeatDelayMillis = pause.inWholeMilliseconds.toInt(),
        initialDelayMillis = pause.inWholeMilliseconds.toInt(),
        // Remembered: a spacing has no equality, so a new one each composition
        // is a changed parameter and the marquee measures again for it.
        spacing = remember(gap) { MarqueeSpacing(gap) },
        velocity = velocity,
    )
}

/**
 * Where a draggable marquee is: how wide its text and its box are, and how far
 * through a pass it has got.
 *
 * [offset] runs from 0, the text's start against the box's start edge, to
 * [cycle], where the next copy has arrived there. It is measured in the
 * reading direction, so the same number means the same place in either layout
 * direction.
 */
@Stable
private class MarqueeStrip {
    var content by mutableIntStateOf(0)
    var container by mutableIntStateOf(0)
    var gap by mutableIntStateOf(0)
    var offset by mutableFloatStateOf(0f)

    /** Whether a finger or the mouse has it. */
    var held by mutableStateOf(false)

    /**
     * Whether it has been pulled since it last rested at its start. Only read
     * under reduced motion, where the text is truncated until it is pulled.
     */
    var moved by mutableStateOf(false)

    /** The velocity it was let go at, spent by the next run of the effect. */
    var thrown = 0f

    /** One whole pass, or 0 when the text fits and nothing moves. */
    val cycle: Int get() = if (content > container) content + gap else 0

    fun moveBy(px: Float) {
        val cycle = cycle.toFloat()
        if (cycle > 0f) offset = (offset + px).mod(cycle)
    }

    /**
     * Waits [pause], then moves to [to] at reading pace, [pxPerSecond].
     *
     * The wait is the animation's own delay rather than a `delay`, so it runs
     * on the frame clock, as foundation's marquee waits, and not on the wall's.
     */
    suspend fun glide(to: Float, pxPerSecond: Float, pause: Duration) {
        val millis = (abs(to - offset) / pxPerSecond * 1000).roundToInt()
        val spec = tween<Float>(millis, delayMillis = pause.inWholeMilliseconds.toInt(), easing = LinearEasing)
        animate(offset, to, animationSpec = spec) { value, _ ->
            offset = value
        }
    }
}

@Composable
private fun Modifier.draggableMarquee(
    iterations: Int,
    pause: Duration,
    velocity: Dp,
    gap: Dp,
    runs: Boolean,
): Modifier {
    val strip = remember { MarqueeStrip() }
    val density = LocalDensity.current
    strip.gap = with(density) { gap.roundToPx() }
    val pxPerSecond = with(density) { velocity.toPx() }
    // The text's start is on the right in a right-to-left layout, and it reads
    // towards the left, so a pass moves the other way.
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val decay = rememberSplineBasedDecay<Float>()
    val cycle = strip.cycle
    // A new text or a new count starts again from the beginning, as
    // foundation's does when its parameters change.
    val passesLeft: MutableIntState = remember(iterations, cycle) { mutableIntStateOf(iterations) }

    // At a fixed pace, as foundation's marquee runs, whatever the system's
    // animation scale: at a scale of 0 every pause and every pass would take
    // a frame, and an endless marquee would flicker through passes forever.
    LaunchedEffect(strip, cycle, passesLeft, strip.held, runs, pause, pxPerSecond) {
        withContext(FixedPace) {
            if (cycle == 0 || strip.offset > cycle) {
                strip.offset = 0f
                strip.moved = false
            }
            if (cycle == 0 || strip.held) return@withContext
            val thrown = strip.thrown
            strip.thrown = 0f
            if (thrown != 0f) {
                var last = 0f
                AnimationState(initialValue = 0f, initialVelocity = thrown).animateDecay(decay) {
                    strip.moveBy(value - last)
                    last = value
                }
            }
            if (!runs) return@withContext
            if (strip.offset != 0f) {
                // With passes left it finishes the one it is in. With none it goes
                // home the shorter way, rather than reading the whole text again
                // for the sake of a nudge.
                val forward = passesLeft.intValue > 0 || strip.offset > cycle / 2f
                strip.glide(to = if (forward) cycle.toFloat() else 0f, pxPerSecond, pause)
                strip.offset = 0f
                if (forward) passesLeft.intValue = (passesLeft.intValue - 1).coerceAtLeast(0)
            }
            strip.moved = false
            while (passesLeft.intValue > 0) {
                strip.glide(to = cycle.toFloat(), pxPerSecond, pause)
                strip.offset = 0f
                passesLeft.intValue--
            }
        }
    }

    return this
        .draggable(
            state = rememberDraggableState { delta -> strip.moveBy(-delta * sign) },
            orientation = Orientation.Horizontal,
            // Text that fits takes no drag from what it sits in.
            enabled = cycle > 0,
            onDragStarted = {
                strip.held = true
                strip.moved = true
            },
            onDragStopped = { letGoAt ->
                strip.thrown = -letGoAt * sign
                strip.held = false
            },
        )
        .drawWithContent {
            val pass = strip.cycle
            // Fits, or truncated in its box until pulled: drawn where it is.
            if (pass == 0 || !runs && !strip.moved) {
                drawContent()
                return@drawWithContent
            }
            val start = if (sign < 0f) size.width - strip.content else 0f
            val at = start - strip.offset * sign
            // Clipped across only: a glyph that reaches above or below its
            // line keeps its ink.
            clipRect(left = 0f, top = -size.height, right = size.width, bottom = size.height * 2) {
                translate(left = at) { this@drawWithContent.drawContent() }
                translate(left = at + pass * sign) { this@drawWithContent.drawContent() }
            }
        }
        .layout { measurable, constraints ->
            // Under reduced motion, and before it has been pulled, the text is
            // measured in its box and truncates as it would without the
            // modifier. Only its width is asked for in full, to know whether
            // there is anything to pull.
            val truncated = !runs && !strip.moved
            val full = if (truncated) measurable.maxIntrinsicWidth(constraints.maxHeight) else null
            val placeable = measurable.measure(
                if (truncated) constraints else constraints.copy(maxWidth = Constraints.Infinity),
            )
            val width = constraints.constrainWidth(placeable.width)
            strip.content = full ?: placeable.width
            strip.container = width
            layout(width, placeable.height) {
                if (placeable.width > width) placeable.place(0, 0) else placeable.placeRelative(0, 0)
            }
        }
}

/** The pace foundation's marquee runs at, whatever the system's animation scale. */
private object FixedPace : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}
