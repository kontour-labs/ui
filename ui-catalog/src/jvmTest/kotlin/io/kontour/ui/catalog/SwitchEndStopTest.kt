package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.Switch
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A switch's thumb pushed into its end squashes, straight away, however fast it
 * got there.
 *
 * Reported from a phone: "if you touch it and swipe it almost instantly, the head
 * stays as a wide pill when you hit the end, and you have to drag it a bit further
 * to squash it". The squash was taken from the *press-stretched* width, so a thumb
 * held against the wall was still partly a press — held 20dp past the end it came
 * out barely narrower than at rest — and the press stretch is a bouncy spring: a
 * thumb swiped into the end the instant it was touched reached the wall before
 * that spring had grown, and then widened by an eighth while the finger pushed.
 *
 * Measured across the thumb's middle, with the finger held at the same point past
 * the end after a slow drag and after an instant swipe, at 16ms and 8ms frames.
 */
class SwitchEndStopTest {

    @Test
    fun aThumbPushedIntoItsEndSquashesAndStaysSquashed() {
        val failures = mutableListOf<String>()
        for (frame in listOf(16_000_000L, 8_000_000L)) {
            val slow = held(frame) { scene, b ->
                scene.drag(Offset(b.left + b.width * 0.3f, b.center.y), Offset(b.right + 60f, b.center.y), steps = 30, release = false)
            }
            val instant = held(frame) { scene, b ->
                scene.press(Offset(b.left + b.width * 0.3f, b.center.y))
                scene.move(Offset(b.right + 60f, b.center.y))
            }
            for ((name, run) in listOf("a slow drag" to slow, "an instant swipe" to instant)) {
                val (resting, widths) = run
                val rate = "${frame / 1_000_000}ms frames"
                if (widths.max() > resting - 5) {
                    failures += "$rate, $name: held past the end the thumb was ${widths.max()}px against $resting at rest — not squashed: $widths"
                }
                val widened = widths.zipWithNext { a, b -> b - a }.max()
                if (widened > 1) {
                    failures += "$rate, $name: the thumb widened by ${widened}px in a frame while pushed into the end: $widths"
                }
            }
            if (abs(slow.second.last() - instant.second.last()) > 1) {
                failures += "${frame / 1_000_000}ms frames: held at the same place, the thumb was ${slow.second.last()}px " +
                    "after a slow drag and ${instant.second.last()}px after an instant swipe"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** The thumb's resting width, and its width each frame while held past the end. */
    private fun held(frame: Long, gesture: (Scene, Rect) -> Unit): Pair<Int, List<Int>> {
        var checked by mutableStateOf(false)
        var bounds = Rect.Zero
        val widths = mutableListOf<Int>()
        var resting = 0
        Scene(width = 400, height = 200, density = 3f, frameNanos = frame) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                Switch(checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.reportBounds { bounds = it })
            }
        }.use { scene ->
            resting = scene.frames(20).thumb(bounds).count()
            gesture(scene, bounds)
            repeat((500_000_000L / frame).toInt()) { widths += scene.frame().thumb(bounds).count() }
        }
        return resting to widths
    }

    /** The thumb along the switch's middle: whatever differs from the track just inside its top edge. */
    private fun BufferedImage.thumb(switch: Rect): IntRange {
        val track = getRGB(switch.center.x.toInt(), switch.top.toInt() + 3)
        val row = switch.center.y.toInt()
        var first = -1
        var last = -1
        for (x in switch.left.toInt() until switch.right.toInt()) {
            val c = getRGB(x, row)
            if (abs((c shr 16 and 0xFF) - (track shr 16 and 0xFF)) > 40 ||
                abs((c shr 8 and 0xFF) - (track shr 8 and 0xFF)) > 40 ||
                abs((c and 0xFF) - (track and 0xFF)) > 40
            ) {
                if (first < 0) first = x
                last = x
            }
        }
        return if (first < 0) IntRange.EMPTY else first..last
    }
}
