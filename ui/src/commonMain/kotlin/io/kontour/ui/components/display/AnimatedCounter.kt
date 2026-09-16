package io.kontour.ui.components.display

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.LocalTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Motion
import io.kontour.ui.theme.SpringToken
import io.kontour.ui.interaction.rememberTapFeedback
import io.kontour.ui.theme.Theme
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * A number that rolls to its new value instead of being replaced.
 *
 * ```
 * AnimatedCounter(value = minutesAway, format = { "$it min" })
 * ```
 *
 * For a figure that changes while the user is looking at it — minutes to the
 * next bus, an unread count, a fare as options are added. A number that simply
 * swaps is a number the eye can miss entirely; one that rolls says *this
 * changed* without a highlight, a flash, or anything else that has to be undone
 * a moment later.
 *
 * ### Only the digits that changed move
 *
 * "14 min" to "13 min" rolls one column. The `1` does not move, and neither does
 * " min". That is the whole difference between this and a cross-fade of two
 * strings: a cross-fade says the *value* changed, and this says *which part* of
 * it did — which on a four-digit fare is the difference between reading it again
 * and glancing at it.
 *
 * Digits roll **up** when the number grows and **down** when it shrinks, so the
 * direction carries the sign. Counting down to a departure looks like a
 * departure board and not like a lottery draw.
 *
 * ### The row does not twitch
 *
 * Proportional fonts draw `1` narrower than `8` — the default face draws them at
 * 23px and 42px at headline size — so a counter laid out naturally changes width
 * as it counts and drags whatever is beside it along. The same problem
 * [io.kontour.ui.components.selection.Stepper] solves by measuring its widest
 * value.
 *
 * Two things fix it, in order. The digits are asked for the font's **tabular
 * figures**, which are drawn to one advance and spaced for it; and each cell is
 * then given the measured width of the widest digit, which is a no-op when the
 * face honoured the request and a fallback when it did not. Non-digits keep
 * their natural width, since they do not change.
 *
 * ### It reads as one number
 *
 * The cells are a dozen separate nodes, and a screen reader walking them would
 * announce "one", "four", "space", "m", "i", "n". So the row carries the whole
 * formatted string as its own description and the cells are cleared — the same
 * bargain any composed-from-parts control makes.
 *
 * @param format Turns the value into what is drawn. Everything that is not a
 *   digit is left alone, so a unit, a currency symbol or a thousands separator
 *   all come through and simply do not animate.
 * @param contentDescription What a screen reader hears. Defaults to the
 *   formatted text, which is right whenever the text is self-explanatory —
 *   override where it is not: "$4.20" reads better as "four dollars twenty".
 */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
    colour: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    /**
     * Where the digits sit when the caller gives them more room than they need.
     *
     * `Start` is right for a counter that is simply as wide as its number.
     * `Center` is what a caller reserving a *fixed* width wants —
     * [io.kontour.ui.components.selection.Stepper] holds a column wide enough
     * for the longest value it can reach, so that the buttons either side do
     * not shuffle as the number grows, and without this the digits packed
     * against the leading edge of that column while the static
     * `Text` beside them was centred. Same component, same width, two different
     * places for the number depending on whether it animated.
     */
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    /**
     * How long the old number is held before a fall is rolled.
     *
     * `ZERO` — off — and it only ever applies to a **decrease**. A number going
     * up is good news and arrives as fast as it likes; a number going down is a
     * seat gone, a balance spent, a minute lost, and the report was that it
     * happens with no warning at all.
     *
     * **This is the hold, and not the length of the wiggle.** One number used to
     * mean both, so a two-second warning shook for two seconds — long past the
     * point where a tremor reads as an announcement. The digits about to change
     * now shake for a fixed couple of cycles at the start of the hold and are
     * still for the rest of it, so a long warning is a held number rather than a
     * vibrating one.
     *
     * The counter cannot see the future, so it makes one: a drop is *held* for
     * this long, wiggled at the front of that, and only then rolled. What the reader gets is a few
     * seconds of "something is about to change" before it does, which is the
     * thing being asked for; what it costs is that the drawn number lags the
     * hoisted [value] by exactly this much while the warning runs. That is the
     * trade, and it is why this is opt-in and zero by default rather than a
     * behaviour every counter in an app suddenly has.
     *
     * A second drop landing mid-warning restarts nothing: the wiggle continues
     * and the roll, when it comes, goes to wherever the value has reached. So a
     * value falling every second does not queue a second of warning per step.
     *
     * Ignored under reduced motion, which takes the delay with it — a reader who
     * has asked for less movement should not also be shown a stale number.
     */
    warnBefore: Duration = Duration.ZERO,
    contentDescription: String? = null,
) {
    val motion = Theme.motion
    val tap = rememberTapFeedback()

    // What is actually drawn, which is `value` except while a fall is being
    // announced. See [warnBefore].
    var shown by remember { mutableIntStateOf(value) }
    var warning by remember { mutableStateOf(false) }

    LaunchedEffect(value, warnBefore, motion.reduceMotion) {
        val falling = value < shown
        // Down is the one direction worth reporting, and the report is the whole
        // reason the warning exists at all: a number that rises is good news the
        // reader can take at their leisure, and a number that falls is a seat
        // count or a time remaining that they may be about to act on. It fires
        // whether or not there is a `warnBefore` to hold it, and whether or not
        // motion is reduced — a reader who has asked for less movement is exactly
        // the one the drop is quietest for.
        if (falling) tap()
        if (!falling || warnBefore <= Duration.ZERO || motion.reduceMotion) {
            warning = false
            shown = value
            return@LaunchedEffect
        }
        // Already warning: the wiggle running is the announcement, and this
        // effect will be relaunched by the value it lands on.
        if (warning) return@LaunchedEffect
        warning = true
        delay(warnBefore)
        warning = false
        shown = value
    }

    val text = format(shown)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Tabular figures, where the face has them.
    //
    // Outfit does, and they are the right answer rather than a nicety: designed
    // tabular digits are all one advance *and* spaced for it. Padding a
    // proportional `1` out to the width of a `0` gets the first without the
    // second, and at headline sizes the gap beside a 1 reads as a space in the
    // middle of the number.
    //
    // `tnum` is a request, not a guarantee — a face without the feature ignores
    // it and nothing changes. Which is why the measured cell below stays: it is
    // the fallback, and against a font that honoured `tnum` it measures the
    // tabular advance and comes out exact.
    val figures = remember(style) {
        style.copy(
            fontFeatureSettings = listOfNotNull(style.fontFeatureSettings, TabularFigures)
                .joinToString(", ")
        )
    }

    // Once per font, not per frame: the widest digit is a property of the type,
    // and every cell is that wide whatever it currently holds.
    val digitWidth: Dp = remember(figures, density, measurer) {
        with(density) {
            (0..9).maxOf { measurer.measure(it.toString(), figures).size.width }.toDp()
        }
    }

    // Which way the digits travel. Recomputed only when the value actually
    // changes, and the previous value is written *after* composition — so a
    // recomposition that changes nothing else does not flip the direction, and
    // a jump from 14 to 3 still rolls downward. It went down, however far.
    val previous = remember { PreviousValue(shown) }
    val goingUp = remember(shown) { shown >= previous.value }
    SideEffect { previous.value = shown }

    /**
     * Which drawn positions are about to change, right-aligned.
     *
     * The wiggle used to be on the whole `Row`, on the argument that the number
     * is one object saying something about itself. The report was that it should
     * be the digits that are about to move, and that is the better reading: the
     * announcement is *which* part of the figure is going, and shaking the whole
     * thing says only that something is.
     *
     * Right-aligned because a formatted number can change length — 1000 falls to
     * 999 — so comparing index for index from the left marks every position as
     * moving on exactly the transition where three of the four genuinely do and
     * the leading 1 is disappearing rather than changing. Counting from the right
     * makes the shift explicit, and a position with nothing opposite it is a
     * position that is going away, which counts as moving.
     */
    val moving = remember(text, warning, value, format) {
        if (!warning) {
            BooleanArray(text.length)
        } else {
            val next = format(value)
            val shift = text.length - next.length
            BooleanArray(text.length) { index ->
                val opposite = index - shift
                opposite !in next.indices || next[opposite] != text[index]
            }
        }
    }

    // The wiggle itself: a small horizontal shake, per changing digit.
    //
    // An `Animatable` driven by an effect rather than `rememberInfiniteTransition`,
    // and that is not a style choice. An infinite transition runs for as long as
    // it is composed — so every counter in an app would carry a perpetual
    // animation to be ready for a warning most of them never give, which is the
    // exact shape of a defect this repository has already fixed once under the
    // heading of animations running for nobody. This one exists between the drop
    // and the roll and at no other time.
    //
    // **Bounded, where it used to run for as long as `warnBefore`.** One number
    // controlled both how long the figure was held and how long it shook, so a
    // two-second warning was a two-second tremor — which stops reading as an
    // announcement somewhere around the third cycle and starts reading as a
    // fault. `warnBefore` now means only how long the old number is held, and
    // the shake is [WiggleCycles] there-and-backs whatever that is.
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(warning) {
        if (!warning) {
            wobble.snapTo(0f)
            return@LaunchedEffect
        }
        val leg = tween<Float>(WigglePeriodMillis, easing = LinearEasing)
        repeat(WiggleCycles) {
            wobble.animateTo(1f, leg)
            wobble.animateTo(-1f, leg)
        }
        // Back to rest rather than stopping wherever the last leg left it, or a
        // warning that outlives its wiggle holds the digits 1.5dp off centre for
        // the remainder — visible on a headline figure as a number that is
        // slightly crooked.
        wobble.animateTo(0f, leg)
    }
    val amplitude = with(LocalDensity.current) { WiggleAmplitude.toPx() }

    Row(
        modifier = modifier
            .semantics {
                // What is drawn, not what is pending.
                //
                // The other way round is tempting — a screen reader gets no
                // wiggle, so the warning does not exist for it, and announcing
                // the number that is coming would spare it the delay. It is
                // wrong: a sighted user of a screen reader would hear one number
                // and see another, and the invariant a counter's description
                // has always had is that it says what the counter says.
                //
                // The delay is the cost of the warning and it is paid in every
                // channel. `warnBefore` is opt-in and zero by default for that
                // reason.
                this.contentDescription = contentDescription ?: text
            },
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.Bottom,
    ) {
        text.forEachIndexed { index, character ->
            if (character.isDigit()) {
                AnimatedContent(
                    targetState = character,
                    transitionSpec = { rollSpec(goingUp, motion) },
                    label = "digit$index",
                    modifier = Modifier
                        .width(digitWidth)
                        // Opposite phases on neighbouring cells, which is what
                        // keeps two adjacent changing digits from reading as the
                        // whole number sliding again — the thing this replaced.
                        //
                        // Unclipped, so 1.5dp of the tremor crosses into the
                        // next cell. Accepted rather than clipped: a cell is
                        // exactly a digit wide, so clipping would shave the edge
                        // off the glyph at the extremes of every cycle, and a
                        // digit that loses a column of pixels is a worse artefact
                        // than one that briefly overlaps its neighbour's
                        // whitespace.
                        .graphicsLayer {
                            translationX = if (moving.getOrElse(index) { false }) {
                                wobble.value * amplitude * if (index % 2 == 0) 1f else -1f
                            } else {
                                0f
                            }
                        },
                ) { digit ->
                    Box(Modifier.width(digitWidth), Alignment.Center) {
                        Text(
                            text = digit.toString(),
                            colour = colour,
                            style = figures,
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
            } else {
                Text(
                    text = character.toString(),
                    colour = colour,
                    style = style,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * A digit leaving upward while the next arrives from below, or the reverse.
 *
 * Under reduced motion neither slides. A rolling digit is a small movement and
 * an easy one to argue for, but the argument is exactly the one that preference
 * has already heard: it is decoration on top of a value that has changed, and
 * the value is legible without it.
 */
private fun rollSpec(goingUp: Boolean, motion: Motion): ContentTransform {
    if (motion.reduceMotion) {
        return fadeIn(tween(motion.instant)) togetherWith fadeOut(tween(motion.instant))
    }
    val sign = if (goingUp) 1 else -1
    val roll = AnimatedCounterDefaults.Roll
    return (
        slideInVertically(roll.spec()) { height -> sign * height } + fadeIn(roll.spec())
        ) togetherWith (
        slideOutVertically(roll.spec()) { height -> -sign * height } + fadeOut(roll.spec())
        )
}

/** Holds the value the counter was showing before this one. */
private class PreviousValue(var value: Int)

/** The OpenType feature for figures drawn to one advance. */
private const val TabularFigures = "tnum"

/** Timing for [AnimatedCounter]. */
object AnimatedCounterDefaults {

    /**
     * Snappy, and critically damped.
     *
     * A digit that overshoots its cell shows the next digit's edge coming back
     * down, which reads as a rendering fault rather than as bounce — so this is
     * the one place in the library where `springBouncy` is wrong on purpose. It
     * is a token of its own rather than `springSnappy` for that reason: the
     * damping is the point, and borrowing a token whose damping might sensibly
     * change is borrowing the wrong guarantee.
     */
    val Roll: SpringToken = SpringToken(dampingRatio = 1f, stiffness = 1400f)
}

/**
 * How far the digits travel each way while a fall is being announced.
 *
 * A private top-level value rather than a field on `AnimatedCounterDefaults`,
 * for the reason the literals ratchet exists: this is a fact about what a
 * wiggle is, not a dial a brand reaches for. 1.5dp is small enough to read as
 * a tremor on a headline figure and large enough to see on a body one.
 */
private val WiggleAmplitude: Dp = 1.5.dp

/** One leg. Fast enough to read as agitation rather than as drift. */
private const val WigglePeriodMillis: Int = 90

/**
 * How many there-and-backs a warning shakes for, however long it is held.
 *
 * Two, which at a 90ms leg is 360ms of tremor and a 90ms settle back to centre.
 * It used to be "for as long as `warnBefore`", and one number meaning both was
 * the defect: a warning long enough to be read was a wiggle long enough to look
 * like a fault. Two cycles is enough to be seen and short enough that the eye
 * arrives at a still number.
 */
private const val WiggleCycles: Int = 2
