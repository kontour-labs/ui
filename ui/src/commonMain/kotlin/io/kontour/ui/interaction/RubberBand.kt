package io.kontour.ui.interaction

import androidx.compose.animation.core.AnimationConstants
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
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
     * part way home. The shape it is drawn as reads the sign too — the end against
     * the wall is the round one, see `squashedCapsule` — so a crossing now swaps
     * which end is circular as well as where the thumb sits, and holding the sign
     * is what stops both.
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

    /**
     * Throws the stretch out at [velocity], pixels a second, and springs it home:
     * a boundary hit at speed rather than pulled into.
     *
     * [spec] runs from nothing to nothing with [velocity] as its start, so how far
     * the stretch goes is the spring's to say. The distance is then read through
     * the same approach to [limit] that [pull] uses, so however hard the flick,
     * the band gives less than [limit] and never looks like somewhere to stop.
     *
     * An under-damped [spec] ends the moment it passes home, rather than carrying
     * on through nothing and back. A critically damped one should say when it is
     * close enough, with a visibility threshold, or it spends a good part of a
     * second a pixel or two short — which is what was reported on a two-row drum
     * (below).
     *
     * Written for the wheel picker, where a flick into the first or last row
     * stopped dead: "add bounce-back animation when flicking to end".
     */
    suspend fun bounce(velocity: Float, limit: Float, spec: AnimationSpec<Float>) {
        carryOn(past = 0f, velocity = velocity, limit = limit, spec = spec, sinceNanos = AnimationConstants.UnspecifiedTime)
    }

    /**
     * [bounce], taken up by a motion that has just run into the boundary: [past]
     * pixels beyond it already, in the frame the motion last drew at
     * [sinceNanos].
     *
     * ### Why the hand-off has to be seamless
     *
     * Reported from a phone on the AM/PM drum: a flick "settles, then sort of
     * moves up just a little bit, and the item is a bit off-centre". A drum of
     * two rows has one row of travel, so nearly every flick ends in the bounce,
     * and the bounce was two steps: the decay stopped the drum *on* its end row,
     * and a new animation then started from nothing on the next frame. Measured,
     * the row sat centred for two frames, went out past the band by up to eleven
     * points, and crept back over thirty frames on a critically damped spring —
     * a drum that had visibly arrived, then left, then spent half a second a
     * pixel or two off centre.
     *
     * So the frame the motion ran out of room in draws the part it had no room
     * for, and the spring's first frame follows on from that frame's time rather
     * than starting its own clock at zero. There is no frame at rest in between,
     * which is what made the end row look like somewhere the drum had stopped.
     */
    internal suspend fun carryOn(
        past: Float,
        velocity: Float,
        limit: Float,
        spec: AnimationSpec<Float>,
        sinceNanos: Long,
    ) {
        if (limit <= 0f || (velocity == 0f && past <= 0f)) return
        val sign = if (velocity > 0f) 1f else -1f
        fun place(raw: Float) {
            offset = sign * limit * (1f - exp(-raw.coerceAtLeast(0f) / limit))
        }
        place(past)
        AnimationState(
            initialValue = past.coerceAtLeast(0f),
            initialVelocity = abs(velocity),
            lastFrameTimeNanos = sinceNanos,
        ).animateTo(
            targetValue = 0f,
            animationSpec = spec,
            sequentialAnimation = sinceNanos != AnimationConstants.UnspecifiedTime,
        ) {
            place(value)
            // Home, for a spring that goes through it: the rest of an under-damped
            // one is a lobe the other side of nothing, which the clamp above would
            // hold at nothing anyway. *Through*, not at: the first frame is at the
            // value it starts from, which is nothing when the motion ran out of
            // room exactly at the boundary — and stopping there was a bounce that
            // never happened, the drum stopping dead.
            if (value < 0f) cancelAnimation()
        }
        offset = 0f
    }
}

/** A [RubberBand] that survives recomposition. */
@Composable
fun rememberRubberBand(): RubberBand = remember { RubberBand() }
