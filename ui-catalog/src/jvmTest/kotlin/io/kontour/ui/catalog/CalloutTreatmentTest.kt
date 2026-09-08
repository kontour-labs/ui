package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Banner
import io.kontour.ui.components.display.BannerTone
import io.kontour.ui.components.display.Callout
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A callout and a banner of the same tone are the same treatment.
 *
 * Reported as "callout still looks crap", and the direction was settled with the
 * reporter: make it a sibling of `Banner` rather than fix its accent rule for a
 * fourth time.
 *
 * ### What this replaces, and why the old test could not have helped
 *
 * `CalloutEdgeTest` measured how far in from the container's leading edge the
 * accent rule started, on **one row through the middle** — which is the one row
 * where a 3dp band clipped to a 22dp corner still survives. It passed
 * throughout, across three separate attempts at that rule, every one of which
 * came back. A test aimed at the middle of a thing whose whole problem is its
 * ends.
 *
 * The rule is gone, so this asks the question that replaced it: do the two
 * components agree? Same ground for the same tone, and a *mark* rather than
 * colour alone — which is the accessibility argument as much as the visual one,
 * since a tint is the one signal a reader may not have.
 *
 * ### The instrument this went through, because the first one was wrong
 *
 * The first version counted pixels darker than the container's own fill inside a
 * 72px leading strip, and asserted there were enough of them to be a glyph. It
 * passed. Then a control — the same callout with `icon = null` — was added to
 * prove the count could not be met without a mark, and it drew **1262px against
 * the marked version's 1045px**. More, not less.
 *
 * The strip was measuring the *text*. Without an icon the words start at the
 * container's padding instead of behind it, so they move into the strip and fill
 * it more thoroughly than a hollow triangle does. The assertion was green, the
 * number was real, and it was answering a different question from the one asked.
 *
 * So the measurement here is not "is there ink". It is **whether two tones draw
 * two different shapes** in the leading strip, compared as ground/not-ground
 * masks:
 *
 *   * the *tint* cancels, because a mask is taken against each render's own
 *     ground;
 *   * the *border* cancels, because it is in the same place in both;
 *   * the *text* cancels, because it is the same words at the same position;
 *   * only a per-tone **mark** can make the two masks differ.
 *
 * And the control now proves it: with `icon = null` the two masks are identical,
 * so a difference is the mark and nothing else.
 */
class CalloutTreatmentTest {

    @Test
    fun aCalloutAndABannerShareTheirGround() {
        val rendered = render(BannerTone.Warning)
        assertEquals(
            rendered.bannerGround, rendered.calloutGround,
            "a warning banner's ground is ${rendered.bannerGround} and a warning " +
                "callout's is ${rendered.calloutGround}. The tone is supposed to " +
                "decide both, through the same `bannerColoursFor`",
        )
    }

    @Test
    fun aCalloutCarriesItsToneAsAMarkAndNotOnlyAsATint() {
        val differing = markDifference(withIcons = true)
        assertTrue(
            differing > MinMark,
            "a warning callout and an info callout draw the same shape in their " +
                "leading strip — only $differing pixels of the two ground masks " +
                "differ, and a glyph is worth several hundred. The tone is a tint " +
                "and nothing else, which is the one signal a reader may not have. " +
                "On a documentation site nobody passes an icon either, because " +
                "every markdown blockquote becomes one of these",
        )
    }

    @Test
    fun theDifferenceIsTheMarkAndNotTheTextOrTheBorder() {
        // The control, and it is not optional: the first version of the test
        // above counted ink in this same strip and was satisfied by the text.
        // Suppress the icon and the two tones must become indistinguishable
        // there — if they do not, whatever is separating them is not the mark
        // and the assertion above proves nothing.
        val differing = markDifference(withIcons = false)
        assertEquals(
            0, differing,
            "with `icon = null` a warning callout and an info callout still " +
                "differ in $differing pixels of the leading strip. Something " +
                "other than the mark is moving between the two tones — so the " +
                "test above could pass on a callout that draws no mark at all",
        )
    }

    /**
     * Pixels where the two tones disagree about what is ground, in the strip
     * where a mark belongs.
     *
     * Ground is sampled per render, so the tint itself contributes nothing: the
     * question is only *where* each render put something that is not its own
     * fill.
     */
    private fun markDifference(withIcons: Boolean): Int {
        val warning = render(BannerTone.Warning, withIcon = withIcons)
        val info = render(BannerTone.Info, withIcon = withIcons)
        var differing = 0
        for (y in 0 until warning.strip.size) {
            for (x in 0 until warning.strip[y].size) {
                if (warning.strip[y][x] != info.strip[y][x]) differing++
            }
        }
        return differing
    }

    private class Rendered(
        val bannerGround: Int,
        val calloutGround: Int,
        /** True where the pixel is the container's own fill. */
        val strip: Array<BooleanArray>,
    )

    private fun render(tone: BannerTone, withIcon: Boolean = true): Rendered {
        var banner = Rect.Zero
        var callout = Rect.Zero
        val image = Scene(width = 400, height = 400) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Column(Modifier.padding(20.dp).width(280.dp)) {
                        Banner(
                            tone = tone,
                            modifier = Modifier.reportBounds { banner = it },
                        ) { +"Trams do not run to the terminus after nine." }
                        Callout(
                            modifier = Modifier.reportBounds { callout = it },
                            tone = tone,
                            // Null suppresses the mark; the default is the
                            // tone's own, which is the case under test.
                            icon = if (withIcon) calloutMark(tone) else null,
                        ) { Text("Trams do not run to the terminus after nine.") }
                    }
                }
            }
        }.use { it.frames(4) }

        // Sampled well inside each container and clear of its text: the ground is
        // what a tone paints, and it is the same fill in both.
        val bannerGround = image.getRGB(banner.right.toInt() - 12, banner.center.y.toInt())
        val calloutGround = image.getRGB(callout.right.toInt() - 12, callout.center.y.toInt())

        // A fixed window relative to the callout's own top-left, so the two
        // tones are compared over exactly the same pixels. Both callouts are the
        // same size — same words, same padding, same type — so this is the same
        // window in both.
        val left = callout.left.toInt() + 2
        val top = callout.top.toInt() + 2
        val strip = Array(StripHeight) { row ->
            BooleanArray(StripWidth) { column ->
                image.getRGB(left + column, top + row) == calloutGround
            }
        }

        return Rendered(bannerGround, calloutGround, strip)
    }

    /** The tone's own mark, which is what `Callout` defaults to. */
    private fun calloutMark(tone: BannerTone) = when (tone) {
        BannerTone.Warning -> io.kontour.ui.foundation.SystemIcons.Warning
        BannerTone.Info -> io.kontour.ui.foundation.SystemIcons.Info
        BannerTone.Success -> io.kontour.ui.foundation.SystemIcons.Success
        BannerTone.Danger -> io.kontour.ui.foundation.SystemIcons.Danger
        BannerTone.Accent -> io.kontour.ui.foundation.SystemIcons.Info
    }

    private companion object {
        /** The 12dp padding plus a 20dp glyph, at density 2, with room to spare. */
        const val StripWidth = 72

        /** Two lines of `bodySmall` plus the padding, at density 2. */
        const val StripHeight = 80

        /**
         * A hollow triangle against a hollow circle, at 40px. They share a lot of
         * their area, so this is deliberately well under the glyph's own pixel
         * count — the failure it has to separate is *zero*, not *fewer*.
         */
        const val MinMark = 200
    }
}
