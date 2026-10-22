package io.kontour.ui.charts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.locale.LocalPseudoLocale
import io.kontour.ui.locale.PseudoLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The words a chart is given are the same words from one recomposition to the next. */
@OptIn(ExperimentalTestApi::class)
class ChartStringsTest {

    /**
     * In a pseudo-locale the gallery's strings were made again at every call,
     * and since their phrases are functions, no two are equal: every chart
     * below took them for new words and wrote its description again at each
     * recomposition.
     */
    @Test
    fun pseudoLocalisedStringsAreKeptAcrossRecompositions() = runComposeUiTest {
        val seen = mutableListOf<ChartStrings>()
        var tick by mutableIntStateOf(0)
        setContent {
            CompositionLocalProvider(LocalPseudoLocale provides PseudoLocale.Accented) {
                // Read, so that a tick recomposes this.
                if (tick >= 0) seen += ChartDefaults.strings()
            }
        }
        tick++
        waitForIdle()
        assertEquals(2, seen.size, "the tick should have recomposed")
        assertSame(seen[0], seen[1], "the same strings should be given again")
    }
}
