package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.ToastCountdown
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.ToastHostState
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A toast that shows the time it has left: asked for as *"with toasts, i'd
 * like the option to have some sort of progress indicator showing when they
 * will disappear… a 'background wipe', as well as a progress bar down the
 * bottom"*. And it stands still while the pointer rests on the toast, whose
 * clock stops then — a countdown running out under the pointer reading it
 * would be the one thing worse than none.
 *
 * On the wall clock, as the toast's own clock is: see `ToastStackTest`.
 */
class ToastCountdownTest {

    private fun scene(countdown: ToastCountdown, duration: Duration = 6_000.milliseconds) = Scene(width = 600, height = 300) {
        val toasts = remember { ToastHostState() }
        OverlayHost(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.White))
            ToastHost(toasts, countdown = countdown)
            LaunchedEffect(Unit) { toasts.show("Trip saved to favourites", duration = duration) }
        }
    }

    /** The toast's dark capsule: its rows, top to bottom, and its columns. */
    private fun capsule(image: BufferedImage): Pair<IntRange, IntRange>? {
        var top = -1
        var bottom = -1
        var left = Int.MAX_VALUE
        var right = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (luminance(image.getRGB(x, y)) < 0.3f) {
                if (top < 0) top = y
                bottom = y
                left = minOf(left, x)
                right = maxOf(right, x)
            }
        }
        return if (top < 0) null else (top..bottom) to (left..right)
    }

    private fun luminance(rgb: Int): Float = ((rgb shr 16 and 0xff) + (rgb shr 8 and 0xff) + (rgb and 0xff)) / (3f * 255f)

    /** How much of the bar along the capsule's foot is lit: its light pixels on the row it is drawn on. */
    private fun barLength(image: BufferedImage): Int {
        val (rows, columns) = capsule(image) ?: return 0
        // Along the flat of the foot, clear of the round ends — past them is
        // the page, white — a few pixels up, inside the rim.
        val end = (rows.last - rows.first) / 2
        val flat = (columns.first + end)..(columns.last - end)
        return (rows.last - 8..rows.last - 3).maxOf { y -> flat.count { x -> luminance(image.getRGB(x, y)) > 0.5f } }
    }

    /** How much of the capsule's top band is washed: lighter than the capsule's own dark, nowhere near white. */
    private fun washLength(image: BufferedImage): Int {
        val (rows, columns) = capsule(image) ?: return 0
        val y = rows.first + (rows.last - rows.first) / 5
        val base = columns.minOf { x -> luminance(image.getRGB(x, y)) }
        return columns.count { x -> luminance(image.getRGB(x, y)).let { it > base + 0.06f && it < 0.6f } }
    }

    @Test
    fun aBarAlongTheFootShortensAsTheTimeRunsOut() {
        scene(ToastCountdown.Bar).use { scene ->
            val early = barLength(scene.frames(24))
            Thread.sleep(1_500)
            val later = barLength(scene.frames(2))
            assertTrue(early > 100, "no bar along the toast's foot: $early pixels")
            assertTrue(later < early - 20, "the bar did not shorten as the time ran: $early, then $later")
        }
    }

    @Test
    fun aWipeOfTheBackgroundDrawsBackAsTheTimeRunsOut() {
        scene(ToastCountdown.Wipe).use { scene ->
            val early = washLength(scene.frames(24))
            Thread.sleep(1_500)
            val later = washLength(scene.frames(2))
            assertTrue(early > 100, "no wash across the toast: $early pixels")
            assertTrue(later < early - 20, "the wash did not draw back as the time ran: $early, then $later")
        }
    }

    @Test
    fun noCountdownIsDrawnUnlessAskedFor() {
        scene(ToastCountdown.None).use { scene ->
            assertTrue(barLength(scene.frames(24)) < 20, "a bar was drawn with no countdown asked for")
        }
    }

    @Test
    fun aPinnedToastHasNoCountdown() {
        scene(ToastCountdown.Bar, duration = Duration.INFINITE).use { scene ->
            assertTrue(barLength(scene.frames(24)) < 20, "a pinned toast showed a countdown")
        }
    }

    @Test
    fun theCountdownStandsStillWhileTheMouseRestsOnTheToast() {
        scene(ToastCountdown.Bar, duration = 3_000.milliseconds).use { scene ->
            val first = scene.frames(24)
            val (rows, columns) = capsule(first) ?: error("no toast")
            val middle = Offset((columns.first + columns.last) / 2f, (rows.first + rows.last) / 2f)
            scene.move(Offset(middle.x, 5f), pointer = PointerType.Mouse)
            scene.move(middle, pointer = PointerType.Mouse)
            val resting = barLength(scene.frames(2))
            // Longer than the toast had left: it would have gone.
            Thread.sleep(3_200)
            val image = scene.frames(2)
            assertTrue(capsule(image) != null, "the toast ran out with the mouse resting on it")
            val after = barLength(image)
            assertTrue(abs(after - resting) <= 3, "the countdown ran on under the mouse: $resting, then $after")
        }
    }
}
