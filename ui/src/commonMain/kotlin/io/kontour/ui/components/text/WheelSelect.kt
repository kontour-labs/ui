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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
 * the drum grows out of it, with the value where the field's value was, and
 * turning it changes the value as each row comes to the middle.
 *
 * **Where it opens.** About the field, its rows above and below, where there is
 * room for them. Near the top or the foot of the window there is not, and it
 * opens away from that edge instead: the value stays on the field, the rest of
 * the wheel's rows run to the side with room — below, or above where there is
 * room there instead, as a [Select]'s menu picks its side — and half of the next
 * row shows on the other side, fading out, to say the wheel goes on that way
 * too. Asked for as *"can you make it so the wheel picker is still centred on
 * the actual field, but it just shows more elements above/below (depending on
 * the side)"*, and then *"rather than swapping between beloworabove and
 * overfield, can we make it just intelligently select based on how much room is
 * around it?"*: the wheel used to be pushed along to fit, and near either edge
 * its value was no longer on the field.
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
    variant: TextFieldVariant = TextFieldVariant.Outlined,
    colours: TextFieldColours = TextFieldDefaults.colours(variant),
    metrics: TextFieldMetrics = TextFieldDefaults.metrics(),
    state: SelectState = rememberSelectState(),
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
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

    // Written every frame the field scrolls, and read only where that costs
    // nothing: by the panel's layout, through a lambda, and here as whether
    // there is one at all. It was read in this body — by the effect's key and
    // by `rememberUpdatedState` — so every wheel select on a scrolling page
    // recomposed on every frame of the scroll, open or not. `Select` keeps its
    // anchor out of its body for the same reason; see `WithAnchor`.
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val anchored by remember { derivedStateOf { anchor != null } }

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
            // The keys turn the value from the field, as a select's do on a
            // desktop, open or not: the drum in the panel follows. It takes no
            // focus of its own from here, so a reader's Tab goes on to the next
            // field rather than into a panel that folds itself away.
            .onKeyEvent { event ->
                if (!usable || event.type != KeyEventType.KeyDown || options.isEmpty()) return@onKeyEvent false
                val last = options.lastIndex
                fun step(by: Int) = if (infinite) (index + by).mod(options.size) else (index + by).coerceIn(0, last)
                val target = when (event.key) {
                    Key.DirectionDown -> step(1)
                    Key.DirectionUp -> step(-1)
                    Key.PageDown -> step(visibleItems)
                    Key.PageUp -> step(-visibleItems)
                    Key.MoveHome -> 0
                    Key.MoveEnd -> last
                    Key.Escape -> if (state.expanded) {
                        close()
                        return@onKeyEvent true
                    } else {
                        return@onKeyEvent false
                    }
                    else -> return@onKeyEvent false
                }
                if (target != index) {
                    onValueChange(options[target])
                    onValueChangeFinished?.invoke()
                }
                true
            }
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

    val panel by rememberUpdatedState<@Composable () -> Unit> {
        // The drum has as many rows each side of its middle as the wheel shows
        // in all but one, so it can open to either side; the panel shows the
        // part of it that fits. Rows further than two from the middle are drawn
        // alike, so about the field it looks as a drum of `visibleItems` would.
        val drumRows = visibleItems * 2 - 1
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
                // The field takes the keys; a drum that folds away with its
                // panel would take the focus with it.
                takesFocus = false,
                onCentreTap = { close() },
            )
        }
        WheelPanel(
            anchor = { anchor },
            reach = visibleItems / 2,
            content = drum,
        )
    }

    DisposableEffect(Unit) { onDispose { host.hide(key) } }
    LaunchedEffect(expanded, anchored) {
        if (!expanded || !anchored) {
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
 * The open wheel, out of the field it came from: its middle row where the
 * field's value is, as wide as the field, and as many of its rows either side as
 * there is room for.
 *
 * **The drum is centred on the field, and the panel need not be.** The drum has
 * rows enough to open to either side, and the panel is a window onto it: [reach]
 * rows above and below the middle where there is room for both, which is how it
 * opens over most of a page. Near the top or the foot of the window it opens to
 * the side with room instead, as a menu picks its side — twice [reach] rows
 * there, and half a row on the other, fading out — so the value stays where the
 * field showed it and the wheel turns about it either way. Where neither side
 * has room for that the panel moves to fit, and the drum with it.
 *
 * The room is the host's, less its margin and whatever of the window's insets
 * reach into it. The side is chosen when the wheel opens and kept until it
 * closes.
 *
 * **It grows out of the field**, from the field's own height to the panel's, on
 * the host's progress, so it shuts back into the field the same way; under
 * reduced motion it is whole from the first frame and only fades.
 */
@Composable
private fun WheelPanel(
    anchor: () -> Rect?,
    reach: Int,
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
    val geometry = remember { PanelGeometry() }

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
                        // Opened to one side, the sliver of the other fades out,
                        // so the row cut there reads as more of the wheel rather
                        // than as a row cut off. Not at high contrast, where
                        // nothing fades.
                        //
                        // Offscreen only while there is that sliver to fade.
                        // The layer is a buffer the whole drum renders into
                        // and is composited back from, every frame it scrolls,
                        // and opened about its field or at high contrast
                        // nothing fades. The same mistake `fadingEdges` made.
                        .graphicsLayer {
                            val opening = geometry.opening
                            compositingStrategy = if (solid || opening == null || opening == Opening.About) {
                                CompositingStrategy.Auto
                            } else {
                                CompositingStrategy.Offscreen
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val opening = geometry.opening
                            if (solid || opening == null || opening == Opening.About) return@drawWithContent
                            val fade = rowPx * PeekShare
                            val below = opening == Opening.Below
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
        val about = reach * rowPx + padPx
        val far = reach * 2 * rowPx + padPx
        val near = rowPx * PeekShare
        val roomBelow = bottom - (centre + half)
        val roomAbove = (centre - half) - top
        val opening = geometry.opening ?: when {
            roomAbove >= about && roomBelow >= about -> Opening.About
            roomBelow >= far -> Opening.Below
            roomAbove >= far -> Opening.Above
            roomBelow >= roomAbove -> Opening.Below
            else -> Opening.Above
        }.also { geometry.opening = it }

        // Where the panel stands open, and how far it has to move to be inside
        // the room; the drum moves with it, so its middle row stays its own.
        val (above, below) = when (opening) {
            Opening.About -> about to about
            Opening.Below -> near to far
            Opening.Above -> far to near
        }
        var openTop = centre - half - above
        var openBottom = centre + half + below
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
        val drumHeight = rowPx * (reach * 4 + 1) + padPx * 2
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

/** Which way a [WheelPanel] opened from its field. */
private enum class Opening {
    /** Its rows above and below the field's value. */
    About,

    /** Its rows below the value, and a sliver above. */
    Below,

    /** Its rows above the value, and a sliver below. */
    Above,
}

/** Which way a [WheelPanel] opened, and where it put its drum, from its measure to its drawing. */
private class PanelGeometry {
    /**
     * State, because the drum's layer reads it as well as its drawing: the
     * layer is offscreen only when there is a sliver to fade, and the two have
     * to agree, or the fade's `DstOut` erases through the panel to the page.
     */
    var opening: Opening? by mutableStateOf(null)
    var drumTop: Int = 0
}

/** How much of the next row a [WheelPanel] opened to one side shows on the other. */
private const val PeekShare = 0.5f

/** How much of the growth the panel takes to become opaque. */
private const val RevealFade = 0.35f
