package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.nav.TopBar
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A badged thing lines up with the unbadged things beside it.
 *
 * Reported as buttons in a top bar's actions going *out of alignment with
 * other buttons* when they have a badge: `BadgedBox` reported a size that
 * included half the badge, on the top and the trailing side, so a row that
 * centres its children centred the larger box, and the button in it sat half
 * a badge lower and further along than its neighbours.
 */
@OptIn(ExperimentalTestApi::class)
class BadgeAlignmentTest {

    private fun ComposeUiTest.bar(count: Int?): Triple<Rect, Rect, Rect> {
        setContent {
            KontourTheme(reduceMotion = true) {
                TopBar(
                    actions = {
                        IconButton(onClick = {}, icon = SystemIcons.Close, contentDescription = "Search")
                        BadgedBox(badge = { Badge(count = count) }) {
                            IconButton(onClick = {}, icon = SystemIcons.Close, contentDescription = "Filter")
                        }
                        IconButton(onClick = {}, icon = SystemIcons.Close, contentDescription = "More")
                    },
                ) { +"Chats" }
            }
        }
        waitForIdle()
        fun bounds(name: String) = onNodeWithContentDescription(name).fetchSemanticsNode().boundsInRoot
        return Triple(bounds("Search"), bounds("Filter"), bounds("More"))
    }

    private fun assertInLine(search: Rect, filter: Rect, more: Rect) {
        assertTrue(abs(filter.center.y - search.center.y) < 0.5f, "the badged button sits ${filter.center.y - search.center.y}px off its neighbours' line")
        assertTrue(abs(more.center.y - search.center.y) < 0.5f)
        val before = filter.left - search.right
        val after = more.left - filter.right
        assertTrue(abs(before - after) < 0.5f, "the badged button's gaps are uneven: $before before it, $after after")
    }

    @Test
    fun aDotBadgedActionIsInLineWithTheOthers() = runComposeUiTest {
        val (search, filter, more) = bar(count = null)
        assertInLine(search, filter, more)
    }

    @Test
    fun aCountBadgedActionIsInLineWithTheOthers() = runComposeUiTest {
        val (search, filter, more) = bar(count = 12)
        assertInLine(search, filter, more)
    }

    @Test
    fun aBadgedBoxIsTheSizeOfWhatItBadges() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                BadgedBox(badge = { Badge(count = 3) }, modifier = Modifier.testTag("badged")) {
                    Box(Modifier.size(40.dp).testTag("content"))
                }
            }
        }
        waitForIdle()
        val box = onNodeWithTag("badged").fetchSemanticsNode().boundsInRoot
        val content = onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        assertEquals(content.size, box.size, "the badge's overhang was added to the box's size")
        assertEquals(content.topLeft, box.topLeft)
    }
}
