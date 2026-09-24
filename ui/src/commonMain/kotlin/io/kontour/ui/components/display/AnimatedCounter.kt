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
import io.kontour.ui.interaction.withinOrNull
import io.kontour.ui.foundation.LocalTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Motion
import io.kontour.ui.theme.SpringToken
import io.kontour.ui.interaction.rememberTapFeedback
import io.kontour.ui.theme.Theme
import kotlin.time.Duration
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

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
     * How long the digits shake before a fall is rolled.
     *
     * `ZERO` — off — and it only ever applies to a **decrease**. A number going
     * up is good news and arrives as fast as it likes; a number going down is a
     * seat gone, a balance spent, a minute lost, and the report was that it
     * happens with no warning at all.
     *
     * **The warning and the tremor are one thing, and this is its length.** They
     * were two, and both arrangements of them were reported. A tremor at the
     * front of a longer hold shook for 450ms and then stood still for a second
     * before the number moved; moving it to the back put the same silence in
     * front, so the drop was announced by nothing happening. There is no third
     * place to put a pause inside a warning, because the pause *was* the fault:
     * a warning is the tremor, it starts on the frame the fall is noticed, and
     * the number rolls as it stops.
     *
     * So this is the whole of it — no lead-in, no tail. **About 450ms is the
     * length to ask for**, which is two there-and-backs at the tremor's natural
     * rhythm and is what the demo uses. Longer is honest rather than clever: ask
     * for two seconds and you get two seconds of shaking, which stops reading as
     * an announcement somewhere around the third cycle. The rhythm is held near
     * constant and the number of cycles follows the duration, so a longer
     * warning is more shaking rather than slower shaking.
     *
     * The counter cannot see the future, so it makes one: a drop is held at the
     * old number for this long, shaking throughout, and then rolled. What it
     * costs is that the drawn number lags the hoisted [value] by exactly this
     * much while the warning runs. That is the trade, and it is why this is
     * opt-in and zero by default rather than a behaviour every counter in an app
     * suddenly has.
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

    // Down is the one direction worth reporting, and the report is the whole
    // reason the warning exists at all: a number that rises is good news the
    // reader can take at their leisure, and a number that falls is a seat count
    // or a time remaining that they may be about to act on. Once per drop,
    // whether or not there is a `warnBefore` to hold it, and whether or not
    // motion is reduced — a reader who has asked for less movement is exactly
    // the one the drop is quietest for.
    val heard = remember { PreviousValue(value) }
    LaunchedEffect(value) {
        if (value < heard.value) tap()
        heard.value = value
    }

    // **One hold, for as long as the counter is composed, and it cannot be
    // left stuck.**
    //
    // This was an effect keyed on `value` that set `warning`, waited, and then
    // cleared it. A second drop inside the wait relaunched it — which cancelled
    // the first one in the middle of its `delay`, before the line that cleared
    // the flag — and the relaunched one saw a warning "already running" and
    // returned. Nothing was left to finish it. Reported from the catalog as
    // "Tick down" stopping working entirely if it was pressed quickly enough:
    // the drawn number froze and never wiggled again.
    //
    // Now the value is *collected* rather than keyed on. The flow is conflated,
    // so drops that land during a hold are not queued — the roll, when it
    // comes, goes to wherever the value has reached, which is the promise the
    // `warnBefore` KDoc has always made. The flag is cleared in `finally`, so a
    // cancelled hold cannot leave it set. And a rise during a hold ends it at
    // once: good news does not wait for a warning about bad news to finish.
    val latest by rememberUpdatedState(value)
    LaunchedEffect(warnBefore, motion.reduceMotion) {
        snapshotFlow { latest }.collect { target ->
            if (target >= shown || warnBefore <= Duration.ZERO || motion.reduceMotion) {
                warning = false
                shown = target
                return@collect
            }
            warning = true
            try {
                withinOrNull(warnBefore.inWholeMilliseconds) { snapshotFlow { latest }.first { it >= shown } }
            } finally {
                warning = false
            }
            shown = latest
        }
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

    // The wiggle itself: a small vertical bob, per changing digit.
    //
    // An `Animatable` driven by an effect rather than `rememberInfiniteTransition`,
    // and that is not a style choice. An infinite transition runs for as long as
    // it is composed — so every counter in an app would carry a perpetual
    // animation to be ready for a warning most of them never give, which is the
    // exact shape of a defect this repository has already fixed once under the
    // heading of animations running for nobody. This one exists between the drop
    // and the roll and at no other time.
    //
    // **The tremor is the warning, and it fills it exactly.** The two used to be
    // separate lengths, and both ways of arranging them were reported. A fixed
    // 450ms shake at the front of a 1.5s hold stood still for a second before
    // the number moved; putting it at the back left the same second of silence
    // in front of it, so the drop was announced by nothing happening. There is
    // nowhere left to put a pause, because the pause was the fault. The shake
    // starts on the frame the fall is noticed and stops as the roll begins.
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(warning) {
        if (!warning) {
            wobble.snapTo(0f)
            return@LaunchedEffect
        }
        // Cycles from the duration, leg from the cycles — so a longer warning is
        // more shaking rather than slower shaking, and the last leg lands on
        // centre at the moment the hold ends rather than a fraction before it.
        //
        // Rounding the cycle count on its own would leave up to two legs of
        // stillness at the end, which is the reported defect again in miniature;
        // dividing the remainder back into the leg keeps the rhythm within about
        // a tenth of [WigglePeriodMillis] and closes the gap. At the 450ms this
        // is written for both numbers come out exactly where they were: two
        // cycles at 90ms.
        val cycles = wiggleCycles(warnBefore)
        val legMillis = (warnBefore.inWholeMilliseconds / (2L * cycles + 1L))
            .toInt()
            .coerceAtLeast(1)
        val leg = tween<Float>(legMillis, easing = LinearEasing)
        repeat(cycles) {
            wobble.animateTo(1f, leg)
            wobble.animateTo(-1f, leg)
        }
        // Back to rest rather than stopping wherever the last leg left it. It is
        // the third leg of the count above rather than an extra on the end, so
        // the digits reach centre on the frame the roll starts and the number
        // begins travelling from where it was standing — not from 1.5dp off it.
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
                        // **Every digit about to move trembles the same way, and
                        // up and down.**
                        //
                        // Up and down because that was asked for: the tremor was a
                        // side-to-side shake, and the report was that it should
                        // bob instead. It also agrees with what comes next — the
                        // roll is vertical, so a digit that has been bobbing in
                        // its cell rolls out along the line it was already moving
                        // on, rather than changing direction to go.
                        //
                        // The same way, not alternating by column: reported from
                        // a phone as *"if multiple digits are about to move, then
                        // they wiggle in the same direction"*. The digits that are
                        // not about to change sit still, so `1,200` falling to
                        // `1,199` bobs two columns while two hold their ground.
                        //
                        // Unclipped, so 1.5dp of the tremor crosses into the line
                        // above or below. Accepted rather than clipped: a cell is
                        // exactly a line tall, so clipping would shave the top off
                        // the glyph at the extremes of every cycle.
                        .graphicsLayer {
                            translationY = if (moving.getOrElse(index) { false }) {
                                wobble.value * amplitude
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
 * How many there-and-backs fill a warning of [warnBefore] long.
 *
 * A there-and-back is two legs and the settle back to centre is a third, so a
 * warning of `n` legs holds `n / 2` cycles. At the 450ms `AnimatedCounter` is
 * written for that is two — 360ms of tremor and a 90ms settle, which is exactly
 * the fixed pair it replaced.
 *
 * Derived rather than fixed because the warning is now the tremor's own length
 * rather than a hold wrapped round it, and a fixed count would leave a warning
 * that is mostly silence — which is the defect, in both of the arrangements it
 * was reported in. At least one, so the shortest warning anybody can ask for is
 * still a shake and not a twitch.
 */
private fun wiggleCycles(warnBefore: Duration): Int =
    (warnBefore.inWholeMilliseconds / (2L * WigglePeriodMillis)).toInt().coerceAtLeast(1)
