package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.kontourSizing
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A bigger touch target must not draw a bigger component.
 *
 * `Modifier.minimumTouchTarget()` promises this in as many words: "a 20dp
 * checkbox stays a 20dp checkbox on screen; it just reserves 48dp of layout
 * space and centres itself in it." Reserving space is a *layout* change. If the
 * modifier ends up underneath `clip`, `background` or `border` in a chain, those
 * take the reserved size instead and the component is drawn at the size of its
 * own touch target.
 *
 * That is not hypothetical. `Chip` built its chain as
 * `.focusRing().clip().background().clickable()` and handed it to a private
 * `ChipSurface` that appended `.minimumTouchTarget().height(34.dp)` — so an
 * assist chip's pill was drawn at 48dp on Android, beside 34dp filter and input
 * chips whose chains were the right way round. It was reported from a phone as
 * "some of the chips are different heights to others".
 *
 * ### Why no golden could have caught it
 *
 * `platformMinTouchTarget` is 48dp on Android, 44 on iOS and web, and **24dp on
 * the JVM** — desktop is pointer-driven, where WCAG's 24px floor is the right
 * answer. Nothing in this library is smaller than 24dp, so on the test host the
 * modifier expands nothing at all and both orderings render identically. Every
 * golden in the project has been blind to this since the modifier was written.
 *
 * ### Measured by how much is drawn, not by how far it spreads
 *
 * Each component is rendered twice — once with a 24dp minimum, once with 48dp —
 * and the **number of pixels it draws** is compared.
 *
 * The first version of this compared bounding boxes and was wrong. A container
 * legitimately gets bigger when its children reserve more: a `RadioGroup` spaces
 * its rows further apart, a `Rating` spreads its stars, a `Stepper`'s buttons sit
 * wider apart. All correct, all flagged, none a bug. Ink *area* separates the two
 * cases cleanly — pushing the same glyphs further apart draws exactly as many
 * pixels as before, whereas a pill drawn at 48dp instead of 34dp draws
 * considerably more.
 */
class TouchTargetOrderingTest {

    @Test
    fun aBiggerTouchTargetDoesNotDrawABiggerComponent() {
        val grew = mutableListOf<String>()

        for (spec in componentRegistry) {
            val small = inkArea(DesktopTarget) { spec.content(Modifier, true) {} }
            val large = inkArea(AndroidTarget) { spec.content(Modifier, true) {} }
            if (small == 0 || large == 0) continue

            // Antialiasing moves a handful of pixels when anything shifts by a
            // subpixel; being drawn at the touch target moves a great many.
            val grownBy = (large - small).toFloat() / small
            if (grownBy > Tolerance) {
                grew += "${spec.name}: draws $small pixels at ${DesktopTarget.value.toInt()}dp " +
                    "and $large at ${AndroidTarget.value.toInt()}dp — " +
                    "${(grownBy * 100).toInt()}% more"
            }
        }

        assertTrue(
            grew.isEmpty(),
            "${grew.size} component(s) are drawn at the size of their touch target " +
                "rather than reserving it:\n" + grew.joinToString("\n") { "  · $it" } +
                "\n\nThe touch target belongs at the *top* of the modifier chain, above " +
                "clip/background/border. Underneath them, the visuals take the reserved " +
                "size — which on the JVM is 24dp and invisible, and on a phone is 48dp.",
        )
    }

    /** How many pixels a component draws, or 0 if it drew nothing. */
    private fun inkArea(target: Dp, content: @Composable () -> Unit): Int {
        var area = 0
        Scene(width = Canvas, height = Canvas) {
            KontourTheme(
                reduceMotion = true,
                sizing = kontourSizing(ContrastLevel.Standard).copy(minTouchTarget = target),
            ) {
                // Some specimens reach for the host the moment they compose.
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize().background(Probe),
                        contentAlignment = Alignment.Center,
                    ) {
                        content()
                    }
                }
            }
        }.use { scene -> area = scene.frames(Frames).inkArea() }
        return area
    }

    /**
     * How much of the page the component covers.
     *
     * Measured against a colour the design system never uses, because the first
     * version of this counted "darker than very pale" on a white page — and a
     * chip's container is `surfaceSunken`, a pale grey that fell on the wrong
     * side of the line. The pill was never counted at all, so the test passed
     * with the bug it was written for put back. Against magenta, everything the
     * component draws counts, including the pale things.
     */
    private fun BufferedImage.inkArea(): Int {
        var drawn = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((getRGB(x, y) and 0xFFFFFF) != ProbeRgb) drawn++
            }
        }
        return drawn
    }

    private companion object {
        const val Canvas = 600
        const val Frames = 6

        /** `platformMinTouchTarget` on the JVM, and on Android. */
        val DesktopTarget = 24.dp
        val AndroidTarget = 48.dp

        /** A page colour nothing in the design system draws, so any pixel that differs is the component. */
        val Probe = Color.Magenta
        const val ProbeRgb = 0xFF00FF

        /**
         * How much more ink is still just antialiasing.
         *
         * Everything shifts by a subpixel when spacing changes, and a few
         * hundred edge pixels move with it. A component drawn at its touch
         * target instead of its own size grows by far more than a tenth.
         */
        const val Tolerance = 0.10f
    }
}
