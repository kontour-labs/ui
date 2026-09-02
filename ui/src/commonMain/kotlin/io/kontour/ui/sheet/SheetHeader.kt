package io.kontour.ui.sheet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ProvideContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch

/**
 * The grab bar at the top of a sheet.
 *
 * Its only real job is to say "this can be dragged" — so it is drawn, not
 * interactive: the whole sheet is already draggable, and a handle that is the
 * *only* draggable part makes a 4dp-tall target the user has to hit.
 *
 * It does carry the sheet's accessibility actions, though, since a drag gesture
 * is not something a screen reader can perform. Expand and collapse appear as
 * actions on the handle, which is why it is not marked decorative.
 *
 * It widens slightly on hover *and* under a finger — the one hint a pointer user
 * gets that a sheet is draggable at all, and on touch the only acknowledgement
 * that the thing they just pressed is a control. It presses without consuming
 * anything, so the sheet's own drag still starts from the same touch.
 *
 * The bar is 4dp tall and the thing you can press is [DragHandleDefaults.Target],
 * which is not the same number and should not be: a 4dp target is unhittable,
 * and a handle nobody can hit is a handle that never plays its animation.
 */
@Composable
fun DragHandle(
    modifier: Modifier = Modifier,
    state: SheetState? = LocalSheetState.current,
    expandLabel: String = Theme.strings.expandSheet,
    collapseLabel: String = Theme.strings.collapseSheet,
) {
    val scope = rememberCoroutineScope()
    val motion = Theme.motion
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val engaged = hovered || pressed

    val width by animateDpAsState(
        targetValue = if (engaged) 48.dp else 36.dp,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "dragHandleWidth",
    )
    val color by animateColorAsState(
        targetValue = if (engaged) Theme.colors.contentMuted else Theme.colors.outlineStrong,
        animationSpec = motion.tweenFast(),
        label = "dragHandleColor",
    )

    val expanded = state != null && state.currentDetent == state.detents.last()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DragHandleDefaults.Target)
            .hoverable(interactions)
            /**
             * Presses, without taking the gesture.
             *
             * The handle is not clickable and should not become one — the sheet
             * is what a drag from here moves, and a `clickable` would announce a
             * button that does nothing. So this reports presses into the same
             * interaction source hover uses and consumes nothing at all, which
             * leaves the down event to travel on to the sheet's drag exactly as
             * it did before.
             *
             * Without it the widen-and-brighten only ever ran for a mouse, and
             * touch — where the handle is most obviously the thing you would
             * press — got no acknowledgement of any kind.
             */
            .pointerInput(interactions) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    scope.launch { interactions.emit(press) }
                    val up = waitForUpOrCancellation()
                    scope.launch {
                        interactions.emit(
                            if (up == null) {
                                PressInteraction.Cancel(press)
                            } else {
                                PressInteraction.Release(press)
                            }
                        )
                    }
                }
            }
            .semantics {
                contentDescription = if (expanded) collapseLabel else expandLabel
                if (state != null) {
                    expand(expandLabel) {
                        scope.launch { state.expand() }
                        true
                    }
                    collapse(collapseLabel) {
                        scope.launch { state.partialExpand() }
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(width)
                .height(4.dp)
                .background(color, Theme.shapes.pill)
        )
    }
}

object DragHandleDefaults {
    /**
     * How tall the pressable area around the 4dp bar is.
     *
     * Not `minTouchTarget`. The handle is not the only way to move a sheet —
     * the whole sheet is draggable — so this does not have to clear the
     * platform minimum, and a header that reserved 48dp for a decorative bar
     * would push every sheet's content down by that much. 24dp is enough to
     * land on deliberately and small enough that the header still reads as a
     * header.
     */
    val Target: Dp = 24.dp
}

/**
 * Closes the sheet this header is inside, or null when there is nothing to close.
 *
 * The default for [SheetHeader]'s `onClose`, which makes the cases fall out of
 * one parameter: inside a sheet that can close you get a close button that
 * animates it shut, anywhere else you get no close button, and passing a lambda
 * overrides both. Animating matters — a caller-supplied close that only flips a
 * boolean makes the sheet vanish rather than leave.
 *
 * ### Null when the sheet has no [SheetDetent.Hidden]
 *
 * A sheet's detents are what it can be, and one whose detents are `peek` and
 * `half` cannot be nothing: [SheetState.hide] on it is documented as a no-op.
 * Offering a close button there drew a control that could not do the one thing
 * its icon promises — which is worse than the absence of one, because a user who
 * presses it learns nothing except that the app ignored them. Found by a test
 * that presses every control in the catalog and asks what changed.
 */
@Composable
fun closeEnclosingSheet(): (() -> Unit)? {
    val state = LocalSheetState.current ?: return null
    if (SheetDetent.Hidden !in state.detents) return null
    val scope = rememberCoroutineScope()
    return { scope.launch { state.hide() } }
}

/**
 * A sheet's title row, with optional actions on either side.
 *
 * ```kotlin
 * SheetHeader(
 *     modifier = Modifier.sheetPeekAnchor(),
 *     onClose = { showing = false },
 *     actions = { IconButton(Tabler.Outline.Star, "Favourite", onClick = ::favourite) },
 * ) {
 *     +"Perth Underground"
 *     supporting { +"Platform 2 · Joondalup line" }
 * }
 * ```
 *
 * Put [Modifier.sheetPeekAnchor] on it for a sheet with a `peek` detent, and the
 * sheet rests at exactly the height of this header — including at 200% type,
 * where a fixed peek height would cut the title in half.
 *
 * The title is marked as a heading, so a screen reader can jump to it, and the
 * close button is a real button with its own label rather than a glyph in the
 * title row.
 */
@Composable
fun SheetHeader(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = closeEnclosingSheet(),
    closeLabel: String = Theme.strings.close,
    closeIcon: ImageVector = SystemIcons.Close,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: ListItemScope.() -> Unit,
) {
    val slots = listItemSlots(content)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Theme.spacing.md,
                end = Theme.spacing.xs,
                top = Theme.spacing.xs,
                bottom = Theme.spacing.sm,
            ),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.leading?.let { leading -> ContentSlot(content = leading) }

        Column(Modifier.weight(1f)) {
            slots.label?.let { title ->
                Box(Modifier.semantics { heading() }) {
                    ProvideTextStyle(Theme.typography.titleMedium) {
                        ContentSlot(maxLines = 2, content = title)
                    }
                }
            }
            slots.supporting?.let { supporting ->
                ProvideContentColor(Theme.colors.contentMuted) {
                    ProvideTextStyle(Theme.typography.bodySmall) {
                        ContentSlot(maxLines = 1, content = supporting)
                    }
                }
            }
        }

        actions?.invoke(this)

        if (onClose != null) {
            IconButton(
                icon = closeIcon,
                contentDescription = closeLabel,
                onClick = onClose,
            )
        }
    }
}

