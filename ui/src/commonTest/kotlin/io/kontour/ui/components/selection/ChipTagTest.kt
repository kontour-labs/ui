package io.kontour.ui.components.selection

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Tone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tag at the end of a chip: `FilterChip { +"Buses"; tag { +"3" } }`.
 *
 * Asked for as a tag as a chip's trailing content. What is held:
 *
 * - **One thing to a screen reader.** The chip reads "Buses, 3". A `Tag` placed
 *   in the chip by hand merges its own text into a node of its own, so the
 *   chip read "Buses" and the count was a second stop.
 * - **The chip's size.** A chip is 34dp with a tag in it, as without, and at
 *   200% type it grows to fit rather than clipping the tag.
 * - **Concentric.** The tag's round end is as far from the chip's end as from
 *   its top and bottom, in either reading direction.
 */
@OptIn(ExperimentalTestApi::class)
class ChipTagTest {

    private fun ComposeUiTest.chip(rtl: Boolean = false, fontScale: Float = 1f) {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                KontourTheme {
                    Box {
                        FilterChip(selected = true, onSelectedChange = {}, modifier = Modifier.testTag("chip")) {
                            +"Buses"
                            tag(Tone.Accent) { +"3" }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.bounds(tag: String): Rect =
        onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** The count's text, in its capsule: 6dp of padding either side, 2dp above and below. */
    private fun ComposeUiTest.tagCapsule(): Rect {
        val text = onNodeWithText("3", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val dp = density.density
        return Rect(text.left - 6 * dp, text.top - 2 * dp, text.right + 6 * dp, text.bottom + 2 * dp)
    }

    @Test
    fun theChipReadsItsLabelAndItsTagAsOne() = runComposeUiTest {
        chip()
        val node = onNodeWithTag("chip").fetchSemanticsNode()
        val texts = node.config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Buses", "3"), texts, "the chip and its tag are not one thing to a screen reader")
    }

    @Test
    fun aTagKeepsTheChipItsHeight() = runComposeUiTest {
        chip()
        val height = bounds("chip").height / density.density
        assertTrue(abs(height - 34f) <= 0.5f, "a chip with a tag is ${height}dp tall, not 34")
    }

    @Test
    fun atDoubleTypeTheChipGrowsAroundItsTag() = runComposeUiTest {
        chip(fontScale = 2f)
        val chip = bounds("chip")
        val tag = tagCapsule()
        assertTrue(chip.height / density.density > 34f, "at 200% type the chip stayed 34dp tall")
        assertTrue(
            tag.top >= chip.top - 0.5f && tag.bottom <= chip.bottom + 0.5f,
            "at 200% type the tag ($tag) overflows its chip ($chip)",
        )
    }

    @Test
    fun theTagSitsConcentricWithTheChipsEnd() = runComposeUiTest {
        chip()
        assertConcentric(bounds("chip"), tagCapsule(), rtl = false)
    }

    @Test
    fun rightToLeftTheTagSitsConcentricWithTheLeftEnd() = runComposeUiTest {
        chip(rtl = true)
        assertConcentric(bounds("chip"), tagCapsule(), rtl = true)
    }

    private fun ComposeUiTest.assertConcentric(chip: Rect, tag: Rect, rtl: Boolean) {
        val dp = density.density
        val vertical = ((chip.bottom - tag.bottom) + (tag.top - chip.top)) / 2f / dp
        val end = (if (rtl) tag.left - chip.left else chip.right - tag.right) / dp
        assertTrue(
            abs(end - vertical) <= 1f,
            "the tag is ${end}dp from the chip's end and ${vertical}dp from its top and bottom: not concentric",
        )
    }
}
