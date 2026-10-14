package io.kontour.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Everything in one redacted block shimmers as one surface, whenever it arrived.
 *
 * Every redacted `Text` and `Icon` ran an infinite transition of its own — a
 * hundred and twenty of them in a redacted list of forty rows, each its own
 * subscriber to the frame clock — and each one started from nought on the first
 * frame it was drawn. So a row composed later, scrolled in or loaded in, swept
 * out of step with the rows above it, and a list of placeholders read as a list
 * of unrelated flickers rather than one surface being lit. A `Redacted` block
 * keeps one sweep now, and everything in it draws from that.
 *
 * In a scene of its own because the shimmer is infinite: the usual harness waits
 * for idle, and a composition holding an infinite transition never is. See
 * `RedactionTest`, which runs under reduced motion for that reason.
 */
class RedactionPhaseTest {

    @Test
    fun aPlaceholderComposedLaterSweepsInStepWithTheOthers() {
        var second by mutableStateOf(false)
        val scene = ImageComposeScene(width = 200, height = 60, density = Density(1f)) {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    CompositionLocalProvider(LocalContentColour provides Color.Black) {
                        Redacted {
                            Column {
                                Box(Modifier.size(Width.dp, 20.dp).redacted(shape = RectangleShape))
                                Spacer(Modifier.height(10.dp))
                                if (second) Box(Modifier.size(Width.dp, 20.dp).redacted(shape = RectangleShape))
                            }
                        }
                    }
                }
            }
        }
        var nanos = 0L
        fun frame(): PixelMap {
            nanos += 16_000_000L
            return scene.render(nanos).toComposeImageBitmap().toPixelMap()
        }
        try {
            // A third of the way through a sweep, where the band is on the bar.
            repeat(20) { frame() }
            second = true
            var pixels = frame()
            repeat(3) { pixels = frame() }

            val first = (0 until Width).map { pixels[it, 10] }
            val later = (0 until Width).map { pixels[it, 40] }
            assertTrue(
                first.any { !it.near(first[0]) },
                "the first placeholder is one flat colour, so the band is not on it and this measured nothing",
            )
            val apart = (0 until Width).filter { !first[it].near(later[it]) }
            assertTrue(
                apart.isEmpty(),
                "a placeholder composed 320ms after the one above it sweeps out of step " +
                    "with it: ${apart.size} of $Width columns differ, first at x=${apart.firstOrNull()}. " +
                    "Each started a sweep of its own on the first frame it was drawn.",
            )
        } finally {
            scene.close()
        }
    }

    private fun Color.near(other: Color): Boolean =
        abs(red - other.red) < 0.01f && abs(green - other.green) < 0.01f && abs(blue - other.blue) < 0.01f

    private companion object {
        const val Width = 100
    }
}
