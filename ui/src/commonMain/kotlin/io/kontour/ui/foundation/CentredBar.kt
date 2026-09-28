package io.kontour.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * So a bar with room is properly centred, and a bar without room degrades to a
 * leading title rather than to something broken.
 *
 * ### A title that cannot be centred is a leading title
 *
 * It used to degrade to a title *centred in what was left* — off-centre, and
 * visibly so with two actions at one end and nothing at the other, which is how
 * it was reported: "the centred style is not really that centred". So a title
 * that does not fit centred is laid out exactly as the non-centred style lays it
 * out, against the leading controls.
 *
 * The decision is made at measure time, where composition cannot see it, so it
 * is handed to the title in its constraints: **a title this bar has centred is
 * measured at exactly the width it is centred in**, and one it has not is given
 * a range. [CentredBarTitle] reads that and centres its lines, or starts them.
 *
 * @param startInset Room at the leading edge, inside the bar, before the leading
 *   controls — padding that counts towards the centre. Padding put *around* the
 *   bar instead moves its centre when the two sides differ, which is how a sheet
 *   header's title came to sit 4dp right of the sheet's middle.
 * @param endInset The same at the trailing edge.
 */
@Composable
internal fun CentredBar(
    modifier: Modifier = Modifier,
    centred: Boolean = true,
    startInset: Dp = 0.dp,
    endInset: Dp = 0.dp,
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
        val start = startInset.roundToPx()
        val end = endInset.roundToPx()
        // The insets count as part of each side: the title is centred on the bar,
        // and kept clear of the controls and of the room beyond them.
        val leadWidth = start + lead.sumOf { it.width }
        val trailWidth = end + trail.sumOf { it.width }

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
        // Exactly `symmetric` wide when centred, and a range when not: the
        // contract [CentredBarTitle] reads.
        val titlePlaceable = titleMeasurable.measure(
            if (useSymmetric) {
                Constraints.fixedWidth(symmetric).copy(maxHeight = constraints.maxHeight)
            } else {
                Constraints(minWidth = 0, maxWidth = between, minHeight = 0, maxHeight = constraints.maxHeight)
            }
        )

        val height = maxOf(
            constraints.minHeight,
            titlePlaceable.height,
            lead.maxOfOrNull { it.height } ?: 0,
            trail.maxOfOrNull { it.height } ?: 0,
        )

        layout(width, height) {
            fun placeRun(placeables: List<Placeable>, startAt: Int) {
                var x = startAt
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, Alignment.CenterVertically.align(placeable.height, height))
                    x += placeable.width
                }
            }
            placeRun(lead, start)
            placeRun(trail, width - trailWidth)

            val titleX = if (useSymmetric) {
                // The middle of the *bar*.
                (width - titlePlaceable.width) / 2
            } else {
                // Everything between the controls, and never under one.
                leadWidth
            }
            titlePlaceable.placeRelative(
                x = titleX.coerceIn(leadWidth, max(leadWidth, width - trailWidth - titlePlaceable.width)),
                y = Alignment.CenterVertically.align(titlePlaceable.height, height),
            )
        }
    }
}

/**
 * A [CentredBar] title's lines, stacked: centred on each other and on the bar
 * when the bar has centred the title, and against the leading edge when it has
 * not.
 *
 * The bar says which by the constraints it measures the title with — exactly the
 * width it is centred in, or a range — so a title that could not be centred is
 * laid out exactly as the non-centred style lays it out: a title *and* its
 * supporting line starting at the leading edge, not a supporting line centred
 * under a title pushed against the controls.
 */
@Composable
internal fun CentredBarTitle(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val centre = constraints.hasFixedWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val lines = measurables.map { it.measure(loose) }
        val widest = lines.maxOfOrNull { it.width } ?: 0
        // The whole range when there is one, so a leading title is as wide as
        // the room it was given, as a full-width column was.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else widest
        val height = lines.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var y = 0
            lines.forEach { line ->
                line.placeRelative(if (centre) (width - line.width) / 2 else 0, y)
                y += line.height
            }
        }
    }
}
