package io.kontour.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.highContrast

/**
 * One line of [text] that is cut where it runs out of room, its last few
 * letters fading out rather than given up to an ellipsis: in a narrow day an
 * ellipsis is a letter's room, and a fade says the same in none. Cut hard at
 * high contrast, where a fade is ink that is neither there nor not.
 *
 * Written for a calendar's events and here, beside [Text], since a document
 * tab's title is cut the same way.
 */
@Composable
internal fun FadingText(text: String, style: TextStyle, modifier: Modifier = Modifier, colour: Color = Color.Unspecified) =
    FadingText(AnnotatedString(text), style, modifier, colour)

/** [FadingText] with styles of its own: a block's times, with an end on a later day raised. */
@Composable
internal fun FadingText(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier, colour: Color = Color.Unspecified) {
    var cut by remember { mutableStateOf(false) }
    val fade = if (highContrast()) 0.dp else ItemFade
    Text(
        text,
        style = style,
        colour = colour,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { cut = it.hasVisualOverflow },
        modifier = modifier
            .graphicsLayer { compositingStrategy = if (cut && fade > 0.dp) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .drawWithContent {
                drawContent()
                if (!cut || fade <= 0.dp) return@drawWithContent
                val width = minOf(fade.toPx(), size.width / 3f)
                val rtl = layoutDirection == LayoutDirection.Rtl
                val from = if (rtl) width else size.width - width
                val to = if (rtl) 0f else size.width
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = from, endX = to),
                    topLeft = Offset(if (rtl) 0f else from, 0f),
                    size = Size(width, size.height),
                    blendMode = BlendMode.DstOut,
                )
            },
    )
}

/** How much of a cut title fades out. */
private val ItemFade = 12.dp
