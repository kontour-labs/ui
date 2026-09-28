package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DotsVertical
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.SheetHeaderStyle
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A centred top bar's title is centred on the **bar**.
 *
 * Reported as: hide the back button and the title is no longer centred, it moves
 * off to the left a bit. It does, and a `Row` of leading, weighted title,
 * trailing cannot do otherwise — a weighted child is centred in the space its
 * siblings left over, which is the middle of the bar only when the two sides
 * happen to be the same width.
 *
 * The title's own centre against the bar's, with a back button and without one.
 * Both have to land in the middle; the second is the reported case and the first
 * is what stops a fix that simply moves the error to the other configuration.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarCentringTest {

    @Test
    fun aCentredTitleSitsInTheMiddleWithOrWithoutABackButton() {
        val withBack = titleCentre(back = true)
        val withoutBack = titleCentre(back = false)

        assertTrue(
            abs(withBack - BarCentre) <= Tolerance,
            "with a back button the title's centre was ${withBack}dp against the " +
                "bar's ${BarCentre}dp",
        )
        assertTrue(
            abs(withoutBack - BarCentre) <= Tolerance,
            "with the back button hidden the title's centre was ${withoutBack}dp " +
                "against the bar's ${BarCentre}dp — it is centred on what the " +
                "controls left over rather than on the bar",
        )
    }

    /**
     * A title too long to be centred is laid out exactly as a Small bar lays it
     * out — against the leading controls — rather than centred in what they left.
     *
     * Reported against the sheet header with two actions at one end: "the
     * centred style is not really that centred". It was centred, on the leftover
     * space; now it is either centred on the bar or not centred at all.
     */
    @Test
    fun aTitleThatCannotBeCentredStartsLikeSmall() {
        val long = "Perth Underground to Elizabeth Quay via the Esplanade"
        val centred = titleLeft(TopBarStyle.Centred, long)
        val small = titleLeft(TopBarStyle.Small, long)
        assertTrue(
            abs(centred - small) <= 0.5f,
            "a Centred bar whose title does not fit started it at ${centred}dp, a Small one at ${small}dp",
        )
    }

    @Test
    fun aSheetHeaderTitleIsCentredOnTheSheetDespiteItsUnevenPadding() {
        var centre = -1f
        runComposeUiTest {
            setContent {
                KontourTheme {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        SheetHeader(
                            modifier = Modifier.width(BarWidth.dp),
                            onClose = {},
                            style = SheetHeaderStyle.Centred,
                        ) { +"Stops" }
                    }
                }
            }
            val bounds = onNodeWithText("Stops").fetchSemanticsNode().boundsInRoot
            centre = (bounds.left + bounds.right) / 2f
        }
        // Its padding is `md` at the start and `xs` at the end; around the bar
        // that moved the middle 4dp towards the close button.
        assertTrue(abs(centre - BarCentre) <= 1f, "the header's title is centred at ${centre}dp, the sheet at ${BarCentre}dp")
    }

    /**
     * A large header with nothing leading puts its title on the close button's
     * line, level with it, instead of on an empty row's worth of forehead below.
     */
    @Test
    fun aLargeSheetHeaderSharesTheControlsLineUnlessSomethingLeadsIt() {
        fun centres(leading: Boolean): Pair<Float, Float> {
            var title = -1f
            var close = -1f
            runComposeUiTest {
                setContent {
                    KontourTheme {
                        Box(Modifier.fillMaxSize().background(Color.White)) {
                            SheetHeader(
                                modifier = Modifier.width(BarWidth.dp),
                                onClose = {},
                                style = SheetHeaderStyle.Large,
                            ) {
                                +"Stops"
                                if (leading) leading { +"←" }
                            }
                        }
                    }
                }
                title = onNodeWithText("Stops").fetchSemanticsNode().boundsInRoot.center.y
                close = onNodeWithContentDescription("Close").fetchSemanticsNode().boundsInRoot.center.y
            }
            return title to close
        }
        val (alone, closeAlone) = centres(leading = false)
        assertTrue(abs(alone - closeAlone) <= 1f, "a large title with nothing leading sat at ${alone}, its close at ${closeAlone}")
        val (under, closeLed) = centres(leading = true)
        assertTrue(under > closeLed + 12f, "with something leading, the large title should sit under the controls")
    }

    /** Where the title starts, in dp, in a 400dp bar with a back button and one action. */
    private fun titleLeft(style: TopBarStyle, title: String): Float {
        var left = -1f
        runComposeUiTest {
            setContent {
                KontourTheme {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        TopBar(
                            modifier = Modifier.width(BarWidth.dp),
                            style = style,
                            onBack = {},
                            actions = {
                                IconButton(icon = Tabler.Outline.DotsVertical, contentDescription = "More", onClick = {})
                                IconButton(icon = Tabler.Outline.DotsVertical, contentDescription = "More 2", onClick = {})
                            },
                        ) {
                            +title
                        }
                    }
                }
            }
            left = onNodeWithText(title).fetchSemanticsNode().boundsInRoot.left
        }
        return left
    }

    /** The x of the centre of the title, in dp, in a 400dp bar. */
    private fun titleCentre(back: Boolean): Float {
        var centre = -1f
        runComposeUiTest {
            setContent {
                KontourTheme {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        TopBar(
                            modifier = Modifier.width(BarWidth.dp),
                            style = TopBarStyle.Centred,
                            onBack = if (back) ({}) else null,
                            actions = {
                                IconButton(
                                    icon = Tabler.Outline.DotsVertical,
                                    contentDescription = "More",
                                    onClick = {},
                                )
                            },
                        ) {
                            +Title
                        }
                    }
                }
            }
            val bounds = onNodeWithText(Title).fetchSemanticsNode().boundsInRoot
            centre = (bounds.left + bounds.right) / 2f
        }
        return centre
    }

    private companion object {
        const val Title = "Perth Underground"
        const val BarWidth = 400

        /** The middle of the bar. The harness runs at density 1, so dp is px. */
        const val BarCentre = BarWidth / 2f

        /**
         * Half a character. Tight enough that centring on the leftover space
         * fails it — a back button is 48dp, so that error is 24dp — and loose
         * enough to survive text measurement rounding.
         */
        const val Tolerance = 6f
    }
}
