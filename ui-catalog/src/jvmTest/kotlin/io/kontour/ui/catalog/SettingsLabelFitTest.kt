package io.kontour.ui.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import io.kontour.ui.demo.theme.demoThemes
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which settings labels survive a phone's text-size setting, and which do not.
 *
 * Reported from a phone: the segmented control in the Text size panel "doesn't
 * always properly adapt". It adapts exactly as documented — `SegmentedControl`
 * divides its width evenly and ellipsises a label that does not fit, and its
 * KDoc says so. The trouble is what that produces: at 200% type every option in
 * the Text size control arrived as `10…`, `20…`, which is the one control a
 * reader at 200% has opened the panel to use.
 *
 * ### Measured rather than looked at
 *
 * A `TextMeasurer` gives the label's width in dp at a given font scale, which is
 * the number the ellipsis decision is made from — so this compares it against
 * the segment width directly instead of hunting for an ellipsis in pixels. Both
 * real surfaces are covered: the gallery's sheet is 380dp wide with 16dp of
 * padding a side, and the site's popover caps at 320dp with the same padding.
 * Six dp of track padding comes off each end inside that.
 *
 * ### What the numbers said
 *
 * | control | surface | scale | widest | width | segment |
 * |---|---|---|---|---|---|
 * | Text size, with `%` | sheet | 200% | `200%` | 71.5dp | 67.2dp |
 * | Text size, without | sheet | 200% | `Auto` | 61.0dp | 67.2dp |
 * | Input modality | popover | 130% | `Keyboard` | 81.0dp | 69.0dp |
 * | Input modality | sheet | 200% | `Keyboard` | 124.0dp | 84.0dp |
 *
 * Dropping the per-cent sign is what the phone needed: on the sheet — the
 * surface a phone opens — Text size goes from every option reading `20…` at
 * 200% to every option readable. The site's narrower popover still cuts one
 * label there, and it is now `Auto` alone rather than all five.
 *
 * **Input modality cannot be fixed this way and is not fixed here**: four word
 * labels do not fit four 84dp segments at 200% however they are spelled, and
 * `Keys` is not what that setting is called. That wants an adaptive fallback in
 * the control — segments sized to content, or stacked when they cannot be — and
 * that is a change to `:ui`, not a change to a string.
 *
 * So this is a table rather than a pass/fail: every combination is pinned as it
 * measures today, and both directions of change have to be stated here.
 */
class SettingsLabelFitTest {

    /** The widest label in [labels] and its width in dp at [fontScale]. */
    private fun widest(labels: List<String>, fontScale: Float): Pair<String, Float> {
        var worst = "" to 0f
        val scene = ImageComposeScene(width = 400, height = 200, density = Density(2f, fontScale)) {
            KontourTheme {
                CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                    val measurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    for (label in labels) {
                        val width = measurer
                            .measure(label, style = Theme.typography.labelMedium)
                            .size.width
                        val dp = with(density) { width.toDp().value }
                        if (dp > worst.second) worst = label to dp
                    }
                }
            }
        }
        try {
            repeat(3) { scene.render(16_000_000L * it) }
        } finally {
            scene.close()
        }
        return worst
    }

    /**
     * Every control on both surfaces at all three text sizes, pinned.
     *
     * The assertion is over the whole table at once rather than one per row: a
     * label that starts fitting matters as much as one that stops, and reading
     * the two lists side by side is how you see which.
     */
    @Test
    fun theSettingsLabelsMeasureWhatTheyMeasured() {
        val controls = listOf(
            "Theme" to demoThemes.map { it.name },
            "Text size" to textScales.map { it.first },
            "Input modality" to inputModalities.map { it.first },
        )
        val rows = buildList {
            for ((surface, track) in Surfaces) {
                for (scale in listOf(1f, 1.3f, 2f)) {
                    for ((control, labels) in controls) {
                        val (label, width) = widest(labels, scale)
                        val segment = track / labels.size
                        add(
                            "$surface $control at $scale: '$label' " +
                                "${width}dp in ${segment}dp — " +
                                if (width <= segment) "fits" else "ELLIPSISED"
                        )
                    }
                }
            }
        }
        assertEquals(
            Pinned.joinToString("\n"),
            rows.joinToString("\n"),
            "a settings label changed how it fits its segment. Every row is a " +
                "measurement, not a target: a label that started fitting is as " +
                "much a change as one that stopped, and `ELLIPSISED` means a " +
                "reader at that text size sees the label cut to an ellipsis.",
        )
    }

    private companion object {
        /**
         * The two panels' track widths in dp.
         *
         * Sheet: 380dp phone, 16dp of `Column` padding a side, 6dp of track
         * padding each end. Popover: `PopoverPanel` caps at 320dp and spends
         * 16dp a side, then the same 6dp each end.
         */
        val Surfaces = listOf("sheet" to 336f, "popover" to 276f)

        /** What the table reads today. */
        val Pinned = listOf(
            "sheet Theme at 1.0: 'Kontour' 51.5dp in 168.0dp — fits",
            "sheet Text size at 1.0: 'Auto' 30.5dp in 67.2dp — fits",
            "sheet Input modality at 1.0: 'Keyboard' 62.0dp in 84.0dp — fits",
            "sheet Theme at 1.3: 'Kontour' 67.0dp in 168.0dp — fits",
            "sheet Text size at 1.3: 'Auto' 40.0dp in 67.2dp — fits",
            "sheet Input modality at 1.3: 'Keyboard' 81.0dp in 84.0dp — fits",
            "sheet Theme at 2.0: 'Kontour' 103.0dp in 168.0dp — fits",
            "sheet Text size at 2.0: 'Auto' 61.0dp in 67.2dp — fits",
            "sheet Input modality at 2.0: 'Keyboard' 124.0dp in 84.0dp — ELLIPSISED",
            "popover Theme at 1.0: 'Kontour' 51.5dp in 138.0dp — fits",
            "popover Text size at 1.0: 'Auto' 30.5dp in 55.2dp — fits",
            "popover Input modality at 1.0: 'Keyboard' 62.0dp in 69.0dp — fits",
            "popover Theme at 1.3: 'Kontour' 67.0dp in 138.0dp — fits",
            "popover Text size at 1.3: 'Auto' 40.0dp in 55.2dp — fits",
            "popover Input modality at 1.3: 'Keyboard' 81.0dp in 69.0dp — ELLIPSISED",
            "popover Theme at 2.0: 'Kontour' 103.0dp in 138.0dp — fits",
            "popover Text size at 2.0: 'Auto' 61.0dp in 55.2dp — ELLIPSISED",
            "popover Input modality at 2.0: 'Keyboard' 124.0dp in 69.0dp — ELLIPSISED",
        )
    }
}
