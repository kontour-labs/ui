package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.list.scrollbar
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A scrollbar under touch is an indicator: it appears while the content moves
 * and fades once it stops, and while it is drawn a finger can take hold of its
 * thumb — and of nothing else, so the rest of the strip is still the list's.
 * With a mouse or a keyboard it is there all along. And it reads every scroller
 * foundation ships.
 *
 * Asked for: *"scrollbar: make it so it can show up on mobile when you start
 * scrolling, and make that the default behaviour. also have a horizontal
 * option. also make it easier to attach to a column, lazylist, etc."* Under
 * touch it was never drawn at all; a grid's was never drawn anywhere; and it
 * was attached as a sibling in a `Box`, aligned by hand. `Modifier.scrollbar`
 * goes on the scroller itself.
 *
 * Read from the pixels: white rows, so anything darker in the strip at an edge
 * is the bar.
 */
class ScrollbarTouchTest {

    private companion object {
        const val Width = 400
        const val Height = 600

        /** The bar's strip at a scene's density of 2: `hoveredThickness` and its padding, and a little. */
        const val Strip = 28
    }

    private fun ink(image: BufferedImage, xs: IntProgression, ys: IntProgression = 0 until Height): Int {
        var count = 0
        for (x in xs) for (y in ys step 4) if ((image.getRGB(x, y) shr 16 and 0xFF) < 235) count++
        return count
    }

    private fun right(image: BufferedImage) = ink(image, (Width - Strip) until Width)
    private fun left(image: BufferedImage) = ink(image, 0 until Strip)
    private fun foot(image: BufferedImage) = ink(image, 0 until Width step 4, (Height - Strip) until Height)

    /**
     * Sixty white rows. Clickable only where a test taps one: a pressed row's
     * highlight runs the full width, strip included, and would be read as the bar.
     */
    @Composable
    private fun Rows(state: LazyListState, modifier: Modifier = Modifier, onClick: ((Int) -> Unit)? = null) {
        LazyColumn(modifier.fillMaxSize().background(Color.White), state = state) {
            items(60) { row ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Color.White)
                        .then(if (onClick != null) Modifier.clickable { onClick(row) } else Modifier)
                )
            }
        }
    }

    // ── The composable, under touch ────────────────────────────────────────────

    @Test
    fun aTouchScrollShowsTheBar() {
        Scene(width = Width, height = Height) {
            val list = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                Rows(list)
                Scrollbar(list, Modifier.align(Alignment.CenterEnd))
            }
        }.use { scene ->
            val rest = scene.frames(6)
            assertEquals(0, right(rest), "under touch, nothing should be drawn at rest")
            scene.drag(Offset(200f, 450f), Offset(200f, 250f), steps = 20, release = false)
            val moving = scene.frames(8)
            assertTrue(right(moving) > 0, "under touch, the bar should be up while the list moves")
            scene.release(Offset(200f, 250f))
        }
    }

    @Test
    fun itFadesAfterTheLinger() {
        Scene(width = Width, height = Height) {
            val list = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                Rows(list)
                Scrollbar(list, Modifier.align(Alignment.CenterEnd))
            }
        }.use { scene ->
            scene.frames(6)
            scene.drag(Offset(200f, 450f), Offset(200f, 250f), steps = 20)
            assertTrue(right(scene.frames(4)) > 0, "the bar should still be up just after the finger lifts")
            // The linger is a `delay`, on the wall clock.
            val gone = scene.renderUntil(timeoutMillis = 5_000) { right(it) == 0 }
            assertTrue(gone != null, "the bar should fade once the list has stopped and the linger has passed")
            scene.frames(30)
            assertTrue(!scene.stillAnimating(), "a faded bar should leave nothing animating")
        }
    }

    @Test
    fun aTouchOverlayLeavesTheListItsTouches() {
        val clicked = mutableListOf<Int>()
        Scene(width = Width, height = Height) {
            val list = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                Rows(list) { clicked += it }
                Scrollbar(list, Modifier.align(Alignment.CenterEnd), alwaysVisible = true)
            }
        }.use { scene ->
            scene.frames(6)
            // Row 2, a few pixels in from the right: under the bar's strip.
            scene.tap(Offset(Width - 6f, 2 * 80f + 40f))
            scene.frames(4)
            assertEquals(listOf(2), clicked, "a tap under a touch bar's strip should be the row's")
        }
    }

    // ── A finger on a drawn bar ─────────────────────────────────────────────────

    /** How far the list is through what it can scroll: sixty 80px rows in a 600px window. */
    private fun through(list: LazyListState): Float =
        (list.firstVisibleItemIndex * 80f + list.firstVisibleItemScrollOffset) / (60 * 80f - Height)

    /**
     * The thumb's travel at a scene's density of 2: the track is the height less
     * `xxs` at each end, and the thumb is the eighth of it the window shows.
     */
    private val travel = (Height - 16f) * (1f - Height / (60 * 80f))

    /**
     * Asked for: *"if the scrollbar is visible (even on mobile), then dragging
     * it should do what you expect"*. It took no touches, so a bar on a phone
     * could be seen and not moved.
     *
     * Down the thumb is down the list, and by the track's rate. The same drag on
     * the content would have pulled a list at its top nowhere, so a list that
     * went forward went by the bar.
     */
    @Test
    fun aFingerDragsADrawnBarsThumb() {
        lateinit var list: LazyListState
        Scene(width = Width, height = Height) {
            list = rememberLazyListState()
            Rows(list, Modifier.scrollbar(list, alwaysVisible = true))
        }.use { scene ->
            scene.frames(6)
            scene.drag(Offset(Width - 6f, 30f), Offset(Width - 6f, 330f), steps = 20)
            scene.frames(10)
            val expected = 300f / travel
            assertTrue(
                abs(through(list) - expected) < 0.05f,
                "a finger dragging the thumb 300px should take the list ${expected} of its way, took ${through(list)}",
            )
        }
    }

    /** The same, on the composable laid over its list rather than the modifier on it. */
    @Test
    fun aFingerDragsAnOverlaidBarsThumb() {
        lateinit var list: LazyListState
        Scene(width = Width, height = Height) {
            list = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                Rows(list)
                Scrollbar(list, Modifier.align(Alignment.CenterEnd), alwaysVisible = true)
            }
        }.use { scene ->
            scene.frames(6)
            scene.drag(Offset(Width - 6f, 30f), Offset(Width - 6f, 330f), steps = 20)
            scene.frames(10)
            val expected = 300f / travel
            assertTrue(
                abs(through(list) - expected) < 0.05f,
                "a finger dragging the thumb 300px should take the list ${expected} of its way, took ${through(list)}",
            )
        }
    }

    /**
     * Up because the list scrolled, not because it was asked to stay: the
     * thumb can be taken hold of in the linger after a scroll, which is when a
     * phone shows it.
     */
    @Test
    fun aBarUpAfterAScrollCanBeTakenHoldOf() {
        lateinit var list: LazyListState
        Scene(width = Width, height = Height) {
            list = rememberLazyListState()
            Rows(list, Modifier.scrollbar(list))
        }.use { scene ->
            scene.frames(6)
            // Held still before lifting, so nothing flings on afterwards.
            scene.drag(Offset(200f, 450f), Offset(200f, 250f), steps = 20, release = false)
            scene.frames(4)
            scene.release(Offset(200f, 250f))
            scene.frames(2)
            val before = through(list)
            val thumb = 8f + travel * before + 20f
            scene.drag(Offset(Width - 6f, thumb), Offset(Width - 6f, thumb + 200f), steps = 20)
            scene.frames(10)
            assertTrue(
                through(list) - before > 0.3f,
                "dragging the thumb down in the linger should take the list on, went from $before to ${through(list)}",
            )
        }
    }

    /** Off the thumb, a drawn bar's strip is the list's to scroll. */
    @Test
    fun offTheThumbTheStripIsTheLists() {
        lateinit var list: LazyListState
        Scene(width = Width, height = Height) {
            list = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                Rows(list)
                Scrollbar(list, Modifier.align(Alignment.CenterEnd), alwaysVisible = true)
            }
        }.use { scene ->
            scene.frames(6)
            scene.drag(Offset(Width - 6f, 450f), Offset(Width - 6f, 250f), steps = 20, release = false)
            scene.frames(4)
            scene.release(Offset(Width - 6f, 250f))
            scene.frames(4)
            assertEquals(2, list.firstVisibleItemIndex, "a drag up the strip below the thumb should scroll the list by 200px")
        }
    }

    /** A bar that is not drawn takes nothing, even where its thumb would be. */
    @Test
    fun aBarThatIsNotDrawnTakesNothing() {
        lateinit var list: LazyListState
        Scene(width = Width, height = Height) {
            list = rememberLazyListState()
            Rows(list, Modifier.scrollbar(list))
        }.use { scene ->
            scene.frames(6)
            scene.drag(Offset(Width - 6f, 30f), Offset(Width - 6f, 330f), steps = 20)
            scene.frames(10)
            assertEquals(0f, through(list), "a drag down on a list at its top, with no bar drawn, should leave it there")
        }
    }

    // ── The modality, and the scrollers it reads ────────────────────────────────

    @Test
    fun aKeyboardUserSeesTheBarAtRest() {
        Scene(width = Width, height = Height) {
            CompositionLocalProvider(LocalInputModality provides InputModality.Keyboard) {
                val list = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                    Rows(list)
                    Scrollbar(list, Modifier.align(Alignment.CenterEnd))
                }
            }
        }.use { scene ->
            assertTrue(right(scene.frames(6)) > 0, "with a keyboard, the bar should be there at rest, as it is with a mouse")
        }
    }

    @Test
    fun aGridHasABar() {
        Scene(width = Width, height = Height) {
            val grid = rememberLazyGridState()
            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize().background(Color.White), state = grid) {
                    items(300) { Box(Modifier.size(40.dp).background(Color.White)) }
                }
                Scrollbar(grid, Modifier.align(Alignment.CenterEnd), alwaysVisible = true)
            }
        }.use { scene ->
            assertTrue(right(scene.frames(6)) > 0, "a grid's bar should be drawn")
        }
    }

    // ── Modifier.scrollbar ───────────────────────────────────────────────────────

    /** A mouse over the window, so the scene's input is a mouse. */
    private fun Scene.hover() {
        move(Offset(200f, 300f), PointerType.Mouse)
        frames(4)
    }

    @Test
    fun theModifierDrawsOnTheScrollersEdge() {
        Scene(width = Width, height = Height) {
            val list = rememberLazyListState()
            Rows(list, Modifier.scrollbar(list))
        }.use { scene ->
            scene.hover()
            val image = scene.frames(6)
            assertTrue(right(image) > 0, "with a mouse, the bar should be on the list's trailing edge")
            assertEquals(0, left(image), "and nowhere else")
        }
    }

    @Test
    fun draggingTheModifiersBarScrollsTheList() {
        lateinit var list: LazyListState
        Scene(width = Width, height = Height) {
            list = rememberLazyListState()
            Rows(list, Modifier.scrollbar(list))
        }.use { scene ->
            scene.hover()
            val x = Width - 6f
            scene.move(Offset(x, 60f), PointerType.Mouse)
            scene.frames(4)
            scene.drag(Offset(x, 60f), Offset(x, 180f), steps = 20, pointer = PointerType.Mouse)
            scene.frames(10)
            assertTrue(list.firstVisibleItemIndex > 0, "dragging the bar down should scroll the list down")
        }
    }

    @Test
    fun aMouseClickOffTheBarReachesTheRow() {
        val clicked = mutableListOf<Int>()
        Scene(width = Width, height = Height) {
            val list = rememberLazyListState()
            Rows(list, Modifier.scrollbar(list)) { clicked += it }
        }.use { scene ->
            scene.hover()
            val at = Offset(200f, 2 * 80f + 40f)
            scene.press(at, PointerType.Mouse)
            scene.frame()
            scene.release(at, PointerType.Mouse)
            scene.frames(4)
            assertEquals(listOf(2), clicked, "a click away from the bar should be the row's")
        }
    }

    @Test
    fun rightToLeftTheBarIsOnTheLeft() {
        Scene(width = Width, height = Height) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val list = rememberLazyListState()
                Rows(list, Modifier.scrollbar(list))
            }
        }.use { scene ->
            scene.hover()
            val image = scene.frames(6)
            assertTrue(left(image) > 0, "right to left, the trailing edge is the left one")
            assertEquals(0, right(image))
        }
    }

    @Test
    fun aRowsBarRunsAlongItsFoot() {
        Scene(width = Width, height = Height) {
            val row = rememberLazyListState()
            LazyRow(Modifier.fillMaxSize().background(Color.White).scrollbar(row, Orientation.Horizontal), state = row) {
                items(60) { Box(Modifier.size(80.dp, 300.dp).background(Color.White)) }
            }
        }.use { scene ->
            scene.hover()
            assertTrue(foot(scene.frames(6)) > 0, "a horizontal bar should run along the row's foot")
        }
    }

    @Test
    fun underTouchTheModifierShowsWhileScrolling() {
        Scene(width = Width, height = Height) {
            val list = rememberLazyListState()
            Rows(list, Modifier.scrollbar(list))
        }.use { scene ->
            assertEquals(0, right(scene.frames(6)), "under touch, nothing at rest")
            scene.drag(Offset(200f, 450f), Offset(200f, 250f), steps = 20, release = false)
            assertTrue(right(scene.frames(8)) > 0, "under touch, the bar should be up while the list moves")
            scene.release(Offset(200f, 250f))
        }
    }

    // ── A press that changes the hand ───────────────────────────────────────────

    /**
     * The hand is read from the press, so a tap is what turns a mouse's bar into
     * a finger's. Whatever the bar does about that, the tap is still the row's.
     * The bar's nodes for a mouse were taken off the scroller as the tap went
     * down, which cancelled the press on everything inside it.
     */
    @Test
    fun aFingersTapAfterAMouseReachesTheRow() {
        val clicked = mutableListOf<Int>()
        Scene(width = Width, height = Height) {
            val list = rememberLazyListState()
            Rows(list, Modifier.scrollbar(list)) { clicked += it }
        }.use { scene ->
            scene.hover()
            scene.tap(Offset(200f, 2 * 80f + 40f))
            scene.frames(4)
            assertEquals(listOf(2), clicked, "a finger's tap on a row, after a mouse, should be the row's")
        }
    }

    /** And the other way: a mouse's click on a list a finger last touched. */
    @Test
    fun aMouseClickAfterAFingerReachesTheRow() {
        val clicked = mutableListOf<Int>()
        Scene(width = Width, height = Height) {
            val list = rememberLazyListState()
            Rows(list, Modifier.scrollbar(list)) { clicked += it }
        }.use { scene ->
            scene.frames(6)
            val at = Offset(200f, 2 * 80f + 40f)
            scene.press(at, PointerType.Mouse)
            scene.frame()
            scene.release(at, PointerType.Mouse)
            scene.frames(4)
            assertEquals(listOf(2), clicked, "a mouse's click on a row, under touch, should be the row's")
        }
    }
}
