package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.kontourSizing
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * At twice the type size, in a phone's width, no specimen cuts its text off.
 *
 * accessibility.md says components "must lay out correctly at **200%**", with
 * no fixed heights on anything holding text and no single line assumed of a
 * label that can wrap. Nothing checked the half of that a reader sees. The
 * contract suite puts every specimen at 200% in a 320dp window and asks only
 * that it stays inside the window, which a label cut to half its height in a
 * row of a fixed height passes; `PseudoLocaleSweepTest` reads the text, at one
 * and a half times. Found in the November 2026 audit.
 *
 * So every specimen is drawn at 200% in the contract's 320dp window, with
 * Android's 48dp target, which is the platform where type grows tallest beside
 * the largest controls, and every text layout it exposes is read. A text fails
 * that is:
 *
 * - **cut at the top or bottom**: taller than the room it was given, which is a
 *   fixed height, or a line height too tight for the glyphs;
 * - **cut short with nothing to say so**: more lines than its `maxLines` under
 *   `TextOverflow.Clip`, the rest dropped;
 * - **cut mid-letter**: a word wider than its box under `TextOverflow.Clip`.
 *
 * An ellipsis is a choice, and it shows, so a label that ends in one at its
 * `maxLines` is left alone. So is a fade: an event's title and a document
 * tab's end in their last letters faded out, which says the same in no room
 * at all. A width cut is therefore read from the pixels as well, and counts
 * only where ink reaches the box's end edge; a fade erases its last pixels.
 */
@OptIn(ExperimentalTestApi::class)
class LargeTypeSweepTest {

    @Test
    fun noSpecimenCutsItsTextAtTwiceTheTypeSize() {
        val cut = componentRegistry.flatMap { spec ->
            cuts { spec.content(Modifier, true) {} }.map { "${spec.name}: $it" }
        }
        assertTrue(
            cut.isEmpty(),
            "${cut.size} text(s) cut off at 200% type in a 320dp window:\n" +
                cut.joinToString("\n") { "  · $it" } +
                "\nGive the text a minimum height rather than a height, let it wrap, or end it in an ellipsis",
        )
    }

    @Test
    fun aFixedHeightIsCaught() {
        // The controls: a label in a box its own height at 100% is cut at 200%,
        // and the same label given a minimum height instead is not.
        assertTrue(cuts { Box(Modifier.height(20.dp)) { Text("Departures") } }.isNotEmpty(), "the sweep cannot see a cut")
        assertTrue(cuts { Box(Modifier.heightIn(min = 20.dp)) { Text("Departures") } }.isEmpty(), "a minimum height is not a cut")
    }

    @Test
    fun aClippedLineIsCaughtAndAnEllipsisIsNot() {
        assertTrue(
            cuts { Box(Modifier.width(60.dp)) { Text("Fremantle", maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) } }.isNotEmpty(),
            "the sweep cannot see a word cut mid-letter",
        )
        assertTrue(
            cuts { Box(Modifier.width(60.dp)) { Text("Every 15 minutes until 11pm", maxLines = 1, overflow = TextOverflow.Clip) } }.isNotEmpty(),
            "the sweep cannot see lines dropped past maxLines",
        )
        assertTrue(
            cuts { Box(Modifier.width(60.dp)) { Text("Every 15 minutes until 11pm", maxLines = 1, overflow = TextOverflow.Ellipsis) } }.isEmpty(),
            "the sweep flags an ellipsis, which is a choice",
        )
    }

    /** What of [content]'s text is cut, at 200% type in the contract's narrow window. */
    private fun cuts(content: @Composable () -> Unit): List<String> {
        val found = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    KontourTheme(
                        reduceMotion = true,
                        sizing = kontourSizing(ContrastLevel.Standard).copy(minTouchTarget = 48.dp),
                    ) {
                        OverlayHost {
                            Box(Modifier.width(NarrowWindow)) { content() }
                        }
                    }
                }
            }
            waitForIdle()
            onAllNodes(hasTextLayout, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                    ?: layouts.firstOrNull()?.layoutInput?.text?.text.orEmpty()
                layouts.forEach { layout ->
                    val (cut, how) = howCut(layout) ?: return@forEach
                    if (cut == Cut.Width && !inkAtEnd(node.id, layout)) return@forEach
                    found += "\"$text\" $how"
                }
            }
        }
        return found
    }

    /** How [layout] is cut, and the words for it, or null if it is whole or ends in an ellipsis. */
    private fun howCut(layout: TextLayoutResult): Pair<Cut, String>? {
        val overflow = layout.layoutInput.overflow
        val pastMaxLines = layout.multiParagraph.didExceedMaxLines
        // Read from the lines rather than from `didOverflowWidth`, which
        // compares the box with the width the text was laid out in: a line
        // that may not wrap is laid out in an unbounded width, and was always
        // "wider" than any box, however short.
        val widest = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) - layout.getLineLeft(it) } ?: 0f
        return when {
            // Taller than the room it was given, whatever its overflow says:
            // an ellipsis marks the end of a line, not the top of one.
            layout.didOverflowHeight && !pastMaxLines && layout.multiParagraph.height > layout.size.height + Slack ->
                Cut.Height to "is ${layout.multiParagraph.height.toInt()}px tall in ${layout.size.height}px"
            // Before the lines: a line that may not wrap and is too wide for
            // its box is reported as past its one line as well.
            widest > layout.size.width + Slack && overflow == TextOverflow.Clip ->
                Cut.Width to "is ${widest.toInt()}px wide in ${layout.size.width}px, cut mid-letter"
            pastMaxLines && overflow == TextOverflow.Clip ->
                Cut.Lines to "drops what is past its ${layout.layoutInput.maxLines} line(s) with no ellipsis"
            else -> null
        }
    }

    /**
     * Whether the text of node [id] has ink at its end edge, where a line too
     * wide for its box is cut: some pixel of its last column apart from the
     * rest of that column, which is the ground behind it. A fade leaves the
     * column all ground.
     */
    private fun ComposeUiTest.inkAtEnd(id: Int, layout: TextLayoutResult): Boolean {
        val pixels = onNode(SemanticsMatcher("node $id") { it.id == id }, useUnmergedTree = true)
            .captureToImage()
            .toPixelMap()
        if (pixels.width == 0 || pixels.height == 0) return false
        val x = if (layout.layoutInput.layoutDirection == LayoutDirection.Rtl) 0 else pixels.width - 1
        val column = (0 until pixels.height).map { pixels[x, it].luminance() }
        val ground = column.sorted()[column.size / 2]
        return column.count { abs(it - ground) > InkContrast } >= InkPixels
    }

    private enum class Cut { Height, Lines, Width }

    private companion object {
        /** The contract's narrow window: a small phone in portrait, less its margins. */
        val NarrowWindow = 320.dp

        /**
         * A pixel: a size rounded to whole pixels against a text measured in
         * fractions of one, which is no cut a reader could see.
         */
        const val Slack = 1f

        /** How far from the ground, in luminance, a pixel has to be to be ink. */
        const val InkContrast = 0.2f

        /** How many pixels of ink at the edge make a cut rather than a stray pixel of a fade. */
        const val InkPixels = 2

        val hasTextLayout = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
    }
}
