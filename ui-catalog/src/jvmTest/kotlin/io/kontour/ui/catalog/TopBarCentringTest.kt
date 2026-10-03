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
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.DotsVertical
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
     * A title that fits centred stays centred when the line under it does not:
     * the subtitle is fitted to the title's width and ends in an ellipsis. The
     * two used to be judged together, and a long subtitle pushed a short title to
     * the leading edge — asked from a phone to "try a bit harder" to centre.
     */
    @Test
    fun aLongSubtitleDoesNotStopTheTitleBeingCentred() {
        var title = -1f
        var subtitle = -1f
        var subtitleEnd = -1f
        var controlsStart = -1f
        runComposeUiTest {
            setContent {
                KontourTheme {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        SheetHeader(
                            modifier = Modifier.width(BarWidth.dp),
                            onClose = {},
                            actions = {
                                IconButton(icon = Tabler.Outline.DotsVertical, contentDescription = "More", onClick = {})
                            },
                            style = SheetHeaderStyle.Centred,
                        ) {
                            +"Stops"
                            supporting { +"Perth Busport, Elizabeth Quay and the Esplanade busway" }
                        }
                    }
                }
            }
            val t = onNodeWithText("Stops").fetchSemanticsNode().boundsInRoot
            val s = onNodeWithText("Perth Busport", substring = true).fetchSemanticsNode().boundsInRoot
            title = t.center.x
            subtitle = s.center.x
            subtitleEnd = s.right
            controlsStart = onNodeWithContentDescription("More").fetchSemanticsNode().boundsInRoot.left
        }
        assertTrue(abs(title - BarCentre) <= 1f, "the title is centred at ${title}dp, the sheet at ${BarCentre}dp")
        assertTrue(abs(subtitle - BarCentre) <= 1f, "the subtitle is centred at ${subtitle}dp, the sheet at ${BarCentre}dp")
        assertTrue(subtitleEnd <= controlsStart, "the subtitle ends at ${subtitleEnd}dp, under the controls from ${controlsStart}dp")
    }

    /**
     * A large header's title is on a line of its own under the controls, whatever
     * leads it. It shared the close button's line for a while when nothing led,
     * and was put back.
     */
    @Test
    fun aLargeSheetHeaderPutsItsTitleUnderTheControls() {
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
                        ) { +"Stops" }
                    }
                }
            }
            title = onNodeWithText("Stops").fetchSemanticsNode().boundsInRoot.center.y
            close = onNodeWithContentDescription("Close").fetchSemanticsNode().boundsInRoot.center.y
        }
        assertTrue(title > close + 12f, "a large title sat at ${title}, level with its close at ${close}")
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
