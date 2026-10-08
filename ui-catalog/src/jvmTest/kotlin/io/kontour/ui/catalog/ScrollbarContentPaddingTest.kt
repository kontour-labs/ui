package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.list.scrollbar
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A scrollbar keeps clear of what floats over its scroller's ends.
 *
 * Asked for as *"we need to make sure the scrollbar modifier can keep the bar
 * clear of floating tabs or top bars"*: a list under a top bar and a floating
 * nav bar scrolls its rows clear of both with `contentPadding`, and its bar ran
 * the whole height anyway, its thumb under the top bar at the top of the list
 * and under the nav bar at the foot. `contentPadding` on the bar is the same
 * padding, and the track stops short of it.
 *
 * Measured as differences, as `ScrollbarCornerInsetTest` is, so the track's own
 * margin is not pinned by accident: the thumb's top at the start of the list
 * moves down by the top padding, and its foot at the end of the list moves up
 * by the bottom padding — for the modifier and for the component alike.
 */
class ScrollbarContentPaddingTest {

    @Test
    fun theModifiersTrackStopsShortOfTheBarsOverItsEnds() {
        assertClears { padding, atEnd -> OnTheScroller(padding, atEnd) }
    }

    @Test
    fun aScrollbarOfItsOwnStopsShortOfThemToo() {
        assertClears { padding, atEnd -> BesideTheScroller(padding, atEnd) }
    }

    private fun assertClears(content: @Composable (PaddingValues, Boolean) -> Unit) {
        val none = PaddingValues(0.dp)
        val bars = PaddingValues(top = Top.dp, bottom = Bottom.dp)

        val topFlush = inkRows(none, atEnd = false) { p, e -> content(p, e) }.first
        val topClear = inkRows(bars, atEnd = false) { p, e -> content(p, e) }.first
        val footFlush = inkRows(none, atEnd = true) { p, e -> content(p, e) }.second
        val footClear = inkRows(bars, atEnd = true) { p, e -> content(p, e) }.second

        assertTrue(
            topFlush >= 0 && topClear >= 0 && footFlush >= 0 && footClear >= 0,
            "the thumb was not drawn: $topFlush $topClear $footFlush $footClear",
        )
        val down = topClear - topFlush
        assertTrue(
            abs(down - Top * Density) <= Tolerance,
            "a ${Top}dp top bar moved the thumb's top by ${down / Density}dp at the start of the list, not ${Top}dp",
        )
        val up = footFlush - footClear
        assertTrue(
            abs(up - Bottom * Density) <= Tolerance,
            "a ${Bottom}dp bar at the foot moved the thumb's foot by ${up / Density}dp at the end of the list, not ${Bottom}dp",
        )
    }

    /** The first and last rows holding anything but the white page. */
    private fun inkRows(
        padding: PaddingValues,
        atEnd: Boolean,
        content: @Composable (PaddingValues, Boolean) -> Unit,
    ): Pair<Int, Int> {
        var rows = -1 to -1
        Scene(width = 120, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White)) { content(padding, atEnd) }
        }.use { scene -> rows = scene.frames(30).inkRows() }
        return rows
    }

    private fun BufferedImage.inkRows(): Pair<Int, Int> {
        var first = -1
        var last = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((getRGB(x, y) and 0xFFFFFF) != 0xFFFFFF) {
                    if (first < 0) first = y
                    last = y
                    break
                }
            }
        }
        return first to last
    }

    private companion object {
        const val Density = 2
        const val Top = 56
        const val Bottom = 80

        /** Antialiasing on the thumb's rounded cap, and nothing more. */
        const val Tolerance = 3
    }
}

@Composable
private fun OnTheScroller(padding: PaddingValues, atEnd: Boolean) {
    val scroll = rememberScrollState()
    ScrollTo(scroll, atEnd)
    Column(
        Modifier
            .fillMaxSize()
            // Drawn at rest: the scene has no pointer to hover it.
            .scrollbar(scroll, alwaysVisible = true, contentPadding = padding)
            .verticalScroll(scroll),
    ) {
        repeat(20) { Box(Modifier.fillMaxWidth().height(40.dp)) }
    }
}

@Composable
private fun BesideTheScroller(padding: PaddingValues, atEnd: Boolean) {
    val scroll = rememberScrollState()
    ScrollTo(scroll, atEnd)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(scroll)) {
            repeat(20) { Box(Modifier.fillMaxWidth().height(40.dp)) }
        }
        Scrollbar(
            state = scroll,
            modifier = Modifier.align(Alignment.CenterEnd),
            alwaysVisible = true,
            contentPadding = padding,
        )
    }
}

@Composable
private fun ScrollTo(scroll: ScrollState, atEnd: Boolean) {
    LaunchedEffect(scroll, atEnd) {
        if (!atEnd) return@LaunchedEffect
        // Once it has been measured: the most it can scroll is unknown before.
        val max = snapshotFlow { scroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
        scroll.scrollTo(max)
    }
}
