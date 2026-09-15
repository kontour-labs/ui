package io.kontour.ui.catalog

import androidx.compose.runtime.Composable

/**
 * The fastest this display can refresh, in frames a second.
 *
 * [FrameReadout] needs it because a frame's *budget* is the reciprocal of it,
 * and for the life of that file the budget was the constant 16.7ms — sixty a
 * second, written down when every phone in reach refreshed at sixty.
 *
 * On a 120Hz phone that is off by a factor of two in the direction that hides
 * the problem: a solid 16ms is every second frame dropped, and the readout
 * coloured it green. It took a reader saying "I don't think it's adapting to
 * the phone's frame rate properly" to notice that the instrument for answering
 * that question had the answer baked in.
 *
 * ### Asked, not measured
 *
 * The tempting implementation is to take the shortest frame the readout has
 * ever seen, which needs no per-platform code at all. It fails in exactly the
 * case that matters: an app pinned at 60 on a 120Hz panel never produces an
 * 8.3ms frame, so the estimate comes back 60, the budget comes back 16.7, and
 * everything is green again. An instrument that infers its own target from the
 * thing it is measuring cannot report that the thing is capped.
 */
@Composable
internal expect fun platformDisplayHz(): Int

/**
 * What to assume where the platform will not say.
 *
 * Sixty, because that is the floor every display in question clears, and
 * because assuming *low* is the safe direction: the budget comes out generous
 * and the readout under-reports rather than crying wolf.
 */
internal const val FallbackHz = 60
