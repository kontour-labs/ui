package io.kontour.ui.components.display

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import io.kontour.ui.foundation.LocalTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Motion
import io.kontour.ui.theme.SpringToken
import io.kontour.ui.theme.Theme

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
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    contentDescription: String? = null,
) {
    val motion = Theme.motion
    val text = format(value)
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
    val previous = remember { PreviousValue(value) }
    val goingUp = remember(value) { value >= previous.value }
    SideEffect { previous.value = value }

    Row(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription ?: text
        },
        verticalAlignment = Alignment.Bottom,
    ) {
        text.forEachIndexed { index, character ->
            if (character.isDigit()) {
                AnimatedContent(
                    targetState = character,
                    transitionSpec = { rollSpec(goingUp, motion) },
                    label = "digit$index",
                    modifier = Modifier.width(digitWidth),
                ) { digit ->
                    Box(Modifier.width(digitWidth), Alignment.Center) {
                        Text(
                            text = digit.toString(),
                            color = color,
                            style = figures,
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
            } else {
                Text(
                    text = character.toString(),
                    color = color,
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
