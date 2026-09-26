package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.kontourSizing
import androidx.compose.runtime.Composable
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The indicator is the width of its dots, and every pixel of it belongs to a page.
 *
 * Reported from a phone as the dots feeling "too spread out", and the cause was
 * not the demo's layout. Each dot carried its own `minimumTouchTarget()`, which
 * on Android reserves **48dp of row apiece** — so five 8dp dots with a 6dp gap
 * between them, 64dp of ink, were laid out across 264dp and sat nearly four
 * times further from each other than they looked. `page-indicator.md` had
 * written the symptom down as a feature: *"each gets a full `minimumTouchTarget`,
 * which is why the dots sit further apart than they look"*.
 *
 * The fix moves the duty up one level: one reserved band over the whole strip,
 * and a tap goes to whichever dot centre is nearest the finger.
 *
 * ### What that trades
 *
 * A slice of the band is one dot's pitch wide — 14dp — where WCAG 2.5.8 asks for
 * 24dp, so the *pointer* route gets narrower slices than the criterion's minimum
 * even as it gets a full-height band and no dead space in it. The exact route is
 * unaffected: each dot still carries `Role.RadioButton` and an `onClick` in the
 * semantics tree, which is what a screen reader activates, and it costs no
 * layout at all. [CarouselTest] is where that half is pinned; this is the half
 * that is made of pixels.
 */
class PageIndicatorStripTest {

    /**
     * 264dp of row for 64dp of dots, at Android's touch target.
     *
     * The bar is set at twice the ink rather than at the ink, because the strip
     * is allowed to reserve *something* — one band, 48dp tall, which on a strip
     * this narrow can also widen it to 48dp. What it is not allowed to do is
     * multiply by the number of pages, which is the only way to get past 168dp
     * here and is what was reported.
     */
    @Test
    fun theStripIsTheWidthOfItsDots() {
        val measured = stripBounds(pages = 5, touchTarget = AndroidTouchTarget)
        val wide = measured.width / Density

        assertTrue(
            wide <= 2 * InkWidth,
            "five 8dp dots with 6dp between them are ${InkWidth}dp of ink, and the " +
                "strip measured ${wide}dp — so each dot is still reserving a touch " +
                "target of its own and the row is ${wide / InkWidth} times the size " +
                "of what it draws",
        )
    }

    /**
     * And it is still a full-height band, which is the half of the target that
     * was worth keeping.
     *
     * A ratchet rather than a report: 48dp was already there, one box at a time,
     * and the point of reserving it once is that it survives. An 8dp-tall strip
     * would be a worse control than the spread-out one.
     */
    @Test
    fun theStripIsStillAsTallAsAFingertip() {
        val measured = stripBounds(pages = 5, touchTarget = AndroidTouchTarget)
        val tall = measured.height / Density

        assertEquals(
            AndroidTouchTarget.value.toDouble(),
            tall,
            absoluteTolerance = 1.0,
            "the strip is ${tall}dp tall, so nothing reserved the target after the " +
                "dots stopped reserving it themselves",
        )
    }

    /**
     * **No dead pixels.** Every point in the band selects some page.
     *
     * This is the property the old layout could not have: 48dp boxes separated by
     * the row's own 6dp `Arrangement` gap leave four 6dp strips — 24dp of a 264dp
     * indicator — that are inside the control, look like part of it, and select
     * nothing at all. Sampled every 5px across the band, the old route came back
     * with about one point in ten selecting nothing, in four clumps where the
     * arithmetic says the gaps are.
     *
     * The run is also asserted to be **ordered and complete**: left to right the
     * page never goes backwards and all five are reachable, which is what makes
     * "nearest dot centre" the same thing as "the dot you aimed at".
     */
    @Test
    fun everyPixelOfTheBandBelongsToAPage() {
        var strip = Rect.Zero
        val hits = mutableListOf<Pair<Int, Int?>>()

        Scene(width = 700, height = 200) {
            Themed(AndroidTouchTarget) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    val carousel = rememberCarouselState { 5 }
                    PageIndicator(
                        state = carousel,
                        modifier = Modifier.reportBounds { strip = it },
                        onPageClick = { page -> picked = page },
                    )
                }
            }
        }.use { scene ->
            scene.frames(4)
            // Near the top of the band, so the sample is in the reserved height
            // rather than on the 8dp of dot in the middle of it. Under the old
            // layout that row was inside each dot's own box, so this is not what
            // makes the difference — the horizontal holes are.
            val row = strip.top + 4f
            var x = strip.left + 1f
            while (x < strip.right - 1f) {
                picked = null
                scene.press(Offset(x, row))
                scene.advance()
                scene.release(Offset(x, row))
                scene.advance()
                hits += (x - strip.left).toInt() to picked
                x += SampleStep
            }
        }

        val dead = hits.filter { it.second == null }
        assertTrue(
            dead.isEmpty(),
            "${dead.size} of ${hits.size} points in the band selected nothing — at " +
                "${dead.take(8).joinToString { "${it.first}px" }} — so the indicator " +
                "has gaps in it that look like part of the control",
        )

        val pages = hits.mapNotNull { it.second }
        assertEquals(
            (0..4).toList(),
            pages.distinct(),
            "sampled left to right the band selected $pages, which is either out of " +
                "order or missing a page",
        )
    }

    /**
     * The default indicator **travels**, which is the whole of the new style.
     *
     * At rest it is a pill over the current dot, exactly as `Dots` would draw it;
     * held halfway between two pages it stretches across both, exactly as `Worm`
     * would. The second half is what fails on the old default, where nothing is
     * drawn between two dots however far the finger has gone — the pill is the
     * current dot and the current dot is in one place.
     *
     * Measured as the widest run of `primary`, the way
     * [SelectionAnimationTest.theWormStretchesBetweenTwoDots] measures it: the
     * pill is `primary` and the dots under it are `outlineStrong`, so a threshold
     * between the two finds the pill and nothing else.
     */
    @Test
    fun theDefaultRestsWideAndStillTravels() {
        var atRest = 0
        var midTravel = 0
        var dots = Rect.Zero
        var pages = Rect.Zero

        Scene(width = 400, height = 300) {
            val carousel = rememberCarouselState { 4 }
            Column(Modifier.fillMaxSize().background(Color.White)) {
                Carousel(
                    state = carousel,
                    contentDescription = "Pages",
                    modifier = Modifier.fillMaxWidth().height(100.dp).reportBounds { pages = it },
                ) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                // No `style`: the default is the subject.
                PageIndicator(carousel, modifier = Modifier.reportBounds { dots = it })
            }
        }.use { scene ->
            atRest = scene.frames(6).widestPrimaryRun(dots.center.y.toInt())
            // Half a page along and held there. Releasing would snap to one page
            // or the other, and halfway is the only place a travelling style is
            // doing anything a resting one is not.
            scene.drag(
                from = pages.alongX(0.75f),
                to = pages.alongX(0.25f),
                steps = 20,
                release = false,
            )
            midTravel = scene.frames(2).widestPrimaryRun(dots.center.y.toInt())
            scene.release(pages.alongX(0.25f))
        }

        // The comparison that separates this from `Worm`, which rests at one dot
        // diameter — 16px here — and so says nothing at all about which page you
        // are on until something moves.
        assertTrue(
            atRest >= RestingPill,
            "the default rested as a ${atRest}px pill where a widened dot is " +
                "${RestingPill}px and a plain one is ${DotSize}px — so at rest it is " +
                "drawing a worm's dot and nothing says which page is current",
        )
        assertTrue(
            midTravel > atRest * 1.4f,
            "the pill was ${atRest}px at rest and ${midTravel}px with the finger " +
                "held halfway between two pages, so it is not travelling — which " +
                "is the half of this style that `Dots` does not have",
        )
    }

    /** Where the last tap went, written from inside the scene. */
    private var picked: Int? = null

    private fun stripBounds(pages: Int, touchTarget: Dp): Rect {
        var strip = Rect.Zero
        Scene(width = 700, height = 200) {
            Themed(touchTarget) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    val carousel = rememberCarouselState { pages }
                    PageIndicator(
                        state = carousel,
                        modifier = Modifier.reportBounds { strip = it },
                        // The report is about the interactive indicator. A
                        // decorative one never reserved anything.
                        onPageClick = {},
                    )
                }
            }
        }.use { scene -> scene.frames(4) }
        return strip
    }

    /**
     * The widest run of near-black pixels in [row].
     *
     * Copied in spirit from [SelectionAnimationTest]: `primary` clears the
     * threshold and `outlineStrong` does not, so this finds the pill without
     * having to know where the dots are.
     */
    private fun BufferedImage.widestPrimaryRun(row: Int): Int {
        var best = 0
        var run = 0
        for (x in 0 until width) {
            val dark = (getRGB(x, row) shr 16 and 0xFF) < 90
            run = if (dark) run + 1 else 0
            if (run > best) best = run
        }
        return best
    }

    @Composable
    private fun Themed(touchTarget: Dp, content: @Composable () -> Unit) {
        KontourTheme(
            reduceMotion = true,
            sizing = kontourSizing(ContrastLevel.Standard).copy(minTouchTarget = touchTarget),
            content = content,
        )
    }

    private companion object {
        /** The scene's density, so measurements can come back in dp. */
        const val Density = 2.0

        /** `platformMinTouchTarget` on Android, which is where this was reported. */
        val AndroidTouchTarget: Dp = 48.dp

        /**
         * Five dots with 8dp between them, one of them widened: `4 * 8 + 20 + 4 * 8`.
         *
         * The ink, and therefore the pitch the eye is aiming at.
         */
        const val InkWidth = 84.0

        /** `PageIndicatorDefaults.DotSize`, 8dp, in scene pixels. */
        const val DotSize = 16

        /**
         * How long the pill is at rest, in scene pixels.
         *
         * `ActiveWidth`, 20dp, at a density of 2 — the pill is the hull of the
         * dots it spans, and at rest it spans one, the one the row widened to
         * exactly this. Against 16px for a dot that is not current.
         */
        const val RestingPill = 40

        /**
         * Pixels between samples across the band.
         *
         * Smaller than the 12px the old layout's dead strips were wide, so a hole
         * cannot be stepped over.
         */
        const val SampleStep = 5f
    }
}
