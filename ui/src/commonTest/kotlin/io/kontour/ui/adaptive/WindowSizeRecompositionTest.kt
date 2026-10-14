package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun aNestedWindowChangingShapeLeavesTheOuterOneAlone() {
        // The catalog's adaptive demos each sit in a frame with a provider of its
        // own, inside the page's, and dragging a frame's edge crosses boundaries
        // on purpose. That must be news to the frame's content and to nothing
        // around it — the page's content is under the *outer* provider, whose
        // window has not changed at all.
        var frame by mutableStateOf(400.dp)
        val page = Compositions()
        val inside = Compositions()

        runComposeUiTest {
            setContent {
                Box(Modifier.width(1400.dp).height(800.dp)) {
                    WindowSizeClassProvider {
                        Column {
                            Counted(page)
                            Box(Modifier.width(frame).height(300.dp)) {
                                WindowSizeClassProvider { Counted(inside) }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val pageSettled = page.count
            val insideSettled = inside.count

            // Compact -> Expanded, inside the frame only.
            frame = 900.dp
            waitForIdle()

            assertTrue(inside.count > insideSettled, "the frame changed shape and its content did not notice")
            assertEquals(
                pageSettled,
                page.count,
                "a frame inside the page crossed a boundary and the page around it recomposed",
            )
        }
    }

    /**
     * The size behind the class follows the window within the class.
     *
     * The class an app holds is the one first provided while the buckets stay
     * the same — that is the point of leaving the dp out of `equals` — so its
     * `heightDp` is the height at the last crossing, not the height now. The
     * date-time picker decided whether its calendar and wheels fit one above the
     * other from that number, and a window grown from 700dp to 880dp, still
     * Medium, went on being 700dp to it. What a component that needs the real
     * size reads is the live one behind the class, inside a derived state, so it
     * hears about the crossings it cares about and not every pixel.
     */
    @Test
    fun theLiveSizeFollowsTheWindowWithinAClass() {
        var height by mutableStateOf(700.dp)
        var held: WindowSizeClass? = null

        runComposeUiTest {
            setContent {
                // A *required* height: the test's own window is 768dp tall, and
                // a plain `height` is held to it, so 880dp would have been 768.
                Box(Modifier.width(400.dp).requiredHeight(height)) {
                    WindowSizeClassProvider {
                        val size = LocalWindowSizeClass.current
                        SideEffect { if (held == null) held = size }
                    }
                }
            }
            waitForIdle()
            val size = assertNotNull(held)
            assertEquals(700.dp, size.currentDpSize.height)

            height = 880.dp
            waitForIdle()
            assertEquals(WindowHeightClass.Medium, size.height, "880dp should still be Medium; the test needs one class")
            assertEquals(880.dp, size.currentDpSize.height, "the live size stayed at the height of the last crossing")
            // And the field, as documented, did not move.
            assertEquals(700.dp, size.heightDp)
        }
    }

    @Test
    fun aClassMadeOutrightIsItsOwnSize() {
        val size = WindowSizeClass.of(412.dp, 915.dp)
        assertEquals(DpSize(412.dp, 915.dp), size.currentDpSize)
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
