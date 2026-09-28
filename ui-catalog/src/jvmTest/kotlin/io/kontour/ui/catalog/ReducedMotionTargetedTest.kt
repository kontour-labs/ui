package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.CarouselState
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.ModalSideSheet
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The largest movements in the library, each asked for by code rather than by a
 * finger, do not travel under reduced motion.
 *
 * A sheet sliding the height of the window, a side sheet sliding its own width, a
 * carousel page sliding the width of the carousel: the setting's whole reason for
 * existing, and each used to run at full amplitude whatever it said. Each is
 * watched frame by frame after it is asked for, with the setting on and — as a
 * control, so the test is not passing because nothing ever moves — with it off.
 */
class ReducedMotionTargetedTest {

    @Test
    fun aBottomSheetOpensWhereItRests() {
        val (reduced, full) = travelOf { open, report ->
            ModalBottomSheet(visible = open, onDismissRequest = {}) {
                Box(Modifier.fillMaxWidth().height(200.dp).reportBounds { report(it.top) })
            }
        }
        assertEquals(0f, reduced, "a sheet asked to open under reduced motion spent ${reduced} frames on the way")
        assertTrue(full >= 3f, "with full motion the sheet spent ${full} frames on the way, so this measures nothing")
    }

    @Test
    fun aSideSheetOpensWhereItRests() {
        val (reduced, full) = travelOf { open, report ->
            ModalSideSheet(visible = open, onDismissRequest = {}) {
                Box(Modifier.fillMaxSize().reportBounds { report(it.left) })
            }
        }
        assertEquals(0f, reduced, "a side sheet asked to open under reduced motion spent ${reduced} frames on the way")
        assertTrue(full >= 3f, "with full motion the side sheet spent ${full} frames on the way")
    }

    @Test
    fun aCarouselAskedForAPageIsThereAtOnce() {
        assertEquals(3, pageAfterTwoFrames(reduceMotion = true), "under reduced motion the carousel slid to its page")
        assertTrue(pageAfterTwoFrames(reduceMotion = false) < 3, "with full motion the carousel arrived at once, so this measures nothing")
    }

    /**
     * How many frames one edge of the content spent in transit after it was asked
     * to open, with the setting on and then off.
     */
    private fun travelOf(content: @androidx.compose.runtime.Composable (Boolean, (Float) -> Unit) -> Unit): Pair<Float, Float> {
        fun run(reduceMotion: Boolean): Float {
            var open by mutableStateOf(false)
            val edges = mutableListOf<Float>()
            var latest = Float.NaN
            Scene(width = 800, height = 1200, reduceMotion = reduceMotion) {
                OverlayHost(Modifier.fillMaxSize()) {
                    content(open) { latest = it }
                }
            }.use { scene ->
                scene.frames(5)
                open = true
                repeat(60) {
                    scene.frame()
                    if (!latest.isNaN()) edges += latest
                }
            }
            if (edges.isEmpty()) return 0f
            // Frames spent between where the sheet was placed before it opened
            // and where it rests: a slide passes through dozens; a sheet that
            // appears in place, through none.
            val start = edges.first()
            val rest = edges.last()
            return edges.count { abs(it - start) > Tolerance && abs(it - rest) > Tolerance }.toFloat()
        }
        return run(reduceMotion = true) to run(reduceMotion = false)
    }

    private fun pageAfterTwoFrames(reduceMotion: Boolean): Int {
        var state: CarouselState? = null
        var go by mutableStateOf(false)
        Scene(width = 800, height = 400, reduceMotion = reduceMotion) {
            val carousel = rememberCarouselState { 6 }
            state = carousel
            LaunchedEffect(go) { if (go) carousel.animateScrollToPage(3) }
            Carousel(state = carousel, contentDescription = "Stops", modifier = Modifier.fillMaxSize()) { page ->
                Text("Page $page")
            }
        }.use { scene ->
            scene.frames(5)
            go = true
            scene.frames(2)
            return requireNotNull(state).currentPage
        }
    }

    private companion object {
        /** Pixels: an edge that settles within a device pixel or two has not travelled. */
        const val Tolerance = 4f
    }
}
