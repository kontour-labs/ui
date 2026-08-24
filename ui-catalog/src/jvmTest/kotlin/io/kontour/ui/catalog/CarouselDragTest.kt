package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.rememberCarouselState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A carousel is a stack of cards, so it can be pulled aside with a mouse.
 *
 * A `LazyRow` answers a finger and a wheel, and on desktop that is all of it.
 * That is the right convention for a list and the wrong one for a carousel:
 * grabbing a card and dragging it is the only gesture anyone tries, and until
 * now a desktop user had the indicator dots and nothing else.
 */
class CarouselDragTest {

    @Test
    fun aMouseDragTurnsThePage() {
        var page = 0
        var bounds = Rect.Zero

        Scene(width = 600, height = 300) {
            val carousel = rememberCarouselState { 4 }
            page = carousel.currentPage
            Box(Modifier.fillMaxSize()) {
                Carousel(
                    state = carousel,
                    contentDescription = "Stop photos",
                    modifier = Modifier.reportBounds { bounds = it },
                ) { index ->
                    Box(Modifier.fillMaxSize().background(if (index % 2 == 0) Color.Gray else Color.DarkGray))
                }
            }
        }.use { scene ->
            scene.frames(4)
            assertTrue(bounds.width > 0f, "the carousel never reported a size")
            scene.drag(
                from = bounds.alongX(0.8f),
                to = bounds.alongX(0.1f),
                steps = 20,
                pointer = PointerType.Mouse,
            )
            scene.frames(12)
        }

        assertEquals(1, page, "a mouse drag leftward left the carousel on page $page")
    }

    @Test
    fun aTouchDragStillTurnsOnePage() {
        // The list already handles touch, and an outer drag that handled it too
        // would carry the carousel two pages for one swipe.
        var page = 0
        var bounds = Rect.Zero

        Scene(width = 600, height = 300) {
            val carousel = rememberCarouselState { 4 }
            page = carousel.currentPage
            Box(Modifier.fillMaxSize()) {
                Carousel(
                    state = carousel,
                    contentDescription = "Stop photos",
                    modifier = Modifier.reportBounds { bounds = it },
                ) { index ->
                    Box(Modifier.fillMaxSize().background(if (index % 2 == 0) Color.Gray else Color.DarkGray))
                }
            }
        }.use { scene ->
            scene.frames(4)
            scene.drag(from = bounds.alongX(0.8f), to = bounds.alongX(0.1f), steps = 20)
            scene.frames(12)
        }

        assertEquals(1, page, "a one-page touch swipe left the carousel on page $page")
    }
}
