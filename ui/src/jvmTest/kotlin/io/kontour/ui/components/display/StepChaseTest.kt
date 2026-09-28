package io.kontour.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable

/**
 * A working step pressed on mid-chase draws nothing in the next step until its
 * turn.
 *
 * Reported from a phone as the working animation "flickering in and out" while
 * the next step's fill played. The step moved on in composition, and the chase
 * was reset by an effect that ran after it — so for a frame the next step was
 * drawn at the old step's phase, halfway through a cycle, then blank until the
 * fill had run, then its own bars from the start.
 *
 * ### Why a scene of its own
 *
 * Neither of the usual harnesses could see it. Both start an effect the moment
 * it is launched, inside the frame and before that frame is drawn, and the flash
 * lives in exactly the gap that closes. A phone runs a new effect after the frame
 * that launched it has been drawn. So this scene is given a dispatcher that only
 * queues, and the queue is run after each frame is drawn — the order a device
 * keeps. Against the chase as it was, this fails on the first frame after the
 * press.
 */
class StepChaseTest {

    @Test
    fun pressingOnMidChaseDrawsNothingInTheNextStepUntilItsTurn() {
        var current by mutableStateOf(2)
        val afterTheFrame = AfterTheFrame()
        val scene = ImageComposeScene(
            width = SceneWidth,
            height = SceneHeight,
            density = Density(2f),
            coroutineContext = afterTheFrame,
        ) {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(10.dp)) {
                    StepProgress(
                        current = current,
                        total = Steps,
                        working = true,
                        colour = Color.Black,
                        trackColour = Color.White,
                        height = 12.dp,
                        modifier = Modifier.width(RowWidth.dp),
                    )
                }
            }
        }
        var nanos = 0L
        fun frame(): PixelMap {
            nanos += 16_000_000L
            val pixels = scene.render(nanos).toComposeImageBitmap().toPixelMap()
            afterTheFrame.drain()
            return pixels
        }
        try {
            afterTheFrame.drain()
            // Well into a cycle, where the bars are at their widest.
            repeat(50) { frame() }
            assertTrue(frame().inkIn(segment = 1) > 0, "the working step's bars should be showing before it moves on")

            current = 3
            val next = mutableListOf<Int>()
            val finished = mutableListOf<Int>()
            repeat(40) {
                val pixels = frame()
                next += pixels.inkIn(segment = 2)
                finished += pixels.inkIn(segment = 1)
            }
            val firstLit = next.indexOfFirst { it > 0 }
            assertTrue(firstLit >= 0, "the bars never started in the next step: $next")
            val full = finished.max()
            assertTrue(
                finished[firstLit] >= full * 0.95f,
                "bars were drawn in the next step on frame $firstLit after the press, while the step before " +
                    "was ${finished[firstLit]} of $full columns full — they flashed in before its turn: $next",
            )
        } finally {
            scene.close()
        }
    }

    /** Inked columns across the middle of segment [segment]. */
    private fun PixelMap.inkIn(segment: Int): Int {
        val left = 20f
        val gap = 8f
        val width = (RowWidth * 2f - gap * (Steps - 1)) / Steps
        val from = (left + segment * (width + gap)).toInt()
        val to = (from + width).toInt()
        val y = 20 + 12
        return (from until to).count { x -> this[x, y].red < 0.5f }
    }

    /**
     * Holds every task until [drain], so what a frame launches runs after that
     * frame is drawn.
     */
    private class AfterTheFrame : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun drain() {
            var ran = 0
            while (queue.isNotEmpty()) {
                queue.removeFirst().run()
                check(++ran < 100_000) { "the queue never emptied" }
            }
        }
    }

    private companion object {
        const val Steps = 5
        const val RowWidth = 250
        const val SceneWidth = 540
        const val SceneHeight = 64
    }
}
