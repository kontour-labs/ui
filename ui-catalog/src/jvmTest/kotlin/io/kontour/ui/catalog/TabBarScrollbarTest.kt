package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tab row that scrolls says so, on a phone as well as under a pointer.
 *
 * Asked for as "a horizontal scrollbar for a scrollable `TabBar`, as an option
 * on `Scrollbar` rather than duplicated". `Scrollbar` needed no new behaviour —
 * it already takes a `ScrollState`, already threads `orientation` through every
 * branch, and a horizontal one is already in production over the docs site's
 * code blocks. What `TabBar` lacked was a scroll state anything could read: it
 * built one inline and never named it.
 *
 * ### `alwaysVisible`, and why it is not a default being overridden lightly
 *
 * `Scrollbar` returns early for anything but a mouse — `LocalInputModality` is
 * `Touch` by default and `supportsHover` is true only for `Mouse`. That is the
 * right default for a scrollbar over content you can already see and drag: on a
 * phone it would be clutter over a thing the finger is touching anyway.
 *
 * A tab row is the case where it is not. Its whole problem is that you cannot
 * tell there are more tabs past the edge, and an affordance that appears only
 * once you have a pointer to discover them with answers the wrong question. The
 * report came from a phone, so a scrollbar the phone never draws would have been
 * shipping nothing.
 *
 * It still draws nothing when there is nothing to scroll — that is `Scrollbar`'s
 * own `geometry.isUseful`, which is why the second test here is a control rather
 * than a duplicate.
 */
class TabBarScrollbarTest {

    private val Tabs = listOf(
        "Departures", "Arrivals", "Platforms", "Disruptions", "Lifts", "Fares",
    )

    /** Ink in the bottom [rows] of [bounds], where the bar is drawn. */
    private fun BufferedImage.inkAlongTheBottom(bounds: Rect, rows: Int): Int {
        var count = 0
        for (y in (bounds.bottom.toInt() - rows) until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                val p = getRGB(x, y)
                val luminance = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
                if (luminance < 210) count++
            }
        }
        return count
    }

    private fun ink(scrollable: Boolean, tabs: List<String>): Int {
        var bounds = Rect.Zero
        var ink = 0
        // Narrow enough that six tabs cannot fit, so a scrollable row really has
        // somewhere to scroll.
        Scene(width = 420, height = 160) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TabBar(
                    scrollable = scrollable,
                    modifier = Modifier.width(200.dp).reportBounds { bounds = it },
                ) {
                    tabs.forEachIndexed { index, label ->
                        Tab(selected = index == 0, onClick = {}, key = index) { +label }
                    }
                }
            }
        }.use { scene ->
            ink = scene.frames(8).inkAlongTheBottom(bounds, rows = 10)
        }
        return ink
    }

    @Test
    fun aScrollableRowDrawsItsBarWithNoPointerAnywhere() {
        // `LocalInputModality` is `Touch` in a scene, which is the whole point:
        // this is the configuration the report came from.
        val scrolling = ink(scrollable = true, tabs = Tabs)
        val fixed = ink(scrollable = false, tabs = Tabs)

        assertTrue(
            scrolling > fixed + 200,
            "a scrollable tab row put ${scrolling}px of ink along its bottom " +
                "edge against ${fixed}px for a fixed one — not enough to be a " +
                "scrollbar. On touch `Scrollbar` draws nothing unless it is " +
                "asked to, so a tab row that overflows was giving a finger no " +
                "sign that there was anything past the edge.",
        )
    }

    @Test
    fun aRowWithNothingToScrollDrawsNoBar() {
        // The control, and it is `Scrollbar`'s rule rather than this call site's:
        // two tabs fit, so there is no travel and nothing to say.
        //
        // Two *short* tabs. A first draft used "Departures" and "Arrivals" and
        // reported 748px of bar, because two words of that length overflow a
        // 200dp row just as six do — so the control was measuring the same
        // overflow the test above does, from the other side.
        val short = listOf("Go", "Up")
        assertEquals(
            ink(scrollable = false, tabs = short),
            ink(scrollable = true, tabs = short),
            "a scrollable row with nothing to scroll drew something a fixed one " +
                "did not — `alwaysVisible` is supposed to override the pointer " +
                "rule, not the useful-geometry one",
        )
    }
}
