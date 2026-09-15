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
 * Two numbers, because they answer different questions, and a third that says
 * what they are being judged against:
 *
 * - **The rolling mean** is what the app feels like.
 * - **The worst frame in the last two seconds** is what a *stutter* is. A mean
 *   of 17 with a worst of 120 is a smooth app with a hitch in it, and the hitch
 *   is the thing anybody actually notices. A mean alone hides it completely.
 * - **The worst since the readout was switched on**, which is the line that
 *   answers "the first animation janks". A rolling two-second window has moved
 *   on by the time anybody looks up from the thing they just pressed, so a
 *   once-per-launch stall was gone before it could be read. This one holds.
 *   Toggling **Frame times** off and on is how it resets.
 * - **The display's rate**, from [platformDisplayHz], because the budget is its
 *   reciprocal and the budget used to be the constant 16.7ms. On a 120Hz phone
 *   that was wrong by a factor of two in the direction that hides the problem —
 *   every second frame dropped, coloured green — and it was wrong in the
 *   instrument somebody would reach for to find out whether the app was keeping
 *   up with the phone. The number is on screen now so that a reader can see
 *   which budget produced the colour instead of assuming one.
 *
 * ### It keeps the display at that rate while it is on
 *
 * `withFrameNanos` in a loop asks for a frame every frame, so a panel that
 * varies its rate with demand — every ProMotion iPhone, most recent Android
 * flagships — sits at its ceiling for as long as the readout is visible. That is
 * what makes judging against the *maximum* rate the right thing rather than a
 * harsh thing: while you are reading it, the maximum is what the display is
 * actually doing. It is also the honest caveat about the measurement, since the
 * app is never idle while being measured.
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
 * with like: `:showcase:android:installRelease`, or on iOS the Release
 * configuration of `showcase/ios/KontourUI.xcodeproj`.
 *
 * **That explanation is about the JVM and Android and stops there.**
 * Kotlin/Native is compiled ahead of time, so there is no interpreter and no
 * profile to be missing — a first open that janks on iOS in a Release build is
 * doing something else, and `FirstOpenCostDiagnostic` is where the hunt for it
 * is written down.
 */
@Composable
internal fun FrameReadout(modifier: Modifier = Modifier) {
    var mean by remember { mutableStateOf(0) }
    var worst by remember { mutableStateOf(0) }
    var peak by remember { mutableStateOf(0) }
    val displayHz = platformDisplayHz()

    LaunchedEffect(Unit) {
        val recent = ArrayDeque<Long>()
        var last = withFrameNanos { it }
        var sinceReport = 0L
        var worstEver = 0L

        while (true) {
            val now = withFrameNanos { it }
            val delta = now - last
            last = now

            if (isFrame(delta)) {
                recent.addLast(delta)
                while (recent.size > Window) recent.removeFirst()
                sinceReport += delta
                if (delta > worstEver) worstEver = delta

                if (sinceReport >= ReportEveryNanos && recent.isNotEmpty()) {
                    sinceReport = 0
                    mean = ((recent.sum() / recent.size) / 100_000L).toInt()
                    worst = ((recent.max()) / 100_000L).toInt()
                    peak = (worstEver / 100_000L).toInt()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .background(Theme.colours.scrim, Theme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${mean.tenths()} ms  mean",
            style = Theme.typography.mono,
            colour = Color.White,
        )
        Text(
            text = "${worst.tenths()} ms  worst",
            style = Theme.typography.mono,
            // The colour is the reading: a number you have to interpret gets
            // glanced at and forgotten.
            colour = when (verdictFor(worst, displayHz)) {
                FrameVerdict.Smooth -> Theme.colours.success.solid
                FrameVerdict.Halved -> Theme.colours.warning.solid
                FrameVerdict.Worse -> Theme.colours.danger.solid
            },
        )
        Text(
            text = "${peak.tenths()} ms  peak",
            style = Theme.typography.mono,
            colour = when (verdictFor(peak, displayHz)) {
                FrameVerdict.Smooth -> Theme.colours.success.solid
                FrameVerdict.Halved -> Theme.colours.warning.solid
                FrameVerdict.Worse -> Theme.colours.danger.solid
            },
        )
        Text(
            text = "$displayHz Hz  display",
            style = Theme.typography.mono,
            colour = Color.White,
        )
    }
}

/** Tenths of a millisecond, held as an Int so no formatter is needed. */
private fun Int.tenths(): String = "${this / 10}.${this % 10}"

/**
 * How a worst-frame reading scores against the display it was taken on.
 *
 * Three bands, and they are the same three the readout has always had — every
 * frame arrived, every *second* frame arrived, or worse than that. What changed
 * is that "arrived" is now relative to the display rather than to sixty.
 */
internal enum class FrameVerdict { Smooth, Halved, Worse }

/**
 * [worstTenths] against the budget [displayHz] implies.
 *
 * Deliberately strict at the boundary: a frame is on time if it fits the budget,
 * with no tolerance added. A tolerance would be a number nobody here measured,
 * and the strict form is what this has always done at sixty — the band moved,
 * the rule did not.
 */
internal fun verdictFor(worstTenths: Int, displayHz: Int): FrameVerdict = when {
    worstTenths <= budgetTenths(displayHz) -> FrameVerdict.Smooth
    worstTenths <= budgetTenths(displayHz, frames = 2) -> FrameVerdict.Halved
    else -> FrameVerdict.Worse
}

/**
 * [frames] frame periods at [displayHz], in tenths of a millisecond, rounded.
 *
 * 167 at sixty — the constant this replaced, so a 60Hz display is scored exactly
 * as it always was — and 83 at a hundred and twenty. A nonsensical rate falls
 * back rather than dividing by zero.
 *
 * **[frames] multiplies before the rounding, not after, and a test is the reason
 * the signature looks like this.** The first version doubled the one-frame
 * budget to get the amber ceiling, which at sixty is 167 x 2 = 334 where the
 * constant it replaced was 333 — two frames at sixty a second is 33.33ms, and
 * rounding 16.67 up first and then doubling carries the rounding error with it.
 * One tenth of a millisecond, at exactly one boundary value, found by the test
 * that asserts this agrees with the old constants at every edge.
 */
internal fun budgetTenths(displayHz: Int, frames: Int = 1): Int {
    val hz = if (displayHz > 0) displayHz else FallbackHz
    return (10_000 * frames + hz / 2) / hz
}

/** Two seconds of frames at sixty a second. */
private const val Window = 120

/** How often the numbers are allowed to change: often enough to watch, rarely enough to be cheap. */
private const val ReportEveryNanos = 500_000_000L

/**
 * Whether a gap between frame callbacks is a frame this should count.
 *
 * ### The quarter of a second that hid the bug it was asked about
 *
 * This used to read `delta in 1..250_000_000`, with the comment "a frame that
 * took longer than a quarter of a second is the app having been in the
 * background, not the app being slow." The intent was right and the number was
 * far too tight: **a quarter of a second is well inside the range of a real
 * stall.** Anything worse was dropped on the floor, silently.
 *
 * That is not hypothetical. Asked for frame times on a first sheet open that
 * "jumped between a few frames and was then open", a reader got 40.4ms — on iOS
 * *and* on Android, the same figure on two different GPUs. The figure was not a
 * finding about the frame; it was the worst frame that survived this filter. And
 * the coincidence across platforms was read here as evidence that the cause was
 * platform-independent, which it is not evidence of at all: two runs of the same
 * code against the same ceiling land in the same place.
 *
 * ### Why there is still a filter, and where the line is
 *
 * Without one, backgrounding the app and returning puts a multi-second "frame"
 * into the window and the reading is ruined for as long as it stays there.
 *
 * A stall and a short excursion out of the app are **not separable by duration**
 * — a bad stall and a fast app-switch are both around a second — so this line is
 * a judgement rather than a measurement, and it is drawn to fail in the
 * direction that reports too much. Two seconds: longer than any stall worth
 * calling a stall, shorter than leaving the app and coming back. A frame that
 * genuinely takes a second is something a reader should see in red, not
 * something to hide in case it was a task switch.
 */
internal fun isFrame(deltaNanos: Long): Boolean = deltaNanos in 1..MaxPlausibleNanos

/** See [isFrame] for why this is not the quarter of a second it used to be. */
internal const val MaxPlausibleNanos = 2_000_000_000L
