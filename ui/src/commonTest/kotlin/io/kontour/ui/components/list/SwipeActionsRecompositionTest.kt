package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A row with swipe actions is composed once as it appears, not twice.
 *
 * Its anchors wait for its width, which its first measure gives it, and the
 * effect that sets them was keyed on the width in the row's own body. A key is
 * a read in composition, so every row composed again on the frame after it
 * appeared, its whole body and modifier chain: in a list, every row scrolled
 * into view, 6 scopes each on top of the 59 it took to compose. The width is
 * read in a scope of its own now, which is all that composes again.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeRuntimeApi::class)
class SwipeActionsRecompositionTest {

    @Test
    fun aRowIsNotComposedAgainOnceItHasItsWidth() = runComposeUiTest {
        var shown by mutableStateOf(false)
        lateinit var composition: Composition
        val archive = listOf(SwipeAction("Archive", SystemIcons.Default.check, {}, Color.Blue))
        val delete = listOf(SwipeAction("Delete", SystemIcons.Default.close, {}, Color.Red))
        mainClock.autoAdvance = false
        setContent {
            KontourTheme {
                composition = currentComposer.composition
                Column(Modifier.fillMaxSize()) {
                    if (shown) {
                        repeat(Rows) { row ->
                            SwipeActions(start = archive, end = delete) {
                                ListItem(onClick = {}) { +"Stop $row" }
                            }
                        }
                    }
                }
            }
        }
        repeat(2) { mainClock.advanceTimeByFrame() }
        val scopes = Scopes()
        val watching = runOnUiThread { composition.setObserver(scopes) }

        shown = true
        mainClock.advanceTimeByFrame()
        val appearing = scopes.entered
        scopes.entered = 0
        repeat(5) { mainClock.advanceTimeByFrame() }
        val after = scopes.entered
        runOnUiThread { watching?.dispose() }

        assertTrue(watching != null, "the composition could not be watched, so this measured nothing")
        assertTrue(appearing >= Rows * 20, "the rows composed in only $appearing scopes: they did not appear")
        assertTrue(
            after <= Rows * 2,
            "$Rows rows composed $after scopes again in the frames after they appeared, with nothing " +
                "changed: each row is reading its width in composition, and composing its whole body " +
                "again once it has one",
        )
    }

    private class Scopes : CompositionObserver {
        var entered = 0
        override fun onBeginComposition(composition: ObservableComposition) = Unit
        override fun onScopeEnter(scope: RecomposeScope) { entered++ }
        override fun onReadInScope(scope: RecomposeScope, value: Any) = Unit
        override fun onScopeExit(scope: RecomposeScope) = Unit
        override fun onEndComposition(composition: ObservableComposition) = Unit
        override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
        override fun onScopeDisposed(scope: RecomposeScope) = Unit
    }

    private companion object {
        const val Rows = 8
    }
}
