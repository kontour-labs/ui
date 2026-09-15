package io.kontour.ui.foundation

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import io.kontour.ui.components.display.skeletonFill
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
 * Text("4 min", colour = Theme.colours.contentMuted)          // explicit colour
 * ```
 *
 * Resolution order for colour: the [colour] parameter, then [style]'s colour,
 * then [LocalContentColour] (which [Surface] sets from the ground it paints),
 * then `Theme.colours.content`. That chain is why text on a dark card is light
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
    colour: Color = Color.Unspecified,
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
    val redaction = rememberTextRedaction()

    BasicText(
        text = text,
        modifier = modifier.then(redaction.modifier),
        style = resolveTextStyle(
            style = style,
            colour = colour,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
        ),
        onTextLayout = {
            redaction.onLayout(it)
            onTextLayout?.invoke(it)
        },
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
    colour: Color = Color.Unspecified,
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
    val redaction = rememberTextRedaction()

    BasicText(
        text = text,
        modifier = modifier.then(redaction.modifier),
        style = resolveTextStyle(
            style = style,
            colour = colour,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
        ),
        onTextLayout = {
            redaction.onLayout(it)
            onTextLayout?.invoke(it)
        },
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@Composable
private fun resolveTextStyle(
    style: TextStyle,
    colour: Color,
    fontSize: TextUnit,
    fontStyle: FontStyle?,
    fontWeight: FontWeight?,
    fontFamily: FontFamily?,
    letterSpacing: TextUnit,
    textDecoration: TextDecoration?,
    textAlign: TextAlign?,
    lineHeight: TextUnit,
): TextStyle {
    val inherited = LocalContentColour.current
    val themeContent = Theme.colours.content

    val resolvedColour = when {
        colour.isSpecified -> colour
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
        remember(style, resolvedColour) { style.copy(color = resolvedColour) }
    } else {
        remember(
            style,
            resolvedColour,
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
                color = resolvedColour,
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

/**
 * One bar per line of text, in place of the glyphs.
 *
 * ### Why this lives in `Text` and not in `Modifier.redacted`
 *
 * A modifier sees a node's size and nothing else, so the best it can do over a
 * paragraph is one rectangle the shape of the whole block — which is not what a
 * paragraph looks like and is exactly the tell that gives a hand-drawn skeleton
 * away. Lines have different lengths, the last one is short, and a centred
 * heading's bars are centred too.
 *
 * `TextLayoutResult` knows all of that, and this library owns `Text`, so the one
 * place that can draw it properly is here. The bars come from the real layout of
 * the real string: a label that wraps to two lines redacts as two bars without
 * anybody saying so.
 *
 * ### How it draws
 *
 * The line boxes become a clip path, and [skeletonFill] paints the whole node
 * through it — so the shimmer sweeps across the bars as one surface rather than
 * each bar running its own. `drawWithContent { }` after it is what stops the
 * glyphs: the fill draws behind, so it has already painted by the time the chain
 * reaches there.
 *
 * Bars are inset vertically against the line box, which is taller than the ink
 * by the font's leading; without that a redacted single line is visibly fatter
 * than the text it replaces and a paragraph's bars touch each other.
 */
@Composable
private fun rememberTextRedaction(): TextRedaction {
    val redacted = LocalRedacted.current

    // The **line boxes**, not the `TextLayoutResult` they came from.
    //
    // This is the whole of why the first version hung. `onTextLayout` runs on
    // every layout pass; storing its result in state that composition reads
    // means every layout schedules a recomposition, and a recomposition
    // re-lays out. That terminates only if the second write is equal to the
    // first — and `TextLayoutResult` carries a fresh `MultiParagraph` each
    // time, so it never is. The loop ran until the test harness gave up after
    // a minute.
    //
    // A list of `Rect` is value-equal, so the second write is a no-op and the
    // whole thing settles after one extra pass. It is also all the drawing
    // needs.
    val bars = remember { mutableStateOf(emptyList<Rect>()) }
    val boxes = bars.value

    val shape = remember(boxes) {
        if (boxes.isEmpty()) {
            null
        } else {
            object : Shape {
                override fun createOutline(
                    size: Size,
                    layoutDirection: LayoutDirection,
                    density: Density,
                ): Outline {
                    val path = Path()
                    for (box in boxes) {
                        val radius = box.height / 2f
                        path.addRoundRect(
                            RoundRect(
                                left = box.left,
                                top = box.top,
                                right = box.right,
                                bottom = box.bottom,
                                radiusX = radius,
                                radiusY = radius,
                            )
                        )
                    }
                    return Outline.Generic(path)
                }
            }
        }
    }

    val density = LocalDensity.current
    val onLayout: (TextLayoutResult) -> Unit = remember(density, redacted) {
        { result ->
            if (!redacted) {
                if (bars.value.isNotEmpty()) bars.value = emptyList()
            } else {
                val inset = with(density) { BarInset.toPx() }
                val next = buildList {
                    for (line in 0 until result.lineCount) {
                        val top = result.getLineTop(line) + inset
                        val bottom = result.getLineBottom(line) - inset
                        if (bottom <= top) continue
                        add(
                            Rect(
                                left = result.getLineLeft(line),
                                top = top,
                                right = result.getLineRight(line),
                                bottom = bottom,
                            )
                        )
                    }
                }
                if (next != bars.value) bars.value = next
            }
        }
    }

    val fill = when {
        !redacted -> Modifier
        shape != null -> Modifier.clip(shape).skeletonFill().drawWithContent { }
        // First pass: the layout has not been reported yet, so there is nothing
        // to draw bars from. Draw nothing at all rather than the glyphs — a
        // frame of readable text in the middle of a loading screen is the one
        // outcome worse than a frame of blank space.
        else -> Modifier.drawWithContent { }
    }

    return TextRedaction(
        modifier = if (redacted) Modifier.clearAndSetSemantics { }.then(fill) else fill,
        onLayout = onLayout,
    )
}

/** What [rememberTextRedaction] hands back: a modifier, and a layout to feed it. */
@Immutable
private class TextRedaction(
    val modifier: Modifier,
    val onLayout: (TextLayoutResult) -> Unit,
)

/**
 * How far a bar is inset from its line box, top and bottom.
 *
 * A line box is taller than the ink in it by the font's leading, so a bar drawn
 * to the box is fatter than the text it stands for and a paragraph's bars touch.
 * Two dp is enough to part them at body sizes without making a headline's bar
 * look starved.
 */
private val BarInset: Dp = 2.dp
