package io.kontour.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.max

/**
 * A bar whose title is centred on the **bar**, not on whatever its controls left
 * over.
 *
 * A `Row` of leading, weighted title, trailing centres the title in the space
 * between the two — which is the middle of the bar only when both sides happen
 * to be the same width. With a back button on one side and nothing on the other,
 * or a close button on one side and nothing on the other, the title sits visibly
 * off-centre, and it moves when the back button appears or goes. Both have been
 * reported, against the top bar and against the sheet header, and they are the
 * same bug in two components.
 *
 * ### Why not simply pad the short side
 *
 * Because it starves the title. Mirroring the trailing controls' width onto the
 * leading side is arithmetically exactly right and was tried: on a 340dp sheet
 * header with a star and a close on one side, it left the title 104dp and broke
 * "Perth Underground" across two lines mid-word. A centred title that cannot be
 * read is worse than an off-centre one.
 *
 * ### What this does instead
 *
 * Asks the title how wide it would like to be, and centres it **only if it fits
 * there**. When it does, the title is placed in the middle of the bar and the
 * controls sit at the edges, which is what "centred" is supposed to mean. When
 * it does not, the title takes everything between the two sides — off-centre,
 * but whole, and clamped so it never runs under a control.
 *
 * So a bar with room is properly centred, and a bar without room degrades to
 * exactly what it does today rather than to something broken.
 */
@Composable
internal fun CentredBar(
    modifier: Modifier = Modifier,
    centred: Boolean = true,
    leading: @Composable () -> Unit,
    title: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(leading, title, trailing),
        modifier = modifier,
    ) { (leadingItems, titleItems, trailingItems), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val lead = leadingItems.map { it.measure(loose) }
        val trail = trailingItems.map { it.measure(loose) }
        val leadWidth = lead.sumOf { it.width }
        val trailWidth = trail.sumOf { it.width }

        // Unbounded means there is no bar to centre on — a header inside a
        // horizontally scrolling parent, or one being measured for its
        // intrinsics. Fall back to the width the three parts actually need,
        // which is what a `Row` would have given; `layout()` cannot be handed
        // `Constraints.Infinity`.
        val bounded = constraints.hasBoundedWidth
        val titleMeasurable = titleItems.first()
        // How wide the title would like to be, asked before anything is decided:
        // it is both the fallback width below and the test for whether the title
        // can be centred at all.
        val titleWish = titleMeasurable.maxIntrinsicWidth(constraints.maxHeight)
        val width = if (bounded) {
            constraints.maxWidth
        } else {
            (leadWidth + titleWish + trailWidth).coerceAtLeast(constraints.minWidth)
        }
        // The widest the title can be while sitting in the middle of the bar:
        // the same inset on both sides, taken from whichever side needs more.
        val side = max(leadWidth, trailWidth)
        val symmetric = (width - 2 * side).coerceAtLeast(0)
        // And the widest it can be at all, which is what is actually between the
        // two sets of controls.
        val between = (width - leadWidth - trailWidth).coerceAtLeast(0)

        // Centred only when it fits centred. See the note above: a title squeezed
        // into a symmetric slot it cannot fill is the worse of the two failures.
        val useSymmetric = centred && bounded && titleWish <= symmetric
        val titleWidth = if (useSymmetric) symmetric else between
        val titlePlaceable = titleMeasurable.measure(
            Constraints(minWidth = 0, maxWidth = titleWidth, minHeight = 0, maxHeight = constraints.maxHeight)
        )

        val height = maxOf(
            constraints.minHeight,
            titlePlaceable.height,
            lead.maxOfOrNull { it.height } ?: 0,
            trail.maxOfOrNull { it.height } ?: 0,
        )

        layout(width, height) {
            fun place(placeables: List<androidx.compose.ui.layout.Placeable>, startAt: Int) {
                var x = startAt
                placeables.forEach { placeable ->
                    placeable.place(x, Alignment.CenterVertically.align(placeable.height, height))
                    x += placeable.width
                }
            }
            place(lead, 0)
            place(trail, width - trailWidth)

            val titleX = if (useSymmetric) {
                // The middle of the *bar*.
                (width - titlePlaceable.width) / 2
            } else {
                // Everything between the controls, and never under one.
                leadWidth
            }
            titlePlaceable.place(
                x = titleX.coerceIn(leadWidth, max(leadWidth, width - trailWidth - titlePlaceable.width)),
                y = Alignment.CenterVertically.align(titlePlaceable.height, height),
            )
        }
    }
}
