package io.kontour.ui.components.display

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
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
                BranchTimeline(history, id = { it.first }, parents = { it.second }, onItemClick = { opened += it.first }) {
                    +"Commit ${it.first}"
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
                BranchTimeline(history, id = { it.first }, parents = { it.second }, enabled = false, onItemClick = {}) {
                    +"Commit ${it.first}"
                }
            }
        }
        onNodeWithText("Commit m").assertIsNotEnabled()
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
                    ratio >= ContrastThreshold.NON_TEXT,
                    "lane colour $turn is $ratio:1 against the ${if (dark) "dark" else "light"} page",
                )
            }
        }
    }
}
