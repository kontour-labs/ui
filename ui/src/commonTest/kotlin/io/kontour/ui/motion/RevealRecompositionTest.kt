package io.kontour.ui.motion

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.ScopeCount
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Content arriving through [revealOnScroll] composes nothing but its own reveal.
 *
 * The modifier is a composable factory, so whatever it read in composition was
 * read in its caller's scope: whether the element had come into view, and
 * whether it had been revealed. Each of those turned once per element, and each
 * time it did the whole page round it composed again, every other row's reveal
 * with it. A page of forty, scrolled past twenty of them in sixty frames,
 * composed 1,558 scopes; now 38, which is each row turning twice on its own.
 *
 * Counted rather than timed, as `RatingFillRecompositionTest` is.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeRuntimeApi::class)
class RevealRecompositionTest {

    @Test
    fun aRowArrivingDoesNotComposeThePageAgain() = runComposeUiTest {
        val scroll = ScrollState(initial = 0)
        lateinit var composition: Composition
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = false) {
                composition = currentComposer.composition
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    repeat(Rows) { row ->
                        Box(Modifier.fillMaxWidth().height(120.dp).revealOnScroll()) { Text("Stop $row") }
                    }
                }
            }
        }
        repeat(10) { mainClock.advanceTimeByFrame() }

        val scopes = ScopeCount()
        val watching = runOnUiThread { composition.setObserver(scopes) }
        repeat(2) { mainClock.advanceTimeByFrame() }
        scopes.entered = 0

        val before = scroll.value
        repeat(Frames) {
            scroll.dispatchRawDelta(40f)
            mainClock.advanceTimeByFrame()
        }
        runOnUiThread { watching?.dispose() }
        println("RevealRecompositionTest: $Frames frames composed ${scopes.entered} scopes")

        // How many rows came into view, at 120dp each, beyond the first screenful.
        val arrived = (scroll.value - before) / runOnIdle { with(density) { 120.dp.toPx() } }
        assertTrue(watching != null, "the composition could not be watched, so this measured nothing")
        assertTrue(arrived >= 8, "only $arrived rows came into view, too few to tell")
        assertTrue(
            scopes.entered <= arrived * PerRow + Slack,
            "$Frames frames of scrolling past ${arrived.toInt()} arriving rows composed ${scopes.entered} scopes: " +
                "the reveal is being read in its caller's composition again, so each row arriving composes the page",
        )
    }

    private companion object {
        const val Rows = 40
        const val Frames = 60

        /** Each row turns twice, seen and then revealed, and each turn is one small scope of its own. */
        const val PerRow = 2

        /** The rows already arriving when the count starts, and the one in view when it ends. */
        const val Slack = 6
    }
}
