package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Star
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarCollapseState
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.topBarCollapse
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A large top bar collapses by carrying its title into the first line, and grows
 * shorter as it does.
 *
 * Asked for directly: *"can we make it so scrolling smoothly animates the title
 * text into the top, rather than fading? please ensure it looks good with and
 * without the back action. and we need to make the actual bar at the top become
 * less tall during that animation."* It used to cross-fade a large title out
 * and a small one in, at a bar height that never changed.
 *
 * Read from pixels where the question is what is drawn — the title's ink, and
 * a collapsed bar against a Small one — and from bounds where it is where the
 * content is.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarCollapseTest {

    private companion object {
        const val Density = 2f
        const val Width = 360
        const val Title = "Perth"
        const val Subtitle = "Platform 2"

        /** Near-black: the title's own colour, not a half-faded one. */
        const val Ink = 0x60
    }

    @Composable
    private fun Bar(
        style: TopBarStyle,
        back: Boolean,
        progress: Float = 0f,
        titled: Boolean = true,
        subtitled: Boolean = false,
        onBounds: (Rect) -> Unit = {},
    ) {
        TopBar(
            modifier = Modifier.reportBounds(onBounds),
            style = style,
            onBack = if (back) ({}) else null,
            actions = { IconButton(icon = Tabler.Outline.Star, contentDescription = "Save", onClick = {}) },
            collapseProgress = progress,
            windowInsets = WindowInsets(0),
        ) {
            if (titled) +Title
            if (subtitled) supporting { +Subtitle }
        }
    }

    /** The bar at [progress], and its bounds. */
    private fun render(
        style: TopBarStyle,
        back: Boolean,
        progress: Float = 0f,
        titled: Boolean = true,
        subtitled: Boolean = false,
    ): Pair<BufferedImage, Rect> {
        var bounds = Rect.Zero
        val image = Scene(width = Width * 2, height = 300, density = Density, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Bar(style, back, progress, titled, subtitled) { bounds = it }
            }
        }.use { it.frames(4) }
        return image to bounds
    }

    /** How many of the pixels in [bar]'s height differ between two renders. */
    private fun differing(a: BufferedImage, b: BufferedImage, bar: Rect): Pair<Int, Int> {
        var differ = 0
        var total = 0
        for (y in 0 until bar.height.toInt()) {
            for (x in 0 until a.width) {
                total++
                val p = a.getRGB(x, y)
                val q = b.getRGB(x, y)
                val d = maxOf(abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)), abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)), abs((p and 0xFF) - (q and 0xFF)))
                if (d > 24) differ++
            }
        }
        return differ to total
    }

    /**
     * The bounding box of the title's ink: what differs from the same bar with
     * no title, so the back button and the actions are never mistaken for it.
     */
    private fun titleInk(image: BufferedImage, bare: BufferedImage, bar: Rect): Rect? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in bar.top.toInt() until bar.bottom.toInt()) {
            for (x in 0 until image.width) {
                val a = image.getRGB(x, y) shr 16 and 0xFF
                val b = bare.getRGB(x, y) shr 16 and 0xFF
                if (abs(a - b) > 64) {
                    left = minOf(left, x); right = maxOf(right, x)
                    top = minOf(top, y); bottom = maxOf(bottom, y)
                }
            }
        }
        return if (right < 0) null else Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    private fun darkest(image: BufferedImage, area: Rect): Int {
        var darkest = 0xFF
        for (y in area.top.toInt()..area.bottom.toInt()) {
            for (x in area.left.toInt()..area.right.toInt()) {
                darkest = minOf(darkest, image.getRGB(x, y) shr 16 and 0xFF)
            }
        }
        return darkest
    }

    @Test
    fun theBarGrowsShorterAsItCollapsesAndEndsAtASmallBarsHeight() {
        for (back in listOf(true, false)) {
            val heights = listOf(0f, 0.5f, 1f).map { render(TopBarStyle.Large, back, it).second.height }
            val small = render(TopBarStyle.Small, back).second.height
            assertTrue(
                heights[0] > heights[1] && heights[1] > heights[2],
                "back = $back: the bar's height through the collapse was $heights — it should shrink",
            )
            assertEquals(small, heights[2], 1f, "back = $back: collapsed, the bar is ${heights[2]}px, a Small bar $small")
        }
    }

    @Test
    fun collapsedItIsTheSmallBarToThePixel() {
        // Where the title ends up, and in what type: the small title, where a
        // Small bar puts it, beside the back button or at the margin without one.
        for (back in listOf(true, false)) {
            val (large, lb) = render(TopBarStyle.Large, back, 1f)
            val (small, sb) = render(TopBarStyle.Small, back)
            assertEquals(sb.height, lb.height, 1f)
            val (differ, total) = differing(large, small, sb)
            assertTrue(
                differ <= total / 500,
                "back = $back: a collapsed Large bar differs from a Small one in $differ of $total pixels",
            )
        }
    }

    @Test
    fun theTitleTravelsIntoTheFirstLineRatherThanFading() {
        for (back in listOf(true, false)) {
            val frames = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { p ->
                val (image, bar) = render(TopBarStyle.Large, back, p)
                val bare = render(TopBarStyle.Large, back, p, titled = false).first
                val ink = titleInk(image, bare, bar)
                checkNotNull(ink) { "back = $back: no title drawn at $p" }
                Triple(p, ink, darkest(image, ink))
            }
            val tops = frames.map { it.second.top }
            val heights = frames.map { it.second.height }
            assertTrue(tops.zipWithNext().all { (a, b) -> b <= a }, "back = $back: the title's top went $tops — it should rise the whole way")
            assertTrue(heights.first() > heights.last() * 1.6f, "back = $back: the title's ink was ${heights.first()}px tall open and ${heights.last()}px collapsed — it should shrink")
            if (back) {
                val lefts = frames.map { it.second.left }
                assertTrue(lefts.zipWithNext().all { (a, b) -> b >= a }, "with a back button the title's start went $lefts — it should move over to beside the button")
            } else {
                val lefts = frames.map { it.second.left }
                assertTrue(lefts.max() - lefts.min() <= 3f * Density, "without a back button the title should rise straight up, but its start went $lefts")
            }
            // Never two half-faded titles: at every point one of them is at
            // nearly full strength. A cross-fade halfway is grey throughout.
            for ((p, _, dark) in frames) {
                assertTrue(dark < Ink, "back = $back: at $p the darkest of the title is 0x${dark.toString(16)} — it is fading rather than moving")
            }
        }
    }

    /**
     * And with a subtitle, which a Small bar keeps under its title: *"can we also
     * keep the subtitle text? we basically want it to morph into a small top bar
     * when you scroll"*. It used to fade out, and the collapsed bar was a Small
     * bar without its second line.
     */
    @Test
    fun collapsedWithASubtitleItIsTheSmallBarToThePixel() {
        for (back in listOf(true, false)) {
            val (large, lb) = render(TopBarStyle.Large, back, 1f, subtitled = true)
            val (small, sb) = render(TopBarStyle.Small, back, subtitled = true)
            assertEquals(sb.height, lb.height, 1f, "back = $back: collapsed, the bar is ${lb.height}px, a Small bar ${sb.height}")
            val (differ, total) = differing(large, small, sb)
            assertTrue(
                differ <= total / 500,
                "back = $back: a collapsed Large bar with a subtitle differs from a Small one in $differ of $total pixels",
            )
        }
    }

    /**
     * On the way, the subtitle is drawn in full and moves: what a bar with a
     * subtitle draws and the same bar without one does not, at every point of
     * the collapse, rising the whole way.
     */
    @Test
    fun theSubtitleTravelsWithTheTitleRatherThanFading() {
        for (back in listOf(true, false)) {
            // What a subtitle adds to the bar. Open, the title is where it is
            // without one, so the difference is the subtitle's ink alone; later
            // the title sits differently too, but the subtitle is always the
            // lowest of what differs, and as tall as it was open.
            fun added(p: Float): Rect {
                val (image, bar) = render(TopBarStyle.Large, back, p, subtitled = true)
                val bare = render(TopBarStyle.Large, back, p).first
                return checkNotNull(titleInk(image, bare, bar)) { "back = $back: a subtitle added nothing at $p" }
            }
            val tall = added(0f).height
            val frames = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { p ->
                val image = render(TopBarStyle.Large, back, p, subtitled = true).first
                val all = added(p)
                val ink = Rect(all.left, all.bottom - tall, all.right, all.bottom)
                Triple(p, ink, darkest(image, ink))
            }
            val bottoms = frames.map { it.second.bottom }
            assertTrue(bottoms.zipWithNext().all { (a, b) -> b <= a }, "back = $back: the subtitle's bottom went $bottoms — it should rise the whole way")
            assertTrue(bottoms.first() - bottoms.last() > 20f * Density, "back = $back: the subtitle rose only from ${bottoms.first()} to ${bottoms.last()}")
            val open = frames.first().third
            for ((p, _, dark) in frames) {
                assertTrue(
                    dark <= open + 16,
                    "back = $back: at $p the darkest of the subtitle is 0x${dark.toString(16)}, against 0x${open.toString(16)} open — it is fading rather than moving",
                )
            }
        }
    }

    @Test
    fun theTitleIsSaidOnce() {
        runComposeUiTest {
            setContent {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    Bar(TopBarStyle.Large, back = true, progress = 0.6f)
                }
            }
            val nodes = onAllNodesWithText(Title).fetchSemanticsNodes()
            assertEquals(1, nodes.size, "the title is in the semantics tree ${nodes.size} times")
        }
    }

    /**
     * Collapsed from the content's own scroll, the content moves with the finger.
     *
     * With the bar in a column above a list, a bar that shrinks moves the list
     * up by as much; if the list scrolled the whole way at the same time the
     * content would travel as far again as the finger. The state's connection
     * gives the bar the part of the scroll its height takes up, and the list
     * only the rest.
     */
    @Test
    fun theContentStaysAgainstTheBarWhileItCollapses() {
        var state: TopBarCollapseState? = null
        var list: LazyListState? = null
        var bar = Rect.Zero
        var first = Rect.Zero
        Scene(width = Width * 2, height = 1200, density = Density) {
            val collapse = rememberTopBarCollapseState().also { state = it }
            val listState = rememberLazyListState().also { list = it }
            Column(Modifier.fillMaxSize().background(Color.White).topBarCollapse(collapse)) {
                TopBar(
                    modifier = Modifier.reportBounds { bar = it },
                    style = TopBarStyle.Large,
                    onBack = {},
                    collapseState = collapse,
                    windowInsets = WindowInsets(0),
                ) { +Title }
                LazyColumn(state = listState) {
                    items(60) { row ->
                        Text(
                            "Row $row",
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                .then(if (row == 0) Modifier.reportBounds { first = it } else Modifier),
                        )
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            val open = bar.height
            val rowAt = first.top
            val from = Offset(Width.toFloat(), 900f)
            // A slow pull, short of the bar's travel, and held.
            val pull = 100f
            scene.drag(from, from - Offset(0f, pull), steps = 30, release = false)
            scene.frames(2)
            val midway = state!!.progress
            assertTrue(midway > 0.05f && midway < 0.95f, "a ${pull}px pull left the bar at $midway")
            assertTrue(bar.height < open, "the bar did not grow shorter")
            // The content went with the finger: no further than it — the touch
            // slop aside, all of it — and mostly by the bar giving way rather
            // than by the list scrolling under it.
            val moved = rowAt - first.top
            val scrolled = list!!.firstVisibleItemScrollOffset.toFloat()
            assertTrue(moved <= pull + 1f, "the first row moved ${moved}px for a ${pull}px pull — the content is outrunning the finger")
            assertTrue(moved >= pull - 40f, "the first row moved ${moved}px for a ${pull}px pull — the content is lagging the finger")
            assertEquals(0, list!!.firstVisibleItemIndex)
            assertTrue(scrolled < (open - bar.height) / 2f, "the list scrolled ${scrolled}px while the bar gave up only ${open - bar.height}px")
            // On, past the collapse: now the list scrolls.
            scene.move(from - Offset(0f, 500f))
            scene.frames(2)
            scene.release(from - Offset(0f, 500f))
            scene.frames(40)
            assertEquals(1f, state!!.progress, "a long pull left the bar at ${state!!.progress}")
            assertTrue(list!!.firstVisibleItemIndex > 0 || list!!.firstVisibleItemScrollOffset > 0, "the list never scrolled")
        }
    }

    @Test
    fun aCollapseLeftPartWayFinishes() {
        for (pull in listOf(60f, 120f)) {
            var state: TopBarCollapseState? = null
            var before = 0f
            Scene(width = Width * 2, height = 1200, density = Density) {
                val collapse = rememberTopBarCollapseState().also { state = it }
                Column(Modifier.fillMaxSize().background(Color.White).topBarCollapse(collapse)) {
                    TopBar(style = TopBarStyle.Large, collapseState = collapse, windowInsets = WindowInsets(0)) { +Title }
                    LazyColumn { items(60) { Text("Row $it", modifier = Modifier.fillMaxWidth().height(48.dp)) } }
                }
            }.use { scene ->
                scene.frames(3)
                val from = Offset(Width.toFloat(), 900f)
                scene.drag(from, from - Offset(0f, pull), steps = 30, release = false)
                before = state!!.progress
                scene.release(from - Offset(0f, pull))
                scene.frames(60)
            }
            assertTrue(before > 0f && before < 1f, "a ${pull}px pull never left the bar part-way (it was at $before), so this measured nothing")
            val settled = state!!.progress
            assertTrue(settled == 0f || settled == 1f, "a ${pull}px pull left the bar part-way, at $settled")
        }
    }
}
