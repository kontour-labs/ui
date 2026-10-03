package io.kontour.ui.sheet

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import androidx.compose.runtime.RememberObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.takeOrElse
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
import io.kontour.ui.adaptive.ContainerGaps
import io.kontour.ui.adaptive.OverlapInsets
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.adaptive.measuresGaps
import io.kontour.ui.foundation.Surface
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.overlay.BackdropStyle
import io.kontour.ui.overlay.LocalOverlayBack
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayBackState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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

/** What [BottomSheet] and [ModalBottomSheet] take by default. */
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
     * A sheet at full height reaches the top of the screen, so its top corners
     * meet the device's — and a corner against a larger one looks wrong unless it
     * is *concentric* with it: the same centre and, with no gap between them, the
     * same radius. A 34dp corner in a 55dp bezel reads as two unrelated curves.
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
                direction = LocalLayoutDirection.current,
            )
        SheetPresentation.Floating -> Theme.shapes.panel
    }


    /**
     * The inset a sheet's content keeps from its edges: the sides of a
     * [SheetHeader] and of a pinned part, and below them; and above them, when
     * the sheet has no drag handle.
     *
     * One number, so a sheet collapsed to its header or its pinned part shows
     * that part with the same space round it, and every sheet in an app the same
     * space. Asked for as *"when the sheet is collapsed and only the header (or
     * the part that is pinned) is shown, padding should be uniform around that
     * part, and whatever that padding value is should stay throughout all
     * sheets"*.
     *
     * **Under a drag handle, the top is the handle's band instead**,
     * [DragHandleDefaults.Target], and nothing under it adds more. The band is
     * taller, by the handle's own room: at 16dp the handle sat on the content
     * (*"the top of it looks a bit cramped now"*). What the band replaced was a
     * 24dp handle row with the header's own padding and its close button's slack
     * stacked under it, twice as much above a collapsed title as beside it.
     *
     * `Theme.spacing.md`: what the sheet's content has always been inset by at
     * the sides.
     */
    val ContentPadding: Dp
        @Composable @ReadOnlyComposable get() = Theme.spacing.md

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
     * A sheet at full height reaches the top of the screen, so with no top inset
     * its drag handle and its header drew *under* the status bar and the notch. Reported from a phone,
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
    // What a programmatic open or close reads to decide between a slide and a
    // fade. A plain field: nothing recomposes because it was re-stated.
    state.motion = motion
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

    // **The part of each inset that reaches the sheet's container**, and nothing
    // of what lies outside it. An app whose root already made room for the
    // keyboard ends the container where the keyboard begins, and the whole of
    // the keyboard's inset on top of that lifted the sheet twice as far as it
    // needed to go. Everything below reads this one value — the chrome, the
    // float, the content's padding — so they cannot disagree. See `ContainerGaps`.
    val gaps = remember { ContainerGaps() }
    val insets = remember(windowInsets, gaps) { OverlapInsets(windowInsets, gaps) }

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
        remember(insets, inset) {
            WindowInsets(left = inset, top = inset, right = inset, bottom = inset)
                .union(insets)
        }
    } else {
        insets
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
    val minThrow = with(density) { SheetMinThrow.toPx() }
    // Remembered: a new connection on every composition was a nested-scroll node
    // updated on every composition, for an object that says the same thing.
    val scrollConnection = remember(state, settleSpec, flickVelocity, minThrow) {
        state.nestedScrollConnection(settleSpec, flickVelocity, minThrow)
    }

    // **One flick closes it.** A throw of the sheet itself lands where it was
    // aimed — projected half a second on, as iOS does — and at least a detent
    // along; a hard one skips the rest. It used to be Compose's own rule, one
    // detent per flick however hard, so a sheet at full height with a middle
    // detent took two flicks to close — reported as taking "too much effort".
    // Foundation's own snapping fling, which is what Compose's default is built
    // from, so it moves the sheet inside the drag it already holds.
    val handleFlick = with(density) { SheetHandleFlickVelocity.toPx() }
    val fling = remember(state, settleSpec, handleFlick, minThrow) {
        val snap = snapFlingBehavior(
            snapLayoutInfoProvider = SheetSnapping(state, handleFlick),
            decayAnimationSpec = exponentialDecay(),
            snapAnimationSpec = settleSpec,
        )
        // The throw as the finger made it, where the platform's tracker lost it,
        // and no throw at all for a finger that only rolled off the glass: see
        // `SheetFinger`. Handed on as the fling's own velocity, so the snap both
        // aims by it and starts at it.
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float =
                with(snap) { performFling(state.finger.throwOf(initialVelocity, minThrow)) }
        }
    }

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
            // Where the container sits in the window, for `insets`.
            .measuresGaps(gaps)
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
                // The fade that stands in for the slide under reduced motion;
                // 1 at every other moment. See `SheetState.animateTo`.
                .graphicsLayer { alpha = state.presence.value }
                .then(
                    if (draggable) {
                        Modifier
                            .followSheetFinger(state.finger)
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
                windowInsets = if (floating && morph == null) NoInsets else insets,
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
     * What sits between the sheet and the screen behind it, as a
     * [ModalSideSheet]'s does. [ScrimStyle.None] lets the screen behind stay
     * usable and stops the sheet holding focus; anything else keeps both. Read
     * when the sheet opens.
     */
    scrim: ScrimStyle = ScrimStyle.Dimmed,
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
    // Before the first `show()`, which runs before the sheet itself composes.
    state.motion = Theme.motion
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

    // **Thrown away is already gone, as far as the page is concerned.** A release
    // aimed at hidden lets go of the page the moment it is made, not when the
    // sheet has landed: UIKit counts a sheet as dismissed when the finger lets go
    // of it, and a scrim that kept taking taps for the rest of the slide was the
    // "extra split second" reported from a phone. Caught again, or put back
    // because the caller declined, it holds the page again.
    LaunchedEffect(state, host, key) {
        // Three answers, not two: landing hidden is neither — the caller decides
        // what happens next — and a sheet then put back has to read as a change.
        snapshotFlow {
            when {
                state.targetDetent != SheetDetent.Hidden -> SheetHold.Held
                state.isMoving -> SheetHold.Away
                else -> SheetHold.Landed
            }
        }.collect { hold ->
            when (hold) {
                SheetHold.Away -> host.letGo(key)
                SheetHold.Held -> if (showing) host.takeBack(key)
                SheetHold.Landed -> Unit
            }
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            host.show(
                OverlayEntry(
                    key = key,
                    layer = OverlayLayer.Sheet,
                    scrim = scrim,
                    // Trapping unless the screen behind can still be used, for
                    // the reason `ModalSideSheet` gives.
                    trapFocus = scrim != ScrimStyle.None,
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
                    backdrop = if (scrim == ScrimStyle.Dimmed) BackdropStyle.Scale else BackdropStyle.None,
                    dismissOnOutside = dismissible,
                    dismissOnBack = dismissible,
                    dismissLabel = dismissLabel,
                    // The sheet slides itself down and hides the entry when it
                    // has landed — see the `else` branch below.
                    managesOwnExit = true,
                    // The sheet slides on its own spring, so the scrim has to
                    // follow the sheet rather than the host's fade — otherwise
                    // the dimming is gone while the sheet is still on its way
                    // down. This is the worst of the desyncs, because a spring
                    // and a tween disagree most in the middle.
                    visibility = { state.visibleFraction * state.presence.value },
                    // Tell the caller, not just the sheet. This used to only
                    // launch `state.hide()`, so after an outside tap the host
                    // had dropped the entry while the caller's `visible` was
                    // still true — and `LaunchedEffect(visible)` never re-ran,
                    // so the sheet could not be reopened. `onDismissRequest`
                    // flips `visible`, which is what actually closes it.
                    onDismiss = { dismiss() },
                    content = {
                        // Back runs the sheet's own way out: see [FollowBack].
                        LocalOverlayBack.current?.let { back ->
                            FollowBack(state, back, stillWanted = { showing })
                        }
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
            // pulled out from under a sheet that is still moving. It fades
            // there without standing in the way: the page answers from the
            // moment the sheet is closed, not from when it has landed.
            host.letGo(key)
            if (state.isVisible) state.hide()
            host.hide(key)
        }
    }
}

/** Whether a modal sheet is holding the page: see its `letGo`. */
private enum class SheetHold { Held, Away, Landed }

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
    // The room the parts near the top corners keep from them, for the roundest
    // corners this sheet will have: see [SheetCornerRoom].
    val room = rememberSheetCornerRoom(shape, expandedShape.takeIf { morph != null })
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
            val pins = remember(state) { SheetPins() }
            // **Resolved by the consumer, not here.** `asPaddingValues`
            // reads the inset where it is used, which for a `LazyColumn`'s
            // `contentPadding` is the measure pass; calling
            // `calculateBottomPadding()` in composition would recompose the
            // whole of a sheet's content on every frame of the keyboard
            // sliding up.
            val safeArea = if (morph == null) {
                windowInsets.only(WindowInsetsSides.Bottom).asPaddingValues()
            } else {
                // The same, less what the bottom margin still clears —
                // zero while floating, the whole inset as an edge sheet.
                // Resolved by the consumer for the reason above.
                remember(windowInsets, state, floatInsets, morph, density) {
                    BottomInsetTheMarginNoLongerClears(windowInsets, state, floatInsets, morph, density)
                }
            }
            // What the content is handed: the safe area, and the room the
            // pinned parts take, so a list's rows scroll out from behind them.
            val padding = remember(safeArea, pins) { PinnedPadding(safeArea, pins) }
            val contentInset = BottomSheetDefaults.ContentPadding
            // The pinned parts keep clear of the same sides the content does.
            val sides = if (morph == null) {
                Modifier.windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
            } else {
                Modifier
                    .keepsTheFloatingWidth(state, floatInsets, morph)
                    .consumeWindowInsets(windowInsets.only(WindowInsetsSides.Horizontal))
            }
            Layout(
                // Composed in this order on purpose: a `pinned` call in the
                // content registers its part as the content composes, so the
                // blocks after it find the part there in the same pass rather
                // than a frame later.
                contents = listOf(
                    { SheetColumn(state, floating, floatInsets, morph, windowInsets, density, dragHandle, pins, room, padding, content) },
                    { PinnedBlock(pins.top, containerColour, sides, safeArea = null, pins = pins, room = room) },
                    { PinnedBlock(pins.bottom, containerColour, sides, safeArea = safeArea, pins = pins) },
                ),
            ) { (column, top, bottom), constraints ->
                // The blocks first, so the content's padding reads their
                // heights in this pass rather than the next.
                val loose = constraints.copy(minHeight = 0)
                pins.insetForContent = contentInset
                val topBlock = top.firstOrNull()?.measure(loose)
                val bottomBlock = bottom.firstOrNull()?.measure(loose)
                pins.topHeight = (topBlock?.height ?: 0).toDp()
                pins.bottomHeight = if (bottomBlock == null) {
                    0.dp
                } else {
                    (bottomBlock.height - safeArea.calculateBottomPadding().roundToPx())
                        .coerceAtLeast(0)
                        .toDp()
                }
                val sheet = column.first().measure(constraints)
                val handle = pins.handleHeight
                // Sized as the parts rest at that detent, not as they are now. A
                // morphing sheet's bottom block grows by the safe area as the
                // sheet becomes an edge sheet, and a detent that grew with it
                // moved the anchors under a drag: each move snapped the sheet to
                // its target, so a drag down from full height ran ahead of the
                // finger and a drag on the content did not move it at all. The
                // pinned detent is the lowest, where the sheet still floats.
                val bottomAtRest = bottomBlock?.let { block ->
                    val now = safeArea.calculateBottomPadding().roundToPx()
                    val atRest = (safeArea as? BottomInsetTheMarginNoLongerClears)?.floating()?.roundToPx() ?: now
                    block.height - now + atRest
                } ?: 0
                state.pinnedHeight = if (topBlock == null && bottomBlock == null) {
                    0f
                } else {
                    (handle + (topBlock?.height ?: 0) + bottomAtRest).toFloat()
                }
                state.updateAnchors(this)
                layout(sheet.width, sheet.height) {
                    sheet.placeRelative(0, 0)
                    // Where the content starts: under the handle, and under the
                    // status bar while the sheet is (see [sheetTopInset]).
                    val topEdge = sheetTop(state, floating, floatInsets, this@Layout, morph)
                    val inset = windowInsets.getTop(this@Layout)
                    val contentTop = (inset - topEdge).coerceIn(0, inset) + handle
                    // Each in a layer of its own: a bottom block moves on every
                    // frame the sheet does, and a layer moves without redrawing.
                    topBlock?.placeRelativeWithLayer(0, contentTop)
                    if (bottomBlock != null) {
                        // The bottom of what the sheet shows: the window's, for
                        // an edge sheet that hangs below it, and the surface's
                        // own for a floating one, whose bottom edge is seen.
                        val shown = minOf(
                            constraints.maxHeight,
                            (state.containerHeight - topEdge).roundToInt(),
                        )
                        // Never above the content's top, which is where it goes
                        // with the sheet as the sheet leaves.
                        val floor = contentTop + (topBlock?.height ?: 0)
                        val y = maxOf(floor, shown - bottomBlock.height)
                        pins.extension = (y - floor).coerceIn(0, contentInset.roundToPx()).toFloat()
                        bottomBlock.placeRelativeWithLayer(0, y)
                    }
                }
            }
        }
    }
}

/**
 * The sheet's own column: the drag handle, then the caller's content.
 *
 * Its own composable only so the surface can lay it out beside the pinned parts;
 * nothing about it changed when they arrived.
 */
@Composable
private fun SheetColumn(
    state: SheetState,
    floating: Boolean,
    floatInsets: WindowInsets,
    morph: SheetEdgeMorph?,
    windowInsets: WindowInsets,
    density: Density,
    dragHandle: (@Composable () -> Unit)?,
    pins: SheetPins,
    room: SheetCornerRoom,
    padding: PaddingValues,
    content: @Composable SheetContentScope.(PaddingValues) -> Unit,
) {
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
                // Where a sheet this tall rests: the container's top, for one
                // that is or becomes an edge sheet, with its chrome padded below
                // the status bar; a floating one keeps its margins. And how much
                // room that is, so a sheet whose content fills it resolves
                // `Expanded` there rather than a status bar short.
                if (container > 0) {
                    state.fullHeightTop = if (floating && morph == null) (container - window).toFloat() else 0f
                    state.contentCeiling = window.toFloat()
                }
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
        if (dragHandle != null) {
            // The band the handle sits in is the content's top padding: the
            // handle's own height, and at least the sheet's inset. Measured, for
            // where the pinned parts sit and how tall [SheetDetent.pinned] is.
            //
            // Taller by the corners' room, split above and below the handle so
            // it stays centred in the band. Inside the measuring `layout`, so the
            // pinned detent counts it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = BottomSheetDefaults.ContentPadding)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        pins.handleHeight = placeable.height
                        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                    }
                    .padding(vertical = room.top / 2),
                contentAlignment = Alignment.Center,
            ) { dragHandle() }
        } else {
            pins.handleHeight = 0
        }
        // A header knows the band above it is its top padding, and how much
        // further in the corners want it.
        CompositionLocalProvider(LocalSheetBand provides (dragHandle != null), LocalSheetCornerRoom provides room) {
            SheetParts(this, state, pins).content(padding)
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

    /**
     * A piece of the sheet that stays at one edge of what the sheet shows, with
     * the rest of the content passing behind it.
     *
     * ```kotlin
     * val sheet = rememberSheetState(
     *     detents = listOf(SheetDetent.pinned(), SheetDetent.Half, SheetDetent.Expanded),
     * )
     *
     * BottomSheet(sheet) { padding ->
     *     pinned { SearchField(query, placeholder = "Search stops") }
     *     LazyColumn(contentPadding = padding) {
     *         items(stops) { stop -> ListItem { +stop.name } }
     *     }
     * }
     * ```
     *
     * Asked for as a way *"to 'pin' a component to certain parts of the sheet"*:
     * a search bar on the bottom of the sheet, where *"content should be
     * scrollable behind it, and when the sheet is collapsed, only the search bar
     * should show."*
     *
     * **[SheetPin.Bottom] rides the bottom of what the sheet shows** — the window's
     * edge for an edge sheet, the sheet's own for a floating one — above the safe
     * area, at every detent and through every frame of a drag. Pulled below the
     * point where it would cover the handle, it stops, and leaves with the sheet.
     * **[SheetPin.Top] sits under the drag handle**, where the content starts.
     *
     * **Drawn over the content, with the sheet's inset round it** —
     * [BottomSheetDefaults.ContentPadding] at the sides and below, and the
     * handle's band above — so a sheet collapsed to it shows it with the same
     * space at the sides and below. The `PaddingValues` the sheet hands its
     * content grows by the pinned parts' heights, so a `LazyColumn` given it as
     * `contentPadding` scrolls its first and last rows out from behind them.
     * Content that ignores it runs under them.
     *
     * **[containerColour] is what it is drawn on**, the width of the sheet and the
     * inset round it: the sheet's own colour by default, so what scrolls behind it
     * is hidden, and risen off the band a bottom part has the inset in the
     * sheet's colour above it as well. `Color.Transparent` lets the content go
     * under it and show round it — *"the ability for the content to go under the
     * pinned item"* — with the part itself, a search field's own ground, drawn
     * over the rows.
     *
     * [SheetDetent.pinned] is the size that shows the pinned parts and nothing
     * else — the collapsed sheet that is only a search field.
     *
     * Always in the assistive tree, since it is always on screen. Several at one
     * edge stack in the order they were first composed. Declare them anywhere in
     * the content: where one is written does not decide where it is drawn.
     */
    @Composable
    fun pinned(
        to: SheetPin = SheetPin.Bottom,
        containerColour: Color = Color.Unspecified,
        content: @Composable ColumnScope.() -> Unit,
    )
}

/** Which edge of what a sheet shows a [SheetContentScope.pinned] part stays at. */
enum class SheetPin {
    /** Under the drag handle, where the content starts. */
    Top,

    /** At the bottom of what the sheet shows, above the safe area. */
    Bottom,
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
    private val pins: SheetPins,
) : SheetContentScope, ColumnScope by column {

    @Composable
    override fun pinned(to: SheetPin, containerColour: Color, content: @Composable ColumnScope.() -> Unit) {
        // Registered as it composes, not in an effect: the blocks that draw the
        // pinned parts compose after the content, so they find it in this pass.
        // An effect would register it after the frame, and a sheet opened at
        // `SheetDetent.pinned` would show its fallback height for one frame
        // with nothing pinned in it.
        val parts = if (to == SheetPin.Top) pins.top else pins.bottom
        val part = remember(parts) { PinnedPart(parts).also { parts += it } }
        part.content = content
        part.containerColour = containerColour
    }

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
 * A sheet's pinned parts, and the room they take.
 *
 * Written by the content as it composes and by the surface as it measures; read
 * by the blocks that draw the parts and by the padding the content is handed.
 */
@Stable
internal class SheetPins {
    val top: SnapshotStateList<PinnedPart> = mutableStateListOf()
    val bottom: SnapshotStateList<PinnedPart> = mutableStateListOf()

    /** The top block's height. */
    var topHeight by mutableStateOf(0.dp)

    /** The sheet's inset, which the content clears above the bottom block too. */
    var insetForContent by mutableStateOf(0.dp)

    /** The bottom block's height, less the safe area under it. */
    var bottomHeight by mutableStateOf(0.dp)

    /**
     * How much of the sheet's colour the bottom block draws above itself: the
     * inset, once the block has risen far enough off its floor under the handle
     * to have room for it, and less on the way down to it. Written where the
     * block is placed and read where it is drawn, in the same frame.
     */
    var extension by mutableFloatStateOf(0f)

    /**
     * The drag handle's height, from the column's own measure. Plain rather
     * than state: it is written and read in the same measure pass, and nothing
     * outside layout reads it.
     */
    var handleHeight: Int = 0
}

/** One `pinned { }` call, for as long as it is composed. */
internal class PinnedPart(private val list: SnapshotStateList<PinnedPart>) : RememberObserver {
    var content: @Composable ColumnScope.() -> Unit by mutableStateOf({})

    /** What it is drawn on; unspecified is the sheet's colour. */
    var containerColour: Color by mutableStateOf(Color.Unspecified)

    override fun onRemembered() = Unit
    override fun onForgotten() {
        list.remove(this)
    }

    // A composition thrown away still ran the `remember` that added it.
    override fun onAbandoned() {
        list.remove(this)
    }
}

/** The safe area, plus the room the pinned parts take. Read where it is used. */
@Stable
private class PinnedPadding(
    private val safeArea: PaddingValues,
    private val pins: SheetPins,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        safeArea.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = safeArea.calculateTopPadding() + pins.topHeight

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        safeArea.calculateRightPadding(layoutDirection)

    // The bottom block and the inset of colour above it, which the last row
    // clears as well.
    override fun calculateBottomPadding(): Dp = safeArea.calculateBottomPadding() +
        if (pins.bottomHeight > 0.dp) pins.bottomHeight + pins.insetForContent else 0.dp
}

/** Only the bottom of [safeArea]. Read where it is used, as the whole is. */
@Stable
private class BottomOf(private val safeArea: PaddingValues) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateTopPadding(): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateBottomPadding(): Dp = safeArea.calculateBottomPadding()
}

/**
 * The pinned parts at one edge, on the sheet's own colour.
 *
 * The colour runs the full width, past the sides the parts keep clear of, so the
 * content cannot show beside them; a bottom block carries it down through the
 * safe area too. At high contrast a line marks the edge the content passes
 * behind, where the two grounds are the same colour.
 */
@Composable
private fun PinnedBlock(
    parts: SnapshotStateList<PinnedPart>,
    ground: Color,
    sides: Modifier,
    safeArea: PaddingValues?,
    pins: SheetPins,
    room: SheetCornerRoom = SheetCornerRoom.None,
) {
    if (parts.isEmpty()) return
    val edge = contrastEdge()
    val atTop = safeArea == null
    // The sheet's one inset, at the sides and below, so a sheet collapsed to its
    // pinned part shows it with the same space round it: the band under the
    // handle is the space above.
    val inset = BottomSheetDefaults.ContentPadding
    Column(Modifier.fillMaxWidth().semantics { isTraversalGroup = true }) {
        parts.forEachIndexed { index, part ->
            key(part) {
                // Each part on its own ground, so one can let the content show
                // through while another hides it. The part nearest the content
                // carries the block's edge, and the last carries the inset and
                // the safe area under the block.
                val first = index == 0
                val last = index == parts.lastIndex
                val colour = part.containerColour.takeOrElse { ground }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            if (colour.alpha == 0f) return@drawBehind
                            // Above a bottom block that has risen off its floor,
                            // the inset in the part's colour, so the content
                            // passing behind never meets the part edge to edge.
                            // None at the floor, where the handle's band is the
                            // space above it.
                            val above = if (!atTop && first) pins.extension else 0f
                            drawRect(colour, topLeft = Offset(0f, -above), size = Size(size.width, size.height + above))
                            val edgeHere = if (atTop) last else first
                            if (edge != null && edgeHere) {
                                val width = edge.width.toPx()
                                drawRect(
                                    brush = edge.brush,
                                    topLeft = Offset(0f, if (atTop) size.height - width else -above),
                                    size = Size(size.width, width),
                                )
                            }
                        }
                        .then(if (last && safeArea != null) Modifier.padding(BottomOf(safeArea)) else Modifier)
                        .then(sides)
                        // A top block sits under the corners, and moves in from
                        // them as the header does; a bottom block's corners are
                        // square or the floating sheet's own.
                        .padding(start = inset + room.start, end = inset + room.end, bottom = if (last) inset else 0.dp),
                ) {
                    val content = part.content
                    content()
                }
            }
        }
    }
}

/**
 * Sits at [SheetDetent.Hidden]'s offset until the sheet has been measured.
 *
 * **Under reduced motion the sheet does not travel when nobody is moving it.**
 * An open or a close the app asks for — `show()`, `hide()`, `animateTo` — fades
 * the sheet in at its detent and out where it rests (`SheetState.presence`),
 * with the scrim following. It used to slide the full height of the screen on
 * foundation's default spec whatever the reader had asked for, on the argument
 * that the slide says where the sheet came from; a reader who has asked for
 * less movement has answered that argument for themselves.
 *
 * A **drag** is untouched. The sheet follows the finger at every setting,
 * because that movement is the reader's own, and where it settles after the
 * finger lifts is the spec's business, which `springOrTween` already shortens.
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
    // which may be the window's own top, and the status bar under it is
    // `sheetTopInset`'s to pad — so the floating margin up there gives
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

    /** The same while the sheet floats: what it is at a detent below the morph. */
    fun floating(): Dp = with(density) {
        (insets.getBottom(this) - floatInsets.getBottom(this)).coerceAtLeast(0).toDp()
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
    layout(placeable.width, height) {
        state.contentShift = top
        placeable.place(0, top)
    }
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
 * catch. No anchor is above the container's top, so the inset itself is the
 * largest the shift can ever be, and it is a constant.
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
        insets.getTop(this)
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
 * A modal sheet following the back gesture aimed at it, by the movement it
 * already has.
 *
 * The gesture moves the sheet down towards hidden, as a drag would; its scrim
 * reads how much of the sheet is showing and goes with it. Abandoned, the sheet
 * settles back to its detent the way a released drag does. Let go, the modal
 * asks its caller to close, and `hide()` slides it the rest of the way from
 * where it is — or, if the caller keeps it open, it settles back after the
 * frames any declined dismissal gets.
 *
 * Only ever driven under predictive back and for a sheet that may be dismissed:
 * [OverlayBackState.following] is never true otherwise, so a sheet that may not
 * close does not move, and under swipe back no sheet does.
 */
@Composable
private fun FollowBack(state: SheetState, back: OverlayBackState, stillWanted: () -> Boolean) {
    LaunchedEffect(state, back) {
        var from = Float.NaN
        snapshotFlow { if (back.following) back.progress else null }.collect { progress ->
            if (progress == null) {
                from = Float.NaN
                return@collect
            }
            if (from.isNaN()) from = state.offset
            state.followBack(from, progress)
        }
    }
    LaunchedEffect(state, back.cancels) {
        if (back.cancels > 0) state.animateTo(state.currentDetent)
    }
    LaunchedEffect(state, back.commits) {
        if (back.commits == 0) return@LaunchedEffect
        repeat(SheetDismissalFrames) { withFrameNanos { } }
        if (stillWanted()) state.animateTo(state.currentDetent)
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

/**
 * Where a release of the sheet itself settles, for Foundation's snapping fling:
 * straight to the detent [SheetState.detentFlungTo] picks, with no free travel
 * first.
 */
private class SheetSnapping(private val state: SheetState, private val minVelocity: Float) : SnapLayoutInfoProvider {
    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f

    override fun calculateSnapOffset(velocity: Float): Float {
        val from = state.anchoredState.offset
        if (from.isNaN()) return 0f
        val target = state.detentFlungTo(velocity, minVelocity) ?: return 0f
        val at = state.anchoredState.anchors.positionOf(target)
        return if (at.isNaN()) 0f else at - from
    }
}
