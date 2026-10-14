package io.kontour.ui.theme

import androidx.compose.ui.graphics.Color
import io.kontour.ui.a11y.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The two halves of a state are told apart at every tier.
 *
 * `contrastFailures` walks every foreground against every ground it can land
 * on, and never asked the question a switch asks: **is on distinguishable from
 * off?** The two halves of a switch are two fills side by side, a timeline's
 * stops reached and not reached are two marks in a row, and neither is text on
 * a ground. So when the enhanced tier darkened every boundary toward `primary`
 * — right for a boundary — it made the off track 1.85:1 from the on track, a
 * pair that was 4.81 at the standard tier. The tier meant to make things easier
 * to tell apart had made the commonest control in the library harder.
 *
 * What is held, and to what:
 *
 * | Pair | Enhanced | Standard |
 * |---|---|---|
 * | on (`primary`) vs off (`inactive`) — switch, timeline, page dots | 3:1 | 3.5 |
 * | fill vs track — progress, slider, gauge, meter, knob | 3:1 | today's 10 |
 * | tick vs the track it is cut into | 3:1 | today's 3.9 |
 * | `inactive` on the page, a surface and a well | 4.5:1 | today's 3.0 |
 * | `inactive` on a raised ground | 3:1 | today's 3.0 |
 * | a selection's edge (`accent.solid`) vs its tint and the page | 3:1 | — |
 * | `outlineSubtle` — a menu's border — on the page | 4.5:1 | — |
 *
 * The enhanced column is WCAG 1.4.11's 3:1 for a pair of non-text marks, and
 * the tier's own 4.5 for a mark alone on its ground. The standard column is a
 * floor at what the tier already does: nothing there was wrong, and this is
 * what stops it drifting while the enhanced tier is tuned.
 *
 * One floor in it was lowered on purpose, and it is the first row. It was 4.0,
 * which dark met at 4.01. Dark's `outlineStrong` came up so that it could keep
 * 3:1 against a selected tint made easier to see, and `inactive` is
 * `outlineStrong` at this tier, so the off track came up with it: 3.58 from
 * the on track in dark, 4.49 in light. Still the 3:1 WCAG 1.4.11 asks of a pair
 * of marks with room over it, and the off track is easier to find on the page
 * for the same move — 4.68:1 where it was 4.18.
 *
 * The **track** is the tier's call, not the scheme's: at the standard tier a
 * slider's track is `outline` and a gauge's `surfaceSunken`, both faint beside
 * a near-black fill; at the enhanced tier both are `inactive`. The pairs below
 * read the track the way the components do, through the same rule.
 */
class StatePairContrastTest {

    @Test
    fun theEnhancedTierTellsEveryStateApart() {
        for (dark in listOf(false, true)) {
            val s = kontourColourScheme(dark = dark, contrast = ContrastLevel.High)
            val name = if (dark) "high-contrast dark" else "high-contrast light"
            val track = s.inactive
            val pairs = buildList {
                add(Pair("on (primary) vs off (inactive)", contrastRatio(s.primary, s.inactive)) to 3f)
                add(Pair("fill (primary) vs track", contrastRatio(s.primary, track)) to 3f)
                // A tick on the uncovered track is cut in the page's colour at
                // this tier: `contentSubtle` on it measured 1.6.
                add(Pair("tick (background) vs track", contrastRatio(s.background, track)) to 3f)
                add(Pair("tick (onPrimary) vs fill", contrastRatio(s.onPrimary, s.primary)) to 3f)
                for ((ground, colour) in grounds(s)) {
                    val needed = if (ground == "surfaceRaised") 3f else 4.5f
                    add(Pair("inactive on $ground", contrastRatio(s.inactive, colour)) to needed)
                }
                add(Pair("selection edge vs its tint", contrastRatio(s.accent.solid, s.accent.container)) to 3f)
                for ((ground, colour) in grounds(s)) {
                    add(Pair("selection edge on $ground", contrastRatio(s.accent.solid, colour)) to 3f)
                }
                add(Pair("outlineSubtle on background", contrastRatio(s.outlineSubtle, s.background)) to 4.5f)
                add(Pair("outlineSubtle on surface", contrastRatio(s.outlineSubtle, s.surface)) to 4.5f)
            }
            assertPairs(name, pairs)
        }
    }

    @Test
    fun theStandardTierKeepsWhatItHad() {
        for (dark in listOf(false, true)) {
            val s = kontourColourScheme(dark = dark, contrast = ContrastLevel.Standard)
            val name = if (dark) "dark" else "light"
            assertTrue(
                s.inactive == s.outlineStrong,
                "$name: `inactive` is not `outlineStrong` at the standard tier, so every " +
                    "switch and timeline at that tier just changed colour",
            )
            val pairs = buildList {
                add(Pair("on vs off", contrastRatio(s.primary, s.inactive)) to 3.5f)
                add(Pair("fill vs track (outline)", contrastRatio(s.primary, s.outline)) to 10f)
                add(Pair("tick (contentSubtle) vs track", contrastRatio(s.contentSubtle, s.outline)) to 3.9f)
                for ((ground, colour) in grounds(s)) {
                    add(Pair("inactive on $ground", contrastRatio(s.inactive, colour)) to 3f)
                }
            }
            assertPairs(name, pairs)
        }
    }

    private fun grounds(s: ColourScheme): List<Pair<String, Color>> = listOf(
        "background" to s.background,
        "surface" to s.surface,
        "surfaceSunken" to s.surfaceSunken,
        "surfaceRaised" to s.surfaceRaised,
    )

    private fun assertPairs(scheme: String, pairs: List<Pair<Pair<String, Float>, Float>>) {
        val failing = pairs.filter { (measured, needed) -> measured.second < needed }
        assertTrue(
            failing.isEmpty(),
            "$scheme: " + failing.joinToString("; ") { (measured, needed) ->
                "${measured.first} is ${round(measured.second)}:1, needs $needed"
            },
        )
    }

    private fun round(value: Float): String = ((value * 100).toInt() / 100f).toString()
}
