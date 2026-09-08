package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A drag survives the input modality changing underneath it.
 *
 * Reported as "I can drag it one step, but then it just auto drops, and picking
 * it up doesn't always work", on mobile **and** desktop web — and that sentence
 * is almost word for word the one already written into `Reorderable.kt`:
 *
 * > This is the whole of the "it drops after one position" report. The gesture
 * > used to be keyed on `index` — and reordering *changes* a row's index, which
 * > is the gesture succeeding. So the first move restarted the `pointerInput`
 * > node, which cancelled the drag that had just caused it.
 *
 * That was fixed by reading the index live instead of keying on it. The same
 * mistake is one parameter over: the node is `pointerInput(state, immediate)`,
 * and `immediate` is computed from `LocalInputModality`, which
 * `trackInputModality` rewrites **on every pointer event, on the Initial pass**
 * — including the one that starts the drag.
 *
 * So the modality is a thing that changes *during* a gesture, and keying a
 * gesture node on it restarts the node mid-gesture. `awaitEachGesture` then
 * wants a fresh `awaitFirstDown`, which will not come until the finger lifts:
 * the row drops and the pointer is dead until release.
 *
 * ### Why it is intermittent, and why both platforms
 *
 * The local **defaults to `Touch`**. On a desktop the first mouse press is
 * therefore a change, so the first drag of a session is the one that dies and
 * the second works. In a browser the pointer stream mixes touch with the
 * compatibility mouse events that follow it, so it can flip more than once.
 *
 * Driven here by providing the local directly, because that is the thing whose
 * change matters — reproducing the browser's exact event stream would be
 * measuring the browser rather than this component.
 */
class ReorderModalityTest {

    @Test
    fun aDragSurvivesTheModalityChangingUnderIt() {
        val settled = reorderWithFlip(Flip.Never)
        assertEquals(
            "West Leederville", settled.first,
            "the control did not reorder at all, so this test is not measuring " +
                "what it says: ${settled.second}",
        )

        val oscillating = reorderWithFlip(Flip.EveryMove)
        assertEquals(
            "West Leederville", oscillating.first,
            "the row was dropped when the modality oscillated during the drag: " +
                "${oscillating.second}. That is a browser's pointer stream, which " +
                "mixes the compatibility mouse events it synthesises in with the " +
                "touches that produced them.",
        )

        val onPress = reorderWithFlip(Flip.OnPress)
        assertEquals(
            "West Leederville", onPress.first,
            "the row was dropped when the modality changed on the press that " +
                "started the drag: ${onPress.second}. That is a desktop's first " +
                "gesture of a session, every session.",
        )

        val flipped = reorderWithFlip(Flip.MidDrag)
        assertEquals(
            "West Leederville", flipped.first,
            "the row was dropped when the input modality changed mid-drag: the " +
                "list is ${flipped.second} against ${settled.second} without the " +
                "change. `immediate` is a `pointerInput` key and the modality " +
                "moves during a gesture, so the node restarted and took the drag " +
                "with it — the same mistake `currentIndex` exists to avoid.",
        )
    }

    /** Drags the third row down two places, optionally flipping the modality partway. */
    private enum class Flip { Never, OnPress, MidDrag, EveryMove }

    private fun reorderWithFlip(flip: Flip): Pair<String, List<String>> {
        val rows = mutableStateListOf(
            "Perth", "Daglish", "Subiaco", "West Leederville", "Leederville", "Glendalough",
        )
        var bounds = Rect.Zero
        var modality by mutableStateOf(InputModality.Touch)

        Scene(width = 500, height = 300) {
            CompositionLocalProvider(LocalInputModality provides modality) {
                val listState = rememberLazyListState()
                val reorder = rememberReorderableState(listState) { from, to ->
                    rows.add(to, rows.removeAt(from))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(Color.White),
                ) {
                    itemsIndexed(rows) { i, name ->
                        ReorderableItem(state = reorder, index = i, itemCount = rows.size) {
                            ListItem(
                                modifier = if (i == 2) {
                                    Modifier.reportBounds { bounds = it }
                                } else {
                                    Modifier
                                }
                            ) { +name }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val grab = bounds.center
            scene.press(grab)
            if (flip == Flip.OnPress) {
                // What a desktop actually does. The local defaults to `Touch`, so
                // the very first mouse press *is* a change, and it lands on the
                // Initial pass of the same event that starts the gesture.
                modality = InputModality.Mouse
                scene.frame()
            }
            // Long enough to pass the hold, since the modality starts on `Touch`.
            scene.renderUntil(timeoutMillis = 900) { false }
            repeat(Steps) { i ->
                scene.move(grab + Offset(0f, Travel * (i + 1) / Steps))
                scene.frame()
                // Partway through, and only once: a browser's pointer stream
                // mixing a compatibility mouse event into a touch drag.
                if (flip == Flip.MidDrag && i == Steps / 3) {
                    modality = InputModality.Mouse
                    scene.frame()
                }
                if (flip == Flip.EveryMove) {
                    // A browser's stream, which interleaves the compatibility
                    // mouse events it synthesises with the touches they came
                    // from — so the modality does not settle, it oscillates.
                    modality = if (i % 2 == 0) InputModality.Mouse else InputModality.Touch
                    scene.frame()
                }
            }
            scene.release(grab + Offset(0f, Travel))
            scene.frames(10)
        }

        return rows[2] to rows.toList()
    }

    private companion object {
        const val Steps = 12

        /** Two rows down, which is far enough to be unambiguous about where it landed. */
        const val Travel = 160f
    }
}
