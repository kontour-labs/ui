package io.kontour.ui.dragdrop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A screen reader is offered a move to every target there is, whenever it
 * attached.
 *
 * Found building a board whose columns are the items of a `LazyRow`: each
 * column's list attaches as the row lays it out, after the columns before it
 * have worked out their cards' moves — and those were never worked out again,
 * so a card in the first column could be moved to the archive but to no other
 * column, and one in the second only back to the first.
 */
@OptIn(ExperimentalTestApi::class)
class DragMovesOfferedTest {

    @Test
    fun aCardIsOfferedEveryColumnHoweverLateItAttached() = runComposeUiTest {
        setContent {
            KontourTheme {
                OverlayHost {
                    val lanes = remember { listOf("To do" to listOf("Timetable"), "Doing" to listOf("Fares"), "Done" to listOf("Maps")) }
                    LazyRow(Modifier.fillMaxSize()) {
                        items(lanes, key = { it.first }) { (title, initial) ->
                            val cards = remember { mutableStateListOf<String>().apply { addAll(initial) } }
                            val scroll = rememberLazyListState()
                            val lane = rememberDropListState(
                                listState = scroll,
                                type = Card,
                                items = cards,
                                key = { it },
                                label = title,
                                itemLabel = { it },
                                onDrop = {},
                            )
                            LazyColumn(state = scroll, modifier = Modifier.width(120.dp).fillMaxHeight().dropList(lane)) {
                                dragItems(lane) { _, card -> Box(Modifier.height(48.dp)) { Text(card) } }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(listOf("Move to Doing", "Move to Done"), movesOf("Timetable"))
        assertEquals(listOf("Move to To do", "Move to Done"), movesOf("Fares"))
        assertEquals(listOf("Move to To do", "Move to Doing"), movesOf("Maps"))
    }

    /** The moves to other targets offered for [card]: none at all, where nothing offers any. */
    private fun ComposeUiTest.movesOf(card: String): List<String> =
        onAllNodes(hasMoves() and holds(card)).fetchSemanticsNodes().lastOrNull()
            ?.config?.getOrNull(SemanticsActions.CustomActions).orEmpty()
            .map { it.label }.filter { it.startsWith("Move to") }

    private fun hasMoves() = SemanticsMatcher("offers moves") { it.config.getOrNull(SemanticsActions.CustomActions)?.isNotEmpty() == true }

    private fun holds(card: String) = SemanticsMatcher("is $card") { node ->
        fun says(n: SemanticsNode): Boolean =
            n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == card } == true || n.children.any(::says)
        says(node)
    }

    private companion object {
        val Card = DragType<String>("card")
    }
}
