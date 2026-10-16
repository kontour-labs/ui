package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.unit.Dp
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

/**
 * [base], less the part of its foot that is only a system handle: iOS's home
 * indicator, or Android's gesture handle — a short line drawn over the app,
 * which leaves the rest of the strip it reserves free to look at.
 *
 * A floating bar padded by the whole of that strip stood clear of empty space:
 * 42dp up on an iPhone, and 32dp over Android's gesture handle. *"move the
 * navbar down a bit, it's very high (especially on ios, since the home bar is
 * not so visible …). on android with button navigation, it's perfect."* So
 * where the foot is a handle, it counts only up to [reach], where the handle
 * ends; Android's buttons, which take taps, are a strip of their own and are
 * kept whole.
 *
 * **Which part is a handle** is the navigation bars' inset less the part of it
 * that takes taps: all of iOS's, all of Android's under gestures, and none of
 * Android's three buttons, or of a desktop's or a browser's, which have none.
 *
 * **A foot that is more than the handle** — a caller's insets with the keyboard
 * in them, say — rises from [reach] back to the whole of itself over the next
 * stretch as long as the trimmed part, twice as fast as it grows, so the bar
 * moves continuously rather than jumping as the keyboard comes up.
 *
 * Read in layout, like any inset.
 */
@Stable
internal class HandleClearingInsets(
    private val base: WindowInsets,
    private val navigationBars: WindowInsets,
    private val tappable: WindowInsets,
    private val reach: Dp,
) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = base.getLeft(density, layoutDirection)

    override fun getTop(density: Density): Int = base.getTop(density)

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = base.getRight(density, layoutDirection)

    override fun getBottom(density: Density): Int {
        val handle = (navigationBars.getBottom(density) - tappable.getBottom(density)).coerceAtLeast(0)
        return handleClearedBottom(base.getBottom(density), handle, with(density) { reach.roundToPx() })
    }

    override fun equals(other: Any?): Boolean =
        other is HandleClearingInsets && other.base == base && other.navigationBars == navigationBars &&
            other.tappable == tappable && other.reach == reach

    override fun hashCode(): Int = ((base.hashCode() * 31 + navigationBars.hashCode()) * 31 + tappable.hashCode()) * 31 + reach.hashCode()

    override fun toString(): String = "HandleClearingInsets($base)"
}

/**
 * The foot [bottom] keeps when [handle] of it is a system handle that ends
 * [reach] up: see [HandleClearingInsets]. All in pixels.
 */
internal fun handleClearedBottom(bottom: Int, handle: Int, reach: Int): Int {
    val trimmed = handle - reach
    if (handle <= 0 || trimmed <= 0) return bottom
    return when {
        bottom <= handle -> minOf(bottom, reach)
        bottom < handle + trimmed -> reach + 2 * (bottom - handle)
        else -> bottom
    }
}

/** The system's own insets [HandleClearingInsets] works out a handle from, when a test stands in for them. */
@Immutable
internal class HandleInsets(val navigationBars: WindowInsets, val tappable: WindowInsets)

/** Null, the default, reads the platform's; a test has none and provides its own. */
internal val LocalHandleInsets = staticCompositionLocalOf<HandleInsets?> { null }

/** [base] as [HandleClearingInsets] has it, against the platform's insets or a test's. */
@Composable
internal fun rememberHandleClearingInsets(base: WindowInsets, reach: Dp): WindowInsets {
    val standIn = LocalHandleInsets.current
    val navigationBars = standIn?.navigationBars ?: WindowInsets.navigationBars
    val tappable = standIn?.tappable ?: WindowInsets.tappableElement
    return remember(base, navigationBars, tappable, reach) { HandleClearingInsets(base, navigationBars, tappable, reach) }
}

/**
 * The margin something floating keeps from each edge of the window: [margin]
 * from the window's edge where nothing but a system handle is there, and
 * [margin] clear of whatever stands at it where something does.
 *
 * **Even, from what can be seen.** A floating sheet used to keep the union of the
 * margin and [base] — the larger of the two, side by side — so its foot sat on
 * top of the whole strip a home indicator or a gesture handle reserves: 34dp up
 * on an iPhone against 12 at the sides, with the line drawn over empty space
 * under it, and, over Android's three buttons, flush on the bar with no margin
 * at all. *"can we have uniform padding around edges of the screen … if there is
 * like 3-button navigation on android, it should be even padding under that and
 * around the edges, because that is opaque."*
 *
 * So a handle — the navigation bars' inset less the part of it that takes taps,
 * the rule [HandleClearingInsets] has — counts for nothing: the margin is taken
 * from the window's edge, under it. Anything more at the foot — Android's
 * buttons, the keyboard — counts whole, and the margin is kept beyond it, so a
 * floating search field stands as far above the keyboard as it does above the
 * edge of the window. Coming up past the handle, the keyboard is followed
 * continuously, as [handleClearedBottom] has it with no reach.
 *
 * The sides the same, over the part of the side insets that takes taps — the
 * three buttons, in landscape — and as before everywhere else: the larger of the
 * margin and the inset, which keeps a landscape iPhone's sheet out from the
 * notch. The top as before too, the larger of the two, for a sheet whose head
 * reaches the status bar.
 *
 * Read in layout, like any inset.
 */
@Stable
internal class FloatingMarginInsets(
    private val base: WindowInsets,
    private val navigationBars: WindowInsets,
    private val tappable: WindowInsets,
    private val margin: Dp,
) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int =
        side(density, base.getLeft(density, layoutDirection), tappable.getLeft(density, layoutDirection))

    override fun getTop(density: Density): Int = maxOf(with(density) { margin.roundToPx() }, base.getTop(density))

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int =
        side(density, base.getRight(density, layoutDirection), tappable.getRight(density, layoutDirection))

    override fun getBottom(density: Density): Int {
        val handle = (navigationBars.getBottom(density) - tappable.getBottom(density)).coerceAtLeast(0)
        return with(density) { margin.roundToPx() } + handleClearedBottom(base.getBottom(density), handle, reach = 0)
    }

    private fun side(density: Density, inset: Int, taps: Int): Int {
        val margin = with(density) { margin.roundToPx() }
        return maxOf(margin + minOf(inset, taps), inset, margin)
    }

    override fun equals(other: Any?): Boolean =
        other is FloatingMarginInsets && other.base == base && other.navigationBars == navigationBars &&
            other.tappable == tappable && other.margin == margin

    override fun hashCode(): Int = ((base.hashCode() * 31 + navigationBars.hashCode()) * 31 + tappable.hashCode()) * 31 + margin.hashCode()

    override fun toString(): String = "FloatingMarginInsets($base, $margin)"
}

/** [FloatingMarginInsets] over [base], against the platform's insets or a test's. */
@Composable
internal fun rememberFloatingMargin(base: WindowInsets, margin: Dp): WindowInsets {
    val standIn = LocalHandleInsets.current
    val navigationBars = standIn?.navigationBars ?: WindowInsets.navigationBars
    val tappable = standIn?.tappable ?: WindowInsets.tappableElement
    return remember(base, navigationBars, tappable, margin) { FloatingMarginInsets(base, navigationBars, tappable, margin) }
}
