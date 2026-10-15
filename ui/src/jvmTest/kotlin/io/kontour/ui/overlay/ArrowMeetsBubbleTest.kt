package io.kontour.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Density
import io.kontour.ui.components.list.IndexAlphabet
import io.kontour.ui.components.list.IndexScroller
import io.kontour.ui.components.list.indexedBy
import io.kontour.ui.foundation.Text
import io.kontour.ui.locale.localeFormatsFor
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An arrow meets its bubble along the bubble's own edge.
 *
 * Reported on the index scroller: *"the little arrow next to the bubble in the
 * index scroller is not quite properly flush with the bubble"*. The bubble is
 * one letter in a squircle, so very nearly round, and the arrow's base was on
 * its bounding box: its corners stood out past the side, which had already
 * curved away, as a ledge either side of the arrow.
 *
 * Read off the picture, column by column from the arrow's tip back into the
 * bubble: the dark shape there only ever grows taller. A ledge is a column
 * taller than the next one in.
 */
@OptIn(ExperimentalTestApi::class)
class ArrowMeetsBubbleTest {

    @Test
    fun theIndexScrollersBubbleAndItsArrowAreOneShape() = runDesktopComposeUiTest(width = 1400, height = 2800) {
        val names = ('A'..'Z').map { "$it name" }
        val indexed = names.indexedBy(IndexAlphabet.Latin, localeFormatsFor(Locale("en"))) { it }
        setContent {
            // Large, so a step of a fraction of a dp is pixels.
            CompositionLocalProvider(LocalDensity provides Density(4f)) {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize().background(Theme.colours.background)) {
                        Box(Modifier.fillMaxSize()) {
                            val list = rememberLazyListState()
                            LazyColumn(state = list) { items(names.size) { Text(names[it]) } }
                            IndexScroller(
                                state = list,
                                sections = indexed.sections,
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().testTag("index"),
                                alphabet = IndexAlphabet.Latin,
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("index").performTouchInput { down(center) }
        mainClock.advanceTimeBy(500)
        waitForIdle()

        val image = onRoot().captureToImage().toPixelMap()
        fun dark(x: Int, y: Int): Boolean = image[x, y].let { it.red < 0.3f && it.green < 0.3f && it.blue < 0.3f }

        // The bubble is the tallest dark shape on the right of the picture; the
        // rail's letters are a line of small ones beside it.
        fun count(x: Int, from: Int = 0, to: Int = image.height) = (from until to).count { dark(x, it) }
        val centreX = (image.width / 2 until image.width).maxBy { count(it) }
        val rows = (0 until image.height).filter { dark(centreX, it) }
        val midY = (rows.first() + rows.last()) / 2
        // Out from the bubble to where the arrow ends.
        var tipX = centreX
        while (tipX + 1 < image.width && count(tipX + 1, midY - 40, midY + 40) > 0) tipX++
        assertTrue(tipX - centreX > 60, "no arrow beside the bubble: was the finger down?")

        // Each column's dark run through the arrow's centre line, from the tip in.
        fun run(x: Int): Int {
            if (!dark(x, midY)) return 0
            var up = midY
            while (up > 0 && dark(x, up - 1)) up--
            var down = midY
            while (down < image.height - 1 && dark(x, down + 1)) down++
            return down - up + 1
        }
        // Back from the tip past the arrow's base, short of the letter in the middle.
        val runs = (tipX downTo tipX - 70).map(::run)
        val steps = (1 until runs.size).filter { runs[it] < runs[it - 1] - 1 }
        assertTrue(
            steps.isEmpty(),
            "from the arrow's tip in, the dark shape got shorter again at " +
                steps.joinToString { "${it}px in (${runs[it - 1]} to ${runs[it]})" } +
                ": the arrow's base stands out past the bubble's side",
        )
    }
}
