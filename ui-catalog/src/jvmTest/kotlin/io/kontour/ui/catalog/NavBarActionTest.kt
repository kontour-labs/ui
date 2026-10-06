package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.MapPin
import io.kontour.icons.tabler.outline.Plus
import io.kontour.icons.tabler.outline.Star
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowSizeClass
import io.kontour.ui.components.action.FabSize
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarDefaults
import io.kontour.ui.nav.NavBarItemWidth
import io.kontour.ui.nav.NavBarScope
import io.kontour.ui.nav.NavBarStyle
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.NavigationSuiteType
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A nav bar's floating action sits beside a floating bar, not in it.
 *
 * Asked for directly: *"in the floating nav bar, if there's a FAB, please put it
 * after the bar, not in it."* It used to go in as a slot, so a round button sat
 * among round destinations in the same capsule and read as one more of them.
 *
 * Read from the pixels between the two. The page is pure red and the bar's
 * surface is near white, so the green channel says which is under a point: a
 * shadow darkens the red but adds no green, and the capsule is all green. A
 * point halfway along the gap before the action is the page for a floating bar
 * and the bar's own surface for a docked one — the docked case is the control
 * that shows the sampler can tell the two apart.
 */
class NavBarActionTest {

    private companion object {
        const val Density = 2f

        /** A phone: the default window size class, and so a bar that spans. */
        const val PhoneWidth = 800

        val Page = Color(0xFFFF0000)

        /** Below this much green a pixel is the page, however shadowed. */
        const val PageGreen = 60

        /** Above this much, it is the bar's surface. */
        const val SurfaceGreen = 200

        /** Rounding in a centred placement, in pixels. */
        const val Slack = 2f
    }

    private class Bounds {
        var action = Rect.Zero
        val items = mutableListOf(Rect.Zero, Rect.Zero, Rect.Zero)
    }

    @Composable
    private fun NavBarScope.items(bounds: Bounds) {
        item(true, {}, key = "nearby", icon = Tabler.Outline.MapPin, modifier = Modifier.reportBounds { bounds.items[0] = it }) { +"Nearby" }
        item(false, {}, key = "routes", icon = Tabler.Outline.Bus, modifier = Modifier.reportBounds { bounds.items[1] = it }) { +"Routes" }
        item(false, {}, key = "saved", icon = Tabler.Outline.Star, modifier = Modifier.reportBounds { bounds.items[2] = it }) { +"Saved" }
    }

    @Composable
    private fun Fab(bounds: Bounds, size: FabSize = FabSize.Medium) {
        FloatingActionButton(
            onClick = {},
            icon = Tabler.Outline.Plus,
            contentDescription = "New trip",
            size = size,
            modifier = Modifier.reportBounds { bounds.action = it },
        )
    }

    private fun render(
        width: Int = PhoneWidth,
        rtl: Boolean = false,
        wide: Boolean = false,
        content: @Composable () -> Unit,
    ): BufferedImage = Scene(width = width, height = 400, density = Density, reduceMotion = true) {
        val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        val window = if (wide) WindowSizeClass.of(1200.dp, 800.dp) else LocalWindowSizeClass.current
        CompositionLocalProvider(LocalLayoutDirection provides direction, LocalWindowSizeClass provides window) {
            Box(Modifier.fillMaxSize().background(Page), contentAlignment = Alignment.BottomCenter) { content() }
        }
    }.use { it.frames(8) }

    private fun green(image: BufferedImage, x: Float, y: Float): Int =
        (image.getRGB(x.toInt(), y.toInt()) shr 8) and 0xFF

    @Test
    fun aFloatingBarsActionSitsBesideTheCapsuleOnThePage() {
        val bounds = Bounds()
        val image = render {
            // No fade: this finds the capsule by the page round it, which the fade would cover.
            NavBar(fadeUnder = false, action = { Fab(bounds) }) { items(bounds) }
        }

        val last = bounds.items.maxBy { it.right }
        assertTrue(bounds.action.width > 0f, "the action never reported a size")
        assertTrue(
            bounds.action.left > last.right,
            "the action starts at ${bounds.action.left}px, inside the destinations that end at ${last.right}px",
        )
        // Halfway along the run from the last destination to the action there
        // has to be page, at the action's own height: the capsule ends first.
        val gapX = (last.right + bounds.action.left) / 2f
        val probe = bounds.action.center.y
        var page = false
        var x = bounds.action.left - 1f
        while (x > last.right) {
            if (green(image, x, probe) < PageGreen) {
                page = true
                break
            }
            x -= 1f
        }
        assertTrue(
            page,
            "no page between the last destination (${last.right}px) and the action (${bounds.action.left}px) — " +
                "the action is still inside the capsule; green at the midpoint ${gapX}px is ${green(image, gapX, probe)}",
        )
        // And it is a real button's size, not squashed into the bar's row.
        assertEquals(56f * Density, bounds.action.width, Slack, "the action was not its own size")
        // Level with the destinations, which are centred in the capsule.
        assertTrue(
            abs(bounds.action.center.y - last.center.y) <= Slack,
            "the action's centre is at ${bounds.action.center.y}px and the destinations' at ${last.center.y}px",
        )
        // The capsule still spans the phone: the action is at the far end, a
        // bar's inset from the edge, rather than the whole group centred.
        assertTrue(
            PhoneWidth - bounds.action.right <= 16f * Density + Slack,
            "the action ends at ${bounds.action.right}px of $PhoneWidth — the capsule no longer spans the phone",
        )
    }

    @Test
    fun aDockedBarKeepsItsActionInside() {
        val bounds = Bounds()
        val image = render {
            NavBar(style = NavBarStyle.Docked, action = { Fab(bounds) }) { items(bounds) }
        }

        val last = bounds.items.maxBy { it.right }
        assertTrue(bounds.action.left > last.right, "the action is not after the destinations")
        // The control: here the run up to the action is all bar.
        val probe = bounds.action.center.y
        var x = bounds.action.left - 1f
        while (x > last.right) {
            assertTrue(
                green(image, x, probe) > SurfaceGreen,
                "a docked bar has page showing at ${x}px, between its destinations and its action",
            )
            x -= 1f
        }
        assertEquals(56f * Density, bounds.action.height, Slack, "the docked bar squashed its action")
    }

    @Test
    fun aTallActionAndTheCapsuleAreCentredOnEachOther() {
        val bounds = Bounds()
        render {
            NavBar(action = { Fab(bounds, FabSize.Large) }) { items(bounds) }
        }

        val item = bounds.items.first()
        assertEquals(72f * Density, bounds.action.height, Slack, "the large action was not its own size")
        assertTrue(
            abs(bounds.action.center.y - item.center.y) <= Slack,
            "a 72dp action's centre is at ${bounds.action.center.y}px and the capsule's destinations' at " +
                "${item.center.y}px — the two should stand level, not on the same floor",
        )
    }

    @Test
    fun rightToLeftPutsTheActionOnTheLeft() {
        val bounds = Bounds()
        render(rtl = true) {
            NavBar(action = { Fab(bounds) }) { items(bounds) }
        }

        val first = bounds.items.minBy { it.left }
        assertTrue(
            bounds.action.right < first.left,
            "in a right-to-left layout the action ends at ${bounds.action.right}px, not before the destinations " +
                "that start at ${first.left}px",
        )
        assertTrue(
            bounds.action.left <= 16f * Density + Slack,
            "the action starts at ${bounds.action.left}px — it should be at the start edge, a bar's inset in",
        )
    }

    @Test
    fun onAWideWindowTheCapsuleAndTheActionAreCentredTogether() {
        val bounds = Bounds()
        val width = 2400
        render(width = width, wide = true) {
            NavBar(
                itemWidth = NavBarItemWidth.Natural,
                marker = NavBarDefaults.glyphMarker(),
                action = { Fab(bounds) },
            ) { items(bounds) }
        }

        // The capsule hugs: its destinations are packed, with `sm` of the
        // capsule either side of them — natural circles', which this counts in.
        val capsuleLeft = bounds.items.minOf { it.left } - 12f * Density
        val middle = (capsuleLeft + bounds.action.right) / 2f
        assertTrue(
            abs(middle - width / 2f) <= Slack,
            "the capsule and its action span $capsuleLeft..${bounds.action.right}px, centred on ${middle}px " +
                "rather than the window's ${width / 2f}px",
        )
        assertTrue(bounds.action.left > bounds.items.maxOf { it.right }, "the action is not after the capsule")
    }

    @Test
    fun theSuitePutsItsActionBesideTheBarAndPadsForIt() {
        val bounds = Bounds()
        var padding: PaddingValues? = null
        val image = render {
            NavigationSuiteScaffold(
                destinations = {
                    item(true, {}, key = "nearby", icon = Tabler.Outline.MapPin, modifier = Modifier.reportBounds { bounds.items[0] = it }) { +"Nearby" }
                    item(false, {}, key = "routes", icon = Tabler.Outline.Bus, modifier = Modifier.reportBounds { bounds.items[1] = it }) { +"Routes" }
                    item(false, {}, key = "saved", icon = Tabler.Outline.Star, modifier = Modifier.reportBounds { bounds.items[2] = it }) { +"Saved" }
                },
                type = NavigationSuiteType.Bar,
                containerColour = Page,
                action = { Fab(bounds, FabSize.Large) },
            ) { padding = it }
        }

        val last = bounds.items.maxBy { it.right }
        assertTrue(bounds.action.left > last.right, "the suite's action is not after the destinations")
        val probe = bounds.action.center.y
        var page = false
        var x = bounds.action.left - 1f
        while (x > last.right && !page) {
            page = green(image, x, probe) < PageGreen
            x -= 1f
        }
        assertTrue(page, "the suite put its action inside the capsule")
        val bottom = padding?.calculateBottomPadding()?.value ?: 0f
        assertTrue(
            bottom * Density >= 400f - bounds.action.top - Slack,
            "the content is padded ${bottom}dp at the bottom, short of the action's top at ${bounds.action.top}px",
        )
    }
}
