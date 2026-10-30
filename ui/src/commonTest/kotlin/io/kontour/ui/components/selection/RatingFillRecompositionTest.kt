package io.kontour.ui.components.selection

import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.components.ScopeCount
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A rating's marks fill and spring without being composed for every frame of it.
 *
 * Setting a score eases the fill of every mark between the old score and the new
 * and springs each one that fills, and both springs were read in composition:
 * each frame composed every moving mark again — its box, both its icons and the
 * modifiers round them — for a clip and a scale that the draw pass can move on
 * its own. One score from one to four composed 117 scopes over the second it
 * took to settle; now 27 — the row, and each mark once for its new fill and
 * once as it starts or stops being drawn — and the springs repaint them.
 *
 * Counted rather than timed, as `FieldKeystrokeRecompositionTest` is.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeRuntimeApi::class)
class RatingFillRecompositionTest {

    @Test
    fun aNewScoreIsNotComposedOnEveryFrameOfItsFill() = runComposeUiTest {
        var score by mutableFloatStateOf(1f)
        lateinit var composition: Composition
        mainClock.autoAdvance = false
        setContent {
            KontourTheme {
                composition = currentComposer.composition
                Rating(value = score, onValueChange = { score = it }, contentDescription = "Stop rating")
            }
        }
        repeat(10) { mainClock.advanceTimeByFrame() }

        val scopes = ScopeCount()
        val watching = runOnUiThread { composition.setObserver(scopes) }
        repeat(2) { mainClock.advanceTimeByFrame() }
        scopes.entered = 0

        score = 4f
        repeat(Frames) { mainClock.advanceTimeByFrame() }
        runOnUiThread { watching?.dispose() }

        assertTrue(watching != null, "the composition could not be watched, so this measured nothing")
        assertTrue(scopes.entered > 0, "the new score composed nothing, so the rating never saw it")
        assertTrue(
            scopes.entered <= Budget,
            "a new score composed ${scopes.entered} scopes over $Frames frames: the marks' fill or spring is " +
                "being read in composition again, so every frame of it composes every moving mark",
        )
    }

    private companion object {
        /** Long enough for the bouncy spring to settle. */
        const val Frames = 60

        /**
         * The 27 a new score honestly costs, with room. A mark composed on every
         * frame of its springs is past a hundred.
         */
        const val Budget = 40
    }
}
