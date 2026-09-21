package io.kontour.ui.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.foundation.Surface
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.overlay.BackdropStyle
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.platform.platformDeviceCorners
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.concentricWith
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Whether a sheet meets the window's edges or floats clear of them. */
enum class SheetPresentation {
    /**
     * Flush to the bottom and to both sides. A drawer pulled out of the screen.
     *
     * The default, and what a sheet has always been. Its bottom corners are
     * square because there is no bottom edge to round.
     */
    Edge,

    /**
     * Lifted off all three edges, with every corner rounded.
     *
     * A panel *over* the screen rather than a drawer out of it. Worth reaching
     * for when the sheet is permanently present rather than summoned, and
     * necessary when its lowest detent is small: a bar-height sheet flush to the
     * bottom of the window reads as a drawer that failed to open, and the same
     * thing floating reads as a control.
     */
    Floating,
}

object SheetDefaults {
    /**
     * The corners for a presentation, floored at the device's own.
     *
     * `Theme.shapes.sheet` has square bottom corners, which is right against the
     * window's edge and wrong away from it — a floating panel with two sharp
     * corners at the bottom looks like a drawer that has come loose. So the
     * floating one takes `Theme.shapes.panel`, rounded all round, which is the
     * token for exactly that: something with an edge on every side.
     *
     * ### Concentric with the device
     *
     * A sheet at full height stops [SheetTopGap] short of the screen, so its top
     * corners sit just inside the device's — and a corner inside a larger one
     * looks wrong unless it is *concentric* with it: the same centre, a radius
     * smaller by the gap between them. A 34dp corner inside a 55dp bezel reads
     * as two unrelated curves.
     *
     * `atLeast` is a floor rather than an assignment, so a device with gentler
     * corners than the scale's own leaves the scale alone, and `atLeast(null)`
     * is the identity — which is what JVM and web return, and is why there is no
     * branch here. The backdrop has done exactly this since it started insetting
     * a receding page; this is the same rule for the thing in front of it.
     *
     * No longer `@ReadOnlyComposable`, which it was while it only read tokens:
     * the device's radius is not a token read. Android needs `LocalView` and the
     * window behind it, and iOS `remember`s a KVC lookup — neither is legal in a
     * read-only composable, and the annotation is a promise about what the body
     * does rather than a hint.
     */
    @Composable
    fun shapeFor(presentation: SheetPresentation): Shape = when (presentation) {
        SheetPresentation.Edge ->
            // The top pair only, in the end: `sheet` zeroes its bottom corners
            // because it is flush to the window, and `MaxCornerSize` passes a
            // zero through untouched — so handing it all four is safe and says
            // what is meant. The bezel's own curve comes with them.
            Theme.shapes.sheet.concentricWith(
                platformDeviceCorners(),
                SheetTopGap,
                LocalLayoutDirection.current,
            )
        SheetPresentation.Floating -> Theme.shapes.panel
    }

    /**
     * How far a drag must travel before it commits to the next detent.
     *
     * Applies to a *slow* gesture only. Above `AnchoredDraggableMinFlingVelocity`
     * — 125dp/s, a private constant in Compose Foundation — direction decides
     * instead of distance, and the sheet goes to the next anchor whichever side
     * of the halfway mark the finger left it.
     *
     * Which is also why a hard flick and a gentle one land in the same place:
     * both clear 125dp/s, both move exactly one detent. Skipping detents on a
     * fast throw needs a `TargetedFlingBehavior` of our own, because the version
     * of `AnchoredDraggableDefaults.flingBehavior` we are on takes the
     * positional threshold and the snap spec and nothing else — there is no
     * velocity parameter to raise. Worth doing, and worth doing against a real
     * finger rather than a JVM host, since the failure mode of getting the drag
     * mutex wrong inside `performFling` is a sheet that stops responding.
     */
    val PositionalThreshold: (Float) -> Float = { distance -> distance * 0.5f }

    /** A sheet wider than this is a panel; centre it rather than stretching it. */
    val MaxWidth: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.sheetMaxWidth

    /** Between the sheet's top edge and anything riding above it. */
    val ActionsGap: Dp = 8.dp
}

/**
 * A sheet that lives *in* the layout, over the content behind it.
 *
 * ```kotlin
 * val sheet = rememberSheetState(
 *     detents = listOf(SheetDetent.Hidden, SheetDetent.peek(140.dp), SheetDetent.Expanded),
 * )
 *
 * Box(Modifier.fillMaxSize()) {
 *     Map(contentPadding = PaddingValues(bottom = with(density) { sheet.visibleHeight.toDp() }))
 *     BottomSheet(sheet, Modifier.align(Alignment.BottomCenter)) {
 *         SheetHeader(Modifier.sheetPeekAnchor()) { +"Perth Underground" }
 *         LazyColumn { … }
 *     }
 * }
 * ```
 *
 * Non-modal by design: nothing behind it is dimmed or blocked, which is the
 * whole point over a map. The user pans the map with the sheet resting at its
 * peek detent, and `sheet.visibleHeight` is what the map insets its controls by
 * so they stay above it.
 *
 * For a sheet that *does* take over — a form, a confirmation, a picker — use
 * [ModalBottomSheet].
 *
 * A `LazyColumn` inside works without ceremony — the drag hands off between
 * list and sheet at each end, which is why the sheet takes its content as a
 * slot rather than being a modifier. See
 * `ui-docs/content/sheets.md`.
 *
 * ### Content taller than the window
 *
 * The content is measured at the room the sheet has, not at an unbounded
 * height, so a scroller inside it scrolls rather than being cropped — wrap a
 * long `Column` in `verticalScroll`, or use the `LazyColumn` above, and the part
 * that does not fit is reachable. Content that *does* fit is unaffected:
 * `SheetDetent.Expanded` still means "as tall as the content".
 */
@Composable
fun BottomSheet(
    state: SheetState,
    modifier: Modifier = Modifier,
    /**
     * Whether the sheet meets the window's edges or floats clear of them.
     *
     * [SheetPresentation.Edge] is the default and is what a sheet has always
     * been: flush to the bottom and to both sides, with its top corners rounded
     * and its bottom ones square because there is no bottom to round.
     *
     * [SheetPresentation.Floating] lifts it off all three edges by
     * [Theme.componentDefaults][io.kontour.ui.theme.ComponentDefaults.sheetFloatingInset] and rounds every corner. Two things follow
     * that are the reason to reach for it. It reads as a *panel over* the screen
     * rather than a drawer pulled out of it, which is what a sheet that is
     * permanently present wants to look like. And it can shrink to something the
     * size of a control without looking like a drawer that failed to open — a
     * search field parked at the bottom of a map, which is the case this was
     * asked for.
     *
     * **Collapsing instead of dismissing is a detent question, not this one.**
     * A sheet's anchors come from its own detent list, so one whose detents are
     * `[height("bar", 64.dp), Half, Expanded]` — with no [SheetDetent.Hidden] —
     * has no anchor to be dragged away to, and stretches past its lowest detent
     * exactly as it does above its top. That works at either presentation; this
     * one only decides what it looks like while it does.
     */
    presentation: SheetPresentation = SheetPresentation.Edge,
    shape: Shape = SheetDefaults.shapeFor(presentation),
    containerColour: Color = Theme.colours.surfaceRaised,
    contentColour: Color = Theme.colours.content,
    paneTitle: String? = null,
    /**
     * Whether the sheet answers a drag.
     *
     * `false` removes the gesture **and** the drag handle, because a handle that
     * does nothing is a lie about what the sheet will do — and a handle is the
     * only thing on a sheet that says "pull me". A sheet that cannot be dragged
     * is moved by [SheetState.animateTo] and by whatever the content offers.
     *
     * The scrim follows the sheet's visible height, so a sheet that cannot be
     * dragged also cannot fade its scrim halfway: there is no halfway to be at.
     */
    draggable: Boolean = true,
    dragHandle: (@Composable () -> Unit)? = { DragHandle(state = state) },
    /**
     * What the sheet's *chrome* keeps clear of, and what its content is **told
     * about**. The status bar, the gesture bar, the cutout and the keyboard, so
     * a text field in a sheet is not typed at from behind it. The sheet's own
     * surface still reaches the bottom of the window, and still reaches the top.
     *
     * **The bottom side is handed to [content] rather than applied to it**, and
     * the other three are applied. See [content]: the sheet cannot get inside a
     * caller's scroller, so a sheet that padded its content could only ever stop
     * the last row *above* the gesture bar rather than let it travel through.
     *
     * **[allEdges][io.kontour.ui.adaptive.allEdges] rather than
     * [sheetEdges][io.kontour.ui.adaptive.sheetEdges], which has no top side.**
     * A sheet at full height stops [SheetTopGap] short of the screen, which is
     * far above the status bar, so with no top inset its drag handle and its
     * header drew *under* the status bar and the notch. Reported from a phone,
     * and the same defect `ToastPosition.Top` had for the same reason.
     *
     * A half-height sheet is unaffected: `windowInsetsPadding` applies only the
     * part of an inset that overlaps the node, and a sheet whose top is at the
     * middle of the window overlaps no status bar. Passing `WindowInsets(0)` is
     * still how a caller says something above it has already handled all of
     * this.
     */
    windowInsets: WindowInsets = WindowInsets.allEdges,
    /**
     * Controls that ride *above* the sheet's top edge rather than inside it.
     *
     * For the things that must stay reachable while the content scrolls — a
     * "recentre" on a map, a filter, a layer toggle. Inside the sheet they would
     * either scroll away or need a pinned header eating the sheet's height; here
     * they sit on whatever is behind it and move with the sheet as it is
     * dragged, which is the arrangement every maps app converges on.
     *
     * **Not [SheetHeader]'s `actions`,** which is the row *inside* the sheet
     * beside its title. This was called `actions` too, and one component family
     * with two different `actions` meaning two different places is the reason
     * nobody could tell which one they wanted. These float; those do not.
     *
     * They belong to the sheet, so they go when it goes: the row fades out over
     * the last stretch of the sheet's travel and is not composed at all once it
     * has settled hidden. It used to stay parked at the bottom of the window
     * over a sheet that was no longer there.
     *
     * End-aligned by default. The row is a `RowScope`, so `Arrangement` and
     * `Modifier.align` are how you say otherwise.
     */
    floatingControls: (@Composable RowScope.() -> Unit)? = null,
    /**
     * The sheet's content, and the safe area under it.
     *
     * The `PaddingValues` is the bottom inset — the gesture bar, or the keyboard
     * when it is up — and the sheet does **not** apply it. Reported as content
     * being cut off at the safe zone: *"rather than cutting it off, could we
     * make it so it keeps going, but there's just some content padding so all
     * content is scrollable above the safe zone."*
     *
     * That is the difference between padding a scroller and padding what is
     * inside it, and only the caller knows which they have:
     *
     * ```
     * BottomSheet(state) { padding ->
     *     LazyColumn(contentPadding = padding) { … }   // scrolls through the bar
     *     Column(Modifier.padding(padding)) { … }      // sits above it
     * }
     * ```
     *
     * `Scaffold` has taken the same position since it was written, in the same
     * words: *"a `LazyColumn` wants it as `contentPadding`, a `Column` wants it
     * as `padding`, and applying it to the wrong one clips the scroll."* The
     * sheet used to be the one container in the library that decided for you,
     * and what it decided was the first of those two for everybody.
     *
     * **A sheet that ignores it draws to the bottom of the window**, which is a
     * behaviour change for content written before this parameter existed. The
     * other three sides are still applied, and a floating sheet hands out zero
     * because its own margin has already cleared the inset.
     */
    content: @Composable SheetContentScope.(PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val motion = Theme.motion
    val actionsGap = SheetDefaults.ActionsGap
    val floating = presentation == SheetPresentation.Floating

    // The margin a floating sheet keeps, on each of the four sides.
    //
    // `union` and not a sum, because the margin is a *minimum* clearance rather
    // than a gap added to whatever the system asks for. On a phone with a 24dp
    // gesture bar, a sum floats the sheet 36dp up — a margin that reads as a
    // mistake next to the 12dp at the sides. The union gives 24, which is the
    // clearance that was already required, and the sides stay at 12.
    //
    // The keyboard is in `allEdges`, so the same line is what lifts a floating
    // search field above the IME instead of letting it be covered. The status bar
    // is in there too now, which is the same fix as the chrome's below: a
    // floating sheet tall enough to reach the top of the window has rounded
    // corners up there, and they belong below the bar rather than under it.
    val floatInsets = if (floating) {
        val inset = Theme.componentDefaults.sheetFloatingInset
        remember(windowInsets, inset) {
            WindowInsets(left = inset, top = inset, right = inset, bottom = inset)
                .union(windowInsets)
        }
    } else {
        windowInsets
    }

    // Critically damped. A sheet that bounces on arrival looks unweighted, and
    // unlike a button it is carrying content the user is reading.
    //
    // Named, because the nested-scroll connection settles with it too — a fling
    // that starts in the sheet's list has to finish the way one that started on
    // the handle does.
    val settleSpec: FiniteAnimationSpec<Float> = motion.springOrTween(motion.springGentle)

    val overscroll = rememberSheetOverscroll(state, settleSpec)
    // And the *other* overscroll: the one a list inside the sheet would have got
    // from the platform. Inside a draggable sheet the sheet owns the vertical
    // axis, because only one of the two can answer a finger that has run out of
    // list. See `SheetChildOverscroll`.
    val childOverscroll = rememberSheetChildOverscrollFactory(draggable)
    // What tells a finger from an `animateTo`. See `SheetState.draggedByHand`.
    val dragInteractions = remember { MutableInteractionSource() }
    LaunchedEffect(dragInteractions, state) {
        var held = 0
        dragInteractions.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> held++
                is DragInteraction.Stop, is DragInteraction.Cancel -> held--
            }
            state.draggedByHand = held > 0
        }
    }

    // One tick per detent the sheet is dragged across, and none for a sheet
    // opened in code. Through `DetentTicker` like every other detent in the
    // library, which is also why it costs nothing against the haptics ceiling:
    // the ceiling counts direct `perform` calls and the ticker is one of them
    // for the whole library.
    val ticker = rememberDetentTicker()
    LaunchedEffect(state) {
        snapshotFlow { state.draggedByHand to state.targetDetent }
            .collect { (dragging, detent) ->
                // The index in the sheet's *own* list, which is reassignable —
                // a sheet whose detents depend on what is in it changes them
                // mid-life, and an index out of a stale copy would tick for a
                // move that did not happen.
                if (dragging) ticker.at(state.detents.indexOf(detent)) else ticker.reset()
            }
    }

    // In pixels here, because `SheetState` works in pixels throughout and the
    // nested-scroll callbacks have no density of their own.
    val flickVelocity = with(density) { SheetFlickVelocity.toPx() }

    val fling = AnchoredDraggableDefaults.flingBehavior(
        state = state.anchoredState,
        positionalThreshold = SheetDefaults.PositionalThreshold,
        animationSpec = settleSpec,
    )

    // A detent asked for before the sheet had been measured, delivered once it
    // has. `updateAnchors` deliberately does not apply it: doing so snapped, and
    // the first open is always the one with no anchors yet.
    //
    // A `snapshotFlow` rather than a composition read, and the difference is
    // not style: `hasPendingDelivery` is `pendingDetent != null && hasAnchors`,
    // which short-circuits. Read during composition while no detent is pending,
    // it never subscribes to the anchors at all — so the anchors arrive, nothing
    // recomposes, and the sheet stays shut. `snapshotFlow` re-evaluates the whole
    // expression on every snapshot commit and does not have that hole.
    LaunchedEffect(state) {
        snapshotFlow { state.hasPendingDelivery }
            .filter { it }
            .collect { state.deliverPending() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // The container's height, read in the **measure** phase.
            //
            // `onSizeChanged` fires after layout, so for the whole of the first
            // pass `containerHeight` was 0 — and everything downstream is sized
            // from it. The surface is told to be 0 tall, the content is measured
            // against that, `sheetHeight` comes out 0, `Expanded` resolves to
            // "hidden", and the sheet never appears. Measuring the content
            // unbounded was the old way round that, and it is what cropped
            // anything taller than the window; it also makes a `verticalScroll`
            // inside a sheet **throw** — Compose refuses a scroller measured at
            // an infinite height by name.
            //
            // `fillMaxSize` means the incoming constraints *are* the container,
            // and they are known before any child is measured. So the first pass
            // is already correct and nothing downstream needs a fallback.
            .layout { measurable, constraints ->
                if (constraints.hasBoundedHeight) {
                    // The height only. **Not** `updateAnchors` — the content's
                    // own measure block calls that, and it is the first moment
                    // at which both this and `sheetHeight` are known. Rebuilding
                    // the anchors here instead resolves every detent from a
                    // `sheetHeight` of 0, which settles the sheet at "hidden"
                    // before it has been measured and leaves it there.
                    state.containerHeight = constraints.maxHeight.toFloat()
                }
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            // Still the source of record for a host with an unbounded height,
            // where `fillMaxSize` has nothing to fill and the block above
            // declines to guess. Writes the same number in every other case.
            .onSizeChanged { size ->
                state.containerHeight = size.height.toFloat()
                state.updateAnchors(density)
            }
    ) {
        Box(
            modifier = modifier
                // Top-aligned, then offset. Bottom-aligning *and* offsetting
                // double-counts: the box's top is already at
                // container - sheetHeight, and the offset is measured from the
                // container's top, so the sheet ends up that much too low.
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = SheetDefaults.MaxWidth)
                // The float, horizontally. A padding at the sides is enough
                // because nothing here is measured from a side edge; the
                // vertical half of the same margin is not, and is in `sheetTop`.
                .then(
                    if (floating) {
                        Modifier.windowInsetsPadding(
                            floatInsets.only(WindowInsetsSides.Horizontal)
                        )
                    } else {
                        Modifier
                    }
                )
                // Read in the layout phase, so neither the drag nor the stretch
                // above the top detent ever recomposes the sheet's content.
                .offset { IntOffset(0, sheetTop(state, floating, floatInsets, this)) }
                .then(
                    if (draggable) {
                        Modifier
                            .nestedScroll(
                                state.nestedScrollConnection(settleSpec, flickVelocity)
                            )
                            .anchoredDraggable(
                                state = state.anchoredState,
                                orientation = SheetOrientation,
                                flingBehavior = fling,
                                interactionSource = dragInteractions,
                                // Lets the sheet be pulled above its top detent
                                // and springs it back — see `SheetOverscroll`.
                                overscrollEffect = overscroll,
                            )
                    } else {
                        // The nested-scroll connection goes with the drag. Its
                        // whole job is handing a list's overscroll to the sheet,
                        // and a sheet that does not move should not be receiving
                        // it — a flick at the top of the list would otherwise
                        // still close the sheet.
                        Modifier
                    }
                )
                .semantics {
                    isTraversalGroup = true
                    if (paneTitle != null) this.paneTitle = paneTitle
                }
        ) {
            SheetSurface(
                state = state,
                shape = shape,
                floating = floating,
                floatInsets = floatInsets,
                // A floating sheet is already clear of the window's edges, so
                // padding its content by them again would inset it twice — and
                // on a gesture-navigation phone that is a bar's worth of dead
                // space under a sheet the size of a search field.
                windowInsets = if (floating) NoInsets else windowInsets,
                containerColour = containerColour,
                contentColour = contentColour,
                // A handle on a sheet that cannot be dragged is a lie.
                dragHandle = dragHandle.takeIf { draggable },
                density = density,
                // Provided around the content rather than the whole sheet: the
                // sheet's own `anchoredDraggable` is handed its effect directly
                // and never reads this, and the scrim and the chrome scroll
                // nothing. `rememberSheetChildOverscrollFactory` hands back the
                // platform's own factory unchanged when the sheet is not
                // draggable, so this line is one shape for both cases.
                content = { padding ->
                    CompositionLocalProvider(
                        LocalOverscrollFactory provides childOverscroll,
                    ) {
                        content(padding)
                    }
                },
            )
        }

        // Not composed at all once the sheet has settled hidden. Alpha alone
        // would not do: a node at zero alpha is still hit-tested, so the
        // controls went on swallowing taps aimed at the page behind them.
        if (floatingControls != null && state.isVisible) {
            var actionsHeight by remember { mutableIntStateOf(0) }
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = SheetDefaults.MaxWidth)
                    .onSizeChanged { actionsHeight = it.height }
                    // The sheet's own offset, less this row's height and a gap,
                    // so it rides the top edge wherever the drag leaves it.
                    // Placed after the sheet so it draws over the surface's
                    // shadow rather than under it.
                    .offset {
                        IntOffset(
                            0,
                            sheetTop(state, floating, floatInsets, this) -
                                actionsHeight -
                                actionsGap.roundToPx(),
                        )
                    }
                    .graphicsLayer {
                        // Faded over exactly the distance the row occupies, so
                        // it is gone by the time the sheet's top edge has passed
                        // where it was sitting — and at full strength for the
                        // whole of any travel between real detents, which a
                        // fraction of the sheet's own height would not be.
                        //
                        // Read here rather than captured: `visibleHeight` moves
                        // every frame of a drag, and reading it in composition
                        // would recompose the row and everything in it for each
                        // one.
                        val over = (actionsHeight + actionsGap.toPx()).coerceAtLeast(1f)
                        alpha = (state.visibleHeight / over).coerceIn(0f, 1f)
                    }
                    .windowInsetsPadding(floatInsets.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = Theme.spacing.md),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Bottom,
                content = floatingControls,
            )
        }
    }
}

/**
 * A sheet that takes over the screen until it is dealt with.
 *
 * ```kotlin
 * ModalBottomSheet(visible = editing, onDismissRequest = { editing = false }) {
 *     SheetHeader { +"Rename favourite" }
 *     TextField(state = name, label = "Name")
 *     Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { +"Save" }
 * }
 * ```
 *
 * Renders into the [io.kontour.ui.overlay.OverlayHost], so it stacks and shares
 * a scrim with dialogs and menus, and a back gesture closes it. Owns its own
 * [SheetState] unless one is passed.
 *
 * The distinction from [BottomSheet] is not decoration: a modal sheet dims and
 * blocks what is behind it, which is right for a decision and wrong for
 * anything the user needs to keep looking at while they work.
 */
@Composable
fun ModalBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    state: SheetState = rememberSheetState(
        detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
        initialDetent = SheetDetent.Hidden,
    ),
    /** See [BottomSheet]. `Floating` lifts the sheet off all three edges. */
    presentation: SheetPresentation = SheetPresentation.Edge,
    shape: Shape = SheetDefaults.shapeFor(presentation),
    containerColour: Color = Theme.colours.surfaceRaised,
    contentColour: Color = Theme.colours.content,
    /**
     * Whether the user can close this sheet without the app's help.
     *
     * `false` closes **every** route out: the tap outside, the back gesture, and
     * the drag to the bottom — which springs back instead. For the sheet that
     * must be answered rather than escaped: a required choice, a form with
     * unsaved changes. The app can still close it by setting `visible` to false,
     * and should offer some way to.
     *
     * Not the same as `draggable = false`, which stops the sheet moving at all
     * and takes its handle with it. A sheet can be undismissable and still be
     * dragged between its detents.
     *
     * Pair it with `onClose = null` on the [SheetHeader], or the sheet grows a
     * close button that contradicts it.
     */
    dismissible: Boolean = true,
    dismissLabel: String = Theme.strings.close,
    paneTitle: String? = null,
    /** See [BottomSheet]. `false` removes the gesture and the handle with it. */
    draggable: Boolean = true,
    dragHandle: (@Composable () -> Unit)? = { DragHandle(state = state) },
    /** See [BottomSheet]. Every edge, so the handle clears the status bar. */
    windowInsets: WindowInsets = WindowInsets.allEdges,
    /**
     * See [BottomSheet]'s, which this hands the bottom inset straight through
     * from: a `LazyColumn` wants it as `contentPadding`, a `Column` as `padding`.
     */
    content: @Composable SheetContentScope.(PaddingValues) -> Unit,
) {
    val host = LocalOverlayHost.current
    val key = remember { Any() }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismissRequest)
    val body by rememberUpdatedState(content)
    // Everything the entry's content reads has to be read *live*. The effect
    // below is keyed on `visible` alone, so anything else it closes over is
    // frozen at the moment the sheet was shown: re-theming a sheet that is
    // already up, or swapping its drag handle, did nothing until it was closed
    // and reopened. `content` and `onDismissRequest` were already hoisted for
    // this reason; the appearance was missed.
    val latestModifier by rememberUpdatedState(modifier)
    val latestShape by rememberUpdatedState(shape)
    val latestPresentation by rememberUpdatedState(presentation)
    val latestContainerColour by rememberUpdatedState(containerColour)
    val latestContentColour by rememberUpdatedState(contentColour)
    val latestPaneTitle by rememberUpdatedState(paneTitle)
    val latestDragHandle by rememberUpdatedState(dragHandle)
    val latestDraggable by rememberUpdatedState(draggable)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    // Drag it shut and the caller finds out, so `visible` and the sheet cannot
    // disagree about whether it is open.
    val showing by rememberUpdatedState(visible)
    val canDismiss by rememberUpdatedState(dismissible)

    // The state has to know, because the two things that enforce it — the drag
    // and the list inside the sheet — both reach the sheet through it. In a
    // `SideEffect` rather than written straight out: this is publishing a
    // composition's value to an object that lives outside it, and a gesture
    // cannot arrive before the composition it belongs to has finished.
    SideEffect { state.userDismissible = dismissible }
    LaunchedEffect(state) {
        snapshotOfHidden(state, stillVisible = { showing }) {
            // A sheet that cannot be dismissed does not pass the drag on as a
            // request. `snapshotOfHidden` then finds the caller still wants it
            // visible and puts it back — which is the whole of "it does not
            // close", using the mechanism that was already there for a caller
            // declining one.
            if (canDismiss) dismiss()
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            host.show(
                OverlayEntry(
                    key = key,
                    layer = OverlayLayer.Sheet,
                    scrim = ScrimStyle.Dimmed,
                    // Always dimmed, so always modal, so always trapping.
                    trapFocus = true,
                    // A sheet covers part of the screen rather than floating in
                    // the middle of it, so the presenting content recedes. That
                    // is what says "on top of this screen" rather than "a new
                    // screen".
                    //
                    // `Scale` rather than `BlurAndScale`, which is what this
                    // asked for until the softening was priced: a blurred
                    // backdrop costs 7.3x the rest of the frame and costs it on
                    // every frame the sheet is open, not only while it arrives.
                    // A sheet is the surface people sit in — the gallery's
                    // settings live in one — and "switching themes is
                    // ridiculously laggy" was reported from exactly that. A
                    // dialog still blurs; it is on screen for one decision and
                    // has nothing to recede.
                    backdrop = BackdropStyle.Scale,
                    dismissOnOutside = dismissible,
                    dismissLabel = dismissLabel,
                    // The sheet slides itself down and hides the entry when it
                    // has landed — see the `else` branch below.
                    managesOwnExit = true,
                    // The sheet slides on its own spring, so the scrim has to
                    // follow the sheet rather than the host's fade — otherwise
                    // the dimming is gone while the sheet is still on its way
                    // down. This is the worst of the desyncs, because a spring
                    // and a tween disagree most in the middle.
                    visibility = { state.visibleFraction },
                    // Tell the caller, not just the sheet. This used to only
                    // launch `state.hide()`, so after an outside tap the host
                    // had dropped the entry while the caller's `visible` was
                    // still true — and `LaunchedEffect(visible)` never re-ran,
                    // so the sheet could not be reopened. `onDismissRequest`
                    // flips `visible`, which is what actually closes it.
                    onDismiss = { dismiss() },
                    content = {
                        BottomSheet(
                            state = state,
                            modifier = latestModifier,
                            presentation = latestPresentation,
                            shape = latestShape,
                            containerColour = latestContainerColour,
                            contentColour = latestContentColour,
                            paneTitle = latestPaneTitle,
                            draggable = latestDraggable,
                            dragHandle = latestDragHandle,
                            content = body,
                        )
                    },
                )
            )
            state.show()
        } else {
            // `hide()` suspends until the sheet has finished sliding down, so
            // the scrim is still there to fade behind it rather than being
            // pulled out from under a sheet that is still moving.
            if (state.isVisible) state.hide()
            host.hide(key)
        }
    }
}

@Composable
private fun BoxScope.SheetSurface(
    state: SheetState,
    shape: Shape,
    floating: Boolean,
    floatInsets: WindowInsets,
    windowInsets: WindowInsets,
    containerColour: Color,
    contentColour: Color,
    dragHandle: (@Composable () -> Unit)?,
    density: Density,
    content: @Composable SheetContentScope.(PaddingValues) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // As tall as the container, always — and then translated down to
            // where the detent wants it.
            //
            // The requirement is that the surface reaches the bottom of the
            // container at every detent, so its colour runs to the bottom edge.
            // It used to be wrap-content and merely translated, which left a gap
            // of `visibleHeight - contentHeight` below the sheet at every detent
            // except `Expanded`; and because the content height is fixed while
            // the offset moves with the finger, dragging a half-open sheet
            // upward carried its bottom edge up the screen with it.
            //
            // The fix for that sized the surface to `containerHeight - offset`,
            // which was correct and expensive: **the height changed on every
            // frame the sheet moved**, and a node whose size changes cannot keep
            // the drawing it recorded last frame. Measured, the sheet re-recorded
            // itself 0.9 times per frame while sliding — which on a phone means
            // re-rasterising its two blurred `dropShadow` layers sixty times a
            // second. See `SheetFramePressureTest`.
            //
            // A constant height gets the same picture for nothing. The surface
            // is `containerHeight` tall and starts at `offset`, so its bottom
            // lands at `offset + containerHeight`, at or below the container's
            // own bottom at every detent — the gap cannot open. What hangs below
            // the screen is never seen. The content is measured against the
            // same `containerHeight` either way, so the region actually on
            // screen, `offset` to `containerHeight`, is identical to what it
            // was.
            //
            // ### A floating sheet is the exception, and has to be
            //
            // It has a bottom edge that is *seen*, pinned a margin off the
            // window's, so there is no surplus to hang off the screen: the
            // surface is exactly as tall as the sheet is visible, and that
            // number changes on every frame of a drag. The saving above is not
            // available to a presentation whose whole point is two edges that
            // both move.
            //
            // What it does keep is the content's measurement. The column inside
            // is measured against the **container**, not against this, so
            // `sheetHeight` — the content's own full height, which is what
            // `SheetDetent.Expanded` resolves from — does not shrink to whatever
            // the sheet is currently showing. It did in the first draft, and the
            // sheet could then never expand: a shorter surface measured shorter
            // content, which resolved `Expanded` lower, which made the surface
            // shorter again.
            .layout { measurable, constraints ->
                // `surfaceHeight`, not `containerHeight`: the tallest detent is
                // what decides how much surface has to exist, and a sheet that
                // never opens past a third of the window was being given a
                // window's worth. It is still a **constant** — it changes when
                // the anchors do and not when the sheet moves — so everything
                // the paragraphs above say about not resizing still holds.
                //
                // What it buys is the shadow, whose blur is linear in the area
                // it covers: measured at 1.2ms for a 200dp-tall surface against
                // 5.9 for a 900dp one, on the frame a modal sheet mounts, where
                // the sheet is off-screen and none of it can be seen. See
                // `SheetState.surfaceHeight`.
                val window = state.containerHeight
                    .coerceAtLeast(0f)
                    .roundToInt()
                    .coerceAtMost(constraints.maxHeight)
                val target = if (floating) {
                    // A floating sheet's bottom edge is *seen*, pinned a margin
                    // off the window's, so its height is measured from the
                    // window and changes every frame. The saving below is not
                    // available to it, and the `graphicsLayer` on its content
                    // is what keeps that from reaching the caller's content.
                    (window - floatingLift(state, floatInsets, this) -
                        sheetTop(state, true, floatInsets, this))
                        .coerceIn(0, window)
                } else {
                    state.surfaceHeight
                        .coerceAtLeast(0f)
                        .roundToInt()
                        .coerceAtMost(constraints.maxHeight)
                }
                val placeable = measurable.measure(
                    constraints.copy(minHeight = target, maxHeight = target)
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .onGloballyPositioned { coordinates ->
                state.sheetCoordinates = coordinates
                state.measurePeek()
                state.updateAnchors(density)
            },
        shape = shape,
        colour = containerColour,
        contentColour = contentColour,
        border = contrastEdge(),
        shadow = Theme.elevation.overlay,
    ) {
        CompositionLocalProvider(LocalSheetState provides state) {
            // Inside the surface: the sheet's own colour still runs to the
            // bottom of the window, so the gesture bar sits on the sheet rather
            // than on a strip of whatever is behind it.
            Column(
                Modifier
                    .fillMaxWidth()
                    // A layer of its own, for a floating sheet only.
                    //
                    // The surface's size changes on every frame a floating sheet
                    // moves — see its layout block — and a size change
                    // invalidates that node's draw. Without a layer here the
                    // content is part of the same recording, so it is re-recorded
                    // with it: measured at 8 re-records over 40 frames of
                    // dragging, against 0 for an edge sheet. With one, the
                    // content is rasterised once and composited, and only the
                    // surface's own background and shadow redraw.
                    //
                    // Not given to an edge sheet, which does not need it: its
                    // surface never resizes, so there is nothing to isolate it
                    // from, and a layer is an offscreen buffer the size of the
                    // sheet on every platform that has one.
                    .then(if (floating) Modifier.graphicsLayer() else Modifier)
                    // The top inset, which only `windowInsetsPadding` looks like
                    // it could do. See [sheetTopInset].
                    .sheetTopInset(state, windowInsets, floating, floatInsets)
                    // Free to be shorter than the surface, never taller.
                    //
                    // `SheetDetent.Expanded` means "as tall as the content", so
                    // the content's own height has to stay knowable after the
                    // surface stopped being sized by it — otherwise `Expanded`
                    // resolves to whatever the sheet is currently showing and
                    // the anchor chases itself. Dropping `minHeight` is what
                    // buys that: a `Column` wraps its children, so for content
                    // that fits, `placeable.height` is the content's height and
                    // `Expanded` resolves exactly where it always did.
                    //
                    // ### Why not `Constraints.Infinity` every time
                    //
                    // It used to be, and content taller than the window was
                    // **cropped rather than scrolled**. The child was told it
                    // had infinite room, laid itself out believing it, and was
                    // then placed in the room the surface actually has;
                    // everything past the window's bottom edge simply was not
                    // drawn. Reported from a phone: the gallery's settings panel
                    // is 728dp at 100% type and 863dp at 200%, against roughly
                    // 867dp of usable window on a Pixel-class phone — at 200% a
                    // reader lost Text size and Input modality, the two controls
                    // they opened the panel to use.
                    //
                    // A `verticalScroll` inside could not rescue it — Compose
                    // refuses a scroller measured at an infinite height by name
                    // and **throws**, so the recommended cure for a long sheet
                    // was a crash. Neither could a `LazyColumn`, which at an
                    // infinite viewport composes *every* item, exactly what the
                    // KDoc above recommends it for. A finite maxHeight is what
                    // makes both work; see `SheetContentConstraintsTest`.
                    //
                    // Nothing is lost at the tall end: `resolveAnchors` clamps
                    // the visible height to the container, so a `sheetHeight`
                    // that is now the window's height instead of the content's
                    // resolves `Expanded` to the same offset — the top of the
                    // container — that a taller number did.
                    //
                    // Two measure passes are not the alternative: a `Measurable`
                    // may be measured once. Nor are intrinsics — a `LazyColumn`
                    // has none.
                    .layout { measurable, constraints ->
                        // A `maxHeight` of zero is not a measurement, it is
                        // the absence of one, and measuring the content against
                        // it gives `sheetHeight = 0`, `Expanded` resolving to
                        // "hidden", and a sheet that never appears — which is
                        // what thirteen tests across five classes reported when
                        // the first draft of this took the incoming constraints
                        // unconditionally.
                        //
                        // A host with a size never reaches this now: the
                        // container is read in the measure phase above, so the
                        // surface has its real height on the very first pass.
                        // What is left is the host that is genuinely unbounded,
                        // where the sheet has no room to speak of and unbounded
                        // is the only honest answer. Note that a `verticalScroll`
                        // inside a sheet in *that* host will throw — Compose
                        // refuses a scroller measured at an infinite height —
                        // and that is Compose's rule rather than this one's.
                        //
                        // The container rather than the incoming constraints,
                        // which for a floating sheet is the difference between a
                        // sheet that can expand and one that cannot. See the
                        // surface's own layout block above. Less whatever the
                        // top edge has reserved — [sheetContentCeiling] has why.
                        val container = state.containerHeight
                            .coerceAtLeast(0f)
                            .roundToInt()
                        val ceiling = sheetContentCeiling(
                            container = container,
                            insets = windowInsets,
                            floating = floating,
                            floatInsets = floatInsets,
                        )
                        val room = when {
                            container > 0 ->
                                constraints.copy(minHeight = 0, maxHeight = ceiling)

                            constraints.hasBoundedHeight && constraints.maxHeight > 0 ->
                                constraints.copy(minHeight = 0)

                            else ->
                                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                        }
                        val placeable = measurable.measure(room)
                        state.sheetHeight = placeable.height.toFloat()
                        state.updateAnchors(density)
                        val height = placeable.height.coerceAtMost(constraints.maxHeight)
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
                    // **Horizontal only, where this used to take the bottom as
                    // well.** The surface reaches the bottom of the window at
                    // every detent and the full width at all of them, so the
                    // sides apply in full whatever the sheet is doing — and the
                    // top is the hard one, which [sheetTopInset] above has.
                    //
                    // The bottom is now the content's to place, because the
                    // sheet cannot get inside a caller's scroller: padding here
                    // ends a `LazyColumn`'s viewport above the gesture bar, so
                    // the last row stops short of it instead of travelling
                    // through it and coming to rest clear. Reported as content
                    // being cut off at the safe zone. The value is handed to
                    // `content` below; see [BottomSheet]'s `content`.
                    //
                    // The drag handle keeps its own placement either way — it is
                    // at the top, where the bottom inset was never reaching it.
                    .windowInsetsPadding(
                        windowInsets.only(WindowInsetsSides.Horizontal)
                    )
            ) {
                dragHandle?.invoke()
                // **Resolved by the consumer, not here.** `asPaddingValues`
                // reads the inset where it is used, which for a `LazyColumn`'s
                // `contentPadding` is the measure pass; calling
                // `calculateBottomPadding()` in composition would recompose the
                // whole of a sheet's content on every frame of the keyboard
                // sliding up.
                SheetParts(this, state).content(
                    windowInsets.only(WindowInsetsSides.Bottom).asPaddingValues()
                )
            }
        }
    }
}

/**
 * The scope a sheet's content is built in: a [ColumnScope], plus [part].
 *
 * Everything a `Column` offers is here unchanged — `weight`, `align` — so
 * content written before this existed reads the same and does the same.
 */
@Stable
@LayoutScopeMarker
interface SheetContentScope : ColumnScope {

    /**
     * A piece of the sheet that is only there once the sheet is big enough.
     *
     * ```kotlin
     * BottomSheet(state) {
     *     part { StopHeader(stop) }
     *     part(from = SheetDetent.Half) { DepartureBoard(stop) }
     * }
     * ```
     *
     * **The part declares when it appears**, rather than the caller re-deciding
     * what the sheet contains on every frame of a drag. A sheet collapsed around
     * a search field is the same sheet as the one showing a header above it, and
     * saying so here keeps the two from being two code paths that have to agree.
     *
     * `from = null` is a part that is always there, which is worth writing anyway:
     * it puts every piece of the sheet in the same shape and makes the ones that
     * come and go legible as the exceptions.
     *
     * It arrives **after the sheet has settled**, not as it passes the detent.
     * A part changes the content's height, `SheetDetent.Expanded` is measured
     * from that height, and moving an anchor under a finger re-pins a drag that
     * is already running — so the change waits for the one moment nothing is
     * being dragged. The cost is a beat between the sheet arriving and the part
     * doing so, which reads as the sheet settling into its new size.
     */
    @Composable
    fun part(from: SheetDetent? = null, content: @Composable ColumnScope.() -> Unit)
}

/**
 * [SheetContentScope] over a real column, with a real sheet to ask.
 *
 * The column is delegated to rather than wrapped, so a part is a direct child of
 * the sheet's own `Column` and `weight` inside one means what it says.
 */
private class SheetParts(
    column: ColumnScope,
    private val state: SheetState,
) : SheetContentScope, ColumnScope by column {

    @Composable
    override fun part(from: SheetDetent?, content: @Composable ColumnScope.() -> Unit) {
        val motion = Theme.motion
        val here = from == null || state.hasSettledAtLeast(from)
        AnimatedVisibility(
            visible = here,
            // The height is the part of this the sheet's anchors can feel, so it
            // takes the slower spec and the fade rides on top of it: a part whose
            // ink arrived before its room did would push the rest of the sheet
            // down through text that was already legible.
            enter = expandVertically(motion.tweenDefault()) + fadeIn(motion.tweenFast()),
            exit = shrinkVertically(motion.tweenDefault()) + fadeOut(motion.tweenFast()),
        ) {
            Column(content = content)
        }
    }
}

/**
 * Sits at [SheetDetent.Hidden]'s offset until the sheet has been measured.
 *
 * **The sheet's own travel is not gated on reduced motion, and that is a
 * decision rather than an oversight.** Round 31 took the amplitude out of the
 * two largest transforms in the library under that preference — the backdrop's
 * scale-back and every overlay's scale-in — and stopped here. A sheet sliding up
 * from the bottom edge *is* what tells you where it came from and which way to
 * push it back; a sheet that appeared in place would be a dialog with a drag
 * handle. The preference asks for less gratuitous movement, not for a component
 * to stop being the thing it is.
 *
 * What the preference does reach is the sheet's spec: `springOrTween` degrades
 * the spring to a tween, so the travel is shorter and never overshoots.
 */
private fun sheetTop(
    state: SheetState,
    floating: Boolean,
    floatInsets: WindowInsets,
    density: Density,
): Int {
    val top = offsetOrHidden(state) - state.drawnOvershoot.roundToInt()
    if (!floating) return top
    // Up by the bottom margin, so the sheet's *bottom* edge lands a margin off
    // the window's rather than on it. Doing this with a padding instead is the
    // mistake worth naming: the box wrapping the surface grows taller, its top
    // stays where the offset put it, and the bottom edge arrives at the window's
    // after all — `FloatingSheetTest` found the sheet's own colour on the last
    // row of the window that way round.
    //
    // **Paid back on the way out**, which is the whole of the report that a
    // floating sheet "stays floating while it closes". The lift was applied at
    // every offset, including the ones between the sheet's smallest size and
    // gone — so a closing sheet kept a margin under it the whole way down and
    // read as a panel drifting off the bottom rather than as one leaving through
    // it. Below the lowest detent that is somewhere to *be*, the sheet is on its
    // way out and nothing is down there but hidden, so the margin goes with it
    // and the sheet lands on the window's edge as it goes.
    //
    // Linear in the visible height rather than timed, because the close is a
    // spring and the two have to agree frame by frame; a sheet dragged down by
    // hand pays it back at exactly the speed of the hand, and one let go pays it
    // back at the speed of the spring, with no second animation to keep in step.
    // Clamped at the top margin, because a floating sheet has a top edge too. A
    // detent that resolves to an offset of zero means "as tall as the window",
    // and a floating sheet that tall is the window less a margin on all four
    // sides, not a panel with its head off the top of the screen.
    return (top - floatingLift(state, floatInsets, density))
        .coerceAtLeast(floatInsets.getTop(density))
}

/**
 * How much of its bottom margin a floating sheet is still keeping under it.
 *
 * The full margin everywhere the sheet is somewhere to *be*, and paid back to
 * nothing between its lowest detent and gone.
 *
 * **Both halves of the geometry have to read this**, which is what the first
 * attempt got wrong. A floating sheet's bottom edge is *seen*, so it is pinned:
 * the surface's height is measured as `window - margin - top` and shrinks as the
 * sheet slides down, rather than the surface translating with a constant height
 * the way an edge sheet's does. Changing [sheetTop] alone therefore moved the top
 * and left the bottom where it was — the sheet closed *faster* and still went out
 * while floating, which is a different answer to the same report rather than an
 * answer to it. Measured on a 900px window with a 24px margin: the bottom edge
 * sat at 875 on every frame of the close, before and after.
 *
 * Taken from the raw offset rather than from [sheetTop], because [sheetTop]
 * subtracts this — and a lift derived from a position that already has the lift
 * in it is a loop, not a fraction.
 */
private fun floatingLift(state: SheetState, floatInsets: WindowInsets, density: Density): Int {
    val margin = floatInsets.getBottom(density)
    if (margin <= 0) return 0
    val container = state.containerHeight
    val floor = state.lowestRestingOffset
    if (floor.isNaN() || container <= floor) return margin
    val raw = offsetOrHidden(state) - state.drawnOvershoot.roundToInt()
    val landed = ((container - raw) / (container - floor)).coerceIn(0f, 1f)
    return (margin * landed).roundToInt()
}

/**
 * Pads the sheet's chrome clear of the top inset, by however much of it the
 * sheet is actually under.
 *
 * Reported from a phone: at full height the drag handle and the header draw
 * under the status bar and the notch. The cause was plain — the sheet's only
 * inset padding was `windowInsets`, which defaulted to
 * [sheetEdges][io.kontour.ui.adaptive.sheetEdges], bottom and horizontal only —
 * and so the fix looked plain too: add the top side and let
 * `Modifier.windowInsetsPadding` sort it out.
 *
 * **It does not sort it out, and the name is why.** `windowInsetsPadding` pads
 * by the whole unconsumed inset wherever the node happens to be; the only thing
 * that reduces it is an *ancestor* having consumed some, through
 * `consumeWindowInsets`. It knows nothing about where the node sits on screen.
 * Measured, with a 40dp top inset on a 1120px canvas at 2x: a sheet at `Half`,
 * whose top edge is at 560, put its content at 640 — a status bar's worth of
 * empty sheet above the handle, in the middle of the screen — and one at `Full`,
 * top edge at 24, put its content at 104 rather than at the 80 where the status
 * bar ends. Both wrong, and the first one visibly.
 *
 * So the overlap is computed here. The sheet's top edge is [sheetTop], the inset
 * band is the top `inset` pixels of the window, and what the chrome owes is the
 * part of the band the sheet is under: `inset - top`, floored at zero. A sheet
 * at `Half` owes nothing and is measured and placed exactly as it was before any
 * of this existed.
 *
 * ### It costs a measurement, and only inside the band
 *
 * The inner content is measured against the constraints less that padding, so a
 * change to it is a re-measure of everything in the sheet — which during a drag
 * would be once a frame, and `SheetFramePressureTest` exists because that class
 * of cost is what sheets get wrong.
 *
 * `coerceIn` is what makes it cheap, and it is the same trick the backdrop's
 * clip shapes use. The padding is pinned at `inset` while the sheet is above the
 * band and at zero while it is below, so it is **constant** for all but the few
 * frames of a drag that cross the band itself — 56px of travel on a phone, out
 * of a window's worth. Outside those frames nothing re-measures at all.
 *
 * Deliberately *not* folded into the `.layout` below that records `sheetHeight`:
 * that block measures the content to find out how tall the content is, and a
 * number that grows by the status bar when the sheet reaches the top would make
 * `SheetDetent.Expanded` resolve higher, which moves the sheet up, which grows
 * the padding. This one sits outside it and the recorded height never sees it.
 */
private fun Modifier.sheetTopInset(
    state: SheetState,
    insets: WindowInsets,
    floating: Boolean,
    floatInsets: WindowInsets,
): Modifier = layout { measurable, constraints ->
    val inset = insets.getTop(this)
    val top = (inset - sheetTop(state, floating, floatInsets, this)).coerceIn(0, inset)
    val placeable = measurable.measure(constraints.offset(vertical = -top))
    val height = (placeable.height + top).coerceAtMost(constraints.maxHeight)
    layout(placeable.width, height) { placeable.place(0, top) }
}

/**
 * How tall the sheet's content may be, given that its top has been shifted down.
 *
 * **The reported defect, and it was a regression from the fix above.** A sheet at
 * full height could not be scrolled to the end of its content: the last rows sat
 * below the window and no amount of dragging reached them. Reported on the
 * catalog's own Display settings panel, which is the one place in the repository
 * where a sheet holds a scroller taller than a phone.
 *
 * [sheetTopInset] moves the content column down by the part of the top inset the
 * sheet is under, and the measurement below it did not know: it measured the
 * content against the **whole** container. So the column was a window tall with
 * its top a status bar down, its bottom edge landed that far *below* the window,
 * and a `verticalScroll` inside sized its viewport to the oversized measurement.
 * At the end of its scroll range the last rows were still off-screen — invisible
 * rather than obviously wrong, because `Surface` clips and the layout reports a
 * shorter height than the placeable it places.
 *
 * So the same space is reserved here. At full height the column then runs from
 * the bottom of the status bar to exactly the bottom of the window, and the
 * bottom inset applied inside it keeps the last row clear of the gesture bar.
 *
 * ### Why a constant rather than the real shift
 *
 * The shift [sheetTopInset] applies is a function of the **live** offset, and a
 * content height that moves with the offset is a re-measure of everything in the
 * sheet on every frame of a drag — the cost `SheetFramePressureTest` exists to
 * catch. `SheetTopGap` is the floor under every anchor, so `max(inset, gap)` is
 * the largest the shift can ever be, and it is a constant.
 *
 * Deriving it from [SheetState.surfaceHeight] would read better and is a cycle:
 * `surfaceHeight` comes from the lowest anchor, `SheetDetent.Expanded`'s anchor
 * comes from `sheetHeight`, and `sheetHeight` comes from the measurement this
 * feeds. The constant closes the loop.
 *
 * ### What this does not fix
 *
 * A sheet whose *tallest* detent is a short one — `Half`, a `peek` — still
 * measures its content against nearly the whole window while only part of it is
 * on screen, so a scroller inside one has the same unreachable tail. That is
 * older than this function and was not what was reported; fixing it needs either
 * the live offset or the cycle above, so it is written down here rather than
 * guessed at.
 */
private fun Density.sheetContentCeiling(
    container: Int,
    insets: WindowInsets,
    floating: Boolean,
    floatInsets: WindowInsets,
): Int {
    if (container <= 0) return container
    val reserved = if (floating) {
        // A floating sheet is inset on all four sides and its surface is sized
        // from both, so the content owes the pair. `floatInsets` rather than
        // `insets`: the caller hands a floating sheet `NoInsets` for the content,
        // because the margin has already taken the window's edges into it.
        floatInsets.getTop(this) + floatInsets.getBottom(this)
    } else {
        maxOf(insets.getTop(this), SheetTopGap.toPx().roundToInt())
    }
    return (container - reserved).coerceIn(1, container)
}

/** No padding at all, for a sheet that is already clear of every edge. */
private val NoInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)

private fun offsetOrHidden(state: SheetState): Int {
    val offset = state.anchoredState.offset
    return if (offset.isNaN()) {
        state.containerHeight.roundToInt()
    } else {
        offset.roundToInt()
    }
}

/**
 * Reports every settle at [SheetDetent.Hidden] to [onHidden], and puts the sheet
 * back if the caller does not accept it.
 *
 * `visible` belongs to the caller, so a drag to the bottom is a *request*. Most
 * callers say yes, but declining is a real case — unsaved changes, a required
 * choice — and the sheet has to come back rather than stay where the drag left
 * it. Without this it sits off the bottom of the window with its scrim still up,
 * so the screen is dimmed and blocked by a sheet nobody can see. A dead
 * `onDismissRequest` produces exactly the same picture, which is how it was
 * found.
 *
 * [stillVisible] is read *after* giving the caller a couple of frames, because
 * `onDismissRequest` sets state that only reaches this composable at the next
 * composition — read immediately, every dismissal would look declined.
 *
 * Frames rather than a timeout, and not only because it is two lines shorter:
 * the question being asked is "has `visible` changed yet", and `visible` changes
 * at a composition. A duration is a guess at how long that takes.
 */
private suspend fun snapshotOfHidden(
    state: SheetState,
    stillVisible: () -> Boolean,
    onHidden: () -> Unit,
) {
    snapshotFlow { state.currentDetent }
        // A sheet cannot be dismissed before it has opened, and `snapshotFlow`
        // hands over the current value first — which for a sheet is `Hidden`,
        // since that is where every one of them starts. Reported, that closes a
        // sheet declared `visible = true` on the frame it appears: the caller is
        // told to dismiss something the user has not seen yet, and `state.show()`
        // has not run. Only a *transition* into `Hidden` is a dismissal.
        .drop(1)
        .filter { it == SheetDetent.Hidden }
        .collect {
            onHidden()
            repeat(SheetDismissalFrames) { withFrameNanos { } }
            if (stillVisible()) state.show()
        }
}

/**
 * How many frames the caller gets to act on a dismissal before it counts as
 * declined.
 *
 * Two: one for the state written by `onDismissRequest` to be applied, one for it
 * to reach this composable. Internal rather than private so a test can step
 * exactly this far; there is nothing here for a caller to tune.
 */
internal const val SheetDismissalFrames = 2
