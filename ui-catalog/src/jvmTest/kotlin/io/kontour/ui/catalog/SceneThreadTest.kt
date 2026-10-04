package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [Scene] composes, lays out and answers input on the AWT event thread.
 *
 * Because Compose posts its own work on a scene to that thread — the
 * `RectManager` dispatch that compacts the scene's `RectList` — and a scene is
 * not safe to share. Driven from the test's thread, `MonthJumpTest` failed on CI
 * with `LayoutNode … not found in RectList` during a drag, which surfaced as an
 * `IndexOutOfBoundsException` in the date picker's pager and, a test later, as
 * `NavBarGeometryTest`'s "uncaught exceptions before test". See
 * [onComposeUiThread].
 *
 * The race itself is a race, and a test of it would be one too. This pins the
 * thing that removes it: every phase that touches the scene's nodes, seen from
 * inside them, on the one thread.
 */
class SceneThreadTest {

    @Test
    fun theSceneIsDrivenOnTheThreadComposePostsTo() {
        val seen = mutableMapOf<String, MutableSet<Boolean>>()
        fun saw(phase: String) {
            seen.getOrPut(phase) { mutableSetOf() } += SwingUtilities.isEventDispatchThread()
        }

        Scene(width = 200, height = 200) {
            SideEffect { saw("composition") }
            Box(
                Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        saw("layout")
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                                saw("input")
                            }
                        }
                    },
            )
        }.use { scene ->
            scene.frames(2)
            scene.tap(Offset(100f, 100f))
            scene.frames(1)
        }

        assertEquals(setOf("composition", "layout", "input"), seen.keys, "a phase never ran: $seen")
        for ((phase, onEventThread) in seen) {
            assertTrue(
                onEventThread == setOf(true),
                "$phase ran off the AWT event thread, where Compose posts its own work on the " +
                    "scene: two threads in one scene is how a node goes missing from its RectList",
            )
        }
    }
}
