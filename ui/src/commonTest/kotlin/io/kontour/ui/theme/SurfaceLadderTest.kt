package io.kontour.ui.theme

import androidx.compose.ui.graphics.Color
import io.kontour.ui.a11y.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The fills that identify a state separate from what they sit on.
 *
 * [ColourSchemeContrastTest] walks every foreground against every ground and is
 * the load-bearing accessibility test. It has one blind spot by construction:
 * the surface tokens are only ever *grounds* there, so they are never paired
 * against **each other**. A segmented control's thumb is a surface fill on a
 * surface ground, and for the whole life of this library that pairing measured
 * **1.08:1** with nothing anywhere saying so.
 *
 * ### Why the pairing this asserts changed
 *
 * The first fix pushed `surfaceSunken` down until `surface` stood out on it —
 * 1.25–1.30 across the four schemes — and put a 1dp `outlineStrong` border
 * round the thumb, because 1.30 is still nowhere near the 3:1 WCAG 1.4.11 asks
 * of anything identifying a control's state.
 *
 * Both halves were then reported as wrong, and both reports were fair. The
 * border was a visible apology for a fill not doing its job. And the darker
 * well was one token paying for another's problem: `surfaceSunken` is *also*
 * every code block, table, text field and card, so a page of documentation went
 * grey so that a segmented thumb could be seen.
 *
 * So the ground split. [ColourScheme.surfaceSunken] went back to being quiet,
 * and [ColourScheme.surfaceTrack] took the one job that needed a loud ground.
 * This file asserts the pairing that now carries the state, which is **the
 * thumb against the track**:
 *
 * | scheme | thumb | on track | ratio | was |
 * |---|---|---|---|---|
 * | light | `surface` `#FFFFFF` | `#D0D0D0` | 1.54 | 1.08 |
 * | light/high-contrast | `surface` `#FFFFFF` | `#D0D0D0` | 1.54 | 1.14 |
 * | dark | `surfaceRaised` `#302B3B` | `#000000` | 1.53 | 1.08 |
 * | dark/high-contrast | `surfaceRaised` `#302942` | `#000000` | 1.52 | 1.08 |
 *
 * ### Dark is the interesting one
 *
 * The previous round concluded dark's ladder was exhausted, and the reasoning
 * was sound given what it was trying to do: the well was already at black, so
 * there was nowhere further down to go, and the top could not rise because
 * `outlineStrong` has to clear 3:1 against the *lightest* surface.
 *
 * It was moving the wrong end. Dark puts the track at black and lifts the
 * **thumb** to `surfaceRaised`, which is 1.53:1 — better than light manages —
 * off the ramp that had been declared finished. That is why the light and dark
 * numbers above are within 0.01 of each other rather than dark trailing.
 *
 * ### What this floor is, and is not
 *
 * It is a house floor. It is **not** WCAG 1.4.11, and the library no longer
 * claims to meet that here. No two greys in one monochrome ramp reach 3:1 —
 * a white thumb needs a `#959595` track, which is a dark bar rather than a
 * ground — so the choice was a permanent border or a fill that falls short,
 * and the border was the thing a reader objected to.
 *
 * What identifies the selected segment is three things together: this fill, the
 * shadow under the thumb in light, and the label moving `contentMuted` →
 * `content`. `IndicatorVisibilityTest` photographs the first of those and
 * measures the step across a real thumb's edge; the two halves are a pair.
 *
 * ### What is deliberately not asserted
 *
 * - **`surface` on `surfaceSunken`.** Back to 1.08 in light and by design. A
 *   well is a hint that content is inset, not a boundary, and a filled field
 *   that has to shout is a page of grey boxes. That was the whole complaint.
 * - **`surfaceRaised` on `surface`.** In light both are pure white and the
 *   ratio is 1.00, on purpose: a card on a white page is separated by its
 *   shadow, which is what `Elevation` is for.
 */
class SurfaceLadderTest {

    private data class Scheme(val name: String, val colours: ColourScheme)

    private val schemes = listOf(
        Scheme("light", lightColourScheme()),
        Scheme("dark", darkColourScheme()),
        Scheme("light/high-contrast", highContrastLightColourScheme()),
        Scheme("dark/high-contrast", highContrastDarkColourScheme()),
    )

    /**
     * The thumb a segmented control draws, per scheme.
     *
     * Mirrors `SegmentedControl`'s own choice rather than restating a colour:
     * light uses `surface`, dark lifts to `surfaceRaised` because its track is
     * already at black. If that component's rule changes and this does not, the
     * two disagree and this test is measuring a thumb nobody draws.
     */
    private val ColourScheme.thumb: Color
        get() = if (isDark) surfaceRaised else surface

    @Test
    fun theSelectedSegmentSeparatesFromItsTrack() {
        val weak = mutableListOf<String>()
        for ((name, c) in schemes) {
            check(weak, name, "thumb on surfaceTrack", c.thumb, c.surfaceTrack, ThumbFloor)
            check(weak, name, "accent.container on surface", c.accent.container, c.surface, ContainerFloor)
        }
        assertTrue(
            weak.isEmpty(),
            "a fill that identifies a state no longer separates from what it sits on:\n" +
                weak.joinToString("\n") +
                "\nThis is the pairing `contrastFailures` cannot see: it uses the " +
                "surface tokens only as grounds, never against each other. A " +
                "segmented thumb on its track is this number, and it was 1.08:1 in " +
                "every scheme before `surfaceTrack` existed to carry it. Since the " +
                "thumb has no border any more, this fill is most of what says which " +
                "segment is selected — read this class's KDoc before lowering it.",
        )
    }

    private fun check(
        into: MutableList<String>,
        scheme: String,
        pair: String,
        fill: Color,
        ground: Color,
        floor: Float,
    ) {
        val ratio = contrastRatio(fill, ground)
        if (ratio < floor) {
            into += "  · $scheme — $pair: ${(ratio * 100).toInt() / 100.0}:1, needs $floor:1"
        }
    }

    private companion object {
        /**
         * What a fill that *identifies a state* has to clear.
         *
         * The measured floor across the four schemes is 1.52, at the dark
         * enhanced tier, and the others sit at 1.53–1.54. Set just under that
         * rather than at it: the track is bounded from the other side by
         * `contentMuted` staying legible on it at the enhanced tier, so there is
         * roughly one step of room and no point pretending to more.
         */
        const val ThumbFloor = 1.5f

        /**
         * The same for a tinted container, one notch lower.
         *
         * `accent.container` in dark is 1.16:1 against `surface` and cannot go
         * further: at 1.26 `outlineStrong` stops clearing 3:1 against it, and a
         * selected chip *is* bounded — that border is what WCAG is satisfied by
         * there, which is exactly what the segmented thumb gave up.
         */
        const val ContainerFloor = 1.15f
    }
}
