package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.AnchoredDropdownMenu
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.anchorBounds
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A menu with little room below opens above, sooner than a popover does.
 *
 * Reported of the combobox: the menu flips above the field when it is too close to
 * the keyboard or the bottom of the screen — but only once there is almost nothing
 * left, and before that it opens below *two items high* and scrolls. The floor it
 * decided against was the popover's, 64dp, which is "a line of text"; a menu is a
 * list to choose from, and two rows of one is not a list anybody can choose from.
 *
 * The floor for a menu is now about four and a half rows — the half so a reader can
 * see there is more — but never more than the menu itself, so a short menu that
 * fits below still opens below.
 */
class MenuFlipTest {

    @Test
    fun aLongMenuWithTwoRowsOfRoomBelowOpensAbove() {
        val (anchor, panel) = openMenu(items = 12, roomBelow = 150)
        assertTrue(panel.height > 0f, "the menu never reported a size")
        assertTrue(
            panel.bottom <= anchor.top,
            "with 150dp under the field, a twelve-item menu opened at ${panel.top}..${panel.bottom} " +
                "against a field at ${anchor.top}..${anchor.bottom} — below it, about two rows high",
        )
    }

    @Test
    fun aShortMenuThatFitsBelowStillOpensBelow() {
        val (anchor, panel) = openMenu(items = 2, roomBelow = 150)
        assertTrue(panel.height > 0f, "the menu never reported a size")
        assertTrue(
            panel.top >= anchor.bottom,
            "a two-item menu fits in the 150dp under its field and opened above it instead",
        )
    }

    /** A field-sized anchor with [roomBelow] dp between it and the bottom of the window. */
    private fun openMenu(items: Int, roomBelow: Int): Pair<Rect, Rect> {
        var anchor = Rect.Zero
        var panel = Rect.Zero
        val height = 800
        Scene(width = 800, height = height * 2, reduceMotion = true) {
            var bounds by remember { mutableStateOf<Rect?>(null) }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (height - roomBelow - FieldHeight).dp)
                            .size(width = 240.dp, height = FieldHeight.dp)
                            .anchorBounds { bounds = it }
                            .reportBounds { anchor = it }
                    )
                    AnchoredDropdownMenu(
                        visible = bounds != null,
                        anchor = bounds,
                        onDismissRequest = {},
                        modifier = Modifier.reportBounds { panel = it },
                        matchAnchorWidth = true,
                    ) {
                        repeat(items) { index -> item(label = "Stop $index", onClick = {}) }
                    }
                }
            }
        }.use { scene -> scene.frames(40) }
        return anchor to panel
    }

    private companion object {
        const val FieldHeight = 48
    }
}
