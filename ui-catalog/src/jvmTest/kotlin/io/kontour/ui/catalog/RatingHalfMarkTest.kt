package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import io.kontour.ui.components.selection.Rating
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Half marks are reachable, by pressing and by sliding.
 *
 * Reported as "near impossible to give a half-mark when half-marks are enabled,
 * especially on mobile", and separately that "half-marks don't pick up sliding".
 * Both had the same cause: the marks owned the taps and a mark can only mean
 * itself, so the only route to a half was a drag that had first crossed 16dp of
 * touch slop and then come back to the correct side of a star's midpoint.
 *
 * The row owns the gesture now and the score comes from where the press landed.
 * A five-mark rating in this scene is five 48dp targets, so the half of the
 * fourth is a region about 24dp wide — which is the other half of the report.
 */
class RatingHalfMarkTest {

    @Test
    fun pressingTheLeftOfAMarkGivesTheHalf() {
        var value by mutableStateOf(0f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            KontourTheme(reduceMotion = true) {
                Column(
                    Modifier.fillMaxSize().background(Color.White).padding(20.dp),
                ) {
                    Rating(
                        value = value,
                        contentDescription = "Your rating",
                        onValueChange = { value = it },
                        allowHalf = true,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)

            val y = bounds.center.y
            val cell = bounds.width / 5f

            // The left quarter of the fourth mark's cell: unambiguously its
            // left half, and well clear of the boundary with the third.
            scene.press(Offset(bounds.left + cell * 3.25f, y))
            scene.frames(1)
            val half = value
            scene.release(Offset(bounds.left + cell * 3.25f, y))
            scene.frames(2)

            assertEquals(
                3.5f,
                half,
                "a press on the left half of the fourth mark gave $half. That is " +
                    "the whole of the half-mark report: the mark under the finger " +
                    "answered for the whole gesture and a mark can only mean itself.",
            )

            // And the right half of the same cell is the whole mark, so the two
            // halves are a mapping rather than an accident of where the glyph is.
            scene.press(Offset(bounds.left + cell * 3.75f, y))
            scene.frames(1)
            val whole = value
            scene.release(Offset(bounds.left + cell * 3.75f, y))
            scene.frames(2)

            assertEquals(4f, whole, "the right half of the fourth mark gave $whole")
        }
    }

    @Test
    fun slidingPicksUpTheHalves() {
        val seen = mutableListOf<Float>()
        var value by mutableStateOf(0f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            KontourTheme(reduceMotion = true) {
                Column(
                    Modifier.fillMaxSize().background(Color.White).padding(20.dp),
                ) {
                    Rating(
                        value = value,
                        contentDescription = "Your rating",
                        onValueChange = {
                            value = it
                            seen += it
                        },
                        allowHalf = true,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)

            val y = bounds.center.y
            val cell = bounds.width / 5f

            scene.press(Offset(bounds.left + cell * 0.25f, y))
            scene.frames(1)
            // Across the row in quarter-cell steps, which lands in both halves
            // of every mark on the way.
            var step = 1
            while (step <= 18) {
                scene.move(Offset(bounds.left + cell * (0.25f + step * 0.25f), y))
                scene.frames(1)
                step++
            }
            scene.release(Offset(bounds.left + cell * 4.75f, y))
            scene.frames(2)

            assertTrue(
                seen.any { it % 1f != 0f },
                "a slide across the whole row never produced a half mark: $seen",
            )
            assertEquals(
                5f,
                value,
                "the slide finished on the right of the last mark and left the " +
                    "score at $value",
            )
        }
    }
}
