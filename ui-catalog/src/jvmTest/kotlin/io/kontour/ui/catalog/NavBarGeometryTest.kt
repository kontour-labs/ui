package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarStyle
import io.kontour.ui.nav.NavItem
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where a [NavBar] puts its contents.
 *
 * These are the regression tests for three bugs that shipped because
 * `NavLogicTest` only ever asserted pure logic — nothing measured the bar, so
 * nothing noticed that its contents were pinned to the top of it, that its labels
 * were clipped at large type, or that its action was jammed inside its shape.
 *
 * Every assertion reads a coordinate, never a parameter. Checking that
 * `contentAlignment == Center` would pass the moment somebody set the argument
 * and still miss a fixed `.height()` left in beside it.
 */
@OptIn(ExperimentalTestApi::class)
class NavBarGeometryTest {

    private val pixelsPerDp = 2f
    private val destinations = listOf("Home", "Map", "Plan")

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
     * `onGloballyPositioned` on it from outside. The items are findable by their
     * labels instead, and their union is the content the bar has to accommodate —
     * which is the thing being asserted either way.
     */
    private fun measure(
        fontScale: Float = 1f,
        style: NavBarStyle = NavBarStyle.Floating,
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
                                items = destinations.map { label ->
                                    NavItem(label = label, icon = Tabler.Outline.Search, onClick = {})
                                },
                                selectedIndex = 1,
                                modifier = Modifier.testTag("bar"),
                                style = style,
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
            val rects = destinations.map { onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
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
     * The gap between adjacent destinations — the spacing *inside* the pill.
     *
     * Used as the yardstick for whether the action is inside the pill too. The
     * `modifier` reaches the outermost node, which is the row holding the pill
     * *and* the action, so there is no rect for the pill itself to measure
     * against. Comparing against the pill's internal spacing needs no absolute
     * numbers and still tells the two arrangements apart: when the action was a
     * sibling of the destinations it sat one internal gap away, and now it is a
     * pill's padding plus a row gap away.
     */
    private fun Measured.internalGap(): Float =
        each.sortedBy { it.left }.zipWithNext { a, b -> b.left - a.right }.min()

    @Test
    fun theActionSitsBesideTheBarNotInsideIt() {
        // A 56dp FAB inside a 64dp pill leaves 4dp of air and reads as jammed in.
        // The app's own toolbar renders it as a separate circle beside the pill.
        val measured = measure(withAction = true)

        assertTrue(measured.fab.width > 0f, "the action never reported a size")
        val gap = measured.fab.left - measured.items.right
        assertTrue(
            gap > measured.internalGap(),
            "the action is only ${gap}px from the destinations, no further than " +
                "the ${measured.internalGap()}px between two of them — it is " +
                "inside the pill rather than beside it",
        )
    }

    @Test
    fun theActionIsStillBesideTheBarInRtl() {
        val measured = measure(withAction = true, direction = LayoutDirection.Rtl)

        assertTrue(measured.fab.width > 0f, "the action never reported a size")
        val gap = measured.items.left - measured.fab.right
        assertTrue(
            gap > measured.internalGap(),
            "in RTL the action is only ${gap}px from the destinations, no further " +
                "than the ${measured.internalGap()}px between two of them",
        )
    }
}
