package io.kontour.ui.foundation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString

/**
 * The ground behind a span — code's sunken ground, a highlighter's tint — drawn
 * by [Text] behind the selection rather than by the paragraph over it.
 *
 * A `SpanStyle.background` is painted by the paragraph as it draws its glyphs,
 * and `BasicText` draws a selection's highlight *before* the paragraph. So an
 * opaque ground covered the highlight: a selection dragged across a sentence
 * with `code` in it lit up every word but the code, which was still selected —
 * it copied — and only looked as if it had been left out. Reported at the start
 * of 0.5.0.
 *
 * So [Text] takes the grounds off the string it hands to `BasicText` and paints
 * them itself, from the same layout, underneath. The order is then the ground,
 * the selection over it, and the glyphs over both, which is what a reader expects
 * of a highlighter pen and of a selection alike.
 *
 * The shape of each ground is the paragraph's own path for its range — line by
 * line, the box a selection of the same characters would fill — so a span that
 * wraps grounds both its halves, and a ground and the highlight over it are the
 * same shape.
 *
 * A link's grounds are not among them. Its focus ground comes from
 * `TextLinkStyles`, which Compose applies inside `BasicText`, out of reach, and
 * its hover and press grounds are `LinkPress`'s, drawn over these. All three are
 * drawn only while the pointer or focus is on the link, which is not when a
 * reader is dragging a selection across it.
 */
internal object SpanGrounds {

    class Span(val start: Int, val end: Int, val colour: Color)

    /**
     * The grounds' shapes for the layout last reported: written in the layout
     * pass, read in the draw pass that follows it, and never a reason to
     * recompose — the same holder [spanGradients] keeps its constraints in.
     */
    class Painted {
        var paths: List<Pair<Path, Color>> = emptyList()
    }

    /**
     * Whether [text] has any ground to draw. One pass over its span styles and
     * no allocation, because it runs for every rich `Text` on every composition
     * and nearly none of them has one.
     */
    fun any(text: AnnotatedString): Boolean = text.spanStyles.any { it.item.background.isVisible }

    /** The grounds in [text], in the order they were added, which is the order they paint. */
    fun find(text: AnnotatedString): List<Span> = text.spanStyles.mapNotNull { range ->
        val colour = range.item.background
        if (colour.isVisible && range.start < range.end) Span(range.start, range.end, colour) else null
    }

    /**
     * [text] with every ground made transparent, and everything else — links,
     * inline content, the annotations [SpanGradients] and the icons look for —
     * exactly as it was.
     *
     * Added as a later style rather than by rebuilding the span list, so that no
     * annotation can be dropped on the way through: a later background replaces
     * an earlier one when Compose merges them, and a transparent one paints
     * nothing.
     */
    fun shown(text: AnnotatedString, spans: List<Span>): AnnotatedString = buildAnnotatedString {
        append(text)
        spans.forEach { addStyle(SpanStyle(background = Color.Transparent), it.start, it.end) }
    }

    /** Each ground's shape in [layout], with its colour, ready to fill. */
    fun paths(layout: TextLayoutResult, spans: List<Span>): List<Pair<Path, Color>> {
        val length = layout.layoutInput.text.length
        return spans.mapNotNull { span ->
            val end = span.end.coerceAtMost(length)
            if (span.start >= end) null else layout.getPathForRange(span.start, end) to span.colour
        }
    }

    private val Color.isVisible: Boolean get() = this != Color.Unspecified && alpha > 0f
}
