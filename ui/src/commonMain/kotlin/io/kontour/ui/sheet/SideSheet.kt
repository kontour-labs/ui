package io.kontour.ui.sheet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.ArrowRight
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Surface
import io.kontour.ui.overlay.BackdropStyle
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.LocalOverlayProgress
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.mirrorHorizontally
import kotlin.math.roundToInt

/** Which edge a [SideSheet] comes in from. Follows the layout direction. */
enum class SheetSide { Start, End }

/**
 * A sheet that slides in from the side and takes over the screen until it is dealt
 * with: a scrim behind it, focus held inside it, dismissed by a tap outside, by back,
 * or by its own close button.
 *
 * ```kotlin
 * ModalSideSheet(visible = filtersOpen, onDismissRequest = { filtersOpen = false }) {
 *     SheetHeader { +"Filters" }
 *     …
 * }
 * ```
 *
 * The wide-screen counterpart of [ModalBottomSheet]. On a phone a bottom sheet
 * is right because the content is near the thumb and the screen is taller than
 * it is wide; on a tablet or desktop the same sheet becomes a short letterbox
 * across a very wide window, and a side sheet uses the shape of the screen
 * instead.
 *
 * Slides rather than dragging through detents. A side sheet has one useful
 * position — open — and a horizontal drag on a wide screen usually means
 * something else was intended. Dismissal is the scrim, the close button or a
 * back gesture.
 *
 * Flush to its side, the top and the bottom, at one width. It could float clear
 * of its edges and be dragged out to the whole window for a while, and both were
 * taken out again: asked for, tried, and not wanted.
 *
 * @param side Which edge it enters from, following the layout direction.
 *   [SheetSide.End] is the default: the sheet is supplementary to the content,
 *   and supplementary things belong on the trailing side.
 */
@Composable
fun ModalSideSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    side: SheetSide = SheetSide.End,
    /**
     * 480dp, not 400.
     *
     * A side sheet holds the same content a bottom sheet does — a list of
     * departures, a form — laid out in a column with padding on both sides. At
     * 400 that column was 400 − 32 = 368dp wide, which is narrower than the
     * phone the same content renders fine on, and it read as a cramped strip
     * rather than a panel.
     */
    width: Dp = SideSheetDefaults.Width,
    /**
     * The corners facing the content are rounded and the ones against the window's
     * edge are square. Mirrored for a sheet on the physical left.
     */
    shape: CornerBasedShape = Theme.shapes.sideSheet,
    containerColour: Color = Theme.colours.surfaceRaised,
    contentColour: Color = Theme.colours.content,
    scrim: ScrimStyle = ScrimStyle.Dimmed,
    dismissible: Boolean = true,
    dismissLabel: String = Theme.strings.close,
    /**
     * Shown as a back arrow at the sheet's leading edge, above the content.
     *
     * For a sheet that was pushed onto from another one — a stop, then a route
     * within it. `null` for a sheet with nothing behind it, where the scrim and
     * a close button in the content are the way out.
     *
     * The arrow follows the reading direction rather than the sheet's side: back
     * means back, and a start-side sheet does not point the other way.
     */
    onBack: (() -> Unit)? = null,
    backLabel: String = Theme.strings.back,
    paneTitle: String? = null,
    /**
     * What the sheet's *content* keeps clear of. Every edge including the
     * keyboard: a side sheet is full height, so it meets the status bar and the
     * gesture bar at once, and it holds forms as often as a bottom sheet does.
     */
    windowInsets: WindowInsets = WindowInsets.allEdges,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    val key = remember { Any() }
    val dismiss by rememberUpdatedState(onDismissRequest)
    val body by rememberUpdatedState(content)
    // Read live, not captured: the effect is keyed on `visible`, `side`, `width`
    // and `scrim`, so anything else the entry closes over is frozen at the moment
    // the sheet appeared.
    val latestModifier by rememberUpdatedState(modifier)
    val latestShape by rememberUpdatedState(shape)
    val latestContainerColour by rememberUpdatedState(containerColour)
    val latestContentColour by rememberUpdatedState(contentColour)
    val latestPaneTitle by rememberUpdatedState(paneTitle)
    val latestOnBack by rememberUpdatedState(onBack)
    val latestBackLabel by rememberUpdatedState(backLabel)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(visible, side, width, scrim) {
        if (!visible) {
            host.hide(key)
            return@LaunchedEffect
        }

        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Sheet,
                scrim = scrim,
                // Derived, and only in the one direction that is sound.
                // `ScrimStyle.None` means this overlay lets pointer events
                // through to what is behind it, and something you can still
                // click is something you must still be able to focus. The other
                // two scrims say nothing either way — `Menu` and the selection
                // toolbar are both `Transparent` and want opposite answers — so
                // anything with a scrim traps, deliberately.
                //
                // A scrimless side sheet is a panel that stays open beside the
                // content — an inspector, a filter rail — and trapping focus in
                // one would lock the keyboard out of the thing it is inspecting.
                trapFocus = scrim != ScrimStyle.None,
                // Same reason as a bottom sheet, and the same answer: it takes
                // an edge of the screen rather than floating over the middle of
                // it, so the presenting content recedes — and it does not blur,
                // because the blur costs 7.3x the frame for every frame the
                // sheet is open. Two sheets that receded differently would read
                // as two components.
                backdrop = if (scrim == ScrimStyle.Dimmed) {
                    BackdropStyle.Scale
                } else {
                    BackdropStyle.None
                },
                dismissOnOutside = dismissible,
                dismissLabel = dismissLabel,
                onDismiss = { dismiss() },
                content = {
                    SideSheetPanel(
                        modifier = latestModifier,
                        side = side,
                        width = width,
                        shape = latestShape,
                        containerColour = latestContainerColour,
                        contentColour = latestContentColour,
                        paneTitle = latestPaneTitle,
                        onBack = latestOnBack,
                        backLabel = latestBackLabel,
                        progress = LocalOverlayProgress.current,
                        // Captured as an object, not a measurement: the modifier
                        // reads the live inset at layout time, so the sheet still
                        // lifts when the keyboard opens after it was shown.
                        windowInsets = windowInsets,
                        content = body,
                    )
                },
            )
        )
    }
}

/**
 * A panel from the side that shares the screen with the page beside it.
 *
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     DepartureList()
 *     SideSheet(visible = filtersOpen, paneTitle = "Filters") {
 *         SheetHeader(onClose = { filtersOpen = false }) { +"Filters" }
 *         …
 *     }
 * }
 * ```
 *
 * The side sheet's counterpart of [BottomSheet], where [ModalSideSheet] is the
 * counterpart of [ModalBottomSheet]. Nothing behind it is dimmed, blocked or taken
 * out of the keyboard's reach: the list beside a filter rail is still the list, and
 * a change to a filter is seen in it as it is made. For a sheet that owns the
 * screen until it is answered, use [ModalSideSheet].
 *
 * **It lives in your layout**, not in the [io.kontour.ui.overlay.OverlayHost]: put
 * it in a `Box` over the content it sits beside, and it fills that box and places
 * itself against the side it belongs to. Where it is not, the page underneath gets
 * every touch and every click.
 *
 * **The app owns [visible].** There is no scrim to tap and no back gesture to
 * catch, so nothing here asks to be closed; give the sheet's header an `onClose`
 * that sets `visible` to false. It slides in and out on its own spring, and is not
 * composed at all once it has slid away.
 *
 * Everything else is [ModalSideSheet]'s: flush to its side, the top and the
 * bottom, at one width.
 */
@Composable
fun SideSheet(
    visible: Boolean,
    modifier: Modifier = Modifier,
    side: SheetSide = SheetSide.End,
    /** See [ModalSideSheet]. */
    width: Dp = SideSheetDefaults.Width,
    /** See [ModalSideSheet]. */
    shape: CornerBasedShape = Theme.shapes.sideSheet,
    containerColour: Color = Theme.colours.surfaceRaised,
    contentColour: Color = Theme.colours.content,
    /** See [ModalSideSheet]. */
    onBack: (() -> Unit)? = null,
    backLabel: String = Theme.strings.back,
    paneTitle: String? = null,
    /** See [ModalSideSheet]. */
    windowInsets: WindowInsets = WindowInsets.allEdges,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = Theme.motion
    // Its own motion rather than the overlay host's, since it is not in the host:
    // the same spring a sheet settles on, and a tween under reduced motion.
    val shown by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springGentle),
        label = "sideSheet",
    )
    // Read through a lambda, in placement, so sliding re-places the sheet without
    // recomposing it.
    val progress = rememberUpdatedState(shown)

    // Not composed once it has slid away: a sheet that is not there should not be
    // in the assistive tree, nor holding its content's state against the next time.
    if (!visible && shown <= 0f) return

    SideSheetPanel(
        modifier = modifier,
        side = side,
        width = width,
        shape = shape,
        containerColour = containerColour,
        contentColour = contentColour,
        paneTitle = paneTitle,
        onBack = onBack,
        backLabel = backLabel,
        windowInsets = windowInsets,
        progress = { progress.value },
        content = content,
    )
}

/** What [SideSheet] and [ModalSideSheet] take by default. */
object SideSheetDefaults {
    /** Wide enough for a column of content with padding on both sides. */
    val Width: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.sideSheetWidth
}

@Composable
private fun SideSheetPanel(
    modifier: Modifier,
    side: SheetSide,
    width: Dp,
    shape: CornerBasedShape,
    containerColour: Color,
    contentColour: Color,
    paneTitle: String?,
    onBack: (() -> Unit)?,
    backLabel: String,
    windowInsets: WindowInsets,
    // How far in the sheet has slid, 0 to 1. Driven from outside in both
    // directions — by the overlay host for a modal sheet, by the sheet's own
    // animation for one that is not — because a panel that set its own `appeared`
    // flag on first composition could only ever run 0 -> 1, which is why nothing in
    // the library animated *out*.
    progress: () -> Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Physical edge, once the layout direction has been applied.
    val fromRight = (side == SheetSide.End) != isRtl
    val alignment = if (fromRight) Alignment.CenterEnd else Alignment.CenterStart
    val travel = with(density) { width.toPx() }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(alignment)
                .width(width)
                .fillMaxHeight()
                // Read in placement, so opening and closing re-place the sheet
                // without measuring or recomposing it.
                .offset {
                    val hidden = travel * (1f - progress().coerceIn(0f, 1f))
                    IntOffset(x = (if (fromRight) hidden else -hidden).roundToInt(), y = 0)
                }
                .then(modifier)
                .semantics {
                    isTraversalGroup = true
                    if (paneTitle != null) this.paneTitle = paneTitle
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                // Mirrored for a start-side sheet, so the rounded edge is always
                // the one facing the content rather than the window edge.
                shape = if (fromRight) shape else shape.mirrorHorizontally(),
                containerColour = containerColour,
                contentColour = contentColour,
                border = contrastEdge(),
                shadow = Theme.elevation.overlay,
            ) {
                CompositionLocalProvider(LocalSheetState provides null) {
                    Column(
                        // Inside the surface, so the sheet's colour still runs
                        // to the edges of the window.
                        Modifier.fillMaxSize().windowInsetsPadding(windowInsets),
                    ) {
                        if (onBack != null) {
                            IconButton(
                                // Reading direction, not sheet side. Back is
                                // back; a start-side sheet does not point the
                                // other way because it happens to open from the
                                // left.
                                icon = if (isRtl) {
                                    Tabler.Outline.ArrowRight
                                } else {
                                    Tabler.Outline.ArrowLeft
                                },
                                contentDescription = backLabel,
                                onClick = onBack,
                                variant = ButtonVariant.Ghost,
                                modifier = Modifier.padding(
                                    start = Theme.spacing.xs,
                                    top = Theme.spacing.xs,
                                ),
                            )
                        }
                        content()
                    }
                }
            }
        }
    }
}
