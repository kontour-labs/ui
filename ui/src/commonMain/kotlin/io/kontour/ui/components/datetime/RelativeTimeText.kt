package io.kontour.ui.components.datetime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
 */
@Composable
fun RelativeTimeText(
    until: Duration,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    imminentThreshold: Duration = 30.seconds,
    announce: Boolean = true,
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

    Text(
        text = formatRelative(remaining, imminentThreshold),
        modifier = if (announce) {
            modifier.semantics { liveRegion = LiveRegionMode.Polite }
        } else {
            modifier
        },
        style = style,
        color = color,
    )
}

/**
 * Renders [remaining] the way a departure board would.
 *
 * Separate from the composable and internal so it can be tested directly —
 * the rounding rules are the part with actual behaviour in them.
 */
internal fun formatRelative(remaining: Duration, imminentThreshold: Duration): String {
    if (remaining.absoluteValue < imminentThreshold) return "now"

    val past = remaining.isNegative()
    val absolute = remaining.absoluteValue
    val minutes = absolute.inWholeMinutes
    val hours = absolute.inWholeHours

    val amount = when {
        // Rounds down, not to nearest: telling someone their bus is 2 minutes
        // away when it is 90 seconds away is the error that makes them miss it.
        minutes < 60 -> "${minutes.coerceAtLeast(1)} min"
        hours < 24 -> "$hours hr"
        else -> "${absolute.inWholeDays} d"
    }

    return if (past) "$amount ago" else "in $amount"
}
