package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.MapPin
import io.kontour.icons.tabler.outline.Star
import io.kontour.icons.tabler.outline.User
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowSizeClass
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarDefaults
import io.kontour.ui.nav.NavBarItemWidth
import io.kontour.ui.nav.NavBarScope
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A floating nav bar's surface: as much room at its ends as between its
 * destinations, and a tap on it is its own.
 *
 * Both reported from a phone: *"make the padding around the start and end of the
 * items even — it feels like there's too much width on either side of all the
 * items"*, and *"make it so I can't click components through the surface"*.
 *
 * Spacing is read the way it is seen, circle to circle: a destination's bounds
 * are its touch target, wider than its circle, so the circles are found from the
 * targets' centres and the capsule's ends from the pixels — red page, white bar.
 */
class NavBarSurfaceTest {

    private companion object {
        const val Density = 2f
        val Page = Color(0xFFFF0000)

        /** A dp either way: rounding, on a bar laid out in whole pixels. */
        const val Slack = 1f * Density
    }

    private val items = mutableListOf(Rect.Zero, Rect.Zero, Rect.Zero, Rect.Zero)

    @Composable
    private fun NavBarScope.four() {
        item(true, {}, key = "a", icon = Tabler.Outline.MapPin, modifier = Modifier.reportBounds { items[0] = it }) { +"Nearby" }
        item(false, {}, key = "b", icon = Tabler.Outline.Bus, modifier = Modifier.reportBounds { items[1] = it }) { +"Routes" }
        item(false, {}, key = "c", icon = Tabler.Outline.Star, modifier = Modifier.reportBounds { items[2] = it }) { +"Saved" }
        item(false, {}, key = "d", icon = Tabler.Outline.User, modifier = Modifier.reportBounds { items[3] = it }) { +"Me" }
    }

    /** The capsule's left and right, at the destinations' height, from the pixels. */
    private fun capsule(image: BufferedImage, y: Float): Pair<Float, Float> {
        val row = y.toInt()
        // Anything with green in it is the bar: the page is pure red, and the
        // capsule's rim, its own ink faintly over white, is part of the capsule.
        fun isBar(x: Int) = ((image.getRGB(x, row) shr 8) and 0xFF) > 100
        val left = (0 until image.width).first { isBar(it) }
        val right = (image.width - 1 downTo 0).first { isBar(it) }
        return left.toFloat() to right.toFloat()
    }

    private fun spacing(width: Int, wide: Boolean): Pair<Float, List<Float>> {
        var circle = 0f
        val image = Scene(width = width, height = 300, density = Density, reduceMotion = true) {
            circle = with(androidx.compose.ui.platform.LocalDensity.current) {
                io.kontour.ui.nav.NavBarDefaults.CircleSize.width.toPx()
            }
            val window = if (wide) WindowSizeClass.of(1200.dp, 800.dp) else LocalWindowSizeClass.current
            CompositionLocalProvider(LocalWindowSizeClass provides window) {
                Box(Modifier.fillMaxSize().background(Page), contentAlignment = Alignment.BottomCenter) {
                    // Natural circles: the spacing this measures is theirs. Equal
                    // cells touch, and their ends are `NavBarShapeTest`'s.
                    // No fade: this finds the capsule by the page round it, which the fade would cover.
                    NavBar(itemWidth = NavBarItemWidth.Natural, marker = NavBarDefaults.glyphMarker(), fadeUnder = false) { four() }
                }
            }
        }.use { it.frames(6) }
        val (left, right) = capsule(image, items[0].center.y)
        // The circles: centred in their targets, a circle wide.
        val lefts = items.map { it.center.x - circle / 2 }
        val rights = items.map { it.center.x + circle / 2 }
        val ends = listOf(lefts.first() - left, right - rights.last())
        val between = (0 until 3).map { lefts[it + 1] - rights[it] }
        return ends.average().toFloat() to between + ends
    }

    @Test
    fun onAPhoneTheEndsHaveAsMuchRoomAsBetweenTheCircles() {
        val (_, gaps) = spacing(width = 800, wide = false)
        val between = gaps.take(3)
        val ends = gaps.drop(3)
        for (end in ends) {
            assertTrue(
                abs(end - between.average().toFloat()) <= Slack,
                "the capsule's ends have ${ends}px and the circles ${between}px between them — the ends should match",
            )
        }
    }

    @Test
    fun onAWideWindowTheEndsHaveAsMuchRoomAsBetweenTheCircles() {
        val (_, gaps) = spacing(width = 2400, wide = true)
        val between = gaps.take(3)
        val ends = gaps.drop(3)
        for (end in ends) {
            assertTrue(
                abs(end - between.average().toFloat()) <= Slack,
                "hugging, the capsule's ends have ${ends}px and the circles ${between}px between them",
            )
        }
    }

    @Test
    fun aTapOnTheBarBetweenDestinationsDoesNotReachThePage() {
        var pageTaps = 0
        Scene(width = 800, height = 400, density = Density) {
            Box(Modifier.fillMaxSize().background(Page).clickable { pageTaps++ }, contentAlignment = Alignment.BottomCenter) {
                // No fade: this finds the capsule by the page round it, which the fade would cover.
                NavBar(fadeUnder = false) { four() }
            }
        }.use { scene ->
            scene.frames(4)
            // Between the first two destinations, on the capsule.
            val between = Offset((items[0].right + items[1].left) / 2f, items[0].center.y)
            scene.tap(between)
            scene.frames(4)
            assertEquals(0, pageTaps, "a tap on the bar between two destinations went through to the page")

            // The control: just off the capsule's end, in the bar's inset, is
            // still the page — so the page was listening all along.
            val image = scene.frames(1)
            val (left, _) = capsule(image, items[0].center.y)
            scene.tap(Offset(left - 4f * Density, items[0].center.y))
            scene.frames(4)
            assertEquals(1, pageTaps, "the page did not take a tap beside the capsule, so this measured nothing")
        }
    }
}
