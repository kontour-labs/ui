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
     * How long it waits before setting off, and between passes. Back and forth,
     * it waits this long at each end.
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
 * How a [Modifier.marquee] travels through text too wide for its box.
 */
enum class MarqueeTravel {

    /**
     * Round and round: the text scrolls off its start, and a second copy
     * follows it in a gap behind, so every pass sets off the same way. The
     * default, and foundation's `basicMarquee`.
     */
    Loop,

    /**
     * Out and back: the text glides until its end reaches the box's end edge,
     * waits, and glides back to its start. Only one copy is ever drawn, and it
     * goes no further than it has to.
     */
    BackAndForth,
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
 * ### Back and forth
 *
 * With `travel = MarqueeTravel.BackAndForth` the text does not loop. It waits
 * [pause] at its start, glides until its end reaches the box's end edge, waits
 * again, and glides back. It travels only as far as the text overflows, and
 * there is no gap and no second copy.
 *
 * Reach for it where seeing the start and the end matters, and a wrap-around
 * would split the text: a now-playing line, or a long stop name. A loop brings
 * the start back in a gap behind the end, so for a moment the box shows the
 * last words of one copy beside the first words of the next — "Stand E
 * Elizabeth Quay" — which reads as a stop that does not exist. Back and forth,
 * the box only ever shows one stretch of the text, in order. A loop suits a
 * ticker better, where the text is a stream and the wrap is the point.
 *
 * ```
 * Text(
 *     text = stop.name,
 *     maxLines = 1,
 *     modifier = Modifier.marquee(travel = MarqueeTravel.BackAndForth),
 * )
 * ```
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
 * The exceptions are [draggable] and [MarqueeTravel.BackAndForth]. Pulling the
 * text needs the offset, which `basicMarquee` keeps to itself, and it only ever
 * loops. So either of them measures, scrolls and draws on its own. Both are
 * opt-in, so every marquee that asks for neither is still foundation's.
 *
 * ### Dragging it
 *
 * With `draggable = true`, a finger or a mouse can pull the text sideways and
 * read it at its own pace instead of waiting for the next pass. Holding it
 * stops it. Let go and it coasts, then, after [pause], carries on from where it
 * was left and finishes the pass it was in. With no passes left it goes back
 * the shorter way to its start, so it never settles part-way through the text.
 *
 * Back and forth, it pulls only as far as either end of the text, and it does
 * not wrap round. Let go, it finishes the way it was going: on its way out, out
 * to the end and home again; on its way home, home. With no passes left it
 * goes straight home.
 *
 * Under reduced motion it still drags, because motion the user makes is not
 * motion done to them, but it never sets off by itself. Until it is first
 * pulled, the text is truncated as it would be without the modifier.
 *
 * It takes a sideways drag only when the text is too wide. Then, within the
 * label, it wins over anything around it that also swipes sideways, such as a
 * pager or a row's swipe actions. That is why it is off by default.
 *
 * @param iterations How many passes before it settles. Back and forth, a pass
 *   is there and back, so it always settles at its start. `Int.MAX_VALUE` for a
 *   ticker that never stops — appropriate for a live status line, and not for a
 *   list of labels, where a dozen rows all scrolling at once is a screen nobody
 *   can read.
 * @param gap How far behind the text its next copy follows. Back and forth
 *   there is no next copy, and it is not used.
 * @param draggable Whether the text can be pulled sideways to read it by hand.
 *   See **Dragging it**.
 * @param travel Whether the text loops round or goes back and forth. See
 *   **Back and forth**.
 */
@Composable
fun Modifier.marquee(
    iterations: Int = 3,
    pause: Duration = MarqueeDefaults.Pause,
    velocity: Dp = MarqueeDefaults.Velocity,
    gap: Dp = MarqueeDefaults.Gap,
    draggable: Boolean = false,
    travel: MarqueeTravel = MarqueeTravel.Loop,
): Modifier = if (draggable || travel == MarqueeTravel.BackAndForth && !Theme.motion.reduceMotion) {
    // A new strip for a new travel: an offset part-way round a loop means
    // nothing to one going back and forth, and the other way round.
    val strip = remember(travel) { MarqueeStrip(bounces = travel == MarqueeTravel.BackAndForth) }
    this.scrollingMarquee(strip, iterations, pause, velocity, gap, draggable, runs = !Theme.motion.reduceMotion)
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
 * Where a marquee that scrolls on its own is: how wide its text and its box
 * are, and how far along it has got.
 *
 * [offset] runs from 0, the text's start against the box's start edge, to
 * [reach]. It is measured in the reading direction, so the same number means
 * the same place in either layout direction.
 *
 * Internal rather than private so a test can read where the text is, which a
 * still cannot say and a moving picture says only roughly.
 */
@Stable
internal class MarqueeStrip(
    /** Whether it goes back and forth, rather than looping round. */
    val bounces: Boolean,
) {
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

    /**
     * Whether, going back and forth, it is on its way out or waiting at its
     * start to set off, rather than on its way home. It is what a let-go
     * carries on with, so it is only ever changed at an end.
     */
    var outbound = true

    /** The velocity it was let go at, spent by the next run of the effect. */
    var thrown = 0f

    /**
     * How far it goes, or 0 when the text fits and nothing moves. Looping, one
     * whole pass, to where the next copy has arrived at the start. Back and
     * forth, only until the text's end reaches the box's end edge.
     */
    val reach: Int
        get() = when {
            content <= container -> 0
            bounces -> content - container
            else -> content + gap
        }

    /**
     * Moves it [px] further along, and says whether an end stopped it short.
     * Looping, there is no end: past a whole pass it is the next copy's start.
     */
    fun moveBy(px: Float): Boolean {
        val reach = reach.toFloat()
        if (reach == 0f) return true
        if (!bounces) {
            offset = (offset + px).mod(reach)
            return false
        }
        val to = offset + px
        offset = to.coerceIn(0f, reach)
        return offset != to
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
            // Grabbed, it is the finger's. The effect hears of it a frame
            // later, and a frame of the glide in between would put the text
            // back where the glide had it, under the finger's first move.
            if (!held) offset = value
        }
    }

    /**
     * Loops round until [passesLeft] runs out. Let go part-way with passes
     * left, it finishes the one it is in. With none it goes home the shorter
     * way, rather than reading the whole text again for the sake of a nudge.
     */
    suspend fun loop(passesLeft: MutableIntState, pxPerSecond: Float, pause: Duration) {
        val reach = reach.toFloat()
        if (offset != 0f) {
            val forward = passesLeft.intValue > 0 || offset > reach / 2f
            glide(to = if (forward) reach else 0f, pxPerSecond, pause)
            offset = 0f
            if (forward) passesLeft.intValue = (passesLeft.intValue - 1).coerceAtLeast(0)
        }
        moved = false
        while (passesLeft.intValue > 0) {
            glide(to = reach, pxPerSecond, pause)
            offset = 0f
            passesLeft.intValue--
        }
    }

    /**
     * Goes out and back until [passesLeft] runs out, a pass being there and
     * back, so it always settles at its start.
     *
     * Let go part-way, it finishes the way it was going: on its way out, out
     * to the end and home again; on its way home, home. With no passes left it
     * goes straight home, since the end is not where it is meant to rest.
     */
    suspend fun bounce(passesLeft: MutableIntState, pxPerSecond: Float, pause: Duration) {
        val reach = reach.toFloat()
        if (offset != 0f || !outbound) {
            if (outbound && passesLeft.intValue > 0) {
                glide(to = reach, pxPerSecond, pause)
                outbound = false
            }
            // Pulled all the way home on its way there, it has finished the
            // pass already, and waiting here as well would be a second pause.
            if (offset != 0f) glide(to = 0f, pxPerSecond, pause)
            outbound = true
            passesLeft.intValue = (passesLeft.intValue - 1).coerceAtLeast(0)
        }
        moved = false
        while (passesLeft.intValue > 0) {
            glide(to = reach, pxPerSecond, pause)
            outbound = false
            glide(to = 0f, pxPerSecond, pause)
            outbound = true
            passesLeft.intValue--
        }
    }
}

/**
 * A marquee that measures, scrolls and draws on its own, because it can be
 * pulled, goes back and forth, or both. [strip] holds where it is.
 */
@Composable
internal fun Modifier.scrollingMarquee(
    strip: MarqueeStrip,
    iterations: Int,
    pause: Duration,
    velocity: Dp,
    gap: Dp,
    draggable: Boolean,
    runs: Boolean,
): Modifier {
    val density = LocalDensity.current
    strip.gap = with(density) { gap.roundToPx() }
    val pxPerSecond = with(density) { velocity.toPx() }
    // The text's start is on the right in a right-to-left layout, and it reads
    // towards the left, so a pass moves the other way.
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val decay = rememberSplineBasedDecay<Float>()
    val reach = strip.reach
    // A new text or a new count starts again from the beginning, as
    // foundation's does when its parameters change.
    val passesLeft: MutableIntState = remember(iterations, reach) { mutableIntStateOf(iterations) }

    // At a fixed pace, as foundation's marquee runs, whatever the system's
    // animation scale: at a scale of 0 every pause and every pass would take
    // a frame, and an endless marquee would flicker through passes forever.
    LaunchedEffect(strip, reach, passesLeft, strip.held, runs, pause, pxPerSecond) {
        withContext(FixedPace) {
            if (reach == 0 || strip.offset > reach) {
                strip.offset = 0f
                strip.outbound = true
                strip.moved = false
            }
            if (reach == 0 || strip.held) return@withContext
            val thrown = strip.thrown
            strip.thrown = 0f
            if (thrown != 0f) {
                var last = 0f
                AnimationState(initialValue = 0f, initialVelocity = thrown).animateDecay(decay) {
                    // Thrown against an end, it stops there rather than
                    // pressing on it for the rest of the coast.
                    if (strip.moveBy(value - last)) cancelAnimation()
                    last = value
                }
            }
            if (!runs) return@withContext
            if (strip.bounces) {
                strip.bounce(passesLeft, pxPerSecond, pause)
            } else {
                strip.loop(passesLeft, pxPerSecond, pause)
            }
        }
    }

    return this
        .draggable(
            state = rememberDraggableState { delta -> strip.moveBy(-delta * sign) },
            orientation = Orientation.Horizontal,
            // Text that fits takes no drag from what it sits in, and nor does
            // a marquee that was not asked to be pulled.
            enabled = draggable && reach > 0,
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
            val far = strip.reach
            // Fits, or truncated in its box until pulled: drawn where it is.
            if (far == 0 || !runs && !strip.moved) {
                drawContent()
                return@drawWithContent
            }
            val start = if (sign < 0f) size.width - strip.content else 0f
            val at = start - strip.offset * sign
            // Clipped across only: a glyph that reaches above or below its
            // line keeps its ink.
            clipRect(left = 0f, top = -size.height, right = size.width, bottom = size.height * 2) {
                translate(left = at) { this@drawWithContent.drawContent() }
                // Looping, the next copy follows a gap behind. Back and forth
                // there is only ever the one.
                if (!strip.bounces) translate(left = at + far * sign) { this@drawWithContent.drawContent() }
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
