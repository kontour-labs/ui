package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.RangeSlider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A finger on each thumb drags both at once.
 *
 * Asked for directly: *"please make it so both heads of the range slider can be
 * dragged simultaneously."* The slider's drag followed one pointer, so a second
 * finger on the other thumb was consumed and did nothing — `TwoFingerTest` only
 * ever asked that it stayed up.
 *
 * Driven with `Scene.touch`, which sends every finger on the screen in every
 * event, as a pointer event is.
 */
class RangeSliderTwoFingerTest {

    private companion object {
        const val Density = 2f

        /** `SliderThumbReach`: the track is held in from each end by this much. */
        const val InsetDp = 18f

        /** Movement small enough to be exact, large enough to be past any slop. */
        const val Step = 12f

        /** Pixel rounding across a 600px track, as a fraction of it. */
        const val Slack = 0.01f
    }

    private class Rig(val scene: Scene, private val bounds: () -> Rect, private val rtl: Boolean) {
        /** Where a fraction of the track is on screen. */
        fun at(fraction: Float): Offset {
            val b = bounds()
            val inset = InsetDp * Density
            val along = if (rtl) 1f - fraction else fraction
            return Offset(b.left + inset + (b.width - inset * 2f) * along, b.center.y)
        }

        /**
         * Two fingers down at [a] and [b], moved by [da] and [db] over [steps]
         * events, and lifted if [lift].
         */
        fun twoFingers(a: Offset, b: Offset, da: Offset, db: Offset, steps: Int = 10, lift: Boolean = true) {
            scene.touch(PointerEventType.Press, listOf(Scene.Touch(1L, a), Scene.Touch(2L, b)))
            scene.frame()
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                scene.touch(PointerEventType.Move, listOf(Scene.Touch(1L, a + da * t), Scene.Touch(2L, b + db * t)))
                scene.frame()
            }
            if (lift) {
                scene.touch(
                    PointerEventType.Release,
                    listOf(Scene.Touch(1L, a + da, down = false), Scene.Touch(2L, b + db, down = false)),
                )
                scene.frames(4)
            }
        }
    }

    private fun rig(
        initial: ClosedFloatingPointRange<Float>,
        rtl: Boolean = false,
        minDistance: Float = 0f,
        onFinished: () -> Unit = {},
        test: Rig.(value: () -> ClosedFloatingPointRange<Float>) -> Unit,
    ) {
        var value by mutableStateOf(initial)
        var bounds = Rect.Zero
        Scene(width = 700, height = 200, density = Density) {
            val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    RangeSlider(
                        value = value,
                        onValueChange = { value = it },
                        minDistance = minDistance,
                        onValueChangeFinished = onFinished,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            Rig(scene, { bounds }, rtl).test { value }
        }
    }

    @Test
    fun aFingerOnEachThumbSpreadsTheRangeBothWays() = rig(0.4f..0.6f) { value ->
        val left = at(0.4f)
        val right = at(0.6f)
        val span = at(0.2f).x - at(0f).x
        twoFingers(left, right, Offset(-span, 0f), Offset(span, 0f))
        val now = value()
        assertEquals(0.2f, now.start, Slack, "the start thumb did not follow its finger — the range is $now")
        assertEquals(0.8f, now.endInclusive, Slack, "the end thumb did not follow its finger — the range is $now")
    }

    @Test
    fun bothMoveWhileBothFingersAreStillDown() = rig(0.3f..0.7f) { value ->
        // Not only by the end: a slider that took turns — one thumb, then the
        // other on release — would pass the test above.
        val span = at(0.1f).x - at(0f).x
        val seen = mutableListOf<ClosedFloatingPointRange<Float>>()
        scene.touch(PointerEventType.Press, listOf(Scene.Touch(1L, at(0.3f)), Scene.Touch(2L, at(0.7f))))
        scene.frame()
        for (step in 1..8) {
            val t = step / 8f
            scene.touch(
                PointerEventType.Move,
                listOf(Scene.Touch(1L, at(0.3f) + Offset(span * t, 0f)), Scene.Touch(2L, at(0.7f) - Offset(span * t, 0f))),
            )
            scene.frame()
            seen += value()
        }
        val middle = seen[3]
        assertTrue(
            middle.start > 0.3f + Slack && middle.endInclusive < 0.7f - Slack,
            "halfway through a two-finger pinch the range was $middle — both thumbs should be moving together",
        )
        assertEquals(0.4f, value().start, Slack)
        assertEquals(0.6f, value().endInclusive, Slack)
    }

    @Test
    fun aHeldThumbIsNotPushedTheyMeetAndStop() = rig(0.3f..0.7f, minDistance = 0.1f) { value ->
        // The end finger holds still; the start finger goes well past it.
        val far = at(0.9f).x - at(0.3f).x
        twoFingers(at(0.3f), at(0.7f), Offset(far, 0f), Offset.Zero, lift = false)
        val now = value()
        assertEquals(0.7f, now.endInclusive, Slack, "the end thumb, held still by its finger, was pushed to ${now.endInclusive}")
        assertEquals(0.6f, now.start, Slack, "the start thumb should stop its minimum short of the held one, at 0.6; it is at ${now.start}")
        // And coming back, it comes back straight away: no dead travel past the wall.
        val back = at(0.5f).x - at(0.9f).x
        scene.touch(
            PointerEventType.Move,
            listOf(Scene.Touch(1L, at(0.9f) + Offset(back, 0f)), Scene.Touch(2L, at(0.7f))),
        )
        scene.frame()
        assertTrue(
            value().start < 0.6f - Slack,
            "the start finger came back 0.4 of the track and the thumb is still at ${value().start} — the travel past the held thumb was banked",
        )
    }

    @Test
    fun twoFingersOnACollapsedRangeOpenIt() = rig(0.5f..0.5f) { value ->
        val span = at(0.2f).x - at(0f).x
        // A few pixels either side of the one place both thumbs are.
        twoFingers(at(0.5f) - Offset(3f, 0f), at(0.5f) + Offset(3f, 0f), Offset(-span, 0f), Offset(span, 0f))
        val now = value()
        assertTrue(now.start < 0.35f && now.endInclusive > 0.65f, "two fingers spreading a collapsed range left it at $now")
    }

    @Test
    fun twoFingersDownOnOneSideEachTakeTheThumbOnTheirSide() = rig(0.3f..0.5f) { value ->
        // The first lands nearer the end thumb; the second lands past it. Left
        // alone, the first would take the end thumb and the second the start —
        // which it would then have to drag across the first finger.
        twoFingers(at(0.55f), at(0.8f), Offset(Step, 0f), Offset(Step, 0f))
        val now = value()
        val nudge = (at(0.55f).x + Step - at(0f).x) / (at(1f).x - at(0f).x) - 0.55f
        assertEquals(0.55f + nudge, now.start, Slack, "the nearer finger did not take the start thumb — $now")
        assertEquals(0.8f + nudge, now.endInclusive, Slack, "the further finger did not take the end thumb — $now")
    }

    @Test
    fun aSecondFingerArrivingMidDragTakesTheOtherThumb() = rig(0.2f..0.8f) { value ->
        val span = at(0.1f).x - at(0f).x
        // One finger, already dragging the start thumb.
        scene.touch(PointerEventType.Press, listOf(Scene.Touch(1L, at(0.2f))))
        scene.frame()
        scene.touch(PointerEventType.Move, listOf(Scene.Touch(1L, at(0.2f) + Offset(span, 0f))))
        scene.frame()
        // A second lands on the end thumb, and both move.
        scene.touch(
            PointerEventType.Press,
            listOf(Scene.Touch(1L, at(0.2f) + Offset(span, 0f)), Scene.Touch(2L, at(0.8f))),
        )
        scene.frame()
        scene.touch(
            PointerEventType.Move,
            listOf(Scene.Touch(1L, at(0.2f) + Offset(span * 2f, 0f)), Scene.Touch(2L, at(0.8f) - Offset(span, 0f))),
        )
        scene.frame()
        val now = value()
        assertEquals(0.4f, now.start, Slack, "the first finger lost its thumb when the second arrived — $now")
        assertEquals(0.7f, now.endInclusive, Slack, "the second finger did not take the end thumb — $now")
        // The first lifts; the second carries on alone.
        scene.touch(
            PointerEventType.Release,
            listOf(Scene.Touch(1L, at(0.2f) + Offset(span * 2f, 0f), down = false), Scene.Touch(2L, at(0.8f) - Offset(span, 0f))),
        )
        scene.frame()
        scene.touch(PointerEventType.Move, listOf(Scene.Touch(2L, at(0.8f) - Offset(span * 2f, 0f))))
        scene.frame()
        assertEquals(0.6f, value().endInclusive, Slack, "the second finger stopped driving its thumb when the first lifted")
        assertEquals(0.4f, value().start, Slack, "the start thumb moved after its finger had lifted")
    }

    @Test
    fun rightToLeftEachFingerKeepsTheThumbUnderIt() = rig(0.4f..0.6f, rtl = true) { value ->
        // Mirrored: the start thumb is on the right, and spreading the fingers
        // on screen moves the start thumb right and the end thumb left.
        assertTrue(at(0.4f).x > at(0.6f).x, "the rig does not mirror")
        twoFingers(at(0.4f), at(0.6f), at(0.2f) - at(0.4f), at(0.8f) - at(0.6f))
        val now = value()
        assertEquals(0.2f, now.start, Slack, "right to left, the start thumb did not follow its finger — $now")
        assertEquals(0.8f, now.endInclusive, Slack, "right to left, the end thumb did not follow its finger — $now")
    }

    @Test
    fun theDragIsFinishedOnceWhenTheLastFingerLifts() {
        var finished = 0
        rig(0.3f..0.7f, onFinished = { finished++ }) {
            twoFingers(at(0.3f), at(0.7f), Offset(-Step, 0f), Offset(Step, 0f))
        }
        assertEquals(1, finished, "onValueChangeFinished ran $finished times for one two-finger drag")
    }
}
