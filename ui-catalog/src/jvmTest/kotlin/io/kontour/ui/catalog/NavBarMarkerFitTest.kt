package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import io.kontour.ui.components.display.Badge
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.kontourSizing
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A labelled destination's label and badge are inside the marker round it.
 *
 * Asked for with the compact labelled bar: *"please also ensure that the text
 * fits comfortably inside the selection indicator. same goes for icons that may
 * have badges on it"*. The marker covers the whole destination in the bar's own
 * capsule, so its corners are round all the way to its middle; a label laid out
 * to the cell's width ran out through the rounded bottom, and a badge anchored on
 * an icon near the top poked out over the bar.
 *
 * Read off the pixels, on a phone-width bar of five, with the colours flattened:
 * the bar white, the marker a mid grey, the label and icon black and the badge
 * its own red. Anything black or red *outside* the marker's capsule — computed
 * from the destination's own bounds, with a pixel and a half of slack for the
 * squircle's edge — is something that does not fit.
 */
@OptIn(ExperimentalTestApi::class)
class NavBarMarkerFitTest {

    @Test
    fun theLabelSitsInsideTheMarker() {
        val (outside, _) = outsideTheMarker()
        assertTrue(
            outside == 0,
            "$outside pixels of the current destination's label or icon are outside its marker, " +
                "on a 360dp bar of five: the label is wider than the marker is where the label sits",
        )
    }

    @Test
    fun aBadgeStaysInsideTheMarker() {
        val (_, badge) = outsideTheMarker()
        assertTrue(
            badge == 0,
            "$badge pixels of the badge on the current destination are outside its marker",
        )
    }

    /** Ink and badge pixels outside the selected destination's capsule. */
    private fun outsideTheMarker(): Pair<Int, Int> {
        var ink = 0
        var badge = 0
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(PixelsPerDp, 1f)) {
                    KontourTheme(
                        darkTheme = false,
                        reduceMotion = true,
                        sizing = kontourSizing(ContrastLevel.Standard).copy(minTouchTarget = 48.dp),
                    ) {
                        Box(Modifier.fillMaxSize().background(Color.White)) {
                            NavBar(
                                modifier = Modifier.width(360.dp).testTag("bar"),
                                showLabels = true,
                                colours = NavDefaults.colours(
                                    container = Color.White,
                                    content = Color.Black,
                                    indicator = Marker,
                                    selectedContent = Color.Black,
                                ),
                            ) {
                                Labels.forEachIndexed { index, label ->
                                    item(
                                        selected = index == Selected,
                                        onClick = {},
                                        key = label,
                                        icon = Tabler.Outline.Search,
                                        contentDescription = label,
                                        badge = if (index == Selected) {
                                            { Badge(count = 12) }
                                        } else {
                                            null
                                        },
                                    ) { +label }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val bar = onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
            val pixels = onNodeWithTag("bar").captureToImage().toPixelMap()
            val item = onNodeWithContentDescription(Labels[Selected]).fetchSemanticsNode().boundsInRoot
                .translate(-bar.left, -bar.top)
            // A little beyond the destination too, where a badge poking out would land.
            val around = item.inflate(Around * PixelsPerDp)
            for (y in around.top.toInt().coerceAtLeast(0) until around.bottom.toInt().coerceAtMost(pixels.height)) {
                for (x in around.left.toInt().coerceAtLeast(0) until around.right.toInt().coerceAtMost(pixels.width)) {
                    if (inCapsule(item, x + 0.5f, y + 0.5f, Slack)) continue
                    val c = pixels[x, y]
                    when {
                        c.red > 0.6f && c.green < 0.4f && c.blue < 0.4f -> badge++
                        c.red < 0.4f && c.green < 0.4f && c.blue < 0.4f -> ink++
                    }
                }
            }
        }
        return ink to badge
    }

    /** Whether ([x], [y]) is within [slack] of the capsule that fills [box]. */
    private fun inCapsule(box: Rect, x: Float, y: Float, slack: Float): Boolean {
        val r = min(box.width, box.height) / 2f
        val cx = x.coerceIn(box.left + r, box.right - r)
        val cy = y.coerceIn(box.top + r, box.bottom - r)
        val dx = x - cx
        val dy = y - cy
        return sqrt(dx * dx + dy * dy) <= r + slack
    }

    private companion object {
        const val PixelsPerDp = 2f
        const val Selected = 1
        const val Around = 6f
        const val Slack = 1.5f
        val Labels = listOf("Home", "Departures", "Map", "Saved", "Settings")
        val Marker = Color(0xFFBBBBBB)
    }
}
