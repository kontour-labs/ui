package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.CalendarMonth
import io.kontour.ui.components.datetime.RangePosition
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Replacing a range takes the old one away at once.
 *
 * It faded it out instead. Every cell of the old range eased its fill away over
 * a tween, so choosing new dates dragged a trail of grey circles behind it — a
 * near-black cap on its way to nothing is grey for most of the animation, and
 * there is one on every day the range used to cover. That is the reported
 * "flashes grey circles on the old range", and the animation causing it was
 * never worth watching: a cell arriving is a stone being laid down, a cell
 * leaving is just gone.
 *
 * ### Measured against the settled frame, not against a colour
 *
 * The assertion is that **the first frame after the change already looks like
 * the last one**, within the band the old range occupied. That needs no colour
 * written down and no arithmetic about where a cell is: if anything in that band
 * is still moving, its pixels differ from where they end up.
 *
 * The two ranges are put in different weeks so the band holding the old one is
 * not also holding the new one arriving — which does animate, and should.
 */
class CalendarRangeFadeTest {

    @Test
    fun theOldRangeIsGoneOnTheNextFrame() {
        var range by mutableStateOf(FirstWeek)
        val scene = ImageComposeScene(width = 700, height = 700, density = Density(2f)) {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    CalendarMonth(
                        // August 2026 lays out as 1–2, 3–9, 10–16, 17–23,
                        // 24–30, 31. The two ranges below are three rows apart.
                        month = LocalDate(2026, 8, 1),
                        isSelected = { false },
                        onSelectedChange = {},
                        modifier = Modifier.padding(20.dp),
                        rangePositionOf = { date ->
                            when (date.day) {
                                range.first -> RangePosition.Start
                                range.last -> RangePosition.End
                                in range -> RangePosition.Middle
                                else -> RangePosition.None
                            }
                        },
                    )
                }
            }
        }

        var nanos = 0L
        fun shot(): BufferedImage {
            nanos += 16_000_000L
            val png = requireNotNull(scene.render(nanos).encodeToData(EncodedImageFormat.PNG))
            return requireNotNull(ImageIO.read(png.bytes.inputStream()))
        }

        try {
            repeat(6) { shot() }
            val before = shot()
            val band = tintBand(before) ?: error("no range tint was drawn to watch")

            range = SecondWeek
            val next = shot()
            repeat(Frames) { shot() }
            val settled = shot()

            val moving = differences(next, settled, band)
            assertTrue(
                moving <= Tolerance,
                "one frame after the range moved, $moving pixels of the band the " +
                    "old range occupied (rows ${band.first}..${band.last}) still " +
                    "differ from where they finish — the old range is animating " +
                    "itself out",
            )
        } finally {
            scene.close()
        }
    }

    /** The rows of the image the range's tint covers, grown to the whole cell. */
    private fun tintBand(image: BufferedImage): IntRange? {
        var top = Int.MAX_VALUE
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                // Bluer than it is red, and light: the range tint, and nothing
                // else this calendar draws.
                if ((rgb and 0xFF) - (rgb shr 16 and 0xFF) <= 4) continue
                if (luminance(rgb) <= 190) continue
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (bottom < 0) return null
        // The tint stops short of the cell's own top and bottom, and the caps at
        // each end are taller than it. Twelve pixels covers both.
        return maxOf(0, top - 12)..minOf(image.height - 1, bottom + 12)
    }

    /** How many pixels of [rows] differ between the two frames. */
    private fun differences(a: BufferedImage, b: BufferedImage, rows: IntRange): Int {
        var differing = 0
        for (y in rows) {
            for (x in 0 until a.width) {
                val p = a.getRGB(x, y)
                val q = b.getRGB(x, y)
                if (
                    abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)) > 8 ||
                    abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)) > 8 ||
                    abs((p and 0xFF) - (q and 0xFF)) > 8
                ) {
                    differing++
                }
            }
        }
        return differing
    }

    private fun luminance(rgb: Int): Int =
        ((rgb shr 16 and 0xFF) * 30 + (rgb shr 8 and 0xFF) * 59 + (rgb and 0xFF) * 11) / 100

    private companion object {
        val FirstWeek = 3..9
        val SecondWeek = 24..30

        /** Long enough for a tween to finish. */
        const val Frames = 20

        /** A scattering of antialiased edge pixels. The defect was tens of thousands. */
        const val Tolerance = 200
    }
}
