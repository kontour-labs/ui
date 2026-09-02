package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.kontourSizing
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.fail

/**
 * Every page at every window size class the library documents.
 *
 * `WindowSizeClass` names four — Compact under 600dp, Medium under 840, Expanded
 * under 1200, Large above — and until now **two of them had never been
 * rendered**. The phone pages are 360dp and the gallery shell is drawn at 760
 * and 2400; nothing had ever drawn a page at 700dp or 1000dp, which is a small
 * tablet and a split-screen desktop window, and between them they are most of
 * what is not a phone.
 *
 * ### Horizontal overflow only
 *
 * These pages scroll, so being tall is what they are for. Being *wide* is not: a
 * page wider than its window has content nobody can reach, because there is no
 * sideways to scroll. That is the same rule `PhoneWidthTest` applies at 360dp,
 * asked at the three widths it does not cover.
 *
 * ### And it writes contact sheets
 *
 * The measurable half of "looks correct at every size" is thin — nothing
 * overflows, nothing crashes. The rest needs eyes, and this is where the eyes
 * get something to look at: every page at every class, side by side, written to
 * [ContactSheets]. Round 13 found two defects by looking at a filmstrip that
 * every assertion had passed, and this round found a dialog drawing on top of
 * itself the same way.
 */
class WindowClassTest {

    @Test
    fun everyPageFitsEveryWindowClass() {
        val failures = mutableListOf<String>()
        File(ContactSheets).mkdirs()

        for (page in pages) {
            val shots = mutableListOf<Pair<String, BufferedImage>>()

            for (window in Windows) {
                val frame = try {
                    render(page.content, window)
                } catch (error: Throwable) {
                    failures += "${page.title} at ${window.name} — threw " +
                        "${error::class.simpleName}: ${error.message?.lineSequence()?.firstOrNull()}"
                    continue
                }
                shots += window.name to frame

                val spill = frame.rightmostInk() - (window.width * Density - 1)
                if (spill > Slack) {
                    failures += "${page.title} at ${window.name} — ink runs ${spill}px " +
                        "past the right edge of a ${window.width}dp window"
                }
            }

            if (shots.isNotEmpty()) writeSheet(page.title.slug(), shots)
        }

        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} failures across ${pages.size} pages and " +
                    "${Windows.size} window classes:\n" +
                    failures.joinToString("\n") { "  · $it" }
            )
        }
    }

    private fun render(
        content: @Composable (Modifier) -> Unit,
        window: Window,
    ): BufferedImage {
        var frame: BufferedImage? = null
        Scene(
            width = window.width * Density,
            height = SheetHeight,
            density = Density.toFloat(),
        ) {
            KontourTheme(
                reduceMotion = true,
                sizing = kontourSizing(ContrastLevel.Standard)
                    .copy(minTouchTarget = window.touchTarget.dp),
            ) {
                WindowSizeClassProvider(Modifier.fillMaxSize()) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Theme.colours.background)
                                .verticalScroll(rememberScrollState())
                        ) {
                            content(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }.use { frame = it.frames(Frames) }
        return requireNotNull(frame)
    }

    /** The x of the last inked column, or -1 for a blank frame. */
    private fun BufferedImage.rightmostInk(): Int {
        val page = getRGB(1, 1)
        for (x in width - 1 downTo 0) {
            for (y in 0 until height) {
                if (getRGB(x, y) != page) return x
            }
        }
        return -1
    }

    /** One page at every class, side by side, scaled to something scannable. */
    private fun writeSheet(slug: String, shots: List<Pair<String, BufferedImage>>) {
        val scale = 2
        val height = shots.maxOf { it.second.height } / scale
        val width = shots.sumOf { it.second.width / scale + Gutter }
        val sheet = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = java.awt.Color(0xE0, 0x10, 0xE0)
        g.fillRect(0, 0, width, height)
        var x = 0
        for ((_, image) in shots) {
            val w = image.width / scale
            g.drawImage(image, x, 0, w, image.height / scale, null)
            x += w + Gutter
        }
        g.dispose()
        ImageIO.write(sheet, "png", File(ContactSheets, "$slug.png"))
    }

    private fun String.slug(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** One window class, and the touch minimum a device of that size implies. */
    private class Window(val name: String, val width: Int, val touchTarget: Int)

    private companion object {
        /**
         * One width inside each of `WindowSizeClass`'s four bands.
         *
         * Compact and Large already had pictures — 360dp and the 2400px shell.
         * Medium and Expanded had none at all, and they are a small tablet and a
         * split-screen window, which between them are most of what is not a
         * phone.
         *
         * The touch minimum follows the device the width implies: a phone and a
         * tablet are touched, a desktop window that size is pointed at.
         */
        val Windows = listOf(
            Window("Compact 360", 360, 48),
            Window("Medium 700", 700, 48),
            Window("Expanded 1000", 1000, 44),
            Window("Large 1400", 1400, 24),
        )

        const val Density = 2

        /** Tall enough to show the head of a page without being a golden. */
        const val SheetHeight = 2400

        const val Frames = 12

        /** A pixel of antialiasing on a full-bleed background. */
        const val Slack = 1

        const val Gutter = 8

        /** Written for review, not compared: these are for looking at. */
        const val ContactSheets = "build/contact-sheets"
    }
}
