package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * A fixed window of frames, gathered on demand and held until the next one.
 *
 * ### Why not [FrameReadout]
 *
 * That one is a live overlay: it runs for as long as it is on screen and reports
 * a rolling window, which is the right shape for *using* an app and watching the
 * numbers move. It is the wrong shape for an experiment. Comparing two arms
 * needs the same length of run each time, started deliberately, with a number
 * that stops moving so it can be read and written down — and a rolling window
 * has moved on by the time anybody looks up.
 *
 * ### It stops, and that is a requirement rather than a tidiness
 *
 * `withFrameNanos` in a loop asks for a frame every frame, so a collector that
 * never stops holds the display at its ceiling and one core awake for as long as
 * the page is open. `IdleAnimationTest` is the standing rule about that, written
 * after four components in this library did it by accident. This runs for
 * [Frames] frames and then nothing.
 *
 * ### Two numbers, and the second is the one that decides
 *
 * The **peak** is what a stutter is: one 99.9ms frame is what a reader notices
 * and what a mean hides completely. The **95th percentile** is what the arm
 * costs when it is not stuttering — a peak on its own cannot tell a single
 * hitch from a run that is uniformly too slow, and those want opposite fixes.
 */
@Stable
internal class FrameRun(val label: String) {

    /** Bumped to start a run; the collector is keyed on it. */
    var token by mutableStateOf(0)
        private set

    var collecting by mutableStateOf(false)
        internal set

    /** How many frames have landed in the current or last run. */
    var frames by mutableStateOf(0)
        internal set

    /** The worst frame of the run, in tenths of a millisecond. */
    var peakTenths by mutableStateOf(0)
        internal set

    /** The 95th percentile of the run, in tenths of a millisecond. */
    var p95Tenths by mutableStateOf(0)
        internal set

    /** Throws the last run away and starts another. */
    fun start() {
        frames = 0
        peakTenths = 0
        p95Tenths = 0
        token++
    }

    /** Whether there is a reading to show. */
    val hasReading: Boolean get() = frames > 0 && !collecting

    internal companion object {
        /**
         * How long a run is.
         *
         * Two seconds at 120Hz, four at 60 — long enough that a 95th percentile
         * means something (a dozen frames are above it) and short enough that
         * somebody comparing four arms is not standing there for a minute.
         */
        const val Frames = 240
    }
}

/** A [FrameRun] that survives recomposition, and the collector that fills it. */
@Composable
internal fun rememberFrameRun(label: String): FrameRun {
    val run = remember(label) { FrameRun(label) }

    LaunchedEffect(run, run.token) {
        // Nothing has been asked for yet. Crucially this does *not* call
        // `withFrameNanos`, so a page sitting open with no run in progress asks
        // for no frames at all.
        if (run.token == 0) return@LaunchedEffect

        run.collecting = true
        val deltas = ArrayList<Long>(FrameRun.Frames)
        var last = withFrameNanos { it }
        while (deltas.size < FrameRun.Frames) {
            val now = withFrameNanos { it }
            val delta = now - last
            last = now
            // The same filter the overlay uses, for the same reason: a frame
            // clock that has been asleep reports the nap as one frame.
            if (isFrame(delta)) deltas.add(delta)
        }

        deltas.sort()
        run.frames = deltas.size
        run.peakTenths = (deltas.last() / 100_000L).toInt()
        // Nearest-rank, which for 240 samples is the 228th. Not interpolated:
        // an interpolated percentile of frame times is a number that never
        // happened, and every other reading here is a frame that did.
        run.p95Tenths = (deltas[(deltas.size * 95) / 100] / 100_000L).toInt()
        run.collecting = false
    }

    return run
}
