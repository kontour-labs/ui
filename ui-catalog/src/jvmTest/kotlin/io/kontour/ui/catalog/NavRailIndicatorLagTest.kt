package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavRail
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The selection pill grows at the same rate as the rail it is in.
 *
 * Reported as the indicator not collapsing at the same rate as the surface
 * around it. The rail's width is an `animateDpAsState` read in composition and
 * applied by `Modifier.width` in the same frame; the pill's rectangle takes a
 * longer road to the screen — the row's `onGloballyPositioned` reports it during
 * *that* frame's layout, which invalidates `SelectionIndicatorBox`, which
 * recomposes on the next frame, whose `LaunchedEffect` restarts and snaps the
 * `Animatable`, which invalidates it again. The marker is measured and placed
 * two frames after the geometry it is drawn at.
 *
 * ### Nothing in this suite had ever watched the pill move
 *
 * `NavRailGrowthTest` and `NavRailStillnessTest` both render the rail with
 * `indicatorColour = Color.Transparent`, on purpose: they measure ink and icon
 * edges, and a pill is a large moving block of colour in the way of both.
 * `SelectionIndicatorGeometryTest` and `TabIndicatorTest` sample only at rest,
 * one of them with `reduceMotion = true`. So the lag was invisible to every test
 * that exists, which is why this one starts by making the pill the brightest
 * thing on the rail.
 *
 * ### Self-calibrating, so the measurement owns no constants
 *
 * The pill is `Inset(horizontal = xxs)` on a `fillMaxWidth` row, but the exact
 * gap between its right edge and the rail's depends on padding this test should
 * not have to know. So the gap is measured once with everything at rest and the
 * lag is what the gap does during the animation: zero if the pill keeps up,
 * positive if it is behind.
 */
class NavRailIndicatorLagTest {

    private val items = listOf(
        NavItem(label = "Nearby", icon = Tabler.Outline.MapPin, onClick = {}),
        NavItem(label = "Routes", icon = Tabler.Outline.Bus, onClick = {}),
        NavItem(label = "Saved", icon = Tabler.Outline.Star, onClick = {}),
    )

    @Test
    fun thePillKeepsUpWithTheRail() {
        var expanded by mutableStateOf(false)
        var restingGap = -1
        val lags = mutableListOf<Int>()
        val growth = mutableListOf<Int>()

        Scene(width = 900, height = 700) {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                NavRail(
                    items = items,
                    selectedIndex = 0,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    // The one thing on the rail drawn in this colour, so the
                    // pill's edge can be found without a band to get wrong.
                    indicatorColour = Pill,
                )
            }
        }.use { scene ->
            val settled = scene.frames(30)
            restingGap = settled.railRight() - settled.pillRight()

            expanded = true
            var previousRail = settled.railRight()
            repeat(Frames) {
                val frame = scene.frame()
                val rail = frame.railRight()
                val pill = frame.pillRight()
                if (rail > 0 && pill > 0) {
                    lags += (rail - restingGap) - pill
                    growth += rail - previousRail
                }
                previousRail = rail
            }
        }

        assertTrue(restingGap > 0, "the pill was never drawn, so nothing below is a measurement")
        assertTrue(
            growth.max() > 4,
            "the rail never grew by more than ${growth.max()}px in a frame, so this " +
                "test would report no lag whatever the indicator did",
        )

        val worst = lags.max()
        assertTrue(
            worst <= Tolerance,
            "the pill's right edge fell ${worst}px behind the rail's while it was " +
                "expanding, against a tolerance of ${Tolerance}px. The rail grew at " +
                "most ${growth.max()}px in a frame, so that is about " +
                "${"%.1f".format(worst.toDouble() / growth.max())} frames of lag — the " +
                "pill is being drawn at geometry the rail has already left behind. " +
                "Gap at rest ${restingGap}px; worst frames: " +
                lags.withIndex().sortedByDescending { it.value }.take(5)
                    .joinToString { "f${it.index}=${it.value}" },
        )
    }

    /** How far right the rail's surface reaches, which is how wide it is. */
    private fun BufferedImage.railRight(): Int {
        val y = height / 2
        for (x in 0 until width) if (isPage(getRGB(x, y))) return x
        return width
    }

    /** The rightmost pixel of the selection pill. */
    private fun BufferedImage.pillRight(): Int {
        for (x in width - 1 downTo 0) {
            for (y in 0 until height) if (isPill(getRGB(x, y))) return x + 1
        }
        return -1
    }

    private fun isPage(rgb: Int): Boolean = (rgb and 0xFFFFFF) == 0xFF00FF

    private fun isPill(rgb: Int): Boolean {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return g > 140 && g - r > 60 && g - b > 60
    }

    private companion object {
        val Pill = Color(0xFF00A000)

        /** Long enough for `springGentle` to carry 88dp to 280dp. */
        const val Frames = 60

        /**
         * A pill one frame behind at the spring's fastest is about this much.
         *
         * Not zero: the rail's width is a `Dp` rounded to whole pixels and the
         * pill's rect is rounded separately, so a pixel of disagreement is
         * arithmetic rather than lag.
         */
        const val Tolerance = 3
    }
}
