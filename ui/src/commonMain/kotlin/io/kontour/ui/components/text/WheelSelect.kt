package io.kontour.ui.components.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.components.datetime.WheelDrive
import io.kontour.ui.components.datetime.WheelDrum
import io.kontour.ui.components.datetime.WheelPickerDefaults
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.interaction.verticalDragOwning
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.LocalOverlayProgress
import io.kontour.ui.overlay.LocalOverlaySafeArea
import io.kontour.ui.overlay.MenuDefaults
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.OverlaySurface
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.overlay.anchorBounds
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * How long a wheel opened by a drag stays open once it has been left alone —
 * no finger on it and the drum at rest on a row — before it folds back into the
 * field: long enough to read the value it landed on among its neighbours, or to
 * reach in for another flick, short enough that the field is back before the
 * next thing is reached for.
 */
private const val FoldAfterRestMillis = 350L

/** Where a [WheelSelect]'s wheel opens. */
enum class WheelSelectPlacement {
    /**
     * Over the field, about its value: the drum grows out of the field above
     * and below, with the row in the middle where the field's value was. The
     * default.
     */
    OverField,

    /**
     * Still about the field's value, but open to one side of it: the row in
     * the middle stays where the field's value was, the wheel's other rows run
     * below it — or above it, where there is room there instead, as a
     * [Select]'s menu picks its side — and half of the next row shows on the
     * other side, fading out, to say the wheel goes on that way too. Near the
     * top or the foot of the window it opens away from that edge, rather than
     * being pushed off the field to fit.
     */
    BelowOrAbove,
}

/**
 * Picks one of a list, on a wheel that opens out of the field.
 *
 * ```kotlin
 * WheelSelect(
 *     value = passengers,
 *     options = (1..9).toList(),
 *     onValueChange = { passengers = it },
 *     label = "Passengers",
 * )
 * ```
 *
 * Closed it is a field like [Select]'s, in the same frame and with the same
 * label, helper and error. Opened, the field turns into a [io.kontour.ui.components.datetime.WheelPicker]:
 * the drum grows out of it above and below, with the value where the field's
 * value was, and turning it changes the value as each row comes to the middle.
 *
 * **Two ways in.** A tap opens the wheel and it stays open: turn it, tap the
 * value in the middle, tap anywhere else, or go back, and it closes. A drag up
 * or down on the closed field turns the wheel *at once*: it opens around the
 * finger, follows it — the same ticks, the same give at either end and the same
 * fling onto a row as a finger on the drum — and folds back into the field once
 * it has been left alone a moment. A finger back on it before then keeps it open
 * for another flick, as many as a long list needs. The second is the one a
 * regular reaches for; the first is the one a newcomer finds.
 *
 * **What the drag costs.** A vertical drag that starts on the field turns the
 * wheel rather than scrolling the page, as a slider's sideways drag is its own
 * inside a pager. In a long scrolling form that can be the wrong trade: pass
 * `dragToPick = false` and the field only opens on a tap.
 *
 * Reach for it over [Select] where the options are an ordered run — a count, a
 * size, a duration — and nudging a value a step or two is the usual change. A
 * list of unrelated names reads better as a menu.
 *
 * @param value The value shown, and the row the wheel opens on. One of
 *   [options]; anything else opens on the first.
 * @param optionLabel How each option reads, in the field and on the wheel.
 * @param visibleItems How many rows the open wheel shows. Odd, so one sits in
 *   the middle, over where the field was.
 * @param infinite Whether the wheel wraps around; see
 *   [io.kontour.ui.components.datetime.WheelPicker]'s `infinite`.
 * @param dragToPick Whether a vertical drag on the closed field turns the wheel
 *   straight away. Off, only a tap opens it.
 * @param placement Where the wheel opens: about the field, by default, or
 *   about the field's value but open to one side, as a menu picks its side.
 *   Asked for as *"can we make the wheel select able to "anchor" the wheel to
 *   the top or the bottom of the box that opens? similar to how other select
 *   components pick which side they show up based on how close the box is to
 *   the edge of the screen"*, and then as *"still centred on the actual field,
 *   but it just shows more elements above/below (depending on the side) and
 *   also has some sort of indication that there are more elements on the other
 *   side"*. A drag on the field turns it the same either way.
 * @param state Whether the wheel is open, hoisted — shared with [Select].
 * @param onValueChangeFinished Called when the wheel comes to rest after the
 *   reader turned it, on the row it stopped on. [onValueChange] follows every
 *   row the wheel passes — the field shows each — so a save, a request or a
 *   toast belongs here instead, once a flick has settled rather than once a
 *   row: *"it's toasting every time the "home stop" changes, which becomes
 *   annoying if i scroll through them fast"*. Not called when [value] is set
 *   by the caller.
 */
@Composable
fun <T> WheelSelect(
    value: T,
    options: List<T>,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    optionLabel: (T) -> String = { it.toString() },
    supporting: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    visibleItems: Int = WheelPickerDefaults.VisibleItems,
    infinite: Boolean = false,
    dragToPick: Boolean = true,
    placement: WheelSelectPlacement = WheelSelectPlacement.OverField,
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    colours: TextFieldColours = TextFieldDefaults.colours(variant),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
    state: SelectState = rememberSelectState(),
    interactionSource: MutableInteractionSource? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    require(visibleItems % 2 == 1) {
        "WheelSelect's visibleItems must be odd so a row can sit where the field was, not $visibleItems"
    }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val host = LocalOverlayHost.current
    val scope = rememberCoroutineScope()
    val drive = remember { WheelDrive() }
    val key = remember { Any() }
    val closeLabel = Theme.strings.closeMenu

    var anchor by remember { mutableStateOf<Rect?>(null) }

    /**
     * Opened by a drag rather than a tap, so it folds away again by itself once
     * it has been left alone. A tap on the field opens it to stay.
     */
    var transient by remember { mutableStateOf(false) }

    /** A finger on the field's drag, or anywhere on the open wheel. */
    var held by remember { mutableStateOf(false) }
    val expanded = state.expanded
    val usable = enabled && options.isNotEmpty()
    val index = options.indexOf(value).coerceAtLeast(0)
    val shown = options.getOrNull(index)?.let(optionLabel).orEmpty()

    fun close() {
        transient = false
        state.collapse()
    }

    // Folds once the wheel has been left alone for the pause: no finger on the
    // field or the wheel, and the drum at rest. Another flick before then starts
    // the pause over, so a long list can take as many as it needs — *"longer
    // lists might require multiple flicks"*. It used to fold a fixed pause after
    // the drag it opened with came to rest, so a second flick could be folded
    // away under the finger; and a flick that caught the wheel still turning
    // took the drum's scroll from that drag, whose rest was then never
    // reported, and the wheel stayed open for good.
    LaunchedEffect(transient, expanded) {
        if (!transient || !expanded) return@LaunchedEffect
        snapshotFlow { held || drive.moving() }.collectLatest { busy ->
            if (busy) return@collectLatest
            // Counted in frames rather than slept: the pause is about what is
            // on screen, and a paused frame clock is a paused screen.
            val since = withFrameNanos { it }
            var now = since
            while (now - since < FoldAfterRestMillis * 1_000_000L) now = withFrameNanos { it }
            close()
        }
    }

    FieldScaffold(
        modifier = modifier,
        enabled = enabled,
        focused = focused || expanded,
        colours = colours,
        metrics = metrics,
        shape = Theme.shapes.field,
        label = label,
        supporting = supporting,
        errorMessage = errorMessage,
        leadingIcon = leadingIcon,
        trailing = {
            Icon(
                imageVector = SystemIcons.ChevronUpDown,
                contentDescription = null,
                tint = if (enabled) colours.label else colours.contentDisabled,
                size = Theme.sizing.iconMedium,
            )
        },
        frameModifier = Modifier
            .anchorBounds { anchor = it }
            .semantics {
                role = Role.DropdownList
                if (label != null) contentDescription = label
                stateDescription = shown
                if (!enabled) disabled()
            }
            .pointerCursor(enabled = usable)
            // Sideways presses and taps are the field's; a drag that is mostly
            // up or down is the wheel's, from its first pixel past deciding.
            .verticalDragOwning(
                enabled = usable && dragToPick,
                interactionSource = null,
                scope = scope,
                claimsOn = DragClaim.Direction,
                onStart = {
                    held = true
                    drive.begin()
                    if (!state.expanded) {
                        transient = true
                        state.expand()
                    }
                },
                onDelta = { dy -> drive.drag(dy) },
                onRelease = { velocity ->
                    held = false
                    drive.release(velocity)
                },
                // A drag taken away rather than let go still has to land on a
                // row; after a release this finds no session and does nothing.
                onEnd = {
                    held = false
                    drive.release(0f)
                },
            )
            .clickable(
                interactionSource = interactions,
                indication = kontourIndication(Theme.shapes.field, pressScale = 1f),
                enabled = usable,
                onClick = {
                    if (state.expanded) close() else {
                        transient = false
                        state.expand()
                    }
                },
            ),
    ) {
        Text(
            text = shown,
            modifier = Modifier.weight(1f),
            style = Theme.typography.bodyMedium,
            colour = if (enabled) colours.content else colours.contentDisabled,
            maxLines = 1,
        )
    }

    val latestAnchor by rememberUpdatedState(anchor)
    val panel by rememberUpdatedState<@Composable () -> Unit> {
        val fullHeight = WheelPickerDefaults.ItemHeight * visibleItems + Theme.spacing.xxs * 2
        // To one side, the drum has as many rows each side of its middle as
        // the open side shows, and the panel shows only that side of it.
        val drumRows = if (placement == WheelSelectPlacement.BelowOrAbove) visibleItems * 2 - 1 else visibleItems
        val drum: @Composable () -> Unit = {
            WheelDrum(
                items = options,
                selectedIndex = index,
                onSelectedIndexChange = { i -> options.getOrNull(i)?.let(onValueChange) },
                itemLabel = optionLabel,
                modifier = Modifier
                    // Watched, not handled: a finger anywhere on the wheel holds
                    // off the fold until it lifts.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            held = true
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { it.pressed })
                            } finally {
                                held = false
                            }
                        }
                    }
                    .padding(Theme.spacing.xxs),
                enabled = enabled,
                contentDescription = label,
                visibleItems = drumRows,
                infinite = infinite,
                onSettled = onValueChangeFinished,
                drive = drive,
                onCentreTap = { close() },
            )
        }
        when (placement) {
            WheelSelectPlacement.OverField -> WheelPanel(
                anchor = { latestAnchor },
                fieldHeight = latestAnchor?.height ?: 0f,
                fullHeight = fullHeight,
                content = drum,
            )
            WheelSelectPlacement.BelowOrAbove -> WheelSidePanel(
                anchor = { latestAnchor },
                open = visibleItems - 1,
                content = drum,
            )
        }
    }

    DisposableEffect(Unit) { onDispose { host.hide(key) } }
    LaunchedEffect(expanded, anchor != null) {
        if (!expanded || anchor == null) {
            host.hide(key)
            return@LaunchedEffect
        }
        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Menu,
                scrim = ScrimStyle.Transparent,
                dismissLabel = closeLabel,
                onDismiss = {
                    transient = false
                    state.collapse()
                },
                trapFocus = true,
                content = { panel() },
            )
        )
    }
}

/**
 * The open wheel, over the field it came out of: its middle row where the
 * field's value is, as wide as the field, and moved only as far as it has to be
 * to stay on screen.
 *
 * **It grows out of the field.** The panel starts the field's height and opens
 * to the drum's, about the middle row, on the host's own progress — so it shuts
 * the same way, back into the field, when the host lets it go. The drum inside
 * is laid out whole and uncovered as the panel grows, rather than squeezed.
 * Under reduced motion it is the drum's full height from the first frame and
 * only fades.
 */
@Composable
private fun WheelPanel(
    anchor: () -> Rect?,
    fieldHeight: Float,
    /** The panel open: what it is clamped by, whatever it is this frame. */
    fullHeight: Dp,
    content: @Composable () -> Unit,
) {
    val progress = LocalOverlayProgress.current
    val reduced = Theme.motion.reduceMotion
    val marginPx = with(LocalDensity.current) { Theme.spacing.sm.roundToPx() }
    val fullPx = with(LocalDensity.current) { fullHeight.roundToPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Layout(
        content = {
            OverlaySurface(
                modifier = Modifier.graphicsLayer {
                    // A quick fade at either end of the growth, and the whole
                    // of the appearance under reduced motion.
                    val p = progress()
                    alpha = if (reduced) p else (p / RevealFade).coerceIn(0f, 1f)
                },
            ) {
                Box(
                    Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val drum = measurable.measure(constraints)
                            val full = drum.height
                            val height = if (reduced) {
                                full
                            } else {
                                lerp(fieldHeight.coerceAtMost(full.toFloat()), full.toFloat(), progress())
                                    .roundToInt()
                            }
                            layout(drum.width, height) {
                                // About the middle: the row over the field stays
                                // put while the rows either side are uncovered.
                                drum.placeRelative(0, (height - full) / 2)
                            }
                        },
                ) { content() }
            }
        },
    ) { measurables, constraints ->
        val at = anchor()
        if (at == null || measurables.isEmpty()) return@Layout layout(0, 0) {}
        val width = at.width.roundToInt().coerceAtLeast(0)
        val panel = measurables.first().measure(
            Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight),
        )
        // Where the panel will stand open, clamped once against its full
        // height, so the growing panel stays centred on one line rather than
        // chasing its own clamp as it grows.
        val room = constraints.maxHeight
        val half = fullPx / 2f
        val lowest = marginPx + half
        val highest = (room - marginPx - half).coerceAtLeast(lowest)
        val centre = at.center.y.coerceIn(lowest, highest)
        // The anchor is measured in the root's coordinates, which do not
        // mirror: its left, counted from the start, is from the right in RTL.
        val start = if (rtl) constraints.maxWidth - at.right else at.left
        layout(constraints.maxWidth, room) {
            panel.placeRelative(start.roundToInt(), (centre - panel.height / 2f).roundToInt())
        }
    }
}

/**
 * The open wheel to one side of the field, as [WheelSelectPlacement.BelowOrAbove]
 * asks: its middle row where the field's value is, [open] rows on the side with
 * room, and half a row on the other, fading out.
 *
 * **The drum is centred on the field, and the panel is not.** The drum has
 * [open] rows each side of its middle, and the panel is a window onto one side
 * of it and a sliver of the other, so the value stays where the field showed
 * it and the wheel turns about it as it does over the field.
 *
 * **The side** is a menu's: below where the open rows fit there, above where
 * they fit there instead, and otherwise whichever has more room — inside the
 * host, less its margin and whatever of the window's insets reach into it.
 * Chosen when the wheel opens and kept until it closes. Where neither side has
 * room the panel moves to fit, and the drum with it.
 *
 * **It grows out of the field**, from the field's own height to the panel's,
 * on the host's progress, so it shuts back into the field the same way; under
 * reduced motion it is whole from the first frame and only fades.
 */
@Composable
private fun WheelSidePanel(
    anchor: () -> Rect?,
    open: Int,
    content: @Composable () -> Unit,
) {
    val host = LocalOverlayHost.current
    val progress = LocalOverlayProgress.current
    val reduced = Theme.motion.reduceMotion
    val density = LocalDensity.current
    val rowPx = with(density) { WheelPickerDefaults.ItemHeight.toPx() }
    val padPx = with(density) { Theme.spacing.xxs.toPx() }
    val marginPx = with(density) { MenuDefaults.ScreenMargin.roundToPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val solid = highContrast()
    val safeArea = LocalOverlaySafeArea.current ?: WindowInsets.allEdges
    val geometry = remember { SidePanelGeometry() }

    Layout(
        content = {
            OverlaySurface(
                modifier = Modifier.graphicsLayer {
                    val p = progress()
                    alpha = if (reduced) p else (p / RevealFade).coerceIn(0f, 1f)
                },
            ) {
                Box(
                    Modifier
                        .clipToBounds()
                        // The sliver of the far side fades out, so the row cut
                        // there reads as more of the wheel rather than as a row
                        // cut off. Not at high contrast, where nothing fades.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (solid) return@drawWithContent
                            val fade = rowPx * PeekShare
                            val below = geometry.below
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Black,
                                    1f to Color.Transparent,
                                    startY = if (below) 0f else size.height,
                                    endY = if (below) fade else size.height - fade,
                                ),
                                blendMode = BlendMode.DstOut,
                            )
                        }
                        .layout { measurable, constraints ->
                            val drum = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                drum.placeRelative(0, geometry.drumTop)
                            }
                        },
                ) { content() }
            }
        },
    ) { measurables, constraints ->
        val rect = anchor()
        if (rect == null || measurables.isEmpty()) return@Layout layout(0, 0) {}
        val at = rect.translate(-host.originInRoot)
        val room = constraints.maxHeight
        val fromRoot = host.edgesFromRoot
        val top = marginPx + (safeArea.getTop(this) - fromRoot.top).coerceAtLeast(0)
        val bottom = room - marginPx - (safeArea.getBottom(this) - fromRoot.bottom).coerceAtLeast(0)

        val centre = at.center.y
        val half = rowPx / 2f
        val far = open * rowPx + padPx
        val near = rowPx * PeekShare
        val roomBelow = bottom - (centre + half)
        val roomAbove = (centre - half) - top
        val below = geometry.side ?: when {
            roomBelow >= far -> true
            roomAbove >= far -> false
            else -> roomBelow >= roomAbove
        }.also { geometry.side = it }
        geometry.below = below

        // Where the panel stands open, and how far it has to move to be inside
        // the room; the drum moves with it, so its middle row stays its own.
        var openTop = centre - half - if (below) near else far
        var openBottom = centre + half + if (below) far else near
        val shift = when {
            openTop < top -> top - openTop
            openBottom > bottom -> (bottom - openBottom).coerceAtLeast(top - openTop)
            else -> 0f
        }
        openTop += shift
        openBottom += shift

        val p = if (reduced) 1f else progress().coerceIn(0f, 1f)
        val panelTop = lerp(at.top, openTop, p)
        val panelBottom = lerp(at.bottom, openBottom, p)
        val height = (panelBottom - panelTop).roundToInt().coerceAtLeast(0)
        val width = at.width.roundToInt().coerceAtLeast(0)
        val drumHeight = rowPx * (open * 2 + 1) + padPx * 2
        geometry.drumTop = (centre + shift - drumHeight / 2f - panelTop).roundToInt()

        val panel = measurables.first().measure(Constraints.fixed(width, height))
        // The anchor is in the root's coordinates, which do not mirror: its
        // left, counted from the start, is from the right in RTL.
        val start = if (rtl) constraints.maxWidth - at.right else at.left
        layout(constraints.maxWidth, room) {
            panel.placeRelative(start.roundToInt(), panelTop.roundToInt())
        }
    }
}

/** Where a [WheelSidePanel] put its drum, and which side it opened on, from its measure to its drawing. */
private class SidePanelGeometry {
    var side: Boolean? = null
    var below: Boolean = true
    var drumTop: Int = 0
}

/** How much of the next row a [WheelSidePanel] shows on its far side. */
private const val PeekShare = 0.5f

/** How much of the growth the panel takes to become opaque. */
private const val RevealFade = 0.35f
