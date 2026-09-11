package io.kontour.ui.theme

import androidx.compose.ui.graphics.Color
import io.kontour.ui.a11y.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The surface tokens separate from each other, not just from the text on them.
 *
 * [ColourSchemeContrastTest] walks every foreground against every ground and is
 * the load-bearing accessibility test. It has one blind spot by construction:
 * the four surface tokens are only ever *grounds* there, so they were never
 * paired against **each other**. `surface` on `surfaceSunken` — a segmented
 * thumb against its own track, a filled field against the page — measured
 * **1.08:1** in every scheme, and nothing said so.
 *
 * That is this file's job, and it is a house floor rather than a WCAG one. WCAG
 * 1.4.11 is satisfied by the *boundary*: `outlineStrong` clears 3:1 against
 * every fill and `SegmentedControl` draws it, which is what Round 31 landed. A
 * fill that also separates on its own is the belt to that boundary's braces —
 * it is what makes a filled text field look like a field before you have found
 * its edge.
 *
 * ### What moved, and what it cost
 *
 * | scheme | `surface` on `surfaceSunken` | before |
 * |---|---|---|
 * | light | 1.30 | 1.08 |
 * | light/high-contrast | 1.30 | 1.14 |
 * | dark | 1.29 | 1.08 |
 * | dark/high-contrast | 1.25 | 1.08 |
 *
 * Light paid for it in three tokens, because darkening the well drags the things
 * that have to stay legible *on* the well down with it: `outlineStrong`
 * #8A8A8A → #818181 (3.01:1 on the new well), `contentSubtle` #6B6B6B → #646464
 * (4.57:1), and `accent.container` #EFF6FF → #D5E4F9, which has to darken too or
 * a selected chip is the only fill left that does not separate.
 *
 * Dark paid for it in none. The well went *down* to black instead of the
 * surfaces going up, so every text ratio in the scheme improved rather than
 * tightening.
 *
 * ### Why it stops here
 *
 * Both modes are at their ceiling, and the ceilings are different constraints.
 *
 * **Light converges from two sides.** Darkening `surfaceSunken` pushes
 * `outlineStrong` darker to keep its 3:1 on it; darkening `accent.container`
 * pushes it *toward* `outlineStrong` from the other side. At 1.35 they meet:
 * `outlineStrong` lands at exactly 3.00 against the container and the next step
 * fails. Measured, not guessed.
 *
 * **Dark is capped by `outlineStrong` as well, from the top.** It has to clear
 * 3:1 against the *lightest* surface, so `surfaceRaised` cannot rise — at
 * `#332E40` it reads 2.92:1 and `contentSubtle` fails with it. With the top of
 * the ramp pinned and the bottom already at black, 1.29 is the whole of the
 * range.
 *
 * ### What is deliberately not asserted
 *
 * - **`surfaceRaised` on `surface`.** In light they are both pure white and the
 *   ratio is 1.00, on purpose: a card on a white page is separated by its
 *   shadow, which is what `Elevation` is for. Asserting a floor here would mean
 *   a grey page.
 * - **`surfaceSunken` on `background`.** At the dark enhanced tier the page is
 *   pure black and so is the well, because there is nothing below black to go
 *   to. That tier draws a real border on a filled field — see `TextFieldStyles`
 *   — so the fill is not carrying it alone.
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
    fun aFillSeparatesFromTheGroundItSitsOn() {
        val weak = mutableListOf<String>()
        for ((name, c) in schemes) {
            check(weak, name, "surface on surfaceSunken", c.surface, c.surfaceSunken, ThumbFloor)
            check(weak, name, "accent.container on surface", c.accent.container, c.surface, ContainerFloor)
        }
        assertTrue(
            weak.isEmpty(),
            "a fill no longer separates from what it sits on:\n" + weak.joinToString("\n") +
                "\nThis is the pairing `contrastFailures` cannot see: it uses the " +
                "surface tokens only as grounds, never against each other. " +
                "A segmented thumb on its track and a filled field on the page are " +
                "both this number, and it was 1.08:1 in every scheme until the " +
                "surface ladder was retuned. Lowering it again is a decision, not a " +
                "tidy-up: read this class's KDoc for what the ceilings are and what " +
                "the last retune cost.",
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
         * The measured floor across the four schemes is 1.25, at the dark
         * enhanced tier, and the others sit at 1.29–1.30. Set to the floor
         * rather than below it: there is no slack to give away, because the
         * ceilings above are within 0.05 of it.
         */
        const val ThumbFloor = 1.25f

        /**
         * The same for a tinted container, one notch lower.
         *
         * `accent.container` in dark is 1.16:1 against `surface` and cannot go
         * further: at 1.26 `outlineStrong` stops clearing 3:1 against it, and the
         * boundary is the thing WCAG actually requires.
         */
        const val ContainerFloor = 1.15f
    }
}
