package io.kontour.ui.components.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.LocalTouchTargetOwnedByParent
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.CapsuleCap
import io.kontour.ui.theme.ProvideConcentric
import io.kontour.ui.theme.outset
import io.kontour.ui.theme.Shadow
import io.kontour.ui.foundation.VerticalDivider
import io.kontour.ui.theme.Theme

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
 *     IconButton(Tabler.Outline.Layers, "Map layers", onClick = ::openLayers)
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
    Surface(
        modifier = modifier.semantics { isTraversalGroup = true },
        shape = shape,
        colour = containerColour,
        shadow = shadow,
        // An elevated surface over content is white on whatever is behind it
        // with a shadow for an edge, and a shadow does not change between
        // contrast tiers. Same reasoning as an elevated `Card`.
        border = contrastEdge(),
    ) {
        // The bar owns the touch target for the controls in it.
        //
        // Left to themselves each `IconButton` reserves `minTouchTarget` and
        // centres a 40dp visual inside it, so on Android the 4dp arrangement gap
        // draws at 12dp and the bar grows 8dp taller than the concentricity
        // maths below assumes — the radius is derived from a child that is 40dp
        // here and 48dp there, so the nesting stops being concentric on the one
        // platform anybody looks at it on. Sizing the bar instead keeps the gaps
        // the gaps they were authored as, and a full-height strip is still a
        // target a finger can hit. See [LocalTouchTargetOwnedByParent].
        CompositionLocalProvider(LocalTouchTargetOwnedByParent provides true) {
            Row(
                modifier = Modifier
                    .padding(contentPadding)
                    .defaultMinSize(minHeight = Theme.sizing.minTouchTarget),
                horizontalArrangement = arrangement,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The bar already derives its own corner from its children's
                // (see `ToolbarDefaults.Shape`); this publishes the same
                // relationship the other way round, so a child that is *not* a
                // standard control — a custom chip, a menu anchor — can ask for
                // the corner that matches instead of guessing at one.
                val row = this
                ProvideConcentric(shape, contentPadding) { row.content() }
            }
        }
    }
}

/**
 * Separates one cluster of actions from the next.
 *
 * Inset from the toolbar's own padding rather than running its full height, so
 * it reads as a division between groups rather than as the edge of two
 * toolbars pushed together.
 */
@Composable
fun ToolbarDivider(modifier: Modifier = Modifier) {
    VerticalDivider(
        modifier
            .padding(horizontal = Theme.spacing.xxs)
            .height(ToolbarDefaults.DividerHeight)
    )
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
    val ContentPadding: Dp = 6.dp

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
     * [CapsuleCap] ends that, and it is worth being precise about how, because
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
        @Composable get() = Theme.shapes.control.outset(ContentPadding)

    /**
     * Shorter than the toolbar, so the rule floats rather than butting into the
     * padding at both ends.
     */
    val DividerHeight: Dp = 20.dp
}
