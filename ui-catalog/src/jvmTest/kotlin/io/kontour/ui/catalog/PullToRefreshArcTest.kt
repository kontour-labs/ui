package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.rememberPullToRefreshState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The ring the finger draws is the one the spinner carries on.
 *
 * A full pull used to close the circle, and the [io.kontour.ui.components.display.Spinner]
 * that replaced it on release opens at a third of one — so the instant the
 * gesture committed, the thing the user had just finished filling emptied.
 * Reported as the ring filling rather than stopping at an arc and continuing
 * from there.
 *
 * ### Counted rather than measured
 *
 * The claim is about a *handover*, so the assertion is about two frames rather
 * than about any one shape: the last frame of the pull and the first of the
 * spin, with the ink in each counted. A closed ring against an opening arc is
 * three times the ink, and no arithmetic about radii or sweep angles is needed
 * to see it. Same stroke, same size, same colour on both sides, so anything left
 * over is the length of the arc.
 */
class PullToRefreshArcTest {

    @Test
    fun theArcHandsOverToTheSpinnerWithoutAStep() {
        var refreshing by mutableStateOf(false)
        var bounds = Rect.Zero
        var pulled = 0
        var spinning = 0

        Scene(width = 400, height = 600) {
            val pull = rememberPullToRefreshState()
            val rows = rememberLazyListState()
            // An opaque ground under everything. A pull moves the content down
            // and what it uncovers is the scene itself, which is transparent —
            // and transparent reads as black to `getRGB`, so counting dark
            // pixels without this counts the gap the gesture just opened, which
            // is seventy thousand of them and the ring is three hundred.
            Box(Modifier.fillMaxSize().background(Color.White))
            PullToRefresh(
                refreshing = refreshing,
                onRefresh = {},
                state = pull,
                modifier = Modifier.fillMaxSize().reportBounds { bounds = it },
            ) {
                LazyColumn(
                    state = rows,
                    modifier = Modifier.fillMaxSize().background(Color.White),
                ) {
                    items(40) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
            }
        }.use { scene ->
            scene.frames(4)
            assertTrue(bounds.height > 0f, "the container never reported a size")

            // Held at a full pull, one frame short of committing.
            val from = Offset(bounds.center.x, bounds.top + 40f)
            scene.press(from)
            repeat(24) { step ->
                scene.move(Offset(from.x, from.y + PastThreshold * (step + 1) / 24f))
                scene.frame()
            }
            pulled = scene.frames(6).darkPixels()

            // ...and the first frame of the spin that replaces it.
            refreshing = true
            spinning = scene.frames(2).darkPixels()
            scene.release(from)
        }

        assertTrue(
            pulled > 40 && spinning > 40,
            "one of the two states drew almost nothing — $pulled pixels pulled and " +
                "$spinning spinning — so the comparison below would be between two " +
                "kinds of empty",
        )
        val step = abs(pulled - spinning).toFloat() / maxOf(pulled, spinning)
        assertTrue(
            step < 0.35f,
            "the ring carried ${pulled}px of ink at a full pull and the spinner that " +
                "replaced it ${spinning}px — a jump of ${(step * 100).toInt()}%. The " +
                "arc is supposed to stop where the spinner opens and be handed over, " +
                "not fill the circle and empty again",
        )
    }
}

/** How much near-black ink is on this frame. The rings, and nothing else here. */
private fun java.awt.image.BufferedImage.darkPixels(): Int {
    var dark = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if ((getRGB(x, y) shr 16 and 0xFF) < 120) dark++
        }
    }
    return dark
}

private const val PastThreshold = 260f
