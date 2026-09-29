package io.kontour.ui.foundation

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

/**
 * A gradient laid across the words it covers, rather than across the whole text.
 *
 * Compose can fill a span with a `Brush`, and sizes that brush to the *paragraph*:
 * a gradient word in the middle of a sentence showed the slice of the gradient
 * that happened to lie under it — one colour, most of the time, which is not what
 * anyone writing `gradient(…)` meant. There is no hook to size a span's brush to
 * the span, because on some platforms the brush is turned into a shader while the
 * text is still being laid out, before anyone knows where the span is.
 *
 * So [Text] draws these words itself. The text is laid out and drawn as usual with
 * the gradient's words invisible; then the same text is measured again under the
 * same constraints — so it breaks into the same lines — with **only** those words
 * visible, in one opaque colour, and drawn over it in a layer of its own. Inside
 * each span's own box the gradient is painted onto that layer with
 * [BlendMode.SrcIn], which keeps the glyphs' shape and edges and gives them the
 * gradient's colour. Nothing else is in the layer, so nothing else is touched.
 *
 * It costs a second layout, and only for a text that has a gradient in it: the
 * check is one scan of the annotations, as the inline icons' is.
 *
 * [RichTextScope.gradient] also puts the brush on the span in the ordinary way, so
 * a string drawn by something other than this library's [Text] still shows a
 * gradient, sized as Compose sizes it.
 */
internal object SpanGradients {

    /** Marks a range as a gradient this library draws. */
    const val Tag = "io.kontour.ui.spanGradient"

    /** Compose's own mark for an inline placeholder: see `appendInlineContent`. */
    private const val InlineContentTag = "androidx.compose.foundation.text.inlineContent"

    /** A brush that draws nothing, and — being a brush — replaces one. */
    private val Invisible: Brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))

    class Span(val start: Int, val end: Int, val brush: Brush)

    /** The gradients in [text], each with the brush [RichTextScope.gradient] put on it. */
    fun find(text: AnnotatedString): List<Span> =
        text.getStringAnnotations(Tag, 0, text.length).mapNotNull { mark ->
            text.spanStyles
                .lastOrNull { it.start == mark.start && it.end == mark.end && it.item.brush != null }
                ?.let { Span(mark.start, mark.end, it.item.brush!!) }
        }

    /**
     * [text] as the [Text] itself draws it: everything as it was, and the
     * gradients' words invisible, for [overlay] to draw.
     *
     * With a transparent *brush*: a later colour on a range that has a brush
     * leaves the brush in place — Compose merges them that way round — and a
     * later brush replaces it.
     */
    fun shown(text: AnnotatedString, spans: List<Span>): AnnotatedString = buildAnnotatedString {
        append(text)
        spans.forEach { addStyle(SpanStyle(brush = Invisible), it.start, it.end) }
    }

    /**
     * The same characters with the same metrics, and nothing visible but the
     * gradients' words, in one opaque colour. Links and every other annotation
     * are left out: this is only ever drawn.
     */
    fun overlay(text: AnnotatedString, spans: List<Span>): AnnotatedString = AnnotatedString(
        text = text.text,
        spanStyles = text.spanStyles.map {
            it.copy(item = it.item.copy(color = Color.Transparent, background = Color.Unspecified, shadow = null))
        } + spans.map { AnnotatedString.Range(SpanStyle(color = Color.Black), it.start, it.end) },
        paragraphStyles = text.paragraphStyles,
    )

    /** The placeholders [text] was laid out with, so the overlay leaves the same gaps. */
    fun placeholders(
        text: AnnotatedString,
        content: Map<String, InlineTextContent>,
    ): List<AnnotatedString.Range<Placeholder>> =
        text.getStringAnnotations(InlineContentTag, 0, text.length).mapNotNull { mark ->
            content[mark.item]?.let { AnnotatedString.Range(it.placeholder, mark.start, mark.end) }
        }
}

/**
 * Draws [text]'s gradients over the text this modifies. See [SpanGradients].
 *
 * Placed directly before the text's own node, so the constraints it records are
 * the ones the text was measured with, and it draws in the text's coordinates.
 */
@Composable
internal fun Modifier.spanGradients(
    text: AnnotatedString,
    spans: List<SpanGradients.Span>,
    style: TextStyle,
    overflow: TextOverflow,
    softWrap: Boolean,
    maxLines: Int,
    inlineContent: Map<String, InlineTextContent>,
): Modifier {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val overlay = remember(text, spans) { SpanGradients.overlay(text, spans) }
    val placeholders = remember(text, inlineContent) { SpanGradients.placeholders(text, inlineContent) }
    val hidden = remember(style) { style.copy(color = Color.Transparent) }
    // A plain holder: written in the layout pass, read in the draw pass that
    // follows it, and never a reason to recompose.
    val measured = remember { arrayOfNulls<Constraints>(1) }
    return this
        .layout { measurable, constraints ->
            measured[0] = constraints
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
        .drawWithContent {
            drawContent()
            val constraints = measured[0] ?: return@drawWithContent
            val layout = measurer.measure(
                text = overlay,
                style = hidden,
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                placeholders = placeholders,
                constraints = constraints,
            )
            drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
            drawText(layout)
            spans.forEach { span ->
                val end = span.end.coerceAtMost(layout.layoutInput.text.length)
                if (span.start >= end) return@forEach
                val box = layout.getPathForRange(span.start, end).getBounds()
                if (box.isEmpty) return@forEach
                // The gradient runs across the words' own box; the rect it is
                // painted in reaches a little past it, for the ink an italic or a
                // wide glyph puts beyond its advance, and the brush's ends carry
                // on into that margin.
                val margin = box.height / 4f
                translate(box.left, box.top) {
                    // A shader made for the words' box and then drawn over the
                    // larger rect, so the gradient's ends are the words' ends.
                    val fill = when (val brush = span.brush) {
                        is ShaderBrush -> ShaderBrush(brush.createShader(box.size))
                        is SolidColor -> brush
                    }
                    drawRect(
                        brush = fill,
                        topLeft = Offset(-margin, -margin),
                        size = Size(box.width + margin * 2, box.height + margin * 2),
                        blendMode = BlendMode.SrcIn,
                    )
                }
            }
            drawIntoCanvas { it.restore() }
        }
}
