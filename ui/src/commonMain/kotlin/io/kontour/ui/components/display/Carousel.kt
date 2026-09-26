package io.kontour.ui.components.display

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Surface
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** What a [Carousel] takes by default. */
object CarouselDefaults {
    /**
     * How far into the next page a drag has to reach before letting go commits.
     *
     * A quarter, where Compose's own snapping is a half. A half is the right
     * answer for a *list*, where the question is which item is mostly on screen;
     * a carousel is a stack of cards being turned over, and half a full-width
     * card is a long way to drag to find out whether the gesture took. Reported
     * as the detent threshold being too high.
     *
     * Direction is read from the list rather than assumed, because position
     * alone cannot tell you: a drum a third of the way between two pages is
     * either a third forward from the first or two thirds back from the second,
     * and which one it is decides which way a quarter counts.
     *
     * **It governs a trackpad too**, which is the other half of the same report
     * and turned out not to need anything of its own. A sideways two-finger push
     * already settles: traced, the offset sat at 73px for about ninety
     * milliseconds after the last notch and then animated back to zero on its
     * own, because the platform runs the fling behaviour once a wheel gesture
     * goes quiet. Snapping *code* here was written, measured against its own
     * removal, found to change nothing, and deleted. What a push actually
     * inherits from this file is the threshold above — and a push that ends
     * short of it lands back where it began, which is what reads as a carousel
     * refusing to move.
     */
    const val SnapThreshold: Float = 0.25f

    /**
     * How much of the next page [CarouselStyle.Hero] shows beside the current one:
     * none, unless a theme says otherwise.
     *
     * A getter onto `ComponentDefaults.carouselHeroPeek` rather than a constant,
     * because how much of the next picture to show is a brand's decision and not
     * a fact about carousels — a row of wide landscapes wants less of it than a
     * row of book covers.
     */
    val HeroPeek: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.carouselHeroPeek
}

/**
 * Which page a [Carousel] is on, and how to get to another one.
 *
 * Wraps a `LazyListState` rather than replacing it, so anything that already
 * works on one — `animateScrollToItem`, scroll position restoration — still
 * does.
 */
@Stable
class CarouselState internal constructor(
    val listState: LazyListState,
    private val pageCountOf: () -> Int,
) {
    /**
     * The page currently settled, or the one a drag is closest to.
     *
     * Derived from the scroll *offset*, not from `firstVisibleItemIndex` alone.
     * That index changes the instant a single pixel of the next page appears, so
     * an indicator driven by it flips forward at the very start of a drag and
     * then sits there while the user is still looking at the previous page.
     */
    val currentPage: Int by derivedStateOf {
        val info = listState.layoutInfo
        val viewportCentre = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo
            .minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - viewportCentre) }
            ?.index
            ?: listState.firstVisibleItemIndex
    }

    /**
     * Where the carousel is between pages, as a page number with a fraction.
     *
     * `2.5` is halfway from the third page to the fourth. [currentPage] is this
     * rounded to whatever is nearest the middle of the viewport, and that is the
     * right answer for anything that has to name a page; this is for anything
     * that has to *draw* the space between two — the worm indicator, and nothing
     * else so far.
     *
     * The pitch is measured from two real items rather than assumed, because a
     * carousel's `pageSpacing` is part of the distance between pages and the
     * state does not know what the caller asked for.
     */
    val pagePosition: Float by derivedStateOf {
        val visible = listState.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull() ?: return@derivedStateOf 0f
        val pitch = visible.getOrNull(1)?.let { (it.offset - first.offset).toFloat() }
            ?: first.size.toFloat()
        if (pitch <= 0f) first.index.toFloat() else first.index + (-first.offset) / pitch
    }

    /** How many pages there are, from the lambda [rememberCarouselState] was given. */
    val pageCount: Int
        get() = pageCountOf().also {
            // Checked here rather than at each reader, because both `Carousel`
            // and `PageIndicator` take the count from this one property and one
            // of them sizes a `FloatArray` with it — so a negative count reached
            // the user as `NegativeArraySizeException`, which names neither the
            // carousel nor the lambda that produced the number.
            require(it >= 0) {
                "Carousel's pageCount lambda returned $it. A page count cannot be " +
                    "negative; this is usually a subtraction against a collection that " +
                    "has not loaded yet."
            }
        }

    /** Moves to [page] at once, clamped to the pages there are. */
    suspend fun scrollToPage(page: Int) {
        listState.scrollToItem(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }

    /** Slides to [page], clamped to the pages there are. What a dot or an arrow does. */
    suspend fun animateScrollToPage(page: Int) {
        listState.animateScrollToItem(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }
}

/**
 * Remembers a [CarouselState]. [pageCount] is read whenever it is needed, so a
 * carousel whose pages load in keeps its count current without a new state.
 */
@Composable
fun rememberCarouselState(pageCount: () -> Int): CarouselState {
    val listState = rememberLazyListState()
    return remember(listState) { CarouselState(listState, pageCount) }
}

/**
 * A row of pages, one at a time, that snaps.
 *
 * ```kotlin
 * val carousel = rememberCarouselState { photos.size }
 *
 * Carousel(carousel, contentDescription = "Stop photos") { page ->
 *     AspectRatioBox(16f / 9f) { Image(photos[page]) }
 * }
 * PageIndicator(carousel, onPageClick = { carousel.animateScrollToPage(it) })
 * ```
 *
 * ### The swipe is a shortcut, not the route
 *
 * A drag is invisible, has no keyboard equivalent, and is unreachable for anyone
 * who cannot make a sustained one. So the carousel carries **previous** and
 * **next** as custom accessibility actions, and the pages announce which one of
 * how many is showing.
 *
 * That still leaves a sighted mouse user with nothing to click, which is what
 * [PageIndicator] is for — give it `onPageClick` and its dots become targets.
 * A carousel with a decorative indicator and no arrows is operable by exactly
 * one input method, and the app has four.
 *
 * @param contentDescription What the set of pages *is* — "Stop photos". Required:
 *   "1 of 5" without it is a count of nothing.
 * @param pageSpacing The gap between pages. Part of the snap distance, so it
 *   belongs here rather than in the caller's own padding — and in
 *   [CarouselStyle.Hero] it is the gap between the two boxes, which is the one
 *   place in the layout a reader can see it.
 * @param peek How much of the next page [CarouselStyle.Hero] shows beside the
 *   current one, and read by nothing else. **None by default**, from
 *   `ComponentDefaults.carouselHeroPeek`.
 *
 *   **Zero is one page at a time**: the frame holds the hero and nothing else, and
 *   [pageSpacing] opens between the two boxes only while a swipe is in flight —
 *   there being nothing for a gap to separate at rest. Every other value reserves
 *   the gap *and* the peek, so the frame is `[hero][gap][peek]`.
 *
 *   **A third of the carousel is the ceiling worth designing to** — past that the
 *   "next" box competes with the page being looked at, and a hero carousel with
 *   two heroes in it is a two-column list. It is not clamped, and cannot sensibly
 *   be: the strip's own slots are a peek apart, so a peek narrowed after the fact
 *   would put every box out of step with the slot it is measured in. What an
 *   over-wide one costs is the hero, down to a floor of a third of the frame.
 * @param parallax How much of the strip's travel the *content* of a page being
 *   left behind keeps, from `0` for none of it to `1` for all of it. Read only by
 *   [CarouselStyle.Hero].
 *
 *   The box of a page on its way out is pinned to the frame's start and closes
 *   over its own content — so at `0` the picture holds still and is taken away,
 *   and at `1` it travels with the strip and slides out under a shrinking window.
 *   Around `0.2` to `0.3` gives it a drift behind the closing edge without it
 *   arriving from off screen. **The page arriving drifts by the same share**, in
 *   from the end as its box opens, so both pictures move together; at `0` it holds
 *   still, end-aligned in a box whose leading edge wipes across it.
 *
 *   **Ignored under reduced motion**, which is the whole of what that preference
 *   can sensibly take away here — boxes trading width is the style, and a picture
 *   drifting underneath the one closing over it is the embellishment on top.
 */
@Composable
fun Carousel(
    state: CarouselState,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: CarouselStyle = CarouselStyle.Slide,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = Theme.spacing.xs,
    peek: Dp = CarouselDefaults.HeroPeek,
    parallax: Float = 0f,
    previousLabel: String = Theme.strings.previous,
    nextLabel: String = Theme.strings.next,
    content: @Composable (page: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val count = state.pageCount
    val current = state.currentPage
    val position = Theme.strings.itemOfCount(current, count)
    val direction = LocalLayoutDirection.current
    val hero = style == CarouselStyle.Hero
    val density = LocalDensity.current
    val gapPx = with(density) { pageSpacing.toPx() }
    val peekPx = with(density) { peek.coerceAtLeast(0.dp).toPx() }

    // Coerced rather than required: this is the kind of number a caller animates or
    // reads from a setting, and a carousel that throws at `1.02` on one frame of a
    // spring is worse than one that draws `1`.
    val drift = if (Theme.motion.reduceMotion) 0f else parallax.coerceIn(0f, 1f)

    /**
     * How far apart the slots are, which for a hero carousel is **less than a
     * slot**.
     *
     * A page's slot is the whole frame, and the strip is compressed by a peek's
     * width — a negative spacing — so consecutive slots start `V - S` apart, which
     * is the pitch `L + G` the geometry needs. Two things fall out of it and both
     * were fought for the length of a rewrite.
     *
     * A box can never be wider than the slot it is measured in. `Placeable.width`
     * is the measured size *coerced into the constraints*, and a layout that
     * reports more than it was given is centred inside what it was given — so with
     * slots a hero wide, the last page's full-frame box came out 120px narrower
     * and 60px to the left. Measured, and the reason the slot is not `L`.
     *
     * And the last page can reach the start of the viewport with no extra padding
     * at the end of the strip. The maximum scroll is `(N-1)` pitches exactly, which
     * is where the last slot's left edge lands — where uniform `L`-wide slots fall a
     * gap and a peek short and the carousel spends every fling at the end fighting a
     * position it cannot reach.
     */
    val spacing = when {
        !hero -> pageSpacing
        // Nothing to separate at rest, so the gap is not reserved in the frame: the
        // slots are a whole frame *plus* a gap apart, which puts the next page
        // entirely off screen until a swipe brings it in. One page at a time.
        peek <= 0.dp -> pageSpacing
        else -> -peek
    }

    // One tick per page crossed **under a finger**, and none for a page reached
    // any other way.
    //
    // A carousel's pages are detents in the strictest sense — the card snaps to
    // one and rests there — and the eye is on the card rather than on a counter,
    // which is the case the policy names. What it must not do is report a page
    // arrived at by `scrollToPage`: the accessibility actions, the indicator's
    // dots and an autoplay all call it, and a carousel that buzzes when a dot is
    // clicked is buzzing for something the reader is already watching.
    //
    // So the same distinction a sheet draws, from the same kind of signal: the
    // list's own interaction source for a touch scroll, and the pointer drag's
    // for the desktop path, which does not go through the list at all.
    val dragInteractions = remember { MutableInteractionSource() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.listState, dragInteractions) {
        var held = 0
        merge(
            state.listState.interactionSource.interactions,
            dragInteractions.interactions,
        ).collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> held++
                is DragInteraction.Stop, is DragInteraction.Cancel -> held--
            }
            dragging = held > 0
        }
    }
    val ticker = rememberDetentTicker(FeedbackIntent.Snap)
    LaunchedEffect(state) {
        // The fling a release starts is still the hand's gesture, as a wheel's and
        // a knob's spin are: a short flick onto the next card — the commonest
        // thing anyone does to a carousel — used to say nothing, because the
        // finger was already up when the page changed. So a release waits for the
        // settle to finish and reports where it landed, if that is a page it had
        // not already reported. A page changed in code arms nothing and is silent.
        var settle: Job? = null
        snapshotFlow { dragging to state.currentPage }.collect { (byHand, page) ->
            if (byHand) {
                settle?.cancel()
                settle = null
                ticker.at(page)
            } else if (settle == null) {
                settle = launch {
                    // A frame for the fling to begin, then the end of it.
                    withFrameNanos { }
                    snapshotFlow { state.listState.isScrollInProgress }.first { !it }
                    ticker.at(state.currentPage)
                    ticker.reset()
                    settle = null
                }
            }
        }
    }

    // **The first and last page refuse to go further, and say nothing about it.**
    //
    // There was a whole `NestedScrollConnection` here for one report — the part
    // of a drag the list declined, which is the only signal that separates "the
    // finger asked for more page and got none" from "page one is merely
    // showing". It went, and so did the connection, which existed for nothing
    // else — end stops are for a range's ends, and a strip that will not move
    // under a finger has already said so by not moving. The per-page tick above
    // stays — a card snapping to the next one while the eye is on the card is
    // exactly what a detent is.

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            // Draggable with a pointer, not only scrollable with a finger.
            //
            // A `LazyRow` answers touch and the wheel, and on desktop that is
            // all — dragging a list with the mouse is not a thing desktops do.
            // A carousel is the exception: it is a stack of cards, and grabbing
            // one and pulling it aside is the only gesture anybody tries.
            //
            // Outside the list in the modifier chain, so it is the list's
            // ancestor: a child gets the main pointer pass first, which leaves
            // every touch drag to the list's own scrolling and this seeing only
            // what it declined.
            .then(
                if (enabled) {
                    Modifier.draggable(
                        state = rememberDraggableState { delta ->
                            state.listState.dispatchRawDelta(-delta)
                        },
                        orientation = Orientation.Horizontal,
                        interactionSource = dragInteractions,
                        // `currentPage` is the page nearest the viewport centre,
                        // so this settles on whichever one the drag left showing
                        // — the same answer the fling behaviour would give.
                        onDragStopped = { scope.launch { state.animateScrollToPage(state.currentPage) } },
                    )
                } else {
                    Modifier
                }
            )
            .semantics {
                isTraversalGroup = true
                this.contentDescription = contentDescription
                stateDescription = position
                customActions = listOf(
                    CustomAccessibilityAction(previousLabel) {
                        if (current > 0) {
                            scope.launch { state.animateScrollToPage(current - 1) }
                            true
                        } else {
                            false
                        }
                    },
                    CustomAccessibilityAction(nextLabel) {
                        if (current < count - 1) {
                            scope.launch { state.animateScrollToPage(current + 1) }
                            true
                        } else {
                            false
                        }
                    },
                )
            },
        state = state.listState,
        userScrollEnabled = enabled,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        // Snapping rather than free scroll: a carousel that stops between two
        // pages is showing neither, and the indicator below it is then lying
        // whatever it says.
        flingBehavior = firmSnapFlingBehaviour(state.listState),
    ) {
        items(count) { page ->
            // **The slot is always the same width**, whatever the box inside it is
            // doing. The `LazyRow` measures the pitch from the items it places, and
            // `CarouselState.pagePosition` measures it again from two of them, so an
            // item that reported the width of a shrinking box would move the
            // arithmetic the shrinking is derived from.
            Box(Modifier.fillParentMaxWidth()) {
                if (hero) {
                    Box(
                        Modifier
                            .heroPage(
                                state = state,
                                page = page,
                                count = count,
                                gap = gapPx,
                                peek = peekPx,
                                drift = drift,
                                shape = Theme.shapes.container,
                            )
                    ) { content(page) }
                } else {
                    content(page)
                }
            }
        }
    }
}

/** How a [Carousel] gets from one page to the next. */
enum class CarouselStyle {
    /**
     * The pages are a strip, and the strip slides under the viewport.
     *
     * What a carousel is by default and what a finger expects: the outgoing page
     * leaves to one side at exactly the speed the incoming one arrives from the
     * other, and halfway through you are looking at half of each.
     */
    Slide,

    /**
     * Two boxes side by side, trading width as you swipe.
     *
     * The page you are on is most of the frame; the next one is a narrow box
     * beside it with a gap between them, and a swipe hands the width from one to
     * the other. Material's hero carousel, and the arrangement a row of pictures
     * wants: what the eye follows is a photograph getting bigger, with the next
     * one already there to say the row continues.
     *
     * Right for pages that are **one picture each**. Wrong for pages with
     * structure — a form, a list — because a page is measured once at the hero's
     * width and masked down to whatever it currently has, so its content is
     * cropped rather than reflowed. That is the trade, and it is deliberate: a
     * page that re-laid-out sixty times a second while a finger moved is what a
     * carousel of text would cost.
     *
     * `peek` is how much of the next page shows; the gap between the two boxes is
     * `pageSpacing`. Everything else — the gesture, the snap, the page the
     * indicator names, what a screen reader is told — is the same as [Slide].
     */
    Hero,
}

/**
 * One hero page's box: how wide it is, and how far it sits from its own slot.
 *
 * Both in pixels, and both derived from `d` — how many pages ahead of the
 * viewport this one is, fractionally. Null for a page with no box at all.
 */
private class HeroBox(val width: Float, val shift: Float)

/**
 * The whole of the hero layout, as a table.
 *
 * With `V` the frame, `S` the peek, `G` the gap, `L` the hero and `P = L + G` the
 * pitch — which is the pitch the `LazyRow` already uses, because a slot is the whole
 * frame and the arrangement pulls the next one back into it by whatever the frame
 * reserves. `L` and `P` come from [heroMetrics], which is also where a zero peek is
 * dealt with:
 *
 * | `d` | width | shift |
 * |---|---|---|
 * | `d < 0` | `L + d·P` | `-d·P` |
 * | `d >= 0` | `min(L, V - d·P)` | `0` |
 * | `d >= 0`, last page | `V - d·P` | `0` |
 *
 * Nothing is drawn where the width comes out at zero or less.
 *
 * Every box sits inside its own slot, which is what keeps `Placeable.width` from
 * coercing it — see the carousel's `spacing`.
 *
 * **Every box is exactly as visible as its own slot**, and that is the constraint
 * the table is built around rather than a happy accident. A `LazyRow` composes the
 * pages whose slots meet the viewport and no others, so a box pulled in from a
 * slot that is still outside it is a box that is not there: the first version of
 * this shifted the far page inward from `d = 2`, and for most of a swipe the
 * frame's end was a strip of background — measured as an eleven-pixel hole two
 * frames into a drag. Here the leaving box's right edge *is* its slot's right
 * edge and the arriving box's left edge *is* its slot's left edge, so a page has a
 * box precisely when the row has composed it.
 *
 * What the arithmetic gives back is exact tiling. Consecutive boxes are always `G`
 * apart — `(P - fP) - (L - fP) = P - L = G`, with the `f` cancelling — and the
 * boxes and their gaps cover `[0, V]` with nothing left over at either end, at
 * every fraction of every swipe. With a zero peek `L` is `V`, so the one box at rest
 * *is* the frame and the pair in flight covers all of it but the gap between them.
 * `CarouselHeroTest` sweeps both.
 *
 * The one shift in the table is what makes a leaving page *shrink into* the start
 * edge rather than slide out through it: its box is pinned at zero while its width
 * runs out, so the picture holds still and is taken away rather than travelling.
 * Written as a placement and in reading order, so a right-to-left row is free.
 *
 * **The last page grows to the whole frame**, which is the `L` cap dropped: there is
 * no page after it to fill the gap and the peek, and the end of a row of pictures
 * being one picture is the only honest thing for it to look like. With a zero peek
 * the cap is already the frame, so the last page is like every other one.
 */
private fun heroBoxOf(
    d: Float,
    frame: Float,
    metrics: HeroMetrics,
    last: Boolean,
): HeroBox? {
    if (frame <= 0f) return null
    val width = if (d < 0f) metrics.hero + d * metrics.pitch else frame - d * metrics.pitch
    val capped = if (d < 0f || last) width else minOf(width, metrics.hero)
    if (capped <= 0f) return null
    return HeroBox(width = capped, shift = if (d < 0f) -d * metrics.pitch else 0f)
}

/** The hero's own width and how far apart the slots are, both in pixels. */
private class HeroMetrics(val hero: Float, val pitch: Float)

/**
 * The two numbers the style is derived from, taken from the frame and nothing else.
 *
 * A slot is the whole frame — `fillParentMaxWidth` inside a `LazyRow` is the
 * viewport less the content padding — and the arrangement pulls the next slot back
 * into it, so nothing here has to measure the viewport. The pitch must match that
 * arrangement exactly or every box is out of step with the slot it is measured in;
 * see the carousel's own `spacing`, which is this `gap - reserve`.
 *
 * **The gap is reserved in the frame only when there is a peek to separate.** With
 * a peek the frame is `[hero][gap][peek]`, so the reserve is both. With none, there
 * is nothing beside the hero at rest and the hero is the whole frame: slots are a
 * frame *plus* a gap apart, the next page is entirely off screen until a swipe
 * brings it in, and the gap becomes something that opens between two boxes in
 * flight rather than a strip of background standing at the frame's end. That is
 * what `peek = 0.dp` means, and without it a zero peek drew a hero one gap short of
 * the frame with the gap left over at the edge.
 *
 * The floor on the hero is for a peek and a gap that between them leave nothing —
 * a mistake no arithmetic here can rescue, since the slots are already that far
 * apart. What it buys is a carousel that is visibly wrong rather than blank, which
 * at least says which number to change. The pitch is left alone by it: the pitch is
 * the arrangement's, not this function's to correct.
 */
private fun heroMetrics(frame: Float, gap: Float, peek: Float): HeroMetrics {
    val reserve = if (peek > 0f) gap + peek else 0f
    return HeroMetrics(
        hero = (frame - reserve).coerceAtLeast(frame / 3f),
        pitch = frame - reserve + gap,
    )
}


private fun Modifier.heroPage(
    state: CarouselState,
    page: Int,
    count: Int,
    gap: Float,
    peek: Float,
    drift: Float,
    shape: Shape,
): Modifier = this
    .layout { measurable, constraints ->
        // The shift, applied by *placing* the box rather than by translating it, so
        // the clip below and everything it gates — drawing, hit-testing — move with
        // it. `placeRelative` and not `place`: every number in `heroBoxOf` is in
        // reading order, and a right-to-left row then comes out right with no
        // `LayoutDirection` anywhere in the geometry. The wipe before this needed one
        // because it worked in physical draw coordinates.
        val frame = constraints.maxWidth.toFloat()
        val box = heroBoxOf(
            d = page - state.pagePosition,
            frame = frame,
            metrics = heroMetrics(frame, gap, peek),
            last = page == count - 1,
        )
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(box?.shift?.roundToInt() ?: 0, 0)
        }
    }
    .clip(shape)
    .layout { measurable, constraints ->
        val frame = constraints.maxWidth
        val last = page == count - 1
        val metrics = heroMetrics(frame.toFloat(), gap, peek)
        // Measured **once**, at the width this page's box can reach: the hero's for
        // every page but the last, whose box grows to the whole frame because
        // nothing follows it. Re-measuring to the box's current width instead would
        // reflow the page's content on every frame of a drag, which is the cost a
        // carousel of pictures must not pay — and the reason the docs say this style
        // crops rather than reflows.
        val natural = if (last) frame else metrics.hero.roundToInt().coerceIn(0, frame)
        val placeable = measurable.measure(
            constraints.copy(minWidth = natural, maxWidth = natural)
        )
        val box = heroBoxOf(
            d = page - state.pagePosition,
            frame = frame.toFloat(),
            metrics = metrics,
            last = last,
        )
        val width = box?.width?.roundToInt()?.coerceIn(0, natural) ?: 0
        layout(width, placeable.height) {
            // **The content's own share of the travel, which is the parallax.**
            //
            // The box of a page on its way out is pinned to the frame's start by the
            // shift above, and its content is pinned with it — the box closes over a
            // picture that holds still. Handing the content back `drift` of that
            // shift hands it back that much of the strip's travel: at `1` it slides
            // out with the strip under a shrinking window, which is the "slide seen
            // through a moving window" the wipe's own `parallax` named.
            //
            // No clamp is needed. The content is the hero's width and the box is
            // `hero - shift` wide, so the slack behind it is exactly the shift and a
            // fraction of the shift cannot uncover the box's far edge. Every other
            // box has no shift, so this is zero and the content travels with its box
            // — which is the strip's own motion, and has nothing to drift against.
            //
            // `placeRelative`, so the content sits at the reading start of its window
            // and drifts in reading order. In a right-to-left row that is the right
            // edge, which is also the crop a mirrored carousel wants — where `place`
            // was showing it the left one.
            //
            // **And a page on its way in holds still too**, end-aligned in its box.
            // Its box is at the strip's position and its content used to be at the
            // box's start, so the picture rode in with the strip — and swiping back,
            // rode out with it. Reported: "it still sometimes looks like the photo
            // slides in and out … it should look like the photo doesn't move, but
            // instead the boxes just wipe". End-aligned, the picture's end edge is
            // the box's, and while the box is still growing that is the frame's own
            // end — so the picture stands where it will rest and the box's leading
            // edge sweeps across it. With a zero peek, the default, that is true of
            // the whole swipe. With a peek the box stops growing at the hero's width
            // a gap and a peek short of home, and covers that last stretch sliding.
            //
            // Not placed at zero width: an empty box draws nothing and hit-tests
            // nothing, which is what a page off the end of the frame should be.
            //
            // **Parallax moves both.** With a drift, the arriving picture gives back
            // the same share of the strip's travel the leaving one keeps: it starts
            // that far toward the end and closes the distance as its box opens, so
            // the two pictures drift together behind the wipe instead of one drifting
            // and one standing still. At `0` it is end-aligned and holds still; at `1`
            // it is start-aligned and rides in with its box.
            if (width > 0) {
                val shift = box?.shift ?: 0f
                val x = if (shift > 0f) {
                    -drift * shift
                } else {
                    -(natural - width) * (1f - drift)
                }
                placeable.placeRelative(x.roundToInt(), 0)
            }
        }
    }

/**
 * Which page of how many, as a row of dots.
 *
 * ```kotlin
 * PageIndicator(carousel, onPageClick = { scope.launch { carousel.animateScrollToPage(it) } })
 * ```
 *
 * **Pass `onPageClick` unless something else can change the page.** Without it
 * the dots are decoration and the carousel is swipe-only — see the note on
 * [Carousel]. With it each dot is a `Role.RadioButton` naming the page it goes
 * to, and the whole strip becomes one full-height target that sends a tap to the
 * nearest dot: a pointer route and an assistive-tech route, kept separate so
 * neither has to pay for the other's shape.
 *
 * The current page is drawn **wider** than the rest rather than only tinted.
 * Colour alone fails WCAG 1.4.1, and at this size — a few pixels of tinted
 * circle — it is the hardest place in the system to see a tint difference.
 *
 * @param onPageClick `null` makes the dots decorative, and hides them from the
 *   accessibility tree entirely: the carousel already announces "3 of 5", and a
 *   screen reader walking five unlabelled dots after it is noise.
 */
@Composable
fun PageIndicator(
    state: CarouselState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onPageClick: ((Int) -> Unit)? = null,
    style: PageIndicatorStyle = PageIndicatorStyle.Pill,
    activeColour: Color = Theme.colours.primary,
    inactiveColour: Color = Theme.colours.outlineStrong,
    pageDescription: (Int, Int) -> String = Theme.strings.pageOfCount,
    /**
     * Glyphs for a step-back and step-forward button either side of the dots.
     *
     * Both optional and independent, like every decorative icon in the library:
     * the design system ships no icon set beyond the few glyphs its controls
     * cannot do without, so a component that draws one has chosen for you.
     *
     * They need [onPageClick] — it is the only way this has of moving the
     * carousel — and they disable themselves at the ends rather than wrapping
     * around. A carousel is a row you can see the edges of; a "next" that jumps
     * back to the first page is a different control.
     *
     * Worth adding wherever the carousel is not obviously swipeable: a desktop
     * window, a page a mouse is driving, or a small set of pages where the dots
     * are too fine a target to aim at one by one.
     */
    previousIcon: ImageVector? = null,
    nextIcon: ImageVector? = null,
    previousLabel: String = Theme.strings.previous,
    nextLabel: String = Theme.strings.next,
) {
    if (previousIcon == null && nextIcon == null) {
        PageDots(state, modifier, enabled, onPageClick, style, activeColour, inactiveColour, pageDescription)
        return
    }

    val count = state.pageCount
    val current = state.currentPage

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
    ) {
        if (previousIcon != null) {
            IconButton(
                icon = previousIcon,
                contentDescription = previousLabel,
                onClick = { onPageClick?.invoke(current - 1) },
                enabled = enabled && onPageClick != null && current > 0,
                size = ButtonSize.Small,
            )
        }

        PageDots(
            state = state,
            modifier = Modifier,
            enabled = enabled,
            onPageClick = onPageClick,
            style = style,
            activeColour = activeColour,
            inactiveColour = inactiveColour,
            label = pageDescription,
        )

        if (nextIcon != null) {
            IconButton(
                icon = nextIcon,
                contentDescription = nextLabel,
                onClick = { onPageClick?.invoke(current + 1) },
                enabled = enabled && onPageClick != null && current < count - 1,
                size = ButtonSize.Small,
            )
        }
    }
}

/**
 * The dots themselves, without the buttons.
 *
 * Its own composable because the worm is drawn from positions measured against
 * *this* row — put the step buttons in the same row and every dot centre moves
 * by the width of a button, which the worm would faithfully follow to the wrong
 * place.
 */
@Composable
private fun PageDots(
    state: CarouselState,
    modifier: Modifier,
    enabled: Boolean,
    onPageClick: ((Int) -> Unit)?,
    style: PageIndicatorStyle,
    activeColour: Color,
    inactiveColour: Color,
    label: (Int, Int) -> String,
) {
    val count = state.pageCount
    val current = state.currentPage
    // Both of the styles that draw a pill *over* the dots rather than widening
    // one of them. What separates them is only how long that pill is at rest.
    val travels = style != PageIndicatorStyle.Dots

    // Where each dot ended up, so the pill can be drawn between two of them.
    //
    // Measured rather than derived from the dot size and the gap, because those
    // need not be the pitch.
    val dotCentre = remember(count) { FloatArray(count) }
    // And how wide each one ended up, which is the other half of its box.
    //
    // The pill is drawn as the **hull of the two dots it spans**, so it needs
    // both. Reported rather than derived for the same reason the centres are:
    // one dot in the row is a different width from the rest and which one that is
    // changes under a spring, so the only figure that is certainly right is the
    // one the layout just produced.
    val dotWidth = remember(count) { FloatArray(count) }
    val dotRadius = with(LocalDensity.current) { PageIndicatorDefaults.DotSize.toPx() / 2f }
    // The gesture below outlives the composition that installed it, so the
    // handler has to be read at tap time rather than captured.
    val select = rememberUpdatedState(onPageClick)

    Row(
        modifier = modifier
            .then(if (onPageClick != null) Modifier.selectableGroup() else Modifier)
            .then(
                if (onPageClick != null) {
                    // **One target over the whole strip, rather than one per dot.**
                    //
                    // Reported as the indicator being too spread out, and it was:
                    // `minimumTouchTarget` on each dot reserves 48dp of row apiece
                    // on Android, so five 8dp dots held 264dp to show 40dp of ink
                    // and sat nearly four times further apart than they looked.
                    //
                    // Reserved once, on the strip, `fill = true`: the row becomes
                    // a full-height band the width of the dots, and the tap goes
                    // to whichever dot centre is nearest the finger. Every pixel
                    // of the band belongs to some page, so nothing is lost by the
                    // dots no longer each owning a box — a 14dp-wide slice of a
                    // 48dp-tall band is an easier thing to hit than an 8dp circle,
                    // which is what the eye was aiming at all along.
                    //
                    // `fill` rather than the centring default because the dot
                    // centres are measured in the row's own space: filled, the
                    // reserved band *is* the row, so the x a tap arrives at and
                    // the x `reportBox` recorded are the same number. Centred,
                    // they would differ by half the slack and only on the axis
                    // where there is any — and a coordinate that is right except
                    // on narrow strips is the kind that is found on a phone.
                    //
                    // The trade is honest and written down under
                    // `page-indicator.md`: a slice is narrower than WCAG 2.5.8's
                    // 24dp, and the exact route — the `onClick` in each dot's
                    // semantics below — is unaffected by how wide anything is.
                    Modifier
                        .minimumTouchTarget(fill = true)
                        .pointerCursor(enabled = enabled)
                        .then(
                            if (enabled && count > 0) {
                                Modifier.pointerInput(count) {
                                    detectTapGestures { at ->
                                        var nearest = 0
                                        var best = Float.MAX_VALUE
                                        for (page in 0 until count) {
                                            val x = dotCentre[page]
                                            val away = kotlin.math.abs(x - at.x)
                                            if (away < best) {
                                                best = away
                                                nearest = page
                                            }
                                        }
                                        select.value?.invoke(nearest)
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                } else {
                    Modifier
                }
            )
            .then(
                if (travels && count > 0) {
                    Modifier.drawWithContent {
                        drawContent()
                        // Read here, its only use: the pill follows every frame
                        // of a swipe, and read in composition that recomposed
                        // the whole row of dots for each of them.
                        val at = state.pagePosition.coerceIn(0f, (count - 1).toFloat())
                        val from = at.toInt().coerceIn(0, count - 1)
                        val to = (from + 1).coerceAtMost(count - 1)
                        val fraction = at - from
                        val a = dotCentre[from]
                        val b = dotCentre[to]
                        if (a == 0f && b == 0f) return@drawWithContent
                        // The leading edge goes first and the trailing edge
                        // catches up, so the pill is at its longest halfway
                        // between the two dots. Both ends arriving together
                        // would just be a dot sliding.
                        val ahead = (fraction * 2f).coerceAtMost(1f)
                        val behind = (fraction * 2f - 1f).coerceAtLeast(0f)
                        val lead = a + (b - a) * ahead
                        val trail = a + (b - a) * behind
                        // **The pill is the hull of the two dots' own boxes**,
                        // read at the same fractions its two ends are — so each
                        // end is as thick as the dot it is currently over.
                        //
                        // It used to be a fixed resting length centred on a
                        // point, and that was reported: a `Pill` rests over a dot
                        // the row has widened to `ActiveWidth`, and a length
                        // picked independently of that either hangs past it —
                        // leaving half a gap on each side where every other gap
                        // is whole — or has to be capped short of it, which is
                        // the same unevenness with the pill as the small one.
                        // Taken from the boxes, it rests *exactly* on the widened
                        // dot at every spacing, and it can never leave the row.
                        val wa = dotWidth[from]
                        val wb = dotWidth[to]
                        val leadHalf = (wa + (wb - wa) * ahead) / 2f
                        val trailHalf = (wa + (wb - wa) * behind) / 2f
                        val left = minOf(lead - leadHalf, trail - trailHalf)
                        val right = maxOf(lead + leadHalf, trail + trailHalf)
                        drawRoundRect(
                            color = activeColour,
                            topLeft = Offset(left, (size.height - dotRadius * 2f) / 2f),
                            size = Size(right - left, dotRadius * 2f),
                            cornerRadius = CornerRadius(dotRadius),
                        )
                    }
                } else {
                    Modifier
                }
            )
            .semantics {
                if (onPageClick == null) {
                    // Decorative. The carousel above already says "3 of 5"; five
                    // more unlabelled nodes saying nothing is the noise that
                    // makes people turn a screen reader off.
                    isTraversalGroup = false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(PageIndicatorDefaults.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { page ->
            // **Which page you are on, and which dot is drawn wide, are two
            // questions now.** They were one, and a worm answered the second
            // "none of them" — so it answered the first that way too, and a
            // screen reader heard a row of page buttons with none of them
            // current. Every style has a current page; only [Worm] declines to
            // widen a dot for it, which is what makes a worm a worm.
            val selected = page == current
            val wide = selected && style != PageIndicatorStyle.Worm
            val width by animateDpAsState(
                targetValue = if (wide) {
                    PageIndicatorDefaults.ActiveWidth
                } else {
                    PageIndicatorDefaults.DotSize
                },
                animationSpec = Theme.motion.springOrTween(Theme.motion.springSnappy),
                label = "pageIndicatorDot",
            )

            val dot = @Composable {
                Surface(
                    modifier = Modifier
                        .width(width)
                        .height(PageIndicatorDefaults.DotSize)
                        .clip(Theme.shapes.capsule),
                    shape = Theme.shapes.capsule,
                    // Under a travelling style every dot is a track, and the pill
                    // drawn over them is what says which page this is.
                    containerColour = if (wide) activeColour else inactiveColour,
                    content = {},
                )
            }

            if (onPageClick == null) {
                Box(Modifier.reportBox(dotCentre, dotWidth, page)) { dot() }
            } else {
                Box(
                    modifier = Modifier
                        .reportBox(dotCentre, dotWidth, page)
                        // **Semantics, and no touch target of its own.** The
                        // strip's `pointerInput` above owns the pointer route and
                        // picks the nearest dot to where the finger landed; what
                        // is left here is what a screen reader needs, which was
                        // never the part that took up room. `onClick` in the
                        // semantics tree is a real action — TalkBack and
                        // VoiceOver activate the focused node, not a rectangle —
                        // so the two routes are independent and only one of them
                        // costs layout.
                        .semantics {
                            this.contentDescription = label(page, count)
                            this.role = Role.RadioButton
                            this.selected = selected
                            if (enabled) {
                                onClick {
                                    onPageClick(page)
                                    true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    dot()
                }
            }
        }
    }
}

/** How a [PageIndicator] shows which page is current. */
enum class PageIndicatorStyle {
    /**
     * The current dot widens into a pill and the others stay round.
     *
     * Nothing travels: the old page's dot narrows and the new one widens, which
     * is two things changing rather than one thing moving. Right where the
     * indicator is never the *subject* of a gesture — a stepper's position, a
     * wizard — and where a swipe's midpoint is not worth drawing.
     */
    Dots,

    /**
     * One pill stretches from the dot it is leaving to the dot it is arriving
     * at, then contracts.
     *
     * It reads as a single thing travelling rather than as one dot going out
     * and another coming on, and it is the one indicator style that shows the
     * *middle* of a swipe rather than only its ends: the pill is at its longest
     * exactly halfway between two pages. That needs a fractional page position,
     * which is why [CarouselState.pagePosition] exists.
     *
     * The pill contracts to a *dot* at each end, so at rest this looks exactly
     * like an indicator with no current page at all — and no dot is widened to
     * make up for it, which is what keeps the row evenly spaced. [Pill] is the
     * same travel over a row that does widen one, and is the default for that
     * reason.
     */
    Worm,

    /**
     * Both of the above: round dots, and a pill over the current one that
     * stretches to the next as the page travels.
     *
     * **The default.** [Dots] shows where you are and says nothing about the
     * journey; [Worm] shows the journey and, at rest, nothing about where you
     * are — the pill is a dot like all the others until something moves. This
     * rests like the first and travels like the second, which is the only one of
     * the three that is legible in both states.
     *
     * It also fixes something [Worm] gets wrong and this one cannot: under a
     * worm every dot is drawn inactive and **no dot reports as selected**, so a
     * screen reader hears a row of page buttons with none of them current. The
     * pill is drawn on top of the dots rather than in place of them, so the
     * selection is a fact about a dot again.
     *
     * Its layout **is** [Dots]': the row widens the current dot to `ActiveWidth`
     * exactly as that style does, and the pill at rest is that dot's own box. So
     * the gaps are the one gap everywhere, the two styles are the same width, and
     * switching between them moves nothing.
     */
    Pill,
}

/** What a [PageIndicator] takes by default. */
object PageIndicatorDefaults {
    val DotSize: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.pageIndicatorDotSize

    /**
     * How wide the current dot grows.
     *
     * Wide enough to read as a different *shape* rather than a slightly bigger
     * circle. A tint change alone fails WCAG 1.4.1, and eight pixels of colour
     * is the hardest place in the system to see one.
     */
    val ActiveWidth: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.pageIndicatorActiveWidth

    val Gap: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.pageIndicatorGap
}


/** Records this dot's centre, relative to the indicator row, for the worm. */
private fun Modifier.reportBox(
    centres: FloatArray,
    widths: FloatArray,
    index: Int,
): Modifier = onGloballyPositioned {
    centres[index] = it.positionInParent().x + it.size.width / 2f
    widths[index] = it.size.width.toFloat()
}

/**
 * A snap with no coast in it.
 *
 * `rememberSnapFlingBehavior`'s default lets a fling decay across as many pages
 * as its velocity carries it and snaps wherever it runs out — which on a
 * carousel of wide cards means a flick throws three pages past the one you were
 * looking at and lands soft. Reported as the snapping not feeling strong enough,
 * and the *approach* is the part that is loose rather than the snap.
 *
 * So the approach offset is zero: whatever the velocity, the fling decelerates
 * straight into the nearest page rather than travelling first. One flick, one
 * page, and the spring at the end is the library's snappy one rather than the
 * platform default. A carousel is a stack of cards being turned over, not a list
 * being scrolled.
 *
 * Everything else — where a page snaps to, what counts as nearest — is Compose's
 * own provider, delegated to. The only thing worth changing here is how far it
 * is allowed to drift before it starts.
 */
@Composable
private fun firmSnapFlingBehaviour(listState: LazyListState): FlingBehavior {
    val motion = Theme.motion
    val snap = motion.springOrTween<Float>(motion.springSnappy)
    val decay = remember { exponentialDecay<Float>() }
    val provider = remember(listState) {
        val base = SnapLayoutInfoProvider(listState)
        object : SnapLayoutInfoProvider by base {
            override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f

            /**
             * The same snap, committed a quarter of the way instead of half.
             *
             * `base` answers "which page is nearest", which is a list's
             * question. A carousel's is "did that gesture turn the card", and a
             * full-width card is a long way to drag before finding out.
             *
             * Derived from `base` rather than from the layout's own offsets,
             * which is the part worth keeping: the platform already returns the
             * distance to the nearest page edge, and the sign of it says which
             * edge — negative to fall back to the page's start, positive to
             * carry on to the next. From that one number and the pitch, both
             * candidate distances are known without touching
             * `viewportStartOffset`, `beforeContentPadding` or any of the other
             * places an off-by-a-padding hides.
             */
            override fun calculateSnapOffset(velocity: Float): Float {
                val nearest = base.calculateSnapOffset(velocity)
                val visible = listState.layoutInfo.visibleItemsInfo
                if (visible.size < 2) return nearest
                val pitch = (visible[1].offset - visible[0].offset).toFloat()
                if (pitch <= 0f) return nearest

                // How far past the current page's start edge the drum sits, and
                // how far short of the next one.
                val past = if (nearest <= 0f) -nearest else pitch - nearest
                val short = pitch - past
                if (past <= 0f || short <= 0f) return nearest

                // Which way the finger went. `past` on its own cannot say: a
                // third of the way along is a third forward from this page or
                // two thirds back from the next, and the threshold counts from
                // wherever the gesture began.
                val travelled = if (listState.lastScrolledBackward) short else past
                return if (travelled >= pitch * CarouselDefaults.SnapThreshold) {
                    if (listState.lastScrolledBackward) -past else short
                } else {
                    if (listState.lastScrolledBackward) short else -past
                }
            }

        }
    }
    return remember(provider, decay, snap) { snapFlingBehavior(provider, decay, snap) }
}

