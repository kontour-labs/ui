package io.kontour.ui.theme

import androidx.compose.ui.graphics.Color
import io.kontour.ui.a11y.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The selected tint, `accent.container`, can be seen, and can be read on.
 *
 * It is the fill of most of the library's selections — a selected chip, row,
 * page, table row and nav item — and a focused filled field's ground. Reported
 * from a phone as hard to see in dark: a selected chip on the page was 1.35:1
 * from it, and 1.17 from a card.
 *
 * ### Two sides, and the tint lives between them
 *
 * A tint is held from both ends. It has to stand off the page, the card and
 * the rows it is selected among, which pushes it away from the ground. And it
 * has to be read on, which pulls it back: in dark the accent itself is drawn
 * on it as a hovered `TextButton`'s label, `contentSubtle` as a focused field's
 * placeholder, and `outlineStrong` has to keep 3:1 against it as the edge a
 * tint can be given. That last is in `contrastFailures`; the first two were
 * checked by nothing, so the window was only ever known from one side.
 *
 * So both ends are asserted, and the window between them is the one the
 * palette has to land in:
 *
 * | | light | dark | floor |
 * |---|---|---|---|
 * | against the page | **1.35** (was 1.21) | **1.52** (was 1.35) | 1.3 |
 * | against `surface` | **1.35** (was 1.21) | **1.33** (was 1.17) | 1.3 |
 * | against the rows, `surfaceSunken` | **1.25** (was 1.12) | **1.43** (was 1.26) | 1.2 |
 * | the weakest text on it | **4.60**, `contentSubtle` | **4.65**, the accent | 4.5 |
 *
 * None of this is the 3:1 WCAG 1.4.11 asks of what identifies a state, and no
 * tint can be: a fill 3:1 from a dark page is a mid-tone, and a mid-tone
 * cannot carry both light and mid-grey text at 4.5. What reaches 3:1 at a
 * selection is the label changing colour, an icon where the component has
 * one, and at the enhanced tier `selectionEdge()`. This file holds what the
 * fill itself contributes, so that it cannot slide back.
 *
 * The separation is asserted at the standard tier only. The enhanced tier
 * draws an edge in the accent round every selected tint, and
 * `StatePairContrastTest` holds that edge to 3:1 against the tint and the page.
 */
class SelectedTintTest {

    private val standard = listOf(
        "light" to lightColourScheme(),
        "dark" to darkColourScheme(),
    )

    private val every = standard + listOf(
        "light/high-contrast" to highContrastLightColourScheme(),
        "dark/high-contrast" to highContrastDarkColourScheme(),
    )

    @Test
    fun theSelectedTintStandsOffWhatItIsSelectedAgainst() {
        val weak = buildList {
            for ((name, s) in standard) {
                val tint = s.accent.container
                measure(name, "accent.container against background", tint, s.background, PageFloor)
                measure(name, "accent.container against surface", tint, s.surface, PageFloor)
                measure(name, "accent.container against surfaceSunken", tint, s.surfaceSunken, RowFloor)
            }
        }
        assertTrue(
            weak.isEmpty(),
            "a selected tint no longer separates from what it sits on:\n" + weak.joinToString("\n") +
                "\nThis is what a selected chip, row or nav item is filled with. Read this " +
                "class's KDoc for how far it can move before the text on it breaks.",
        )
    }

    @Test
    fun everythingDrawnOnTheSelectedTintIsReadable() {
        val weak = buildList {
            for ((name, s) in every) {
                val tint = s.accent.container
                // A selected row's label, and anything else on a tint.
                measure(name, "content on accent.container", s.content, tint, Text)
                // A selected row's supporting line.
                measure(name, "contentMuted on accent.container", s.contentMuted, tint, Text)
                // A focused filled field's placeholder.
                measure(name, "contentSubtle on accent.container", s.contentSubtle, tint, Text)
                // A hovered or pressed `TextButton`'s label.
                measure(name, "accent.solid on accent.container", s.accent.solid, tint, Text)
                // A selected chip's label.
                measure(name, "accent.onContainer on accent.container", s.accent.onContainer, tint, Text)
            }
        }
        assertTrue(
            weak.isEmpty(),
            "text drawn on the selected tint is under 4.5:1:\n" + weak.joinToString("\n") +
                "\nThe tint was most likely lightened (or, in light, deepened) to stand " +
                "further off the page. That is the other end of the same window — see " +
                "this class's KDoc.",
        )
    }

    private fun MutableList<String>.measure(scheme: String, pair: String, fg: Color, bg: Color, floor: Float) {
        val ratio = contrastRatio(fg, bg)
        if (ratio < floor) add("  · $scheme — $pair: ${(ratio * 100).toInt() / 100.0}:1, needs $floor:1")
    }

    private companion object {
        /** Against the page and a card: where a chip, a page dot or a nav item is selected. */
        const val PageFloor = 1.3f

        /** Against the well a group's rows are drawn in: where a row is selected. */
        const val RowFloor = 1.2f

        /** WCAG AA body text, at every tier: AA is what a tint can promise. */
        const val Text = 4.5f
    }
}
