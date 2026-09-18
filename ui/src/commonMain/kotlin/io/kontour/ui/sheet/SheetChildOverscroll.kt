package io.kontour.ui.sheet

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Velocity

/**
 * The overscroll a scrollable gets while it is **inside a draggable sheet**:
 * whatever the platform gives it sideways, and nothing at all up and down.
 *
 * A list in a sheet shares one axis with the sheet, and only one of them can
 * answer a finger that has run out of list. [SheetState.nestedScrollConnection]
 * says which: dragging down scrolls the list to its top and then moves the sheet.
 * That is the promise in the docs, and on iOS it was not kept.
 *
 * ### Why it was iOS and not Android
 *
 * Each platform hands a scrollable a different [OverscrollEffect] and this
 * library never had an opinion about it: `null` on desktop, an edge stretch on
 * Android, `CupertinoOverscrollEffect` on iOS. The effect wraps the whole scroll
 * — foundation's `ScrollingLogic` calls
 * `overscroll.applyToScroll(delta) { parent.onPreScroll → own scrollBy → parent.onPostScroll }`
 * — so it sees the finger both before the sheet is offered anything and after the
 * sheet has declined it, and the iOS one uses both halves. It withholds delta to
 * unwind a band it already has open *before* calling through, which is frames in
 * which the sheet is offered nothing at all and the content visibly pulls down;
 * and `ScrollingLogic.onScrollStopped` hands it the release through
 * `applyToFling`, where it keeps velocity for its own spring — so the sheet's
 * `onPostFling` reads a throw smaller than the one the finger made, and a flick
 * that would have crossed [SheetFlickVelocity] on Android does not.
 *
 * Reported as both halves of exactly that: *"i feel like i have to flick with
 * more power on iOS"*, and *"sometimes when i start dragging the sheet with a
 * scrolling list, it instead just tries to pull the content down"*. The
 * *sometimes* is the open band — whether there is one to unwind depends on what
 * the gesture did just before, which is why it is intermittent rather than
 * constant.
 *
 * ### One rule, every platform
 *
 * Not an `expect`/`actual` per platform, and not a change to any platform's
 * rubber band — the curves are the platforms' and stay theirs. What changes is
 * *whose* gesture a declined vertical drag is, and that is one decision for the
 * whole library: inside a draggable sheet the sheet's own stretch is the only one
 * on the vertical axis. It is also what desktop has always done, since it has no
 * child overscroll at all, and desktop is the behaviour that was reported as
 * correct.
 *
 * Sideways is untouched. A sheet has no opinion about horizontal scroll, so a
 * carousel in one keeps the platform's bounce: [inner] is handed every delta and
 * velocity with no vertical component and nothing else. A diagonal drag counts as
 * vertical, which costs a `LazyRow` its bounce on the frames a finger is sliding
 * off the axis and is not worth a second rule to recover.
 *
 * @param inner The platform's own effect, or `null` where the platform has none.
 */
internal class SheetChildOverscroll(
    private val inner: OverscrollEffect?,
) : OverscrollEffect {

    override val isInProgress: Boolean
        get() = inner?.isInProgress ?: false

    // The wrapped effect's own node, because it is the thing that draws: this
    // class adds no picture of its own, it only decides who is offered what.
    // Only one modifier ever attaches it — the effect is built here and handed
    // straight to the scrollable that asked for it — so there is no second
    // owner to conflict with. Foundation's own `withoutEventHandling` wrapper
    // delegates the node the same way.
    override val node: DelegatableNode = inner?.node ?: object : Modifier.Node() {}

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        // Straight through, consuming nothing. What the scroll and the sheet
        // between them decline stays declined, and the sheet's own overscroll —
        // which is the one the sheet's nested-scroll connection feeds — is the
        // only thing that can absorb it.
        if (inner == null || delta.y != 0f) return performScroll(delta)
        return inner.applyToScroll(delta, source, performScroll)
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        if (inner == null || velocity.y != 0f) {
            performFling(velocity)
            return
        }
        inner.applyToFling(velocity, performFling)
    }
}

/**
 * Builds a [SheetChildOverscroll] around whatever the platform would have given.
 *
 * A `data class` on purpose: [OverscrollFactory] declares `equals` and `hashCode`
 * **abstract**, and a scrollable's node re-reads [LocalOverscrollFactory] through
 * `onObservedReadsChanged`. Structural equality is what stops an equal factory
 * from counting as a new one and rebuilding the effect under a finger.
 */
internal data class SheetChildOverscrollFactory(
    private val inner: OverscrollFactory?,
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect =
        SheetChildOverscroll(inner?.createOverscrollEffect())
}

/**
 * The factory a sheet provides to its content, or the platform's own unchanged.
 *
 * [draggable] is the gate, and it is the same gate the nested-scroll connection
 * is under for the same reason: where the sheet does not take the scroll, nothing
 * would answer a list that has reached its top, and a list that stops dead is the
 * rigid boundary this library spends a whole primitive avoiding. So a sheet that
 * cannot be dragged leaves its content's overscroll exactly as it found it.
 *
 * A composition local rather than a parameter because the scrollable belongs to
 * the caller: a `LazyColumn` in a sheet is written by whoever wrote the screen,
 * and its no-argument overload is the one that reads this.
 */
@Composable
internal fun rememberSheetChildOverscrollFactory(draggable: Boolean): OverscrollFactory? {
    val platform = LocalOverscrollFactory.current
    return remember(platform, draggable) {
        if (draggable) SheetChildOverscrollFactory(platform) else platform
    }
}
