package io.kontour.ui.components

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.ObservableComposition

/**
 * Counts the scopes a composition composes again, for the components' tests
 * that count what a frame of motion or a keystroke costs.
 *
 * Every scope the composition enters, less those it enters only to find that
 * the derived states which invalidated it still hold what they held: those do
 * no work, because a derived state that comes out the same changes nothing for
 * its reader. The same rule as `AnchoredOverlayRecompositionTest`'s count, and
 * the same caveat — a scope whose last read went unseen cannot be told to be
 * unchanged, so watch a frame or two before counting.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
internal class ScopeCount : CompositionObserver {
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

    /** A derived state is the `State` a composition reads that is not a `MutableState`. */
    private fun isDerived(value: Any?): Boolean = value is State<*> && value !is MutableState<*>

    private fun current(state: State<*>): Any? = Snapshot.withoutReadObservation { state.value }
}
