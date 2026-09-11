package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How tall the display settings are, and a ceiling so the next row is deliberate.
 *
 * **A bottom sheet crops its content; it does not scroll it.** `BottomSheet`
 * measures the content once at `maxHeight = Constraints.Infinity` — it has to,
 * so that the `Expanded` detent can mean "as tall as the content" — and then
 * places that same placeable in whatever room the surface has. The child
 * therefore believes it has infinite height, so anything past the window's
 * bottom edge is clipped rather than reachable, and a scroller inside cannot
 * help because its viewport was infinite when it was measured. The site's
 * popover has no height cap either.
 *
 * That makes the panel's height a real constraint rather than a matter of taste,
 * and nothing measured it. This round adds a row — "Follow device", the way back
 * from a pinned setting — and a row added to a list that silently truncates is
 * exactly the change that wants a number beside it rather than a look.
 *
 * ### What was measured
 *
 * The gallery's sheet, 380dp wide:
 *
 * | device font scale | height |
 * |---|---|
 * | 100% | 728dp |
 * | 130% | 751dp |
 * | 200% | 863dp |
 *
 * A Pixel-class phone is 915dp tall and gives up roughly 48dp of that to its
 * status bar and gesture inset, so **200% is already inside the last 20dp of the
 * window**. A 640dp phone — a 5" device, which `minSdk = 29` still admits —
 * crops the panel at every text size, and did before this round too: the panel
 * measured about 672dp without the new row.
 *
 * ### So this is a ratchet, not a guarantee
 *
 * It cannot honestly assert "fits a phone", because at 200% on a small one it
 * does not. What it can do is stop the panel growing by accident: the ceiling is
 * what it measures today, and a change that needs more room has to say so here —
 * and by then the sheet has to have learnt to scroll, which is a change to `:ui`
 * and a different piece of work.
 */
class SettingsPanelHeightTest {

    /** The panel's height in dp at [widthDp] wide and a device font scale of [fontScale]. */
    private fun heightDp(widthDp: Int, fontScale: Float): Int {
        var height = 0
        val scene = ImageComposeScene(
            width = widthDp * 2,
            height = 4000,
            density = Density(2f, fontScale),
        ) {
            KontourTheme {
                Box(
                    Modifier
                        .width(widthDp.dp)
                        .onGloballyPositioned { height = it.size.height }
                ) {
                    SettingsSheetContent(CatalogSettings(), systemDark = false)
                }
            }
        }
        try {
            repeat(6) { scene.render(16_000_000L * it) }
        } finally {
            scene.close()
        }
        return height / 2
    }

    /**
     * The gallery's sheet, at the phone width its compact layout is written for.
     *
     * `textScale` is left at Auto, so the scene's own font scale is what the
     * panel is drawn at — which is the case that matters. A reader at 200% did
     * not open this sheet and choose 200%; their phone chose it, and the sheet
     * they open to change it back is the one that has to fit.
     */
    @Test
    fun theSheetHasNotGrown() {
        val over = mutableListOf<String>()
        for ((fontScale, ceiling) in Ceilings) {
            val height = heightDp(widthDp = 380, fontScale = fontScale)
            if (height > ceiling) over += "  · font scale $fontScale: ${height}dp, ceiling ${ceiling}dp"
        }
        assertTrue(
            over.isEmpty(),
            "the display settings grew:\n" + over.joinToString("\n") +
                "\nA bottom sheet crops what does not fit rather than scrolling " +
                "to it, and the rows at the bottom of this panel are Text size " +
                "and Input modality — the two a reader at 200% type most needs " +
                "to reach. At 200% the panel is already within about 20dp of a " +
                "Pixel-class window. Raise a ceiling only with something that " +
                "makes the extra room reachable.",
        )
    }

    /**
     * And at the site's popover width, which is narrower and no taller.
     *
     * `PopoverPanel` caps its width at 320dp and spends 16dp a side on padding.
     * The check is that narrowing does not make the panel *taller* — a label
     * that wrapped, or a segmented control that grew a second line, would show
     * up here and nowhere else.
     */
    @Test
    fun narrowingToThePopoverDoesNotMakeItTaller() {
        val wide = heightDp(widthDp = 380, fontScale = 1f)
        val narrow = heightDp(widthDp = 288, fontScale = 1f)
        assertTrue(
            narrow <= wide,
            "the panel is ${narrow}dp at the popover's 288dp of content width " +
                "against ${wide}dp at the sheet's 380dp. Something wrapped when " +
                "it got narrow, which on the site means a popover that runs " +
                "further down the window than it did before.",
        )
    }

    private companion object {
        /**
         * What the panel measures today, to the dp, at 380dp wide.
         *
         * Exact rather than rounded up: a ceiling with slack in it is a ceiling
         * that lets a row land unnoticed, which is the whole thing this is here
         * to prevent. Legitimate growth edits these numbers and says why.
         */
        val Ceilings = listOf(1f to 728, 1.3f to 751, 2f to 863)
    }
}
