package io.kontour.ui.components.display

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Surface
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch

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

    val count: Int get() = pageCount()

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
 *   belongs here rather than in the caller's own padding.
 */
@Composable
fun Carousel(
    state: CarouselState,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = Theme.spacing.xs,
    previousLabel: String = Theme.strings.previous,
    nextLabel: String = Theme.strings.next,
    content: @Composable (page: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val count = state.count
    val current = state.currentPage

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
        horizontalArrangement = Arrangement.spacedBy(pageSpacing),
        // Snapping rather than free scroll: a carousel that stops between two
        // pages is showing neither, and the indicator below it is then lying
        // whatever it says.
        flingBehavior = firmSnapFlingBehaviour(state.listState),
    ) {
        items(count) { page ->
            Box(Modifier.fillParentMaxWidth()) { content(page) }
        }
    }
}

/**
 * Which page of how many, as a row of dots.
 *
 * ```kotlin
 * PageIndicator(carousel, onPageSelect = { scope.launch { carousel.scrollToPage(it) } })
 * ```
 *
 * **Pass `onPageSelect` unless something else can change the page.** Without it
 * the dots are decoration and the carousel is swipe-only — see the note on
 * [Carousel]. With it each dot is a `Role.RadioButton` with a real touch target,
 * which is a pointer route and an assistive-tech route in one.
 *
 * The current dot **widens** rather than only changing colour. Colour alone
 * fails WCAG 1.4.1, and at this size — a few pixels of tinted circle — it is the
 * hardest place in the system to see a tint difference.
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
    style: PageIndicatorStyle = PageIndicatorStyle.Dots,
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
    val worm = style == PageIndicatorStyle.Worm

    // Where each dot ended up, so the worm can be drawn between two of them.
    //
    // Measured rather than derived from the dot size and the gap, because those
    // are not the pitch: with `onPageSelect` every dot is wrapped in a 48dp
    // touch target and sits three times further from its neighbour than it looks.
    val dotCentre = remember(count) { FloatArray(count) }
    val dotRadius = with(LocalDensity.current) { PageIndicatorDefaults.DotSize.toPx() / 2f }
    val position = if (worm) state.pagePosition else 0f

    Row(
        modifier = modifier
            .then(if (onPageSelect != null) Modifier.selectableGroup() else Modifier)
            .then(
                if (worm && count > 0) {
                    Modifier.drawWithContent {
                        drawContent()
                        val at = position.coerceIn(0f, (count - 1).toFloat())
                        val from = at.toInt().coerceIn(0, count - 1)
                        val to = (from + 1).coerceAtMost(count - 1)
                        val fraction = at - from
                        val a = dotCentre[from]
                        val b = dotCentre[to]
                        // The leading edge goes first and the trailing edge
                        // catches up, so the pill is at its longest halfway
                        // between the two dots. Both ends arriving together
                        // would just be a dot sliding.
                        val lead = a + (b - a) * (fraction * 2f).coerceAtMost(1f)
                        val trail = a + (b - a) * (fraction * 2f - 1f).coerceAtLeast(0f)
                        if (a == 0f && b == 0f) return@drawWithContent
                        drawRoundRect(
                            color = activeColour,
                            topLeft = Offset(
                                minOf(lead, trail) - dotRadius,
                                (size.height - dotRadius * 2f) / 2f,
                            ),
                            size = Size(
                                kotlin.math.abs(lead - trail) + dotRadius * 2f,
                                dotRadius * 2f,
                            ),
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
            val active = page == current && !worm
            val width by animateDpAsState(
                targetValue = if (active) {
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
                        .clip(Theme.shapes.pill),
                    shape = Theme.shapes.pill,
                    // Under a worm every dot is a track, and the pill on top is
                    // the only thing that says which page this is.
                    colour = if (active) activeColour else inactiveColour,
                    content = {},
                )
            }

            if (onPageSelect == null) {
                Box(Modifier.reportCentre(dotCentre, page)) { dot() }
            } else {
                Box(
                    modifier = Modifier
                        .reportCentre(dotCentre, page)
                        .minimumTouchTarget()
                        .pointerCursor(enabled = enabled)
                        .selectable(
                            selected = active,
                            onClick = { onPageSelect(page) },
                            enabled = enabled,
                            role = androidx.compose.ui.semantics.Role.RadioButton,
                            indication = null,
                            interactionSource = null,
                        )
                        .semantics { this.contentDescription = label(page, count) },
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
     * The default, and the right one when the indicator is also the control —
     * every dot keeps its own footprint, so every dot keeps its own target.
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
     */
    Worm,
}

object PageIndicatorDefaults {
    val DotSize: Dp = 8.dp

    /**
     * How wide the current dot grows.
     *
     * Wide enough to read as a different *shape* rather than a slightly bigger
     * circle. A tint change alone fails WCAG 1.4.1, and eight pixels of colour
     * is the hardest place in the system to see one.
     */
    val ActiveWidth: Dp = 20.dp

    val Gap: Dp = 6.dp
}


/** Records this dot's centre, relative to the indicator row, for the worm. */
private fun Modifier.reportCentre(into: FloatArray, index: Int): Modifier =
    onGloballyPositioned { into[index] = it.positionInParent().x + it.size.width / 2f }

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
        }
    }
    return remember(provider, decay, snap) { snapFlingBehavior(provider, decay, snap) }
}
