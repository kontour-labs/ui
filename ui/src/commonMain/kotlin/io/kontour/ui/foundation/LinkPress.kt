package io.kontour.ui.foundation

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How far a pressed word's ground reaches past its glyphs, either side.
 *
 * Enough for the ground to clear the first and last letters and no more. It is
 * drawn, never laid out, so it costs the words beside it nothing; a
 * [TextButton][io.kontour.ui.components.action.TextButton] pads its label by the
 * same, and a link's ground is drawn out to it, so the two look the same.
 */
internal val GroundClearance: Dp = 2.dp

/**
 * The press and hover of the links in one [Text], drawn behind just the link
 * under the finger, line box by line box.
 *
 * Asked for for 0.5.2, with the text button's, as *"make sure it works well with
 * all the inline text button and stuff that we do"*. A link's own pressed look
 * is a `TextLinkStyles.pressedStyle`, which Compose applies while its press is
 * down, and a tap is a press and a release in one frame — inside a scrolling
 * column, where the press is held back to see whether it is a scroll, always.
 * So a link answered a mouse and never a thumb, exactly as the text button did.
 *
 * Nothing here can reach the link's own press: Compose makes its interaction
 * source inside `BasicText` and keeps it. So this watches the pointer over the
 * text itself, without consuming anything, and turns what it sees over a link
 * into presses and hovers of an interaction source of its own, which the shared
 * ground indication answers — with the floor that holds a tap long enough to be
 * seen, hover only for a pointer that can, and the theme's own fade. The ground
 * is the text button's, in its shape, from the theme.
 *
 * It follows `clickable`'s reading of a touch. A finger that lands on a link
 * is not yet a press: if it is still down and still on the link after
 * [PressDelay], it is; if it lifts first, it was a tap, and is answered then; if
 * it moves past the slop or something above it scrolls, it was never a press at
 * all, and a page scrolled by a finger that happened to land on a link does not
 * light the link up on its way past.
 *
 * Only for a link whose styles give it a pressed look: [LinkDefaults.styles]
 * does. A caller's own `TextLinkStyles(style = …)` with none is drawn as it asked
 * — the documentation site's prose links are that.
 *
 * @param links Each link's characters, start inclusive and end exclusive.
 */
internal class LinkPress(private val links: List<IntRange>, private val shape: Shape) {

    /** What the indication answers. Nothing outside this class writes to it. */
    val interactions = MutableInteractionSource()

    /**
     * The text's layout, as last reported: written in the layout pass, read by
     * the pointer and the draw after it — the holder `SpanGrounds.Painted` is.
     */
    var layout: TextLayoutResult? = null

    /**
     * The link the ground is behind, once one has been pressed or hovered.
     *
     * State, so that moving from one link to the next redraws: a pointer that
     * crosses from one to another is a hover ending and a hover starting at the
     * same alpha, which animates nothing and so asks for no frame of its own.
     * Left where it was when the press or hover ends, so the ground fades out
     * from behind the words it was behind.
     */
    private var active by mutableStateOf<IntRange?>(null)

    /** Behind the link: each line's box, in the theme's shape. */
    val ground: Shape = PathShape(shaped = true)

    /**
     * Where the pressed ink goes: the same boxes, square, so a letter in a
     * rounded corner is recoloured whole rather than in part.
     *
     * It recolours whatever the text drew inside them, and a selection's
     * highlight is drawn by the text: a link selected and pressed at once has
     * its highlight in the ink for the length of the press. Pressing a link
     * does not start a selection, so that takes a selection made first and a
     * press on it after, and it lasts as long as the press.
     */
    val ink: Shape = PathShape(shaped = false)

    /**
     * Watches the pointer over the text, and consumes nothing: the link's own
     * press is Compose's, and a selection's drag is the selection's.
     *
     * Read on the final pass, after everything under and over the text has had
     * the event — so a move a scroller has claimed arrives here consumed, and is
     * taken for what it is.
     */
    suspend fun track(scope: PointerInputScope) = coroutineScope {
        val later = this
        scope.awaitPointerEventScope {
            var down: PointerId? = null
            var downAt = Offset.Zero
            var downLink: IntRange? = null
            var pending: Job? = null
            var press: PressInteraction.Press? = null
            var hover: HoverInteraction.Enter? = null
            var hoverLink: IntRange? = null

            fun begin(link: IntRange, at: Offset) {
                active = link
                val begun = PressInteraction.Press(at)
                press = begun
                interactions.tryEmit(begun)
            }

            fun end(followed: Boolean) {
                pending?.cancel()
                pending = null
                press?.let { interactions.tryEmit(if (followed) PressInteraction.Release(it) else PressInteraction.Cancel(it)) }
                press = null
                down = null
                downLink = null
            }

            fun hoverOver(link: IntRange?) {
                if (link == hoverLink) return
                hover?.let { interactions.tryEmit(HoverInteraction.Exit(it)) }
                hover = null
                hoverLink = link
                if (link != null) {
                    if (press == null) active = link
                    val entered = HoverInteraction.Enter()
                    hover = entered
                    interactions.tryEmit(entered)
                }
            }

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val pointer = event.changes.firstOrNull() ?: continue
                when (event.type) {
                    PointerEventType.Press -> if (down == null) {
                        val link = linkAt(pointer.position)
                        if (link != null) {
                            down = pointer.id
                            downAt = pointer.position
                            downLink = link
                            if (pointer.type == PointerType.Mouse) {
                                // A mouse button is not the start of a scroll.
                                begin(link, pointer.position)
                            } else {
                                pending = later.launch {
                                    delay(PressDelay)
                                    pending = null
                                    begin(link, downAt)
                                }
                            }
                        }
                    }
                    PointerEventType.Move -> {
                        val held = event.changes.firstOrNull { it.id == down }
                        if (held != null && (
                                held.isConsumed ||
                                    (held.position - downAt).getDistance() > viewConfiguration.touchSlop ||
                                    linkAt(held.position) != downLink
                                )
                        ) {
                            end(followed = false)
                        }
                        if (pointer.type == PointerType.Mouse) hoverOver(linkAt(pointer.position))
                    }
                    PointerEventType.Release -> {
                        val lifted = event.changes.firstOrNull { it.id == down && !it.pressed }
                        val link = downLink
                        if (lifted != null && link != null) {
                            // Up before the delay ran out: a tap, which is the
                            // press it would have been, answered now.
                            if (pending != null) {
                                pending?.cancel()
                                pending = null
                                begin(link, downAt)
                            }
                            end(followed = true)
                        }
                    }
                    PointerEventType.Enter -> if (pointer.type == PointerType.Mouse) hoverOver(linkAt(pointer.position))
                    PointerEventType.Exit -> hoverOver(null)
                }
            }
        }
    }

    /**
     * The link under [position], by the character under it — the same answer
     * Compose's own hit test gives, whose target for a link is the boxes of its
     * characters.
     *
     * `getOffsetForPosition` is a caret position, between two characters, so the
     * character under the pointer is the one after it or the one before.
     */
    private fun linkAt(position: Offset): IntRange? {
        val layout = layout ?: return null
        if (position.y < 0f || position.y >= layout.size.height) return null
        val line = layout.getLineForVerticalPosition(position.y)
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        val caret = layout.getOffsetForPosition(position)
        for (character in intArrayOf(caret, caret - 1)) {
            if (character < lineStart || character >= lineEnd) continue
            val box = layout.getBoundingBox(character)
            if (position.x >= box.left && position.x <= box.right) {
                return links.firstOrNull { character in it }
            }
        }
        return null
    }

    /**
     * [link]'s box on each line it is on, from the paragraph's own path for the
     * part of it on that line: a link that wraps has two, each as tall as its
     * line, and the space the line broke at is in neither.
     */
    private fun boxes(layout: TextLayoutResult, link: IntRange): List<Rect> {
        val length = layout.layoutInput.text.length
        val start = link.first.coerceAtMost(length)
        val end = (link.last + 1).coerceAtMost(length)
        if (start >= end) return emptyList()
        val last = layout.lineCount - 1
        val first = layout.getLineForOffset(start).coerceAtMost(last)
        val final = layout.getLineForOffset(end - 1).coerceAtMost(last)
        return (first..final).mapNotNull { line ->
            val from = maxOf(start, layout.getLineStart(line))
            val to = minOf(end, layout.getLineEnd(line, visibleEnd = true))
            if (from >= to) null else layout.getPathForRange(from, to).getBounds()
        }
    }

    private var cached: Cached? = null

    private class Cached(
        val layout: TextLayoutResult,
        val link: IntRange,
        val density: Float,
        val direction: LayoutDirection,
        val shaped: Path,
        val square: Path,
    )

    private fun path(
        layout: TextLayoutResult,
        link: IntRange,
        density: Density,
        direction: LayoutDirection,
        shaped: Boolean,
    ): Path {
        val hit = cached?.takeIf {
            it.layout === layout && it.link == link && it.density == density.density && it.direction == direction
        } ?: run {
            val clearance = with(density) { GroundClearance.toPx() }
            val rounded = Path()
            val square = Path()
            for (box in boxes(layout, link)) {
                val wide = Rect(box.left - clearance, box.top, box.right + clearance, box.bottom)
                square.addRect(wide)
                val outline = shape.createOutline(wide.size, direction, density)
                rounded.addPath(Path().apply { addOutline(outline) }, wide.topLeft)
            }
            Cached(layout, link, density.density, direction, rounded, square).also { cached = it }
        }
        return if (shaped) hit.shaped else hit.square
    }

    /**
     * A shape that is a path in the text's own coordinates, whatever size it is
     * asked for at: the indication is the text's size, and so are they.
     */
    private inner class PathShape(private val shaped: Boolean) : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val layout = layout
            val link = active
            return Outline.Generic(
                if (layout == null || link == null) Path() else path(layout, link, density, layoutDirection, shaped),
            )
        }
    }

    companion object {
        /**
         * The links in [text] that have a pressed look of their own to answer
         * with. Empty for a string with none, which is most strings with links
         * that were not made by [richText].
         */
        fun find(text: AnnotatedString): List<IntRange> =
            text.getLinkAnnotations(0, text.length)
                .filter { it.start < it.end && it.item.styles?.pressedStyle != null }
                .map { it.start until it.end }

        /**
         * How long a finger on a link waits to be a press: Android's tap
         * timeout, the one `clickable` holds a press back by inside a scroller.
         * A finger that lifts sooner is answered as it lifts.
         */
        private val PressDelay = 100.milliseconds
    }
}
