package io.kontour.ui.components.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
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
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.OverlaySurface
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.overlay.anchorBounds
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * How long a wheel opened by a drag stays open once it has come to rest on a
 * row, before it folds back into the field: long enough to read the value it
 * landed on among its neighbours, short enough that the field is back before
 * the next thing is reached for.
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
 * the drum grows out of it above and below, with the value where the field's
 * value was, and turning it changes the value as each row comes to the middle.
 *
 * **Two ways in.** A tap opens the wheel and it stays open: turn it, tap the
 * value in the middle, tap anywhere else, or go back, and it closes. A drag up
 * or down on the closed field turns the wheel *at once*: it opens around the
 * finger, follows it — the same ticks, the same give at either end and the same
 * fling onto a row as a finger on the drum — and folds back into the field once
 * it has come to rest. The second is the one a regular reaches for; the first is
 * the one a newcomer finds.
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

    var anchor by remember { mutableStateOf<Rect?>(null) }

    /**
     * Opened by a drag rather than a tap, so it folds away again by itself once
     * the drum rests. A tap on the field, or on the wheel, makes it stay.
     */
    var transient by remember { mutableStateOf(false) }
    val expanded = state.expanded
    val usable = enabled && options.isNotEmpty()
    val index = options.indexOf(value).coerceAtLeast(0)
    val shown = options.getOrNull(index)?.let(optionLabel).orEmpty()

    fun close() {
        transient = false
        state.collapse()
    }

    SideEffect {
        drive.onSettled = {
            if (transient) {
                scope.launch {
                    // Counted in frames rather than slept: the pause is about
                    // what is on screen, and a paused frame clock is a paused
                    // screen.
                    val since = withFrameNanos { it }
                    var now = since
                    while (now - since < FoldAfterRestMillis * 1_000_000L) now = withFrameNanos { it }
                    if (transient) close()
                }
            }
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
                    drive.begin()
                    if (!state.expanded) {
                        transient = true
                        state.expand()
                    }
                },
                onDelta = { dy -> drive.drag(dy) },
                onRelease = { velocity -> drive.release(velocity) },
                // A drag taken away rather than let go still has to land on a
                // row; after a release this finds no session and does nothing.
                onEnd = { drive.release(0f) },
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
        WheelPanel(
            anchor = { latestAnchor },
            fieldHeight = latestAnchor?.height ?: 0f,
            fullHeight = WheelPickerDefaults.ItemHeight * visibleItems + Theme.spacing.xxs * 2,
        ) {
            WheelDrum(
                items = options,
                selectedIndex = index,
                onSelectedIndexChange = { i -> options.getOrNull(i)?.let(onValueChange) },
                itemLabel = optionLabel,
                modifier = Modifier.padding(Theme.spacing.xxs),
                enabled = enabled,
                contentDescription = label,
                visibleItems = visibleItems,
                infinite = infinite,
                drive = drive,
                onCentreTap = { close() },
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

/** How much of the growth the panel takes to become opaque. */
private const val RevealFade = 0.35f
