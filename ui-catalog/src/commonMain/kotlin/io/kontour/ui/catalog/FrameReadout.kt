package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * Frame times, on the device, while you use it.
 *
 * The JVM suite can count recompositions and it can count draws, and both of
 * those are the same number on a phone as they are here — but it cannot tell you
 * what a phone's GPU makes of a 150-pixel blur, and this project has no emulator
 * to ask. So the honest instrument is one that runs where the question is.
 *
 * Two numbers, because they answer different questions:
 *
 * - **The rolling mean** is what the app feels like. 16.7ms is sixty a second.
 * - **The worst frame in the last two seconds** is what a *stutter* is. A mean
 *   of 17 with a worst of 120 is a smooth app with a hitch in it, and the hitch
 *   is the thing anybody actually notices. A mean alone hides it completely.
 *
 * ### It costs a frame callback and nothing else
 *
 * `withFrameNanos` in a loop is how Compose measures itself. The readout
 * recomposes only when a number it shows has changed, and the numbers are
 * rounded before they are compared, so a steady sixty redraws two short strings
 * about once a second rather than sixty times.
 *
 * ### Read this in a *release* build
 *
 * A debug build of Compose is much slower than a release one — no baseline
 * profile, so the first run through any code path is interpreted, and opening a
 * sheet for the first time runs a great deal of code for the first time. That
 * shows up here as a terrible first open and a fine second one. Compare like
 * with like: `:showcase:android:installRelease`.
 */
@Composable
internal fun FrameReadout(modifier: Modifier = Modifier) {
    var mean by remember { mutableStateOf(0) }
    var worst by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val recent = ArrayDeque<Long>()
        var last = withFrameNanos { it }
        var sinceReport = 0L

        while (true) {
            val now = withFrameNanos { it }
            val delta = now - last
            last = now

            // A frame that took longer than a quarter of a second is the app
            // having been in the background, not the app being slow.
            if (delta in 1..MaxPlausibleNanos) {
                recent.addLast(delta)
                while (recent.size > Window) recent.removeFirst()
                sinceReport += delta

                if (sinceReport >= ReportEveryNanos && recent.isNotEmpty()) {
                    sinceReport = 0
                    mean = ((recent.sum() / recent.size) / 100_000L).toInt()
                    worst = ((recent.max()) / 100_000L).toInt()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .background(Theme.colors.scrim, Theme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${mean.tenths()} ms  mean",
            style = Theme.typography.monoLabel,
            color = Color.White,
        )
        Text(
            text = "${worst.tenths()} ms  worst",
            style = Theme.typography.monoLabel,
            // Green while every frame in the window made sixty a second, amber
            // while they made thirty, red below that. The colour is the reading:
            // a number you have to interpret gets glanced at and forgotten.
            color = when {
                worst <= SixtyHz -> Theme.colors.success.solid
                worst <= ThirtyHz -> Theme.colors.warning.solid
                else -> Theme.colors.danger.solid
            },
        )
    }
}

/** Tenths of a millisecond, held as an Int so no formatter is needed. */
private fun Int.tenths(): String = "${this / 10}.${this % 10}"

/** A frame at sixty a second, in tenths of a millisecond. */
private const val SixtyHz = 167

/** A frame at thirty a second. */
private const val ThirtyHz = 333

/** Two seconds of frames at sixty a second. */
private const val Window = 120

/** How often the numbers are allowed to change: often enough to watch, rarely enough to be cheap. */
private const val ReportEveryNanos = 500_000_000L

/** Longer than this and the app was not running, so the frame says nothing. */
private const val MaxPlausibleNanos = 250_000_000L
