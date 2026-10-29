package io.kontour.ui.components.text

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.State
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Typing into a field does not compose it again, once there is text in it.
 *
 * The text is the field's state and `BasicTextField` reads it where it lays the
 * text out, so a keystroke needs no composition at all. Two reads of it in
 * composition cost one anyway: the placeholder asked `state.text.isEmpty()` in
 * the field's input slot, and a search field's clear button asked
 * `isNotEmpty()` in its own. Each keystroke changed what those slots had read,
 * so each composed again — the input slot four scopes, with `BasicTextField`
 * handed new keyboard options and modifiers, and a search field eight, its
 * clear button's `AnimatedVisibility` and the button among them — to be told
 * what they already knew. Ten keystrokes composed 40 scopes and 80; now none.
 *
 * Counted by the composition itself, as `AnchoredOverlayRecompositionTest`
 * counts its scroll: on a cheap phone a keystroke's frame is the one the
 * reader is watching, and a stopwatch here would time this machine.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeRuntimeApi::class)
class FieldKeystrokeRecompositionTest {

    @Test
    fun typingIntoATextFieldComposesNothing() = typing("TextField") { state ->
        TextField(state = state, label = "Destination", placeholder = "Where to?", modifier = Modifier.width(300.dp))
    }

    @Test
    fun typingIntoASearchFieldComposesNothing() = typing("SearchField") { state ->
        SearchField(state = state, modifier = Modifier.width(300.dp))
    }

    /**
     * Types into [field] and counts the scopes composed for it.
     *
     * The first character is left out of the count, and so is the motion it
     * starts: it hides the placeholder and brings a search field's clear button
     * in, and those are compositions that change what is drawn.
     */
    private fun typing(what: String, field: @Composable (TextFieldState) -> Unit) = runComposeUiTest {
        val state = TextFieldState()
        lateinit var composition: Composition
        mainClock.autoAdvance = false
        setContent {
            KontourTheme {
                composition = currentComposer.composition
                field(state)
            }
        }
        repeat(4) { mainClock.advanceTimeByFrame() }
        type(state, "P")
        // Past the clear button's entrance, however long the theme makes it.
        repeat(60) { mainClock.advanceTimeByFrame() }

        val scopes = ScopeCount()
        val watching = runOnUiThread { composition.setObserver(scopes) }
        // Keystrokes to see what each scope reads before counting, as in
        // `AnchoredOverlayRecompositionTest`: a scope whose last read went
        // unseen cannot be told to be unchanged.
        repeat(2) {
            type(state, "e")
            mainClock.advanceTimeByFrame()
        }
        scopes.entered = 0

        repeat(Keystrokes) {
            type(state, "r")
            mainClock.advanceTimeByFrame()
        }
        runOnUiThread { watching?.dispose() }

        assertTrue(watching != null, "the composition could not be watched, so this measured nothing")
        assertEquals("Pee" + "r".repeat(Keystrokes), state.text.toString(), "the keystrokes did not all reach the $what")
        assertEquals(
            0,
            scopes.entered,
            "$Keystrokes keystrokes into a $what that already had text composed ${scopes.entered} scopes: something " +
                "in it is reading the text in composition rather than whether there is any",
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.type(state: TextFieldState, text: String) =
        runOnUiThread { state.edit { append(text) } }

    /**
     * Every scope the composition enters, less those it enters only to find
     * that the derived states which invalidated it still hold what they held.
     * Those do no work: a derived state that comes out the same changes nothing
     * for its reader, which is the whole of the fix this counts.
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

        /** A derived state is the `State` a composition reads that is not a `MutableState`. */
        private fun isDerived(value: Any?): Boolean = value is State<*> && value !is MutableState<*>

        private fun current(state: State<*>): Any? = Snapshot.withoutReadObservation { state.value }
    }

    private companion object {
        const val Keystrokes = 10
    }
}
