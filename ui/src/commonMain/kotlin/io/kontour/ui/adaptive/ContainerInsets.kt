package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * How far a container's edges are from the window's: what something else has
 * already put between it and the edge.
 *
 * **Why an overlay needs to know.** A window inset is measured from the window's
 * edge, and an overlay that pads by one assumes it reaches that edge. Inside an
 * app whose root already made room for the keyboard — `imePadding()`, a scaffold
 * padded by `safeDrawing`, a window that resized itself — the container ends
 * where the keyboard begins, and padding it by the keyboard's height again lifted
 * a sheet twice as far as it needed to go. Reported from an Android app: *"when a
 * keyboard opens with a sheet open, it sometimes pushes the sheet to way higher
 * than it needs to be"*.
 *
 * `consumeWindowInsets` is Compose's answer, and it is not enough: only
 * `windowInsetsPadding` honours it, and a sheet reads its insets as numbers, in
 * its own layout, to place a surface that is not padded but moved. And an app that
 * padded its root by a number, or a window the system resized, consumes nothing
 * at all. Geometry answers every one of those the same way: whatever part of an
 * inset falls outside the container is already somebody else's.
 *
 * **Measured, mostly.** The far edges are worked out in the container's own
 * measure, from its size, so a keyboard sliding up under an ancestor that shrinks
 * the container is followed in the same frame, with no lag to bounce on. The near
 * edges, and the window's size, come from the last placement: they do not move
 * while the keyboard does.
 *
 * Before the first placement every gap is 0, which is the old reading: the whole
 * inset.
 */
@Stable
internal class ContainerGaps {
    private var leftInRoot by mutableIntStateOf(0)
    private var topInRoot by mutableIntStateOf(0)
    private var rootWidth by mutableIntStateOf(0)
    private var rootHeight by mutableIntStateOf(0)
    private var width by mutableIntStateOf(0)
    private var height by mutableIntStateOf(0)

    /** From the window's left edge to the container's, in pixels. */
    val left: Int get() = leftInRoot
    val top: Int get() = topInRoot

    /** 0 until the window's size is known: a gap not yet measured is no gap. */
    val right: Int get() = if (rootWidth == 0) 0 else (rootWidth - leftInRoot - width).coerceAtLeast(0)
    val bottom: Int get() = if (rootHeight == 0) 0 else (rootHeight - topInRoot - height).coerceAtLeast(0)

    internal fun measured(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    internal fun placed(left: Int, top: Int, rootWidth: Int, rootHeight: Int) {
        // Never negative: a container hanging off the window's edge is still
        // under all of that edge's inset.
        leftInRoot = left.coerceAtLeast(0)
        topInRoot = top.coerceAtLeast(0)
        this.rootWidth = rootWidth
        this.rootHeight = rootHeight
    }
}

/**
 * Keeps [gaps] up to date for the node this is applied to. Measures nothing
 * differently and places nothing differently.
 */
internal fun Modifier.measuresGaps(gaps: ContainerGaps): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        gaps.measured(placeable.width, placeable.height)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    .onGloballyPositioned { coordinates ->
        val at = coordinates.positionInRoot()
        val root = coordinates.findRootCoordinates().size
        gaps.placed(at.x.roundToInt(), at.y.roundToInt(), root.width, root.height)
    }

/**
 * `windowInsetsPadding`, by only the part of [insets] that overlaps this node:
 * see [ContainerGaps]. For a container that fills its overlay and does not move;
 * one that slides would re-measure its content every frame it moved.
 */
@Composable
internal fun Modifier.overlappingInsetsPadding(insets: WindowInsets): Modifier {
    val gaps = remember { ContainerGaps() }
    val overlap = remember(insets, gaps) { OverlapInsets(insets, gaps) }
    return measuresGaps(gaps).windowInsetsPadding(overlap)
}

/**
 * The part of [base] that overlaps the container [gaps] describes: each side
 * less the gap on that side, and never below 0.
 *
 * Read in layout, like any inset, and as live: a sheet's chrome, its float and
 * its content's padding all take this one value, so they cannot disagree.
 */
@Stable
internal class OverlapInsets(
    private val base: WindowInsets,
    private val gaps: ContainerGaps,
) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int =
        (base.getLeft(density, layoutDirection) - gaps.left).coerceAtLeast(0)

    override fun getTop(density: Density): Int = (base.getTop(density) - gaps.top).coerceAtLeast(0)

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int =
        (base.getRight(density, layoutDirection) - gaps.right).coerceAtLeast(0)

    override fun getBottom(density: Density): Int = (base.getBottom(density) - gaps.bottom).coerceAtLeast(0)

    override fun equals(other: Any?): Boolean =
        other is OverlapInsets && other.base == base && other.gaps === gaps

    override fun hashCode(): Int = base.hashCode() * 31 + gaps.hashCode()

    override fun toString(): String = "OverlapInsets($base)"
}
