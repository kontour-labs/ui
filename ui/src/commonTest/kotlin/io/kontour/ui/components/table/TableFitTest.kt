package io.kontour.ui.components.table

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A fitted column is wide enough for what the table holds, not only for its
 * first rows.
 *
 * Reported from an app: *"Table sizes a column from the rows on screen first,
 * so a date column cut off "30 Sep 2026" as it scrolled. I gave it a fixed
 * width."* The column was fitted once, from the first fifty rows, and kept that
 * width for good: a wider value further down was cut off, and new items — a page
 * loaded in, a refresh — never refitted it.
 */
@OptIn(ExperimentalTestApi::class)
class TableFitTest {

    private data class Trip(val id: Int, val date: String)

    private fun trips(dates: List<String>) = dates.mapIndexed { index, date -> Trip(index, date) }

    private fun ComposeUiTest.show(items: () -> List<Trip>) {
        setContent {
            KontourTheme(reduceMotion = true) {
                Box(Modifier.width(600.dp)) {
                    Table(items = items(), modifier = Modifier.height(400.dp), key = { it.id }) {
                        column("Date") { +it.date }
                        column("Fare", width = 80.dp) { +"3.20" }
                    }
                }
            }
        }
        waitForIdle()
    }

    /** How wide the fitted "Date" column is: from its heading to the next column's. */
    private fun ComposeUiTest.dateColumn(): Float {
        val date = onNode(hasText("Date") and isHeading()).fetchSemanticsNode().boundsInRoot.left
        val fare = onNode(hasText("Fare") and isHeading()).fetchSemanticsNode().boundsInRoot.left
        return fare - date
    }

    @Test
    fun aWideValuePastTheFirstRowsIsFitted() {
        val wide = "Wednesday, 30 September 2026"
        var needed = 0f
        runComposeUiTest {
            show { trips(listOf(wide)) }
            needed = dateColumn()
        }
        var fitted = 0f
        runComposeUiTest {
            show { trips(List(60) { "1 Oct" } + List(20) { wide }) }
            fitted = dateColumn()
        }
        assertTrue(
            fitted >= needed,
            "eighty rows whose last twenty are \"$wide\": the column is $fitted wide and that needs $needed",
        )
    }

    @Test
    fun newItemsWidenTheColumnAndNeverNarrowIt() = runComposeUiTest {
        var items by mutableStateOf(trips(List(5) { "1 Oct" }))
        show { items }
        val short = dateColumn()

        items = trips(List(5) { "Wednesday, 30 September 2026" })
        waitForIdle()
        val wide = dateColumn()
        assertTrue(wide > short, "wider items should widen the column: $short then $wide")

        items = trips(List(5) { "2 Oct" })
        waitForIdle()
        assertEquals(wide, dateColumn(), "narrower items should leave it as it was, so nothing jumps")
    }

    /**
     * The same items, scrolled through, never move a column — not even past a
     * wider value the sample missed, which is what `width` or `minWidth` is for.
     */
    @Test
    fun scrollingNeverChangesAColumn() = runComposeUiTest {
        val dates = List(300) { if (it == 150) "Wednesday, 30 September 2026" else "1 Oct" }
        assertTrue(150 !in tableFitSample(dates.size), "the wide row should be one the sample misses, or this proves nothing")
        show { trips(dates) }
        val before = dateColumn()
        for (index in listOf(150, 299, 0)) {
            onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(index)
            waitForIdle()
            assertEquals(before, dateColumn(), "scrolled to row $index")
        }
    }

    @Test
    fun theSampleTakesTheStartTheEndAndBetween() {
        assertContentEquals((0 until 30).toList(), tableFitSample(30), "a short table is measured whole")
        val sample = tableFitSample(1000)
        assertEquals(50, sample.size)
        assertEquals(sample.sorted(), sample, "in order")
        assertEquals(sample.distinct(), sample, "each row once")
        assertTrue((0 until 20).all { it in sample }, "the first twenty: what is on screen when it opens")
        assertTrue((990 until 1000).all { it in sample }, "the last ten: what a page loaded in or a newest-last list ends with")
        val between = sample.filter { it in 20 until 990 }
        assertEquals(20, between.size)
        assertTrue(between.zipWithNext().all { (a, b) -> b - a in 40..60 }, "spread evenly between: $between")
    }
}
