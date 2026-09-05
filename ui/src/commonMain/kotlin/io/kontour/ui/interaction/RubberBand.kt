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
     * [limit] is how far the band may be stretched — a boundary that gives
     * indefinitely is not a boundary. The caller supplies it because what
     * counts as "a little" depends on what is moving: a sheet uses a twelfth of
     * its container, a drum a row and a half.
     */
    fun pull(by: Float, limit: Float): Float {
        if (limit <= 0f || by == 0f) return 0f
        val resistance = 1f - (abs(offset) / limit).coerceIn(0f, 1f)
        val gained = by * resistance
        offset = (offset + gained).coerceIn(-limit, limit)
        return gained
    }

    /** Springs the stretch back to nothing. */
    suspend fun release(spec: AnimationSpec<Float>) {
        if (offset == 0f) return
        animate(
            initialValue = offset,
            targetValue = 0f,
            animationSpec = spec,
        ) { value, _ -> offset = value }
    }
}

/** A [RubberBand] that survives recomposition. */
@Composable
fun rememberRubberBand(): RubberBand = remember { RubberBand() }
