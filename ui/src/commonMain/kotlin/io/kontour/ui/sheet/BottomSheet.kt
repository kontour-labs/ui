package io.kontour.ui.sheet

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.adaptive.sheetEdges
import io.kontour.ui.foundation.Surface
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.BackdropStyle
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

object SheetDefaults {
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
    val MaxWidth: Dp = 640.dp

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
 */
@Composable
fun BottomSheet(
    state: SheetState,
    modifier: Modifier = Modifier,
    shape: Shape = Theme.shapes.sheet,
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
     * What the sheet's *content* keeps clear of. The gesture bar, the cutout and
     * **the keyboard**, so a text field in a sheet is not typed at from behind
     * it. The sheet's own surface still reaches the bottom of the window.
     */
    windowInsets: WindowInsets = WindowInsets.sheetEdges,
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val motion = Theme.motion
    val actionsGap = SheetDefaults.ActionsGap

    // Critically damped. A sheet that bounces on arrival looks unweighted, and
    // unlike a button it is carrying content the user is reading.
    //
    // Named, because the nested-scroll connection settles with it too — a fling
    // that starts in the sheet's list has to finish the way one that started on
    // the handle does.
    val settleSpec: FiniteAnimationSpec<Float> = motion.springOrTween(motion.springGentle)

    val overscroll = rememberSheetOverscroll(state, settleSpec)
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
                // Less whatever the sheet has been stretched above its top
                // detent. Purely visual, and read in the layout phase so a
                // stretch never recomposes the sheet's content.
                .offset { IntOffset(0, offsetOrHidden(state) - state.overshoot.roundToInt()) }
                .then(
                    if (draggable) {
                        Modifier
                            .nestedScroll(state.nestedScrollConnection(settleSpec))
                            .anchoredDraggable(
                                state = state.anchoredState,
                                orientation = SheetOrientation,
                                flingBehavior = fling,
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
                windowInsets = windowInsets,
                containerColour = containerColour,
                contentColour = contentColour,
                // A handle on a sheet that cannot be dragged is a lie.
                dragHandle = dragHandle.takeIf { draggable },
                density = density,
                content = content,
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
                            offsetOrHidden(state) -
                                state.overshoot.roundToInt() -
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
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
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
    shape: Shape = Theme.shapes.sheet,
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
    /**
     * What the sheet's *content* keeps clear of. The gesture bar, the cutout and
     * **the keyboard**, so a text field in a sheet is not typed at from behind
     * it. The sheet's own surface still reaches the bottom of the window.
     */
    windowInsets: WindowInsets = WindowInsets.sheetEdges,
    content: @Composable ColumnScope.() -> Unit,
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
                    // A sheet covers part of the screen rather than floating in
                    // the middle of it, so the presenting content recedes as
                    // well as blurring. That is what says "on top of this
                    // screen" rather than "a new screen".
                    backdrop = BackdropStyle.BlurAndScale,
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
    windowInsets: WindowInsets,
    containerColour: Color,
    contentColour: Color,
    dragHandle: (@Composable () -> Unit)?,
    density: androidx.compose.ui.unit.Density,
    content: @Composable ColumnScope.() -> Unit,
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
            // the screen is never seen. The content is measured unbounded either
            // way and cropped to the surface, so the region actually on screen,
            // `offset` to `containerHeight`, is identical to what it was.
            .layout { measurable, constraints ->
                val target = state.containerHeight
                    .coerceAtLeast(0f)
                    .roundToInt()
                    .coerceAtMost(constraints.maxHeight)
                val placeable = measurable.measure(
                    constraints.copy(minHeight = target, maxHeight = target)
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .onGloballyPositioned { coordinates ->
                state.sheetTopInRoot = coordinates.positionInRoot().y
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
                    // Measured as tall as it wants to be, then given only the
                    // room the surface has. `SheetDetent.Expanded` means "as
                    // tall as the content", so the content's own height has to
                    // stay knowable after the surface stopped being sized by it
                    // — otherwise `Expanded` resolves to whatever the sheet is
                    // currently showing and the anchor chases itself.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                        )
                        state.sheetHeight = placeable.height.toFloat()
                        state.updateAnchors(density)
                        val height = placeable.height.coerceAtMost(constraints.maxHeight)
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
                    .windowInsetsPadding(windowInsets)
            ) {
                dragHandle?.invoke()
                content()
            }
        }
    }
}

/** Sits at [SheetDetent.Hidden]'s offset until the sheet has been measured. */
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
