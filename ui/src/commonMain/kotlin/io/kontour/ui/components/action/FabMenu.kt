package io.kontour.ui.components.action

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.overlay.anchorBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** How a [FabMenu]'s items arrange themselves around the button they came out of. */
enum class FabMenuLayout {
    /**
     * A column above the FAB, nearest item first. The default, and the only one
     * that shows labels by default — a column has room for them.
     */
    Vertical,

    /** A row beside the FAB, running away from the nearest edge. */
    Horizontal,

    /**
     * An arc, from straight up to straight out along the nearest wall.
     *
     * The showiest of the three and the one with the least room: an arc puts
     * items at diagonals, where a label has nowhere to go that does not overlap
     * the item beside it. Icons only, unless a caller insists.
     */
    Fan,
}

/** One action in a [FabMenu]. Built through [FabMenuScope.item]. */
internal class FabMenuItem(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/** Declares a [FabMenu]'s actions. */
@LayoutScopeMarker
@Stable
class FabMenuScope internal constructor() {

    internal val items = mutableListOf<FabMenuItem>()

    /**
     * One action.
     *
     * [label] is not optional, and that is the point: a bare icon floating over
     * a map is the least identifiable control in any interface. It names the
     * button for a screen reader whichever layout is in use, and it is drawn
     * beside the button wherever there is room for it.
     */
    fun item(
        icon: ImageVector,
        label: String,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        items += FabMenuItem(icon, label, enabled, onClick)
    }
}

/** Sizing and timing shared by every [FabMenu]. */
object FabMenuDefaults {

    /** Between the FAB and the first item, and between items. */
    val Gap: Dp = 12.dp

    /** Between an item and its label. */
    val LabelGap: Dp = 12.dp

    /** How close an item may come to the edge of the window. */
    val ScreenMargin: Dp = 16.dp

    /**
     * How far apart the items start moving, in milliseconds.
     *
     * The whole reason a fan of buttons reads as one thing unfolding rather than
     * five things appearing: each item leaves a beat after the one before it, so
     * the eye follows a wave outward instead of being handed a finished menu.
     *
     * Small, because the stagger accumulates — six items at 40ms is a quarter of
     * a second before the last one has moved, and by then the first has settled
     * and the menu feels slow. At 28ms a six-item menu is fully out in about
     * 300ms including the spring.
     *
     * Zero under `reduceMotion`, where the items simply fade: a sequence is
     * still movement, and staggering it draws the eye across the screen exactly
     * as the preference asks it not to.
     */
    const val StaggerMillis: Long = 28L

    /** How small an item starts before springing out to full size. */
    const val FromScale: Float = 0.55f

    /**
     * The hairline round an item and its label.
     *
     * A FAB menu's items are light where the anchor is near-black, and in the
     * light scheme `background`, `surface` and `surfaceRaised` are all the same
     * white — so an item without this is a white circle on a white page,
     * separated from it by a soft shadow and nothing else. Over a map that is
     * enough. Over a list, or a settings page, it is three shadows.
     *
     * The same hairline `OverlaySurface` puts round every menu and popover, for
     * the reason stated there: it gives a light panel definition against a light
     * ground. `contrastEdge()` is not the right tool — that is the high-contrast
     * *tier's* answer, and this is wrong at every tier.
     */
    @Composable
    @ReadOnlyComposable
    fun itemBorder(): BorderStroke =
        BorderStroke(Theme.sizing.borderWidth, Theme.colours.outlineSubtle)
}

/**
 * A floating action button that opens into several.
 *
 * ```
 * var open by remember { mutableStateOf(false) }
 *
 * FabMenu(
 *     expanded = open,
 *     onExpandedChange = { open = it },
 *     icon = SystemIcons.Plus,
 *     contentDescription = "Add",
 * ) {
 *     item(Tabler.Outline.Star, "Save stop") { save() }
 *     item(Tabler.Outline.Bell, "Set alert") { alert() }
 *     item(Tabler.Outline.Share, "Share") { share() }
 * }
 * ```
 *
 * The anchor **is** a [FloatingActionButton] — same [FabSize], same shape, same
 * press scale — so a screen that already has a FAB gains a menu by changing the
 * call rather than by swapping the component out for a lookalike.
 *
 * ### Where the items render
 *
 * In the [io.kontour.ui.overlay.OverlayHost], anchored to the FAB, for the same
 * reason a menu does: a FAB sits in a corner, and items expanding out of a
 * corner leave whatever box put it there. Rendering them inline would clip them
 * against the first parent with a bound — which, for a FAB, is usually the very
 * next one out.
 *
 * The FAB itself stays exactly where the caller put it. It is *behind* the
 * scrim while the menu is open, which is what makes tapping it close the menu
 * without a second handler — the same bargain
 * [io.kontour.ui.overlay.DropdownMenu] strikes with its trigger.
 *
 * ### Three layouts
 *
 * [FabMenuLayout.Vertical] is the default and the one to reach for. All three
 * pick their direction from where the FAB is in the window rather than from a
 * parameter: a FAB in the bottom-right opens up and to the left, one in the
 * top-left opens down and to the right, and nothing has to be told which corner
 * it is in.
 *
 * @param expanded Whether the menu is open. Hoisted, so the caller can close it
 *   from an item's own `onClick` — which every item should do.
 * @param icon The resting icon. Rotates 45° as the menu opens, which turns a
 *   plus into a cross; pass [expandedIcon] when the resting icon is something a
 *   rotation does not usefully transform.
 * @param expandedIcon Shown instead of [icon] while open. Suppresses the
 *   rotation, since a deliberate second icon arriving on its side is a bug
 *   rather than a flourish.
 * @param itemSize Items are one size down from the anchor by default. A menu of
 *   buttons the same size as the button they came out of has no anchor in it.
 * @param itemBorder The hairline that keeps a light item off a light page — see
 *   [FabMenuDefaults.itemBorder]. Null on a menu that only ever floats over
 *   photography or a map, where the shadow already does the work.
 * @param showLabels Drawn beside each item. On for [FabMenuLayout.Vertical],
 *   off for the other two, where the items sit at angles that leave a label
 *   nowhere to go. Every item is still *named* for a screen reader either way.
 * @param scrim [ScrimStyle.Transparent] by default — taps outside close the menu
 *   but nothing dims, so the FAB does not grey out under its own menu. Pass
 *   [ScrimStyle.Dimmed] where the actions deserve the whole screen's attention.
 */
@Composable
fun FabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: FabSize = FabSize.Medium,
    itemSize: FabSize = FabSize.Small,
    layout: FabMenuLayout = FabMenuLayout.Vertical,
    showLabels: Boolean = layout == FabMenuLayout.Vertical,
    expandedIcon: ImageVector? = null,
    expandedContentDescription: String = Theme.strings.close,
    shape: Shape = Theme.shapes.control,
    containerColour: Color = Theme.colours.primary,
    contentColour: Color = Theme.colours.onPrimary,
    itemContainerColour: Color = Theme.colours.surfaceRaised,
    itemContentColour: Color = Theme.colours.content,
    itemBorder: BorderStroke? = FabMenuDefaults.itemBorder(),
    scrim: ScrimStyle = ScrimStyle.Transparent,
    key: Any = remember { Any() },
    content: FabMenuScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    val motion = Theme.motion
    // A screen reader reads this off the scrim, so it is user-visible English
    // and belongs in `Theme.strings` like the rest of it — `DropdownMenu` still
    // welds its own in, which is a separate thing to go and fix.
    val dismissLabel = Theme.strings.dismiss
    val items = FabMenuScope().apply(content).items

    // Nothing to open onto is not an error — a menu whose actions are all
    // filtered out this frame is an ordinary state — but it must not be a FAB
    // that lies about having more behind it.
    val opens = items.isNotEmpty()
    val open = expanded && opens

    var anchor by remember { mutableStateOf<Rect?>(null) }

    /** How far the *whole* menu is out, for the scrim and the icon. */
    val openness = remember { Animatable(0f) }

    /**
     * How far each item is out, one spring each.
     *
     * Hoisted here rather than held inside the overlay entry so the exit can
     * finish: the entry is torn down when the host is done with it, and an
     * animation living inside it is cancelled mid-flight. This is the same
     * reason `BottomSheet` keeps its `SheetState` outside.
     */
    val fractions = remember(items.size) { List(items.size) { Animatable(0f) } }

    val latestItems by rememberUpdatedState(items)
    val latestLayout by rememberUpdatedState(layout)
    val latestShowLabels by rememberUpdatedState(showLabels)
    val latestItemSize by rememberUpdatedState(itemSize)
    val latestAnchorSize by rememberUpdatedState(size)
    val latestShape by rememberUpdatedState(shape)
    val latestItemContainer by rememberUpdatedState(itemContainerColour)
    val latestItemContent by rememberUpdatedState(itemContentColour)
    val latestItemBorder by rememberUpdatedState(itemBorder)
    val latestAnchor by rememberUpdatedState(anchor)
    val dismiss by rememberUpdatedState(onExpandedChange)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(open, items.size, scrim) {
        val stagger = if (motion.reduceMotion) 0L else FabMenuDefaults.StaggerMillis
        if (open) {
            host.show(
                OverlayEntry(
                    key = key,
                    layer = OverlayLayer.Menu,
                    scrim = scrim,
                    dismissLabel = dismissLabel,
                    // The items spring out and fold back on their own clock, so
                    // the entry stays until they have landed and the scrim
                    // follows them rather than the host's fade.
                    managesOwnExit = true,
                    visibility = { openness.value },
                    onDismiss = { dismiss(false) },
                    content = {
                        FabMenuItems(
                            anchorInRoot = { latestAnchor },
                            items = latestItems,
                            fractions = fractions,
                            layout = latestLayout,
                            showLabels = latestShowLabels,
                            itemSize = latestItemSize,
                            anchorSize = latestAnchorSize,
                            shape = latestShape,
                            containerColour = latestItemContainer,
                            contentColour = latestItemContent,
                            border = latestItemBorder,
                            onDismissRequest = { dismiss(false) },
                        )
                    },
                )
            )
            launch { openness.animateTo(1f, motion.tweenFast()) }
            // Nearest first, so the wave runs outward from the button it came
            // out of.
            fractions.forEachIndexed { index, fraction ->
                launch {
                    delay(index * stagger)
                    // `springDefault`, not `springBouncy`. Overshoot is a
                    // proportion of the distance travelled, so the same spring
                    // that reads as lively on a chip's 6dp tick reads as a
                    // wobble on an item thrown 120dp down the screen — worst in
                    // the vertical and horizontal layouts, where the last item
                    // travels furthest. The stagger already carries the sense of
                    // the menu unfolding; the bounce was a second opinion about
                    // the same thing.
                    fraction.animateTo(1f, motion.springOrTween(motion.springDefault))
                }
            }
        } else {
            val last = fractions.lastIndex
            val folding = fractions.mapIndexed { index, fraction ->
                launch {
                    // Furthest first on the way back: the menu gathers itself
                    // into the FAB rather than peeling away from it.
                    delay((last - index).coerceAtLeast(0) * stagger)
                    fraction.animateTo(0f, motion.springOrTween(motion.springSnappy))
                }
            }
            (folding + launch { openness.animateTo(0f, motion.tweenExit()) }).joinAll()
            host.hide(key)
        }
    }

    // A lambda, so the animation is read at draw rather than in composition — a
    // 45° sweep redraws the glyph instead of recomposing the button forty times
    // on its way round.
    val rotation = { if (expandedIcon == null) 45f * openness.value else 0f }

    // Swapped on the tap rather than partway through the animation. Reading
    // `openness` here would put an animation in the *composition* phase, which
    // is the thing the lambda above exists to avoid — and a swap at the halfway
    // mark is not better than one at the moment the user asked for it.
    val showingClose = expandedIcon != null && open

    FloatingActionButton(
        onClick = { onExpandedChange(!expanded) },
        contentDescription = if (open) expandedContentDescription else contentDescription,
        modifier = modifier.anchorBounds { anchor = it },
        enabled = enabled && opens,
        size = size,
        shape = shape,
        containerColour = containerColour,
        contentColour = contentColour,
    ) {
        Icon(
            imageVector = if (showingClose) expandedIcon else icon,
            contentDescription = null,
            // The turn is a draw-time read of the animation, so a 45° sweep
            // redraws the glyph rather than recomposing the button forty times
            // on its way round.
            modifier = Modifier.graphicsLayer { rotationZ = rotation() },
            size = size.icon,
        )
    }
}

/**
 * The items, laid out around the anchor and animated out of it.
 *
 * One [Layout] rather than a stack of `offset` modifiers because every position
 * here depends on two things a modifier cannot see: where the anchor is in the
 * window, and how much room is left on each side of it. The direction the menu
 * opens is a *measured* fact, not a parameter.
 *
 * Children are interleaved — label, button, label, button — so each item's two
 * parts can be placed independently. A label has to go on whichever side of its
 * button has room, and that is not known until the container has been measured,
 * by which time composition is over.
 */
@Composable
private fun FabMenuItems(
    anchorInRoot: () -> Rect?,
    items: List<FabMenuItem>,
    fractions: List<Animatable<Float, AnimationVector1D>>,
    layout: FabMenuLayout,
    showLabels: Boolean,
    itemSize: FabSize,
    anchorSize: FabSize,
    shape: Shape,
    containerColour: Color,
    contentColour: Color,
    border: BorderStroke?,
    onDismissRequest: () -> Unit,
) {
    val host = LocalOverlayHost.current
    val density = LocalDensity.current

    // Room for each item's fade to composite in without cutting its own shadow.
    //
    // `alpha < 1` composites offscreen into a buffer sized to the layer's own
    // rectangle, and every item here carries `alpha` — the buttons carry `scale`
    // with it. A layer wrapped tight around a 40dp circle whose shadow reaches
    // 24dp further out draws a hard-edged grey square, widest at the smallest
    // scale, on every frame of the menu opening. Exactly the fault
    // `overlayAppearance` documents and fixes for menus and dialogs.
    //
    // One number for buttons and labels alike, taken from the larger of the two
    // shadows: padding a label by more than its own shadow needs costs nothing,
    // and one constant is one thing for `placeLabel` to subtract back out.
    val itemRoom = Theme.elevation.medium.bleed
    val roomPx = with(density) { itemRoom.toPx() }

    val gapPx = with(density) { FabMenuDefaults.Gap.toPx() }
    val labelGapPx = with(density) { FabMenuDefaults.LabelGap.toPx() }
    val marginPx = with(density) { FabMenuDefaults.ScreenMargin.toPx() }
    // What an item actually occupies, which on Android is the 48dp target
    // `FloatingActionButton` reserves rather than the 40dp circle it draws. Space
    // them by the circle and two adjacent items overlap where it counts.
    val footprintPx = with(density) {
        max(itemSize.container.toPx(), Theme.sizing.minTouchTarget.toPx())
    }

    Layout(
        content = {
            items.forEachIndexed { index, item ->
                // The label is always emitted, even when it is not drawn, so the
                // interleaving holds and the index arithmetic below stays a fact
                // about the list rather than about this frame's settings.
                if (showLabels) {
                    Box(
                        Modifier
                            .graphicsLayer { alpha = fractions[index].value }
                            .padding(itemRoom)
                    ) {
                    Surface(
                        modifier = Modifier
                            // Named by the button beside it. Two nodes carrying
                            // the same words is the "label next to a control"
                            // fault the contract suite exists to catch.
                            .clearAndSetSemantics { },
                        shape = Theme.shapes.control,
                        colour = Theme.colours.surfaceRaised,
                        contentColour = Theme.colours.content,
                        // A label is a light chip on a light page too, and it
                        // has no icon inside it to give away where its edges are.
                        border = border,
                        shadow = Theme.elevation.low,
                    ) {
                        ProvideTextStyle(Theme.typography.labelMedium) {
                            Text(
                                text = item.label,
                                maxLines = 1,
                                modifier = Modifier.padding(
                                    horizontal = Theme.spacing.sm,
                                    vertical = Theme.spacing.xs,
                                ),
                            )
                        }
                    }
                    }
                } else {
                    Box(Modifier)
                }

                Box(
                    Modifier
                        .graphicsLayer {
                            val fraction = fractions[index].value
                            alpha = fraction
                            val scale = FabMenuDefaults.FromScale +
                                (1f - FabMenuDefaults.FromScale) * fraction
                            scaleX = scale
                            scaleY = scale
                        }
                        .padding(itemRoom)
                ) {
                FloatingActionButton(
                    icon = item.icon,
                    contentDescription = item.label,
                    onClick = {
                        item.onClick()
                        onDismissRequest()
                    },
                    enabled = item.enabled,
                    size = itemSize,
                    shape = shape,
                    containerColour = containerColour,
                    contentColour = contentColour,
                    border = border,
                )
                }
            }
        },
        // The items come out from *behind* the anchor.
        //
        // They are in the overlay and the button is in the page, so the overlay
        // is above it by construction and the first frames of an opening menu
        // were small circles sliding across the FAB's own face. Nothing about
        // the geometry said "these came out of that button" — they were in front
        // of it the whole way.
        //
        // Cutting the anchor's shape out of this layer fixes the read without a
        // second copy of the anchor to keep in sync: the real button shows
        // through the hole, still rotating its own icon, and an item is hidden
        // until it clears the edge. Difference-clipped rather than z-ordered
        // because the two are in different layers and no z-index reaches across.
        modifier = Modifier.drawWithCache {
            val hole = Path().apply {
                val rect = (anchorInRoot() ?: Rect.Zero).translate(-host.originInRoot)
                if (!rect.isEmpty) {
                    addOutline(shape.createOutline(rect.size, layoutDirection, this@drawWithCache))
                    translate(Offset(rect.left, rect.top))
                }
            }
            onDrawWithContent {
                if (hole.isEmpty) {
                    drawContent()
                } else {
                    clipPath(hole, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
                }
            }
        },
    ) { measurables, constraints ->
        // The host is `fillMaxSize`, so this is the window. Guarded anyway,
        // because laying out at `Constraints.Infinity` throws rather than
        // producing something odd — and every direction below is decided by
        // comparing the anchor against these numbers.
        val container = IntSize(
            if (constraints.hasBoundedWidth) constraints.maxWidth else 0,
            if (constraints.hasBoundedHeight) constraints.maxHeight else 0,
        )
        val loose = Constraints(maxWidth = container.width, maxHeight = container.height)
        val placeables = measurables.map { it.measure(loose) }

        val anchor = (anchorInRoot() ?: Rect.Zero).translate(-host.originInRoot)
        val centre = anchor.center
        // The anchor's own measured radius, not the token: an
        // `ExtendedFloatingActionButton` is a pill, and the first item has to
        // clear whichever half of it the menu is heading over.
        val anchorRadius = max(anchor.width, anchor.height) / 2f

        val geometry = fabMenuGeometry(
            layout = layout,
            count = items.size,
            centre = centre,
            container = container,
            anchorRadius = if (anchor.isEmpty) {
                with(density) { anchorSize.container.toPx() } / 2f
            } else {
                anchorRadius
            },
            footprint = footprintPx,
            gap = gapPx,
            margin = marginPx,
        )

        layout(container.width, container.height) {
            items.indices.forEach { index ->
                val label = placeables[index * 2]
                val button = placeables[index * 2 + 1]
                val fraction = fractions[index].value

                // Out of the FAB and back into it. The travel is interpolated
                // here rather than in a layer so the item's *hit area* travels
                // with it — an item you can see at one end of the screen and tap
                // at the other is the classic animated-menu bug.
                val target = geometry.points[index]
                val point = Offset(
                    x = centre.x + (target.x - centre.x) * fraction,
                    y = centre.y + (target.y - centre.y) * fraction,
                )

                button.place(
                    x = (point.x - button.width / 2f).toInt(),
                    y = (point.y - button.height / 2f).toInt(),
                )

                if (label.width > 0 && label.height > 0) {
                    placeLabel(
                        label, button, point, geometry.labelOnLeft,
                        labelGapPx, container, marginPx, roomPx,
                    )
                }
            }
        }
    }
}

/** Beside its button, on the side the menu decided has room. */
private fun Placeable.PlacementScope.placeLabel(
    label: Placeable,
    button: Placeable,
    point: Offset,
    onLeft: Boolean,
    gap: Float,
    container: IntSize,
    margin: Float,
    room: Float,
) {
    // Both placeables are `room` larger on every side than the thing drawn
    // inside them — see `itemRoom`, which buys the fade a buffer big enough to
    // hold each item's shadow. The gap is between the *drawn* edges, so the
    // padding comes back off here; leave it in and every label sits 24dp
    // further from its button than it is supposed to.
    val edge = button.width / 2f - room + gap
    val x = if (onLeft) point.x - edge - label.width + room else point.x + edge - room
    label.place(
        x = x.coerceIn(margin, (container.width - margin - label.width).coerceAtLeast(margin)).toInt(),
        y = (point.y - label.height / 2f).toInt(),
    )
}

/** Where each item ends up, and which side its label goes on. */
private class FabMenuGeometry(
    val points: List<Offset>,
    val labelOnLeft: Boolean,
)

/**
 * The arrangement, decided from where the anchor sits in the window.
 *
 * Every direction here is chosen by measurement rather than declared: a FAB in
 * the bottom-right corner opens up and to the left because that is where the
 * room is, and the caller never has to say which corner it put the button in.
 * The alternative — a `direction` parameter — is a second place for the truth to
 * live, and it is wrong the first time a phone rotates.
 *
 * ### Running out of room compresses; it does not stack
 *
 * The first version clamped each item to the window independently, which is the
 * obvious thing and the wrong one: once the run is longer than the space, every
 * item past the wall clamps to *the same point* and the tail of the menu becomes
 * one pile of buttons, each hiding the one before it. Three actions rendered as
 * two, and the third could not be tapped at all.
 *
 * So the spacing is derived from the room rather than checked against it. The
 * first item still has to clear the anchor, and whatever is left over is divided
 * between the rest — at full spacing when it fits, tighter when it does not.
 * Items may end up touching on a short window; they never end up on top of each
 * other, and all of them stay on screen.
 */
private fun fabMenuGeometry(
    layout: FabMenuLayout,
    count: Int,
    centre: Offset,
    container: IntSize,
    anchorRadius: Float,
    footprint: Float,
    gap: Float,
    margin: Float,
): FabMenuGeometry {
    val half = footprint / 2f
    val clearance = anchorRadius + gap + half

    // How far the menu may travel toward each wall before an item's own edge
    // would cross the margin.
    val up = centre.y - margin
    val down = container.height - margin - centre.y
    val left = centre.x - margin
    val right = container.width - margin - centre.x

    val upward = up >= down
    val leftward = left >= right
    val alongY = if (upward) up else down
    val alongX = if (leftward) left else right

    /** Where the first item goes and how far apart the rest are, in [room]. */
    fun spacing(room: Float): Pair<Float, Float> {
        val reach = (room - half).coerceAtLeast(0f)
        val first = minOf(clearance, reach)
        val step = if (count > 1) {
            minOf(footprint + gap, (reach - first) / (count - 1))
        } else {
            0f
        }
        return first to step
    }

    val points = when (layout) {
        FabMenuLayout.Vertical -> {
            val (first, step) = spacing(alongY)
            val sign = if (upward) -1f else 1f
            List(count) { index -> Offset(centre.x, centre.y + sign * (first + index * step)) }
        }

        FabMenuLayout.Horizontal -> {
            val (first, step) = spacing(alongX)
            val sign = if (leftward) -1f else 1f
            List(count) { index -> Offset(centre.x + sign * (first + index * step), centre.y) }
        }

        FabMenuLayout.Fan -> {
            // From straight along the vertical wall to straight along the
            // horizontal one — a quarter turn, which for a corner FAB is exactly
            // the quadrant with screen in it.
            val fromAngle = if (upward) -PI / 2 else PI / 2
            val toAngle = when {
                !leftward -> 0.0
                upward -> -PI
                else -> PI
            }
            // Wide enough that neighbours do not touch: the arc between two
            // items is `radius * stepAngle`, so the radius the spacing wants is
            // that rearranged — which is why a six-item fan is visibly larger
            // than a three-item one instead of being six items in a heap. Capped
            // by the room, since both ends of the arc land against a wall.
            val stepAngle = if (count > 1) (toAngle - fromAngle) / (count - 1) else 0.0
            val wanted = if (count > 1) {
                (footprint + gap) / kotlin.math.abs(stepAngle).toFloat()
            } else {
                0f
            }
            val room = minOf(alongY, alongX) - half
            val radius = maxOf(wanted, clearance).coerceAtMost(room.coerceAtLeast(0f))
            List(count) { index ->
                val t = if (count > 1) index.toFloat() / (count - 1) else 0.5f
                val angle = fromAngle + (toAngle - fromAngle) * t
                Offset(
                    centre.x + (cos(angle) * radius).toFloat(),
                    centre.y + (sin(angle) * radius).toFloat(),
                )
            }
        }
    }

    return FabMenuGeometry(points = points, labelOnLeft = leftward)
}
