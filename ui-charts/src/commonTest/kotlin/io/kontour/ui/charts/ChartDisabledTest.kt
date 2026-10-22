package io.kontour.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A chart that cannot be chosen from still says what is chosen on it, marks
 * itself unavailable, and its legend hides nothing; and a series the legend
 * has hidden keeps a label that can be read.
 */
@OptIn(ExperimentalTestApi::class)
class ChartDisabledTest {

    private class Spend(val month: String, val what: String, val amount: Double)

    private val quarter = listOf(
        Spend("Jul", "Rent", 1450.0), Spend("Jul", "Food", 520.0),
        Spend("Aug", "Rent", 1450.0), Spend("Aug", "Food", 610.0),
    )

    private val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
    private val disabled = SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)

    /** The page the chart is drawn on, as the theme has it. */
    private var page = Color.Unspecified

    private fun ComposeUiTest.bars(enabled: Boolean, visibility: ChartSeriesVisibilityState) {
        setContent {
            KontourTheme(reduceMotion = true) {
                page = Theme.colours.background
                OverlayHost(Modifier.fillMaxSize().background(page)) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.size(360.dp, 260.dp),
                        enabled = enabled,
                        selection = ChartSelectionState("Aug"),
                        seriesVisibility = visibility,
                        animateData = false,
                    ) {
                        bar(quarter, x = { it.month }, y = { it.amount }, series = { ChartSeries(it.what) })
                    }
                }
            }
        }
    }

    /**
     * Disabled, the place chosen is drawn chosen and was said as nothing: its
     * node was marked selected only while the chart could be chosen from.
     */
    @Test
    fun aDisabledChartSaysWhatIsChosenAndThatItIsUnavailable() = runComposeUiTest {
        bars(enabled = false, visibility = ChartSeriesVisibilityState(emptySet()))
        onNodeWithContentDescription("Aug: ", substring = true).assert(selected).assert(disabled)
        onNodeWithContentDescription("Jul: ", substring = true).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    /** Disabled, its legend hides nothing: it was a switch whatever the chart was. */
    @Test
    fun aDisabledChartsLegendHidesNothing() = runComposeUiTest {
        val visibility = ChartSeriesVisibilityState(emptySet())
        bars(enabled = false, visibility = visibility)
        val entry = onNode(isToggleable() and hasText("Food"))
        entry.assert(disabled)
        entry.performClick()
        assertTrue(visibility.hidden.isEmpty(), "a disabled chart's legend hid ${visibility.hidden}")
    }

    /** The same for a pie. */
    @Test
    fun aDisabledPieSaysWhatIsChosenAndItsLegendHidesNothing() = runComposeUiTest {
        val visibility = ChartSeriesVisibilityState(emptySet())
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    SectorChart(
                        modifier = Modifier.size(300.dp, 200.dp),
                        enabled = false,
                        selection = ChartSelectionState(ChartSeries("Food")),
                        seriesVisibility = visibility,
                        animateData = false,
                    ) {
                        sectors(listOf("Rent" to 50, "Food" to 30, "Fun" to 20), value = { it.second }, series = { ChartSeries(it.first) })
                    }
                }
            }
        }
        onNodeWithContentDescription("Food, 30%").assert(selected).assert(disabled)
        onNode(isToggleable() and hasText("Fun")).performClick()
        assertTrue(visibility.hidden.isEmpty(), "a disabled pie's legend hid ${visibility.hidden}")
    }

    /**
     * A hidden series' legend entry was told off by fading the whole of it to
     * 38%, its name with it, which left the name at under 3:1 on the page.
     * The name stays readable, at 4.5:1 or more, and the swatch says it is off.
     */
    @Test
    fun aHiddenSeriesNameCanStillBeRead() = runComposeUiTest {
        val visibility = ChartSeriesVisibilityState(setOf(ChartSeries("Food")))
        bars(enabled = true, visibility = visibility)
        val name = onNode(hasText("Food"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pixels = onRoot().captureToImage().toPixelMap()
        var strongest = 1f
        for (y in name.top.toInt() until name.bottom.toInt()) for (x in name.left.toInt() until name.right.toInt()) {
            strongest = max(strongest, contrast(pixels[x, y], page))
        }
        assertTrue(strongest >= 4.5f, "a hidden series' name should hold 4.5:1 on the page: $strongest")
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }
}
