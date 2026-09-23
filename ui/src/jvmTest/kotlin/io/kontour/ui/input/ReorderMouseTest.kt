package io.kontour.ui.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.ReorderableState
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.foundation.SystemIcons
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A mouse picks a row up where it grabbed it, and the row stays under it.
 *
 * The reorder gesture waited for the *touch* slop whatever the pointer was —
 * `awaitTouchSlopOrCancellation` hard-codes a finger — and threw away the distance
 * past it. On a desktop that is 18dp, so a row followed the mouse 18dp behind for
 * the whole drag, and the grip the reader had taken hold of slid away from under
 * the pointer the moment the row lifted. Compose's own drag detectors use a mouse's
 * slop, which is an eighth of a dp.
 *
 * `CursorScene` gives the scene a desktop's slop, 18dp, rather than the eighteen
 * pixels a bare test scene has, which at this density would halve the distance the
 * old code lost and hide half of it.
 */
class ReorderMouseTest {

    @Test
    fun aMouseDragsARowWithoutTrailingIt() {
        var reorder: ReorderableState? = null
        var row = Rect.Zero
        var content = Rect.Zero
        CursorScene(width = 800, height = 600) {
            val list = rememberLazyListState()
            val state = rememberReorderableState(list) { _, _ -> }
            reorder = state
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(3) { index ->
                    ReorderableItem(
                        state = state,
                        index = index,
                        modifier = if (index == 0) {
                            Modifier.onGloballyPositioned { row = it.boundsInRoot() }
                        } else {
                            Modifier
                        },
                        itemCount = 3,
                        handleIcon = SystemIcons.More,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .then(
                                    if (index == 0) {
                                        Modifier.onGloballyPositioned { content = it.boundsInRoot() }
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }.use { scene ->
            val grip = Offset((content.right + row.right) / 2f, row.center.y)
            scene.press(grip)
            // Forty pixels, less than half a row, so nothing is reordered and the
            // offset is the whole of the answer.
            scene.drag(grip, grip + Offset(0f, 40f))

            val state = checkNotNull(reorder)
            assertEquals(0, state.draggingIndex, "the row was never picked up by a 40px mouse drag")
            val offset = state.dragOffset
            assertTrue(
                abs(offset - 40f) <= 1f,
                "the mouse moved 40px and the row moved ${offset}px — it trails the pointer by " +
                    "${40f - offset}px, a finger's slop rather than a mouse's",
            )
            scene.release(grip + Offset(0f, 40f))
        }
    }
}
