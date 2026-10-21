package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import io.kontour.ui.components.list.scrollbar
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.motion.LocalScrollFeel
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.motion.ScrollFeel
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A half-height sheet's content on a desk: the wheel scrolls it, and its
 * scrollbar is the length of what the sheet shows.
 *
 * Reported from a desktop, verbatim: *"if you have scrollable content in a sheet
 * and the sheet is half-height, then 2 things happen: 1. scrolling on desktop
 * will make the sheet full height. i don't want that, i want it to just scroll
 * 2. the scrollbar just stays at full height for some reason"*.
 *
 * The first is the sheet's nested-scroll connection, which takes a scroll
 * towards the content's end for itself until the sheet is at its top detent —
 * the phone's rule, for a finger, and a wheel's notches arrive by the same road.
 * The second is the content's own height: a sheet's content is measured as tall
 * as the window at every detent and the sheet slides it down, so at `Half` the
 * scroller's lower half hangs below the screen, and its bar with it. The thumb
 * was sized for a viewport twice what can be seen and ran off the bottom of the
 * screen half way down the list.
 *
 * A finger still opens the sheet first, as a phone's does, and a press on the
 * sheet's handle still moves it.
 */
class SheetWheelTest {

    private enum class Content { Lazy, Column }

    private class Run(
        val scene: Scene,
        val state: SheetState,
        val scroll: () -> Float,
        val list: () -> Rect,
        val dismissed: () -> Int,
        val toEnd: () -> Unit,
    )

    private fun sheet(
        content: Content = Content.Lazy,
        direction: LayoutDirection = LayoutDirection.Ltr,
        reduceMotion: Boolean = false,
        ownFling: Boolean = false,
        test: Run.() -> Unit,
    ) {
        lateinit var state: SheetState
        var lazy: LazyListState? = null
        var column: ScrollState? = null
        var list = Rect.Zero
        var dismissed = 0
        var visible by mutableStateOf(true)
        var end by mutableStateOf(false)
        Scene(width = Width, height = Height, density = 2f, reduceMotion = reduceMotion) {
            CompositionLocalProvider(
                LocalInputModality provides InputModality.Mouse,
                LocalLayoutDirection provides direction,
                // A fling of the library's own rather than Compose's, as iOS
                // has by default: see [aWheelThatStopsHandsTheSheetNoFling].
                LocalScrollFeel provides if (ownFling) ScrollFeel() else null,
            ) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White))
                    state = rememberSheetState(
                        detents = listOf(SheetDetent.Hidden, SheetDetent.peek(120.dp), SheetDetent.Half, SheetDetent.Expanded),
                        initialDetent = SheetDetent.Hidden,
                    )
                    ModalBottomSheet(
                        visible = visible,
                        onDismissRequest = { dismissed++; visible = false },
                        state = state,
                        containerColour = Color.White,
                    ) {
                        when (content) {
                            Content.Lazy -> {
                                val scrolled = rememberLazyListState().also { lazy = it }
                                LaunchedEffect(end) { if (end) scrolled.scrollToItem(Rows - 1) }
                                LazyColumn(
                                    Modifier
                                        .fillMaxWidth()
                                        .reportBounds { list = it }
                                        .scrollbar(scrolled, colour = Thumb, alwaysVisible = true),
                                    state = scrolled,
                                    flingBehavior = ScrollDefaults.flingBehavior(),
                                ) {
                                    items(Rows) { Box(Modifier.fillMaxWidth().height(RowDp.dp)) }
                                }
                            }
                            Content.Column -> {
                                val scrolled = rememberScrollState().also { column = it }
                                LaunchedEffect(end) { if (end) scrolled.scrollTo(scrolled.maxValue) }
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .reportBounds { list = it }
                                        .scrollbar(scrolled, colour = Thumb, alwaysVisible = true)
                                        .verticalScroll(scrolled, flingBehavior = ScrollDefaults.flingBehavior()),
                                ) {
                                    repeat(Rows) { Box(Modifier.fillMaxWidth().height(RowDp.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(90)
            assertEquals(SheetDetent.Half, state.currentDetent, "the sheet opened somewhere else, so this measured nothing")
            val scroll = {
                lazy?.let { (it.firstVisibleItemIndex * RowDp * 2 + it.firstVisibleItemScrollOffset).toFloat() }
                    ?: column!!.value.toFloat()
            }
            Run(scene, state, scroll, { list }, { dismissed }, { end = true }).test()
        }
    }

    private fun Run.wheel(notches: Int, down: Boolean, at: Offset = Offset(Width / 2f, Height - 120f)) = repeat(notches) {
        scene.scroll(at, Offset(0f, if (down) 1f else -1f))
        scene.frames(4)
    }

    @Test
    fun aWheelOverAHalfSheetsListScrollsTheList() {
        for (content in Content.entries) sheet(content) {
            val top = state.offset
            wheel(6, down = true)
            scene.frames(60)
            assertTrue(scroll() > 0f, "$content: six notches of the wheel never scrolled the content")
            assertEquals(SheetDetent.Half, state.currentDetent, "$content: the wheel moved the sheet to ${state.currentDetent}")
            assertEquals(top, state.offset, 0.5f, "$content: the wheel moved the sheet from $top to ${state.offset}")
        }
    }

    /** And back up past the content's top, the sheet neither closes nor drops. */
    @Test
    fun aWheelBackUpPastTheTopLeavesTheSheetWhereItIs() {
        for (content in Content.entries) sheet(content) {
            val top = state.offset
            wheel(3, down = true)
            wheel(12, down = false)
            scene.frames(90)
            assertEquals(0f, scroll(), "$content: the content never came back to its top")
            assertEquals(SheetDetent.Half, state.currentDetent, "$content: the wheel moved the sheet to ${state.currentDetent}")
            assertEquals(top, state.offset, 0.5f, "$content: the wheel moved the sheet from $top to ${state.offset}")
            assertEquals(0, dismissed(), "$content: the wheel closed the sheet")
        }
    }

    /**
     * At the end of what it can scroll, with more of the sheet below the
     * screen, the wheel opens the sheet to show it: the rest of the list is
     * there and nothing else reaches it.
     */
    @Test
    fun aWheelPastTheEndOpensTheSheetToShowTheRest() {
        for (content in Content.entries) sheet(content) {
            toEnd()
            scene.frames(30)
            assertEquals(SheetDetent.Half, state.currentDetent, "$content: scrolled to its end in code, the sheet moved")
            wheel(2, down = true)
            scene.frames(90)
            assertEquals(SheetDetent.Expanded, state.currentDetent, "$content: a wheel past the end left the sheet at ${state.currentDetent}")
            assertTrue(list().bottom <= Height + 1f, "$content: opened, the list's foot is still off the screen at ${list().bottom}")
        }
    }

    /** The phone's rule stays the finger's: a drag up on the list opens the sheet first. */
    @Test
    fun aFingerDraggingTheListUpStillOpensTheSheet() {
        sheet {
            val from = Offset(Width / 2f, Height - 100f)
            scene.drag(from, from.copy(y = from.y - 300f), steps = 20)
            scene.frames(90)
            assertEquals(SheetDetent.Expanded, state.currentDetent, "a finger dragging the list up left the sheet at ${state.currentDetent}")
        }
    }

    /** A mouse dragging the scrollbar's thumb scrolls the list, and the sheet stays where it is. */
    @Test
    fun aMouseDraggingTheThumbScrollsTheListAndNotTheSheet() {
        sheet {
            val top = state.offset
            val thumb = thumbRows(scene.frame())
            assertTrue(thumb != null, "no thumb was drawn")
            val at = Offset(Width - 6f, (thumb.first + thumb.last) / 2f)
            // Over the bar before it presses, as a mouse is: the hover is what
            // makes the strip the bar's.
            scene.move(at, PointerType.Mouse)
            scene.frames(4)
            scene.press(at, PointerType.Mouse)
            scene.frame()
            repeat(10) {
                scene.move(at.copy(y = at.y + 8f * (it + 1)), PointerType.Mouse)
                scene.frame()
            }
            scene.release(at.copy(y = at.y + 80f), PointerType.Mouse)
            scene.frames(60)
            assertTrue(scroll() > 0f, "dragging the thumb never scrolled the list")
            assertEquals(SheetDetent.Half, state.currentDetent, "dragging the thumb moved the sheet to ${state.currentDetent}")
            assertEquals(top, state.offset, 0.5f, "dragging the thumb moved the sheet from $top to ${state.offset}")
        }
    }

    /**
     * And let go while it is still moving fast, the sheet stays where it is.
     *
     * Found in the 0.5.0 review, 2026-10-21: the thumb let go hands the
     * content's scroll chain a fling of nothing, so that whatever collapses
     * as the content scrolls settles, and the sheet read that fling as a
     * release of its own. The thumb's drag never moved the sheet, so nothing
     * said it was the list's, and the sheet's finger, which follows a mouse's
     * press as it does a finger's, had the mouse still moving when it let go.
     * So the sheet was thrown with the mouse's speed: at two thousand pixels
     * a second, down closed it, and up opened it to full height. The test
     * above moves at five hundred, under what counts as a flick.
     */
    @Test
    fun aThumbLetGoWhileMovingFastLeavesTheSheetWhereItIs() {
        for (down in listOf(true, false)) sheet {
            if (!down) {
                // At the list's end, where the thumb has room to go up.
                toEnd()
                scene.frames(30)
            }
            val top = state.offset
            val thumb = thumbRows(scene.frame())
            assertTrue(thumb != null, "no thumb was drawn")
            val at = Offset(Width - 6f, (thumb.first + thumb.last) / 2f)
            val way = if (down) 1f else -1f
            val before = scroll()
            scene.move(at, PointerType.Mouse)
            scene.frames(4)
            scene.press(at, PointerType.Mouse)
            scene.frame()
            // 32px a 16ms frame: two thousand pixels a second, and let go
            // a frame after the last move, still moving.
            repeat(4) {
                scene.move(at.copy(y = at.y + way * 32f * (it + 1)), PointerType.Mouse)
                scene.frame()
            }
            scene.release(at.copy(y = at.y + way * 128f), PointerType.Mouse)
            scene.frames(90)
            val which = if (down) "down" else "up"
            assertTrue(scroll() != before, "dragging the thumb $which never scrolled the list, so this measured nothing")
            assertEquals(0, dismissed(), "a thumb let go moving $which closed the sheet")
            assertEquals(SheetDetent.Half, state.currentDetent, "a thumb let go moving $which moved the sheet to ${state.currentDetent}")
            assertEquals(top, state.offset, 0.5f, "a thumb let go moving $which moved the sheet from $top to ${state.offset}")
        }
    }

    /**
     * A wheel or a trackpad that stops hands the sheet no fling either, where
     * the list has a fling of its own.
     *
     * Found in the 0.5.0 review, 2026-10-21. Compose flings a scrollable when
     * a wheel stops if its fling is not Compose's default one, which is true
     * of every list under a [LocalScrollFeel] (iOS's, by default) and of a
     * snapping list. That fling runs down the scroll chain as a finger's
     * does, and the sheet answered it as one: a burst of the wheel down a
     * half-height sheet's list opened the sheet to full height, and one back
     * up past the list's top threw the sheet down.
     */
    @Test
    fun aWheelThatStopsHandsTheSheetNoFling() {
        for (content in Content.entries) sheet(content, ownFling = true) {
            val top = state.offset
            // A burst, a notch a frame, and then nothing.
            repeat(6) {
                scene.scroll(Offset(Width / 2f, Height - 120f), Offset(0f, 1f))
                scene.frame()
            }
            scene.frames(90)
            assertTrue(scroll() > 0f, "$content: the wheel never scrolled the content")
            assertEquals(SheetDetent.Half, state.currentDetent, "$content: a burst of the wheel down moved the sheet to ${state.currentDetent}")
            assertEquals(top, state.offset, 0.5f, "$content: a burst of the wheel down moved the sheet from $top to ${state.offset}")
            repeat(12) {
                scene.scroll(Offset(Width / 2f, Height - 120f), Offset(0f, -1f))
                scene.frame()
            }
            scene.frames(90)
            assertEquals(0f, scroll(), "$content: the content never came back to its top")
            assertEquals(0, dismissed(), "$content: a burst of the wheel up past the top closed the sheet")
            assertEquals(SheetDetent.Half, state.currentDetent, "$content: a burst of the wheel up moved the sheet to ${state.currentDetent}")
            assertEquals(top, state.offset, 0.5f, "$content: a burst of the wheel up moved the sheet from $top to ${state.offset}")
        }
    }

    /**
     * The thumb is as long a share of what the sheet shows as what the sheet
     * shows is of the content, at `Half` and at full height.
     */
    @Test
    fun theThumbIsTheShareOfTheContentTheSheetShows() {
        for (content in Content.entries) sheet(content) {
            checkThumb("$content at Half")
            scene.drag(Offset(Width / 2f, state.offset + 12f), Offset(Width / 2f, 60f), steps = 20)
            scene.frames(90)
            assertEquals(SheetDetent.Expanded, state.currentDetent, "$content: dragged by its top, the sheet went to ${state.currentDetent}")
            checkThumb("$content at full height")
        }
    }

    /** And while the sheet is held between the two. */
    @Test
    fun theThumbFollowsTheSheetBetweenItsDetents() {
        sheet {
            val from = Offset(Width / 2f, state.offset + 12f)
            scene.drag(from, from.copy(y = from.y - 160f), steps = 12, release = false)
            scene.frames(4)
            assertTrue(state.offset < Height / 2f - 100f, "the sheet did not follow the drag, so this measured nothing")
            checkThumb("held between Half and full height")
            scene.release(from.copy(y = from.y - 160f))
            scene.frames(60)
        }
    }

    /** At the end of its list, the thumb is on the screen, and short of the foot while there is more below it. */
    @Test
    fun atTheEndOfItsListAHalfSheetsThumbIsStillOnTheScreen() {
        for (content in Content.entries) sheet(content) {
            toEnd()
            scene.frames(60)
            val thumb = thumbRows(scene.frame())
            assertTrue(thumb != null, "$content: scrolled to the end at Half, the thumb was not on the screen at all")
            assertTrue(
                thumb.last < Height - 8,
                "$content: the thumb ran to ${thumb.last}, the foot of the screen, with ${list().bottom - Height}px of list still below it",
            )
        }
    }

    @Test
    fun rightToLeftTheThumbIsOnTheLeftAndTheSameLength() {
        sheet(direction = LayoutDirection.Rtl) {
            checkThumb("right to left", fromRight = false)
        }
    }

    @Test
    fun underReducedMotionTheWheelStillScrollsAndOpensAtTheEnd() {
        sheet(reduceMotion = true) {
            val top = state.offset
            wheel(4, down = true)
            scene.frames(30)
            assertTrue(scroll() > 0f, "the wheel never scrolled the list")
            assertEquals(top, state.offset, 0.5f, "the wheel moved the sheet")
            toEnd()
            scene.frames(30)
            wheel(2, down = true)
            scene.frames(60)
            assertEquals(SheetDetent.Expanded, state.currentDetent, "a wheel past the end left the sheet at ${state.currentDetent}")
        }
    }

    private fun Run.checkThumb(where: String, fromRight: Boolean = true) {
        val image = scene.frame()
        val thumb = thumbRows(image, fromRight)
        assertTrue(thumb != null, "$where: no thumb was drawn")
        val shownTop = list().top.coerceAtLeast(0f)
        val shownFoot = list().bottom.coerceAtMost(Height.toFloat())
        val shown = shownFoot - shownTop
        val content = Rows * RowDp * 2f
        // The track is what is shown, less the bar's margin at each end.
        val expected = (shown - 2 * Margin) * shown / content
        val length = (thumb.last - thumb.first + 1).toFloat()
        assertTrue(
            abs(length - expected) <= Tolerance,
            "$where: the thumb is ${length}px long; ${shown}px of a ${content}px list shown makes it ${expected}px",
        )
        assertTrue(thumb.last <= shownFoot, "$where: the thumb runs to ${thumb.last}, below the sheet's foot at $shownFoot")
    }

    /** The rows the thumb's colour is drawn in, at the side it sits on. */
    private fun thumbRows(image: BufferedImage, fromRight: Boolean = true): IntRange? {
        var first = -1
        var last = -1
        val xs = if (fromRight) (image.width - 24 until image.width) else (0 until 24)
        for (y in 0 until image.height) {
            if (xs.any { isThumb(image.getRGB(it, y)) }) {
                if (first < 0) first = y
                last = y
            }
        }
        return if (first < 0) null else first..last
    }

    private fun isThumb(rgb: Int): Boolean {
        val r = rgb shr 16 and 0xFF
        val g = rgb shr 8 and 0xFF
        val b = rgb and 0xFF
        return r - g > 60 && abs(g - b) < 30
    }

    private companion object {
        const val Width = 600
        const val Height = 900
        const val Rows = 24
        const val RowDp = 40
        val Thumb = Color(0xFFFF0000)

        /** `Theme.spacing.xxs` at each end of the track, in pixels at a density of 2. */
        const val Margin = 8f

        /** Antialiasing on the thumb's rounded caps. */
        const val Tolerance = 4f
    }
}
