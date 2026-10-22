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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.foundation.CentredBar
import io.kontour.ui.foundation.CentredBarTitle
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.nav.BarNavigation
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.pointerCursor
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
    val colour by animateColorAsState(
        targetValue = if (engaged) Theme.colours.contentMuted else Theme.colours.outlineStrong,
        animationSpec = motion.tweenFast(),
        label = "dragHandleColor",
    )

    val expanded = state != null && state.currentDetent == state.detents.last()
    // **Only the actions that would do something.** Expand and collapse were
    // offered whatever the sheet was doing, so a modal sheet at its one size —
    // `Hidden` and `Expanded`, the default — offered both and did nothing for
    // either, and had no way at all to be put away by someone who cannot drag.
    // Derived, so a sheet moving between detents recomposes the handle only
    // when what it can be asked to do changes; read here rather than in the
    // semantics block, so that change reaches the block.
    val moves = remember(state) {
        derivedStateOf { if (state == null) HandleMoves.None else handleMoves(state) }
    }.value
    val dismissLabel = Theme.strings.close

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DragHandleDefaults.Target)
            .pointerCursor(Cursor.ResizeRow)
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
                    if (moves.expand) {
                        expand(expandLabel) {
                            scope.launch { state.expand() }
                            true
                        }
                    }
                    if (moves.collapse) {
                        collapse(collapseLabel) {
                            scope.launch { state.partialExpand() }
                            true
                        }
                    }
                    if (moves.dismiss) {
                        dismiss(dismissLabel) {
                            scope.launch { state.hide() }
                            true
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(width)
                .height(4.dp)
                .background(colour, Theme.shapes.capsule)
        )
    }
}

/** Which of its accessibility actions a [DragHandle] offers. */
private data class HandleMoves(val expand: Boolean, val collapse: Boolean, val dismiss: Boolean) {
    companion object {
        val None = HandleMoves(expand = false, collapse = false, dismiss = false)
    }
}

/**
 * What the handle's actions would do for [state] where it is now: expand only
 * with a taller detent to go to, collapse only with a shorter one, and dismiss
 * where the sheet may be hidden and the user is allowed to hide it.
 *
 * Taller and shorter by **offset**, not by place in the list, because two
 * detents can resolve to one height and the anchors keep only one of them: a
 * sheet whose peek is all its content is at `Expanded` when it is at its peek,
 * and an expand there goes nowhere. By place in the list before there are
 * anchors to ask, which is the only answer there is then.
 */
private fun handleMoves(state: SheetState): HandleMoves {
    val allowed = state.allowedDetents
    val current = state.currentDetent
    val tallest = allowed.lastOrNull { it != SheetDetent.Hidden }
    val shortest = allowed.firstOrNull { it != SheetDetent.Hidden }
    val here = state.offsetOf(current)
    fun taller(target: SheetDetent?): Boolean {
        if (target == null || target == current) return false
        val there = state.offsetOf(target)
        if (here.isNaN() || there.isNaN()) return allowed.indexOf(target) > allowed.indexOf(current)
        return there < here - 0.5f
    }
    fun shorter(target: SheetDetent?): Boolean {
        if (target == null || target == current) return false
        val there = state.offsetOf(target)
        if (here.isNaN() || there.isNaN()) return allowed.indexOf(target) < allowed.indexOf(current)
        return there > here + 0.5f
    }
    return HandleMoves(
        expand = taller(tallest),
        collapse = shorter(shortest),
        dismiss = current != SheetDetent.Hidden && SheetDetent.Hidden in allowed && state.userDismissible,
    )
}

/** What a [DragHandle] takes by default. */
object DragHandleDefaults {
    /**
     * How tall the pressable area around the 4dp bar is.
     *
     * Not `minTouchTarget`. The handle is not the only way to move a sheet —
     * the whole sheet is draggable — so this does not have to clear the
     * platform minimum, and a header that reserved 48dp for a decorative bar
     * would push every sheet's content down by that much.
     *
     * In a sheet it is the band above the content, and the band is the content's
     * top padding: nothing under it adds any more. That is what took the
     * forehead away — *"a fair bit of extra space between the top of the sheet
     * header … and the sheet handle"* — which was this band with a header's own
     * padding and its close button's slack stacked under it.
     *
     * 24dp, with the bar in the middle of it. It was 16dp for a while, the same
     * as the sheet's sides, and the bar then sat 6dp over the content — *"the top
     * of it looks a bit cramped now"*. So the top is the one side with more than
     * [BottomSheetDefaults.ContentPadding], by the handle's own room.
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
 * How much room a [SheetHeader] takes, and where its title sits.
 *
 * Three of the four a [io.kontour.ui.nav.TopBarStyle] offers, and deliberately
 * the same names: a sheet that is a screen wants the same vocabulary as the
 * screen it replaced, and a caller who has met one should not have to learn the
 * other. There is no `Hero`: a picture under the status bar is the top of a page,
 * and the top of a sheet is its handle.
 *
 * There is no collapse-on-scroll here, which is the one thing [Large] does at
 * the top of a page and cannot do on a sheet: a sheet already answers the same
 * gesture by moving between its detents, and two things responding to one drag
 * is the sheet fighting its own header.
 */
enum class SheetHeaderStyle {
    /** One line, title on the leading side. The default. */
    Small,

    /**
     * One line, title centred — or, where it cannot be centred on the header
     * because the controls leave it too little room, exactly [Small].
     *
     * For a sheet with a control on both sides — a close on one, an action on
     * the other — where a leading title reads off-balance against them.
     *
     * Centred on the **header**, not on the room its controls left over — see
     * [io.kontour.ui.foundation.CentredBar], which this and
     * [io.kontour.ui.nav.TopBarStyle.Centred] share. Both were reported for the
     * same defect and both had the same cause: a weighted title in a row is
     * centred between the two sides, which is the middle only when they happen
     * to be the same width.
     */
    Centred,

    /**
     * Two lines: the controls on the first, a large title on the second.
     *
     * For a sheet that *is* the destination rather than a panel over one — a
     * whole trip, a form — where the title is what the user came for and the
     * close button should not be sitting on its line competing with it.
     */
    Large,
}

/**
 * A sheet's title row, with optional actions on either side.
 *
 * ```kotlin
 * SheetHeader(
 *     modifier = Modifier.sheetPeekAnchor(),
 *     onClose = { showing = false },
 *     actions = { IconButton(icon = Tabler.Outline.Star, contentDescription = "Favourite", onClick = ::favourite) },
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
 *
 * @param onBack Draws a back button on the leading side, as a
 *   [TopBar][io.kontour.ui.nav.TopBar]'s does: for a sheet that has gone a step
 *   in — a list, then one of its items — and comes back out without closing.
 * @param backLabel What a screen reader calls the back button.
 * @param navigation Something else to lead with in its place, as a top bar's
 *   `navigation`. Takes precedence over [onBack].
 * @param onClose What the close button does, or **`null` for no close button** —
 *   which is the way to say a sheet cannot be dismissed from its header, and
 *   pairs with `dismissible = false` on the sheet itself. The default asks the
 *   enclosing sheet to close, and is already `null` when there is not one.
 * @param actions The row *inside* the header, beside the title. For controls
 *   that ride above the sheet's top edge instead, see `BottomSheet`'s
 *   `floatingControls`.
 * @param style How much room the header takes. See [SheetHeaderStyle].
 */
@Composable
fun SheetHeader(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = closeEnclosingSheet(),
    closeLabel: String = Theme.strings.close,
    closeIcon: ImageVector = Theme.icons.close,
    style: SheetHeaderStyle = SheetHeaderStyle.Small,
    onBack: (() -> Unit)? = null,
    backLabel: String = Theme.strings.back,
    backIcon: ImageVector = Theme.icons.chevronBack,
    navigation: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: ListItemScope.() -> Unit,
) {
    val slots = listItemSlots(content)
    val leads = navigation != null || onBack != null

    @Composable
    fun Controls(scope: RowScope) {
        with(scope) {
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

    @Composable
    fun Title(textStyle: TextStyle, modifier: Modifier = Modifier) {
        // Centred by the bar when it has centred the title, and started when it
        // has not — see `CentredBarTitle`. A Large title is given a range, so it
        // starts.
        CentredBarTitle(modifier = modifier) {
            slots.label?.let { title ->
                Box(Modifier.semantics { heading() }) {
                    ProvideTextStyle(textStyle) {
                        ContentSlot(maxLines = 2, content = title)
                    }
                }
            }
            slots.supporting?.let { supporting ->
                ProvideContentColour(Theme.colours.contentMuted) {
                    ProvideTextStyle(Theme.typography.bodySmall) {
                        // Ellipsised rather than cut: a centred title is judged on
                        // its heading alone, and the line under it made to fit.
                        ContentSlot(maxLines = 1, overflow = TextOverflow.Ellipsis, content = supporting)
                    }
                }
            }
        }
    }

    // Uneven on purpose: `md` is the sheet's own content inset, where the title
    // lines up with what it heads, and the close button's own padding makes up the
    // rest at the trailing end. The horizontal half is handed to the bar rather
    // than put round it, so it counts towards the centre: padding outside the bar
    // moved a centred title 4dp towards the trailing edge.
    //
    // Further in on a display whose corners are rounder than the sheet's own:
    // see [SheetCornerRoom].
    val inset = BottomSheetDefaults.ContentPadding
    val room = LocalSheetCornerRoom.current
    // A back button's own padding makes up the rest at the leading end, as the
    // close button's does at the trailing.
    val start = (if (leads) Theme.spacing.xs else inset) + room.start
    val end = Theme.spacing.xs + room.end
    val banded = LocalSheetBand.current
    val top = if (banded) 0.dp else inset + room.top
    val controls = actions != null || onClose != null || leads
    val icon = Theme.sizing.iconLarge
    // What is drawn, as against what is laid out: a 48dp button is a 24dp icon
    // with 12dp of nothing round it, and the space round a header is measured to
    // what is drawn. Written as the pieces measure, read as the header does.
    val drawn = remember { DrawnHeights() }

    when {
        style != SheetHeaderStyle.Large -> CentredBar(
            modifier = modifier
                .fillMaxWidth()
                .uniformInset(inset, top) { bar ->
                    val visual = maxOf(drawn.title, drawn.leading, if (controls) icon.roundToPx() else 0)
                    val slack = (bar - visual) / 2
                    slack to slack
                },
            centred = style == SheetHeaderStyle.Centred,
            startInset = start,
            endInset = end,
            leading = {
                if (leads || slots.leading != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BarNavigation(onBack, backLabel, backIcon, navigation)
                        slots.leading?.let { leading ->
                            Box(Modifier.recordHeight { drawn.leading = it }) { ContentSlot(content = leading) }
                        }
                    }
                }
            },
            title = {
                // No padding of its own. The bar already insets the whole header
                // by `md`, which is the sheet's own content padding, and a second
                // `sm` on top of that put the title and its supporting line
                // further in than everything they head.
                Title(textStyle = Theme.typography.titleMedium, modifier = Modifier.recordHeight { drawn.title = it })
            },
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Controls(this)
                }
            },
        )

        // Controls first, title under them. The title gets the whole width, so
        // it is the one thing here that never has to truncate around a button.
        // It shared the controls' line for a while when nothing led it, and was
        // put back: the title on a line of its own is what the style is for.
        else -> Column(
            modifier = modifier
                .fillMaxWidth()
                .uniformInset(inset, top) {
                    // Above: the controls' row, whose buttons are icons with
                    // slack round them. Below: the title, which has none.
                    val visual = maxOf(drawn.leading, if (controls) icon.roundToPx() else 0)
                    ((drawn.row - visual) / 2).coerceAtLeast(0) to 0
                }
                .padding(start = start, end = end),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().recordHeight { drawn.row = it },
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarNavigation(onBack, backLabel, backIcon, navigation)
                slots.leading?.let { leading ->
                    Box(Modifier.recordHeight { drawn.leading = it }) { ContentSlot(content = leading) }
                }
                Spacer(Modifier.weight(1f))
                Controls(this)
            }

            // In line with what it heads, whatever the row above leads with.
            Title(
                textStyle = Theme.typography.titleLarge,
                modifier = if (leads) Modifier.padding(start = inset - Theme.spacing.xs) else Modifier,
            )
        }
    }
}

/** The heights of a header's pieces, as they last measured. Plain: read in the same pass. */
private class DrawnHeights {
    var title = 0
    var leading = 0
    var row = 0
}

/** Reports this node's measured height, in the measure pass. */
private fun Modifier.recordHeight(onHeight: (Int) -> Unit): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    onHeight(placeable.height)
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/**
 * [top] above what the header draws and one [inset] below it, whatever its
 * buttons reserve round their icons.
 *
 * [slack] is the space above and below what is drawn, inside the measured header —
 * a 48dp close button round a 24dp title leaves 12dp each way. The header gives
 * that back: it is laid out [top] tall above what it draws and [inset] below,
 * and the slack overlaps those instead of adding to them. Under a sheet's handle
 * band, the band is the space above, so [top] is zero there.
 *
 * Drawn above its own top by the slack when banded, into the band: the button's
 * empty top reaches under the handle, which draws nothing there, and the header
 * is composed after the band, so the button still takes the presses there.
 */
private fun Modifier.uniformInset(
    inset: Dp,
    top: Dp,
    slack: Density.(height: Int) -> Pair<Int, Int>,
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val insetPx = inset.roundToPx()
    val topPx = top.roundToPx()
    val (over, under) = slack(placeable.height).let { (a, b) -> a.coerceIn(0, insetPx) to b.coerceIn(0, insetPx) }
    val height = constraints.constrainHeight(topPx - over + placeable.height - under + insetPx)
    layout(placeable.width, height) { placeable.placeRelative(0, topPx - over) }
}

