package io.kontour.ui.components.display

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Surface
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

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
    private val pageCount: () -> Int,
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

    val count: Int
        get() = pageCount().also {
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

    suspend fun scrollToPage(page: Int) {
        listState.animateScrollToItem(page.coerceIn(0, (count - 1).coerceAtLeast(0)))
    }
}

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
 * PageIndicator(carousel, onPageSelect = { carousel.scrollToPage(it) })
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
 * [PageIndicator] is for — give it `onPageSelect` and its dots become targets.
 * A carousel with a decorative indicator and no arrows is operable by exactly
 * one input method, and the app has four.
 *
 * @param contentDescription What the set of pages *is* — "Stop photos". Required:
 *   "1 of 5" without it is a count of nothing.
 * @param pageSpacing The gap between pages. Part of the snap distance, so it
 *   belongs here rather than in the caller's own padding. Ignored by
 *   [CarouselStyle.Wipe], whose two pages have to meet along one edge — a gap
 *   between them would be a strip of whatever is behind the carousel, moving.
 * @param parallax How far a page's content travels with the wipe, from `0` for
 *   not at all to `1` for the whole page width, and clamped to that range. Only
 *   [CarouselStyle.Wipe] reads it, and `0` is the plain wipe: the page holds
 *   still and the edge moves over it. Around `0.2` to `0.3` gives the content a
 *   drift behind the edge without it arriving from off screen; `1` is a slide
 *   seen through a moving window.
 *
 *   **Ignored under reduced motion**, which is the whole of what that setting
 *   can sensibly take away here — the edge itself is the style rather than an
 *   embellishment on it.
 */
@Composable
fun Carousel(
    state: CarouselState,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: CarouselStyle = CarouselStyle.Slide,
    parallax: Float = 0f,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = Theme.spacing.xs,
    previousLabel: String = Theme.strings.previous,
    nextLabel: String = Theme.strings.next,
    content: @Composable (page: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val count = state.count
    val current = state.currentPage
    val direction = LocalLayoutDirection.current
    // **Parallax is the one part of a wipe a reader can turn off**, and the
    // standing rule that a transform which *moves* has to ask for itself is why
    // it is read here rather than left to a spec. `Motion`'s helpers shorten a
    // movement and cannot make one smaller, and none of them applies at all to a
    // transform whose input is a finger.
    //
    // The wipe's own offset is not gated with it and cannot be: it is what puts a
    // page in the frame, so taking it away is not less motion but a different
    // style. That is the right way round anyway — a wipe holds its content still
    // and moves one edge across it, which is *less* movement than the strip a
    // `Slide` pulls past the window, and it is a page's content drifting
    // underneath that edge for decoration that reduced motion names.
    //
    // Coerced rather than required: this is the kind of number a caller animates
    // or reads from a setting, and a carousel that throws at `1.02` on one frame
    // of a spring is worse than one that draws `1`.
    val drift = if (Theme.motion.reduceMotion) 0f else parallax.coerceIn(0f, 1f)

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
    val ticker = rememberDetentTicker()
    LaunchedEffect(state) {
        snapshotFlow { dragging to state.currentPage }.collect { (byHand, page) ->
            if (byHand) ticker.at(page) else ticker.reset()
        }
    }

    // **The first and last page refuse to go further, and say nothing about it.**
    //
    // There was a whole `NestedScrollConnection` here for one report — the part
    // of a drag the list declined, which is the only signal that separates "the
    // finger asked for more page and got none" from "page one is merely
    // showing". It went with the rest of the library's end stops, and so did the
    // connection, which existed for nothing else: a strip that will not move
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
                        onDragStopped = { scope.launch { state.scrollToPage(state.currentPage) } },
                    )
                } else {
                    Modifier
                }
            )
            .semantics {
                isTraversalGroup = true
                this.contentDescription = contentDescription
                stateDescription = "${current + 1} of $count"
                customActions = listOf(
                    CustomAccessibilityAction(previousLabel) {
                        if (current > 0) {
                            scope.launch { state.scrollToPage(current - 1) }
                            true
                        } else {
                            false
                        }
                    },
                    CustomAccessibilityAction(nextLabel) {
                        if (current < count - 1) {
                            scope.launch { state.scrollToPage(current + 1) }
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
        horizontalArrangement = Arrangement.spacedBy(
            if (style == CarouselStyle.Wipe) 0.dp else pageSpacing,
        ),
        // Snapping rather than free scroll: a carousel that stops between two
        // pages is showing neither, and the indicator below it is then lying
        // whatever it says.
        flingBehavior = firmSnapFlingBehaviour(state.listState),
    ) {
        items(count) { page ->
            Box(
                Modifier
                    .fillParentMaxWidth()
                    .then(
                        if (style == CarouselStyle.Wipe) {
                            Modifier.wipe(state, page, drift, direction)
                        } else {
                            Modifier
                        }
                    )
            ) { content(page) }
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
     * The pages are stacked in one place, and the edge between them moves.
     *
     * Two boxes trading width: the page you are leaving keeps the near part of
     * the frame and the page you are arriving at takes the far part, and neither
     * of them goes anywhere. At rest one page has the whole frame and the other
     * is not drawn at all, which is the difference you can see in a still —
     * [Slide] at rest looks the same and is a strip that happens to be aligned.
     *
     * Right for pages that are **one picture each**, where a slide reads as a
     * filmstrip being pulled past a slot and this reads as the picture itself
     * changing. Wrong for pages with structure — a form, a list — where holding
     * the text still while a hard edge crosses it is harder to read than moving
     * it out of the way.
     *
     * `parallax` gives the content back some of its travel without giving up the
     * edge; see [Carousel]'s parameter. The gesture, the snap and everything the
     * carousel announces are the same either way: this changes where the pixels
     * go and nothing else.
     */
    Wipe,
}

/**
 * Draws this page in the frame the settled page occupies, clipped to its share.
 *
 * **The layout is untouched**, which is the whole trick: the `LazyRow` still
 * lays the pages out as a strip, still snaps, still measures the pitch two real
 * items apart for [CarouselState.pagePosition]. All this does is put the page
 * back where a settled page sits and cut it down to the part of the frame it has
 * won — so the scroll, the fling, the accessibility actions and the indicator
 * all work on the strip they were written for.
 *
 * `d` is how many pages ahead of the viewport this one is, fractionally: `0` is
 * settled, `-0.4` is four tenths of the way out, and anything at or past a whole
 * page is off screen and not drawn. The share of the frame is `1 - |d|`, and
 * **which end of the frame it sits at** is what separates the two pages: the one
 * being left keeps the near end, the one arriving takes the far end, and they
 * tile the frame between them with no seam and no overlap.
 *
 * `pin` is the offset that puts the page back in the frame. It is the one place
 * the layout direction enters: `pagePosition` counts pages in reading order and
 * a right-to-left row lays them out the other way, so the same `d` is the
 * opposite distance on screen. Everything after it is physical — `clipRect` and
 * `translationX` both are — so "near end" flips with it too.
 *
 * Two modifiers rather than one because they want different coordinate spaces.
 * The clip belongs to the page's *box*, which is where the frame is measured
 * from; the offset belongs to the page's *layer*, so that a button on a page
 * can be pressed where it is drawn rather than where the strip put it. A single
 * `drawWithContent` doing both would move the pixels and leave the touch
 * targets behind.
 */
private fun Modifier.wipe(
    state: CarouselState,
    page: Int,
    parallax: Float,
    direction: LayoutDirection,
): Modifier = this
    .drawWithContent {
        val width = size.width
        val d = page - state.pagePosition
        if (width <= 0f || kotlin.math.abs(d) >= 1f) return@drawWithContent
        val pin = pagePin(d, width, direction)
        val share = (1f - kotlin.math.abs(d)) * width
        // Whether this page's share sits against the frame's left edge. The page
        // being left keeps the near end, and in a right-to-left row the near end
        // is the right one.
        val near = (d <= 0f) != (direction == LayoutDirection.Rtl)
        val from = if (near) 0f else width - share
        val to = if (near) share else width
        // `+ pin` because the clip is measured in the box the strip placed, and
        // the frame is `pin` away from it.
        clipRect(left = from + pin, right = to + pin) {
            this@drawWithContent.drawContent()
        }
    }
    .graphicsLayer {
        // `direction` is handed in rather than read here: a `GraphicsLayerScope`
        // carries a density and not a layout direction, and this is the one
        // number in the block that does not change every frame anyway.
        val d = page - state.pagePosition
        translationX = pagePin(d, size.width, direction) * (1f - parallax)
    }

/** How far this page is from the frame a settled page sits in, in pixels. */
private fun pagePin(d: Float, width: Float, direction: LayoutDirection): Float =
    (if (direction == LayoutDirection.Rtl) d else -d) * width

/**
 * Which page of how many, as a row of dots.
 *
 * ```kotlin
 * PageIndicator(carousel, onPageSelect = { scope.launch { carousel.scrollToPage(it) } })
 * ```
 *
 * **Pass `onPageSelect` unless something else can change the page.** Without it
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
 * @param onPageSelect `null` makes the dots decorative, and hides them from the
 *   accessibility tree entirely: the carousel already announces "3 of 5", and a
 *   screen reader walking five unlabelled dots after it is noise.
 */
@Composable
fun PageIndicator(
    state: CarouselState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onPageSelect: ((Int) -> Unit)? = null,
    style: PageIndicatorStyle = PageIndicatorStyle.Pill,
    activeColour: Color = Theme.colours.primary,
    inactiveColour: Color = Theme.colours.outlineStrong,
    label: (Int, Int) -> String = Theme.strings.pageOfCount,
    /**
     * Glyphs for a step-back and step-forward button either side of the dots.
     *
     * Both optional and independent, like every other icon in the library: the
     * design system ships no icon set, so a component that draws one has chosen
     * for you.
     *
     * They need [onPageSelect] — it is the only way this has of moving the
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
        PageDots(state, modifier, enabled, onPageSelect, style, activeColour, inactiveColour, label)
        return
    }

    val count = state.count
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
                onClick = { onPageSelect?.invoke(current - 1) },
                enabled = enabled && onPageSelect != null && current > 0,
                size = ButtonSize.Small,
            )
        }

        PageDots(
            state = state,
            modifier = Modifier,
            enabled = enabled,
            onPageSelect = onPageSelect,
            style = style,
            activeColour = activeColour,
            inactiveColour = inactiveColour,
            label = label,
        )

        if (nextIcon != null) {
            IconButton(
                icon = nextIcon,
                contentDescription = nextLabel,
                onClick = { onPageSelect?.invoke(current + 1) },
                enabled = enabled && onPageSelect != null && current < count - 1,
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
    onPageSelect: ((Int) -> Unit)?,
    style: PageIndicatorStyle,
    activeColour: Color,
    inactiveColour: Color,
    label: (Int, Int) -> String,
) {
    val count = state.count
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
    val position = if (travels) state.pagePosition else 0f
    // The gesture below outlives the composition that installed it, so the
    // handler has to be read at tap time rather than captured.
    val select = rememberUpdatedState(onPageSelect)

    Row(
        modifier = modifier
            .then(if (onPageSelect != null) Modifier.selectableGroup() else Modifier)
            .then(
                if (onPageSelect != null) {
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
                        val at = position.coerceIn(0f, (count - 1).toFloat())
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
                if (onPageSelect == null) {
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
                    colour = if (wide) activeColour else inactiveColour,
                    content = {},
                )
            }

            if (onPageSelect == null) {
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
                                    onPageSelect(page)
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

