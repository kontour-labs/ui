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
 * backdrop blur — there is no portable one.
 */
@Composable
fun Toolbar(
    modifier: Modifier = Modifier,
    shape: Shape = ToolbarDefaults.Shape,
    containerColor: Color = Theme.colors.surface,
    shadow: Shadow = Theme.elevation.medium,
    contentPadding: Dp = ToolbarDefaults.ContentPadding,
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(Theme.spacing.xxs),
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.semantics { isTraversalGroup = true },
        shape = shape,
        color = containerColor,
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
                content = content,
            )
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
     * One rung of the shape scale, which is what lets [Shape] be the token
     * directly above the children's without any arithmetic at the call site.
     */
    val ContentPadding: Dp = 6.dp

    /**
     * One step up the shape scale from the controls inside, which is what
     * concentric means at this padding.
     *
     * Two rounded shapes nested inside one another look right when the outer
     * radius is the inner radius *plus* the gap between them, and wrong
     * otherwise — the corners stop being parallel and the gap pinches. This used
     * to be [io.kontour.ui.theme.Shapes.pill], which is as far from a `small`
     * [ButtonGroup] as a radius can get: the group's top corner fell *outside*
     * the pill's curve at 4dp of padding and was clipped flat against it, which
     * is what the toolbar's own render showed and nobody read.
     *
     * The scale climbs in even 6dp steps and [ContentPadding] is 6dp, so the
     * rung above the children's `small` is exactly `small + ContentPadding`.
     * That coincidence is not one — the ladder is built to nest. Change
     * [ContentPadding] and this stops being concentric, so derive the shape with
     * [io.kontour.ui.theme.inset] rather than picking a token.
     *
     * **A pill is right for a toolbar of nothing but circular
     * [IconButton]s** — there the children's radius is half their height, the
     * outer radius is half the toolbar's, and those already differ by the
     * padding. Pass `Theme.shapes.pill` for that one.
     */
    val Shape: CornerBasedShape
        @Composable get() = Theme.shapes.medium

    /**
     * Shorter than the toolbar, so the rule floats rather than butting into the
     * padding at both ends.
     */
    val DividerHeight: Dp = 20.dp
}
