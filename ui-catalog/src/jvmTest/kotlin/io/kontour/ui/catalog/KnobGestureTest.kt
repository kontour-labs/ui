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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A knob turns the way a finger goes round it, spins on when thrown, and leaves
 * the page to scroll from the corners of its square.
 *
 * A 200dp knob at density 2: its middle is the middle of its bounds, and 270° of
 * scale means a quarter turn is a third of the range.
 */
class KnobGestureTest {

    @Test
    fun aQuarterTurnClockwiseIsAThirdOfTheRange() {
        val (value, _) = turn(fromDegrees = -90.0, byDegrees = 90.0, settle = true, reduceMotion = true)
        assertTrue(
            abs(value - (0.5f + 1f / 3f)) < 0.02f,
            "a quarter turn from the top should take 0.5 to 0.83, and took it to $value",
        )
    }

    @Test
    fun aQuarterTurnBackIsAThirdTheOtherWay() {
        val (value, _) = turn(fromDegrees = -90.0, byDegrees = -90.0, settle = true, reduceMotion = true)
        assertTrue(abs(value - (0.5f - 1f / 3f)) < 0.02f, "took 0.5 to $value, not 0.17")
    }

    @Test
    fun thrownItSpinsOnPastWhereItWasLetGo() {
        val (thrown, atRelease) = turn(fromDegrees = -90.0, byDegrees = 60.0, settle = false, steps = 6, reduceMotion = false)
        assertTrue(
            thrown > atRelease + 0.02f,
            "let go while turning fast, it stopped where the finger left it ($atRelease → $thrown)",
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

    /** The value after turning round the knob, and the value when the finger lifted. */
    private fun turn(
        fromDegrees: Double,
        byDegrees: Double,
        settle: Boolean,
        steps: Int = 30,
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
            val centre = bounds.center
            val radius = bounds.width * 0.35f
            fun at(degrees: Double) = Offset(
                centre.x + (cos(degrees * PI / 180) * radius).toFloat(),
                centre.y + (sin(degrees * PI / 180) * radius).toFloat(),
            )
            scene.press(at(fromDegrees))
            scene.frame()
            repeat(steps) { i ->
                scene.move(at(fromDegrees + byDegrees * (i + 1) / steps))
                // A throw lifts on its last move rather than a frame later, or the
                // velocity it lifts with is the stop, not the throw.
                if (settle || i < steps - 1) scene.frame()
            }
            if (settle) scene.frames(4)
            atRelease = value
            scene.release(at(fromDegrees + byDegrees))
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
}
