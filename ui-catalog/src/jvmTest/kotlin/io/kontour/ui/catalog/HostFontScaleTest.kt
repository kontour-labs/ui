package io.kontour.ui.catalog

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The device's text-size setting reaches the gallery's type.
 *
 * [HostDensityTest] pins the arithmetic; this pins that the gallery *calls* it.
 * The two are not the same check and only the second one would have caught the
 * defect: `hostDensity` did not exist, the expression was written inline in
 * `Catalog` and again in `Site`, and a unit test of a helper nobody calls proves
 * nothing about a host that stopped calling it.
 *
 * ### Why this could not have been a golden
 *
 * Every screenshot in this module renders through `Screenshot.render`, which
 * builds its scene with `Density(density)` — a font scale of exactly 1, because
 * that is `Density`'s default. So the entire golden suite is blind to the font
 * scale by construction: there is no frame anywhere in the repository where it
 * is anything but 1. Passing a second argument is the whole trick, and no test
 * had ever passed one.
 *
 * ### Ink, not layout — and a canvas tall enough for it to mean anything
 *
 * Bigger type means more pixels that are not the background. Counting them is
 * indifferent to where the text lands, which a position assertion would not be:
 * a larger scale reflows the page, so measuring a position measures the reflow.
 * The technique comes from `ComponentRenderTest.inkedFraction`.
 *
 * **The canvas has to be taller than the content, and the first version was
 * not.** At 760×1500 — the shell goldens' size — 130% type drew *fewer* inked
 * pixels than 100%: 92,139 against 93,026. Nothing was wrong; the About page
 * scrolls, so larger type pushes more of it past the bottom edge than the
 * larger glyphs add. The count was measuring how much of the page fits, which
 * is the opposite of the question. At 6000px nothing is cut and the number says
 * what it looks like it says.
 */
class HostFontScaleTest {

    private val width = 760
    private val height = 6000

    /**
     * How many pixels of the gallery are not its background, at [fontScale].
     *
     * The settings are pinned the way `CatalogScreenshotTest` pins them and for
     * the same reason — a render that depends on the machine's appearance
     * setting is not a measurement. `textScale` is deliberately *not* pinned:
     * null is Auto, which is the state under test.
     */
    private fun ink(fontScale: Float): Int {
        val settings = CatalogSettings().apply {
            dark = false
            highContrast = false
            reduceMotion = true
        }
        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(2f, fontScale),
            content = { Catalog(settings) },
        )
        val image: Image = try {
            var frame = scene.render(0L)
            repeat(5) { frame = scene.render(16_000_000L * (it + 1)) }
            frame
        } finally {
            scene.close()
        }
        val bitmap = Bitmap.makeFromImage(image)
        val background = bitmap.getColor(2, height - 2)
        var inked = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitmap.getColor(x, y) != background) inked++
            }
        }
        return inked
    }

    /**
     * A phone at 130% draws more ink than a phone at 100%.
     *
     * Nothing in the gallery is told to do this — `textScale` stays null, which
     * is Auto — so the only path from the scene's font scale to the type is
     * `Catalog` handing `LocalDensity.current` to `hostDensity` and installing
     * the result. Breaking that path is what the report from the phone was.
     */
    @Test
    fun theDevicesTextSizeReachesTheType() {
        val hundred = ink(1f)
        val hundredAndThirty = ink(1.3f)
        assertTrue(hundred > 0, "the gallery rendered nothing at all at 100%")
        assertTrue(
            hundredAndThirty > hundred * GrowthFloor,
            "the gallery drew $hundredAndThirty inked pixels at a device font " +
                "scale of 1.3 against $hundred at 1.0, a ratio of " +
                "${hundredAndThirty.toDouble() / hundred}. Type 30% larger " +
                "cannot draw the same amount of ink, so the device's font scale " +
                "is not reaching the type: `Catalog` installs a `LocalDensity` " +
                "of its own, and if it builds one that ignores the incoming " +
                "`fontScale` then every phone renders at exactly 100% no matter " +
                "what its accessibility settings say.",
        )
    }

    private companion object {
        /**
         * How much more ink 130% type must draw than 100%.
         *
         * Measured at **1.485** — 423,381 inked pixels against 285,098 — which
         * is above 1.3 rather than below it, because a glyph grows in two
         * dimensions and 1.3² is 1.69, pulled back down by the icons and rules
         * that do not scale at all.
         *
         * A discarded font scale gives exactly **1.0**: the two renders come out
         * byte-identical, 285,098 both times. So the floor only has to be above
         * 1, and 1.2 leaves the page most of the room it could want to reflow
         * into while staying a long way from the number that means "broken".
         */
        const val GrowthFloor = 1.2
    }
}
