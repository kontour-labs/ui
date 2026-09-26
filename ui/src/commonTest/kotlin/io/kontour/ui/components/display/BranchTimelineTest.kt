package io.kontour.ui.components.display

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.ContrastThreshold
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A branching history's rows, and the colours its lanes are drawn in. */
@OptIn(ExperimentalTestApi::class)
class BranchTimelineTest {

    private val history = listOf(
        "m" to listOf("b", "f"),
        "f" to listOf("a"),
        "b" to listOf("a"),
        "a" to emptyList(),
    )

    @Test
    fun aRowTappedHandsBackItsItem() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent {
            KontourTheme {
                BranchTimeline(history, key = { it.first }, parents = { it.second }) { commit ->
                    item(onClick = { opened += commit.first }) { +"Commit ${commit.first}" }
                }
            }
        }
        onNodeWithText("Commit f").performClick()
        onNodeWithText("Commit a").performClick()
        assertEquals(listOf("f", "a"), opened)
    }

    @Test
    fun aDisabledHistoryDisablesItsRows() = runComposeUiTest {
        setContent {
            KontourTheme {
                BranchTimeline(history, key = { it.first }, parents = { it.second }, enabled = false) { commit ->
                    item(onClick = {}) { +"Commit ${commit.first}" }
                }
            }
        }
        onNodeWithText("Commit m").assertIsNotEnabled()
    }

    /** A merge is a ring unless its row says otherwise; any other commit is solid. */
    @Test
    fun aMergeIsARingUnlessItSaysOtherwise() {
        val stops = history.map { (_, parents) ->
            BranchTimelineScope(merge = parents.size > 1).apply { item { } }.stops.single()
        }
        assertEquals(listOf(false, true, true, true), stops.map { it.filled })
        val solid = BranchTimelineScope(merge = true).apply { item(filled = true) { } }.stops.single()
        assertTrue(solid.filled)
    }

    /**
     * A commit's connector styles its lines down to its parents, in every row they
     * pass through — and not its children's lines into it.
     */
    @Test
    fun aCommitsConnectorAppliesToItsLinesDownToItsParents() {
        val shape = BranchShape.of(history, { it.first }, { it.second }, progress = null)
        val stops = history.map { (id, _) ->
            BranchTimelineScope(merge = false).apply {
                if (id == "f") item(connector = ConnectorStyle.Dotted, connectorColour = Color.Magenta) { } else item { }
            }.stops.single()
        }
        val colours = BranchTimelineColours(lanes = listOf(Color.Red, Color.Green), muted = Color.Gray)
        val rails = history.indices.map { branchRail(shape, it, stops, colours, 2.dp) }
        // f is row 1, in lane 1; its line leaves it for a, passing b in row 2.
        val fOut = rails[1].legs.single { it.start.at == LegAt.Node && it.end.at == LegAt.End }
        assertEquals(ConnectorStyle.Dotted, fOut.style)
        assertEquals(Color.Magenta, fOut.colour)
        val passingB = rails[2].legs.single { it.start.at == LegAt.Start && it.start.lane == 1 }
        assertEquals(ConnectorStyle.Dotted, passingB.style, "still f's line as it passes b")
        val intoF = rails[1].legs.filter { it.end.at == LegAt.Start }
        assertTrue(intoF.all { it.style == ConnectorStyle.Solid }, "the merge's line into f is the merge's: $intoF")
    }

    /**
     * Progress: the commit reached pulses, what it descends from keeps its
     * colours, and the rest is muted — with a band on the way up to [towards].
     */
    @Test
    fun progressMutesWhatItHasNotReached() {
        val shape = BranchShape.of(history, { it.first }, { it.second }, BranchProgress(reached = "f", towards = "m"))
        val stops = history.map { BranchTimelineScope(merge = false).apply { item { } }.stops.single() }
        val colours = BranchTimelineColours(lanes = listOf(Color.Red, Color.Green), muted = Color.Gray)
        val rails = history.indices.map { branchRail(shape, it, stops, colours, 2.dp) }
        assertEquals(listOf(false, true, false, false), rails.map { it.node!!.here })
        assertEquals(Color.Gray, rails[0].node!!.colour, "the merge is not reached")
        assertEquals(Color.Gray, rails[2].node!!.colour, "nor is b, on the other side of it")
        assertEquals(Color.Green, rails[1].node!!.colour, "f keeps its lane's colour")
        assertEquals(Color.Red, rails[3].node!!.colour, "and so does a, which f descends from")
        val band = rails[0].legs.filter { it.travel?.band == true }
        assertEquals(1, band.size, "the merge's line down to f carries the band: ${rails[0].legs}")
        assertEquals(1f, band.single().travel!!.from, "starting at the merge's node, the far end of the line")
    }

    /**
     * A lane is a 2dp line against the page, which is the non-text threshold's
     * case: every colour a lane can default to has to clear it, in both schemes.
     */
    @Test
    fun everyLaneColourStandsOffThePage() {
        for (dark in listOf(false, true)) {
            var palette = emptyList<Color>()
            var page = Color.Unspecified
            runComposeUiTest {
                setContent {
                    KontourTheme(darkTheme = dark) {
                        palette = BranchTimelineDefaults.palette()
                        page = Theme.colours.background
                    }
                }
            }
            assertEquals(6, palette.size, "the default palette was never read")
            palette.forEachIndexed { turn, colour ->
                val ratio = contrastRatio(colour, page)
                assertTrue(
                    ratio >= ContrastThreshold.NonText,
                    "lane colour $turn is $ratio:1 against the ${if (dark) "dark" else "light"} page",
                )
            }
        }
    }
}
