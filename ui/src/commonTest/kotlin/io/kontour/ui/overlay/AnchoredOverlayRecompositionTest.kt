package io.kontour.ui.overlay

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Anchored overlays cost nothing to compose while nothing about them changes.
 *
 * Each of these is an overlay pointed at something on the page, and the thing it
 * points at is a rectangle that moves on every frame its page scrolls. That
 * rectangle belongs to the overlay's measure pass, which reads it to place the
 * panel. Wherever composition read it as well, every frame of a scroll composed
 * the overlay again — once per row, for overlays none of which were showing.
 * Not visible in any picture; counted.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeRuntimeApi::class)
class AnchoredOverlayRecompositionTest {

    /**
     * A list of rows with tooltips and popovers on them, none of them open, does
     * not compose any of them again as it scrolls.
     *
     * The tooltip's own caller stopped recomposing a while ago —
     * `RecompositionCostTest` has it — by moving the read into the tooltip. That
     * moved the cost rather than removing it. Counted by the composition itself,
     * which reports every scope it recomposes: nothing about the page changes as
     * it scrolls, so nothing should.
     */
    @Test
    fun closedTooltipsAndPopoversDoNotRecomposeAsTheirRowsScroll() {
        runComposeUiTest {
            val scroll = ScrollState(initial = 0)
            lateinit var composition: Composition
            mainClock.autoAdvance = false
            setContent {
                KontourTheme {
                    OverlayHost(Modifier.fillMaxSize()) {
                        composition = currentComposer.composition
                        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                            repeat(Rows) { row ->
                                Box(Modifier.size(40.dp).tooltip("Route $row")) {
                                    Popover(visible = false, onDismissRequest = {}) { Text("Stop $row") }
                                }
                            }
                            Spacer(Modifier.height(2000.dp))
                        }
                    }
                }
            }
            repeat(4) { mainClock.advanceTimeByFrame() }
            val scopes = ScopeCount()
            val watching = runOnUiThread { composition.setObserver(scopes) }
            // Frames to see what each scope reads before counting: a scope
            // whose last read went unseen cannot be told to be unchanged. Two,
            // because a scroll is laid out in one frame, and what its new
            // positions invalidate is composed in the next.
            repeat(2) {
                scroll.dispatchRawDelta(10f)
                mainClock.advanceTimeByFrame()
            }
            scopes.entered = 0

            repeat(ScrolledFrames) {
                scroll.dispatchRawDelta(10f)
                mainClock.advanceTimeByFrame()
            }
            runOnUiThread { watching?.dispose() }

            assertTrue(watching != null, "the composition could not be watched, so this measured nothing")
            assertTrue(scroll.value > 0, "the column never scrolled")
            assertTrue(
                scopes.entered <= Rows,
                "$ScrolledFrames frames of scrolling recomposed ${scopes.entered} scopes round $Rows rows of " +
                    "closed tooltips and popovers: their anchors are being read in composition",
            )
        }
    }

    /**
     * A coach mark waiting its turn does not recompose the screen it is on.
     *
     * Its request is registered with the queue after every composition, and the
     * queue keeps the condition in snapshot state that the coach mark's own
     * composition reads — so a new condition each time was a change to something
     * it had just read. It composed again, registered again, and the screen round
     * a pending coach mark recomposed on every frame.
     */
    @Test
    fun aPendingCoachmarkDoesNotRecomposeItsScreenEveryFrame() {
        runComposeUiTest {
            var compositions = 0
            mainClock.autoAdvance = false
            setContent {
                KontourTheme {
                    OverlayHost(Modifier.fillMaxSize()) {
                        val queue = rememberOverlayQueue()
                        CompositionLocalProvider(LocalOverlayQueue provides queue) {
                            CoachmarkCaller { compositions++ }
                        }
                    }
                }
            }
            repeat(10) { mainClock.advanceTimeByFrame() }
            val settled = compositions
            assertTrue(settled >= 1, "the coach mark's caller never composed")

            repeat(IdleFrames) { mainClock.advanceTimeByFrame() }

            assertEquals(
                settled,
                compositions,
                "$IdleFrames frames with nothing happening recomposed a coach mark's caller " +
                    "${compositions - settled} times",
            )
        }
    }

    /**
     * Counts the scopes a composition recomposes.
     *
     * Not every scope the observer is told of entering: a scope invalidated
     * only by derived states is entered as well when none of them has changed,
     * to read them again, and its body is *not* run — the runtime compares each
     * derived state with what the scope last read of it, and skips it. That is
     * the remedy for a value that moves on every frame and a decision that does
     * not, and it is what the overlays do now: whether there is an anchor, not
     * where it is. Counting those visits counted the remedy as the fault. So a
     * scope whose invalidations since it last ran were all derived states, each
     * still what the scope last read, is not counted, as the runtime does not
     * recompose it. Anything else is: a plain state read in composition — the
     * anchor's rectangle, which is what this is here to catch — or a derived
     * state whose value did change.
     */
    private class ScopeCount : CompositionObserver {
        var entered = 0

        /** What each scope last read of each derived state it read. */
        private val derivedRead = HashMap<RecomposeScope, HashMap<State<*>, Any?>>()

        /** What has invalidated each scope since it last ran: derived states, or null for anything else. */
        private val invalidatedBy = HashMap<RecomposeScope, MutableList<State<*>?>>()

        override fun onBeginComposition(composition: ObservableComposition) = Unit
        override fun onScopeEnter(scope: RecomposeScope) {
            val by = invalidatedBy.remove(scope)
            val read = derivedRead[scope]
            val unchanged = by != null && by.isNotEmpty() && read != null && by.all { state ->
                state != null && state in read && current(state) == read[state]
            }
            if (!unchanged) entered++
        }
        override fun onReadInScope(scope: RecomposeScope, value: Any) {
            if (isDerived(value)) derivedRead.getOrPut(scope) { HashMap() }[value as State<*>] = current(value)
        }
        override fun onScopeExit(scope: RecomposeScope) = Unit
        override fun onEndComposition(composition: ObservableComposition) = Unit
        override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) {
            invalidatedBy.getOrPut(scope) { mutableListOf() } += if (isDerived(value)) value as State<*> else null
        }
        override fun onScopeDisposed(scope: RecomposeScope) {
            derivedRead.remove(scope)
            invalidatedBy.remove(scope)
        }

        /**
         * Whether a value read in composition is a derived state. The runtime's
         * own type for one is internal; of the state a composition reads, it is
         * the one that is a `State` without being a `MutableState`.
         */
        private fun isDerived(value: Any?): Boolean = value is State<*> && value !is MutableState<*>

        /** A derived state's value now, without this being a read of it in the scope being composed. */
        private fun current(state: State<*>): Any? = Snapshot.withoutReadObservation { state.value }
    }

    private companion object {
        const val Rows = 8
        const val ScrolledFrames = 20
        const val IdleFrames = 10
    }
}

/** Counts its own compositions, and carries a coach mark the queue has not yet shown or dismissed. */
@Composable
private fun CoachmarkCaller(onCompose: () -> Unit) {
    SideEffect(onCompose)
    Box(
        Modifier
            .size(40.dp)
            .coachmark(id = "save-trip", title = "Save this trip", message = "Trips you save show up here."),
    )
}
