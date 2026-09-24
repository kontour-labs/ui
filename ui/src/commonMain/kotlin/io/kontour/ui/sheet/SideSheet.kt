package io.kontour.ui.sheet

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.ArrowRight
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.adaptive.windowAdaptiveInfo
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Surface
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.overlay.BackdropStyle
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.LocalOverlayProgress
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.theme.SquircleShape
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.mirrorHorizontally
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
 * The one drag it has is asked for: an [expandable] sheet has a grip on its inner
 * edge that widens it to the whole window, and a [SheetPresentation.Floating] one
 * becomes an edge sheet on the way — see `expandable` and `edgeMorph`.
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
     * Whether the sheet meets the window's edges or floats clear of them.
     *
     * [SheetPresentation.Edge], the default, is flush to its side, the top and the
     * bottom, with only the corners facing the content rounded. A modal sheet
     * recedes the page behind it, and a floating panel over a receded page is two
     * frames around one thing — the reason the non-modal [SideSheet] floats and
     * this does not.
     *
     * [SheetPresentation.Floating] lifts it off its side, the top and the bottom by
     * `Theme.componentDefaults.sheetFloatingInset` — unioned with [windowInsets],
     * so it is a minimum clearance and not a gap added to the system's — and
     * rounds every corner, which is the panel-over-the-page reading a floating
     * bottom sheet has.
     */
    presentation: SheetPresentation = SheetPresentation.Edge,
    shape: CornerBasedShape = SideSheetDefaults.shapeFor(presentation),
    /**
     * Whether the sheet can be widened to the whole window.
     *
     * `true` puts a grip on the sheet's inner edge — the edge facing the page —
     * which drags the sheet out to the far side of the window and back, and which
     * a tap, a screen reader or a keyboard toggles. [SideSheetState.expand] and
     * [SideSheetState.collapse] do the same from code.
     *
     * `false`, the default, is the side sheet as it has always been: one width,
     * and no drag. A navigation drawer is a side sheet that has no business
     * filling the window.
     *
     * A window no wider than the resting sheet has nothing to expand across, so
     * there the grip is not shown and [SideSheetState.expand] does nothing.
     */
    expandable: Boolean = false,
    state: SideSheetState = rememberSideSheetState(),
    /**
     * Whether a floating sheet becomes an edge sheet as it expands — the side
     * sheet's counterpart of [BottomSheet]'s `edgeMorph`.
     *
     * `true`, the default: widening from its resting width to the whole window, its
     * margins close up and its corners become [expandedShape]'s, so it arrives as a
     * page flush to every edge. It follows the drag frame by frame; held halfway, it
     * is halfway there. `false` keeps the margin on all four sides, and the corners,
     * at every width. Nothing at all for an edge sheet, or one that is not
     * [expandable].
     */
    edgeMorph: Boolean = true,
    /**
     * What the sheet's shape becomes as it reaches the whole window: square by
     * default, because a page filling the window has no corners of its own — a
     * phone's display rounds them physically. Corner by corner, and the curve with
     * them, all the way across.
     */
    expandedShape: CornerBasedShape = SideSheetDefaults.ExpandedShape,
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
    val latestPresentation by rememberUpdatedState(presentation)
    val latestExpandable by rememberUpdatedState(expandable)
    val latestState by rememberUpdatedState(state)
    val latestEdgeMorph by rememberUpdatedState(edgeMorph)
    val latestExpandedShape by rememberUpdatedState(expandedShape)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    // A sheet closed while expanded opens again at its resting width: expanded is
    // something the reader did to this showing of it, not a setting. Only once it
    // has been shown and closed, so `initiallyExpanded` still means what it says the
    // first time.
    var closedAfterShowing by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(visible, side, width, scrim) {
        if (!visible) {
            host.hide(key)
            if (shown) closedAfterShowing = true
            return@LaunchedEffect
        }
        if (closedAfterShowing) {
            state.reset()
            closedAfterShowing = false
        }
        shown = true

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
                        presentation = latestPresentation,
                        shape = latestShape,
                        expandable = latestExpandable,
                        state = latestState,
                        edgeMorph = latestEdgeMorph,
                        expandedShape = latestExpandedShape,
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
 * Everything else is [ModalSideSheet]'s — [expandable] to the whole window with a
 * grip on its inner edge, and becoming an edge sheet as it widens — except that
 * this one floats by default, where the modal one is an edge sheet: nothing
 * behind it recedes, so the panel is the only frame on the screen.
 */
@Composable
fun SideSheet(
    visible: Boolean,
    modifier: Modifier = Modifier,
    side: SheetSide = SheetSide.End,
    /** See [ModalSideSheet]. */
    width: Dp = SideSheetDefaults.Width,
    /** See [ModalSideSheet]. Floating by default. */
    presentation: SheetPresentation = SheetPresentation.Floating,
    shape: CornerBasedShape = SideSheetDefaults.shapeFor(presentation),
    /** See [ModalSideSheet]: a grip on the inner edge that widens the sheet to the window. */
    expandable: Boolean = false,
    state: SideSheetState = rememberSideSheetState(),
    /** See [ModalSideSheet]: a floating sheet becomes an edge sheet as it widens. */
    edgeMorph: Boolean = true,
    expandedShape: CornerBasedShape = SideSheetDefaults.ExpandedShape,
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

    // A sheet closed while expanded opens again at its resting width — the modal
    // sheet's rule, for the same reason.
    var closedAfterShowing by remember { mutableStateOf(false) }
    var shownOnce by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible) {
            if (shownOnce) closedAfterShowing = true
            return@LaunchedEffect
        }
        if (closedAfterShowing) {
            state.reset()
            closedAfterShowing = false
        }
        shownOnce = true
    }

    // Not composed once it has slid away: a sheet that is not there should not be
    // in the assistive tree, nor holding its content's state against the next time.
    if (!visible && shown <= 0f) return

    SideSheetPanel(
        modifier = modifier,
        side = side,
        width = width,
        presentation = presentation,
        shape = shape,
        expandable = expandable,
        state = state,
        edgeMorph = edgeMorph,
        expandedShape = expandedShape,
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

object SideSheetDefaults {
    /** Wide enough for a column of content with padding on both sides. */
    val Width: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.sideSheetWidth

    /**
     * The corners for a presentation.
     *
     * `sideSheet` has only the corners facing the content rounded, which is right
     * against the window's edge and wrong away from it; a floating sheet has an
     * edge on every side and takes `panel`, as a floating bottom sheet does.
     */
    @Composable
    @ReadOnlyComposable
    fun shapeFor(presentation: SheetPresentation): CornerBasedShape = when (presentation) {
        SheetPresentation.Edge -> Theme.shapes.sideSheet
        SheetPresentation.Floating -> Theme.shapes.panel
    }

    /** A sheet at the whole window is a page, and a page has square corners. */
    val ExpandedShape: CornerBasedShape = SquircleShape(ZeroCornerSize)
}

/**
 * Where a side sheet is, for one width of window and one moment of its expansion.
 *
 * Every distance is measured from the window's edge the sheet belongs to — its
 * *outer* side — inwards, so one set of arithmetic serves a sheet on either side
 * and the mirroring happens once, at placement.
 *
 * A plain object rather than state: it is written by the sheet's own measurement
 * and read by its content's padding, which is measured inside that measurement, so
 * it is always current when it is read and nothing needs to observe it.
 */
private class SideFrame {
    /** The window's width. */
    var window = 0

    /** How far from the window's outer edge the sheet's inner edge is. */
    var inner = 0

    /** The margins the sheet is still keeping, outer side, top and bottom. */
    var outerKept = 0
    var topKept = 0
    var bottomKept = 0
}

@Composable
private fun SideSheetPanel(
    modifier: Modifier,
    side: SheetSide,
    width: Dp,
    presentation: SheetPresentation,
    shape: CornerBasedShape,
    expandable: Boolean,
    state: SideSheetState,
    edgeMorph: Boolean,
    expandedShape: CornerBasedShape,
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
    val floating = presentation == SheetPresentation.Floating
    val morphs = floating && edgeMorph && expandable

    // The margin a floating sheet keeps, on each side: a minimum clearance, so the
    // union with the window's insets rather than the sum — the same rule, and the
    // same token, as a floating bottom sheet.
    val margin = Theme.componentDefaults.sheetFloatingInset
    val floatInsets = remember(floating, windowInsets, margin) {
        if (floating) {
            WindowInsets(left = margin, top = margin, right = margin, bottom = margin).union(windowInsets)
        } else {
            NoSideInsets
        }
    }

    // How far between resting and the whole window the sheet is, and how far it has
    // morphed. Both read in the layout phase below, so a drag moves the sheet
    // without recomposing it; only the shape needs them in composition.
    fun expansion(): Float = if (expandable) state.expansion else 0f
    fun morph(): Float = if (morphs) expansion() else 0f

    val frame = remember { SideFrame() }
    val widthPx = with(density) { width.roundToPx() }

    // The shape is the one thing decided in composition, so a drag recomposes the
    // surface — not its content — and only while the shape is actually changing: an
    // edge sheet squares its inner corners as they reach the far side, a morphing
    // floating one becomes the edge sheet's, and one that does not morph keeps its
    // own.
    val shapeFraction by remember(state, expandable, floating, morphs) {
        derivedStateOf {
            when {
                !expandable -> 0f
                !floating -> state.expansion
                morphs -> state.expansion
                else -> 0f
            }
        }
    }
    // Mirrored for a sheet on the physical left, so the rounded edge is always the
    // one facing the content rather than the window edge.
    val restingShape = if (fromRight) shape else shape.mirrorHorizontally()
    val wholeShape = if (fromRight) expandedShape else expandedShape.mirrorHorizontally()
    val drawnShape = remember(restingShape, wholeShape, shapeFraction) {
        morphShape(restingShape, wholeShape, shapeFraction)
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .layout { measurable, constraints ->
                    val window = constraints.maxWidth
                    val height = constraints.maxHeight
                    val kept = 1f - morph()
                    val outerFull = if (fromRight) {
                        floatInsets.getRight(this, layoutDirection)
                    } else {
                        floatInsets.getLeft(this, layoutDirection)
                    }
                    val farFull = if (fromRight) {
                        floatInsets.getLeft(this, layoutDirection)
                    } else {
                        floatInsets.getRight(this, layoutDirection)
                    }
                    val resting = widthPx.coerceAtMost((window - outerFull).coerceAtLeast(0))
                    val restingInner = outerFull + resting
                    // Where the inner edge is when the sheet is the whole window: the
                    // far side itself, unless the sheet keeps its margin all the way.
                    val expandedInner = window - if (morphs) 0 else farFull
                    val travel = if (expandable) (expandedInner - restingInner).coerceAtLeast(0) else 0
                    state.updateAnchors(travel.toFloat())

                    val outer = (outerFull * kept).roundToInt()
                    val top = (floatInsets.getTop(this) * kept).roundToInt()
                    val bottom = (floatInsets.getBottom(this) * kept).roundToInt()
                    // Linear in the expansion, so the inner edge moves exactly as far
                    // as the finger on the grip does.
                    val inner = restingInner + (travel * expansion()).roundToInt()

                    frame.window = window
                    frame.inner = inner
                    frame.outerKept = outer
                    frame.topKept = top
                    frame.bottomKept = bottom

                    val sheetWidth = (inner - outer).coerceAtLeast(0)
                    val sheetHeight = (height - top - bottom).coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(sheetWidth, sheetHeight))
                    layout(window, height) {
                        // Read here, so opening and closing re-place the sheet
                        // without measuring it again. Far enough to leave the
                        // window completely, margin and all.
                        val hidden = ((sheetWidth + outer) * (1f - progress().coerceIn(0f, 1f))).roundToInt()
                        val x = if (fromRight) window - inner + hidden else outer - hidden
                        placeable.place(x, top)
                    }
                }
                .then(modifier)
                .semantics {
                    isTraversalGroup = true
                    if (paneTitle != null) this.paneTitle = paneTitle
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = drawnShape,
                colour = containerColour,
                contentColour = contentColour,
                border = contrastEdge(),
                shadow = Theme.elevation.overlay,
            ) {
                CompositionLocalProvider(LocalSheetState provides null) {
                    Column(
                        // Inside the surface, so the sheet's colour still runs
                        // to the edges of the window.
                        Modifier.fillMaxSize().then(
                            if (!floating && !expandable) {
                                // The side sheet as it has always been.
                                Modifier.windowInsetsPadding(windowInsets)
                            } else {
                                Modifier
                                    .sideSheetInsets(windowInsets, frame, fromRight)
                                    .consumeWindowInsets(windowInsets)
                            }
                        ),
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

        if (expandable) {
            SideSheetGrip(
                state = state,
                frame = frame,
                fromRight = fromRight,
                expansion = ::expansion,
                progress = progress,
            )
        }
    }
}

/**
 * The window insets a side sheet's content keeps clear of, less what the sheet's
 * margin is still clearing for it.
 *
 * Top, bottom and the outer side: whatever of the inset the margin no longer
 * covers — none of it while the sheet floats, all of it once it is an edge sheet.
 * The inner side is the other way about. A resting sheet is nowhere near the far
 * side of the window and owes its inset nothing; one widened to meet it owes all
 * of it. So that side pads by the part of the far inset the sheet now overlaps,
 * which is the same reasoning a bottom sheet's top edge uses against the status
 * bar, and handles a cutout or a keyboard at any width.
 */
private fun Modifier.sideSheetInsets(
    insets: WindowInsets,
    frame: SideFrame,
    fromRight: Boolean,
): Modifier = layout { measurable, constraints ->
    val outerInset = if (fromRight) insets.getRight(this, layoutDirection) else insets.getLeft(this, layoutDirection)
    val farInset = if (fromRight) insets.getLeft(this, layoutDirection) else insets.getRight(this, layoutDirection)
    val outer = (outerInset - frame.outerKept).coerceAtLeast(0)
    val far = (farInset - (frame.window - frame.inner)).coerceAtLeast(0)
    val top = (insets.getTop(this) - frame.topKept).coerceAtLeast(0)
    val bottom = (insets.getBottom(this) - frame.bottomKept).coerceAtLeast(0)
    val left = if (fromRight) far else outer
    val right = if (fromRight) outer else far
    val placeable = measurable.measure(constraints.offset(horizontal = -(left + right), vertical = -(top + bottom)))
    layout(
        constraints.constrainWidth(placeable.width + left + right),
        constraints.constrainHeight(placeable.height + top + bottom),
    ) { placeable.place(left, top) }
}

/**
 * The strip on an expandable sheet's inner edge that widens it to the window.
 *
 * A sibling of the sheet rather than a child, so it can straddle the edge — half
 * over the sheet and half over the page — and still be above the scrim: a child
 * reaching out of the sheet's bounds is not where a pointer looks for it. It stands
 * where the sheet's frame says the inner edge is, and moves with it.
 *
 * What `PaneScaffold`'s resize handle is, for a sheet. Precise pointers get a
 * narrow strip and a column-resize cursor; touch gets the platform's minimum. A tap
 * toggles, which is also what a screen reader, a keyboard and a switch get — a drag
 * is not a gesture any of them can make.
 */
@Composable
private fun BoxScope.SideSheetGrip(
    state: SideSheetState,
    frame: SideFrame,
    fromRight: Boolean,
    expansion: () -> Float,
    progress: () -> Float,
) {
    val colours = Theme.colours
    val motion = Theme.motion
    val strings = Theme.strings
    val precise = windowAdaptiveInfo.isPrecise
    val scope = rememberCoroutineScope()
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val dragged by interactions.collectIsDraggedAsState()
    val settleSpec: FiniteAnimationSpec<Float> = motion.springOrTween(motion.springGentle)
    val fling = AnchoredDraggableDefaults.flingBehavior(
        state = state.anchoredState,
        positionalThreshold = { distance -> distance * 0.5f },
        animationSpec = settleSpec,
    )

    // A tick for the width crossed by hand, and none for one set in code — through
    // the ticker every detent in the library goes through.
    val ticker = rememberDetentTicker()
    LaunchedEffect(state) {
        snapshotFlow { dragged to state.anchoredState.targetValue }
            .collect { (held, target) -> if (held) ticker.at(target.ordinal) else ticker.reset() }
    }

    // Nothing to expand across on a window no wider than the resting sheet.
    if (state.travel <= 0f) return

    val expanded = state.isExpanded
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .layout { measurable, constraints ->
                // Read so the grip follows the sheet; the frame itself has been
                // written by the sheet's measurement, which comes first.
                expansion()
                val strip = if (precise) GripPreciseWidth.roundToPx() else GripTouchWidth.roundToPx()
                val height = (constraints.maxHeight - frame.topKept - frame.bottomKept).coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(strip, height))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val sheetWidth = frame.inner - frame.outerKept
                    val hidden = ((sheetWidth + frame.outerKept) * (1f - progress().coerceIn(0f, 1f))).roundToInt()
                    val edge = if (fromRight) frame.window - frame.inner + hidden else frame.inner - hidden
                    placeable.place(edge - strip / 2, frame.topKept)
                }
            }
            .anchoredDraggable(
                state = state.anchoredState,
                orientation = Orientation.Horizontal,
                // Towards the page widens it, which is leftwards for a sheet on
                // the right.
                reverseDirection = fromRight,
                flingBehavior = fling,
                interactionSource = interactions,
            )
            .pointerCursor(Cursor.ResizeColumn)
            .hoverable(interactions)
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
            ) {
                scope.launch { if (state.isExpanded) state.collapse() else state.expand() }
            }
            .semantics {
                contentDescription = if (expanded) strings.collapseSheet else strings.expandSheet
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(GripLength)
                .background(
                    if (hovered || dragged) colours.contentMuted else colours.outline,
                    Theme.shapes.capsule,
                )
        )
    }
}

/** A mouse or a trackpad needs no more than this to find the edge. */
private val GripPreciseWidth = 12.dp

/** A finger needs the platform's minimum. */
private val GripTouchWidth = 48.dp

/** The pill drawn down the middle of the grip: a drag handle, stood on end. */
private val GripLength = 40.dp

/** No margin at all, for an edge sheet. */
private val NoSideInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
