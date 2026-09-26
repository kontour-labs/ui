package io.kontour.ui.components.action

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.LocalTouchTargetOwnedByParent
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.VerticalDivider
import io.kontour.ui.theme.Shapes
import io.kontour.ui.theme.ProvideConcentric
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.outset

/**
 * A floating surface holding actions, over content it does not belong to.
 *
 * ```kotlin
 * Toolbar {
 *     ButtonGroup {
 *         item(onClick = ::zoomOut, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
 *         item(onClick = ::zoomIn, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
 *     }
 *     ToolbarDivider()
 *     IconButton(icon = Tabler.Outline.Layers, contentDescription = "Map layers", onClick = ::openLayers)
 * }
 * ```
 *
 * This is deliberately thin — a `Surface` and a `Row` — and it is worth having
 * for the same reason [io.kontour.ui.components.display.Card] is: it fixes the
 * elevation, shape, padding and traversal semantics in one place so a screen
 * that grows a second toolbar does not grow a second set of numbers.
 *
 * **It is not [io.kontour.ui.nav.TopBar].** A top bar is *part of* the screen —
 * it holds the title, sits at the top, and its `actions` slot is where a
 * screen's own actions go. A toolbar floats **over** content that is not its
 * own, which is why it has a shadow and rounded corners and a top bar has
 * neither. If it is the screen's chrome, it is a top bar.
 *
 * **It is not a [ButtonGroup].** A group joins buttons into one control; this
 * holds several controls that are merely near each other. A toolbar usually
 * contains a group.
 *
 * For a translucent one over a live map, wrap
 * [io.kontour.ui.motion.GlassSurface] instead and read the note there about
 * backdrop blur — there is no portable one for a bar over live content. A modal
 * is a different problem and does blur; see
 * [io.kontour.ui.overlay.BackdropStyle].
 */
@Composable
fun Toolbar(
    modifier: Modifier = Modifier,
    shape: Shape = ToolbarDefaults.Shape,
    containerColour: Color = Theme.colours.surface,
    shadow: Shadow = Theme.elevation.medium,
    contentPadding: Dp = ToolbarDefaults.ContentPadding,
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(Theme.spacing.xxs),
    content: @Composable RowScope.() -> Unit,
) {
    ToolbarSurface(modifier, shape, containerColour, shadow, contentPadding, Orientation.Horizontal) {
        Row(
            modifier = Modifier
                .padding(contentPadding)
                .defaultMinSize(minHeight = Theme.sizing.minTouchTarget),
            horizontalArrangement = arrangement,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val row = this
            ProvideConcentric(shape, contentPadding) { row.content() }
        }
    }
}

/**
 * The same bar, stood on its end.
 *
 * ```kotlin
 * VerticalToolbar(Modifier.align(Alignment.CenterEnd)) {
 *     IconButton(icon = Tabler.Outline.Plus, contentDescription = "Zoom in", onClick = ::zoomIn)
 *     IconButton(icon = Tabler.Outline.Minus, contentDescription = "Zoom out", onClick = ::zoomOut)
 *     ToolbarDivider()
 *     IconButton(icon = Tabler.Outline.Layers, contentDescription = "Map layers", onClick = ::openLayers)
 * }
 * ```
 *
 * For the edge of a wide window, where a horizontal bar would either stretch
 * across content it is not about or sit in the middle of it — a map's zoom
 * controls, a canvas's tools, anything that belongs beside the thing it acts on
 * rather than under it.
 *
 * **A separate composable rather than an `orientation` parameter**, because the
 * slot type changes with the axis: a bar laid out in a column hands its content
 * a `ColumnScope`, and a single function cannot offer both without handing out
 * a scope that lies about one of them. `HorizontalDivider` and `VerticalDivider`
 * are the same call, made for the same reason, and this file already uses them.
 *
 * Everything else is shared, including the two things that are easy to lose:
 * the bar owns its children's touch targets, and it publishes its own shape so
 * a child that is not a standard control can ask for the matching corner.
 * [ToolbarDivider] follows the axis by itself.
 */
@Composable
fun VerticalToolbar(
    modifier: Modifier = Modifier,
    shape: Shape = ToolbarDefaults.Shape,
    containerColour: Color = Theme.colours.surface,
    shadow: Shadow = Theme.elevation.medium,
    contentPadding: Dp = ToolbarDefaults.ContentPadding,
    arrangement: Arrangement.Vertical = Arrangement.spacedBy(Theme.spacing.xxs),
    content: @Composable ColumnScope.() -> Unit,
) {
    ToolbarSurface(modifier, shape, containerColour, shadow, contentPadding, Orientation.Vertical) {
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .defaultMinSize(minWidth = Theme.sizing.minTouchTarget),
            verticalArrangement = arrangement,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val column = this
            ProvideConcentric(shape, contentPadding) { column.content() }
        }
    }
}

/**
 * What both bars are, minus the axis.
 *
 * The two things in here are the ones a second copy would quietly drop.
 *
 * `LocalTouchTargetOwnedByParent` — left to themselves each `IconButton`
 * reserves `minTouchTarget` and centres a 40dp visual inside it, so on Android
 * the 4dp arrangement gap draws at 12dp and the bar grows 8dp past what the
 * concentricity maths assumes: the radius is derived from a child that is 40dp
 * here and 48dp there, so the nesting stops being concentric on the one platform
 * anybody looks at it on. Sizing the bar instead keeps the gaps the gaps they
 * were authored as, and a full-height strip is still a target a finger can hit.
 *
 * And [LocalToolbarOrientation], so [ToolbarDivider] can be one name that draws
 * the right rule rather than two the caller has to choose between.
 */
@Composable
private fun ToolbarSurface(
    modifier: Modifier,
    shape: Shape,
    containerColour: Color,
    shadow: Shadow,
    contentPadding: Dp,
    orientation: Orientation,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.semantics { isTraversalGroup = true },
        shape = shape,
        containerColour = containerColour,
        shadow = shadow,
        // An elevated surface over content is white on whatever is behind it
        // with a shadow for an edge, and a shadow does not change between
        // contrast tiers. Same reasoning as an elevated `Card`.
        border = contrastEdge(),
    ) {
        CompositionLocalProvider(
            LocalTouchTargetOwnedByParent provides true,
            LocalToolbarOrientation provides orientation,
            content = content,
        )
    }
}

/**
 * Which way the toolbar this is inside runs.
 *
 * Read by [ToolbarDivider] and nothing else. A divider is the one part of a
 * toolbar whose drawing depends on the axis, and making the caller pass it
 * would be asking them to repeat something the bar they are already inside
 * knows.
 */
internal val LocalToolbarOrientation = staticCompositionLocalOf { Orientation.Horizontal }

/**
 * Separates one cluster of actions from the next.
 *
 * Inset from the toolbar's own padding rather than running its full height, so
 * it reads as a division between groups rather than as the edge of two
 * toolbars pushed together.
 */
@Composable
fun ToolbarDivider(modifier: Modifier = Modifier) {
    // Across the bar, whichever way the bar runs. See [LocalToolbarOrientation].
    when (LocalToolbarOrientation.current) {
        Orientation.Horizontal -> VerticalDivider(
            modifier
                .padding(horizontal = Theme.spacing.xxs)
                .height(ToolbarDefaults.DividerLength)
        )

        Orientation.Vertical -> HorizontalDivider(
            modifier
                .padding(vertical = Theme.spacing.xxs)
                .width(ToolbarDefaults.DividerLength)
        )
    }
}

object ToolbarDefaults {
    /**
     * The ring of space between the surface's edge and its first control.
     *
     * It is the step between two rungs of the shape scale, and since the cap
     * landed it is once again what decides whether the nesting is concentric:
     * [Shape] is this much larger than a child's corner, by construction. See
     * [Shape] for why that stopped being free.
     */
    val ContentPadding: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.toolbarPadding

    /**
     * A child's shape, grown by the ring of space around it.
     *
     * Concentric means the outer radius is the inner one plus the gap between
     * them, and this is that sentence written down rather than arithmetic that
     * happens to come out right.
     *
     * It used to be the bare `control` shape, shared with the buttons, and that
     * *was* concentric by construction: both were uncapped capsules, so the
     * outer radius was half the bar's height and the inner half a child's — and
     * a child inset by [ContentPadding] top and bottom is shorter by exactly
     * twice it, so the two radii differed by exactly [ContentPadding], whatever
     * the numbers were.
     *
     * [Shapes.CapsuleCap] ends that, and it is worth being precise about how, because
     * the failure is invisible in the token and obvious on the screen. A 56dp
     * bar and a 44dp button are both above `small`, so both stop at 18 — two
     * equal radii with 6dp between them. The ring stays 6dp along every straight
     * edge and closes to nothing at the corners, which is exactly the pinch this
     * whole round is about.
     *
     * [outset] resolves the child's corner against the box the child is drawn on
     * — this box less [ContentPadding] on each side — and adds the gap back. So
     * the bar comes out at 24 where its buttons are at 18, and the rule holds
     * again whatever the padding, the height or the buttons turn out to be.
     *
     * It got here the long way. It was a pill; then a `ButtonGroup`'s 8dp
     * corners were found poking *through* the pill's curve and being sheared
     * flat against it, so it became one rung up the size scale instead; then the
     * children became capsules and the bare token was right again; and now it is
     * derived, which is the first version of this that says what it means.
     */
    val Shape: CornerBasedShape
        @Composable get() {
            val control = Theme.shapes.control
            val padding = ContentPadding
            // Remembered: every read of this property built another shape, and a
            // toolbar reads it on each recomposition. See `SquirclePaths`.
            return remember(control, padding) { control.outset(padding) }
        }

    /**
     * Shorter than the toolbar, so the rule floats rather than butting into the
     * padding at both ends.
     *
     * A length rather than a height, because it is the divider's height in a
     * horizontal bar and its width in a vertical one.
     */
    val DividerLength: Dp = 20.dp
}
