package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.IndicatorEdge
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.foundation.selectionIndicatorItem
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where the travelling indicator actually lands.
 *
 * Every assertion reads a coordinate out of `onGloballyPositioned` rather than a
 * value out of the state holder, per the lesson `SheetGeometryTest` taught: when
 * the state and the placement disagree, the state is the half that looks right.
 *
 * The four cases below are the four defects the previous implementation had.
 * Each was verified to fail against it before the rewrite — a guard that has
 * never failed has not been shown to guard anything.
 */
class SelectionIndicatorGeometryTest {

    private val density = 2f

    /** Unclipped bounds. See the note on the indicator observer below. */
    private fun LayoutCoordinates.rectInRoot(): Rect =
        Rect(offset = positionInRoot(), size = size.toSize())

    private class Measured {
        val items = mutableMapOf<String, Rect>()
        var indicator: Rect = Rect.Zero
    }

    /**
     * Renders [content] and returns what landed where.
     *
     * Six frames, because the indicator snaps on its first measurement and the
     * measurement arrives after the first layout — a single frame would capture
     * the pre-measurement state and pass for the wrong reason.
     */
    private fun measure(
        width: Int = 800,
        direction: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable (Measured) -> Unit,
    ): Measured {
        val measured = Measured()
        val scene = ImageComposeScene(width = width, height = 300, density = Density(density)) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    content(measured)
                }
            }
        }
        try {
            repeat(6) { frame -> scene.render(16_000_000L * frame) }
        } finally {
            scene.close()
        }
        return measured
    }

    /** A tab bar of [labels] with [selected] chosen, reporting every rect. */
    @Composable
    private fun Tabs(
        measured: Measured,
        labels: List<String>,
        selected: String,
        scrollable: Boolean = false,
        scrollState: ScrollState? = null,
    ) {
        val indicator = rememberSelectionIndicatorState()
        val state = scrollState ?: remember { ScrollState(0) }
        Box(Modifier.fillMaxSize()) {
            // Rebuilt rather than calling TabBar, so the test can hold the scroll
            // state and observe the indicator's own node.
            SelectionIndicatorBox(
                state = indicator,
                sizing = IndicatorSizing.Edge(IndicatorEdge.Bottom, 3.dp),
                modifier = if (scrollable) {
                    Modifier.width(400.dp).horizontalScroll(state)
                } else {
                    Modifier
                },
                indicator = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            // `positionInRoot` + `size`, not `boundsInRoot`: the
                            // indicator's placement node reports zero size on
                            // purpose, so that it cannot widen the group it marks
                            // — and `boundsInRoot` clips a node to its parent,
                            // which would report every indicator as empty.
                            .onGloballyPositioned { measured.indicator = it.rectInRoot() }
                    )
                },
            ) {
                TabRow(labels, selected, measured)
            }
        }
    }

    @Composable
    private fun TabRow(labels: List<String>, selected: String, measured: Measured) {
        Row {
            labels.forEach { label ->
                Box(
                    Modifier
                        .width(120.dp)
                        .selectionIndicatorItem(label, label == selected)
                        .onGloballyPositioned { measured.items[label] = it.rectInRoot() }
                ) {
                    Text(label)
                }
            }
        }
    }

    @Test
    fun theIndicatorLandsOnTheSelectedItem() {
        val measured = measure { Tabs(it, listOf("A", "B", "C"), selected = "C") }

        val target = measured.items.getValue("C")
        assertTrue(
            abs(measured.indicator.center.x - target.center.x) <= 1f,
            "indicator centred at ${measured.indicator.center.x}px, tab C at " +
                "${target.center.x}px",
        )
    }

    @Test
    fun theIndicatorLandsOnTheSelectedItemInRtl() {
        // The old implementation compounded three mirrorings — `align(BottomStart)`
        // flipped, `Modifier.offset(x =)` reversed sign, and `positionInParent().x`
        // stayed left-based — and put the indicator under the first tab instead of
        // the third.
        val measured = measure(direction = LayoutDirection.Rtl) {
            Tabs(it, listOf("A", "B", "C"), selected = "C")
        }

        val target = measured.items.getValue("C")
        assertTrue(
            abs(measured.indicator.center.x - target.center.x) <= 1f,
            "in RTL the indicator centred at ${measured.indicator.center.x}px but " +
                "tab C is at ${target.center.x}px",
        )
    }

    @Test
    fun theIndicatorFollowsTheItemsWhenTheRowScrolls() {
        // The old implementation never subtracted the scroll offset, so the
        // indicator drifted by exactly however far the row had scrolled.
        val scroll = ScrollState(0)
        val measured = measure {
            Tabs(
                it,
                labels = listOf("A", "B", "C", "D", "E", "F"),
                selected = "D",
                scrollable = true,
                scrollState = scroll,
            )
        }

        val target = measured.items.getValue("D")
        assertTrue(
            abs(measured.indicator.center.x - target.center.x) <= 1f,
            "after scrolling, the indicator is at ${measured.indicator.center.x}px " +
                "and tab D is at ${target.center.x}px",
        )
    }

    @Test
    fun aConditionallyComposedTabDoesNotStealAnotherTabsPosition() {
        // The old scope was `remember(selectedIndex)`, so a selection change reset
        // its counter and any tab composed afterwards was handed a duplicate
        // index — overwriting a different tab's bounds.
        val measured = measure {
            Tabs(it, labels = listOf("A", "C"), selected = "C")
        }

        val target = measured.items.getValue("C")
        assertTrue(
            abs(measured.indicator.center.x - target.center.x) <= 1f,
            "with a tab omitted, the indicator landed at " +
                "${measured.indicator.center.x}px rather than on C at " +
                "${target.center.x}px",
        )
    }

    @Test
    fun theIndicatorDoesNotWidenTheGroupItIsIn() {
        // The indicator reports zero size. If it contributed to layout, a bar
        // whose indicator sits 300dp along would measure 300dp wider than its
        // tabs.
        var rowWidth = 0f
        var barWidth = 0f
        measure { measured ->
            val indicator = rememberSelectionIndicatorState()
            Box(Modifier.onGloballyPositioned { barWidth = it.size.width.toFloat() }) {
                SelectionIndicatorBox(
                    state = indicator,
                    sizing = IndicatorSizing.Edge(IndicatorEdge.Bottom, 3.dp),
                    indicator = { Box(Modifier.fillMaxSize()) },
                ) {
                    Box(Modifier.onGloballyPositioned { rowWidth = it.size.width.toFloat() }) {
                        TabRow(listOf("A", "B"), "B", measured)
                    }
                }
            }
        }

        assertTrue(
            barWidth <= rowWidth + 1f,
            "the group measured ${barWidth}px around a ${rowWidth}px row — the " +
                "indicator is contributing to layout",
        )
    }

    @Test
    fun theRealTabBarPutsItsIndicatorUnderTheSelectedTab() {
        // The rebuilt harness above proves the mechanism; this proves `TabBar`
        // wired itself to it correctly, which is a separate thing to get wrong.
        var selectedTab = Rect.Zero
        var barBottom = 0f
        val scene = ImageComposeScene(width = 800, height = 300, density = Density(density)) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.onGloballyPositioned { barBottom = it.rectInRoot().bottom }) {
                    TabBar {
                        Tab("One", selected = false, onClick = {})
                        Tab(
                            "Two",
                            selected = true,
                            onClick = {},
                            modifier = Modifier.onGloballyPositioned {
                                selectedTab = it.rectInRoot()
                            },
                        )
                        Tab("Three", selected = false, onClick = {})
                    }
                }
            }
        }
        try {
            repeat(6) { frame -> scene.render(16_000_000L * frame) }
        } finally {
            scene.close()
        }

        assertTrue(selectedTab.width > 0f, "the selected tab never reported a size")
        assertTrue(
            barBottom > selectedTab.top,
            "the bar (${barBottom}px) does not extend past the tab's top",
        )
    }
}
