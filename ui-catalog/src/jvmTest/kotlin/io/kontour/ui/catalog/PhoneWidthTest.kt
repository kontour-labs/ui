package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.fail

/**
 * Every page of the gallery, drawn on a phone.
 *
 * Nothing in this project had ever done that. The shell golden renders at 380dp
 * but only ever shows the page that happens to be selected, so twelve of
 * thirteen pages were drawn by nothing narrower than 550dp — and the two that
 * crashed on a real phone were among them.
 *
 * ### A crash here is a *library* bug until proven otherwise
 *
 * The gallery is not the customer. A component that throws when it is drawn
 * narrow throws in anybody's app that puts it in a tight row, and the showcase
 * is only how it was found. So the fix belongs in `:ui` unless the specimen is
 * asking for something no caller reasonably would.
 *
 * ### 360 × 800, at three
 *
 * A Pixel-class phone in portrait: 1080 × 2400 physical pixels at density 3,
 * which is 360 × 800dp — the narrowest width the library claims to support and
 * the one most Android phones actually are. The height is generous on purpose:
 * these pages scroll, and a page cut off at 800dp would hide whatever is below
 * the fold from the very test meant to look at it.
 */
class PhoneWidthTest {

    @Test
    fun everyPageDrawsOnAPhone() {
        val broken = mutableListOf<String>()

        for (page in pages) {
            try {
                render(page)
            } catch (error: Throwable) {
                broken += "${page.title}: ${error::class.simpleName}: ${error.message}"
            }
        }

        if (broken.isNotEmpty()) {
            fail(
                "${broken.size} of ${pages.size} pages could not be drawn at " +
                    "360dp:\n" + broken.joinToString("\n") { "  · $it" },
            )
        }
    }

    @AfterTest
    fun assertGoldensMatched() = Screenshot.assertAllMatched()

    /**
     * A picture of every page on a phone, so the *silent* half is visible too.
     *
     * Not throwing is the floor, not the bar. The crash that started this round
     * was a `Switch` handed zero width by a row that had run out — fixing the
     * throw leaves a switch nobody can see, which is still wrong and which no
     * assertion about exceptions would ever mention. These are the first images
     * of this library at 360dp, and they are meant to be looked at.
     *
     * Rendered at density 2 rather than the 3 above: layout is in dp and is
     * identical either way, and thirteen images at 3× is a great many bytes for
     * no extra information.
     */
    @Test
    fun everyPageIsAPictureOfItselfOnAPhone() {
        for (page in pages) {
            Screenshot.render(
                name = "phone/${page.title.slug()}",
                width = GoldenWidth,
                height = GoldenHeight,
                density = GoldenDensity,
                frames = Frames,
                // These pages scroll; a page taller than the canvas is the norm
                // here rather than a fault.
                allowOverflow = true,
            ) {
                KontourTheme(reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Theme.colors.background)
                                .verticalScroll(rememberScrollState())
                        ) {
                            page.content(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    /**
     * Draws one page, in the shape the app draws it.
     *
     * Inside an [OverlayHost] and a scrolling column, because that is what the
     * catalog puts around a page and some specimens reach for the host the
     * moment they compose. Bare, they would be testing something the app never
     * runs.
     */
    private fun render(page: Page) {
        Scene(width = PhoneWidth, height = PhoneHeight, density = PhoneDensity) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                ) {
                    page.content(Modifier.fillMaxWidth())
                }
            }
        }.use { scene -> scene.frames(Frames) }
    }

    /** `Date & time` → `date-time`, so the file names stay tidy. */
    private fun String.slug(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private companion object {
        /** 360 × 800dp at density 3 — a Pixel-class phone in portrait. */
        const val PhoneWidth = 1080
        const val PhoneHeight = 2400
        const val PhoneDensity = 3f

        /**
         * Long enough for every entry animation to have started.
         *
         * A specimen that throws on its first frame would be caught by one, but
         * several of these animate something in — a selection indicator settling,
         * a marker arriving — and a crash a few frames later is still a crash.
         */
        const val Frames = 12

        /** The same 360dp, at the density the rest of the goldens use. */
        const val GoldenWidth = 720
        const val GoldenHeight = 3000
        const val GoldenDensity = 2f
    }
}
