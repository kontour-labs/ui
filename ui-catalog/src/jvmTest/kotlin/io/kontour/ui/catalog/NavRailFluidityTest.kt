package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bell
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.Home
import io.kontour.icons.tabler.outline.Route
import io.kontour.icons.tabler.outline.Star
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.LocalNavExpansion
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavScope
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A rail growing into a sidebar is one movement, not a width animation with
 * things switching on and off at its halfway frame.
 *
 * Reported from a phone as "the section name animation is not fluid". What
 * happened at the halfway point of every expansion, all in one frame: each
 * section's hairline became its title; every label arrived — the first word of
 * a two-word one, the second a few frames later when it fitted; and an open
 * group's destinations started folding away on a clock of their own. And every
 * frame of it recomposed the rail and every slot that asked how much room it had.
 */
@OptIn(ExperimentalTestApi::class)
class NavRailFluidityTest {

    /**
     * A slot that asks how much room it has is composed when the answer changes,
     * not on every frame of the width getting there.
     */
    @Test
    fun aSlotReadingTheRoomIsNotRecomposedEveryFrame() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var compositions = 0
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                Box(Modifier.fillMaxSize()) {
                    NavRail(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.testTag("rail"),
                        header = {
                            val room = LocalNavExpansion.current
                            SideEffect { compositions++ }
                            if (room.expanded) Text("Transperth") else Text("T")
                        },
                    ) { items() }
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val before = compositions
        val narrow = onNodeWithTag("rail").fetchSemanticsNode().boundsInRoot.width

        expanded = true
        var frames = 0
        var last = narrow
        while (frames < 120) {
            mainClock.advanceTimeByFrame()
            frames++
            val now = onNodeWithTag("rail").fetchSemanticsNode().boundsInRoot.width
            if (frames > 5 && now == last) break
            last = now
        }
        mainClock.autoAdvance = true
        waitForIdle()

        assertTrue(frames >= 10, "the rail grew in $frames frames, too few to tell a frame from a change")
        val during = compositions - before
        assertTrue(
            during <= 3,
            "a header that reads LocalNavExpansion was composed $during times while the rail grew over " +
                "$frames frames — once for the toggle and once where labels fit is all it needs",
        )
    }

    /**
     * A section's title and the hairline standing in for it are both there
     * while the rail grows, one fading as the other arrives — never a swap in a
     * frame, and never a moment with neither.
     */
    @Test
    fun aSectionTitleFadesInOverItsHairline() {
        var expanded by mutableStateOf(false)
        var section = Rect.Zero
        val hairline = mutableListOf<Float>()
        val title = mutableListOf<Float>()
        Scene(width = 800, height = 700) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavRail(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    colours = NavDefaults.colours(indicator = Color.Transparent),
                ) {
                    item(true, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
                    section("Saved", Modifier.reportBounds { section = it }) {
                        item(false, {}, key = "stops", icon = Tabler.Outline.Star) { +"Stops" }
                    }
                }
            }
        }.use { scene ->
            val rest = scene.frames(8)
            val collapsedLine = rest.hairlineInk(section)
            assertTrue(collapsedLine > 0.1f, "a collapsed rail's section drew no hairline to fade from")
            expanded = true
            repeat(70) {
                val image = scene.frame()
                hairline += image.hairlineInk(section) / collapsedLine
                title += image.titleInk(section)
            }
        }
        val finalTitle = title.last()
        assertTrue(finalTitle > 0.1f, "the expanded rail drew no section title")
        val crossing = hairline.indices.filter { hairline[it] in 0.15f..0.85f && title[it] / finalTitle in 0.1f..0.9f }
        assertTrue(
            crossing.size >= 2,
            "no frame had the hairline and the title both part-drawn — the one replaced the other. " +
                "Hairline by frame: ${hairline.fmt()}; title: ${title.map { it / finalTitle }.fmt()}",
        )
        val blank = hairline.indices.filter { hairline[it] < 0.1f && title[it] / finalTitle < 0.1f }
        assertTrue(blank.isEmpty(), "on frames $blank the section drew neither its hairline nor its title")
    }

    /**
     * A label is one line at every width: cut short with an ellipsis while the
     * rail is too narrow for it, never broken at a word.
     *
     * Asked of the layout's input as well as its output, because the output
     * alone cannot fail here: Skia keeps a one-line label whole when it
     * overflows, soft wrap or not, where Android breaks it at the last word
     * that fits and hides the rest — which is what the phone showed, a
     * two-word label arriving a word at a time. So the check is that nothing
     * asks for a soft wrap, and that what is laid out is the whole label.
     */
    @Test
    fun aLabelNeverWrapsWhileTheRailGrows() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        val label = "Service alerts"
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                Box(Modifier.fillMaxSize()) {
                    NavRail(expanded = expanded, onExpandedChange = { expanded = it }) {
                        item(true, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
                        item(false, {}, key = "alerts", icon = Tabler.Outline.Bell) { +label }
                    }
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        expanded = true
        var measured = 0
        var squeezed = 0
        repeat(80) {
            mainClock.advanceTimeByFrame()
            val nodes = onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes()
            val node = nodes.firstOrNull() ?: return@repeat
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layout = results.singleOrNull() ?: return@repeat
            measured++
            if (layout.didOverflowWidth || layout.hasVisualOverflow) squeezed++
            assertTrue(
                !layout.layoutInput.softWrap,
                "the label is laid out to wrap at a word when it runs out of room",
            )
            assertTrue(
                layout.lineCount == 1 && layout.getLineEnd(0) == label.length,
                "at ${layout.size.width}px the label was laid out as ${layout.lineCount} lines, the first " +
                    "ending at ${layout.getLineEnd(0)} of ${label.length} — broken at a word",
            )
        }
        mainClock.autoAdvance = true
        assertTrue(measured > 0, "the label never appeared")
        assertTrue(
            squeezed > 0,
            "the label always had room, over $measured frames, so this cannot tell wrapping from not",
        )
    }

    /**
     * An open group's destinations close up as the rail narrows, following its
     * width — not all at once when it passes halfway.
     */
    @Test
    fun anOpenGroupFoldsWithTheRail() = runComposeUiTest {
        var expanded by mutableStateOf(true)
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                OverlayHost(Modifier.fillMaxSize()) {
                    NavRail(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.testTag("rail"),
                    ) {
                        item(false, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
                        group(
                            expanded = true,
                            onExpandedChange = {},
                            key = "routes",
                            icon = Tabler.Outline.Route,
                            header = { +"Routes" },
                        ) {
                            item(true, {}, key = "950", icon = Tabler.Outline.Bus) { +"950" }
                            item(false, {}, key = "998", icon = Tabler.Outline.Bus) { +"998" }
                        }
                        item(false, {}, key = "saved", icon = Tabler.Outline.Star, modifier = Modifier.testTag("after")) {
                            +"Saved"
                        }
                    }
                }
            }
        }
        waitForIdle()
        val wide = onNodeWithTag("rail").fetchSemanticsNode().boundsInRoot.width
        val openTop = onNodeWithTag("after").fetchSemanticsNode().boundsInRoot.top

        mainClock.autoAdvance = false
        expanded = false
        val widths = mutableListOf<Float>()
        val tops = mutableListOf<Float>()
        repeat(90) {
            mainClock.advanceTimeByFrame()
            widths += onNodeWithTag("rail").fetchSemanticsNode().boundsInRoot.width
            tops += onNodeWithTag("after").fetchSemanticsNode().boundsInRoot.top
        }
        mainClock.autoAdvance = true
        val narrow = widths.last()
        val shutTop = tops.last()
        val travel = openTop - shutTop
        assertTrue(travel > 20f, "the rows under the group moved ${travel}px when it folded away")

        // The frame the rail is a quarter of the way to collapsed: half-way
        // through the part of it where labels are showing.
        val quarter = widths.indexOfFirst { (wide - it) / (wide - narrow) >= 0.25f }
        assertTrue(quarter >= 0, "the rail never narrowed")
        val moved = (openTop - tops[quarter]) / travel
        assertTrue(
            moved >= 0.3f,
            "with the rail a quarter of the way closed the group had folded ${(moved * 100).toInt()}% — its " +
                "destinations wait for the halfway point and then go on a clock of their own",
        )
        val steps = tops.zipWithNext { a, b -> abs(a - b) }
        assertTrue(
            steps.max() <= travel * 0.35f,
            "the rows under the group jumped ${steps.max()}px of $travel in one frame",
        )
    }

    @Composable
    private fun NavScope.items() {
        item(true, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
        item(false, {}, key = "routes", icon = Tabler.Outline.Route) { +"Routes" }
        item(false, {}, key = "stops", icon = Tabler.Outline.Star) { +"Stops" }
    }

    /** How dark the hairline's row is, well to the end of the section where no title reaches. */
    private fun BufferedImage.hairlineInk(section: Rect): Float {
        val y = section.center.y.toInt()
        val x = (section.left + section.width * 0.8f).toInt()
        val ground = darkness(getRGB(section.left.toInt() + 2, section.top.toInt() + 2))
        return (-1..1).maxOf { darkness(getRGB(x, y + it)) } - ground
    }

    /** How dark the darkest pixel of the title's rows is, away from the hairline. */
    private fun BufferedImage.titleInk(section: Rect): Float {
        val ground = darkness(getRGB(section.left.toInt() + 2, section.top.toInt() + 2))
        var darkest = 0f
        for (y in (section.center.y + 4).toInt() until section.bottom.toInt()) {
            for (x in section.left.toInt() until (section.left + section.width * 0.45f).toInt()) {
                darkest = maxOf(darkest, darkness(getRGB(x, y)) - ground)
            }
        }
        return darkest
    }

    private fun darkness(rgb: Int): Float {
        val r = rgb shr 16 and 0xFF
        val g = rgb shr 8 and 0xFF
        val b = rgb and 0xFF
        return 1f - (r + g + b) / (3f * 255f)
    }

    private fun List<Float>.fmt() = joinToString(" ") { "%.2f".format(it) }
}
