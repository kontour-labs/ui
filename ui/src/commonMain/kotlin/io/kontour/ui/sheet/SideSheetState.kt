package io.kontour.ui.sheet

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** The two widths an expandable [SideSheet] rests at. */
internal enum class SideSheetValue { Resting, Expanded }

/**
 * Whether an expandable [SideSheet] is at its resting width or the whole window,
 * and how far between the two it is right now.
 *
 * ```kotlin
 * val filters = rememberSideSheetState()
 * SideSheet(visible = open, onDismissRequest = { open = false }, expandable = true, state = filters) {
 *     SheetHeader { +"Filters" }
 * }
 * // Elsewhere, from a button:
 * scope.launch { filters.expand() }
 * ```
 *
 * Only an `expandable` sheet reads this. A sheet that is not expandable stays at
 * its resting width whatever is asked of its state.
 */
@Stable
class SideSheetState internal constructor(initiallyExpanded: Boolean) {

    internal val anchoredState = AnchoredDraggableState(
        initialValue = if (initiallyExpanded) SideSheetValue.Expanded else SideSheetValue.Resting,
    )

    /**
     * The distance, in pixels, between the resting inner edge and the far side of
     * the window. Zero where there is nothing to expand across — a sheet that is
     * not expandable, or a window no wider than the resting sheet.
     */
    internal var travel by mutableFloatStateOf(0f)
        private set

    /** Asked for before there was anywhere to go; delivered with the first anchors. */
    private var pending: SideSheetValue? by mutableStateOf(null)

    /** Whether the sheet has settled at the whole window. */
    val isExpanded: Boolean get() = anchoredState.settledValue == SideSheetValue.Expanded

    /**
     * How far from its resting width to the whole window the sheet is, 0 to 1 —
     * live, so it follows a drag frame by frame. Read it in a layout or draw
     * block, not in composition, or a drag recomposes whatever reads it.
     */
    val expansion: Float
        get() {
            val offset = anchoredState.offset
            if (travel <= 0f || offset.isNaN()) return 0f
            return (offset / travel).coerceIn(0f, 1f)
        }

    /** Widens the sheet to the whole window. Suspends until it arrives. */
    suspend fun expand() = moveTo(SideSheetValue.Expanded)

    /** Narrows the sheet to its resting width. Suspends until it arrives. */
    suspend fun collapse() = moveTo(SideSheetValue.Resting)

    private suspend fun moveTo(value: SideSheetValue) {
        if (anchoredState.anchors.size == 0) {
            pending = value
            return
        }
        if (anchoredState.anchors.hasPositionFor(value)) anchoredState.animateTo(value)
    }

    /** Back to the resting width at once, for a sheet that has closed. */
    internal suspend fun reset() {
        pending = null
        if (anchoredState.anchors.hasPositionFor(SideSheetValue.Resting)) {
            anchoredState.snapTo(SideSheetValue.Resting)
        }
    }

    /**
     * The two anchors, from the sheet's layout. Only [SideSheetValue.Resting] when
     * there is no [travel] — a sheet with nowhere to go has one place to be.
     */
    internal fun updateAnchors(travel: Float) {
        val clamped = travel.coerceAtLeast(0f)
        if (clamped == this.travel && anchoredState.anchors.size > 0) return
        this.travel = clamped
        val anchors = DraggableAnchors {
            SideSheetValue.Resting at 0f
            if (clamped > 0f) SideSheetValue.Expanded at clamped
        }
        val wanted = pending ?: anchoredState.targetValue
        pending = null
        anchoredState.updateAnchors(
            anchors,
            if (anchors.hasPositionFor(wanted)) wanted else SideSheetValue.Resting,
        )
    }

    internal companion object {
        val Saver: Saver<SideSheetState, Boolean> = Saver(
            save = { it.isExpanded },
            restore = { SideSheetState(initiallyExpanded = it) },
        )
    }
}

/** A [SideSheetState] that survives recomposition and configuration changes. */
@Composable
fun rememberSideSheetState(initiallyExpanded: Boolean = false): SideSheetState =
    rememberSaveable(saver = SideSheetState.Saver) { SideSheetState(initiallyExpanded) }
