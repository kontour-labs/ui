package io.kontour.ui.docs

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.fail

/**
 * Draws every page of the site at every window size class, and looks at it.
 *
 * ### Why this exists
 *
 * The site shipped with a landing page that threw on any window narrower than
 * 600dp, `ProseWidth` that had never once applied, and half its pages showing
 * nothing at all. None of it was caught, and the reason is structural rather
 * than anyone's oversight: `:ui-docs` was a `wasmJs`-only module, so it had no
 * test source set that could run on this machine. There was nowhere to put a
 * test. Every gate in the repository was green while the site was unreadable.
 *
 * So the module gained a `jvm` target and the shell moved to `commonMain`, and
 * this is the thing that pays for it.
 *
 * ### Not goldens
 *
 * [ComponentRenderTest][io.kontour.ui.catalog] compares against committed PNGs,
 * because a component's appearance is a thing that should not change quietly.
 * A documentation page is not: its content changes every time somebody improves
 * a sentence, and 84 pages times four widths of churning goldens is a review tax
 * that would be paid in rubber stamps within a fortnight.
 *
 * What is asserted instead is the pair of things that are never intentional:
 *
 *  - **it renders at all** — an exception from any page at any width fails, and
 *    that is what catches the class of defect that shipped;
 *  - **it drew something** — a page whose image is one flat colour is a page
 *    with no content, which is how a broken specimen or an empty page looks from
 *    the outside. The same "read it back and require it to differ from its own
 *    background" check `ComponentRenderTest` uses, for the same reason.
 *
 * The images themselves go to `build/site-shots/`, with a contact sheet per
 * width class, and they are for a person to scroll through. That is the review
 * step, and it is deliberately not automated — the complaint that started this
 * round was "most components don't have live previews", which no assertion in
 * this file would have phrased for you.
 */
class SiteRenderTest {

    /**
     * One per [WindowWidthClass][io.kontour.ui.adaptive.WindowWidthClass] bucket,
     * not one per marketing device. The library's own breakpoints are the thing
     * under test, so the widths are chosen to land inside each of them: 600 and
     * 840 and 1200 are the boundaries.
     */
    private val widths = listOf(
        "compact" to 390,
        "medium" to 700,
        "expanded" to 1024,
        "large" to 1440,
    )

    /**
     * Density 1, not the 2 the goldens use.
     *
     * These are read by a person at a glance rather than compared pixelwise, and
     * 336 images at 2× is most of a gigabyte for no extra information.
     */
    private val density = 1f

    /** Tall enough to show a page's shape. Overflow below this is expected. */
    private val height = 1400

    @Test
    fun `every page renders at every width`() {
        val root = File(System.getProperty("kontour.siteShots") ?: "build/site-shots")
        val failures = mutableListOf<String>()

        // Home and Gallery are routes too, and Home is where the crash was.
        val routes: List<Pair<String, Route>> =
            listOf("home" to Route.Home) +
                docPages.map { it.slug to Route.Component(it.slug) }

        for ((widthName, width) in widths) {
            val dir = File(root, widthName).apply { mkdirs() }
            for ((name, route) in routes) {
                navigate(route)
                val result = runCatching { shoot(width, File(dir, "$name.png")) }
                result.onFailure { failures += "$widthName/$name threw ${it::class.simpleName}: ${it.message}" }
                result.onSuccess { flat -> if (flat) failures += "$widthName/$name drew nothing — the image is one colour" }
            }
            contactSheet(dir, widthName)
        }

        navigate(Route.Home)
        if (failures.isNotEmpty()) {
            fail("${failures.size} page renders failed:\n\n" + failures.joinToString("\n"))
        }
    }

    /** Renders the site at [width] into [file]. Returns true if the image is one flat colour. */
    private fun shoot(width: Int, file: File): Boolean {
        ImageComposeScene(
            width = width,
            height = height,
            density = Density(density),
            content = { Site() },
        ).use { scene ->
            val bytes = requireNotNull(scene.render(0L).encodeToData(EncodedImageFormat.PNG)) {
                "Skia failed to encode ${file.name}"
            }.bytes
            file.writeBytes(bytes)
            return isUniform(file, width)
        }
    }

    /**
     * Whether the **content area** is one flat colour.
     *
     * Not the whole image, which is what this checked first and which is a
     * weaker question than it looks: the top bar and the index draw on every
     * page, so `date-picker` — a title, one sentence, and 1,200px of white —
     * passed while being exactly the emptiness this round exists to remove.
     *
     * So it starts below the bar and to the right of the index, and the ink it
     * is looking for is the page's own.
     */
    private fun isUniform(file: File, width: Int): Boolean {
        val image = ImageIO.read(file) ?: return true
        val top = ChromeHeight
        val left = if (width >= 600) IndexWidth else 0
        if (top >= image.height || left >= image.width) return true
        val first = image.getRGB(left, top)
        // Every 7th pixel on both axes. A page with content fails this within a
        // few rows; a blank one has to be walked to be sure, and at 1440×1400
        // that is two million calls per image times hundreds of images.
        var x = left
        while (x < image.width) {
            var y = top
            while (y < image.height) {
                if (image.getRGB(x, y) != first) return false
                y += 7
            }
            x += 7
        }
        return true
    }

    /** Below the top bar, and past the index where one is drawn. */
    private val ChromeHeight = 72
    private val IndexWidth = 300

    /** An HTML page tiling every shot, because scrolling a directory of PNGs is not review. */
    private fun contactSheet(dir: File, widthName: String) {
        val shots = dir.listFiles { f: File -> f.extension == "png" }?.sortedBy { it.name }.orEmpty()
        val tiles = shots.joinToString("\n") {
            """<figure><img src="${it.name}" loading="lazy"><figcaption>${it.nameWithoutExtension}</figcaption></figure>"""
        }
        File(dir, "index.html").writeText(
            """
            <!doctype html><meta charset="utf-8"><title>Kontour UI docs — $widthName</title>
            <style>
              body { font: 13px system-ui, sans-serif; margin: 24px; background: #fafafa; }
              h1 { font-size: 18px; }
              main { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; }
              figure { margin: 0; }
              img { width: 100%; border: 1px solid #ddd; background: #fff; display: block; }
              figcaption { padding-top: 6px; color: #555; }
            </style>
            <h1>$widthName — ${shots.size} pages</h1>
            <main>
            $tiles
            </main>
            """.trimIndent(),
        )
    }

    private inline fun <T> ImageComposeScene.use(block: (ImageComposeScene) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
