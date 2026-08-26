package io.kontour.ui.foundation

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import io.kontour.ui.theme.Theme

/**
 * Draws text.
 *
 * The `Text` you reach for by default. It resolves its style and colour from
 * the surrounding theme, so most calls are just the string:
 *
 * ```
 * Text("Departures")                                        // inherits
 * Text("Departures", style = Theme.typography.titleMedium)  // explicit role
 * Text("4 min", color = Theme.colors.contentMuted)          // explicit colour
 * ```
 *
 * Resolution order for colour: the [color] parameter, then [style]'s colour,
 * then [LocalContentColor] (which [Surface] sets from the ground it paints),
 * then `Theme.colors.content`. That chain is why text on a dark card is light
 * without the call site knowing the card is dark.
 *
 * Wraps foundation's `BasicText`. The individual overrides — [fontSize],
 * [fontWeight] and friends — exist for one-off adjustments and are merged over
 * [style]; reaching for them repeatedly is a sign the type scale is missing a
 * role.
 *
 * @param maxLines Text beyond this is truncated per [overflow]. Be careful
 *   pairing a low value with a fixed height: at 200% font scale the text grows
 *   and the container must be allowed to grow with it.
 * @param onTextLayout Called with the layout result. For measuring, not for
 *   reacting to content — it fires during layout.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = resolveTextStyle(
            style = style,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
        ),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

/**
 * Draws styled text.
 *
 * The [AnnotatedString] overload, for text with mixed styling, inline content,
 * or clickable spans. Prefer building the string with `buildAnnotatedString` and
 * `LinkAnnotation` for links, so the platform gets real link semantics rather
 * than a tap handler a screen reader cannot see.
 */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = resolveTextStyle(
            style = style,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
        ),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@Composable
private fun resolveTextStyle(
    style: TextStyle,
    color: Color,
    fontSize: TextUnit,
    fontStyle: FontStyle?,
    fontWeight: FontWeight?,
    fontFamily: FontFamily?,
    letterSpacing: TextUnit,
    textDecoration: TextDecoration?,
    textAlign: TextAlign?,
    lineHeight: TextUnit,
): TextStyle {
    val inherited = LocalContentColor.current
    val themeContent = Theme.colors.content

    val resolvedColor = when {
        color.isSpecified -> color
        style.color.isSpecified -> style.color
        inherited.isSpecified -> inherited
        else -> themeContent
    }

    // `TextStyle.merge` has no fast path of its own: given nine overrides it
    // builds a `SpanStyle` and a `ParagraphStyle` from them, resolves some
    // thirty fields one at a time and allocates a `TextStyle` for the answer —
    // whether or not any of the nine was actually set. This runs once per `Text`
    // per composition, and a documentation page holds several hundred of them.
    //
    // Nothing it depends on is hidden, so all of it can be remembered. The
    // overwhelmingly common call is `Text("…")` with every override left at its
    // default and only the colour to resolve, and that case gets the two-key
    // `remember`, which compares two values and allocates nothing.
    return if (
        fontSize.isUnspecified &&
        fontWeight == null &&
        fontStyle == null &&
        fontFamily == null &&
        letterSpacing.isUnspecified &&
        textDecoration == null &&
        textAlign == null &&
        lineHeight.isUnspecified
    ) {
        remember(style, resolvedColor) { style.copy(color = resolvedColor) }
    } else {
        remember(
            style,
            resolvedColor,
            fontSize,
            fontWeight,
            fontStyle,
            fontFamily,
            letterSpacing,
            textDecoration,
            textAlign,
            lineHeight,
        ) {
            style.merge(
                color = resolvedColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textDecoration = textDecoration,
                textAlign = textAlign ?: TextAlign.Unspecified,
                lineHeight = lineHeight,
            )
        }
    }
}

private val Color.isSpecified: Boolean get() = this != Color.Unspecified
