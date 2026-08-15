package io.kontour.ui.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/** Sizing shared by every menu surface. Override per call site if you must. */
object MenuDefaults {
    /** Distance between a menu and the control it drops from. */
    val Gap: Dp = 4.dp

    /** How close a menu may come to the edge of the window. */
    val ScreenMargin: Dp = 8.dp

    val MinWidth: Dp = 180.dp
    val MaxWidth: Dp = 320.dp

    /** Beyond this the menu scrolls rather than growing. */
    val MaxHeight: Dp = 400.dp
}

/**
 * A menu that drops from the control it is declared next to.
 *
 * ```
 * Box {
 *     IconButton(TablerIcons.Dots, "More", onClick = { expanded = true })
 *     DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
 *         MenuItem("Share", onClick = ::share, leadingIcon = TablerIcons.Share)
 *         MenuDivider()
 *         MenuItem("Delete", onClick = ::delete, destructive = true)
 *     }
 * }
 * ```
 *
 * Anchors to its immediate parent, so the layout that positions the trigger
 * positions the menu too. It renders into the [OverlayHost] rather than inline,
 * which is what lets it escape a clipping parent — a menu on the last row of a
 * scrolling list is the case that catches naive implementations.
 *
 * Opening scales it up from the corner nearest the anchor, so it reads as coming
 * *out of* the control rather than appearing over it.
 *
 * @param side Which way it prefers to open. Flipped automatically when there is
 *   no room — see [positionAnchored].
 * @param matchAnchorWidth For a select, whose menu should line up with the field
 *   it belongs to.
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    side: OverlaySide = OverlaySide.Bottom,
    alignment: OverlayAlignment = OverlayAlignment.Start,
    matchAnchorWidth: Boolean = false,
    scrim: ScrimStyle = ScrimStyle.Transparent,
    content: @Composable MenuScope.() -> Unit,
) {
    var anchor by remember { mutableStateOf<Rect?>(null) }
    Box(Modifier.parentBounds { anchor = it })

    AnchoredDropdownMenu(
        expanded = expanded,
        anchor = anchor,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        side = side,
        alignment = alignment,
        matchAnchorWidth = matchAnchorWidth,
        scrim = scrim,
        content = content,
    )
}

/**
 * A [DropdownMenu] anchored to a rectangle you supply rather than to its parent.
 *
 * ```kotlin
 * var field by remember { mutableStateOf<Rect?>(null) }
 *
 * Row(Modifier.anchorBounds { field = it }) { … }
 * AnchoredDropdownMenu(expanded, field, onDismissRequest = { … }) { … }
 * ```
 *
 * [DropdownMenu] covers the common case by anchoring to the layout it is
 * declared inside. This one is for when that is not the right node: a select,
 * whose menu must line up with the field frame rather than with whichever slot
 * the menu happened to be declared in; a context menu at a pointer position; a
 * submenu attached to its own row.
 *
 * Pair it with [Modifier.anchorBounds] on the element the menu belongs to.
 */
@Composable
fun AnchoredDropdownMenu(
    expanded: Boolean,
    anchor: Rect?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    side: OverlaySide = OverlaySide.Bottom,
    alignment: OverlayAlignment = OverlayAlignment.Start,
    matchAnchorWidth: Boolean = false,
    scrim: ScrimStyle = ScrimStyle.Transparent,
    key: Any = remember { Any() },
    content: @Composable MenuScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    val density = LocalDensity.current
    val modality = LocalInputModality.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    val body by rememberUpdatedState(content)

    val shape = Theme.shapes.medium
    val anchorWidth = anchor?.let { with(density) { it.width.toDp() } } ?: Dp.Unspecified

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(expanded, anchor, side, alignment, matchAnchorWidth, scrim) {
        val target = anchor
        if (!expanded || target == null) {
            host.hide(key)
            return@LaunchedEffect
        }

        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Menu,
                scrim = scrim,
                dismissLabel = "Close menu",
                onDismiss = { dismiss() },
                // A menu should not steal focus from a mouse user mid-gesture,
                // but it must contain focus for a keyboard user. Trapping does
                // both: the ring only shows under keyboard modality anyway.
                trapFocus = true,
                content = {
                    AnchoredOverlayLayout(
                        anchorInRoot = target,
                        side = side,
                        alignment = alignment,
                        gap = MenuDefaults.Gap,
                        margin = MenuDefaults.ScreenMargin,
                        minWidth = if (matchAnchorWidth) anchorWidth else Dp.Unspecified,
                    ) {
                        MenuPanel(
                            modifier = modifier,
                            shape = shape,
                            side = side,
                            alignment = alignment,
                            matchAnchorWidth = matchAnchorWidth,
                            autoFocus = modality == InputModality.Keyboard,
                            onDismissRequest = { dismiss() },
                            content = body,
                        )
                    }
                },
            )
        )
    }
}

@Composable
private fun MenuPanel(
    modifier: Modifier,
    shape: Shape,
    side: OverlaySide,
    alignment: OverlayAlignment,
    matchAnchorWidth: Boolean,
    autoFocus: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable MenuScope.() -> Unit,
) {
    val motion = Theme.motion
    val focusRequester = remember { FocusRequester() }
    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appeared = true
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }

    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "menu",
    )

    // Grow from the corner nearest the anchor, so the menu reads as unfolding
    // out of the control rather than materialising over it.
    val origin = TransformOrigin(
        pivotFractionX = when {
            side == OverlaySide.Start -> 1f
            side == OverlaySide.End -> 0f
            alignment == OverlayAlignment.End -> 1f
            alignment == OverlayAlignment.Center -> 0.5f
            else -> 0f
        },
        pivotFractionY = when (side) {
            OverlaySide.Top -> 1f
            OverlaySide.Bottom -> 0f
            else -> 0.5f
        },
    )

    OverlaySurface(
        modifier = modifier
            // Scale only from 0.9 — a menu springing from nothing is a lot of
            // movement for something the user opens dozens of times.
            .overlayAppearance(progress, fromScale = 0.9f, origin = origin)
            .widthIn(
                min = if (matchAnchorWidth) Dp.Unspecified else MenuDefaults.MinWidth,
                max = MenuDefaults.MaxWidth,
            )
            .heightIn(max = MenuDefaults.MaxHeight)
            .focusRequester(focusRequester)
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismissRequest()
                    true
                } else {
                    false
                }
            },
        shape = shape,
    ) {
        // `fillMaxWidth`, not `width(IntrinsicSize.Max)`. The panel's own
        // `widthIn(min = MenuDefaults.MinWidth)` is satisfied by the surface,
        // and `Surface` does not propagate its minimum inward — so an
        // intrinsic-width column sized itself to the widest row, sat at
        // `TopStart`, and left the difference as bare surface down the trailing
        // edge. Rows filled the *column*, so the hover wash and the dividers
        // stopped short of the panel edge with it. Under RTL the gutter simply
        // moved to the other side.
        //
        // Filling the panel makes every row the panel's width, which is what a
        // menu row has always looked like it was.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Theme.spacing.xxs, vertical = Theme.spacing.xxs)
                .fillMaxWidth(),
        ) {
            MenuScopeImpl(this, onDismissRequest).content()
        }
    }
}

/**
 * One row in a menu.
 *
 * @param trailingText For a keyboard shortcut. Rendered muted and never
 *   truncated before the label is — the label is what the user is reading.
 * @param selected Marks the current choice in a menu that picks one of several.
 *   Draws a check and reports `Role.RadioButton` to assistive tech, because "one
 *   of these is on" is the thing being conveyed.
 * @param destructive Renders in the danger tone. For anything the user cannot
 *   undo.
 */
@Composable
fun MenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingText: String? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = Theme.colors
    val feedback = LocalFeedback.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }

    val contentColor = when {
        !enabled -> colors.contentDisabled
        destructive -> colors.danger.solid
        else -> colors.content
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = if (selected) Role.RadioButton else Role.Button
                if (selected) this.selected = true
            }
            .minimumTouchTarget()
            .padding(horizontal = Theme.spacing.xxs)
            .clip(Theme.shapes.small)
            .clickable(
                interactionSource = interactions,
                // A menu row is a big target; scaling it makes the whole menu
                // look like it is wobbling.
                indication = kontourIndication(Theme.shapes.small, pressScale = 1f),
                enabled = enabled,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onClick()
                },
            )
            .padding(horizontal = Theme.spacing.sm, vertical = Theme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                size = Theme.sizing.iconMedium,
                tint = contentColor,
            )
        }

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = Theme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
        )

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = Theme.typography.labelSmall,
                color = if (enabled) colors.contentMuted else colors.contentDisabled,
                maxLines = 1,
            )
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                size = Theme.sizing.iconSmall,
                tint = if (enabled) colors.contentMuted else colors.contentDisabled,
            )
        }
        if (selected) {
            Icon(
                imageVector = SystemIcons.Check,
                contentDescription = null,
                size = Theme.sizing.iconSmall,
                tint = if (enabled) colors.accent else colors.contentDisabled,
            )
        }
    }
}

/** Separates groups of related items. */
@Composable
fun MenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(
            horizontal = Theme.spacing.xs,
            vertical = Theme.spacing.xxs,
        ),
        color = Theme.colors.outlineSubtle,
    )
}

/**
 * Labels a group of items.
 *
 * Not focusable and not clickable, so keyboard navigation skips straight over it
 * — a heading that can be tabbed to but does nothing is a dead stop.
 */
@Composable
fun MenuSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(
            start = Theme.spacing.sm,
            end = Theme.spacing.sm,
            top = Theme.spacing.xs,
            bottom = Theme.spacing.xxs,
        ),
        style = Theme.typography.labelSmall,
        color = Theme.colors.contentMuted,
    )
}

/**
 * A menu item that opens a menu of its own.
 *
 * ```
 * SubMenu("Sort by") {
 *     MenuItem("Departure", onClick = { … }, selected = sort == Departure)
 *     MenuItem("Duration", onClick = { … }, selected = sort == Duration)
 * }
 * ```
 *
 * Opens on hover for a mouse and on tap for a finger, which is the difference
 * that makes nested menus usable on touch at all — hover-only submenus are
 * unreachable without a pointer, and tap-only submenus feel sticky with a mouse.
 * It opens to the [OverlaySide.End] side and flips to [OverlaySide.Start] near
 * the window edge.
 *
 * Nested menus are worth being sparing with. Two levels is a category; three is
 * a filing system, and by then the flat list with section headers is easier to
 * scan.
 */
@Composable
fun SubMenu(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    content: @Composable MenuScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val modality = LocalInputModality.current

    // Hover opens it for a pointer; a tap opens it for a finger. Closing on
    // un-hover is deliberately not done here — the pointer has to travel across
    // the gap to reach the submenu, and closing en route is the classic
    // nested-menu frustration. The outside-tap scrim closes it instead.
    LaunchedEffect(hovered, modality) {
        if (hovered && modality.supportsHover) open = true
    }

    Box(Modifier.anchorBounds { bounds = it }) {
        MenuItem(
            label = label,
            onClick = { open = !open },
            modifier = modifier,
            leadingIcon = leadingIcon,
            trailingIcon = SystemIcons.ChevronForward,
            enabled = enabled,
            interactionSource = interactions,
        )
    }

    AnchoredDropdownMenu(
        expanded = open && enabled,
        anchor = bounds,
        onDismissRequest = { open = false },
        side = OverlaySide.End,
        alignment = OverlayAlignment.Start,
        content = content,
    )
}

/**
 * Wraps [content] so that a right-click — or a long press on touch — opens
 * [menu] at the pointer.
 *
 * ```
 * ContextMenuArea(
 *     menu = {
 *         MenuItem("Copy stop ID", onClick = ::copy)
 *         MenuItem("Add to favourites", onClick = ::favourite)
 *     },
 * ) {
 *     StopRow(stop)
 * }
 * ```
 *
 * The two triggers are not interchangeable dialects of the same thing: a long
 * press must not fire for a mouse user who simply holds the button still, and a
 * secondary click cannot happen on a touchscreen. Both are wired, and the long
 * press is gated on the active [io.kontour.ui.input.InputModality].
 *
 * A context menu should never be the *only* way to reach an action. Anything in
 * here needs a visible route too.
 */
@Composable
fun ContextMenuArea(
    menu: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var anchor by remember { mutableStateOf<Rect?>(null) }
    var coordinates by remember {
        mutableStateOf<LayoutCoordinates?>(null)
    }
    val feedback = LocalFeedback.current
    val modality = LocalInputModality.current

    fun openAt(local: Offset) {
        val coords = coordinates ?: return
        val root = coords.localToRoot(local)
        // A zero-size anchor: the menu hangs off the point itself, which is what
        // makes it feel like it belongs to the click rather than the row.
        anchor = Rect(root, root)
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates = it }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val secondary = event.buttons.isSecondaryPressed
                        if (event.type == PointerEventType.Press && secondary) {
                            val change = event.changes.firstOrNull() ?: continue
                            change.consume()
                            openAt(change.position)
                        }
                    }
                }
            }
            .pointerInput(enabled, modality) {
                if (!enabled || !modality.needsLargeTargets) return@pointerInput
                detectTapGestures(
                    onLongPress = { position ->
                        feedback.perform(FeedbackIntent.LongPress)
                        openAt(position)
                    },
                )
            }
    ) {
        content()
    }

    AnchoredDropdownMenu(
        expanded = anchor != null,
        anchor = anchor,
        onDismissRequest = { anchor = null },
        side = OverlaySide.Bottom,
        alignment = OverlayAlignment.Start,
        content = menu,
    )
}

