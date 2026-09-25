package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.Knob
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A knob follows a straight drag — up or right is more, from anywhere on it —
 * spins on when thrown, and leaves the page to scroll from the corners of its
 * square.
 *
 * A 200dp knob at density 2. A drag's whole travel is 200dp, which is 400px here,
 * so 100px is a quarter of the range.
 */
class KnobGestureTest {

    /**
     * Up or right is more and down or left is less, wherever on the knob the
     * finger lands.
     *
     * "Can we make it so dragging up/down and side-to-side will make it change, in
     * the right direction?" It followed the finger's angle around the middle, so a
     * straight drag went whichever way that angle did: up on the right-hand side
     * of the knob turned it down, and right along the bottom turned it down too.
     */
    @Test
    fun aStraightDragGoesTheWayItPointsFromAnywhereOnTheKnob() {
        for ((name, from) in Places) {
            for ((way, by, expected) in Ways) {
                val value = drag(from = from, by = by, reduceMotion = true).first
                assertTrue(
                    abs(value - expected) < 0.02f,
                    "a drag $way from the knob's $name should take 0.5 to $expected, and took it to $value",
                )
            }
        }
    }

    /**
     * Round the knob, it turns with the finger — a quarter turn is a third of a 270°
     * scale — including from where the two readings disagree, down the right-hand
     * side, without going the wrong way first.
     *
     * "Can we somehow combine the circular spinning motion of the knob with the
     * left/right and up/down motion? It just doesn't feel 100% natural."
     */
    @Test
    fun goingRoundItTurnsWithTheFinger() {
        for (from in listOf(-90.0, 0.0)) {
            val (end, lowest) = circle(fromDegrees = from, byDegrees = 90.0)
            assertTrue(
                abs(end - (0.5f + 1f / 3f)) < 0.03f,
                "a quarter turn clockwise from ${from}° should take 0.5 to 0.83, and took it to $end",
            )
            assertTrue(lowest >= 0.5f - 0.001f, "going round from ${from}° it dipped to $lowest before rising")
        }
    }

    /** The value after going round the knob at 70% of its radius, and the lowest it went on the way. */
    private fun circle(fromDegrees: Double, byDegrees: Double): Pair<Float, Float> {
        var value by mutableStateOf(0.5f)
        var lowest = 0.5f
        var bounds = Rect.Zero
        Scene(width = 600, height = 600, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Knob(
                    value = value,
                    onValueChange = { value = it; lowest = minOf(lowest, it) },
                    size = 200.dp,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            val centre = bounds.center
            val radius = bounds.width * 0.35f
            fun at(degrees: Double) = Offset(
                centre.x + (kotlin.math.cos(degrees * Math.PI / 180) * radius).toFloat(),
                centre.y + (kotlin.math.sin(degrees * Math.PI / 180) * radius).toFloat(),
            )
            scene.press(at(fromDegrees))
            scene.frame()
            val steps = 45
            repeat(steps) { i ->
                scene.move(at(fromDegrees + byDegrees * (i + 1) / steps))
                scene.frame()
            }
            scene.frames(4)
            scene.release(at(fromDegrees + byDegrees))
            scene.frames(10)
        }
        return value to lowest
    }

    @Test
    fun thrownItSpinsOnPastWhereItWasLetGo() {
        val (thrown, atRelease) = drag(from = Offset.Zero, by = Offset(0f, -120f), moves = 6, settle = false, reduceMotion = false)
        assertTrue(
            thrown > atRelease + 0.02f,
            "let go while moving fast, it stopped where the finger left it ($atRelease → $thrown)",
        )
    }

    @Test
    fun theSquaresCornersScrollThePageAndTheFaceDoesNot() {
        val (cornerScroll, cornerValue) = dragDownIn(column = true, atCorner = true)
        assertTrue(cornerScroll > 20, "a drag from the knob's corner should scroll the page, scrolled $cornerScroll")
        assertEquals(0.5f, cornerValue, "and should not turn the knob")

        val (faceScroll, _) = dragDownIn(column = true, atCorner = false)
        assertEquals(0, faceScroll, "a drag on the knob's face scrolled the page by $faceScroll")
    }

    /**
     * The value after a drag of [by] from [from] (relative to the knob's middle) in
     * [moves] moves, and the value when the finger lifted.
     */
    private fun drag(
        from: Offset,
        by: Offset,
        moves: Int = 20,
        settle: Boolean = true,
        reduceMotion: Boolean,
    ): Pair<Float, Float> {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero
        var atRelease = 0f
        Scene(width = 600, height = 600, reduceMotion = reduceMotion) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Knob(
                    value = value,
                    onValueChange = { value = it },
                    size = 200.dp,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            val start = bounds.center + from
            scene.press(start)
            scene.frame()
            repeat(moves) { i ->
                scene.move(start + by * ((i + 1).toFloat() / moves))
                // A throw lifts on its last move rather than a frame later, or the
                // velocity it lifts with is the stop, not the throw.
                if (settle || i < moves - 1) scene.frame()
            }
            if (settle) scene.frames(4)
            atRelease = value
            scene.release(start + by)
            scene.frames(60)
        }
        return value to atRelease
    }

    /** How far the page scrolled, and the knob's value, after a drag down from its corner or face. */
    private fun dragDownIn(column: Boolean, atCorner: Boolean): Pair<Int, Float> {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero
        var scroll: ScrollState? = null
        Scene(width = 600, height = 600, reduceMotion = true) {
            val page = rememberScrollState()
            scroll = page
            Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(page)) {
                Box(Modifier.fillMaxWidth().height(40.dp))
                Knob(
                    value = value,
                    onValueChange = { value = it },
                    size = 200.dp,
                    modifier = Modifier.reportBounds { bounds = it },
                )
                Box(Modifier.fillMaxWidth().height(800.dp))
            }
        }.use { scene ->
            scene.frames(3)
            val from = if (atCorner) Offset(bounds.left + 8f, bounds.top + 8f) else bounds.center + Offset(60f, 0f)
            scene.drag(from, from + Offset(0f, -120f), steps = 12)
            scene.frames(10)
        }
        return requireNotNull(scroll).value to value
    }

    private companion object {
        /** Where on the face a drag starts, from its middle. */
        val Places = listOf(
            "middle" to Offset.Zero,
            "left" to Offset(-120f, 0f),
            "right" to Offset(120f, 0f),
            "top" to Offset(0f, -120f),
            "bottom" to Offset(0f, 120f),
        )

        /** Which way it goes, how far, and the value it should end on. */
        val Ways = listOf(
            Triple("up", Offset(0f, -100f), 0.75f),
            Triple("right", Offset(100f, 0f), 0.75f),
            Triple("down", Offset(0f, 100f), 0.25f),
            Triple("left", Offset(-100f, 0f), 0.25f),
        )
    }
}
