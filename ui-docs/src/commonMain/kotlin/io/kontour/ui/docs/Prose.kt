package io.kontour.ui.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Text
import androidx.compose.ui.unit.Dp
import io.kontour.ui.theme.Theme

/**
 * Which page is being drawn, for the links in it.
 *
 * A relative link means nothing without the page that wrote it: `../tokens.md`
 * is `tokens` from `components/button` and nothing at all from `tokens`. The
 * resolution happens inside `buildAnnotatedString`, five composables below the
 * one that knows the answer, and the same pattern the library uses for
 * `LocalWindowSizeClass` is the right one here for the same reason — the
 * alternative is a parameter threaded through five functions with no other use
 * for it.
 *
 * No default worth having, so it throws: a link resolved against the wrong page
 * fails quietly and sends the reader somewhere plausible and wrong, which is
 * worse than a stack trace in a test.
 */
val LocalDocPath = compositionLocalOf<String> { error("No LocalDocPath — a page must provide its own path") }

/** The blocks of one page, drawn. */
@Composable
fun Prose(blocks: List<Block>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        blocks.forEach { block -> Block(block) }
    }
}

@Composable
private fun Block(block: Block) {
    when (block) {
        is Block.Heading -> Text(
            text = annotate(block.spans, heading = true),
            style = when (block.level) {
                1, 2 -> Theme.typography.headlineSmall
                3 -> Theme.typography.titleMedium
                else -> Theme.typography.titleSmall
            },
            modifier = Modifier.padding(top = Theme.spacing.md),
        )

        is Block.Paragraph -> Linkable(block.spans, Theme.typography.bodyMedium)

        is Block.Quote -> Row(Modifier.fillMaxWidth()) {
            // A rule down the side rather than an indent, so the quoted passage
            // is visibly an aside at any width — an indent alone reads as a
            // paragraph that happens to start late.
            Box(
                Modifier
                    .width(Theme.sizing.borderWidthStrong)
                    .background(Theme.colors.outline)
                    .padding(vertical = Theme.spacing.xs)
            ) { Text(" ", style = Theme.typography.bodyMedium) }
            Box(Modifier.padding(start = Theme.spacing.md)) {
                Linkable(block.spans, Theme.typography.bodySmall, Theme.colors.contentMuted)
            }
        }

        is Block.Bullets -> Column(
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            block.items.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = if (block.ordered) "${index + 1}." else "·",
                        style = Theme.typography.bodyMedium,
                        color = Theme.colors.contentMuted,
                        modifier = Modifier.width(Theme.spacing.lg),
                    )
                    Linkable(item, Theme.typography.bodyMedium)
                }
            }
        }

        is Block.Code -> CodeBlock(block)

        is Block.Table -> DocTable(block)

        Block.Rule -> HorizontalDivider()
    }
}

/**
 * A fenced block, scrolling sideways rather than wrapping.
 *
 * Wrapped Kotlin is Kotlin that has lost its indentation, and indentation is
 * most of how a nested builder reads. A scrollbar is the honest answer on a
 * narrow window.
 */
@Composable
private fun CodeBlock(block: Block.Code) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Theme.colors.surfaceSunken, Theme.shapes.small)
            .padding(Theme.spacing.md)
            .horizontalScroll(rememberScrollState())
    ) {
        Text(
            text = block.code,
            style = Theme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Theme.colors.content,
            softWrap = false,
        )
    }
}

/**
 * The first row is the header, when it has any words in it.
 *
 * ### Weighted where the columns fit, scrolling where they do not
 *
 * Every column used to take `weight(1f)` with no floor, so on a 390dp phone a
 * three-column table became three 100dp columns of wrapped single words —
 * `button.md`'s variant table ran its first cell to six lines. Below the point
 * where the columns can hold a phrase, the table scrolls sideways instead.
 *
 * **Not always-scrolling**, which would be simpler and wrong: `horizontalScroll`
 * measures its children at *infinite* width, which silently turns every ceiling
 * above it into a fixed width. That is the trap `DeviceStrip`'s KDoc in
 * `ShowcaseLayout` documents at length, and it is why this asks
 * `BoxWithConstraints` first rather than reaching for the scroller by default.
 */
@Composable
private fun DocTable(block: Block.Table) {
    val columns = block.rows.maxOfOrNull { it.size } ?: return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fits = maxWidth >= MinColumnWidth * columns
        DocTableRows(
            block = block,
            columns = columns,
            modifier = if (fits) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.horizontalScroll(rememberScrollState())
            },
            columnWidth = if (fits) null else MinColumnWidth,
        )
    }
}

/** Narrower than this and a cell holds one word per line rather than a phrase. */
private val MinColumnWidth = 148.dp

@Composable
private fun DocTableRows(
    block: Block.Table,
    columns: Int,
    modifier: Modifier,
    columnWidth: Dp?,
) {
    Column(
        modifier
            .background(Theme.colors.surfaceSunken, Theme.shapes.small)
            .padding(Theme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        block.rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            Row(
                Modifier.padding(vertical = Theme.spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                repeat(columns) { column ->
                    Box(
                        if (columnWidth == null) Modifier.weight(1f) else Modifier.width(columnWidth),
                    ) {
                        Linkable(
                            spans = row.getOrNull(column).orEmpty(),
                            style = Theme.typography.bodySmall,
                            color = if (index == 0) {
                                Theme.colors.contentMuted
                            } else {
                                Theme.colors.content
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Prose whose links can be followed.
 *
 * The link is a `LinkAnnotation` on a range rather than a tap handler that
 * works out which range was hit — which is the same thing said by the text
 * layer instead of by this code, and it is what gets a link keyboard focus and
 * a pointer cursor for free.
 *
 * Where it goes is decided at render time by `routeForLink`, against
 * [LocalDocPath]: anything under `ui-docs/content/` is a route within the site,
 * and anything else — a contributor page, a source file — is the file on GitHub.
 * These pages are read here *and* there, so the markdown stores a relative path
 * and neither reading is the stored one.
 */
@Composable
private fun Linkable(
    spans: List<Span>,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color = Theme.colors.content,
) {
    Text(text = annotate(spans), style = style, color = color)
}

@Composable
private fun annotate(spans: List<Span>, heading: Boolean = false): AnnotatedString {
    val from = LocalDocPath.current
    val code = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = if (heading) {
            androidx.compose.ui.graphics.Color.Transparent
        } else {
            Theme.colors.surfaceSunken
        },
    )
    val link = TextLinkStyles(
        style = SpanStyle(
            color = Theme.colors.accent.solid,
            textDecoration = TextDecoration.Underline,
        )
    )
    return buildAnnotatedString { emit(spans, code, link, from) }
}

/**
 * The spans, appended — recursing through emphasis.
 *
 * Bold and italic hold spans rather than a string, because "**Reach for
 * [`Chip`](chip.md) instead**" is the commonest sentence on these pages and a
 * flat string is why twelve of them rendered with the brackets and the filename
 * showing.
 */
private fun AnnotatedString.Builder.emit(
    spans: List<Span>,
    code: SpanStyle,
    link: TextLinkStyles,
    from: String,
) {
    spans.forEach { span ->
        when (span) {
            is Span.Plain -> append(span.text)
            is Span.Code -> withStyleOf(code) { append(span.text) }
            is Span.Strong -> withStyleOf(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                emit(span.spans, code, link, from)
            }
            is Span.Emphasis -> withStyleOf(SpanStyle(fontStyle = FontStyle.Italic)) {
                emit(span.spans, code, link, from)
            }
            is Span.Link -> {
                val target = span.target
                withLink(
                    LinkAnnotation.Clickable(
                        tag = target,
                        styles = link,
                        linkInteractionListener = {
                            val route = routeForLink(target, from)
                            if (route != null) {
                                navigate(route)
                            } else {
                                openExternal(externalUrl(target, from))
                            }
                        },
                    )
                ) { emit(span.spans, code, link, from) }
            }
        }
    }
}

/** `withStyle` under a name that does not collide with the builder's own. */
private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleOf(
    style: SpanStyle,
    block: () -> Unit,
) {
    pushStyle(style)
    block()
    pop()
}
