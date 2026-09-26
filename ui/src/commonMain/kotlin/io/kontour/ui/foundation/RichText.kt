package io.kontour.ui.foundation

import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import io.kontour.ui.theme.ColourScheme
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone

/**
 * Text with emphasis, code and links in it, for [Text].
 *
 * ```kotlin
 * Text(
 *     richText {
 *         +"The "; bold("950"); +" leaves in "; tone(Tone.Accent, "4 minutes"); +". "
 *         link("See replacement buses") { navigate(Replacements) }
 *     }
 * )
 * ```
 *
 * Every verb draws from the theme — the bold weight the type scale has, the mono
 * family on the sunken ground for [code][RichTextScope.code], a tone's own text
 * colour — so a span looks like the rest of the library without the call site
 * choosing a weight or a hex value. That is the mistake it deletes: a
 * hand-built `SpanStyle(fontWeight = FontWeight.Bold)` is a weight the face may
 * not have, and `FontFamily.Monospace` is not the theme's mono.
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
 * [RichTextScope.link] with a `url` needs no listener at all.
 *
 * @param styles How each verb is drawn. [RichTextDefaults.styles] takes them from
 *   the theme.
 */
@Composable
fun richText(
    styles: RichTextStyles = RichTextDefaults.styles(),
    build: RichTextScope.() -> Unit,
): AnnotatedString {
    val links = remember { LinkHandlers() }
    val colours = Theme.colours
    // Cleared and refilled by `build` below, in the order the links appear, and
    // the tag is the index. Running during composition is safe here because it
    // is a plain object rather than snapshot state: an abandoned composition
    // leaves a list that the next one overwrites before anything reads it.
    links.begin()
    return buildAnnotatedString {
        RichTextScope(this, links, styles, colours).build()
    }
}

/**
 * [source] read as inline Markdown: `**bold**`, `*italic*`, `` `code` ``,
 * `~~struck~~` and `[links](https://…)`.
 *
 * ```kotlin
 * Text(markdownText(strings.delayNotice)) // "Services on the **Midland** line are delayed. [Details](https://…)"
 * ```
 *
 * For strings an app already holds — above all translated ones, where the
 * emphasis has to move with the words when another language reorders them. A
 * bold span assembled around concatenated fragments cannot be translated; a
 * Markdown string can. Only the inline half is read: a `#` or a `-` at the start
 * of a line is left as written, because headings and lists are layout. See
 * [RichTextScope.markdown] for the grammar and for mixing Markdown with the
 * other verbs.
 *
 * The parse is remembered against [source], so a recomposition does not read the
 * string again, and the result is value-equal between compositions.
 *
 * @param onLinkClick Called with the address when a link is followed. Null, the
 *   default, opens it with the platform's `UriHandler`.
 */
@Composable
fun markdownText(
    source: String,
    styles: RichTextStyles = RichTextDefaults.styles(),
    onLinkClick: ((String) -> Unit)? = null,
): AnnotatedString {
    val spans = remember(source) { parseInlineMarkdown(source) }
    return richText(styles) { appendSpans(spans, onLinkClick) }
}

/** The receiver of [richText]. */
@LayoutScopeMarker
@Stable
class RichTextScope internal constructor(
    private val builder: AnnotatedString.Builder,
    private val links: LinkHandlers,
    private val styles: RichTextStyles,
    private val colours: ColourScheme,
) {

    /** Plain text, in whatever style the [Text] is drawn with. */
    operator fun String.unaryPlus() {
        builder.append(this)
    }

    /** Text carrying spans of its own, merged over the [Text]'s style. */
    operator fun AnnotatedString.unaryPlus() {
        builder.append(this)
    }

    /** [text] in the theme's bold weight. */
    fun bold(text: String) = bold { +text }

    /** Whatever [content] appends, in the theme's bold weight. */
    fun bold(content: RichTextScope.() -> Unit) = styled(styles.bold, content)

    /** [text] in italic. */
    fun italic(text: String) = italic { +text }

    /** Whatever [content] appends, in italic. */
    fun italic(content: RichTextScope.() -> Unit) = styled(styles.italic, content)

    /** [text] struck through: a price that no longer applies, a cancelled stop. */
    fun strikethrough(text: String) = strikethrough { +text }

    /** Whatever [content] appends, struck through. */
    fun strikethrough(content: RichTextScope.() -> Unit) = styled(styles.strikethrough, content)

    /**
     * [text] as code, in the theme's mono family on the sunken ground.
     *
     * No content form: code is a literal, and a bold word inside one is a
     * different thing that the page should say in words.
     */
    fun code(text: String) {
        builder.withStyle(styles.code) { append(text) }
    }

    /**
     * [text] in [tone]'s own text colour — the one a [Tag][io.kontour.ui.components.display.Tag]
     * of that tone is lettered in, which is held to 4.5:1 against its tint and
     * the page.
     */
    fun tone(tone: Tone, text: String) = tone(tone) { +text }

    /** Whatever [content] appends, in [tone]'s own text colour. */
    fun tone(tone: Tone, content: RichTextScope.() -> Unit) =
        styled(SpanStyle(color = colours.textFor(tone)), content)

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
     * A link reading [text] that opens [url] with the platform's `UriHandler`.
     *
     * Only `http`, `https`, `mailto` and `tel` addresses become links; anything
     * else is drawn as [text] alone. There is no lambda here at all, so there is
     * nothing whose identity could change between compositions.
     */
    fun link(text: String, url: String) {
        if (!isLinkableUrl(url)) {
            builder.append(text)
            return
        }
        builder.withLink(LinkAnnotation.Url(url, styles.links)) { append(text) }
    }

    /**
     * A link whose text is built rather than given.
     *
     * `onClick` is named because the trailing lambda is the *content*, which is
     * the order every other slot in the library uses.
     */
    fun link(onClick: () -> Unit, content: RichTextScope.() -> Unit) {
        builder.withLink(
            LinkAnnotation.Clickable(
                tag = links.register(onClick),
                styles = styles.links,
                linkInteractionListener = links,
            )
        ) {
            content()
        }
    }

    /**
     * [source] read as inline Markdown, appended where this call is.
     *
     * | Written | Drawn |
     * |---|---|
     * | `**bold**`, `__bold__` | [bold] |
     * | `*italic*`, `_italic_` | [italic] |
     * | `` `code` `` | [code] |
     * | `~~struck~~` | [strikethrough] |
     * | `[label](https://…)` | a link, the label read the same way |
     * | `\*` | a literal `*` — any punctuation can be escaped |
     *
     * A delimiter that does not close is text, so a stray asterisk shows as one;
     * an underscore inside a word is a letter, so `snake_case` survives. Only the
     * inline half is read — headings, lists and quotes are layout.
     *
     * @param onLinkClick Called with the address when a link is followed. Null
     *   opens it with the platform's `UriHandler`.
     */
    fun markdown(source: String, onLinkClick: ((String) -> Unit)? = null) {
        appendSpans(parseInlineMarkdown(source), onLinkClick)
    }

    internal fun appendSpans(spans: List<InlineSpan>, onLinkClick: ((String) -> Unit)?) {
        for (span in spans) {
            when (span) {
                is InlineSpan.Plain -> builder.append(span.text)
                is InlineSpan.Code -> code(span.text)
                is InlineSpan.Bold -> bold { appendSpans(span.children, onLinkClick) }
                is InlineSpan.Italic -> italic { appendSpans(span.children, onLinkClick) }
                is InlineSpan.Strikethrough -> strikethrough { appendSpans(span.children, onLinkClick) }
                is InlineSpan.Link -> {
                    val annotation = if (onLinkClick == null) {
                        LinkAnnotation.Url(span.url, styles.links)
                    } else {
                        val url = span.url
                        LinkAnnotation.Clickable(
                            tag = links.register { onLinkClick(url) },
                            styles = styles.links,
                            linkInteractionListener = links,
                        )
                    }
                    builder.withLink(annotation) { appendSpans(span.children, onLinkClick) }
                }
            }
        }
    }

    private fun styled(style: SpanStyle, content: RichTextScope.() -> Unit) {
        builder.withStyle(style) { content() }
    }
}

/**
 * How each [RichTextScope] verb is drawn.
 *
 * @property bold [RichTextScope.bold]: a weight, and nothing else, so it merges
 *   with whatever style the [Text] already has.
 * @property code [RichTextScope.code]: a family and a ground.
 * @property links Every link, hover, focus and press included.
 */
@Immutable
data class RichTextStyles(
    val bold: SpanStyle,
    val italic: SpanStyle,
    val strikethrough: SpanStyle,
    val code: SpanStyle,
    val links: TextLinkStyles,
)

/** What [richText] and [markdownText] draw with by default. */
object RichTextDefaults {

    /**
     * The theme's own: SemiBold, which the brand face ships, rather than a Bold
     * the renderer may have to synthesise; the mono family on `surfaceSunken`
     * for code; and [LinkDefaults.styles] for links.
     */
    @Composable
    @ReadOnlyComposable
    fun styles(
        bold: SpanStyle = SpanStyle(fontWeight = FontWeight.SemiBold),
        italic: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic),
        strikethrough: SpanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
        code: SpanStyle = SpanStyle(
            fontFamily = Theme.typography.mono.fontFamily,
            fontFeatureSettings = Theme.typography.mono.fontFeatureSettings,
            background = Theme.colours.surfaceSunken,
        ),
        links: TextLinkStyles = LinkDefaults.styles(),
    ): RichTextStyles = RichTextStyles(bold, italic, strikethrough, code, links)
}

/** A tone's text colour: what a tag of that tone is lettered in. */
private fun ColourScheme.textFor(tone: Tone): Color = when (tone) {
    Tone.Neutral -> contentMuted
    Tone.Info -> info.onContainer
    Tone.Accent -> accent.onContainer
    Tone.Success -> success.onContainer
    Tone.Warning -> warning.onContainer
    Tone.Danger -> danger.onContainer
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
 * One listener for every link in one [richText], dispatching by tag.
 *
 * See [richText] for why this is not a lambda per link.
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
