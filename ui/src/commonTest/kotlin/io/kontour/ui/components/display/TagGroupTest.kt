package io.kontour.ui.components.display

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Tone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A tag group joins its parts into one tag, or stands them together as a
 * group, and reads as one thing or as several by what its parts are.
 *
 * Asked for for 0.5.3 as "multi-tags": tags grouped either by a straight or by
 * a diagonal line.
 */
@OptIn(ExperimentalTestApi::class)
class TagGroupTest {

    @Test
    fun joinedStraightThePartsTouchAndAreOneHeight() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                TagGroup(Modifier.testTag("group")) {
                    item(tone = Tone.Success) { +"T1" }
                    item(tone = Tone.Info, contentDescription = null) { +"Fremantle" }
                }
            }
        }
        val parts = partBounds()
        assertEquals(2, parts.size)
        assertEquals(parts[0].right, parts[1].left, 0.5f, "joined parts left a gap, or overlapped")
        assertEquals(parts[0].height, parts[1].height, 0.5f, "the parts were different heights")
        val group = onNodeWithTag("group").fetchSemanticsNode().boundsInRoot
        assertEquals(parts[1].right - parts[0].left, group.width, 0.5f)
    }

    @Test
    fun joinedOnASlantEachPartStartsUnderTheOneBefore() = runComposeUiTest {
        var slant = 0f
        setContent {
            KontourTheme(reduceMotion = true) {
                slant = with(androidx.compose.ui.platform.LocalDensity.current) { TagGroupDefaults.Slant.roundToPx().toFloat() }
                TagGroup(join = TagJoin.Diagonal) {
                    item(tone = Tone.Success) { +"T1" }
                    item(tone = Tone.Info) { +"Fremantle" }
                }
            }
        }
        val parts = partBounds()
        // By the slant, and a pixel more, so the earlier part's colour is under
        // the later one's softened edge rather than the page.
        assertEquals(slant + 1f, parts[0].right - parts[1].left, 0.5f)
    }

    @Test
    fun joinedToAPointEachPartStartsUnderTheOneBeforeEitherWay() = runComposeUiTest {
        var reach = 0f
        var flow by androidx.compose.runtime.mutableStateOf(TagFlow.Forward)
        setContent {
            KontourTheme(reduceMotion = true) {
                reach = with(androidx.compose.ui.platform.LocalDensity.current) { TagGroupDefaults.Slant.roundToPx().toFloat() }
                TagGroup(join = TagJoin.Flow, flow = flow) {
                    item(tone = Tone.Success) { +"T1" }
                    item(tone = Tone.Info) { +"Fremantle" }
                }
            }
        }
        // The point sits in the notch, so the parts overlap by its reach, and
        // the pixel more that a slant's parts do, which way ever it points.
        assertEquals(reach + 1f, partBounds().let { it[0].right - it[1].left }, 0.5f)
        flow = TagFlow.Backward
        waitForIdle()
        assertEquals(reach + 1f, partBounds().let { it[0].right - it[1].left }, 0.5f)
    }

    @Test
    fun partsOfOneColourKeepASeamEvenJoined() = runComposeUiTest {
        var seam = 0f
        setContent {
            KontourTheme(reduceMotion = true) {
                seam = with(androidx.compose.ui.platform.LocalDensity.current) { TagGroupDefaults.Seam.roundToPx().toFloat() }
                TagGroup {
                    item { +"v0.5.3" }
                    item { +"stable" }
                }
            }
        }
        val parts = partBounds()
        assertEquals(seam, parts[1].left - parts[0].right, 0.5f, "two grey parts ran together into one tag")
    }

    @Test
    fun spacedThePartsStandApart() = runComposeUiTest {
        var gap = 0f
        setContent {
            KontourTheme(reduceMotion = true) {
                gap = with(androidx.compose.ui.platform.LocalDensity.current) { TagGroupDefaults.Gap.roundToPx().toFloat() }
                TagGroup(spacing = TagGroupDefaults.Gap) {
                    item(tone = Tone.Accent) { +"Bus" }
                    item { +"Route 960" }
                }
            }
        }
        val parts = partBounds()
        assertEquals(gap, parts[1].left - parts[0].right, 0.5f)
    }

    @Test
    fun rightToLeftTheFirstPartIsOnTheRight() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    TagGroup(join = TagJoin.Diagonal) {
                        item(tone = Tone.Success) { +"T1" }
                        item(tone = Tone.Info) { +"Fremantle" }
                    }
                }
            }
        }
        val first = onNodeWithText("T1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val second = onNodeWithText("Fremantle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(first.left > second.right, "the first part was not at the start of a right-to-left line")
    }

    @Test
    fun aGroupOfLabelsIsOneAnnouncement() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                TagGroup(Modifier.testTag("group")) {
                    item(containerColour = Color(0xFF1B5E20)) { +"T1" }
                    item { +"Fremantle" }
                }
            }
        }
        val node = onNodeWithTag("group").fetchSemanticsNode()
        val said = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }
        assertEquals(listOf("T1", "Fremantle"), said, "the group did not read as one tag, in order")
        assertNull(node.config.getOrNull(SemanticsActions.OnClick))
    }

    @Test
    fun aPartThatLeadsSomewhereIsItsOwnButton() = runComposeUiTest {
        var opened = 0
        setContent {
            KontourTheme(reduceMotion = true) {
                TagGroup(Modifier.testTag("group")) {
                    item(onClick = { opened++ }) { +"960" }
                    item(tone = Tone.Warning) { +"Delayed" }
                }
            }
        }
        val button = onNodeWithText("960").fetchSemanticsNode()
        assertEquals(Role.Button, button.config.getOrNull(SemanticsProperties.Role))
        // The label beside it is not swallowed into the button's announcement.
        val said = button.config.getOrNull(SemanticsProperties.Text)?.map { it.text }
        assertEquals(listOf("960"), said)
        onNodeWithText("960").performClick()
        waitForIdle()
        assertEquals(1, opened)
        // And the group lets go of neither: it is not one merged node.
        val group = onNodeWithTag("group").fetchSemanticsNode()
        assertNull(group.config.getOrNull(SemanticsProperties.Text))
    }

    @Test
    fun aDisabledGroupDisablesItsPressableParts() = runComposeUiTest {
        var opened = 0
        setContent {
            KontourTheme(reduceMotion = true) {
                TagGroup(enabled = false) {
                    item(onClick = { opened++ }) { +"960" }
                    item { +"Mirrabooka" }
                }
            }
        }
        val node = onNodeWithText("960").fetchSemanticsNode()
        assertTrue(node.config.contains(SemanticsProperties.Disabled))
        onNodeWithText("960").performClick()
        waitForIdle()
        assertEquals(0, opened)
    }

    /** Each part's bounds, in order: the parts are the group's children. */
    private fun androidx.compose.ui.test.ComposeUiTest.partBounds(): List<Rect> =
        listOf("T1", "Fremantle", "v0.5.3", "stable", "Bus", "Route 960")
            .mapNotNull { text ->
                runCatching { onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode() }.getOrNull()
            }
            // The text sits inside the part's padding; the part is the
            // nearest ancestor wider than the text.
            .map { text -> generateSequence(text.parent) { it.parent }.first { it.boundsInRoot.width > text.boundsInRoot.width + 1f }.boundsInRoot }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float, message: String? = null) =
        assertTrue(abs(expected - actual) <= tolerance, "${message ?: "expected $expected"}: expected $expected, was $actual")
}
