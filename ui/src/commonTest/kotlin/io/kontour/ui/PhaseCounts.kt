package io.kontour.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout

/**
 * How many times a node was measured, placed and drawn.
 *
 * The three phases counted apart, because the difference between them is the
 * whole subject: a node placed every frame is being moved, and a node measured
 * or drawn every frame is having work redone that a graphics layer would let it
 * keep.
 */
internal class PhaseCounts {
    var measures = 0
    var placements = 0
    var draws = 0

    fun reset() {
        measures = 0
        placements = 0
        draws = 0
    }

    override fun toString(): String = "measures=$measures placements=$placements draws=$draws"
}

/**
 * Counts every measure, placement and draw of the node this is applied to.
 *
 * Applied inside a real sheet through its ordinary content slot, so what is
 * counted is the real component in the real tree. A replica assembled by the
 * test is the easiest way to write a performance test that passes while the
 * component it is named after stays slow.
 */
internal fun Modifier.countPhases(into: PhaseCounts): Modifier = this
    .layout { measurable, constraints ->
        into.measures++
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            into.placements++
            placeable.place(0, 0)
        }
    }
    .drawWithContent {
        into.draws++
        drawContent()
    }
