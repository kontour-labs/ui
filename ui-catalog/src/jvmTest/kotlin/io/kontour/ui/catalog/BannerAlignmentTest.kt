package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.X
import io.kontour.ui.components.display.Banner
import io.kontour.ui.components.display.BannerTone
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.kontourSizing
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A banner's text sits in the middle of it, whatever is beside the text.
 *
 * Reported as "the text in banner is not centred vertically on mobile web",
 * and the platform in that sentence is the whole of why it survived.
 *
 * ### The row is top-aligned and two of its three children opt out
 *
 * `Banner`'s outer `Row` passes no `verticalAlignment`, so it takes Compose's
 * default `Alignment.Top`. The leading icon and the dismiss button each carry
 * `.align(Alignment.CenterVertically)`; the body carries only `weight(1f)` and
 * inherits the top.
 *
 * That is invisible while the text is the tallest thing in the row, and the
 * dismiss button is what makes it not be. `IconButtonSurface` applies
 * `.minimumTouchTarget()` *before* `.size(...)`, and that is a
 * `LayoutModifierNode` — it grows the **measured** size, not just the hit rect.
 * `platformMinTouchTarget` is **44dp on web** and **24dp on the JVM**, so a
 * one-line banner is a 44dp row holding 19.5dp of `bodySmall` pinned to the top.
 *
 * ### Wrapping hides this rather than causing it
 *
 * | Message | Text | Row (web) | Text sits |
 * |---|---|---|---|
 * | one line | 19.5dp | 44dp | ~12dp high |
 * | title and message | ~41dp | 44dp | ~1.5dp high |
 * | three lines | ~60dp | 60dp | correct |
 *
 * So the specimen every golden is taken from — a title *and* a message wrapping
 * to three lines at 260dp, rendered at the JVM's 24dp target — is the one case
 * that looks right. **No picture in this repository has ever shown what the
 * reporter's phone shows**, which is why this test sets the touch target rather
 * than taking the default, the way `WidthSweepTest` and `PhoneWidthTest` do.
 *
 * ### Centring cannot drift
 *
 * The comment this replaced argued that "a body that centred itself would drift
 * as the text grew". It cannot: once the body is the tallest child, centre and
 * top are the same placement. The argument was wrong on its own terms.
 */
class BannerAlignmentTest {

    @Test
    fun theTextStaysPutWhenTheTouchTargetGrows() {
        val desktop = textDrift(DesktopTouchTarget)
        val phone = textDrift(WebTouchTarget)

        assertTrue(
            abs(phone - desktop) < Agreement,
            "the message sits ${desktop}dp off the middle of the row at a " +
                "${DesktopTouchTarget.value}dp touch target and ${phone}dp off at " +
                "${WebTouchTarget.value}dp — it is moving with the button beside " +
                "it, which is what `Alignment.Top` does when something else is " +
                "setting the row's height. Centred text does not move when the " +
                "row grows, so these two numbers have to agree.",
        )
    }

    /**
     * And it is near the middle, not merely consistently off it.
     *
     * A difference of zero is also what two equally wrong numbers give, so the
     * absolute number is worth one assertion of its own. It cannot be zero: this
     * measures **ink**, and a line of text has more room under its baseline than
     * over its cap height, so a perfectly centred line reads a couple of dp high.
     * The bound is that asymmetry and nothing more — the defect is 14.
     */
    @Test
    fun aOneLineBannerCentresItsTextAtAPhoneTouchTarget() {
        val drift = textDrift(WebTouchTarget)
        assertTrue(
            drift < AllowedDrift,
            "a one-line banner's text sits ${drift}dp off the middle of the row " +
                "at a ${WebTouchTarget.value}dp touch target. The dismiss button " +
                "sets the row's height there, and the text is inheriting the " +
                "row's `Alignment.Top` while the icon and the dismiss both centre " +
                "themselves — so the two things flanking the message agree with " +
                "each other and disagree with it.",
        )
    }

    /**
     * How far the message's ink is from the middle of the banner, in dp.
     *
     * Ink rather than a reported rect, because the text is inside a slot this
     * test cannot reach a modifier into. Sampled across the message column only —
     * clear of the dismiss on the right — and inset from the container's own top
     * and bottom so the border is not mistaken for a glyph.
     */
    private fun textDrift(touchTarget: androidx.compose.ui.unit.Dp): Float {
        var bounds = Rect.Zero
        val image = Scene(width = 720, height = 300) {
            KontourTheme(
                darkTheme = false,
                reduceMotion = true,
                sizing = kontourSizing(ContrastLevel.Standard).copy(minTouchTarget = touchTarget),
            ) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                    Banner(
                        tone = BannerTone.Warning,
                        onDismissRequest = {},
                        // Without an icon there is no dismiss button, and
                        // without the button the row is only as tall as the text
                        // — which is the one shape where nothing can be out of
                        // place. A first version of this test omitted it and
                        // passed against the defect at both touch targets.
                        dismissIcon = Tabler.Outline.X,
                        modifier = Modifier.reportBounds { bounds = it },
                    ) {
                        message { +"Couldn't reach the server." }
                    }
                }
            }
        }.use { it.frames(4) }

        val top = bounds.top.toInt() + BorderInset
        val bottom = bounds.bottom.toInt() - BorderInset
        val xs = (bounds.left.toInt() + 8) until (bounds.left.toInt() + 320)
        val rows = (top until bottom).filter { y -> xs.any { luminance(image, it, y) < InkLevel } }
        require(rows.isNotEmpty()) { "the message drew no ink between $top and $bottom" }

        val textCentre = (rows.first() + rows.last()) / 2f
        val rowCentre = (bounds.top + bounds.bottom) / 2f
        // Scene density is 2, and every number above is in scene pixels.
        return abs(textCentre - rowCentre) / 2f
    }

    private fun luminance(image: BufferedImage, x: Int, y: Int): Float {
        val rgb = image.getRGB(x, y)
        return 0.2126f * ((rgb shr 16) and 0xFF) +
            0.7152f * ((rgb shr 8) and 0xFF) +
            0.0722f * (rgb and 0xFF)
    }

    private companion object {
        val WebTouchTarget = 44.dp
        val DesktopTouchTarget = 24.dp

        /**
         * How far off centre the text's ink may sit, in dp.
         *
         * Not zero, and the reason is in [aOneLineBannerCentresItsTextAtAPhoneTouchTarget]:
         * a line box has more room below its baseline than above its cap height,
         * so centred ink reads high. The defect is 14.
         */
        const val AllowedDrift = 4f

        /**
         * How closely the two touch targets have to agree, in dp.
         *
         * Centred text does not move when the row grows around it, so the answer
         * is the same number twice. Before the fix it is 6 against 14.
         */
        const val Agreement = 1f

        /** Enough to clear the container's 1dp border at 2x, and its antialiasing. */
        const val BorderInset = 6

        /** Dark enough to be a glyph rather than the tinted container. */
        const val InkLevel = 140f
    }
}
