package io.kontour.ui.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
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
import io.kontour.ui.theme.Theme

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

/** The first row is the header, when it has any words in it. */
@Composable
private fun DocTable(block: Block.Table) {
    val columns = block.rows.maxOfOrNull { it.size } ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.colors.surfaceSunken, Theme.shapes.small)
            .padding(Theme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        block.rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(vertical = Theme.spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                repeat(columns) { column ->
                    Box(Modifier.weight(1f)) {
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
 * Where it goes is decided at render time by `routeForLink`: a sibling page is
 * a route within the site, and anything else is the file in the repository.
 * These pages are read here *and* on GitHub, so the markdown stores a relative
 * path and neither reading is the stored one.
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
private fun annotate(spans: List<Span>, heading: Boolean = false): AnnotatedString =
    buildAnnotatedString {
        val code = SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = if (heading) {
                androidx.compose.ui.graphics.Color.Transparent
            } else {
                Theme.colors.surfaceSunken
            },
        )
        spans.forEach { span ->
            when (span) {
                is Span.Plain -> append(span.text)
                is Span.Code -> withStyleOf(code) { append(span.text) }
                is Span.Strong -> withStyleOf(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(span.text)
                }
                is Span.Emphasis -> withStyleOf(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(span.text)
                }
                is Span.Link -> {
                    val target = span.target
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = target,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = Theme.colors.accent.solid,
                                    textDecoration = TextDecoration.Underline,
                                )
                            ),
                            linkInteractionListener = {
                                val route = routeForLink(target)
                                if (route != null) {
                                    navigate(route)
                                } else {
                                    openExternal(externalUrl(target))
                                }
                            },
                        )
                    ) { append(span.text) }
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
