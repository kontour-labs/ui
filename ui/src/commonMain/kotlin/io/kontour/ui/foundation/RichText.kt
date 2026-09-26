package io.kontour.ui.foundation

import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import io.kontour.ui.theme.Theme

/**
 * Text with links in it, for [Text].
 *
 * ```kotlin
 * Text(
 *     linkedText {
 *         +"Services are suspended between Perth and Midland. "
 *         link("See replacement buses") { navigate(Replacements) }
 *     }
 * )
 * ```
 *
 * **A link inside a sentence is not a
 * [TextButton][io.kontour.ui.components.action.TextButton] placed next to one.** A
 * button beside a paragraph cannot wrap with the words either side of it, it
 * cannot be read out in the order it is written, and it is announced as a button
 * rather than a link. This produces a real `LinkAnnotation`, so the text wraps
 * around it, a screen reader lists it with the page's other links, and the
 * platform's own link focus and hit-testing apply.
 *
 * ### Why this exists rather than `buildAnnotatedString` at the call site
 *
 * Because of a trap that costs more than it looks like it should.
 * `LinkAnnotation.Clickable` compares by `tag`, `styles` **and**
 * `linkInteractionListener` — and the listener **by identity**. A lambda written
 * at the call site is a new object on every composition, so the `AnnotatedString`
 * is never equal to its previous self, and `BasicText` re-runs full text layout
 * every time. Text shaping is the most expensive thing the renderer does; the
 * documentation site found this the hard way across 320 link-bearing spans, and
 * the fix is written up in `Prose.kt`.
 *
 * So the listener here is **one remembered object per call site**, shared by
 * every link in the string, which finds the right handler by the tag it was given
 * rather than by capturing it. The handlers themselves are replaced on every
 * composition — they are free to close over whatever they like — and none of that
 * reaches the string's identity. The string comes out value-equal to the one
 * before it, so nothing re-shapes, and no caller has to know any of this.
 *
 * @param styles How a link is drawn, and what it does under a pointer or a focus
 *   ring. [LinkDefaults.styles] is accent-coloured and underlined.
 */
@Composable
fun linkedText(
    styles: TextLinkStyles = LinkDefaults.styles(),
    build: LinkedTextScope.() -> Unit,
): AnnotatedString {
    val links = remember { LinkHandlers() }
    // Cleared and refilled by `build` below, in the order the links appear, and
    // the tag is the index. Running during composition is safe here because it
    // is a plain object rather than snapshot state: an abandoned composition
    // leaves a list that the next one overwrites before anything reads it.
    links.begin()
    return buildAnnotatedString {
        LinkedTextScope(this, links, styles).build()
    }
}

/** The receiver of [linkedText]. */
@LayoutScopeMarker
@Stable
class LinkedTextScope internal constructor(
    private val builder: AnnotatedString.Builder,
    private val links: LinkHandlers,
    private val styles: TextLinkStyles,
) {

    /** Plain text, in whatever style the [Text] is drawn with. */
    operator fun String.unaryPlus() {
        builder.append(this)
    }

    /** Text carrying spans of its own, merged over the [Text]'s style. */
    operator fun AnnotatedString.unaryPlus() {
        builder.append(this)
    }

    /**
     * A link reading [text], which calls [onClick] when it is followed.
     *
     * The commonest shape by a distance, and the one to reach for. [link] with a
     * content block is for a link that carries styling of its own.
     */
    fun link(text: String, onClick: () -> Unit) {
        link(onClick = onClick) { +text }
    }

    /**
     * A link whose text is built rather than given.
     *
     * `onClick` is named because the trailing lambda is the *content*, which is
     * the order every other slot in the library uses.
     */
    fun link(onClick: () -> Unit, content: LinkedTextScope.() -> Unit) {
        builder.withLink(
            LinkAnnotation.Clickable(
                tag = links.register(onClick),
                styles = styles,
                linkInteractionListener = links,
            )
        ) {
            LinkedTextScope(builder, links, styles).content()
        }
    }
}

/** How a link inside a sentence is drawn. */
object LinkDefaults {

    /**
     * Accent-coloured and underlined.
     *
     * **The underline is not decoration and is not optional by default.** A link
     * inside a paragraph that is only a different colour fails WCAG 1.4.1: a
     * reader who cannot tell the two colours apart has no way to know the words
     * are a link at all. A *standalone*
     * [TextButton][io.kontour.ui.components.action.TextButton] is a different case — it is
     * not inside a block of text, so nothing has to distinguish it from its
     * neighbours — which is why that one is not underlined and this one is.
     *
     * **Hover and press are a highlight behind the words, not a second colour on
     * them.** A `SpanStyle` is all a link gets, and a wash over the glyphs is
     * the one thing it cannot draw — so the feedback every other control in the
     * library gives with `Indication` is given here by the accent's own pair:
     * [highlight] behind, and on press [pressedColour] in front, which is the
     * tone that exists to be legible on exactly that ground. Inventing a darker
     * accent for it would be a colour nothing else in the scheme has.
     */
    @Composable
    @ReadOnlyComposable
    fun styles(
        colour: Color = Theme.colours.accent.solid,
        highlight: Color = Theme.colours.accent.container,
        pressedColour: Color = Theme.colours.accent.onContainer,
    ): TextLinkStyles = TextLinkStyles(
        style = SpanStyle(color = colour, textDecoration = TextDecoration.Underline),
        focusedStyle = SpanStyle(
            color = colour,
            background = highlight,
            textDecoration = TextDecoration.Underline,
        ),
        hoveredStyle = SpanStyle(
            color = colour,
            background = highlight,
            textDecoration = TextDecoration.Underline,
        ),
        pressedStyle = SpanStyle(
            color = pressedColour,
            background = highlight,
            textDecoration = TextDecoration.Underline,
        ),
    )
}

/**
 * One listener for every link in one [linkedText], dispatching by tag.
 *
 * See [linkedText] for why this is not a lambda per link.
 */
internal class LinkHandlers : LinkInteractionListener {
    private val handlers = mutableListOf<() -> Unit>()

    fun begin() {
        handlers.clear()
    }

    fun register(onClick: () -> Unit): String {
        handlers += onClick
        return handlers.lastIndex.toString()
    }

    override fun onClick(link: LinkAnnotation) {
        val tag = (link as? LinkAnnotation.Clickable)?.tag ?: return
        handlers.getOrNull(tag.toIntOrNull() ?: return)?.invoke()
    }
}
