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
 * ### Why the pairing this asserts changed, twice
 *
 * The first fix pushed `surfaceSunken` down until `surface` stood out on it —
 * 1.25-1.30 across the four schemes — and put a 1dp `outlineStrong` border
 * round the thumb, because 1.30 is still nowhere near the 3:1 WCAG 1.4.11 asks
 * of anything identifying a control's state.
 *
 * Both halves were then reported as wrong, and both reports were fair. The
 * border was a visible apology for a fill not doing its job. And the darker
 * well was one token paying for another's problem: `surfaceSunken` is *also*
 * every code block, table, text field and card, so a page of documentation went
 * grey so that a segmented thumb could be seen.
 *
 * So the ground split, and a track token took the one job that needed a loud
 * ground. That held the floor and cost something else: a segmented control and
 * a text field on one screen were two greys apart and read as two design
 * systems. Which is the report that moved it again — this time from the other
 * end. The ground went back to [ColourScheme.surfaceSunken] for good, the track
 * token is gone, and [ColourScheme.surfaceIndicator] lifts the **thumb** off
 * the well instead:
 *
 * | scheme | thumb | on ground | ratio | was |
 * |---|---|---|---|---|
 * | dark | `#403852` | `#1A1820` | **1.59** | 1.53 |
 * | dark/high-contrast | `#453D58` | `#0B0910` | **1.94** | 1.52 |
 * | light | `#FFFFFF` | `#F6F6F6` | *1.08* | 1.54 |
 * | light/high-contrast | `#FFFFFF` | `#F0F0F0` | *1.11* | 1.54 |
 *
 * ### Light is not asserted, and that is the cost
 *
 * Both dark schemes come out **further apart than the arrangement they
 * replace**, which is the part worth knowing: the thumb was always the better
 * end to move, and the round that declared dark's ladder exhausted was only
 * ever trying to move the bottom of it.
 *
 * Light cannot be moved at all. White is the top of the ramp and the well is
 * `#F6F6F6`; no value of `surfaceIndicator` changes 1.08:1, so asserting a
 * floor there would be asserting something no palette can satisfy.
 *
 * So light does not carry it with a fill. It carries it with the shadow —
 * `elevation.medium`, raised from `low` for exactly this — and with the label
 * going `contentMuted` to `content`, and the whole edge measures **1.17:1**.
 * The shadow is worth 0.09 of that: a soft shadow on a near-white ground has
 * very little to darken, and this is the measurement rather than an estimate.
 *
 * A 1dp `outlineStrong` hairline was built and measured and reaches **3.60:1**,
 * clearing WCAG 1.4.11 outright. It was rejected on the rendering: on a
 * near-white ground that line is a hard dark stroke around the selected
 * segment, which is the visible apology for a fill not doing its job that an
 * earlier round already removed once. The number was better and the control was
 * worse. `IndicatorVisibilityTest` holds light to 1.15 and dark to 1.45, which
 * is two floors because there are two situations, and neither is 3:1.
 *
 * `IndicatorVisibilityTest` photographs a real control and measures the step
 * across the thumb's edge, which is the only instrument that can see a line or
 * a shadow at all. A token pair cannot, so this file asserts only the fill, and
 * only where a fill is the answer.
 *
 * ### What is deliberately not asserted
 *
 * - **`surface` on `surfaceSunken`.** 1.08 in light and by design. A well is a
 *   hint that content is inset, not a boundary, and a filled field that has to
 *   shout is a page of grey boxes. That was the whole complaint.
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

    @Test
    fun theSelectedSegmentSeparatesFromItsGround() {
        val weak = mutableListOf<String>()
        for ((name, c) in schemes) {
            // Dark only. Light's thumb is white on a near-white well and no
            // palette can change that — see this class's KDoc for what carries
            // it there instead, and `IndicatorVisibilityTest` for the
            // measurement that can see it.
            if (c.isDark) {
                check(
                    weak, name, "surfaceIndicator on surfaceSunken",
                    c.surfaceIndicator, c.surfaceSunken, ThumbFloor,
                )
            }
            check(weak, name, "accent.container on surface", c.accent.container, c.surface, ContainerFloor)
        }
        assertTrue(
            weak.isEmpty(),
            "a fill that identifies a state no longer separates from what it sits on:\n" +
                weak.joinToString("\n") +
                "\nThis is the pairing `contrastFailures` cannot see: it uses the " +
                "surface tokens only as grounds, never against each other. A " +
                "segmented thumb on its well is this number, and it was 1.08:1 in " +
                "every scheme before `surfaceIndicator` existed to carry it. Since " +
                "the thumb has no border any more, this fill is most of what says " +
                "which segment is selected in dark — read this class's KDoc before " +
                "lowering it.",
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
         * The measured floor across the two schemes this applies to is 1.59,
         * at plain dark, with the enhanced tier at 1.94. Kept at 1.5 rather
         * than raised to meet them: the number is the floor a *consumer's*
         * scheme has to clear, and the built-in ones having headroom over it is
         * not a reason to spend that headroom. GTurbo, the one out-of-tree
         * theme in the tree, sits at 1.57.
         *
         * Light is not held to this at all, and cannot be — see the KDoc.
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
