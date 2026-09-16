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
import kotlin.test.assertEquals
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
 * | device font scale | was | +Haptics | +stacking | added by |
 * |---|---|---|---|---|
 * | 100% | 728dp | 826dp | **826dp** | Haptics: a label and a four-segment control |
 * | 130% | 751dp | 854dp | **854dp** | the same |
 * | 200% | 863dp | 978dp | **1273dp** | two controls laying their options out in rows |
 *
 * The third column is the one worth reading. `SegmentedControl` stacks its
 * options into full-width rows when its labels stop fitting, rather than cutting
 * them — so at 200% both `Haptics` and `Input modality` become four rows each,
 * and the panel gains about 150dp apiece. **Nothing changes at 100% or 130%**,
 * where the labels still fit side by side, which is why those two columns are
 * identical.
 *
 * That is the trade taken deliberately: a reader at 200% gets a much longer panel
 * and can read every option in it, where before they got a shorter panel with
 * `Keyboar…` and `Standar…` in it — on the one accessibility setting whose whole
 * purpose is to make text legible.
 *
 * A Pixel-class phone is 915dp tall and gives up roughly 48dp of that to its
 * status bar and gesture inset. Before this round 200% sat inside the last 20dp
 * of that window; **it is now past the bottom of it by about 110dp.** A 640dp
 * phone — a 5" device, which `minSdk = 29` still admits — cropped the panel at
 * every text size before this round too: it measured about 672dp when the file
 * was written.
 *
 * ### What the Haptics row cost, and why it was spent anyway
 *
 *
 * Roughly a hundred dp at every scale, for one label and one `SegmentedControl` —
 * the same shape as Text size and Input modality above it, and the same cost. It
 * is the most expensive row in the panel per setting it offers, and the
 * alternative that would be cheaper is a `Select`, which is one row instead of
 * two and hides the four options behind a tap.
 *
 * The control stays as it is, for two reasons that are both about this panel
 * rather than about haptics. Four levels visible at once is the *only* way a
 * reader compares them, and comparing is the whole gesture here: somebody
 * changing this is deciding between "less" and "none", not selecting a known
 * value. And the third `SegmentedControl` in a column of three is a row a reader
 * already knows how to read, where a lone dropdown among them would be a fourth
 * idiom on a panel that has three.
 *
 * What it does mean is that **the scroll is now load-bearing at every font scale
 * on a small phone, and at 200% on any phone.** That was already true at 200% and
 * is now true more widely, and the next row added here should be the one that
 * finally reorders the panel by what a reader reaches for rather than appending
 * to it.
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
                "\nAt 200% this panel already runs past the bottom of a " +
                "Pixel-class window, and past a 5\" one at every text size. It " +
                "scrolls, so nothing is unreachable — but the rows down there " +
                "are Text size, Haptics and Input modality, which are the ones a " +
                "reader at 200% type opened the panel to change. Raise a ceiling " +
                "deliberately, with a note saying what moved.",
        )
    }

    /**
     * And at the site's popover width, where narrowing now *does* make it taller.
     *
     * `PopoverPanel` caps its width at 320dp and spends 16dp a side on padding.
     * This used to assert that narrowing could not make the panel taller, on the
     * reasoning that "a label that wrapped, or a segmented control that grew a
     * second line, would show up here and nowhere else".
     *
     * **That is now the intended behaviour rather than the fault.** A segmented
     * control whose labels stop fitting lays them out in rows, and the thing that
     * makes labels stop fitting is exactly a narrower track. So the assertion is
     * inverted into a measurement: how much taller, pinned, so that a change in
     * the threshold shows up as a number moving rather than as a boolean that was
     * always going to be true.
     *
     * 144dp is one four-option control stacking — `4 × 48dp` of rows against the
     * 48dp it occupied in a line. Which control it is depends on this harness
     * rather than on the site: it renders `SettingsSheetContent`, which carries
     * the *sheet's* own padding, so at 288dp the control ends up with a 244dp
     * track where the real popover gives it 276. `SettingsLabelFitTest` measures
     * the real one, and says every label still fits there at 100%.
     */
    @Test
    fun narrowingToThePopoverDoesNotMakeItTaller() {
        val wide = heightDp(widthDp = 380, fontScale = 1f)
        val narrow = heightDp(widthDp = 288, fontScale = 1f)
        assertEquals(
            NarrowGrowth, narrow - wide,
            "the panel is ${narrow}dp at the popover's 288dp of content width " +
                "against ${wide}dp at the sheet's 380dp, a difference of " +
                "${narrow - wide}dp where ${NarrowGrowth}dp was measured. That " +
                "difference is a segmented control stacking, so a change in it is " +
                "a change in when the stacking fires — which is worth looking at " +
                "rather than worth a boolean.",
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
        val Ceilings = listOf(1f to 826, 1.3f to 854, 2f to 1273)

        /**
         * How much taller the panel is at the popover's width than at the sheet's.
         *
         * One four-option `SegmentedControl` laying its options out in rows:
         * `4 × 48dp` against the 48dp a single line occupies.
         */
        const val NarrowGrowth = 144
    }
}
