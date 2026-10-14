package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private const val FooterWords = "Departures refresh every minute."

/**
 * How tall a section is: given a height, and asked for one.
 */
@OptIn(ExperimentalTestApi::class)
class ListSectionHeightTest {

    /**
     * A weighted child of a section of a fixed height takes what the header and
     * footer leave it, and the footer stays inside the section.
     *
     * The body was measured against the section's whole height, so the weight
     * took all of it, and the header and footer were stacked on top: the footer
     * was drawn below the section, over whatever came next.
     */
    @Test
    fun aWeightedChildTakesWhatTheHeaderAndFooterLeave() = runComposeUiTest {
        setContent {
            KontourTheme {
                Box(Modifier.fillMaxSize()) {
                    ListSection(
                        modifier = Modifier.width(320.dp).height(300.dp).testTag("section"),
                        title = { +"Nearby" },
                        footer = { +FooterWords },
                    ) {
                        Box(Modifier.weight(1f).fillMaxWidth().testTag("fill"))
                    }
                }
            }
        }
        waitForIdle()
        val section = onNodeWithTag("section").getUnclippedBoundsInRoot()
        val fill = onNodeWithTag("fill").getUnclippedBoundsInRoot()
        val footer = onNodeWithText(FooterWords).getUnclippedBoundsInRoot()

        assertTrue(footer.bottom <= section.bottom + 0.5.dp, "the footer ends at ${footer.bottom}, past the section's ${section.bottom}")
        assertTrue(fill.bottom <= footer.top + 0.5.dp, "the weighted child (to ${fill.bottom}) runs under the footer (from ${footer.top})")
        assertTrue(fill.bottom > fill.top, "the weighted child has no height")
        assertTrue(
            abs((section.bottom - footer.bottom).value) < 1f,
            "the section has ${section.bottom - footer.bottom} spare under its footer: the weight did not take the rest",
        )
    }

    /**
     * Asked for its height, a section over rows counts its title as it lays it out.
     *
     * The title here wraps only at the rows' inset. The section's intrinsic
     * height used to be worked out as if its content were not rows — an
     * intrinsic measurement has no alignment lines to say they are — so the
     * title was counted one line, laid out as two, and a section in a row of
     * `IntrinsicSize.Min` was a line too short for what it drew.
     */
    @Test
    fun anIntrinsicHeightCountsTheTitleAtTheRowsInset() = runComposeUiTest {
        val width = 320.dp
        setContent {
            KontourTheme {
                Box(Modifier.fillMaxSize()) {
                    Column {
                        Box(Modifier.width(width).height(IntrinsicSize.Min).testTag("asked")) {
                            Section(width)
                        }
                        Box(Modifier.width(width).testTag("laid out")) {
                            Section(width)
                        }
                    }
                }
            }
        }
        waitForIdle()
        val asked = onNodeWithTag("asked").getUnclippedBoundsInRoot()
        val laidOut = onNodeWithTag("laid out").getUnclippedBoundsInRoot()
        val askedHeight = asked.bottom - asked.top
        val laidOutHeight = laidOut.bottom - laidOut.top

        assertTrue(
            askedHeight >= laidOutHeight - 0.5.dp,
            "asked, the section said $askedHeight; laid out, it takes $laidOutHeight",
        )
    }

    /**
     * Over a row, with a title one line wide at the section's full width less
     * the header's 8dp end padding, and two lines at the rows' 16dp inset less
     * that.
     */
    @Composable
    private fun Section(width: Dp) {
        ListSection(title = { WrapsBelow(width - 8.dp) }) {
            Box(ListRowEdges.fillMaxWidth().height(56.dp))
        }
    }

    /**
     * A stand-in for a title that wraps: one line, 20dp, at [at] wide or
     * wider, and two, 40dp, narrower — measured and asked alike, so nothing
     * here depends on a font.
     */
    @Composable
    private fun WrapsBelow(at: Dp) {
        Layout(
            content = {},
            measurePolicy = object : MeasurePolicy {
                fun IntrinsicMeasureScope.heightAt(width: Int): Int =
                    (if (width < at.roundToPx()) 40.dp else 20.dp).roundToPx()

                override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult =
                    layout(constraints.maxWidth, heightAt(constraints.maxWidth)) {}

                override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
                    heightAt(width)

                override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
                    heightAt(width)

                override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
                    at.roundToPx()

                override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
                    at.roundToPx()
            },
        )
    }
}
