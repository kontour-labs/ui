package io.kontour.ui.components.display

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.dragdrop.DropStatus
import io.kontour.ui.dragdrop.DropTargetState
import io.kontour.ui.dragdrop.dropTarget
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.theme.Theme

/**
 * A place to put things down: a bin, an upload area, the end of a column.
 *
 * ```kotlin
 * val archive = rememberDropTargetState(CardType, label = "Archive", onDrop = ::archive)
 * DropZone(archive) {
 *     leading { +Tabler.Outline.Archive }
 *     +"Archive"
 *     message { +"Drop a card here to put it away." }
 * }
 * ```
 *
 * A [dropTarget] that shows where a drag stands with it, laid out as an
 * [EmptyState] is — an icon, a title, a line on what happens, an action:
 *
 * - **At rest**, a dashed edge: a place for something, with nothing in it.
 * - **While something it would take is carried elsewhere**, the edge in the
 *   accent: this is where that could go.
 * - **Over it, and it would be taken**, a solid accent edge on the accent's
 *   ground.
 * - **Over it, and turned away** by the state's `accepts`, the same in the
 *   danger colour, and with `Theme.icons.danger` in place of its icon: the two
 *   differed by colour alone, which is the one difference a reader may not see.
 *
 * For a target that takes only files and text from outside the app — an upload
 * area — make its state with the `rememberDropTargetState(label, external)`
 * that takes no in-app type.
 *
 * Nobody drags with a screen reader or a keyboard. An in-app drop zone is
 * reached from the thing to be moved, as "Move to Archive" among its actions,
 * and that comes with the target; one that takes files has to be reachable
 * another way, so give it an [action][StateScope.action] that opens a picker
 * ([io.kontour.ui.files.rememberFilePicker]).
 *
 * At rest the edge is an outlined field's colour; at high contrast it is drawn
 * at the strong width in every state. The colours fade between states, under
 * reduced motion too: a fade is not movement.
 *
 * @param state What it takes, from [io.kontour.ui.dragdrop.rememberDropTargetState].
 * @param enabled Off, it takes nothing and is drawn as a disabled control is.
 * @param content The words and the action, as an [EmptyState]'s: `+` is the
 *   title.
 */
@Composable
fun DropZone(
    state: DropTargetState<*>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: StateScope.() -> Unit,
) {
    val colours = Theme.colours
    val motion = Theme.motion
    val strong = highContrast()
    val status = if (enabled) state.status else DropStatus.Idle
    val shape = Theme.shapes.container

    // The ground fades from the accent's own with no alpha rather than from
    // transparent black, which goes grey on its way.
    val clear = colours.accent.container.copy(alpha = 0f)
    val edge by animateColorAsState(
        targetValue = when {
            !enabled -> colours.outlineSubtle
            status == DropStatus.Refused -> colours.danger.solid
            status != DropStatus.Idle -> colours.accent.solid
            // An outlined field's edge: a dashed line reads lighter than a
            // solid one, and a place to put things should be as easy to find
            // as a field beside it.
            else -> colours.outlineStrong
        },
        animationSpec = motion.tweenFast(),
        label = "dropZoneEdge",
    )
    val ground by animateColorAsState(
        targetValue = when (status) {
            DropStatus.Over -> colours.accent.container
            DropStatus.Refused -> colours.danger.container
            else -> clear
        },
        animationSpec = motion.tweenFast(),
        label = "dropZoneGround",
    )
    val width by animateDpAsState(
        targetValue = if (strong || status == DropStatus.Over || status == DropStatus.Refused) {
            Theme.sizing.borderWidthStrong
        } else {
            Theme.sizing.borderWidth
        },
        animationSpec = motion.tweenFast(),
        label = "dropZoneEdgeWidth",
    )
    val dashed = status == DropStatus.Idle || status == DropStatus.Available
    val text = when {
        !enabled -> colours.contentDisabled
        status == DropStatus.Over -> colours.accent.onContainer
        status == DropStatus.Refused -> colours.danger.onContainer
        else -> colours.content
    }
    val icon = when {
        !enabled -> colours.contentDisabled
        status == DropStatus.Idle -> colours.contentSubtle
        status == DropStatus.Refused -> colours.danger.onContainer
        status == DropStatus.Over -> colours.accent.onContainer
        else -> colours.accent.solid
    }
    val message = when (status) {
        DropStatus.Over, DropStatus.Refused -> text
        else -> if (enabled) colours.contentMuted else colours.contentDisabled
    }

    // Turned away is said by the mark as well as by the colour.
    val refusedGlyph = Theme.icons.danger
    val slots = stateSlots(content).apply {
        if (status == DropStatus.Refused) leading { +refusedGlyph }
    }

    Box(
        modifier
            .fillMaxWidth()
            .dropTarget(state, enabled)
            // Read as it draws, so a fade redraws the zone and recomposes nothing.
            .zoneFrame(shape, { ground }, { edge }, { width }, dashed),
    ) {
        ProvideContentColour(text) {
            StateBlock(
                modifier = Modifier,
                slots = slots,
                iconTint = icon,
                assertive = false,
                announced = false,
                messageColour = message,
            )
        }
    }
}

/**
 * The ground, and the edge inside the bounds: a stroke centred on the outline
 * would put half of it outside, where a list's clip or the next item takes it.
 */
private fun Modifier.zoneFrame(
    shape: Shape,
    ground: () -> Color,
    edge: () -> Color,
    width: () -> Dp,
    dashed: Boolean,
): Modifier = drawWithCache {
    val fill = shape.createOutline(size, layoutDirection, this)
    val stroke = width().toPx()
    val inset = stroke / 2f
    val outline = shape.createOutline(
        Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
        layoutDirection,
        this,
    )
    val path = Path().apply { addOutline(outline) }
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())) else null
    val style = Stroke(width = stroke, pathEffect = effect)
    onDrawBehind {
        drawOutline(fill, ground())
        translate(inset, inset) { drawPath(path, edge(), style = style) }
    }
}

/** A dash, and the gap after it: long enough to read as a dashed line rather than a dotted one. */
private val DashOn = 8.dp
private val DashOff = 5.dp
