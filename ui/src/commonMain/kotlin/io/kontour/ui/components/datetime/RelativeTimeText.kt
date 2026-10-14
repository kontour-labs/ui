package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import io.kontour.ui.components.display.AnimatedCounter
import io.kontour.ui.foundation.LocalTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.locale.RelativeUnit
import io.kontour.ui.theme.Theme
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A countdown that keeps itself current — "in 4 min", "now", "2 min ago".
 *
 * The single most-read piece of text in a transit app, and the one most often
 * got wrong: a departure board that renders "in 4 min" once and then sits there
 * while four minutes pass is worse than showing a wall-clock time, because the
 * user trusts it.
 *
 * So this re-renders when what it shows changes — the moment "in 2 min"
 * becomes "in 1 min", and no more often — rather than on a fixed timer that is
 * either wasteful or stale.
 *
 * ```
 * RelativeTimeText(until = departure - now, style = Theme.typography.titleMedium)
 * ```
 *
 * Marked as a polite live region, so a screen reader announces the change rather
 * than a user having to re-read the row to find out whether their bus is still
 * coming.
 *
 * @param until How long until the moment. Negative means it has passed.
 * @param imminentThreshold Below this, the text reads "now" rather than counting
 *   down the last few seconds — a bus that is 20 seconds away is, practically,
 *   here.
 * @param animateValue Rolls the number over rather than replacing it, through
 *   [AnimatedCounter]. Off by default, and the same switch [Stepper][
 *   io.kontour.ui.components.selection.Stepper] carries, for the same reason: a
 *   departure board of twenty rows all rolling once a minute is motion nobody
 *   asked for, and the one countdown a screen is *about* is worth it.
 *
 *   Only the number moves. "in" and "min" are not counting and animating them
 *   would be a word sliding for no reason.
 */
@Composable
fun RelativeTimeText(
    until: Duration,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    colour: Color = Color.Unspecified,
    imminentThreshold: Duration = 30.seconds,
    announce: Boolean = true,
    animateValue: Boolean = false,
) = CountingDown(until, modifier, style, colour, imminentThreshold, announce, animateValue, clock = SystemClock)

/** The wall clock, which [RelativeTimeText] counts down against. */
private val SystemClock: () -> Instant = { Clock.System.now() }

/**
 * [RelativeTimeText], against [clock] — the system's, or a test's.
 *
 * **The moment is read off a clock, not counted in ticks.** This added up the
 * delays it had slept, which is the time it had *meant* to sleep: a phone that
 * slept, or an app put in the background, came back to a countdown that had
 * counted none of it. And the ticks were twenty seconds apart above a minute,
 * so "in 2 min" stayed up for as much as twenty seconds after there were
 * fewer than two minutes left — the error the rounding down below is there to
 * prevent. The moment is fixed once, as [until] from now, and what is left of
 * it is read from the clock each time.
 */
@Composable
internal fun CountingDown(
    until: Duration,
    modifier: Modifier,
    style: TextStyle,
    colour: Color,
    imminentThreshold: Duration,
    announce: Boolean,
    animateValue: Boolean,
    clock: () -> Instant,
) {
    // Remembered on `until`: the caller passes a value that was correct when
    // it composed, and the moment it names is fixed from then.
    val moment = remember(until) { clock() + until }
    var remaining by remember(until) { mutableStateOf(until.secondsUp()) }
    val currentClock by rememberUpdatedState(clock)

    LaunchedEffect(moment, imminentThreshold) {
        while (true) {
            // In whole seconds, rounded up, as a countdown is shown. Read to
            // the millisecond, the first reading is already a few short of
            // `until` — the time it took to get on screen — and the minutes
            // round down: `until = 4.minutes` read "in 4 min" for one frame
            // and "in 3 min" from the next, and a screen reader announced the
            // change as the text appeared.
            val left = moment - currentClock()
            remaining = left.secondsUp()
            // Asleep until the text next changes, and no longer than a minute:
            // a delay can sleep through a phone's own sleep, and the clock it
            // wakes to is read again either way. The change is worked out in
            // the whole seconds shown, which step only as the clock passes a
            // whole second, and taken back to the clock from there.
            val next = left - remaining + untilNextChange(remaining, imminentThreshold).secondsUp()
            delay(next.coerceAtMost(1.minutes))
        }
    }

    val parts = relativeParts(remaining, imminentThreshold, Theme.formats)
    val spoken = parts.joined()

    val announced = if (announce) {
        modifier.semantics { liveRegion = LiveRegionMode.Polite }
    } else {
        modifier
    }

    if (!animateValue || parts.amount == null) {
        Text(text = spoken, modifier = announced, style = style, colour = colour)
        return
    }

    Row(
        // One node saying the whole phrase, rather than three saying a word
        // each. The digits are drawn rather than laid out as text, so there is
        // nothing under here worth announcing on its own.
        modifier = announced.semantics(mergeDescendants = true) {
            contentDescription = spoken
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (parts.prefix.isNotEmpty()) {
            Text(text = parts.prefix, style = style, colour = colour)
        }
        AnimatedCounter(value = parts.amount, style = style, colour = colour)
        if (parts.suffix.isNotEmpty()) {
            Text(text = parts.suffix, style = style, colour = colour)
        }
    }
}

/**
 * The phrase, split at the number.
 *
 * "in 4 min" is three things — a word, a count, and a unit — and only the middle
 * one counts. The phrase is the locale's, from [LocaleFormats.relativeTime], so
 * where the number sits in it is found rather than assumed: `vor 4 Min.` puts a
 * word before it, `4分後` nothing, and a phrase whose number cannot be found once,
 * in digits [AnimatedCounter] can draw, is simply not split.
 */
internal data class RelativeParts(
    /** The whole phrase, as a screen reader and a plain rendering say it. */
    val text: String,
    val prefix: String,
    /** `null` when the phrase has no number the counter can roll — "now", or digits it does not draw. */
    val amount: Int?,
    val suffix: String,
) {
    fun joined(): String = text
}

/**
 * Renders [remaining] the way a departure board would.
 *
 * Separate from the composable and internal so it can be tested directly —
 * the rounding rules are the part with actual behaviour in them.
 */
internal fun formatRelative(
    remaining: Duration,
    imminentThreshold: Duration,
    formats: LocaleFormats = LocaleFormats.english(),
): String = relativeParts(remaining, imminentThreshold, formats).joined()

/**
 * How long until the phrase for [remaining] says something else, counting
 * [remaining] down: the moment it falls below the whole unit it shows, or
 * reaches "now", or, once past, grows a whole unit more.
 *
 * Kept beside [relativeParts], whose rounding it follows. A millisecond past a
 * boundary going down, because the boundary itself still reads the larger
 * number — two minutes exactly is "in 2 min".
 */
internal fun untilNextChange(remaining: Duration, imminentThreshold: Duration): Duration {
    // "now", until it is a threshold past.
    if (remaining.absoluteValue < imminentThreshold) return remaining + imminentThreshold
    val absolute = remaining.absoluteValue
    // The unit shown, and the whole number of it, as `relativeParts` counts them.
    val (unit, whole) = when {
        absolute < 1.hours -> 1.minutes to absolute.inWholeMinutes.minutes
        absolute < 1.days -> 1.hours to absolute.inWholeHours.hours
        else -> 1.days to absolute.inWholeDays.days
    }
    return if (!remaining.isNegative()) {
        // Down to the last whole unit, or to "now" where that comes first.
        // Under two minutes there is no lower one to go to: under one is "1
        // min" as well, so the next change is "now".
        val floor = if (unit == 1.minutes && whole < 2.minutes) Duration.ZERO else whole
        remaining - maxOf(floor, imminentThreshold) + 1.milliseconds
    } else {
        // Up to the next whole unit. Under a minute already reads "1 min", so
        // the first change is at two.
        maxOf(whole + unit, 2.minutes) - absolute
    }
}

/** This, in whole seconds, rounded up: 3:59.98 is 4:00, and -1.5s is -1s. */
internal fun Duration.secondsUp(): Duration {
    val whole = inWholeSeconds.seconds
    return if (this > whole) whole + 1.seconds else whole
}

/** See [RelativeParts]. The rounding rules live here. */
internal fun relativeParts(
    remaining: Duration,
    imminentThreshold: Duration,
    formats: LocaleFormats = LocaleFormats.english(),
): RelativeParts {
    if (remaining.absoluteValue < imminentThreshold) {
        val now = formats.relativeNow()
        return RelativeParts(text = now, prefix = now, amount = null, suffix = "")
    }

    val past = remaining.isNegative()
    val absolute = remaining.absoluteValue
    val minutes = absolute.inWholeMinutes
    val hours = absolute.inWholeHours

    // Rounds down, not to nearest: telling someone their bus is 2 minutes away
    // when it is 90 seconds away is the error that makes them miss it.
    val count: Int
    val unit: RelativeUnit
    when {
        minutes < 60 -> {
            count = minutes.coerceAtLeast(1).toInt()
            unit = RelativeUnit.Minute
        }
        hours < 24 -> {
            count = hours.toInt()
            unit = RelativeUnit.Hour
        }
        else -> {
            count = absolute.inWholeDays.toInt()
            unit = RelativeUnit.Day
        }
    }

    val text = formats.relativeTime(if (past) -count.toLong() else count.toLong(), unit)
    val number = count.toString()
    val at = text.indexOf(number)
    val once = at >= 0 && text.indexOf(number, at + number.length) < 0
    return if (once) {
        RelativeParts(text, prefix = text.substring(0, at), amount = count, suffix = text.substring(at + number.length))
    } else {
        RelativeParts(text, prefix = text, amount = null, suffix = "")
    }
}
