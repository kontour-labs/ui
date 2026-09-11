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
 * The panel scrolls now, and the ceiling stays. Those are not in tension: what
 * this measures is how *long* the panel is, and length is still a cost even when
 * it is reachable. Every row past the fold is a row somebody has to scroll to
 * find, on the one screen whose job is to be found quickly.
 *
 * It was written for a harder reason. A bottom sheet used to crop what did not
 * fit — the content was measured at `maxHeight = Constraints.Infinity` and then
 * placed in the room the surface had — so a row added here could push Text size
 * and Input modality off the bottom of a phone in silence, and a `verticalScroll`
 * could not help because its viewport was infinite too. That is fixed in `:ui`
 * (`SheetContentConstraintsTest`), and `SettingsSheetContent` scrolls. The site's
 * popover still has no height cap.
 *
 * The numbers below were taken when the round added a row — "Follow device", the
 * way back from a pinned setting — and they are the reason this file exists: a
 * row added to a list nobody has measured is a change you look at rather than
 * know.
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
 * It never could honestly assert "fits a phone", because at 200% on a small one
 * it does not — and now that the sheet scrolls, it does not need to. What it can
 * do is stop the panel growing by accident: the ceiling is what it measures
 * today, and a change that needs more room has to come here and say so.
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
                "\nAt 200% this panel already runs to within about 20dp of a " +
                "Pixel-class window, and past the bottom of a 5\" one. It " +
                "scrolls, so nothing is unreachable — but the rows down there " +
                "are Text size and Input modality, the two a reader at 200% type " +
                "opened the panel to change. Raise a ceiling deliberately, with " +
                "a note saying what moved.",
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
