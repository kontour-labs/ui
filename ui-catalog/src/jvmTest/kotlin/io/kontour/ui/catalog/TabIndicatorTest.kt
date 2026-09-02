package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.nav.TabBarDefaults
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Sizing
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The tab bar's marker is the same shape wherever it runs.
 *
 * Reported as the tab bar not fitting in with the rest of the app, and the
 * indicator being "a strange height". It was, and the height was not a design
 * choice — it was a platform constant.
 *
 * ### What it was
 *
 * The pill was `Inset(2.dp, 6.dp)` from the **tab**, and a tab was only as tall
 * as its label plus padding — unless `minimumTouchTarget()` grew it, which it
 * does to `Sizing.minTouchTarget`: **24dp on the JVM, 44 on iOS and web, 48 on
 * Android**. So the same bar drew a 24dp marker on desktop, a 32dp one in a
 * phone browser and a 36dp one on Android, inside a bar that is
 * [TabBarDefaults.Height] on all three.
 *
 * Measured on the desktop showcase, that is a 97 × 24dp lozenge — four times as
 * wide as it is tall — floating in the middle of a 48dp bar with 12dp of empty
 * air above and below it and barely 2dp of pill below the label's ink. Which is
 * the report, exactly.
 *
 * ### What this checks
 *
 * The touch target, which was the hidden variable, is made an explicit one: the
 * bar is rendered at 24dp and at 48dp and the pill must come out the same. That
 * is the property the fix establishes — the tab fills the bar, so the marker is
 * inset from the bar's height rather than from a platform guideline — and the
 * one assertion that would have caught this without a phone.
 *
 * Measured off the pixels because the question is what it *looks* like. The
 * pill is a solid block of `accent.container` and nothing else in the bar is
 * that colour, so its bounding box is exact.
 */
class TabIndicatorTest {

    @Test
    fun theMarkerIsTheSameSizeWhateverThePlatformsTouchTargetIs() {
        val desktop = pill(minTouchTarget = 24.dp)
        val android = pill(minTouchTarget = 48.dp)

        assertTrue(desktop != null && android != null, "no indicator was drawn")
        assertTrue(
            desktop!!.height == android!!.height && desktop.width == android.width,
            "the indicator came out ${desktop.width}×${desktop.height}dp with a " +
                "24dp touch target and ${android.width}×${android.height}dp with a " +
                "48dp one — its size is following the platform's guideline rather " +
                "than the bar it sits in.",
        )
    }

    @Test
    fun theMarkerFillsTheBarLessOneGridStep() {
        val measured = requireNotNull(pill(minTouchTarget = 24.dp)) { "no indicator" }

        // 48dp of bar less 4dp of air top and bottom. The number matters less
        // than what it is derived from: the bar, not the label.
        val expected = TabBarDefaults.Height.value - 2 * Air
        assertTrue(
            kotlin.math.abs(measured.height - expected) <= 1.0,
            "the indicator is ${measured.height}dp tall in a " +
                "${TabBarDefaults.Height.value}dp bar, against the ${expected}dp " +
                "that leaves one grid step of air above and below it.",
        )
        // And a pill rather than a slot: the rail's marker is 64×56 and the nav
        // bar's is 40×40, so a marker four times as wide as it is tall was the
        // other half of "it doesn't fit in".
        assertTrue(
            measured.width / measured.height < 3.0,
            "the indicator is ${measured.width}×${measured.height}dp, which is " +
                "${"%.1f".format(measured.width / measured.height)} times as wide " +
                "as it is tall — a slot rather than a pill.",
        )
    }

    @Test
    fun aBarTooNarrowForItsLabelsStillDrawsItsMarker() {
        // Why this is here rather than in `WidthSweepTest`: turning the hairline
        // rule off by default overturns a decision the docs record, and the
        // reason given for keeping it was that a bar squeezed narrow enough to
        // clip its labels "draws nothing at all" without it. That was true of a
        // marker inset from the label's own box — three tabs sharing 48dp is
        // 16dp each against 32dp of horizontal padding, so every label
        // ellipsises to nothing and the rule was the only ink left.
        //
        // A marker inset from the *bar* is 40dp tall whatever the width, so the
        // bar still draws. `WidthSweepTest` requires ink from 48dp up; this
        // asks the same question of the one component whose answer changed.
        val narrow = requireNotNull(pill(minTouchTarget = 24.dp, width = 48)) {
            "a 48dp tab bar drew nothing at all — the hairline rule was holding " +
                "that floor up and turning it off has dropped it"
        }
        // Still the bar's height, not a fraction of it: the width is what ran
        // out, and the marker's height has nothing to do with the width.
        val expected = TabBarDefaults.Height.value - 2 * Air
        assertTrue(
            kotlin.math.abs(narrow.height - expected) <= 1.0,
            "the marker came out ${narrow.width}×${narrow.height}dp in a 48dp-wide " +
                "bar, against the ${expected}dp of height it has at any other width",
        )
    }

    /** Width and height, in dp, of the one block of `accent.container` on screen. */
    private fun pill(minTouchTarget: Dp, width: Int = 320): Size? {
        var image: BufferedImage? = null
        Scene(width = (width + 16) * Density.toInt(), height = 160) {
            // Nested inside the harness's own theme, which is the only way to
            // drive the platform constant this exists to remove.
            Themed(minTouchTarget) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(8.dp)) {
                    TabBar {
                        Tab(selected = false, onClick = {}, key = 0) { +"Departures" }
                        Tab(selected = true, onClick = {}, key = 1) { +"Route map" }
                        Tab(selected = false, onClick = {}, key = 2) { +"Alerts" }
                    }
                }
            }
        }.use { scene -> image = scene.frames(40) }
        val frame = requireNotNull(image)

        // The commonest colour that is neither the white page nor the text: the
        // pill is the only large flat area left once those two are out.
        val counts = mutableMapOf<Int, Int>()
        for (y in 0 until frame.height) for (x in 0 until frame.width) {
            val rgb = frame.getRGB(x, y) and 0xFFFFFF
            counts[rgb] = (counts[rgb] ?: 0) + 1
        }
        val fill = counts.entries
            .filter { it.key != 0xFFFFFF && it.value > 200 }
            .maxByOrNull { it.value }?.key ?: return null

        var minX = Int.MAX_VALUE
        var maxX = -1
        var minY = Int.MAX_VALUE
        var maxY = -1
        for (y in 0 until frame.height) for (x in 0 until frame.width) {
            if ((frame.getRGB(x, y) and 0xFFFFFF) == fill) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return Size(
            width = (maxX - minX + 1) / Density,
            height = (maxY - minY + 1) / Density,
        )
    }

    @Composable
    private fun Themed(minTouchTarget: Dp, content: @Composable () -> Unit) {
        KontourTheme(sizing = Sizing(minTouchTarget = minTouchTarget), content = content)
    }

    private data class Size(val width: Double, val height: Double)

    private companion object {
        /** [Scene]'s default. */
        const val Density = 2.0

        /** `Theme.spacing.xxs`, the inset the bar gives its marker. */
        const val Air = 4f
    }
}
