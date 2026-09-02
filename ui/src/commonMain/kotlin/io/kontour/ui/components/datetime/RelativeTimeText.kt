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
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A countdown that keeps itself current — "in 4 min", "now", "2 min ago".
 *
 * The single most-read piece of text in a transit app, and the one most often
 * got wrong: a departure board that renders "in 4 min" once and then sits there
 * while four minutes pass is worse than showing a wall-clock time, because the
 * user trusts it.
 *
 * So this re-renders on a schedule that matches the resolution being shown —
 * every second while under a minute, every minute above it — rather than on a
 * fixed timer that is either wasteful or stale.
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
    color: Color = Color.Unspecified,
    imminentThreshold: Duration = 30.seconds,
    announce: Boolean = true,
    animateValue: Boolean = false,
) {
    // Recomputed rather than remembered on `until`: the caller passes a value
    // that was correct when it composed, and this ticks it down from there.
    var elapsed by remember(until) { mutableStateOf(Duration.ZERO) }
    val remaining = until - elapsed

    LaunchedEffect(until) {
        while (true) {
            val current = until - elapsed
            // Tick at the resolution being displayed. A minute-level readout
            // does not need 59 wasted recompositions per minute.
            val step = if (current.absoluteValue < 1.minutes) 1.seconds else 20.seconds
            delay(step)
            elapsed += step
        }
    }

    val parts = relativeParts(remaining, imminentThreshold)
    val spoken = parts.joined()

    val announced = if (announce) {
        modifier.semantics { liveRegion = LiveRegionMode.Polite }
    } else {
        modifier
    }

    if (!animateValue || parts.amount == null) {
        Text(text = spoken, modifier = announced, style = style, color = color)
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
            Text(text = parts.prefix, style = style, color = color)
        }
        AnimatedCounter(value = parts.amount, style = style, color = color)
        if (parts.suffix.isNotEmpty()) {
            Text(text = parts.suffix, style = style, color = color)
        }
    }
}

/**
 * The phrase, split at the number.
 *
 * "in 4 min" is three things — a word, a count, and a unit — and only the middle
 * one counts. Splitting here rather than in the composable keeps the wording in
 * one place: [formatRelative] is this, joined up, so the animated and the plain
 * renderings cannot drift into saying different things.
 */
internal data class RelativeParts(
    val prefix: String,
    /** `null` when the phrase has no number in it — "now". */
    val amount: Int?,
    val suffix: String,
) {
    fun joined(): String = if (amount == null) prefix else "$prefix$amount$suffix"
}

/**
 * Renders [remaining] the way a departure board would.
 *
 * Separate from the composable and internal so it can be tested directly —
 * the rounding rules are the part with actual behaviour in them.
 */
internal fun formatRelative(remaining: Duration, imminentThreshold: Duration): String =
    relativeParts(remaining, imminentThreshold).joined()

/** See [RelativeParts]. The rounding rules live here. */
internal fun relativeParts(remaining: Duration, imminentThreshold: Duration): RelativeParts {
    if (remaining.absoluteValue < imminentThreshold) {
        return RelativeParts(prefix = "now", amount = null, suffix = "")
    }

    val past = remaining.isNegative()
    val absolute = remaining.absoluteValue
    val minutes = absolute.inWholeMinutes
    val hours = absolute.inWholeHours

    // Rounds down, not to nearest: telling someone their bus is 2 minutes away
    // when it is 90 seconds away is the error that makes them miss it.
    val count: Int
    val unit: String
    when {
        minutes < 60 -> {
            count = minutes.coerceAtLeast(1).toInt()
            unit = "min"
        }
        hours < 24 -> {
            count = hours.toInt()
            unit = "hr"
        }
        else -> {
            count = absolute.inWholeDays.toInt()
            unit = "d"
        }
    }

    return RelativeParts(
        prefix = if (past) "" else "in ",
        amount = count,
        suffix = if (past) " $unit ago" else " $unit",
    )
}
