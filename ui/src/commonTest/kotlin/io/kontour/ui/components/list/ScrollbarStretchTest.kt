package io.kontour.ui.components.list

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A scrollbar's thumb gives up some of its length while the content under it
 * is pulled past its end, keeps its place at the edge it is pressed against,
 * and has its length back once let go.
 *
 * Asked for for 0.5.2 as *"if a scrollbar is in a container with overscroll,
 * it shrinks at the bottom slightly as the overscroll happens"*.
 */
@OptIn(ExperimentalTestApi::class)
class ScrollbarStretchTest {

    /** An overscroll that takes what it is offered and shows nothing: enough that something overscrolls. */
    private object Swallows : OverscrollFactory {
        override fun createOverscrollEffect(): OverscrollEffect = object : OverscrollEffect {
            override val isInProgress: Boolean get() = false
            override fun applyToScroll(delta: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
                performScroll(delta)
                return delta
            }
            override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
                performFling(velocity)
            }
            override val node: DelegatableNode get() = object : Modifier.Node() {}
        }
        override fun hashCode(): Int = 1
        override fun equals(other: Any?): Boolean = other === this
    }

    /** The thumb's top and bottom in the strip at the list's right edge: anything not the white page. */
    private fun ComposeUiTest.thumb(): Pair<Int, Int> {
        val image = onNodeWithTag("list").captureToImage().toPixelMap()
        val rows = (0 until image.height).filter { y ->
            (image.width - 12 until image.width).any { x -> image[x, y].let { it.green < 0.9f } }
        }
        return (rows.minOrNull() ?: -1) to (rows.maxOrNull() ?: -1)
    }

    private fun ComposeUiTest.list(overscrolls: Boolean) {
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = true) {
                // A pointer's bar, which `alwaysVisible` keeps drawn; a finger's shows
                // only while the list moves.
                CompositionLocalProvider(
                    LocalOverscrollFactory provides if (overscrolls) Swallows else null,
                    LocalInputModality provides InputModality.Mouse,
                ) {
                    val state = rememberLazyListState(initialFirstVisibleItemIndex = 60)
                    LazyColumn(
                        state = state,
                        modifier = Modifier.size(200.dp, 400.dp).background(Color.White).testTag("list")
                            .scrollbar(state, colour = Color.Red, alwaysVisible = true),
                    ) {
                        items(60) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                    }
                }
            }
        }
        mainClock.advanceTimeBy(500)
    }

    @Test
    fun aPullPastTheEndShortensTheThumbFromItsFreeEnd() = runComposeUiTest {
        list(overscrolls = true)
        val (top, bottom) = thumb()
        assertTrue(top >= 0, "no thumb was drawn")
        onNodeWithTag("list").performTouchInput { down(center); moveBy(Offset(0f, -40f)); moveBy(Offset(0f, -120f)); moveBy(Offset(0f, -120f)) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val (pulledTop, pulledBottom) = thumb()
        assertTrue(pulledTop > top + 4, "the thumb did not shorten while the list was pulled past its end: $top–$bottom, then $pulledTop–$pulledBottom")
        assertTrue(kotlin.math.abs(pulledBottom - bottom) <= 1, "the thumb left the end it was pressed against: $bottom, then $pulledBottom")

        onNodeWithTag("list").performTouchInput { up() }
        mainClock.advanceTimeBy(1500)
        assertEquals(top to bottom, thumb(), "the thumb did not have its length back once let go")
    }

    /** Where nothing overscrolls — a desktop — a drag against the end leaves the thumb as it is. */
    @Test
    fun withNothingOverscrollingTheThumbStaysWhole() = runComposeUiTest {
        list(overscrolls = false)
        val before = thumb()
        onNodeWithTag("list").performTouchInput { down(center); moveBy(Offset(0f, -40f)); moveBy(Offset(0f, -240f)) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertEquals(before, thumb())
        onNodeWithTag("list").performTouchInput { up() }
    }
}
