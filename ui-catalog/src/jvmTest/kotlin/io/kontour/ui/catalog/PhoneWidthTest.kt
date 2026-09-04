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
import java.io.File
import javax.imageio.ImageIO
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
 * ### Android's touch target, not the JVM's
 *
 * `platformMinTouchTarget` is an `expect val`: **48dp on Android**, 44 on iOS
 * and web, and **24 on the JVM**, where the pointer is the assumed input and
 * WCAG's 24px floor is the right answer. Every value is correct for its
 * platform, and every test in this project runs on the JVM.
 *
 * That would be harmless if `Modifier.minimumTouchTarget()` only widened a hit
 * area. It does not — it is a `LayoutModifierNode` that expands the node's
 * *measured size* and centres the visual inside it. So on a phone the
 * twenty-odd components that call it each reserve 48dp, and here they reserved
 * 24 — which, since nothing in the library is smaller than 24dp, made the
 * modifier a no-op and the goldens blind to it. A `ButtonGroup`'s 1dp seam
 * renders as 9dp on a phone for exactly this reason, and the picture below said
 * it was fine.
 *
 * So these render with Android's number. It is still a simulation — no test
 * process on any machine loads `Accessibility.android.kt` — but it is the same
 * arithmetic, which is what was actually missing.
 *
 * ### Reviewed, not asserted — and that is not for want of trying
 *
 * There is no automatic check here that a page fits the width, and four
 * mechanisms were tried before giving up on one:
 *
 * - The harness's own `checkFits` measures at infinite constraints, which a
 *   vertically scrolling container refuses outright. It bails before the width.
 * - A maximum intrinsic width throws: `WindowSizeClassProvider` is a
 *   `BoxWithConstraints` and half these pages hold a lazy list, and intrinsics
 *   are unsupported through either.
 * - Counting ink against the right edge of the canvas finds shadows, which reach
 *   seventy dp past whatever casts them, as reliably as it finds overflow.
 * - Giving the page a phone-wide box inside a wider canvas and looking for what
 *   spills — Compose does not clip to bounds unless asked — finds nothing,
 *   because every showcase's root is a `Surface` and `Surface` clips.
 * - Reading the recorded PNG back and looking for ink against its right-hand
 *   column — the trick [bottomIsEmpty] uses successfully for height — reports
 *   nothing on a golden known to be overflowing. What overflows is usually
 *   inside a `horizontalScroll`, which clips at *its* bounds, and those sit a
 *   page margin short of the canvas. The evidence never reaches the edge.
 *
 * So the goldens below are the check, and they are meant to be *looked at*. A
 * test that cannot fail would be worse than none, which is the trap this file
 * has already fallen into twice.
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
        val cutOff = mutableListOf<String>()

        for (page in pages) {
            val golden = Screenshot.render(
                name = "phone/${page.title.slug()}",
                width = GoldenWidth,
                height = GoldenHeight,
                density = GoldenDensity,
                frames = Frames,
                // Tall is fine — these pages scroll. Wide is not, and used to be
                // waved through with it.
                allowVerticalOverflow = true,
            ) {
                PhoneTheme { page.content(Modifier.fillMaxWidth()) }
            }
            if (!bottomIsEmpty(golden)) cutOff += page.title
        }

        if (cutOff.isNotEmpty()) {
            fail(
                "${cutOff.size} phone golden(s) run off the bottom of the " +
                    "canvas, so the end of each page is in no picture anywhere: " +
                    cutOff.joinToString(", ") + ". Raise GoldenHeight past " +
                    "$GoldenHeight and re-record.",
            )
        }
    }

    /**
     * True when the last [TailBand] rows of [golden] are bare background.
     *
     * The check the width could not have. [PhoneWidthTest]'s KDoc lists four
     * mechanisms that failed to catch a page too *wide* for a phone, all of them
     * defeated by measuring the composition. Height is catchable because the
     * evidence survives into the file: a page longer than the canvas is *cut*,
     * and what is left on the final row is whatever was crossing it.
     *
     * That matters more than it sounds. At the 3000px this file shipped with,
     * **nine of thirteen** pages ended mid-specimen — the selection page's
     * steppers, the overlay page's whole second half — and the goldens were
     * green throughout, because a golden only ever compares itself to itself. A
     * picture that silently omits half its subject is the same trap as a test
     * that cannot fail, and this file's own KDoc had just finished warning about
     * that one.
     *
     * The background is taken as the image's most common colour rather than a
     * corner pixel: a corner can land inside a specimen, and a clipped page can
     * end on a run of solid colour that is uniform but is not the page.
     */
    private fun bottomIsEmpty(golden: File): Boolean {
        val image = ImageIO.read(golden) ?: return true
        val counts = HashMap<Int, Int>()
        for (y in 0 until image.height step BackgroundStride) {
            for (x in 0 until image.width step BackgroundStride) {
                val rgb = image.getRGB(x, y) and 0xFFFFFF
                counts[rgb] = (counts[rgb] ?: 0) + 1
            }
        }
        val background = counts.maxByOrNull { it.value }?.key ?: return true

        for (y in (image.height - TailBand).coerceAtLeast(0) until image.height) {
            for (x in 0 until image.width) {
                if ((image.getRGB(x, y) and 0xFFFFFF) != background) return false
            }
        }
        return true
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
            PhoneTheme { page.content(Modifier.fillMaxWidth()) }
        }.use { scene -> scene.frames(Frames) }
    }

    /**
     * A phone's theme, host and scroller, in the shape the app uses.
     *
     * [WindowSizeClassProvider] is here because without it `LocalWindowSizeClass`
     * falls back to a hardcoded 400 × 800dp — so a 360dp canvas reported itself
     * as 400dp wide. Both are `Compact` and nothing in `:ui` reads the raw
     * width yet, so it changed nothing today; it was still a lie, and this is
     * what the real shell does at `Catalog.kt`'s root.
     */
    @Composable
    private fun PhoneTheme(content: @Composable () -> Unit) {
        KontourTheme(
            reduceMotion = true,
            sizing = kontourSizing(ContrastLevel.Standard).copy(minTouchTarget = AndroidTouchTarget),
        ) {
            WindowSizeClassProvider(Modifier.fillMaxSize()) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Theme.colours.background)
                            .verticalScroll(rememberScrollState())
                    ) {
                        content()
                    }
                }
            }
        }
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

        /** `platformMinTouchTarget` on Android, which the JVM would otherwise report as 24dp. */
        val AndroidTouchTarget = 48.dp

        /**
         * The same 360dp, at the density the rest of the goldens use.
         *
         * [GoldenHeight] is 4500dp of page, which is a great deal more than a
         * phone and is the point: the tallest page here (overlays, with its
         * scrim-backed specimens) runs past 4000dp, and at the 3000px this file
         * shipped with, nine of the thirteen were cut off mid-specimen. The
         * blank tail costs almost nothing — it is one run-length in the PNG —
         * and `bottomIsEmpty` fails the run if a page ever outgrows it, which
         * is how each of the two rises since has been noticed.
         */
        const val GoldenWidth = 720
        const val GoldenHeight = 9000
        const val GoldenDensity = 2f

        /**
         * Rows at the foot of the canvas that must be bare background.
         *
         * Deep enough that a page ending exactly on the boundary still shows,
         * shallow enough to stay well clear of the last specimen on the longest
         * page — which at 8000 has 750px of clearance.
         */
        const val TailBand = 64

        /** Every 4th pixel each way is plenty to find the commonest colour. */
        const val BackgroundStride = 4
    }
}
