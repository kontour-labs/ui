package io.kontour.ui.foundation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The icons [RichTextScope.icon] has put in a string, and how [Text] draws them.
 *
 * ### Why an icon travels as an id
 *
 * An `AnnotatedString` carries strings and styles, not objects, and [richText]
 * returns one — so the icon itself cannot ride in it. What rides is an inline
 * content placeholder, as `appendInlineContent` makes, whose id names the icon,
 * its tint and its size; [Text] reads those ids back and supplies the
 * `InlineTextContent` for them, so the caller never writes the map.
 *
 * The id names the icon by its index here, which is the same for the same icon
 * for the life of the process. That keeps the string [richText] builds equal to
 * the one it built last time, which is what keeps the text from being shaped
 * again on every composition.
 */
internal object InlineIcons {

    /** Marks the placeholders that are this library's icons, beside the id itself. */
    const val Tag = "io.kontour.ui.inlineIcon"

    private const val Prefix = "kontour.icon:"

    /**
     * Every icon a string has carried, in the order they were first seen.
     *
     * Append-only and copied on write, so a read never locks and a string
     * built on one thread resolves on another. Bounded by the icons an app
     * draws in text, which is a handful.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private val icons = AtomicReference(emptyList<ImageVector>())

    @OptIn(ExperimentalAtomicApi::class)
    private fun keyOf(icon: ImageVector): Int {
        while (true) {
            val known = icons.load()
            // By identity first: an icon set's glyphs are singletons, and this
            // runs on every build of the string.
            val same = known.indexOfFirst { it === icon }
            if (same >= 0) return same
            val equal = known.indexOf(icon)
            if (equal >= 0) return equal
            if (icons.compareAndSet(known, known + icon)) return known.size
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun iconAt(key: Int): ImageVector? = icons.load().getOrNull(key)

    /** The placeholder id for [icon], drawn in [tint] at [size]. */
    fun id(icon: ImageVector, tint: Color, size: TextUnit): String {
        val tinted = if (tint == Color.Unspecified) "-" else tint.value.toString(16)
        val sized = when {
            size.isSp -> "${size.value}sp"
            size.isEm -> "${size.value}em"
            else -> "1em"
        }
        return "$Prefix${keyOf(icon)}:$tinted:$sized"
    }

    /**
     * The inline content for every icon in [text], or null when it has none.
     *
     * An icon with no tint of its own is drawn in the colour of the words
     * around it: a `tone` or a link it sits inside, and otherwise
     * [textColour]. That one is read as the icon draws, so an animated text
     * colour recolours it without rebuilding the map, which would give the
     * text new placeholders to lay out.
     */
    fun contentFor(text: AnnotatedString, textColour: State<Color>): Map<String, InlineTextContent>? {
        if (!text.hasStringAnnotations(Tag, 0, text.length)) return null
        return text.getStringAnnotations(Tag, 0, text.length).mapNotNull { annotation ->
            val id = annotation.item
            parse(id)?.let { (icon, tint, size) ->
                val colour = tint.takeIf { it != Color.Unspecified } ?: spanColour(text, annotation.start, annotation.end)
                id to InlineTextContent(Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)) {
                    InlineIcon(icon, colour, textColour)
                }
            }
        }.toMap()
    }

    /**
     * The colour a span gives the text from [start] to [end], or unspecified.
     *
     * The innermost wins, as it does for the words: a span opened later over the
     * same range is listed later. A link's colour is in its styles rather than
     * in a span, and a link is inside whatever tone surrounds it.
     */
    private fun spanColour(text: AnnotatedString, start: Int, end: Int): Color {
        val link = text.getLinkAnnotations(start, end).lastOrNull { it.start <= start && end <= it.end }
            ?.item?.styles?.style?.color
        if (link != null && link != Color.Unspecified) return link
        return text.spanStyles.lastOrNull { it.start <= start && end <= it.end && it.item.color != Color.Unspecified }
            ?.item?.color ?: Color.Unspecified
    }

    private fun parse(id: String): Triple<ImageVector, Color, TextUnit>? {
        if (!id.startsWith(Prefix)) return null
        val parts = id.removePrefix(Prefix).split(':')
        if (parts.size != 3) return null
        val icon = parts[0].toIntOrNull()?.let(::iconAt) ?: return null
        val tint = if (parts[1] == "-") Color.Unspecified else Color(parts[1].toULongOrNull(16) ?: return null)
        val size = when {
            parts[2].endsWith("sp") -> parts[2].removeSuffix("sp").toFloatOrNull()?.sp
            parts[2].endsWith("em") -> parts[2].removeSuffix("em").toFloatOrNull()?.em
            else -> null
        } ?: return null
        return Triple(icon, tint, size)
    }
}

/**
 * One icon standing in its placeholder: fitted at its true proportions, as
 * [Icon] fits one, and silent — what a screen reader says for it is the
 * placeholder's alternate text, read in its place in the sentence.
 */
@Composable
private fun InlineIcon(icon: ImageVector, tint: Color, textColour: State<Color>) {
    val painter = rememberVectorPainter(icon)
    val aspect = (icon.viewportWidth / icon.viewportHeight).takeIf { it.isFinite() && it > 0f } ?: 1f
    val colour = if (tint == Color.Unspecified) textColour.value else tint
    val filter = remember(colour) { ColorFilter.tint(colour) }
    Box(Modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = filter,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 1f),
        )
    }
}
