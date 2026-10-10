package io.kontour.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A row of thumbnails under a [Carousel], one a page, as a photo app's
 * filmstrip is: the current page's in the middle and wider than the rest, and
 * the row dragged to scrub through the pages — the carousel following the
 * finger, a page at a time felt as a detent — or a thumbnail pressed to go to
 * it. It follows the carousel too: swipe the pages, and the strip slides with
 * them.
 *
 * ```kotlin
 * val pages = rememberCarouselState { photos.size }
 * Column {
 *     Carousel(pages, contentDescription = "Trip photos") { page -> Photo(photos[page]) }
 *     ThumbnailStrip(pages) { page -> Photo(photos[page], Modifier.fillMaxSize()) }
 * }
 * ```
 *
 * Only the thumbnails on screen are composed, so a strip of thousands of
 * photos costs what a screenful does. Each is cut to the strip's corners; what
 * it draws should fill the box it is given.
 *
 * A screen reader reaches each thumbnail as one of a group, read as its page
 * and selected when it is the current one, and goes to it on a double tap. A
 * keyboard reaches the strip as one stop: the arrows step a page, Home and End
 * go to the first and last.
 *
 * @param state The carousel's state: the strip shows its pages and moves it.
 * @param enabled Off, the strip is shown but not pressed, dragged or stepped.
 * @param pageDescription How a thumbnail is announced: "Photo 2 of 40" for a
 *   strip of photos. `page` counts from zero, as the carousel's does.
 * @param thumbnailAspectRatio Each page's picture's width over its height, so
 *   the current one widens to the picture's own shape: a portrait narrower than
 *   a landscape, as a photo app's filmstrip does. Null for a page — or for the
 *   whole strip, the default — is [ThumbnailStripDefaults.CurrentWidth]. The
 *   width is kept between [ThumbnailStripDefaults.ThumbnailWidth] and a little
 *   over twice the default, so a panorama does not take the strip; a picture
 *   narrower than a thumbnail is shown whole at that width, with black at its
 *   sides.
 * @param thumbnail Draws the thumbnail of `page`, filling the box it is given.
 */
@Composable
fun ThumbnailStrip(
    state: CarouselState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pageDescription: (page: Int, count: Int) -> String = PageIndicatorDefaults.pageDescription(),
    thumbnailAspectRatio: ((page: Int) -> Float?)? = null,
    thumbnail: @Composable (page: Int) -> Unit,
) {
    val height = ThumbnailStripDefaults.Height
    val narrow = ThumbnailStripDefaults.ThumbnailWidth
    val wide = ThumbnailStripDefaults.CurrentWidth
    val gap = ThumbnailStripDefaults.Gap
    val shape = Theme.shapes.extraSmall
    val scope = rememberCoroutineScope()
    val ticker = rememberDetentTicker(FeedbackIntent.Snap)
    val interactions = remember { MutableInteractionSource() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val count = state.pageCount
    val current = state.currentPage
    val describe by rememberUpdatedState(pageDescription)
    val aspect by rememberUpdatedState(thumbnailAspectRatio)
    val density = LocalDensity.current
    // How wide each page's thumbnail grows in the middle, in pixels: its
    // picture's shape where the caller says what that is.
    val wideOf: (Int) -> Float = { page ->
        with(density) {
            currentWidth(aspect?.invoke(page), height.toPx(), narrow.toPx(), wide.toPx(), MaxCurrentWidth.toPx())
        }
    }
    // Where the strip is, as a page and a fraction: the carousel's, or while a
    // finger scrubs, the finger's — the carousel follows it a frame behind.
    val scrub = remember { mutableFloatStateOf(0f) }
    val scrubbing = remember { mutableStateOf(false) }
    val position = { if (scrubbing.value) scrub.floatValue else state.pagePosition }
    // The pages near enough the middle to be drawn: as few as fill the strip.
    var stripWidth by remember { mutableIntStateOf(0) }
    val narrowPx = with(LocalDensity.current) { (narrow + gap).toPx() }
    val shown by remember(state, narrowPx) {
        derivedStateOf {
            val reach = if (narrowPx <= 0f) 0 else ceil(stripWidth / narrowPx / 2f).toInt() + 2
            val at = position().roundToInt()
            (at - reach).coerceAtLeast(0)..(at + reach).coerceAtMost(count - 1)
        }
    }

    val reduceMotion = Theme.motion.reduceMotion
    fun goTo(page: Int) {
        if (!enabled || count == 0) return
        val target = page.coerceIn(0, count - 1)
        // There at once under reduced motion, as the carousel's own arrows are.
        scope.launch { if (reduceMotion) state.scrollToPage(target) else state.animateScrollToPage(target) }
    }

    Layout(
        content = {
            for (page in shown) {
                key(page) {
                    Box(
                        Modifier
                            .clip(shape)
                            .semantics {
                                contentDescription = describe(page, count)
                                role = Role.RadioButton
                                selected = page == current
                                if (enabled) {
                                    onClick {
                                        goTo(page)
                                        true
                                    }
                                }
                            },
                    ) {
                        val ratio = thumbnailAspectRatio?.invoke(page)
                        if (ratio != null && ratio > 0f && height * ratio < narrow) {
                            // Narrower than a thumbnail: whole, with black at
                            // its sides, as a photo app shows a tall sliver.
                            Box(
                                Modifier.fillMaxSize().background(Letterbox),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.fillMaxHeight().width(height * ratio)) { thumbnail(page) }
                            }
                        } else {
                            thumbnail(page)
                        }
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // The thumbnails either side run on past the ends: cut there.
            .clipToBounds()
            .focusRing(interactions, shape, enabled = enabled)
            .selectableGroup()
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val forward = if (rtl) -1 else 1
                when (event.key) {
                    Key.DirectionRight -> goTo(current + forward)
                    Key.DirectionLeft -> goTo(current - forward)
                    Key.MoveHome -> goTo(0)
                    Key.MoveEnd -> goTo(count - 1)
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusable(enabled, interactions)
            .pointerCursor(enabled = enabled)
            .pointerInput(state, enabled, rtl, narrowPx) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocity = VelocityTracker()
                    var travelled = 0f
                    var start = state.pagePosition
                    var dragging = false
                    // Up and down, as a list round the strip scrolls: not a press.
                    var wandered = 0f
                    // Every event, the still ones and the lift included, so a
                    // finger that stopped before letting go has stopped.
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) break
                        val dx = change.positionChange().x
                        travelled += dx
                        wandered += abs(change.positionChange().y)
                        if (!dragging && abs(travelled) > viewConfiguration.touchSlop) {
                            dragging = true
                            start = state.pagePosition
                            scrub.floatValue = start
                            scrubbing.value = true
                            travelled = dx
                        }
                        if (!dragging) continue
                        change.consume()
                        // Dragged left, the next page comes to the middle.
                        val along = if (rtl) travelled else -travelled
                        val target = (start + along / narrowPx).coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
                        scrub.floatValue = target
                        ticker.at(target.roundToInt())
                        state.requestPageAt(target)
                    }
                    if (!dragging) {
                        if (wandered > viewConfiguration.touchSlop) return@awaitEachGesture
                        // A press: the thumbnail under it.
                        val page = pageAt(down.position.x, size.width.toFloat(), position(), narrowPx, wideOf, gap.toPx(), rtl)
                        if (page != null) goTo(page)
                        return@awaitEachGesture
                    }
                    // Let go: on, a little way past where it was, to the nearest page.
                    val speed = velocity.calculateVelocity().x.let { if (rtl) it else -it } / narrowPx
                    val landing = (scrub.floatValue + speed * ScrubCarry).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
                    scrubbing.value = false
                    ticker.reset()
                    goTo(landing)
                }
            },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        stripWidth = width
        val stripHeight = constraints.maxHeight
        val gapPx = gap.toPx()
        val narrowW = narrow.toPx()
        val at = position()
        val first = shown.first
        val placeables = measurables.mapIndexed { i, measurable ->
            val w = thumbWidth(first + i, at, narrowW, wideOf(first + i))
            measurable.measure(Constraints.fixed(w.roundToInt().coerceAtLeast(0), stripHeight))
        }
        layout(width, stripHeight) {
            val lefts = thumbLefts(first, placeables.size, at, width.toFloat(), narrowW, wideOf, gapPx)
            placeables.forEachIndexed { i, placeable -> placeable.placeRelative(lefts[i].roundToInt(), 0) }
        }
    }
}

/** How much further than where it was let go a scrub carries, by the speed it was let go at, in seconds. */
private const val ScrubCarry = 0.12f

/** Moves the carousel to [position], a page and a fraction, on its next layout: what a scrub drives it by. */
private fun CarouselState.requestPageAt(position: Float) {
    val page = floor(position).toInt()
    val visible = listState.layoutInfo.visibleItemsInfo
    val pitch = visible.getOrNull(1)?.let { it.offset - visible[0].offset } ?: visible.firstOrNull()?.size ?: 0
    listState.requestScrollToItem(page.coerceAtLeast(0), ((position - page) * pitch).roundToInt())
}

/**
 * How wide [page]'s thumbnail is with the strip at [position]: [wide], its own
 * width in the middle, narrowing to the rest a page away.
 */
internal fun thumbWidth(page: Int, position: Float, narrow: Float, wide: Float): Float =
    narrow + (wide - narrow) * (1f - abs(page - position)).coerceIn(0f, 1f)

/**
 * How wide a thumbnail grows in the middle: a picture [ratio] wide for each of
 * its [height], kept between [narrow] and [max]; [standard] without a ratio.
 */
internal fun currentWidth(ratio: Float?, height: Float, narrow: Float, standard: Float, max: Float): Float =
    if (ratio == null || !(ratio > 0f) || ratio.isInfinite()) standard else (ratio * height).coerceIn(narrow, max)

/**
 * Where each of [count] thumbnails from [first] starts, in reading order,
 * across a strip [width] wide with it at [position]: the one there in the
 * middle, the rest a [gap] apart either side, so the strip slides as the
 * position moves and the widening one stays centred.
 */
internal fun thumbLefts(first: Int, count: Int, position: Float, width: Float, narrow: Float, wide: (Int) -> Float, gap: Float): FloatArray {
    val lefts = FloatArray(count)
    if (count == 0) return lefts
    // The one the position is at or just past, and how far toward the next.
    val anchor = floor(position).toInt()
    val fraction = position - anchor
    val anchorWidth = thumbWidth(anchor, position, narrow, wide(anchor))
    val nextWidth = thumbWidth(anchor + 1, position, narrow, wide(anchor + 1))
    val anchorLeft = width / 2f - anchorWidth / 2f - fraction * (anchorWidth / 2f + gap + nextWidth / 2f)
    // Out from the anchor both ways.
    var left = anchorLeft
    for (page in anchor downTo first) {
        if (page != anchor) left -= thumbWidth(page, position, narrow, wide(page)) + gap
        if (page - first in 0 until count) lefts[page - first] = left
    }
    left = anchorLeft
    for (page in anchor + 1 until first + count) {
        left += thumbWidth(page - 1, position, narrow, wide(page - 1)) + gap
        if (page - first in 0 until count) lefts[page - first] = left
    }
    return lefts
}

/** The page whose thumbnail is under [x], in a strip [width] wide at [position]; null in a gap or past the ends. */
internal fun pageAt(x: Float, width: Float, position: Float, pitch: Float, wide: (Int) -> Float, gap: Float, rtl: Boolean): Int? {
    val reading = if (rtl) width - x else x
    val narrow = pitch - gap
    val near = position.roundToInt()
    val first = (near - (width / pitch).toInt() - 2).coerceAtLeast(0)
    val count = (width / pitch).toInt() * 2 + 5
    val lefts = thumbLefts(first, count, position, width, narrow, wide, gap)
    for (i in 0 until count) {
        val page = first + i
        val left = lefts[i]
        if (reading >= left && reading <= left + thumbWidth(page, position, narrow, wide(page))) return page
    }
    return null
}

/** What a [ThumbnailStrip] takes by default. */
object ThumbnailStripDefaults {
    /** How tall the strip is, and so each thumbnail: a fingertip's target. */
    val Height: Dp
        @Composable @ReadOnlyComposable get() = DefaultHeight

    /** How wide a thumbnail is beside the current one: a sliver of each, so many are in view. */
    val ThumbnailWidth: Dp
        @Composable @ReadOnlyComposable get() = DefaultThumbnailWidth

    /** How wide the current page's thumbnail grows, in the middle. */
    val CurrentWidth: Dp
        @Composable @ReadOnlyComposable get() = DefaultCurrentWidth

    /** The gap between one thumbnail and the next. */
    val Gap: Dp
        @Composable @ReadOnlyComposable get() = DefaultGap
}

private val DefaultHeight = 48.dp
private val DefaultThumbnailWidth = 28.dp
private val DefaultCurrentWidth = 64.dp
private val DefaultGap = 2.dp

/**
 * The widest a current thumbnail grows for a wide picture: a little over twice
 * [DefaultCurrentWidth], about a 7:3 panorama at the strip's height. Wider ones
 * are cut to it, so one panorama does not take the strip.
 */
private val MaxCurrentWidth = 112.dp

/** The bars either side of a picture narrower than a thumbnail: black, as a photo app's. */
private val Letterbox = Color.Black
