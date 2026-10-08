package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A hero bar drawn over its list and following the list's scroll — the wiring
 * asked for after an iPhone's *"sometimes when i start scrolling up, it doesn't
 * and just does the overscroll animation"* — moves exactly with the rows, and
 * stays wherever the finger leaves it.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarFollowTest {

    private lateinit var collapse: TopBarCollapseState

    private fun ComposeUiTest.hero() {
        setContent {
            KontourTheme(reduceMotion = true) {
                val list = rememberLazyListState()
                collapse = rememberTopBarCollapseState(list)
                Box(Modifier.size(360.dp, 700.dp)) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize().testTag("list"),
                        contentPadding = PaddingValues(top = collapse.openHeight),
                    ) {
                        items(40) { index ->
                            Box(Modifier.fillMaxWidth().height(56.dp).background(Color.Gray).testTag("row$index"))
                        }
                    }
                    TopBar(
                        modifier = Modifier.testTag("bar"),
                        style = TopBarStyle.Hero,
                        image = { Box(Modifier.fillMaxSize().background(Color.Black)) },
                        collapseState = collapse,
                        windowInsets = WindowInsets(0),
                    ) { +"Title" }
                }
            }
        }
        waitForIdle()
    }

    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs((expected - actual).value) <= 1f, "$what: expected $expected, was $actual")

    /** The bar's foot against the first row's top: level, wherever the collapse has got to. */
    private fun ComposeUiTest.footOnFirstRow(what: String) {
        near(onNodeWithTag("row0").getBoundsInRoot().top, onNodeWithTag("bar").getBoundsInRoot().bottom, what)
    }

    @Test
    fun theOpenHeightIsTheBarsAndTheListStartsUnderIt() = runComposeUiTest {
        hero()
        near(onNodeWithTag("bar").getBoundsInRoot().height, collapse.openHeight, "the open height")
        footOnFirstRow("open")
        assertEquals(0f, collapse.progress)
    }

    @Test
    fun theBarsFootMovesWithTheRowsAndStaysWhereTheFingerLeavesIt() = runComposeUiTest {
        hero()
        onRoot().performTouchInput {
            down(Offset(centerX, centerY + 100f))
            repeat(10) { moveBy(Offset(0f, -8f)) }
        }
        waitForIdle()
        val midway = collapse.progress
        assertTrue(midway > 0f && midway < 1f, "a short drag up did not part-collapse the bar: $midway")
        footOnFirstRow("while the finger is on it")
        onRoot().performTouchInput { up() }
        waitForIdle()
        // No finish of its own: where the list is, the bar is.
        footOnFirstRow("let go part-way")
        assertTrue(collapse.progress > 0f && collapse.progress < 1f, "the bar finished on its own after a part-way drag: ${collapse.progress}")
    }

    @Test
    fun scrolledFarItIsCollapsedAndBackAtTheTopOpen() = runComposeUiTest {
        hero()
        onRoot().performTouchInput { swipeUp(centerY + 200f, centerY - 300f) }
        waitForIdle()
        assertEquals(1f, collapse.progress)
        repeat(8) { onRoot().performTouchInput { swipeDown(centerY - 150f, centerY + 300f) } }
        waitForIdle()
        assertEquals(0f, collapse.progress)
        footOnFirstRow("back at the top")
    }
}
