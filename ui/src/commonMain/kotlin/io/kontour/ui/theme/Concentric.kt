package io.kontour.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corner and the ring of space a container publishes to what it holds.
 *
 * Constructed by [ProvideConcentric] rather than by hand: a container only has
 * something useful to say here when its corner is a [CornerBasedShape] and the
 * space inside it is the same on all four sides, and that check belongs in one
 * place. See [ProvideConcentric] for why both conditions matter.
 */
@Immutable
class ConcentricContainer internal constructor(
    internal val shape: CornerBasedShape,
    internal val padding: Dp,
)

/**
 * The container the current content is sitting in, or `null` at the top level.
 *
 * Not `staticCompositionLocalOf`: this changes when a container's shape or
 * padding changes, and reads of it are exactly the places that need to redraw
 * when it does.
 */
internal val LocalConcentricContainer = compositionLocalOf<ConcentricContainer?> { null }

/**
 * Publishes a container's corner so the things inside it can nest concentrically.
 *
 * Two rounded rectangles are concentric when the inner radius is the outer
 * radius minus the space between them; get it wrong and the ring visibly widens
 * or pinches around the corner even though it is even along every straight edge.
 * A container knows both numbers. Its content does not, and until now had to be
 * told by hand — which meant a token picked by eye and re-picked whenever
 * anything moved.
 *
 * `Card` and `Toolbar` call this for you — the two standard containers that
 * take both a shape and an even content padding, which is what it takes to have
 * an answer. `Dialog` and `DropdownMenu` do not publish, because neither exposes
 * a content padding to derive from and inventing one would be guessing at a
 * number their callers control. Call this directly when you are building a
 * container of your own.
 *
 * ### It publishes nothing rather than something wrong
 *
 * Two conditions have to hold before a corner can be derived at all, and where
 * either fails this provides `null` and every [concentric] inside falls back to
 * its normal default. Silently deriving a wrong radius would be worse than
 * deriving none.
 *
 * * **The shape has to be corner-based.** An arbitrary [Shape] is a path; there
 *   is no radius to subtract a gap from.
 * * **The padding has to be even.** With 16dp at the sides and 8dp top and
 *   bottom there is no single inner radius that keeps the ring even — the ring
 *   is genuinely uneven, and no corner can fix that. Concentricity is only
 *   defined for a uniform inset.
 *
 * ### It nests
 *
 * A container inside a container publishes its own corner, so the innermost
 * child measures against the thing actually around it rather than against the
 * outermost box. A `Card` in a `Dialog` holding a `Button` gives the button the
 * card's corner less the card's padding, which is the right answer.
 *
 * @param shape The corner this container draws. Usually its own `shape`
 *   parameter, so that a caller who overrides it moves the children with it.
 * @param padding The space between this container's edge and its content.
 */
@Composable
fun ProvideConcentric(
    shape: Shape,
    padding: Dp,
    content: @Composable () -> Unit,
) {
    val container = remember(shape, padding) {
        (shape as? CornerBasedShape)?.let { ConcentricContainer(it, padding) }
    }
    CompositionLocalProvider(LocalConcentricContainer provides container, content = content)
}

/**
 * [ProvideConcentric] for a container whose padding is a [PaddingValues].
 *
 * Publishes nothing unless all four sides agree — see the parent overload for
 * why an uneven ring has no concentric answer to give.
 */
@Composable
fun ProvideConcentric(
    shape: Shape,
    padding: PaddingValues,
    content: @Composable () -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val even = remember(padding, direction) {
        val start = padding.calculateStartPadding(direction)
        val uniform = start == padding.calculateEndPadding(direction) &&
            start == padding.calculateTopPadding() &&
            start == padding.calculateBottomPadding()
        if (uniform) start else null
    }
    if (even == null) {
        CompositionLocalProvider(LocalConcentricContainer provides null, content = content)
    } else {
        ProvideConcentric(shape, even, content)
    }
}

/**
 * The corner that keeps this element concentric with the container around it.
 *
 * Read it where a component asks for a shape:
 *
 * ```
 * Card {
 *     Button(onClick = {}, shape = Theme.shapes.concentric()) { Text("Save") }
 * }
 * ```
 *
 * Outside any container that published one — or inside one that could not
 * publish, see [ProvideConcentric] — this is [orElse], so the same call site
 * works in both places and a component keeps its normal shape when nothing is
 * wrapping it.
 *
 * ### Why this exists as well as [Modifier.concentric]
 *
 * A modifier cannot reach a component's `shape` parameter. A `Button` draws its
 * own `Surface` at its own corner, and clipping the outside of it to a different
 * one does not change the shape — it shears the button's corners flat against
 * the clip, which is the `ButtonGroup`-through-a-pill fault `ToolbarDefaults`
 * has a paragraph about. So anything that *takes* a shape is served here, and
 * anything you clip or background yourself is served by the modifier.
 *
 * It also does not fight an explicit shape, because it is not automatic: a call
 * site either passes this or passes something else. Nothing is overridden behind
 * a caller's back.
 *
 * @param orElse The shape to use when nothing is publishing a container.
 */
@Composable
fun Shapes.concentric(orElse: CornerBasedShape = control): CornerBasedShape {
    val container = LocalConcentricContainer.current ?: return orElse
    return container.shape.inset(container.padding)
}

/**
 * Clips this element to the corner that keeps it concentric with its container.
 *
 * For anything that draws its own background rather than taking a `shape`:
 *
 * ```
 * Card {
 *     Box(Modifier.fillMaxWidth().height(120.dp).concentric().background(image))
 * }
 * ```
 *
 * Outside a container this adds nothing at all — not a clip to some default
 * shape, which would be a square-cornered clip appearing out of nowhere. Opting
 * in somewhere it cannot apply has to be free, or the modifier is unusable in a
 * component that might be placed anywhere.
 *
 * Use [Shapes.concentric] instead for anything that takes a `shape` parameter;
 * that KDoc says why the two cannot be one function.
 *
 * @param extra Space between this element and the container's content box, for
 *   the case where something sits between them — a `Modifier.padding` of your
 *   own, a spacer column. It is added to the container's own padding before the
 *   corner is derived.
 */
@Composable
fun Modifier.concentric(extra: Dp = 0.dp): Modifier {
    val container = LocalConcentricContainer.current ?: return this
    return clip(container.shape.inset(container.padding + extra))
}
