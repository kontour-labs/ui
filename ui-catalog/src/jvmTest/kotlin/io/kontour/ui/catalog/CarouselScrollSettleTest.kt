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
 * A trackpad push leaves the carousel on a page, not between two.
 *
 * ### This one guards a property rather than fixing a defect, and says so
 *
 * The report reads as "a horizontal scroll does not snap". It does. Traced
 * without any snapping code of our own, the offset sat at 73px for about ninety
 * milliseconds after the last notch and then animated to zero by itself, because
 * the platform runs the list's fling behaviour once a wheel gesture goes quiet.
 * Settling code was written for this, measured against its own removal, found to
 * change nothing, and deleted.
 *
 * What a push *does* inherit from this library is the snap threshold, and a push
 * ending short of it lands back where it began — which is what reads as a
 * carousel that will not move, and is fixed in `CarouselDetentTest` rather than
 * here. Notch count is no use for asserting that: a wheel gesture keeps
 * animating after the last notch, so where the row is when the decision happens
 * is not where the notches left it.
 *
 * So this asserts the part that is worth keeping: whatever a push does, the row
 * comes to rest **on** a page. It fails if the `flingBehavior` ever comes off
 * the list, which is the way this property would actually be lost.
 */
class CarouselScrollSettleTest {

    @Test
    fun aTrackpadPushComesToRestOnAPage() {
        var bounds = Rect.Zero
        var state: CarouselState? = null
        var pushedTo = 0
        var settledPage = -1
        var settledOffset = -1

        Scene(width = 600, height = 300) {
            val carousel = rememberCarouselState { 4 }
            state = carousel
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
            val list = requireNotNull(state).listState

            repeat(Notches) {
                scene.scroll(bounds.center, Offset(1f, 0f))
                scene.frame()
            }
            pushedTo = list.firstVisibleItemScrollOffset

            // Both halves are load-bearing. `isScrollInProgress` goes false in
            // the gap *between* the wheel's own animation and the snap that
            // follows it, so waiting on that alone catches the row mid-air —
            // which is exactly how the first version of this test passed
            // against a component with the snapping taken out.
            scene.renderUntil(timeoutMillis = 5_000) {
                !list.isScrollInProgress && list.firstVisibleItemScrollOffset == 0
            }
            settledPage = list.firstVisibleItemIndex
            settledOffset = list.firstVisibleItemScrollOffset
        }

        assertTrue(
            pushedTo > 0,
            "a sideways scroll moved the carousel not one pixel, so this test would " +
                "have passed whatever the component did — the instrument is wrong, " +
                "not the component",
        )
        assertEquals(
            0,
            settledOffset,
            "the carousel came to rest ${settledOffset}px into page $settledPage, " +
                "showing two half-cards with the indicator underneath claiming one " +
                "of them",
        )
    }

    private companion object {
        /** Enough to leave the row well between two pages. */
        const val Notches = 3
    }
}
