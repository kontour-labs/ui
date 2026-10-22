package io.kontour.ui.catalog.apps

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.PI
import kotlin.math.sin

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

/** Three columns of cards, one on its way between two: Board's picture. */
@Composable
internal fun BoardArt(modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        drawRect(Brush.linearGradient(listOf(Mint, Teal), start = Offset.Zero, end = Offset(size.width, size.height)))
        val unit = size.height / 10f
        val column = size.width * 0.24f
        val gap = (size.width - column * 3) / 4f
        val cards = listOf(3, 2, 1)
        for ((i, count) in cards.withIndex()) {
            val x = gap + i * (column + gap)
            drawRoundRect(Lane, Offset(x, unit * 1.2f), Size(column, unit * 7.6f), CornerRadius(unit * 0.5f))
            for (c in 0 until count) {
                drawRoundRect(
                    CardFace,
                    topLeft = Offset(x + unit * 0.35f, unit * (1.9f + c * 1.9f)),
                    size = Size(column - unit * 0.7f, unit * 1.5f),
                    cornerRadius = CornerRadius(unit * 0.35f),
                )
            }
        }
        // The card being carried, lifted and turned a little, between the first two columns.
        val carried = Offset(gap + column * 0.7f, unit * 5.8f)
        drawRoundRect(Shade, carried + Offset(unit * 0.15f, unit * 0.25f), Size(column - unit * 0.7f, unit * 1.5f), CornerRadius(unit * 0.35f))
        drawRoundRect(Lifted, carried, Size(column - unit * 0.7f, unit * 1.5f), CornerRadius(unit * 0.35f))
    }
}

/** A house with panels on its roof under the sun, and the day's curve of power behind: Energy's picture. */
@Composable
internal fun RoofArt(modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        drawRect(Brush.verticalGradient(listOf(Noon, Morning)))
        drawCircle(Sun, radius = size.minDimension * 0.11f, center = Offset(size.width * 0.8f, size.height * 0.24f))
        // The day's power, a hill of bars behind the house.
        val bars = 14
        val bar = size.width / (bars * 1.6f)
        for (i in 0 until bars) {
            val h = size.height * (0.12f + 0.5f * sin(PI.toFloat() * (i + 0.5f) / bars))
            drawRoundRect(
                Curve,
                topLeft = Offset(size.width * 0.04f + i * bar * 1.6f, size.height * 0.86f - h),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar * 0.3f),
            )
        }
        // The house and its roof, the panels a grid on the near slope.
        val wall = Offset(size.width * 0.3f, size.height * 0.56f)
        val wide = size.width * 0.4f
        drawRect(Wall, wall, Size(wide, size.height * 0.3f))
        val roof = Path().apply {
            moveTo(wall.x - wide * 0.08f, wall.y)
            lineTo(wall.x + wide / 2, size.height * 0.28f)
            lineTo(wall.x + wide * 1.08f, wall.y)
            close()
        }
        drawPath(roof, Tiles)
        for (row in 0 until 2) {
            for (col in 0 until 3) {
                val top = size.height * (0.40f + row * 0.065f)
                val left = wall.x + wide * (0.14f + col * 0.12f) + row * wide * 0.04f
                drawRect(Panel, Offset(left, top), Size(wide * 0.1f, size.height * 0.05f))
            }
        }
        drawRect(Door, Offset(wall.x + wide * 0.62f, size.height * 0.7f), Size(wide * 0.14f, size.height * 0.16f))
        drawRect(Ground, Offset(0f, size.height * 0.86f), Size(size.width, size.height * 0.14f))
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

private val Mint = Color(0xFFB8EBD0)
private val Teal = Color(0xFF6CC3C9)
private val Lane = Color(0x33FFFFFF)
private val CardFace = Color(0xFFFFFFFF)
private val Lifted = Color(0xFFFFF3C4)
private val Shade = Color(0x33000000)

private val Noon = Color(0xFF8CCBF2)
private val Morning = Color(0xFFDDF1F7)
private val Curve = Color(0x66F2B63D)
private val Wall = Color(0xFFF7F1E6)
private val Tiles = Color(0xFF7A4B3A)
private val Panel = Color(0xFF243B6B)
private val Door = Color(0xFF4E6E58)
private val Ground = Color(0xFF7DB36A)
