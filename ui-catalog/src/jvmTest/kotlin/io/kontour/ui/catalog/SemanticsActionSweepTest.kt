package io.kontour.ui.catalog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every action a screen reader can reach, performed.
 *
 * `EverythingRespondsTest` presses everything with an `OnClick`, which is the
 * half of the accessibility surface a pointer also reaches. This is the other
 * half: custom actions, `setProgress`, scroll and paging actions, expand and
 * collapse, dismiss. Nothing else in the suite invokes them, and a pointer
 * cannot — that is the entire point of them existing.
 *
 * ### Why this is worth a file
 *
 * Round 23 found `Slider` and `RangeSlider` crashing on an inverted
 * `valueRange`, and the crash was in `setProgress` and nowhere else. The
 * visible control clamped correctly; the drag path never reached the failing
 * line. So an inverted range drew an ordinary slider, passed every screenshot,
 * satisfied every gesture test, and took the frame down the first time somebody
 * moved it with a screen reader or an automated accessibility check.
 *
 * That is not a slider bug so much as a shape: an action reachable only by
 * assistive technology is an action nothing in a normal suite executes, so it
 * accumulates whatever the rest of the component has grown past. Sweeping them
 * is the only way to find the next one.
 *
 * ### What it asserts
 *
 * That no action throws, and that a floor of them was found at all. The second
 * matters as much: a sweep that silently stops finding anything passes forever,
 * and this one walks a semantics tree whose shape is not its own.
 *
 * Not asserted: that an action *did* anything. `EverythingRespondsTest` makes
 * that argument for clicks and pays for it with an echo local and a
 * re-render-per-suspect; the value here is the execution, because these paths
 * are otherwise never executed at all.
 *
 * ### The whole surface, and how small it is
 *
 * The library declares an action beyond `onClick` in exactly six places:
 *
 * | where | action |
 * |---|---|
 * | `Slider`, `RangeSlider` | `setProgress` |
 * | `PaneScaffold` | `setProgress` |
 * | `Carousel` | `customActions` — previous, next |
 * | `SwipeActions` | `customActions` — one per action |
 * | `Reorderable` | `customActions` — move up, move down |
 *
 * So six of the eleven showcases below sweep nothing, and that is the right
 * answer rather than a hole in the walk: it was checked against the source
 * after the first run reported zero on most pages. The per-page floors are set
 * from what is actually there.
 *
 * Text editing is deliberately out. `TextField` publishes `setText`,
 * `setSelection` and the clipboard actions, but it publishes them through
 * `BasicTextField` — sweeping them would be testing Compose rather than this
 * library, and a failure would be somebody else's to fix.
 */
@OptIn(ExperimentalTestApi::class)
class SemanticsActionSweepTest {

    @Test fun actionsSurviveTheirActions() = sweep("actions", expected = 0) { ButtonShowcase() }

    @Test fun selectionSurvivesItsActions() = sweep("selection", expected = 12) { SelectionShowcase() }

    @Test fun textSurvivesItsActions() = sweep("text", expected = 0) { TextShowcase() }

    @Test fun dateAndTimeSurviveTheirActions() = sweep("datetime", expected = 0) { DateTimeShowcase() }

    @Test fun overlaysSurviveTheirActions() = sweep("overlays", expected = 0) { OverlayShowcase() }

    @Test fun formsSurviveTheirActions() = sweep("forms", expected = 0) { SelectShowcase() }

    @Test fun sheetsSurviveTheirActions() = sweep("sheets", expected = 4) { SheetShowcase() }

    @Test fun listsSurviveTheirActions() = sweep("lists", expected = 2) { ListShowcase() }

    @Test fun navigationSurvivesItsActions() = sweep("nav", expected = 0) { NavShowcase() }

    @Test fun adaptiveLayoutsSurviveTheirActions() = sweep("adaptive", expected = 3) { AdaptiveShowcase() }

    @Test fun displaySurvivesItsActions() = sweep("display", expected = 2) { DisplayShowcase() }

    /** One action to perform, and enough about it to name it in a failure. */
    private class Reachable(val nodeId: Int, val what: String, val perform: (SemanticsNode) -> Unit)

    private fun sweep(showcase: String, expected: Int, content: @Composable () -> Unit) {
        val failures = mutableListOf<String>()
        var performed = 0

        runDesktopComposeUiTest(width = Canvas, height = Canvas) {
            // Hand-driven for the same reason `EverythingRespondsTest` is: the
            // overlays page never stops asking for frames, and an auto-advancing
            // clock chases it forever.
            mainClock.autoAdvance = false
            setContent { Harness(content) }
            settle()

            // Collected before any of them runs. An action changes state, so the
            // tree is different afterwards — gathering as we go would perform
            // whatever the previous action happened to create and miss whatever
            // it destroyed, and neither is the sweep this is meant to be.
            val planned = onAllNodes(everything, useUnmergedTree = true).fetchSemanticsNodes().flatMap { reachableOn(it) }

            for (action in planned) {
                // Re-fetched by id rather than held: the node this was planned
                // from may have left the tree, which is not a failure — a menu
                // closing takes its items with it.
                val live = onAllNodes(everything, useUnmergedTree = true).fetchSemanticsNodes()
                    .firstOrNull { it.id == action.nodeId } ?: continue
                try {
                    action.perform(live)
                    performed++
                } catch (error: Throwable) {
                    failures += "${action.what} threw ${error::class.simpleName}: " +
                        error.message?.lineSequence()?.firstOrNull()
                }
                settle()
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} of $performed accessibility actions on the $showcase " +
                    "showcase failed:\n" + failures.joinToString("\n") { "  · $it" },
            )
        }
        assertTrue(
            performed >= expected,
            "only $performed accessibility actions were found on the $showcase showcase, " +
                "where there should be at least $expected. A sweep that stops finding things " +
                "passes forever, so this fails rather than shrinking quietly. Either a " +
                "component lost an action it used to publish, or the semantics tree changed " +
                "shape under the walk.",
        )
    }

    /** Every action on one node, as something that can be performed later. */
    private fun reachableOn(node: SemanticsNode): List<Reachable> {
        val found = mutableListOf<Reachable>()
        val where = node.describe()

        node.config.getOrNull(SemanticsActions.CustomActions)?.forEachIndexed { index, custom ->
            found += Reachable(node.id, "custom action \"${custom.label}\" on $where") { live ->
                live.config.getOrNull(SemanticsActions.CustomActions)
                    ?.getOrNull(index)?.action?.invoke()
            }
        }

        // The values assistive technology actually sends: the ends of the range
        // the component published, and the middle. Not out-of-range ones — a
        // screen reader reads `ProgressBarRangeInfo` and stays inside it, and a
        // sweep that invents impossible input finds bugs nobody can reach.
        if (node.config.contains(SemanticsActions.SetProgress)) {
            val range = node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
                ?.range ?: 0f..1f
            val middle = range.start + (range.endInclusive - range.start) / 2f
            for (value in listOf(range.start, middle, range.endInclusive)) {
                found += Reachable(node.id, "setProgress($value) on $where") { live ->
                    live.config.getOrNull(SemanticsActions.SetProgress)?.action?.invoke(value)
                }
            }
        }

        for (paging in listOf(
            "pageUp" to SemanticsActions.PageUp,
            "pageDown" to SemanticsActions.PageDown,
            "pageLeft" to SemanticsActions.PageLeft,
            "pageRight" to SemanticsActions.PageRight,
            "expand" to SemanticsActions.Expand,
            "collapse" to SemanticsActions.Collapse,
            "dismiss" to SemanticsActions.Dismiss,
        )) {
            val (name, key) = paging
            if (node.config.contains(key)) {
                found += Reachable(node.id, "$name on $where") { live ->
                    live.config.getOrNull(key)?.action?.invoke()
                }
            }
        }

        return found
    }

    private val everything = SemanticsMatcher("any node") { true }

    /** The same surface `EverythingRespondsTest` uses, and for the same reason. */
    private val Canvas = 1200
    private val CanvasDensity = 0.5f

    private fun ComposeUiTest.settle() {
        repeat(30) { mainClock.advanceTimeByFrame() }
        waitForIdle()
    }

    private fun SemanticsNode.describe(): String {
        val described = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ")
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(", ") { it.text }
        val role = config.getOrNull(SemanticsProperties.Role)?.toString()
        return listOfNotNull(described, text, role).joinToString(" · ").ifEmpty { "unlabelled" }
    }

    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalDensity provides Density(CanvasDensity, fontScale = 1f),
            LocalCatalogEcho provides {},
        ) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}
