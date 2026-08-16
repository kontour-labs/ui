package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavItem
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where a [NavBar] puts its contents.
 *
 * These are the regression tests for four bugs that shipped because
 * `NavLogicTest` only ever asserted pure logic — nothing measured the bar, so
 * nothing noticed that its contents were pinned to the top of it, that its labels
 * were clipped at large type, that its action was jammed inside its shape, or
 * that one badged destination was a different size from its neighbours.
 *
 * Every assertion reads a coordinate, never a parameter. Checking that
 * `contentAlignment == Center` would pass the moment somebody set the argument
 * and still miss a fixed `.height()` left in beside it.
 */
@OptIn(ExperimentalTestApi::class)
class NavBarGeometryTest {

    private val pixelsPerDp = 2f
    private val destinations = listOf("Home", "Map", "Plan")

    /** Darker than this is the glyph; everything else in a destination is not. */
    private val Ink = 0.4f

    private class Measured {
        var bar: Rect = Rect.Zero
        /** The union of the destinations — what the bar has to centre and fit. */
        var items: Rect = Rect.Zero
        /** Each destination, in order, so the gaps between them can be compared. */
        var each: List<Rect> = emptyList()
        var fab: Rect = Rect.Zero
    }

    /**
     * Renders a bar and reads the rects back out of the semantics tree.
     *
     * The destination row is internal to `NavBar`, so there is nowhere to hang an
     * `onGloballyPositioned` on it from outside. Every destination is given a
     * content description instead, which finds it whether or not the bar is
     * showing labels — and labels are off by default now, so finding items by
     * their text would only ever have measured the one arrangement.
     *
     * @param badgeOn Which destination carries a badge, if any.
     */
    private fun measure(
        fontScale: Float = 1f,
        showLabels: Boolean = true,
        badgeOn: Int? = null,
        withAction: Boolean = false,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ): Measured {
        val measured = Measured()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(pixelsPerDp, fontScale),
                    LocalLayoutDirection provides direction,
                ) {
                    KontourTheme(darkTheme = false, reduceMotion = true) {
                        Box(Modifier.fillMaxSize()) {
                            NavBar(
                                items = destinations.mapIndexed { index, label ->
                                    NavItem(
                                        label = label,
                                        icon = Tabler.Outline.Search,
                                        onClick = {},
                                        badge = if (index == badgeOn) 2 else null,
                                        contentDescription = label,
                                    )
                                },
                                selectedIndex = 1,
                                modifier = Modifier.testTag("bar"),
                                showLabels = showLabels,
                                action = if (withAction) {
                                    {
                                        FloatingActionButton(
                                            icon = Tabler.Outline.Search,
                                            contentDescription = "Search",
                                            onClick = {},
                                            modifier = Modifier.testTag("action"),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }

            measured.bar = onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
            val rects = destinations.map {
                onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
            }
            measured.each = rects
            measured.items = Rect(
                left = rects.minOf { it.left },
                top = rects.minOf { it.top },
                right = rects.maxOf { it.right },
                bottom = rects.maxOf { it.bottom },
            )
            if (withAction) {
                measured.fab = onNodeWithTag("action").fetchSemanticsNode().boundsInRoot
            }
        }
        return measured
    }

    @Test
    fun theBarCentresItsContentsVertically() {
        // The bug: `Surface` defaults to `contentAlignment = TopStart`, and the
        // bar gave it a fixed 64dp height without overriding that. ~56dp of
        // content sat at the top with all 8dp of slack below it, which is why the
        // labels looked low and the icons looked high.
        //
        // Asserted as a gap rather than a parameter, so it also catches a fixed
        // height reintroduced beside a correct alignment.
        val measured = measure()

        val above = measured.items.top - measured.bar.top
        val below = measured.bar.bottom - measured.items.bottom
        assertTrue(
            measured.items.height > 0f,
            "the destination row never reported a size",
        )
        assertTrue(
            abs(above - below) <= 2f,
            "the bar's contents are off-centre: ${above}px above, ${below}px below",
        )
    }

    @Test
    fun theBarGrowsRatherThanClippingAtLargeType() {
        val normal = measure(fontScale = 1f)
        val large = measure(fontScale = 2f)

        assertTrue(
            large.bar.height > normal.bar.height,
            "at 200% type the bar stayed ${large.bar.height}px tall — a fixed " +
                "height clips the labels rather than growing for them",
        )
        assertTrue(
            large.items.bottom <= large.bar.bottom + 1f,
            "at 200% type the destinations overflow the bar: content ends at " +
                "${large.items.bottom}px, bar at ${large.bar.bottom}px",
        )
    }

    /**
     * A badge does not move the glyph it annotates.
     *
     * `BadgedBox` reports a size that *includes* the badge's overhang and places
     * its content off-centre inside it — correct for a toolbar icon, and the whole
     * of this bug for a destination, because centring an asymmetrically padded
     * node centres the **padding**. With an 18dp badge the icon landed 4.5dp
     * towards the leading side and 4.5dp below the centre of the very circle drawn
     * to mark it, so the one badged destination sat visibly out of step with the
     * two beside it.
     *
     * **Measured off the rendered pixels.** The destination merges its children's
     * semantics, so the only rect a test can reach is the item's own — and the
     * item's own never changed: a 48dp circle is bigger than a 24dp icon with a
     * badge on it, so nothing grew and every size assertion passes just as happily
     * against the bug. What moved was the glyph inside the box.
     *
     * The comparison is between two destinations in **one** render rather than
     * against a number, so it needs no absolute geometry and stays true if the
     * circle, the icon or the badge is ever resized.
     */
    @Test
    fun aBadgeDoesNotMoveTheGlyphItAnnotates() {
        val ink = mutableListOf<Offset>()
        val boxes = mutableListOf<Rect>()

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(pixelsPerDp, 1f)) {
                    KontourTheme(darkTheme = false, reduceMotion = true) {
                        Box(Modifier.fillMaxSize().background(Color.White)) {
                            NavBar(
                                items = destinations.mapIndexed { index, label ->
                                    NavItem(
                                        label = label,
                                        icon = Tabler.Outline.Search,
                                        onClick = {},
                                        // On the last destination, so the two
                                        // being compared are both unselected and
                                        // therefore drawn in the same ink.
                                        badge = if (index == 2) 2 else null,
                                        contentDescription = label,
                                    )
                                },
                                selectedIndex = 0,
                                modifier = Modifier.testTag("bar"),
                                showLabels = false,
                                // Flattened to two tones, so "which pixels are the
                                // glyph" is a question about the render rather than
                                // about the palette.
                                containerColor = Color.White,
                                indicatorColor = Color.White,
                                contentColor = Color.Black,
                            )
                        }
                    }
                }
            }
            waitForIdle()

            val bar = onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
            val pixels = onNodeWithTag("bar").captureToImage().toPixelMap()

            destinations.forEach { label ->
                val item = onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
                val box = item.translate(-bar.left, -bar.top)
                boxes += box

                // The glyph is the only near-black thing in a destination: the
                // circle behind it is white, the badge is the danger colour and
                // its count is white on top of that, and the circle's shadow is a
                // pale grey nowhere near this threshold.
                var n = 0
                var sumX = 0f
                var sumY = 0f
                for (y in box.top.toInt().coerceAtLeast(0) until box.bottom.toInt()) {
                    for (x in box.left.toInt().coerceAtLeast(0) until box.right.toInt()) {
                        val c = pixels[x, y]
                        if (c.red < Ink && c.green < Ink && c.blue < Ink) {
                            n++
                            sumX += x - box.center.x
                            sumY += y - box.center.y
                        }
                    }
                }
                ink += if (n == 0) Offset.Unspecified else Offset(sumX / n, sumY / n)
            }
        }

        // Against a plain neighbour rather than dead centre: an icon whose own
        // artwork is not symmetric is allowed to sit slightly off, as long as it
        // sits there for every destination. Both are unselected, so both are
        // drawn in the `contentColor` the threshold was chosen for — the current
        // destination takes the accent's on-container colour instead and is not
        // one of the two being compared.
        val plain = ink[1]
        val badged = ink[2]
        listOf(1, 2).forEach { index ->
            assertTrue(
                ink[index] != Offset.Unspecified,
                "${destinations[index]} drew no glyph to measure",
            )
        }
        assertTrue(
            abs(badged.x - plain.x) <= 1f && abs(badged.y - plain.y) <= 1f,
            "the badged destination's glyph is at (${badged.x}, ${badged.y}) " +
                "within its ${boxes[2].width}×${boxes[2].height} circle where its " +
                "plain neighbour's is at (${plain.x}, ${plain.y}) — the badge is " +
                "shoving the icon it annotates out of the middle",
        )
    }

    @Test
    fun theActionSitsBesideTheDestinationsNotAmongThem() {
        // A 56dp FAB inside the bar's own shape left 4dp of air and read as jammed
        // in. It is a separate circle beside the destinations, the same way the
        // app's own toolbar draws it — and now that the destinations are separate
        // circles too, "beside" is the only thing left to assert: there is no
        // container either of them could be inside of.
        val measured = measure(withAction = true)

        assertTrue(measured.fab.width > 0f, "the action never reported a size")
        assertTrue(
            measured.fab.left >= measured.items.right,
            "the action starts at ${measured.fab.left}px, inside the " +
                "destinations, which run to ${measured.items.right}px",
        )
    }

    @Test
    fun theActionIsStillBesideTheDestinationsInRtl() {
        val measured = measure(withAction = true, direction = LayoutDirection.Rtl)

        assertTrue(measured.fab.width > 0f, "the action never reported a size")
        assertTrue(
            measured.fab.right <= measured.items.left,
            "in RTL the action ends at ${measured.fab.right}px, inside the " +
                "destinations, which start at ${measured.items.left}px",
        )
    }
}
