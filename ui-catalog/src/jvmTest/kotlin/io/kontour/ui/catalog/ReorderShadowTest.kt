package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.GripVertical
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.rememberReorderableState
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The lifted row's shadow is the row's shape, handle or no handle.
 *
 * Reported as the shadow going "a bit wonky" once drag handles are enabled, with
 * a guess attached — that something still assumes the container is full width.
 * A guess is not a diagnosis and a screenshot golden cannot tell the two
 * pictures apart at the tolerance it compares at, so this measures the drawn
 * shadow directly and reports the number either way.
 *
 * ### What it measures
 *
 * A row is lifted through `ReorderableState.start`, which is public precisely so
 * a lifted row can be photographed without a gesture. The row under it is plain
 * white, so any pixel darker than white on the row's own line is shadow. The
 * measurement is **how far the shadow reaches past the row's left and right
 * edges**, which is the quantity a wrong width would move.
 *
 * ### What it found
 *
 * Nothing, at this density. Both spans came back `26..573` — identical to the
 * pixel — so the box the shadow is cast from is the same either way and the
 * "full width" guess is not the fault. Written down because a measurement that
 * clears a suspect is worth as much as one that convicts, and because the next
 * person to read the report should start somewhere else.
 *
 * Two places it cannot look, and they are where the remaining suspicion sits.
 * `shadowElevation = 8f` in `ReorderableItem` is a **raw pixel value** rather
 * than `8.dp.toPx()`, so the shadow is a third as deep at 3x as at 1x — a real
 * defect, and not one a handle changes. And `ReorderGrip` carries
 * `minimumTouchTarget()`, which is 24dp on the JVM and 44 to 48 on a phone: a
 * row whose content is shorter than that is inflated by its own grip there and
 * not here, and a shadow cast from a box taller than the row looks like it
 * reads. Neither is reproducible in this harness.
 */
class ReorderShadowTest {

    private fun shadowReach(handle: ImageVector?): Pair<Int, Int> {
        val rows = mutableStateListOf("Perth", "Daglish", "Subiaco")
        var image: BufferedImage? = null

        Scene(width = Width, height = Height) {
            val listState = rememberLazyListState()
            val reorder = rememberReorderableState(listState) { from, to ->
                rows.add(to, rows.removeAt(from))
            }
            // Lift the middle row without a gesture.
            androidx.compose.runtime.LaunchedEffect(Unit) { reorder.start(1) }
            Box(Modifier.fillMaxSize().background(Color.White).padding(Gutter.dp)) {
                LazyColumn(state = listState) {
                    itemsIndexed(rows) { index, name ->
                        ReorderableItem(
                            state = reorder,
                            index = index,
                            itemCount = rows.size,
                            handleIcon = handle,
                        ) {
                            ListItem { +name }
                        }
                    }
                }
            }
        }.use { scene ->
            image = scene.frames(30)
        }

        val shot = image!!
        // The middle row's vertical centre.
        val y = shot.height / 2
        fun darkAt(x: Int): Boolean = (shot.getRGB(x, y) and 0xFFFFFF) != 0xFFFFFF

        val left = (0 until shot.width).firstOrNull { darkAt(it) } ?: shot.width
        val right = (shot.width - 1 downTo 0).firstOrNull { darkAt(it) } ?: 0

        return left to right
    }

    @Test
    fun theShadowIsTheSameWidthWithAHandleAsWithout() {
        val (bareLeft, bareRight) = shadowReach(handle = null)
        val (handleLeft, handleRight) = shadowReach(handle = Tabler.Outline.GripVertical)

        assertTrue(
            bareRight > bareLeft,
            "nothing was drawn without a handle, so this measured nothing: " +
                "$bareLeft..$bareRight",
        )
        assertTrue(
            kotlin.math.abs(bareLeft - handleLeft) <= Tolerance &&
                kotlin.math.abs(bareRight - handleRight) <= Tolerance,
            "the lifted row's shadow spans $handleLeft..$handleRight with a " +
                "handle and $bareLeft..$bareRight without one. A handle changes " +
                "what is *inside* the row; it must not change the box the shadow " +
                "is cast from.",
        )
    }

    private companion object {
        const val Width = 600
        const val Height = 400
        const val Gutter = 24

        /** A pixel either side, for antialiasing on the shadow's own fade. */
        const val Tolerance = 2
    }
}
