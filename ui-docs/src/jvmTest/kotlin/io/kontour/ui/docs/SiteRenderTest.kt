package io.kontour.ui.docs

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
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
abstract class SiteRenderTest(private val widthName: String, private val width: Int) {

    /**
     * Density 1, not the 2 the goldens use.
     *
     * These are read by a person at a glance rather than compared pixelwise, and
     * 336 images at 2× is most of a gigabyte for no extra information.
     */
    private val density = 1f

    /** Tall enough to show a page's shape. Overflow below this is expected. */
    private val height = 1400

    /**
     * Whether to write the pictures, as opposed to only checking them.
     *
     * Two jobs live in this file and they want running at different times. The
     * **gate** — every page renders without throwing, every page drew something —
     * is worth a minute on every change; both halves of it have caught shipped
     * defects. The **contact sheets** are 84 MB of PNGs for a person to scroll
     * through, and that is something somebody asks for.
     *
     * So the sweep runs either way and only the writing is conditional. Off by
     * default; `:ui-docs:siteRenders` turns it on.
     */
    private val writing = System.getProperty("kontour.contactSheets") == "true"

    @Test
    fun `every page renders`() {
        val root = File(System.getProperty("kontour.siteShots") ?: "build/site-shots")
        val failures = mutableListOf<String>()

        // Home and Gallery are routes too, and Home is where the crash was.
        //
        // Named by path with the separator flattened, because there are now two
        // pages called `overlays` — the guide to the mechanism and the family
        // index — and one file per shot cannot hold both.
        val routes: List<Pair<String, Route>> =
            listOf("home" to Route.Home) +
                docPages.map { it.path.replace('/', '-') to Route.Doc(it.path) }

        val dir = File(root, widthName).takeIf { writing }?.apply { mkdirs() }
        for ((name, route) in routes) {
            navigate(route)
            val result = runCatching { shoot(width, dir?.let { File(it, "$name.png") }) }
            result.onFailure { failures += "$widthName/$name threw ${it::class.simpleName}: ${it.message}" }
            result.onSuccess { flat -> if (flat) failures += "$widthName/$name drew nothing — the image is one colour" }
        }
        if (dir != null) contactSheet(dir, widthName)

        navigate(Route.Home)
        if (failures.isNotEmpty()) {
            fail("${failures.size} page renders failed at $widthName:\n\n" + failures.joinToString("\n"))
        }
    }

    /**
     * Renders the site at [width], writing to [file] if there is one.
     *
     * Returns true if the image is one flat colour.
     *
     * The flatness check reads Skia's own pixels rather than a PNG. It used to
     * encode the image, write it, and read the file straight back to look at it
     * — three round trips to answer a question the pixels in hand could answer,
     * and with `writing` off there is no file to read at all.
     */
    private fun shoot(width: Int, file: File?): Boolean {
        ImageComposeScene(
            width = width,
            height = height,
            density = Density(density),
            content = { Site() },
        ).use { scene ->
            val image = scene.render(0L)
            if (file != null) {
                val bytes = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) {
                    "Skia failed to encode ${file.name}"
                }.bytes
                file.writeBytes(bytes)
            }
            return isUniform(image, width)
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
    private fun isUniform(image: Image, width: Int): Boolean {
        val pixels = image.peekPixels() ?: return true
        val top = ChromeHeight
        val left = if (width >= 600) IndexWidth else 0
        if (top >= image.height || left >= image.width) return true
        val first = pixels.getColor(left, top)
        // Every 7th pixel on both axes. A page with content fails this within a
        // few rows; a blank one has to be walked to be sure, and at 1440×1400
        // that is two million calls per image times hundreds of images.
        var x = left
        while (x < image.width) {
            var y = top
            while (y < image.height) {
                if (pixels.getColor(x, y) != first) return false
                y += 7
            }
            x += 7
        }
        return true
    }

    /** Below the top bar, and past the index where one is drawn. */
    private val ChromeHeight = 72

    /**
     * A little past the site's own `IndexWidth`, and it has to stay that way.
     *
     * This is where the scan starts looking for the page's own ink. Let the
     * index grow wider than it and the scan reads sidebar rows, so a page with
     * an entirely empty content area would pass on the strength of its
     * navigation. The site's index is 320dp; this is 340.
     */
    private val IndexWidth = 340

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
              figcaption { padding-top: 6px; colour: #555; }
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

/**
 * One class per width, because that is the unit Gradle can parallelise.
 *
 * This was a single test method looping over four widths, which meant one fork
 * however many were allowed: `maxParallelForks` distributes **classes**, not
 * methods. Four classes can be spread.
 *
 * Measured, cold, `--no-daemon`, all 488 renders: **1m 40s in one fork, 1m 20s
 * across four.** Worth having and not worth much — the sweep was never the
 * expensive thing it was briefly believed to be, and the note that used to sit
 * here claiming it was 90% of a CI job was reading Gradle's `> Task` headers as
 * if they were execution times. They are flush times; forty of them share a
 * 0.3-second window in the same log.
 *
 * The coverage is unchanged by the split. What it costs is four JVMs' worth of
 * memory instead of one, which is why the fork count is capped.
 *
 * They are named for the [WindowWidthClass][io.kontour.ui.adaptive.WindowWidthClass]
 * bucket rather than for a device: the library's own breakpoints are what is
 * under test, so each width lands inside one of them. 600, 840 and 1200 are the
 * boundaries.
 */
class CompactSiteRenderTest : SiteRenderTest("compact", 390)

class MediumSiteRenderTest : SiteRenderTest("medium", 700)

class ExpandedSiteRenderTest : SiteRenderTest("expanded", 1024)

class LargeSiteRenderTest : SiteRenderTest("large", 1440)
