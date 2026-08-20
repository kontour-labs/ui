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
                // Tall is fine — these pages scroll. Wide is not, and used to be
                // waved through with it.
                allowVerticalOverflow = true,
            ) {
                PhoneTheme { page.content(Modifier.fillMaxWidth()) }
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
                            .background(Theme.colors.background)
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

        /** The same 360dp, at the density the rest of the goldens use. */
        const val GoldenWidth = 720
        const val GoldenHeight = 3000
        const val GoldenDensity = 2f
    }
}
