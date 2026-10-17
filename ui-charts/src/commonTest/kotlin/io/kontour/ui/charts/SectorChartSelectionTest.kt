package io.kontour.ui.charts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A sector chart chosen from as a chart's places are: by a tap on a sector, by
 * the arrow keys round the circle, and by a screen reader on each sector's own
 * node. Half rent, a third food and the rest fun, from the top clockwise.
 */
@OptIn(ExperimentalTestApi::class)
class SectorChartSelectionTest {

    private class Spend(val category: String, val amount: Int)

    private val month = listOf(Spend("Rent", 50), Spend("Food", 30), Spend("Fun", 20))

    private fun androidx.compose.ui.test.ComposeUiTest.pie(
        selection: ChartSelectionState<ChartSeries>,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        setContent {
            KontourTheme(reduceMotion = true) {
                // A host for the chosen sector's tooltip, as an app has one.
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        SectorChart(
                            modifier = Modifier.size(200.dp).testTag("pie"),
                            selection = selection,
                            showLegend = false,
                            animateData = false,
                        ) {
                            sectors(month, value = { it.amount }, series = { ChartSeries(it.category) })
                        }
                    }
                }
            }
        }
    }

    @Test
    fun eachSectorIsANodeAScreenReaderCanChoose() = runComposeUiTest {
        val selection = ChartSelectionState<ChartSeries>(null)
        pie(selection)
        onNodeWithContentDescription("Rent, 50%").assertExists()
        onNodeWithContentDescription("Fun, 20%").assertExists()
        onNodeWithContentDescription("Food, 30%").performClick()
        assertEquals(ChartSeries("Food"), selection.x)
        onNodeWithContentDescription("Food, 30%")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    /** The right of the circle is a quarter of the way round from the top: rent, the first half. */
    @Test
    fun aTapChoosesTheSectorUnderIt() = runComposeUiTest {
        val selection = ChartSelectionState<ChartSeries>(null)
        pie(selection)
        onNodeWithTag("pie").performTouchInput { click(Offset(center.x + width / 4, center.y)) }
        assertEquals(ChartSeries("Rent"), selection.x)
        // Just left of the top is the end of the way round: fun.
        onNodeWithTag("pie").performTouchInput { click(Offset(center.x - 4f, center.y - height / 4)) }
        assertEquals(ChartSeries("Fun"), selection.x)
    }

    /**
     * Right to left, the circle is drawn anticlockwise from the top, so its
     * right is three quarters of the way round, in food, and just right of the
     * top is the end of the way round, fun.
     */
    @Test
    fun rightToLeftTheSectorsRunTheOtherWay() = runComposeUiTest {
        val selection = ChartSelectionState<ChartSeries>(null)
        pie(selection, LayoutDirection.Rtl)
        onNodeWithTag("pie").performTouchInput { click(Offset(center.x + width / 4, center.y)) }
        assertEquals(ChartSeries("Food"), selection.x)
        onNodeWithTag("pie").performTouchInput { click(Offset(center.x + 4f, center.y - height / 4)) }
        assertEquals(ChartSeries("Fun"), selection.x)
    }

    @Test
    fun theArrowsGoRoundAndEscapeLetsGo() = runComposeUiTest {
        val selection = ChartSelectionState<ChartSeries>(null)
        pie(selection)
        onNode(isFocusable()).requestFocus()
        onNode(isFocusable()).performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(ChartSeries("Rent"), selection.x)
        onNode(isFocusable()).performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(ChartSeries("Food"), selection.x)
        onNode(isFocusable()).performKeyInput { pressKey(Key.MoveEnd) }
        assertEquals(ChartSeries("Fun"), selection.x)
        // Round past the last to the first.
        onNode(isFocusable()).performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(ChartSeries("Rent"), selection.x)
        onNode(isFocusable()).performKeyInput { pressKey(Key.Escape) }
        assertNull(selection.x)
    }
}
