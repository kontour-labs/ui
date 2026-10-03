package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.locale.localeFormatsFor
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `listSection`: a grouped list's sections inside a `LazyColumn`, with the
 * positions, the gaps and the header worked out by the section rather than at
 * every call site.
 *
 * Asked for as *"a helper to the lazy list scope so we can easily do our nice
 * looking lists with sections and spacing and stuff? that will make things like
 * indexed/alphabetic lists look much nicer."* The gallery's A–Z of stops had
 * been built by hand, and its rows ran into each other: a lazy list has one
 * arrangement for every item, and nothing had put the hairline between rows.
 */
@OptIn(ExperimentalTestApi::class)
class LazyListSectionTest {

    private class Laid(val positions: Map<String, GroupPosition>, val spacing: Dp, val sectionGap: Dp)

    private fun ComposeUiTest.lay(
        height: Dp = 2000.dp,
        sections: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            listSection(listOf("a1", "a2", "a3"), key = { it }, title = { +"A" }) { row, position -> Row(row, position) }
            listSection(listOf("b1", "b2"), key = { it }, title = { +"B" }, footer = { +"Two of them" }) { row, position ->
                Row(row, position)
            }
        },
    ): Laid {
        var spacing = 0.dp
        var gap = 0.dp
        setContent {
            KontourTheme {
                spacing = ListItemDefaults.Spacing
                gap = Theme.spacing.md
                LazyColumn(Modifier.size(320.dp, height).testTag("list"), state = rememberLazyListState().also { listState = it }) {
                    sections()
                }
            }
        }
        waitForIdle()
        return Laid(positions.toMap(), spacing, gap)
    }

    private val positions = mutableStateMapOf<String, GroupPosition>()
    private var listState: LazyListState? = null

    @androidx.compose.runtime.Composable
    private fun Row(name: String, position: GroupPosition) {
        positions[name] = position
        Box(Modifier.fillMaxWidth().height(RowHeight).testTag(name))
    }

    private fun ComposeUiTest.top(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
    private fun ComposeUiTest.bottom(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.bottom

    private fun near(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) < 1f, "$what: expected $expected, was $actual")

    @Test
    fun eachSectionNumbersItsOwnRows() = runComposeUiTest {
        val laid = lay()
        assertEquals(GroupPosition.First, laid.positions["a1"])
        assertEquals(GroupPosition.Middle, laid.positions["a2"])
        assertEquals(GroupPosition.Last, laid.positions["a3"])
        assertEquals(GroupPosition.First, laid.positions["b1"])
        assertEquals(GroupPosition.Last, laid.positions["b2"])
    }

    @Test
    fun rowsInASectionAreAHairlineApart() = runComposeUiTest {
        val laid = lay()
        val hairline = with(density) { laid.spacing.toPx() }
        near(hairline, top("a2") - bottom("a1"), "the gap between two rows of a section")
        near(hairline, top("b2") - bottom("b1"), "the gap between two rows of the next section")
    }

    @Test
    fun theNextSectionStartsUnderItsHeadingWellClearOfTheLast() = runComposeUiTest {
        val laid = lay()
        val hairline = with(density) { laid.spacing.toPx() }
        val heading = onNodeWithText("B").fetchSemanticsNode()
        assertTrue(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading).matches(heading), "the title is not a heading")
        assertTrue(heading.boundsInRoot.top > bottom("a3"), "the heading is not between the sections")
        assertTrue(top("b1") > heading.boundsInRoot.bottom, "the rows are not under the heading")
        assertTrue(
            top("b1") - bottom("a3") > hairline * 4,
            "two sections ran together: ${top("b1") - bottom("a3")}px between them",
        )
        // The footer, under its section's rows.
        assertTrue(onNodeWithText("Two of them").fetchSemanticsNode().boundsInRoot.top > bottom("b2"))
    }

    @Test
    fun sectionsWithoutTitlesStillStandApart() = runComposeUiTest {
        val laid = lay(sections = {
            listSection(listOf("a1", "a2")) { row, position -> Row(row, position) }
            listSection(listOf("b1", "b2")) { row, position -> Row(row, position) }
        })
        val gap = with(density) { laid.sectionGap.toPx() }
        near(gap, top("b1") - bottom("a2"), "the space between two untitled sections")
        assertEquals(GroupPosition.First, laid.positions["b1"], "the second untitled section ran on from the first")
    }

    @Test
    fun anIndexScrollersSectionsLandOnTheHeadings() = runComposeUiTest {
        val words = listOf(
            "Armadale", "Ashfield", "Bassendean", "Bayswater", "Claremont", "Cottesloe",
            "Daglish", "Dalkeith", "Darlington", "Doubleview",
        )
        val formats = localeFormatsFor(Locale("en-AU"))
        val indexed = words.indexedBy(IndexAlphabet.Latin, formats) { it }
        lay(height = 160.dp, sections = {
            indexed.groups.forEach { group ->
                listSection(group.items, key = { it }, title = { +group.label }) { row, position -> Row(row, position) }
            }
        })
        val list = listState!!
        for (section in indexed.sections) {
            runOnUiThread { runBlocking { list.scrollToItem(section.itemIndex) } }
            waitForIdle()
            val heading = onNodeWithText(section.label).fetchSemanticsNode().boundsInRoot
            near(onNodeWithTag("list").fetchSemanticsNode().boundsInRoot.top, heading.top - headingTopInset(), "${section.label}'s heading at the top")
        }
    }

    /** A `SectionHeader`'s text sits its top padding below the item's top. */
    private fun ComposeUiTest.headingTopInset(): Float = with(density) { 16.dp.toPx() }

    @Test
    fun anEmptySectionDrawsNothing() = runComposeUiTest {
        lay(sections = {
            listSection(emptyList<String>(), title = { +"Nothing" }, footer = { +"Nothing here" }) { row, position -> Row(row, position) }
            listSection(listOf("a1")) { row, position -> Row(row, position) }
        })
        onNodeWithText("Nothing").assertDoesNotExist()
        onNodeWithText("Nothing here").assertDoesNotExist()
        onNodeWithTag("a1").assertExists()
    }

    private companion object {
        val RowHeight = 48.dp
    }
}
