package io.kontour.ui.docs

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.kontour.ui.catalog.CatalogSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image

/**
 * That the masthead's switches reach inside the gallery.
 *
 * `#/gallery` is a route on this site, and it used to be a boundary: `Catalog()`
 * installs a `KontourTheme` of its own, and a nested theme **does not inherit**
 * — every parameter it is not given re-resolves from the platform. So the site's
 * dark switch drew a light gallery, and the note at the route said so and called
 * it deliberate.
 *
 * The gallery keeping its own theme *is* deliberate: four standalone hosts need
 * one, and nesting inside the site's put a documentation sidebar beside the
 * gallery's nav rail. What was wrong is that the two themes were given different
 * values. They now read one [CatalogSettings].
 *
 * ### What this measures
 *
 * The gallery's own content, not the shell around it. A dark toggle that only
 * repainted the masthead would pass a whole-frame comparison, because the
 * masthead is in the frame — so the sample is taken well inside the content
 * area, below the top bar and to the right of the nav rail.
 */
class SiteGallerySettingsTest {

    private val width = 1200
    private val height = 900
    private val density = 2f

    private fun galleryPixels(dark: Boolean): IntArray {
        val settings = CatalogSettings().apply { this.dark = dark }
        navigate(Route.Gallery)
        try {
            ImageComposeScene(
                width = width,
                height = height,
                density = Density(density),
                content = { Site(settings) },
            ).use { scene ->
                // Several frames: the gallery's nav indicator and the theme
                // cross-fade both settle over a few, and a first frame catches
                // them mid-flight in a way that is a difference for the wrong
                // reason.
                var image: Image = scene.render(0L)
                repeat(8) { image = scene.render(16_000_000L * (it + 1)) }
                return sample(image)
            }
        } finally {
            navigate(Route.Home)
        }
    }

    /**
     * A grid of pixels from the gallery's content, as ARGB.
     *
     * **The window matters and the first version of it was wrong.** At 1200dp
     * the site keeps its documentation sidebar, which ends around x=660; a
     * sample at 45% of the width lands in that sidebar, which is the *site's*
     * shell and repaints with the site's theme whether or not the gallery hears
     * about it. That version passed with the wiring cut, which is the only
     * reason it was found.
     *
     * So the window is the right-hand region, inset from the gallery's own top
     * bar and from the scrollbar that can appear at the far edge.
     */
    private fun sample(image: Image): IntArray {
        val bitmap = Bitmap.makeFromImage(image)
        val left = 760
        val top = 300
        return IntArray(64) { i ->
            bitmap.getColor(left + (i % 8) * 40, top + (i / 8) * 60)
        }
    }

    @AfterTest
    fun backToHome() = navigate(Route.Home)

    @Test
    fun theSiteSDarkSwitchReachesInsideTheGallery() {
        val light = galleryPixels(dark = false)
        val dark = galleryPixels(dark = true)

        val differing = light.indices.count { light[it] != dark[it] }
        assertTrue(
            differing > 32,
            "only $differing of 64 sampled pixels inside the gallery changed when " +
                "the site's dark setting was flipped. The gallery's own " +
                "`KontourTheme` is not being given the site's settings, so it " +
                "re-resolved dark mode from the platform and drew the same thing " +
                "twice",
        )
    }

    @Test
    fun theSameSettingDrawsTheSameGallery() {
        // The control. Two runs that agree are what makes the run above a
        // difference rather than noise — a scene that rendered differently every
        // time would satisfy the first test for no reason at all.
        assertTrue(
            galleryPixels(dark = true).contentEquals(galleryPixels(dark = true)),
            "two renders of the gallery at the same setting disagreed, so the " +
                "test above is measuring something that moves on its own",
        )
    }

    private inline fun <T> ImageComposeScene.use(block: (ImageComposeScene) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
