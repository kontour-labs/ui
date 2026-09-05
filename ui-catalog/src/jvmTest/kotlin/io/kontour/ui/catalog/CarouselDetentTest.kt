package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.CarouselState
import io.kontour.ui.components.display.rememberCarouselState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A card turns when the gesture says it turned, not when it is more than half
 * over.
 *
 * Compose's own snapping asks which page is *nearest*, which is a list's
 * question: with items smaller than the viewport, nearest is the one you are
 * mostly looking at. A carousel's page is the whole viewport, so nearest means
 * dragging past the middle of a full-width card before the gesture takes — and
 * a drag that ends at forty per cent slides all the way back to where it
 * started, having done nothing. Reported as the detent threshold being too high.
 *
 * ### The release carries no velocity, deliberately
 *
 * A quick flick already turns the page, because the platform's own provider
 * biases toward the direction of travel above a minimum fling velocity. That is
 * not the thing being asserted. So the finger holds still for a few frames
 * before letting go — the velocity tracker sees a run of samples that do not
 * move and decays to nothing — and what is left is the positional threshold on
 * its own.
 */
class CarouselDetentTest {

    @Test
    fun aDragPastAQuarterTurnsThePage() {
        assertEquals(
            1,
            pageAfterDragging(fraction = 0.40f),
            "a drag forty per cent of the way to the next page settled back on the " +
                "one it started from — a full-width card should not have to be " +
                "dragged past its own middle before the gesture counts",
        )
    }

    @Test
    fun aDragShortOfTheThresholdFallsBack() {
        assertEquals(
            0,
            pageAfterDragging(fraction = 0.12f),
            "a drag of an eighth of a page turned it — the threshold is meant to be " +
                "low, not absent, or a carousel would change page every time it was " +
                "brushed",
        )
    }

    @Test
    fun draggingBackReturnsToThePageBefore() {
        assertEquals(
            0,
            pageAfterDragging(fraction = 0.40f, from = 1, backwards = true),
            "a backward drag past the threshold did not go back — the threshold " +
                "counts from wherever the gesture began, and position alone cannot " +
                "say which end that was",
        )
    }

    /**
     * Drags [fraction] of a page across and reports where the carousel settles.
     *
     * Touch, not a mouse: the threshold lives in the list's fling behaviour, and
     * a mouse drag goes through the `draggable` wrapped around it instead.
     */
    private fun pageAfterDragging(
        fraction: Float,
        from: Int = 0,
        backwards: Boolean = false,
    ): Int {
        var page = 0
        var bounds = Rect.Zero
        var state: CarouselState? = null

        Scene(width = 600, height = 300) {
            val carousel = rememberCarouselState { 4 }
            state = carousel
            page = carousel.currentPage
            Box(Modifier.fillMaxSize()) {
                Carousel(
                    state = carousel,
                    contentDescription = "Stop photos",
                    modifier = Modifier.reportBounds { bounds = it },
                ) { index ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(if (index % 2 == 0) Color.Gray else Color.DarkGray)
                    )
                }
            }
        }.use { scene ->
            scene.frames(4)
            assertTrue(bounds.width > 0f, "the carousel never reported a size")

            if (from != 0) {
                scene.frames(1)
                requireNotNull(state).listState.requestScrollToItem(from)
                scene.frames(4)
                assertEquals(from, page, "the carousel would not start on page $from")
            }

            val start = bounds.center
            // Slop first: a scrollable eats the beginning of any touch drag, and
            // a threshold measured from the press point would be measuring the
            // wrong distance.
            val travel = bounds.width * fraction + SlopPx
            val direction = if (backwards) 1f else -1f
            scene.press(start)
            repeat(Steps) { step ->
                scene.move(Offset(start.x + direction * travel * (step + 1) / Steps, start.y))
                scene.frame()
            }
            val held = Offset(start.x + direction * travel, start.y)
            repeat(Held) { scene.move(held); scene.frame() }
            scene.release(held)
            scene.frames(90)
        }

        return page
    }

    private companion object {
        const val Steps = 20

        /** Frames spent still, so the release is a release and not a flick. */
        const val Held = 8

        /** Comfortably over touch slop at this scene's density. */
        const val SlopPx = 40f
    }
}
