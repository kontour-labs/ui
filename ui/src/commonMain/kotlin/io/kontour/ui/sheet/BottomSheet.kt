package io.kontour.ui.sheet

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.foundation.Surface
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.overlay.BackdropStyle
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.platform.platformDeviceCorners
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.SquircleShape
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.concentricWith
import io.kontour.ui.theme.lerpCorners
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Whether a bottom sheet meets the window's edges or floats clear of them. */
enum class SheetPresentation {
    /**
     * Flush to the bottom and to both sides. A drawer pulled out of the screen.
     *
     * **The default for [ModalBottomSheet]**, and what a floating sheet becomes
     * when it is expanded. The side sheets are always flush to their edge.
     * A modal sheet recedes the page behind it into a frame of its own, and a
     * floating panel in front of that is two frames around one thing; flush to
     * the window, the sheet is the one thing in front and the receded page is
     * plainly behind it. Its bottom corners are square because there is no bottom
     * edge to round.
     */
    Edge,

    /**
     * Lifted off all three edges, with every corner rounded. **The default for
     * [BottomSheet]**, which shares the screen with the page rather than taking it
     * over.
     *
     * A panel *over* the screen rather than a drawer out of it — and the only
     * presentation in which a small sheet looks deliberate: a bar-height sheet
     * flush to the bottom of the window reads as a drawer that failed to open, and
     * the same thing floating reads as a control. A modal sheet can still ask for
     * it; see [Edge] for why that is not the default.
     *
     * Pulled up to its top detent it stops floating: across the last step of its
     * travel it becomes the [Edge] sheet it would otherwise have been. See
     * [SheetEdgeMorph], and `edgeMorph = null` to keep it floating at every size.
     */
    Floating,
}

/**
 * How a [SheetPresentation.Floating] sheet turns into an edge sheet as it is expanded.
 *
 * A floating panel is the right thing at a sheet's smaller sizes and the wrong one
 * at its largest: a sheet pulled up to fill the screen is a screen, and a screen
 * with a margin of background round three sides and rounded corners at the bottom
 * reads as a card that has been stretched rather than as somewhere to be. So as the
 * sheet travels from [from] to [until] it becomes the sheet it would have been as
 * [SheetPresentation.Edge] — its margins go, its corners become the edge sheet's,
 * and what the margin had been keeping clear of the window's insets is handed to
 * the content instead.
 *
 * **The edges it reaches are the ones the edge sheet reaches**, which is what makes
 * it right on every size of device without a rule per size. On a phone that is the
 * bottom and both sides, with top corners concentric with the display's own. On a
 * tablet or a desktop window, where [BottomSheetDefaults.MaxWidth] stops the sheet short
 * of the sides, it is the bottom alone, and the sheet stays wherever its
 * `alignment` put it.
 *
 * **It follows the sheet's position, not a clock.** Held halfway through the step
 * by a finger, the sheet is halfway there; let go, it finishes at the speed of the
 * spring that is carrying it. Nothing is animated separately, so nothing can fall
 * behind.
 *
 * **The content keeps its floating width throughout.** The surface grows out to
 * the edges around it and the content stays where it was, so an expanded sheet's
 * content sits a margin further in than an edge sheet's. That is the price of not
 * re-flowing mid-drag: content whose height depends on its width — a line that
 * wraps — would otherwise change the sheet's height, and its anchors with it,
 * while a finger is on it, and the sheet jumped as it neared the top.
 *
 * @param from Where the morph starts: at or below this the sheet is fully
 *   floating. Null is the resting detent just below [until], so the default is the
 *   last step of the sheet's travel — but never less than [nearTop] of it: a last
 *   step of a few dp, which is what content just taller than `Half` makes, would
 *   put the whole morph in a frame, so the morph starts that far down instead.
 * @param until Where it is complete: at or above this the sheet is an edge sheet.
 *   Null is the sheet's highest detent.
 * @param nearTop For a sheet with no step to morph across — one resting detent,
 *   which is a `ModalBottomSheet` on its defaults — how close to the top its rest
 *   has to be for it to be an edge sheet. Decided by **where the sheet rests**, not
 *   where it is: content tall enough to reach the top is an edge sheet from the
 *   first frame of opening to the last of closing, and short content floats the
 *   whole way, so nothing changes shape as the sheet arrives. The top is where a
 *   full-height sheet actually stops — under the status bar, not the window's edge.
 *   Content that rests within this of it is partly morphed, which is the edge of
 *   the rule and the reason the distance is short. It is also the least distance
 *   a default morph runs over.
 */
@Immutable
class SheetEdgeMorph(
    val from: SheetDetent? = null,
    val until: SheetDetent? = null,
    val nearTop: Dp = DefaultNearTop,
) {
    override fun equals(other: Any?): Boolean =
        other is SheetEdgeMorph && other.from == from && other.until == until && other.nearTop == nearTop

    override fun hashCode(): Int = 31 * (31 * from.hashCode() + until.hashCode()) + nearTop.hashCode()

    override fun toString(): String = "SheetEdgeMorph(from=$from, until=$until, nearTop=$nearTop)"

    companion object {
        /** The last stretch before the top a single-size sheet morphs over. */
        val DefaultNearTop: Dp = 64.dp
    }
}

object BottomSheetDefaults {
    /** The default set of detents: closed, half, or all of it. */
    val Detents: List<SheetDetent> = listOf(
        SheetDetent.Hidden,
        SheetDetent.Half,
        SheetDetent.Expanded,
    )

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
 *     BottomSheet(sheet) {
 *         SheetHeader(Modifier.sheetPeekAnchor()) { +"Perth Underground" }
 *         LazyColumn { … }
 *     }
 * }
 * ```
 *
 * **No `Modifier.align` on the sheet.** This example used to pass
 * `Modifier.align(Alignment.BottomCenter)`, which reads as the natural thing to
 * write inside a `Box` and put the sheet off the bottom of the window: the sheet
 * places itself against the *top* of whatever it is in and moves down by its own
 * offset, a caller's `align` outranks the sheet's own, and bottom-aligning as well
 * counts the sheet's height twice. It fills what it is put in; where it sits
 * horizontally is [alignment]'s job.
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
     * [SheetPresentation.Floating], the default, lifts it off all three edges by
     * [Theme.componentDefaults][io.kontour.ui.theme.ComponentDefaults.sheetFloatingInset] and rounds every corner. Two things follow
     * that are the reason to reach for it. It reads as a *panel over* the screen
     * rather than a drawer pulled out of it, which is what a sheet that is
     * permanently present wants to look like. And it can shrink to something the
     * size of a control without looking like a drawer that failed to open — a
     * search field parked at the bottom of a map, which is the case this was
     * asked for. Expanded, it becomes an edge sheet — see [edgeMorph].
     *
     * [SheetPresentation.Edge] is flush to the bottom and to both sides, with its
     * top corners rounded and its bottom ones square because there is no bottom to
     * round: a drawer out of the window at every size.
     *
     * **Collapsing instead of dismissing is a detent question, not this one.**
     * A sheet's anchors come from its own detent list, so one whose detents are
     * `[height("bar", 64.dp), Half, Expanded]` — with no [SheetDetent.Hidden] —
     * has no anchor to be dragged away to, and stretches past its lowest detent
     * exactly as it does above its top. That works at either presentation; this
     * one only decides what it looks like while it does.
     */
    presentation: SheetPresentation = SheetPresentation.Floating,
    /**
     * How a floating sheet becomes an edge sheet as it is expanded. See
     * [SheetEdgeMorph].
     *
     * On by default: across the last step of its travel a floating sheet grows to
     * the edges an edge sheet would reach and takes [expandedShape]. `null` keeps it
     * floating at every size. Nothing at all for [SheetPresentation.Edge], which is
     * already what the morph arrives at.
     */
    edgeMorph: SheetEdgeMorph? = SheetEdgeMorph(),
    /**
     * Where along the bottom edge the sheet sits, once the window is wider than
     * the sheet.
     *
     * A sheet stops at [BottomSheetDefaults.MaxWidth]. Below that it is the window, and
     * this does nothing at all — a phone never sees it. Above it the sheet is a
     * panel with room either side, and a panel has to sit *somewhere*: centred by
     * default, or against the start or end edge, which is where a sheet belongs on
     * a desktop window whose pointer lives on one side of the screen. Start and
     * end follow the layout direction, so an end-aligned sheet moves to the left
     * in a right-to-left locale with nothing here doing the mirroring.
     *
     * **The cap was written down long before it worked.** `MaxWidth`'s own KDoc
     * has always said a sheet wider than it is a panel to be centred, while the
     * box read `fillMaxWidth().widthIn(max = MaxWidth)` — and `fillMaxWidth`
     * hands its child fixed constraints, so the maximum was coerced straight back
     * up to the window. The sheet spanned every desktop screen it was ever shown
     * on. The fix is the order, `widthIn` then `fillMaxWidth`, and this parameter
     * only exists because the fix left something to decide.
     */
    alignment: OverlayAlignment = OverlayAlignment.Centre,
    /**
     * Where [floatingControls] sit along the sheet's top edge — above its start,
     * its centre or its end.
     *
     * *Of the sheet*, not of the window: the row is exactly as wide as the sheet
     * and moves with it, so an end-aligned sheet with start-aligned controls puts
     * them over the sheet's own left corner. That is only a meaningful thing to
     * say because the sheet now has a width of its own to be relative to.
     *
     * The row used to be `Arrangement.End` with no way to change it, while this
     * parameter's documentation said callers could use an `Arrangement` to say
     * otherwise — which a `RowScope` lambda cannot do, since it is the row that
     * owns its arrangement. Weighted spacers were the only way out.
     */
    floatingControlsAlignment: OverlayAlignment = OverlayAlignment.End,
    shape: Shape = BottomSheetDefaults.shapeFor(presentation),
    /**
     * The shape a floating sheet's [shape] becomes as [edgeMorph] completes: the
     * edge sheet's, concentric with the display's corners where the platform
     * reports them. Corner by corner, and the curve with them, when both are
     * corner-based shapes; otherwise it changes over at the end of the morph.
     */
    expandedShape: Shape = BottomSheetDefaults.shapeFor(SheetPresentation.Edge),
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
    /**
     * Whether the user may put this sheet away by dragging it down.
     *
     * `false` gives the sheet a **floor**: a drag below its lowest resting detent
     * stretches and springs back instead of settling at [SheetDetent.Hidden]. For
     * a sheet that has to be answered rather than escaped.
     *
     * Not the same as leaving [SheetDetent.Hidden] out of the detent list, which
     * was the only way to say this before and says something else: with no anchor
     * down there the sheet's travel simply ends, where this keeps the anchor — so
     * [SheetState.hide] still works, and a finger pushing past the bottom detent
     * meets something that gives and comes back. The app can always close it; the
     * user cannot.
     *
     * [ModalBottomSheet] passes its own `dismissible` down to this, where it also
     * closes the tap outside and the back gesture. Neither of those exists on a
     * plain sheet, so here it is the drag and nothing else.
     */
    dismissible: Boolean = true,
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
     * Above the sheet's end by default; [floatingControlsAlignment] moves them.
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
    val actionsGap = BottomSheetDefaults.ActionsGap
    val floating = presentation == SheetPresentation.Floating
    // Only a floating sheet has anything to morph out of. An edge sheet is what the
    // morph arrives at, so for one this is null and every floating branch below is
    // untouched.
    val morph = if (floating) edgeMorph else null

    // **Written here rather than in [ModalBottomSheet]**, which is where it used
    // to be, and that had two consequences. A plain sheet could not refuse a
    // dismissal at all — `SheetState.userDismissible` had exactly one writer and
    // it was inside the modal — and nothing ever wrote it back to `true`, so a
    // hoisted state that had been a modal sheet's kept the floor for every plain
    // sheet it was handed to afterwards. Both of those were one line in the wrong
    // component.
    //
    // In a `SideEffect` rather than written straight out: this publishes a
    // composition's value to an object that outlives it, and a gesture must not be
    // able to arrive before the composition it belongs to has finished.
    SideEffect { state.userDismissible = dismissible }

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
    val ticker = rememberDetentTicker(FeedbackIntent.Snap)
    // Hidden is not a resting place but a dismissal, and a sheet dragged to where
    // letting go closes it reports what a toast swiped to its dismiss point does:
    // the threshold on the way in, and softer, on the way back.
    val dismiss = rememberDetentTicker(FeedbackIntent.DragThreshold, back = FeedbackIntent.DragThresholdBack)
    LaunchedEffect(state) {
        fun report(detent: SheetDetent) {
            val hidden = detent == SheetDetent.Hidden
            dismiss.at(if (hidden) 1 else 0)
            // The index in the sheet's *own* list, which is reassignable — a
            // sheet whose detents depend on what is in it changes them mid-life,
            // and an index out of a stale copy would tick for a move that did
            // not happen.
            if (!hidden) ticker.at(state.detents.indexOf(detent))
        }
        // A flick is the hand's gesture too: the settle a release starts is
        // waited out, and the detent it lands on reported if it is new — so a
        // sheet flicked open from its peek says so, where it used to be silent
        // because the finger had lifted first. Opened in code, it arms nothing.
        var settle: Job? = null
        snapshotFlow { state.draggedByHand to state.targetDetent }
            .collect { (dragging, detent) ->
                if (dragging) {
                    settle?.cancel()
                    settle = null
                    report(detent)
                } else if (settle == null) {
                    settle = launch {
                        withFrameNanos { }
                        snapshotFlow { state.isMoving }.first { !it }
                        report(state.currentDetent)
                        ticker.reset()
                        dismiss.reset()
                        settle = null
                    }
                }
            }
    }

    // In pixels here, because `SheetState` works in pixels throughout and the
    // nested-scroll callbacks have no density of their own.
    val flickVelocity = with(density) { SheetFlickVelocity.toPx() }
    // Remembered: a new connection on every composition was a nested-scroll node
    // updated on every composition, for an object that says the same thing.
    val scrollConnection = remember(state, settleSpec, flickVelocity) {
        state.nestedScrollConnection(settleSpec, flickVelocity)
    }

    val fling = AnchoredDraggableDefaults.flingBehavior(
        state = state.anchoredState,
        positionalThreshold = BottomSheetDefaults.PositionalThreshold,
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
                .align(alignment.atBottomEdge)
                // Capped, then filled — in that order. The other order is the bug
                // `alignment`'s KDoc records: `fillMaxWidth` fixes the minimum at
                // the window's width, and `widthIn` cannot lower a minimum it is
                // handed.
                .widthIn(max = BottomSheetDefaults.MaxWidth)
                .fillMaxWidth()
                // The float, horizontally. A padding at the sides is enough
                // because nothing here is measured from a side edge; the
                // vertical half of the same margin is not, and is in `sheetTop`.
                //
                // Scaled by how much of the float is left, which is all of it
                // unless the sheet is morphing into an edge sheet — so a padding
                // computed in the layout phase rather than `windowInsetsPadding`,
                // which cannot take a fraction and would recompose to change one.
                .then(if (floating) Modifier.floatingSides(state, floatInsets, morph) else Modifier)
                // Read in the layout phase, so neither the drag nor the stretch
                // above the top detent ever recomposes the sheet's content.
                .offset { IntOffset(0, sheetTop(state, floating, floatInsets, this, morph)) }
                .then(
                    if (draggable) {
                        Modifier
                            .nestedScroll(scrollConnection)
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
                expandedShape = expandedShape,
                floating = floating,
                floatInsets = floatInsets,
                morph = morph,
                // A floating sheet is already clear of the window's edges, so
                // padding its content by them again would inset it twice — and
                // on a gesture-navigation phone that is a bar's worth of dead
                // space under a sheet the size of a search field.
                //
                // Unless it can morph: then the content is handed the real
                // insets, less whatever the margin is still clearing of them,
                // which is all of them until the morph starts.
                windowInsets = if (floating && morph == null) NoInsets else windowInsets,
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
                    // The sheet's own placement, so the row is exactly the sheet's
                    // width and stands exactly over it. It used to repeat the
                    // sheet's modifiers in the same wrong order, and so had the
                    // same defeated cap — spanning the window over a sheet that, once
                    // capped, did not.
                    .align(alignment.atBottomEdge)
                    .widthIn(max = BottomSheetDefaults.MaxWidth)
                    .fillMaxWidth()
                    .onSizeChanged { actionsHeight = it.height }
                    // The sheet's own offset, less this row's height and a gap,
                    // so it rides the top edge wherever the drag leaves it.
                    // Placed after the sheet so it draws over the surface's
                    // shadow rather than under it.
                    .offset {
                        IntOffset(
                            0,
                            sheetTop(state, floating, floatInsets, this, morph) -
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
                    .then(
                        if (floating) {
                            Modifier.floatingSides(state, floatInsets, morph)
                        } else {
                            Modifier.windowInsetsPadding(floatInsets.only(WindowInsetsSides.Horizontal))
                        }
                    )
                    .padding(horizontal = Theme.spacing.md),
                horizontalArrangement = floatingControlsAlignment.asArrangement,
                verticalAlignment = Alignment.Bottom,
                content = floatingControls,
            )
        }
    }
}

/**
 * [OverlayAlignment] as the alignment of something top-aligned and then offset.
 *
 * `Top*`, not `Bottom*`, for the reason the sheet's box gives where it is
 * placed: the box is aligned to the container's top and then moved down by
 * `sheetTop`, and bottom-aligning as well counts the sheet's height twice.
 *
 * `TopStart` and `TopEnd` follow the layout direction, which is the whole reason
 * to spell the parameter in start and end rather than left and right.
 */
private val OverlayAlignment.atBottomEdge: Alignment
    get() = when (this) {
        OverlayAlignment.Start -> Alignment.TopStart
        OverlayAlignment.Centre -> Alignment.TopCenter
        OverlayAlignment.End -> Alignment.TopEnd
    }

/** [OverlayAlignment] as a row's arrangement: which end of the sheet the controls gather at. */
private val OverlayAlignment.asArrangement: Arrangement.Horizontal
    get() = when (this) {
        OverlayAlignment.Start -> Arrangement.Start
        OverlayAlignment.Centre -> Arrangement.Center
        OverlayAlignment.End -> Arrangement.End
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
 * `onDismissRequest` is called once each time the *user* closes the sheet — a
 * drag, a tap outside, a back gesture — and not when the caller sets `visible`
 * to false, so it can do something with a consequence, like popping a back stack.
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
    /**
     * See [BottomSheet]. **An edge sheet by default**, where the non-modal sheet
     * floats: a modal sheet recedes the page behind it, and a floating panel over a
     * receded page is two frames around one thing — reported as not feeling right.
     * `Floating` is still there to ask for.
     */
    presentation: SheetPresentation = SheetPresentation.Edge,
    /** See [BottomSheet]: how a floating sheet becomes an edge sheet as it expands. */
    edgeMorph: SheetEdgeMorph? = SheetEdgeMorph(),
    /**
     * See [BottomSheet]: where the sheet sits once the window is wider than
     * [BottomSheetDefaults.MaxWidth], and nothing at all below it. Read live, so a sheet
     * that is already up follows a window resize.
     */
    alignment: OverlayAlignment = OverlayAlignment.Centre,
    shape: Shape = BottomSheetDefaults.shapeFor(presentation),
    /** See [BottomSheet]: what [shape] becomes once [edgeMorph] completes. */
    expandedShape: Shape = BottomSheetDefaults.shapeFor(SheetPresentation.Edge),
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
    val latestEdgeMorph by rememberUpdatedState(edgeMorph)
    val latestExpandedShape by rememberUpdatedState(expandedShape)
    val latestAlignment by rememberUpdatedState(alignment)
    val latestContainerColour by rememberUpdatedState(containerColour)
    val latestContentColour by rememberUpdatedState(contentColour)
    val latestPaneTitle by rememberUpdatedState(paneTitle)
    val latestDragHandle by rememberUpdatedState(dragHandle)
    val latestDraggable by rememberUpdatedState(draggable)
    // Declared and then dropped on the floor until now: the inner sheet was never
    // given this, so a caller passing `WindowInsets(0)` to say "whatever is above
    // me has already handled all of it" was silently ignored and the sheet padded
    // itself twice.
    val latestWindowInsets by rememberUpdatedState(windowInsets)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    // Drag it shut and the caller finds out, so `visible` and the sheet cannot
    // disagree about whether it is open.
    val showing by rememberUpdatedState(visible)
    val canDismiss by rememberUpdatedState(dismissible)

    LaunchedEffect(state) {
        snapshotOfHidden(state, stillVisible = { showing }) {
            // **Only while the caller still wants it open.** Reaching the bottom
            // is a dismissal when something other than the caller put it there —
            // a drag, a scrim tap, a close inside the sheet. When the caller
            // closed it with `visible = false`, the sheet arriving at the bottom
            // is that request being carried out, not a new one. This used to
            // report every arrival, so a caller who closed the sheet was told a
            // frame later that the user had dismissed it, and a scrim tap was
            // reported twice: once by the scrim, once by the landing. Harmless
            // when the callback is `open = false`; a second pop when it pops a
            // back stack, which is how the Navigation 3 strategy found it.
            //
            // A sheet that cannot be dismissed does not pass the drag on as a
            // request. `snapshotOfHidden` then finds the caller still wants it
            // visible and puts it back — which is the whole of "it does not
            // close", using the mechanism that was already there for a caller
            // declining one.
            if (canDismiss && showing) dismiss()
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
                            edgeMorph = latestEdgeMorph,
                            alignment = latestAlignment,
                            shape = latestShape,
                            expandedShape = latestExpandedShape,
                            containerColour = latestContainerColour,
                            contentColour = latestContentColour,
                            paneTitle = latestPaneTitle,
                            draggable = latestDraggable,
                            // The modal's own, handed down rather than written
                            // into the state from here: the sheet owns the drag,
                            // so the sheet is what tells the state about it.
                            dismissible = canDismiss,
                            dragHandle = latestDragHandle,
                            windowInsets = latestWindowInsets,
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
    expandedShape: Shape,
    floating: Boolean,
    floatInsets: WindowInsets,
    morph: SheetEdgeMorph?,
    windowInsets: WindowInsets,
    containerColour: Color,
    contentColour: Color,
    dragHandle: (@Composable () -> Unit)?,
    density: Density,
    content: @Composable SheetContentScope.(PaddingValues) -> Unit,
) {
    // How far into the edge sheet it is becoming, for the one thing that has to be
    // decided in composition: the shape. Everything else the morph moves is read in
    // the layout phase. `derivedStateOf`, so a drag recomposes this only while the
    // fraction is changing — it is 0 all the way below the morph and 1 all the way
    // above it, and a drag through either recomposes nothing.
    val edgeness by remember(state, morph) {
        derivedStateOf { if (morph == null) 0f else edgeness(state, morph) }
    }
    val drawnShape = remember(shape, expandedShape, edgeness) {
        morphShape(shape, expandedShape, edgeness)
    }
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
                    floatingSurfaceHeight(state, floatInsets, this, window, morph)
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
        shape = drawnShape,
        containerColour = containerColour,
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
                    .sheetTopInset(state, windowInsets, floating, floatInsets, morph)
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
                        // A sheet that can morph measures as the edge sheet it
                        // becomes: a constant either way, so nothing re-measures
                        // as it moves and `Expanded` has one answer.
                        val window = sheetContentCeiling(
                            container = container,
                            insets = windowInsets,
                            floating = floating && morph == null,
                            floatInsets = floatInsets,
                        )
                        // Where a sheet this tall rests, for the single-size morph:
                        // below the status bar rather than at the gap.
                        if (container > 0) state.fullHeightTop = (container - window).toFloat()
                        // And no taller than the sheet can ever be seen. A sheet whose
                        // tallest detent is `Half` shows half a window of content at
                        // most; measured at the window, a list in it had rows past
                        // the bottom of the screen that no scrolling could reach.
                        // Only when every detent is independent of the content — see
                        // [SheetState.tallestFixedTop] — which is what keeps this from
                        // chasing its own tail through `Expanded`.
                        val fixedTop = state.tallestFixedTop()
                        val ceiling = if (fixedTop.isNaN() || container <= 0) {
                            window
                        } else {
                            val top = maxOf(fixedTop.roundToInt(), windowInsets.getTop(this))
                            minOf(window, (container - top).coerceAtLeast(1))
                        }
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
                    .then(
                        if (morph == null) {
                            Modifier.windowInsetsPadding(
                                windowInsets.only(WindowInsetsSides.Horizontal)
                            )
                        } else {
                            // Whatever of the side insets the margin has stopped
                            // clearing, which is none of them while the sheet floats
                            // and all of them once it is an edge sheet. Margin plus
                            // padding never falls short of the inset on the way.
                            Modifier
                                .keepsTheFloatingWidth(state, floatInsets, morph)
                                .consumeWindowInsets(windowInsets.only(WindowInsetsSides.Horizontal))
                        }
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
                    if (morph == null) {
                        windowInsets.only(WindowInsetsSides.Bottom).asPaddingValues()
                    } else {
                        // The same, less what the bottom margin still clears —
                        // zero while floating, the whole inset as an edge sheet.
                        // Resolved by the consumer for the reason above.
                        remember(windowInsets, state, floatInsets, morph, density) {
                            BottomInsetTheMarginNoLongerClears(windowInsets, state, floatInsets, morph, density)
                        }
                    }
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
     * A piece of the sheet that belongs to the sizes big enough to show it.
     *
     * ```kotlin
     * BottomSheet(state) {
     *     part { StopHeader(stop) }
     *     part(from = SheetDetent.Half) { DepartureBoard(stop) }
     * }
     * ```
     *
     * **The part declares which sizes it is for**, rather than the caller
     * re-deciding what the sheet contains on every frame of a drag. A sheet
     * collapsed around a search field is the same sheet as the one showing a
     * departure board under it, and saying so here keeps the two from being two
     * code paths that have to agree.
     *
     * `from = null` is a part that is there at every size, which is worth writing
     * anyway: it puts every piece of the sheet in the same shape and makes the ones
     * further down legible as the ones you have to drag for.
     *
     * ### Nothing appears, and that is the point
     *
     * **A part is laid out in place, at its full height, whatever the sheet is
     * doing.** What hides it is the sheet's own bottom edge: a sheet is a column
     * pinned to the top of a card, the card is only so tall, and a part further
     * down the column is already drawn below what the card shows. Dragging the
     * sheet up uncovers it at exactly the speed of the finger — nothing fades in,
     * nothing is composed on the frame the gesture starts, and nothing can arrive
     * late.
     *
     * Two versions of this appeared instead, and both were reported as appearing: a
     * part composed at the settle, and then a part revealed by a clipped height
     * when the drag committed. The third answer is to stop revealing anything.
     *
     * **So order the content the way it is read.** The sheet hides its content at
     * the bottom, so a part that belongs to a taller detent goes *below* the parts
     * that are always shown — which is the order a header and its details are
     * written in anyway. A gated part written above them is above the fold, and
     * will be visible whatever its `from` says.
     *
     * What [from] does decide is whether the part is in the **assistive tree**: off
     * the bottom of the window it cannot be seen or tapped, and it is not read out
     * either.
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
        // **Nothing here gates the pixels, and that is the third answer to the same
        // report.** The first composed a part only once the sheet had settled at its
        // detent; the second composed it always and revealed it with a clipped
        // height when the sheet's target passed the detent. Both were "the part
        // appears", and both were reported as appearing — "it still just appears
        // partway through the animation ... it needs to be almost as if the content
        // existed all along, as soon as the user starts dragging it".
        //
        // It does exist all along. A sheet is a column pinned to a card's top edge
        // and the card is only so tall, so content further down the column is
        // *already there*, below the sheet's bottom edge, and dragging the sheet up
        // uncovers it at exactly the speed of the finger. There is nothing to
        // animate, nothing to compose on the frame the drag starts, and nothing
        // that can arrive late — which is the whole of what was asked for.
        //
        // What [from] still decides is whether the part is in the **assistive
        // tree**. Off the bottom of the window a part cannot be seen or tapped, and
        // it should not be read out either; `clearAndSetSemantics` rather than
        // `hideFromAccessibility` for the reason `OverlayHost` writes down where it
        // hides a dimmed page — the flag leaves the node findable, and measured, it
        // did.
        val reachable = from == null || state.willReach(from)
        Column(
            modifier = if (reachable) Modifier else Modifier.clearAndSetSemantics {},
            content = content,
        )
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
    morph: SheetEdgeMorph? = null,
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
    //
    // The clamp is scaled by the morph as well: an edge sheet's top is its anchor,
    // which already stops `SheetTopGap` short of the window, and the status bar
    // under it is `sheetTopInset`'s to pad — so the floating margin up there gives
    // way at the same rate as the other three.
    val kept = 1f - edgeness(state, morph)
    return (top - floatingLift(state, floatInsets, density, morph))
        .coerceAtLeast((floatInsets.getTop(density) * kept).roundToInt())
}

/**
 * How tall a floating sheet's surface is — and why it stops shrinking.
 *
 * Above the lowest detent that is somewhere to *be*, the bottom edge is pinned a
 * margin off the window's and the height follows the top: that is what a floating
 * sheet is, and it is unchanged here.
 *
 * Below it, the height is frozen at what it was *at* that detent, and this is the
 * fix for a report that survived a round: a floating sheet still "doesn't get
 * dragged out of the screen". The two halves of the arithmetic were cancelling.
 * [sheetTop] places the surface at `raw - lift` and the height was measured as
 * `window - lift - top`, so the bottom edge came out at `window - lift` — with the
 * offset gone from the expression entirely. The top descended, the bottom stayed
 * pinned, and the sheet shrank into the window's edge rather than leaving through
 * it. [floatingLift]'s own note records finding the bottom edge at 875 on every
 * frame of a close, before and after the fix that was supposed to move it.
 *
 * With the height constant below the floor, the only thing still moving is
 * [sheetTop] — so the sheet translates down and out exactly as an edge sheet
 * does, keeping its side margins and all four rounded corners, both of which are
 * independent of this arithmetic.
 *
 * The two branches agree exactly at the floor, where the lift is full and the
 * frozen height is `window - margin - (floor - margin)`, so nothing jumps as the
 * sheet crosses it.
 */
private fun floatingSurfaceHeight(
    state: SheetState,
    floatInsets: WindowInsets,
    density: Density,
    window: Int,
    morph: SheetEdgeMorph?,
): Int {
    val floor = state.lowestRestingOffset
    val raw = offsetOrHidden(state) - state.drawnOvershoot.roundToInt()
    val following = window -
        floatingLift(state, floatInsets, density, morph) -
        sheetTop(state, true, floatInsets, density, morph)
    if (floor.isNaN() || raw <= floor) return following.coerceIn(0, window)
    // At the floor the lift is the whole margin, so the top was `floor - margin`
    // — clamped, because a floating sheet has a top edge too and a detent that
    // resolves to an offset of zero means "as tall as the window less a margin on
    // all four sides".
    val margin = floatInsets.getBottom(density)
    val settledTop = (floor.roundToInt() - margin).coerceAtLeast(floatInsets.getTop(density))
    return (window - margin - settledTop).coerceIn(0, window)
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
private fun floatingLift(
    state: SheetState,
    floatInsets: WindowInsets,
    density: Density,
    morph: SheetEdgeMorph? = null,
): Int {
    val margin = floatInsets.getBottom(density)
    if (margin <= 0) return 0
    // Given back at the top of the travel as well as at the bottom: an edge sheet
    // stands on the window's edge, and a floating one morphing into it lands there
    // at the rate it morphs. The two never overlap — the pay-back below is below
    // the lowest resting detent, and the morph is above the one under the top.
    val kept = 1f - edgeness(state, morph)
    val container = state.containerHeight
    val floor = state.lowestRestingOffset
    if (floor.isNaN() || container <= floor) return (margin * kept).roundToInt()
    val raw = offsetOrHidden(state) - state.drawnOvershoot.roundToInt()
    val landed = ((container - raw) / (container - floor)).coerceIn(0f, 1f)
    return (margin * landed * kept).roundToInt()
}

/**
 * How far a floating sheet has become an edge sheet: 0 while it floats, 1 once it
 * is one. See [SheetEdgeMorph]. Read from the raw offset, like the lift, and in
 * whichever phase asks — layout for the geometry, composition for the shape.
 */
private fun edgeness(state: SheetState, morph: SheetEdgeMorph?): Float {
    if (morph == null) return 0f
    val raw = offsetOrHidden(state) - state.drawnOvershoot.roundToInt()
    return state.edgeMorphFraction(morph, raw.toFloat())
}

/**
 * [shape] on its way to [expanded], [fraction] of the way there.
 *
 * Corner by corner through `lerpCorners`, and the squircle's curve with them when
 * both have one — the edge sheet's may be the display's own, and a curve that
 * changed over at the end would be a step in an otherwise continuous morph. A
 * shape that is not corner-based cannot be interpolated and changes over once the
 * morph is complete.
 */
private fun morphShape(shape: Shape, expanded: Shape, fraction: Float): Shape = when {
    fraction <= 0f -> shape
    fraction >= 1f -> expanded
    shape is CornerBasedShape && expanded is CornerBasedShape -> {
        val corners = shape.lerpCorners(expanded, fraction)
        if (shape is SquircleShape && expanded is SquircleShape && corners is SquircleShape) {
            corners.withSmoothing(shape.smoothing + (expanded.smoothing - shape.smoothing) * fraction)
        } else {
            corners
        }
    }
    else -> shape
}

/**
 * A floating sheet's side margin, less however much of it the morph has given away.
 *
 * Physical sides, because window insets are: a cutout on the left is on the left in
 * either layout direction. And the insets are consumed for what is inside, as the
 * `windowInsetsPadding` this replaced consumed them, so content that pads itself by
 * the safe area is not inset a second time.
 */
private fun Modifier.floatingSides(
    state: SheetState,
    floatInsets: WindowInsets,
    morph: SheetEdgeMorph?,
): Modifier = layout { measurable, constraints ->
    val kept = 1f - edgeness(state, morph)
    val left = (floatInsets.getLeft(this, layoutDirection) * kept).roundToInt()
    val right = (floatInsets.getRight(this, layoutDirection) * kept).roundToInt()
    val placeable = measurable.measure(constraints.offset(horizontal = -(left + right)))
    layout(
        constraints.constrainWidth(placeable.width + left + right),
        constraints.constrainHeight(placeable.height),
    ) { placeable.place(left, 0) }
}.consumeWindowInsets(floatInsets.only(WindowInsetsSides.Horizontal))

/**
 * A morphing sheet's content, held at the width it had while the sheet floated.
 *
 * The surface grows around it: the padding on each side is exactly what the surface
 * has grown by there, so the content stands still while the margins close up. It
 * used to reflow at every width the morph passed through, and content whose height
 * depends on its width — a line of text that wraps — changed the sheet's height,
 * and the anchors with it, in the middle of a drag. That was half of the reported
 * "snap" near the top.
 *
 * The same rounding as [floatingSides], so surface and content agree to the pixel.
 * The floating margin is at least the window's inset on each side, so the content
 * is clear of a cutout at every fraction without asking the insets again.
 */
private fun Modifier.keepsTheFloatingWidth(
    state: SheetState,
    floatInsets: WindowInsets,
    morph: SheetEdgeMorph?,
): Modifier = layout { measurable, constraints ->
    val kept = 1f - edgeness(state, morph)
    val leftMargin = floatInsets.getLeft(this, layoutDirection)
    val rightMargin = floatInsets.getRight(this, layoutDirection)
    val left = (leftMargin - (leftMargin * kept).roundToInt()).coerceAtLeast(0)
    val right = (rightMargin - (rightMargin * kept).roundToInt()).coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(horizontal = -(left + right)))
    layout(
        constraints.constrainWidth(placeable.width + left + right),
        constraints.constrainHeight(placeable.height),
    ) { placeable.place(left, 0) }
}

/**
 * The bottom inset a morphing sheet hands its content: none of it while the sheet
 * floats, since its margin clears it, and all of it once it is an edge sheet.
 *
 * A `PaddingValues` rather than a number, for the reason the edge sheet hands out
 * `asPaddingValues()`: it is resolved where it is used, which for a `LazyColumn`
 * is its measure pass, so the content is re-measured as it changes and never
 * recomposed. The margin is scaled by the morph alone and not by the pay-back on
 * the way out, so a closing sheet does not re-measure its content every frame.
 */
@Stable
private class BottomInsetTheMarginNoLongerClears(
    private val insets: WindowInsets,
    private val state: SheetState,
    private val floatInsets: WindowInsets,
    private val morph: SheetEdgeMorph?,
    private val density: Density,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateTopPadding(): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateBottomPadding(): Dp = with(density) {
        val kept = 1f - edgeness(state, morph)
        val margin = (floatInsets.getBottom(this) * kept).roundToInt()
        (insets.getBottom(this) - margin).coerceAtLeast(0).toDp()
    }
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
    morph: SheetEdgeMorph?,
): Modifier = layout { measurable, constraints ->
    val inset = insets.getTop(this)
    val top = (inset - sheetTop(state, floating, floatInsets, this, morph)).coerceIn(0, inset)
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
 * ### A sheet whose tallest detent is a short one
 *
 * Handled by the caller, not here: a sheet whose detents are all independent of
 * its content — a bar and `Half`, say — is measured at the visible height of the
 * tallest of them, from [SheetState.tallestFixedTop]. It used to be measured
 * against nearly the whole window while only half of it was ever on screen, so a
 * list inside had a tail no scrolling reached; reported of a floating sheet, which
 * is the presentation that short top detents come with. There is no cycle, since
 * those detents' offsets do not depend on the content. A sheet with `Expanded` in
 * its list is left as it was: it grows to its content, so its scroller already
 * reaches its end at `Expanded`.
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
