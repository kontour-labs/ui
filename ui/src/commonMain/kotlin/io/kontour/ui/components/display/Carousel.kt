package io.kontour.ui.components.display

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
        flingBehavior = rememberSnapFlingBehavior(state.listState),
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
    activeColor: Color = Theme.colors.primary,
    inactiveColor: Color = Theme.colors.outlineStrong,
    label: (Int, Int) -> String = Theme.strings.pageOfCount,
) {
    val count = state.count
    val current = state.currentPage

    Row(
        modifier = modifier
            .then(if (onPageSelect != null) Modifier.selectableGroup() else Modifier)
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
            val active = page == current
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
                        .clip(CircleShape),
                    shape = CircleShape,
                    color = if (active) activeColor else inactiveColor,
                    content = {},
                )
            }

            if (onPageSelect == null) {
                dot()
            } else {
                Box(
                    modifier = Modifier
                        .minimumTouchTarget()
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
