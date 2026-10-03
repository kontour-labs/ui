package io.kontour.ui.catalog.apps

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics

/*
 * The apps' pictures, drawn rather than loaded: the gallery runs with no
 * network, its goldens have to come out the same on every machine, and a
 * picture that is a few rectangles and a gradient is enough to show what a hero
 * bar or a card does with one.
 *
 * Fixed colours, as a photograph's are: a picture does not turn dark with the
 * theme, and the components over it are the ones that have to cope.
 */

/**
 * A city at dusk with a train crossing a bridge: Transit's picture, behind its
 * departures and on its card. [seed] moves the towers, so two stops' pictures
 * are not the same picture.
 */
@Composable
internal fun SkylineArt(modifier: Modifier = Modifier, seed: Int = 0) {
    Canvas(modifier.clearAndSetSemantics {}) {
        drawRect(Brush.verticalGradient(listOf(Dusk, Ember, Haze)))
        drawCircle(Sun, radius = size.minDimension * 0.12f, center = Offset(size.width * 0.72f, size.height * 0.58f))
        val ground = size.height * 0.78f
        var x = -size.width * 0.02f
        var i = seed
        while (x < size.width) {
            val w = size.width * (0.05f + ((i * 37) % 5) * 0.012f)
            val h = size.height * (0.16f + ((i * 53) % 7) * 0.05f)
            drawRect(if (i % 2 == 0) Tower else TowerNear, Offset(x, ground - h), Size(w, h))
            // A few lit windows, so the towers read as buildings.
            if (i % 3 == 0) {
                drawRect(Window, Offset(x + w * 0.3f, ground - h * 0.7f), Size(w * 0.18f, h * 0.08f))
            }
            x += w + size.width * 0.006f
            i++
        }
        drawRect(Water, Offset(0f, ground), Size(size.width, size.height - ground))
        // The bridge and the train on it.
        val deck = ground - size.height * 0.02f
        drawLine(Bridge, Offset(0f, deck), Offset(size.width, deck), strokeWidth = size.height * 0.02f)
        val car = Size(size.width * 0.09f, size.height * 0.05f)
        for (c in 0 until 3) {
            drawRoundRect(
                Train,
                topLeft = Offset(size.width * (0.18f + c * 0.095f), deck - car.height),
                size = car,
                cornerRadius = CornerRadius(car.height * 0.3f),
            )
        }
    }
}

/** Three speech bubbles and a note: the Messages & Notes card's picture. */
@Composable
internal fun ConversationArt(modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        drawRect(Brush.linearGradient(listOf(Lilac, Sky), start = Offset.Zero, end = Offset(size.width, size.height)))
        val unit = size.height / 10f
        fun bubble(x: Float, y: Float, w: Float, colour: Color) = drawRoundRect(
            colour,
            topLeft = Offset(x, y),
            size = Size(w, unit * 1.6f),
            cornerRadius = CornerRadius(unit * 0.8f),
        )
        bubble(size.width * 0.08f, unit * 1.6f, size.width * 0.42f, BubbleIn)
        bubble(size.width * 0.40f, unit * 4.0f, size.width * 0.50f, BubbleOut)
        bubble(size.width * 0.08f, unit * 6.4f, size.width * 0.30f, BubbleIn)
        // The note, turned a little, in the corner.
        val note = Offset(size.width * 0.70f, unit * 6.2f)
        drawRoundRect(Paper, note, Size(size.width * 0.2f, unit * 3f), CornerRadius(unit * 0.3f))
        for (line in 0 until 3) {
            val y = note.y + unit * (0.8f + line * 0.7f)
            drawLine(Ink, Offset(note.x + unit * 0.4f, y), Offset(note.x + size.width * 0.2f - unit * 0.4f, y), unit * 0.18f, StrokeCap.Round)
        }
    }
}

private val Dusk = Color(0xFF2B2A5C)
private val Ember = Color(0xFFE0796B)
private val Haze = Color(0xFFF6C38B)
private val Sun = Color(0xFFFFE3A3)
private val Tower = Color(0xFF3A3150)
private val TowerNear = Color(0xFF2A233C)
private val Window = Color(0xFFFFD58A)
private val Water = Color(0xFF332C4E)
private val Bridge = Color(0xFF1C1828)
private val Train = Color(0xFFF2F2F2)

private val Lilac = Color(0xFFB9A7F5)
private val Sky = Color(0xFF8FD3F5)
private val BubbleIn = Color(0xFFFFFFFF)
private val BubbleOut = Color(0xFF5B3FD6)
private val Paper = Color(0xFFFFF6D8)
private val Ink = Color(0xFF8A7A4A)
