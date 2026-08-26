package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A window that changes size without changing shape recomposes nothing.
 *
 * `LocalWindowSizeClass` is a `staticCompositionLocalOf`, and a static local does
 * not track which composable read what — when its value changes it invalidates
 * **everything beneath it**, which here is the whole application. Its value used
 * to be a `data class` carrying the raw `widthDp` and `heightDp`, so any change
 * in the measurement at all was a change in the value.
 *
 * On a desktop that costs nothing: a window sits still. On a phone browser the
 * URL bar collapses and expands *as the reader scrolls*, resizing the viewport by
 * tens of pixels several times a second — so every scroll recomposed every screen
 * in the app. That is why the documentation site struggled on mobile and nowhere
 * else, and every app built on this library was paying it too.
 *
 * The number this asserts is zero, and zero is the only defensible number: a
 * window 36dp shorter is the same window.
 */
@OptIn(ExperimentalTestApi::class)
class WindowSizeRecompositionTest {

    @Test
    fun aViewportTwitchRecomposesNothing() {
        var height by mutableStateOf(800.dp)
        val counted = Compositions()

        runComposeUiTest {
            setContent {
                Box(Modifier.width(400.dp).height(height)) {
                    WindowSizeClassProvider { Counted(counted) }
                }
            }
            waitForIdle()
            val settled = counted.count
            assertTrue(settled > 0, "the content never composed at all")

            // A URL bar collapsing. Still a Medium-height compact-width phone.
            height = 764.dp
            waitForIdle()

            assertEquals(
                settled,
                counted.count,
                "the window lost 36dp and stayed the same shape, but the content " +
                    "recomposed ${counted.count - settled} time(s). A static " +
                    "composition local invalidates its whole subtree, so on a phone " +
                    "browser this is the entire app, once per scrolled frame.",
            )
        }
    }

    @Test
    fun crossingABoundaryStillRecomposes() {
        // The control. Narrowing equality is only correct if it still notices the
        // change a layout actually has to act on — otherwise this test would pass
        // just as well against a size class that reported nothing.
        var width by mutableStateOf(400.dp)
        val counted = Compositions()

        runComposeUiTest {
            setContent {
                Box(Modifier.width(width).height(800.dp)) {
                    WindowSizeClassProvider { Counted(counted) }
                }
            }
            waitForIdle()
            val settled = counted.count

            // Compact -> Medium: a tablet's worth of width, and a layout that
            // should now put its index beside the content rather than behind it.
            width = 800.dp
            waitForIdle()

            assertTrue(
                counted.count > settled,
                "the window went from a phone to a tablet and nothing recomposed",
            )
        }
    }

    @Test
    fun theRawMeasurementIsStillThere() {
        // Excluded from `equals`, not removed: a component that genuinely wants
        // the number can still have it.
        val size = WindowSizeClass.of(412.dp, 915.dp)
        assertEquals(412.dp, size.widthDp)
        assertEquals(915.dp, size.heightDp)
        assertEquals(WindowWidthClass.Compact, size.width)
        assertEquals(WindowHeightClass.Expanded, size.height)

        assertEquals(
            size,
            WindowSizeClass.of(400.dp, 900.dp),
            "two windows of the same shape should compare equal whatever they measure",
        )
    }
}

/**
 * A counter the compiler can skip past.
 *
 * Deliberately not `Counted { compositions++ }`: a capturing lambda is a fresh,
 * unstable instance on every composition, so a composable taking one can never
 * skip and the test would count its own probe rather than the app. Real app
 * content is skippable; the probe has to be too, or zero is unreachable by
 * construction.
 */
@Stable
private class Compositions {
    var count = 0
}

@Composable
private fun Counted(into: Compositions) {
    into.count++
}
