package io.kontour.ui.interaction

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp

/**
 * How far a boundary has been pulled past, and how hard it is pushing back.
 *
 * A scrollable that stops dead at its end has a boundary the finger cannot
 * feel: the gesture simply stops answering, and the control reads as broken
 * rather than as finished. The fix is the same everywhere it comes up — let the
 * content move a little further, make each pixel of finger buy less than the
 * last, and spring it back on release.
 *
 * **Diminishing returns rather than a shorter track.** A linear stretch with a
 * hard stop is the same rigid boundary moved somewhere else; what makes this
 * read as a rubber band is that the resistance grows as the gap does.
 *
 * The arithmetic was written for `SheetState`, measured there — dragged 292px
 * past the top of its anchor range, a sheet's offset stayed at `352.0` for
 * every frame of the drag and its crown row never moved a pixel — and is
 * lifted here so the sheet and the wheel picker share one, rather than the
 * second one to want it growing a copy.
 *
 * Used by the sheet's overshoot, the wheel picker's, and the end stop of every
 * control a finger can push past the end of — `Slider`, `RangeSlider`, `Switch`
 * and `SegmentedControl`, each of which feeds [offset] into the deformation it
 * already had. `PullToRefresh` and `Toast` keep their own arithmetic on purpose:
 * one is unbounded by design, because its progress has to go on climbing past 1
 * to drive the arc, and the other is a stateless function of the whole
 * accumulated pull where this is incremental.
 *
 * Purely visual by construction. Nothing here touches the scrollable it belongs
 * to: [offset] is applied at draw or layout by whoever owns it, so no anchor,
 * index or settled value knows the stretch happened, and letting go returns
 * exactly where it started.
 */
@Stable
class RubberBand internal constructor() {

    /**
     * The current stretch, in pixels, signed the way the pull was.
     *
     * Positive for a pull past the end the axis counts toward.
     */
    var offset: Float by mutableFloatStateOf(0f)
        private set

    /** True while there is anything to spring back. */
    val stretched: Boolean get() = offset != 0f

    /**
     * Closes an open stretch, at full rate, and returns how much of [by] it used.
     *
     * **Before** the content is offered anything. A finger coming back closes
     * the gap it opened before the content itself starts moving again; without
     * that ordering the content slides away while the stretch is still open,
     * and one gesture produces two motions.
     *
     * Zero when the gap is already closed or [by] is pulling it wider — that is
     * [pull]'s half.
     */
    fun payBack(by: Float): Float {
        if (offset == 0f || by == 0f) return 0f
        if ((offset > 0f) == (by > 0f)) return 0f
        val paid = minOf(abs(offset), abs(by))
        offset += paid * if (offset > 0f) -1f else 1f
        return paid * if (by > 0f) 1f else -1f
    }

    /**
     * Takes [by] pixels of pull and returns how much was absorbed.
     *
     * [limit] is the stretch the band approaches and never reaches — a boundary
     * that gives indefinitely is not a boundary, and one that arrives at a hard
     * stop is the rigid boundary again a few pixels further on. It is also the
     * scale of the *pull*: a finger travels about `limit` past the stop to get
     * 63% of the way out, twice that for 86% and two and a half times for 90%.
     * The caller supplies it because what counts as "a little" depends on what
     * is moving: a sheet uses a twelfth of its container, a drum a row and a
     * half, a control the travel it wants a full deformation to cost.
     *
     * ### Integrated over [by], not stepped once per delta
     *
     * The resistance is `1 - offset/limit` either way. What changed is that it
     * used to be evaluated **once** for the whole of [by] and applied linearly
     * across it, which is a single Euler step of `do/dt = 1 - o/limit` — and one
     * Euler step is only accurate while the step is small against the limit.
     * The limits here are small, so it never was: a delta the size of the limit
     * took the band from nothing to the clamp in one frame, and a control with
     * a 6dp limit had two states, squashed and not. That was the report.
     *
     * The closed form of the same equation costs one `exp` and is exact for any
     * delta:
     *
     * ```
     * offset' = limit - (limit - offset) * exp(-by / limit)
     * ```
     *
     * So the stretch is a function of how far the finger has actually travelled
     * past the stop and not of how that travel was chopped into frames. Which
     * is the other thing this fixes, and the one nobody would have found by
     * looking: the old form gave a **different curve at 120Hz than at 60**,
     * because halving each delta halves the error of every step. The library
     * runs at 120 on an iPhone.
     *
     * The clamp is gone with it. An exponential approach cannot overshoot, so
     * `coerceIn` was only ever catching the Euler step's own overrun.
     */
    fun pull(by: Float, limit: Float): Float {
        if (limit <= 0f || by == 0f) return 0f
        // Toward the limit on the side the pull is on. A pull that reverses
        // without the caller paying it back first is rare — `payBack` runs
        // first by construction — but it has to mean *from where the band is*,
        // so the room is measured against the signed offset rather than its
        // magnitude.
        val room = if (by > 0f) limit - offset else -limit - offset
        val gained = room * (1f - exp(-abs(by) / limit))
        offset += gained
        return gained
    }

    /**
     * Springs the stretch back to nothing, and never past it.
     *
     * The magnitude is what animates; the sign is held. `Switch` releases its
     * band on `springBouncy`, which is underdamped and undershoots — so the
     * offset crossed zero and opened a stretch on the **other** side. Nothing
     * about letting go of a control says "and now push it the other way".
     *
     * It became visible rather than merely wrong when the squash started being
     * *pinned* to whichever end is against the wall: crossing zero swaps which end
     * that is, so the thumb jumped sideways by the width it had given up, once,
     * part way home. The shape it is drawn as has since stopped caring which end
     * the wall is on — see `squashedCapsule` — but where it is drawn still does,
     * and that is this.
     */
    suspend fun release(spec: AnimationSpec<Float>) {
        if (offset == 0f) return
        val sign = if (offset > 0f) 1f else -1f
        animate(
            initialValue = abs(offset),
            targetValue = 0f,
            animationSpec = spec,
        ) { value, _ -> offset = sign * value.coerceAtLeast(0f) }
    }
}

/** A [RubberBand] that survives recomposition. */
@Composable
fun rememberRubberBand(): RubberBand = remember { RubberBand() }
