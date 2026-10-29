package io.kontour.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
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
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A text redacted a second time starts again from nothing drawn, and then
 * draws the bars of the string it holds now.
 *
 * Its bars come from its own layout, a pass after it is redacted, so the first
 * frame of a redaction draws nothing at all. What must never be drawn in that
 * frame is the bars of the *last* redaction: a short label's one bar standing
 * over a string that has since grown to two lines.
 *
 * Written 2026-10-29 for 0.5.1, when the bars stopped being kept by every `Text`
 * and came to be held only while it is redacted. They used to be emptied by hand
 * when a redaction ended; they are let go now, and this is what says the two
 * come to the same thing.
 */
class RedactionRestartTest {

    @Test
    fun aTextRedactedAgainStartsFromNothingDrawn() {
        var redacted by mutableStateOf(true)
        var text by mutableStateOf("Short")
        val scene = ImageComposeScene(width = Width, height = Height, density = Density(1f)) {
            // Reduced motion, so the bars are one flat ink and not a sweep.
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    CompositionLocalProvider(LocalContentColour provides Color.Black) {
                        Redacted(enabled = redacted) { Text(text) }
                    }
                }
            }
        }
        var nanos = 0L
        fun frame(): PixelMap {
            nanos += 16_000_000L
            return scene.render(nanos).toComposeImageBitmap().toPixelMap()
        }
        fun PixelMap.inked(): Int {
            var count = 0
            for (x in 0 until width) for (y in 0 until height) if (this[x, y] != Color.White) count++
            return count
        }
        try {
            repeat(3) { frame() }
            val shortBars = frame().inked()
            assertTrue(shortBars > 0, "the redacted text drew no bar at all, so this measured nothing")

            redacted = false
            text = "A string that is a great deal longer than the one before it"
            repeat(3) { frame() }

            redacted = true
            val first = frame().inked()
            assertTrue(
                first == 0,
                "the first frame of a second redaction drew $first pixels. It has not been " +
                    "laid out redacted yet, so anything it draws is the bars of the redaction " +
                    "before: a ${shortBars}-pixel bar for a string that is no longer there.",
            )

            repeat(2) { frame() }
            val longBars = frame().inked()
            assertTrue(
                longBars > shortBars,
                "the second redaction drew $longBars pixels against $shortBars for the short " +
                    "string, so its bars are not the new string's",
            )
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val Width = 600
        const val Height = 40
    }
}
