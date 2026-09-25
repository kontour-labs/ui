package io.kontour.ui.catalog

import androidx.compose.ui.Alignment
import io.kontour.ui.components.selection.Knob
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.datetime.DatePicker
import io.kontour.ui.components.datetime.DateRangePicker
import io.kontour.ui.components.datetime.WheelPicker
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.CarouselState
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.rememberSwipeActionsState
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Rating
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.nav.tabSwipe
import io.kontour.ui.overlay.AlertDialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.ToastHostState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * What the library is allowed to buzz for.
 *
 * Two tiers, and the four [io.kontour.ui.interaction.HapticsLevel]s exist to
 * hold them apart. **An outcome reports something the user could not otherwise
 * tell** — a detent crossed under a finger, a threshold passed that changes what
 * letting go will do, a long press becoming a gesture, a destructive question
 * arriving — and survives every level above `Off`. **A tap acknowledges a
 * press** on a discrete control, says nothing the eye is not also being told,
 * and is therefore the thing a level can drop.
 *
 * Silent at every level is the third case: a press that already carries its own
 * visible answer and has no discrete commit behind it. A [Button], a tab, a menu
 * item, a page control, a navigation destination, a stepped slider tapped
 * mid-track. The commonest interaction in the library is the one that must not
 * buzz.
 *
 * The switch is on both lists, and both halves are below: a tap reports like the
 * checkbox beside it, and a drag reports again at the midpoint, where the
 * gesture commits with nothing on screen having said so first.
 *
 * The previous policy was "make it tactile", and fifty-seven call sites took
 * that literally. Every `clickable` in the library fired, every `toggleable`
 * fired, a stepped slider fired on the press *and* on the release, a swipe row
 * fired four different intents across one gesture, and a wheel picker fired the
 * moment it was composed. The report was that it had become "a bit ridiculous",
 * which is the correct reading of a component set that vibrates when you look at
 * it.
 *
 * The correction overshot in one place, and this round walks that back. A
 * checkbox that answers nothing while the switch beside it does is a settings
 * list where half the rows read as broken. The fix is not fifty-seven sites
 * again — it is one light intent, performed through one helper, that a level can
 * switch off.
 *
 * ### Why "no intent" is stronger than "no haptic"
 *
 * These record [FeedbackIntent]s, not platform haptics. A component that
 * performs no intent is silent at *every* [io.kontour.ui.interaction.HapticsLevel]
 * and under every consumer's replacement dispatcher, because there is nothing
 * for a level or a mapping to let through. Asserting an empty list is therefore
 * the whole claim for a component that must never buzz, and it needs no sweep
 * over the levels to make it. What each level *does* let through is asserted
 * once against the levels themselves, rather than once per component.
 *
 * ### Recorded through `LocalFeedback`
 *
 * `LocalFeedback` is a `staticCompositionLocalOf<FeedbackDispatcher>` and
 * `FeedbackDispatcher` is a `fun interface`, so a test can install one that
 * writes down what it was asked to do. That is the whole instrument — no
 * platform, no expect/actual, no mocking. The mapping from intent to physical
 * haptic being one decision in one place is the property that makes this
 * possible, and is why the intents exist at all.
 *
 * The *sequence* is what is asserted where a count is not enough: what matters
 * for a detent is that the ticks come one per boundary rather than one per
 * frame, and for a threshold that it fires exactly once.
 */
class DetentHapticsTest {

    // -----------------------------------------------------------------------
    // Silent: a tap is a tap
    // -----------------------------------------------------------------------

    @Test
    fun aButtonPressFiresNothing() {
        val felt = mutableListOf<FeedbackIntent>()
        var clicks = 0
        var bounds = Rect.Zero

        Scene(width = 400, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    Button(onClick = { clicks++ }, modifier = Modifier.reportBounds { bounds = it }) {
                        +"Save"
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            scene.tap(bounds.center)
            scene.frames(3)
        }

        assertEquals(1, clicks, "the tap never reached the button")
        assertEquals(
            emptyList(), felt,
            "pressing a button fired ${felt.summary()}. The commonest interaction " +
                "in the library is the one that must not buzz — a press that " +
                "reports itself is a press the user was already watching.",
        )
    }

    /**
     * A switch is two gestures and each reports its own thing.
     *
     * It was silent whole, on the argument that "a toggle is a decision the user
     * made and can see the result of" — and the argument holds for the switch
     * looked at alone. It stopped holding in a settings list, where the checkbox
     * a row above answers a press and this one did not, so the control that moves
     * the most on screen was the one that felt dead. A tap acknowledges now, at
     * the lightest intent there is and only from `Standard` upwards.
     *
     * The drag is a different report and not a louder version of the same one.
     * It commits at the midpoint, crossing it changes what letting go will do,
     * and nothing on screen says so first — the `DragThreshold` row of the policy
     * table word for word. So both halves are here: the tap exactly one [Tap],
     * the drag exactly one threshold and no tick per frame on the way.
     *
     * [FeedbackIntent.Tap]: io.kontour.ui.interaction.FeedbackIntent.Tap
     */
    @Test
    fun aSwitchAcknowledgesATapAndReportsTheCrossingOnceDragged() {
        val tapped = mutableListOf<FeedbackIntent>()
        val dragged = mutableListOf<FeedbackIntent>()
        var checked by mutableStateOf(false)
        var bounds = Rect.Zero

        fun scene(felt: MutableList<FeedbackIntent>) = Scene(width = 400, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    Switch(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }

        scene(tapped).use { scene ->
            scene.frames(3)
            scene.tap(bounds.center)
            scene.frames(6)
        }
        assertEquals(
            listOf(FeedbackIntent.Tap), tapped,
            "tapping a switch fired ${tapped.summary()}, where one `Tap` was " +
                "wanted. The switch was silent for a release, and the report was " +
                "that it feels dead next to a checkbox that is not — so it " +
                "acknowledges, once, at the lightest intent the device has. Not " +
                "`Selection` and not `Tick`: a level has to be able to keep a " +
                "slider's detents and drop this.",
        )

        checked = false
        scene(dragged).use { scene ->
            scene.frames(3)
            scene.drag(bounds.alongX(0.1f), bounds.alongX(0.9f), steps = 12)
            scene.frames(6)
        }
        assertEquals(
            listOf(FeedbackIntent.DragThreshold), dragged,
            "a drag across a switch fired ${dragged.summary()}. One crossing is " +
                "one report: not a tick per frame while the thumb travels, and " +
                "not a second one when the finger lifts, because the release has " +
                "nothing left to say once the midpoint has been.",
        )
    }

    /**
     * Dragging across a rating taps per star, and never more than one a star.
     *
     * This asserted silence, on the true observation that there are no real
     * detents here — five drawings on one continuous track, with the eye on them
     * the whole way across. What the observation missed is that the drag is how
     * the value is *chosen*, and each star it passes is a value it has taken; a
     * control you set with your thumb and feel nothing from is a control you
     * check by looking, which is the thing the haptic was for.
     *
     * So the count is what matters rather than the silence. `Tap` per star, not
     * per frame, which is the shared rate floor doing its job across a drag that
     * recomposes forty times.
     */
    @Test
    fun aRatingDragTapsPerStarAndNotPerFrame() {
        val felt = mutableListOf<FeedbackIntent>()
        var score by mutableStateOf(0f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Rating(
                        value = score,
                        contentDescription = "Your rating",
                        onValueChange = { score = it },
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            // Paced in real time, because this counts taps and the shared rate
            // floor runs on a wall clock — see `Scene.drag`. Thirty steps 20ms
            // apart is a 600ms sweep across five marks, so each one is taken
            // about 150ms after the last and the floor is the backstop rather
            // than the thing under measurement. Unpaced the same drag takes
            // almost no real time and the floor collapses it to one tap, which
            // is a true statement about a gesture no hand makes.
            scene.drag(
                bounds.alongX(0.05f),
                bounds.alongX(0.95f),
                steps = 30,
                paceMillis = 20,
            )
            scene.frames(3)
        }

        assertTrue(score > 0f, "the drag never reached the rating")
        assertTrue(
            felt.isNotEmpty() && felt.all { it == FeedbackIntent.Tap },
            "dragging across a rating fired ${felt.summary()}. Each star the " +
                "finger passes is a value taken, which is a `Tap` — and nothing " +
                "heavier, because there is no detent here to snap to.",
        )
        assertTrue(
            felt.size in 3..5,
            "a drag across five marks fired ${felt.size} taps (${felt.summary()}). " +
                "Five values are taken across this sweep, so five is the answer; " +
                "anything near thirty is one per frame and one is the rate floor " +
                "having swallowed the gesture.",
        )
    }

    @Test
    fun aWheelPickerFiresNothingUntilItIsTurned() {
        val felt = mutableListOf<FeedbackIntent>()
        var hour by mutableStateOf(9)

        Scene(width = 300, height = 400) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    WheelPicker(
                        items = (0..23).toList(),
                        selected = hour,
                        onSelectedChange = { hour = it },
                        label = { it.toString().padStart(2, '0') },
                    )
                }
            }
        }.use { scene -> scene.frames(8) }

        assertEquals(
            emptyList(), felt,
            "composing a wheel picker fired ${felt.summary()} without anything " +
                "having touched it. `snapshotFlow` emits its current value first, " +
                "so this was one buzz per wheel on arrival — three for a " +
                "`TimePicker`, before the screen had finished appearing.",
        )
    }

    // -----------------------------------------------------------------------
    // Detents: a boundary crossed under a finger
    // -----------------------------------------------------------------------

    @Test
    fun aSteppedSliderTicksForStepsDraggedAcrossAndNotForOneTappedOnto() {
        val tapped = mutableListOf<FeedbackIntent>()
        val dragged = mutableListOf<FeedbackIntent>()
        var value by mutableStateOf(0f)
        var bounds = Rect.Zero

        fun scene(felt: MutableList<FeedbackIntent>) = Scene(width = 600, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    )
                }
            }
        }

        scene(tapped).use {
            it.frames(3)
            it.tap(bounds.alongX(0.75f))
            it.frames(4)
        }
        assertTrue(value > 0f, "the tap never reached the slider")
        assertEquals(
            emptyList(), tapped,
            "tapping a stepped slider fired ${tapped.summary()}. Landing on a " +
                "detent is not crossing one — nothing travelled, so nothing was " +
                "felt on the way.",
        )

        value = 0f
        scene(dragged).use {
            it.frames(3)
            // Slowly, so many frames fall inside each step: a per-frame
            // implementation reports forty and a silent one reports none.
            //
            // Paced in *real* time as well as frames, because this counts ticks
            // and the shared ticker's rate limit runs on a wall clock — see
            // `Scene.drag`. Unpaced, the same drag takes almost no real time at
            // all and the limit swallows half of them, which is a true statement
            // about a gesture no hand makes.
            //
            // **Thirty, not twenty, and the ten milliseconds are the difference
            // between a test and a coin toss.** Forty samples across ten detents
            // is four per detent *on average*, and the boundaries do not line up
            // with the samples: measured, the crossings alternate between four
            // steps apart and three, which at a 20ms pace came out at 110ms and
            // **80-86ms** against a floor of exactly 80. Three gaps a run sat
            // within a millisecond or two of it, so whether they were felt was
            // decided by how long the JIT and the machine took over a frame —
            // and a run that lost all three reported seven ticks against a band
            // of `8..12`.
            //
            // At 30ms the same two gaps are 112ms and 150ms. That is 40% of
            // clearance rather than 2%, and it is also the speed the component
            // was measured at on the built site: `MinimumTickInterval` records
            // real detents crossed "about 160ms apart", where the old pacing
            // made this drag twice as fast as any hand.
            //
            // Forty samples stays. Twenty was tried and is worse for a different
            // reason: two steps per detent means one delta occasionally carries
            // two boundaries, `at` reports the second only, and the count comes
            // back nine — a merge the pacing cannot fix.
            it.drag(bounds.alongX(0.02f), bounds.alongX(0.98f), steps = 40, paceMillis = 30)
            it.frames(4)
        }

        val ticks = dragged.count { it == FeedbackIntent.Tick }
        // Ten boundaries, ten ticks, and the band is one either side rather than
        // two. It was `8..12` while the pacing above had three of the ten
        // crossings sitting on the rate floor, which is a band wide enough to
        // pass a run that dropped two of them for no reason anybody could see.
        // With the clearance the drag has now, a missing tick is a defect.
        assertTrue(
            ticks in 9..11,
            "dragging across ten steps produced $ticks ticks " +
                "(${dragged.summary()}). Ten boundaries were crossed.",
        )
        // 0.98 of the slider's bounds is past the end of its track, which is
        // inset by the thumb, so the drag finishes by running into the end —
        // reported once, and nothing else is.
        assertEquals(
            listOf(FeedbackIntent.DragThreshold),
            dragged.filter { it != FeedbackIntent.Tick },
            "the drag fired something other than detents and the one end stop it " +
                "runs into: ${dragged.summary()}",
        )
    }

    /**
     * A finger that runs a slider into the end of its range feels it, once.
     *
     * Asked for: "a haptic in standard mode to all sliders that fires when you hit
     * the end stop". This was the other way for a round — the end stop reported
     * nothing, on the argument that the thumb squashing against the wall already
     * says so — and the argument lost to use: the thumb is under the finger, and a
     * finger pushing a value it cannot see does not know it has run out until the
     * number stops changing. `DragThreshold`, so it is not swallowed by the rate
     * floor right after the last detent's tick; once per wall entered, so holding
     * the finger against the stop is one report rather than a buzz.
     *
     * A continuous slider, so no detent tick is in the record to be confused with it.
     */
    @Test
    fun aSliderSaysSoOnceWhenItRunsIntoItsEnd() {
        val (felt, value) = slidAgainstTheEnd(passes = 1)
        assertTrue(value >= 1f, "the drag never reached the end, so this proves nothing")
        assertEquals(
            listOf(FeedbackIntent.DragThreshold), felt,
            "running a slider into the end of its range fired ${felt.summary()}, where it " +
                "should report the wall once",
        )
    }

    /** Leaving the wall re-arms it: two pushes into the end are two reports. */
    @Test
    fun aSliderReportsEachTimeTheEndIsReachedAgain() {
        val (felt, _) = slidAgainstTheEnd(passes = 2)
        assertEquals(
            listOf(FeedbackIntent.DragThreshold, FeedbackIntent.DragThreshold), felt,
            "into the end, back off it and into it again fired ${felt.summary()}",
        )
    }

    /** The range slider's ends are walls too; the other thumb is not. */
    @Test
    fun aRangeSliderSaysSoAtTheEndOfItsTrack() {
        val felt = mutableListOf<FeedbackIntent>()
        var value by mutableStateOf(0.3f..0.6f)
        var bounds = Rect.Zero
        Scene(width = 600, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    RangeSlider(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            // On the end thumb, which is the nearer one to 60% of the way along.
            scene.drag(bounds.alongX(0.6f), Offset(bounds.right + 200f, bounds.center.y), steps = 20, paceMillis = 20)
            scene.frames(4)
        }
        assertTrue(value.endInclusive >= 1f, "the drag never took the end thumb to the end")
        assertEquals(
            listOf(FeedbackIntent.DragThreshold), felt,
            "running a range slider's thumb into the end fired ${felt.summary()}",
        )
    }

    /** Drags a continuous slider from the middle past its end [passes] times. */
    private fun slidAgainstTheEnd(passes: Int): Pair<List<FeedbackIntent>, Float> {
        val felt = mutableListOf<FeedbackIntent>()
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            // Held down throughout: into the wall, back well clear of it, and in again.
            val y = bounds.center.y
            scene.press(bounds.alongX(0.5f))
            repeat(passes) { pass ->
                for (step in 1..20) {
                    scene.move(Offset(bounds.left + bounds.width * (0.5f + 0.9f * step / 20f), y))
                    scene.frame()
                }
                if (pass < passes - 1) {
                    for (step in 1..20) {
                        scene.move(Offset(bounds.left + bounds.width * (1.4f - 0.9f * step / 20f), y))
                        scene.frame()
                    }
                }
            }
            scene.release(Offset(bounds.right + 100f, y))
            scene.frames(4)
        }
        return felt to value
    }

    /**
     * Two components inside eighty milliseconds are one rattle.
     *
     * The shared floor, proven where it actually matters rather than on the class
     * that implements it. `KontourTheme` hands one `FeedbackFloor` to every light
     * haptic under it, so a stepper and a checkbox that have never heard of each
     * other are rate-limited against *each other* and not each against itself.
     *
     * Both halves, because the first one alone would pass against a floor that
     * simply never let anything through twice.
     *
     * ### Nothing is rendered between the two taps
     *
     * The floor runs on a wall clock and [Scene.frame] encodes a PNG and decodes
     * it back through `ImageIO`, which is far more work than drawing the frame
     * was. Two frames between the taps is two image codecs, and under a full
     * suite that is comfortably more than the eighty milliseconds being tested —
     * so this asserted "one rattle" and got two, in the one run where the machine
     * was busy. A test that goes red under load is red in exactly the place it
     * matters.
     *
     * Pointer events dispatch synchronously and a click handler runs inside that
     * dispatch, so both taps land with nothing but event plumbing between them.
     * That is not a gesture a hand makes, and it is not meant to be: what is
     * under test is that two components share *one* floor rather than keeping one
     * each, and the interval itself is measured next door against a
     * `TestTimeSource` where a clock can be driven rather than waited on.
     */
    @Test
    fun twoComponentsTappedTogetherReportOnceAndSeparatelyReportTwice() {
        fun tapped(gapMillis: Long): List<FeedbackIntent> {
            val felt = mutableListOf<FeedbackIntent>()
            var on by mutableStateOf(false)
            var also by mutableStateOf(false)
            var first = Rect.Zero
            var second = Rect.Zero
            Scene(width = 400, height = 300) {
                Recording(felt) {
                    Column(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = { on = it },
                            modifier = Modifier.reportBounds { first = it },
                        )
                        Checkbox(
                            checked = also,
                            onCheckedChange = { also = it },
                            modifier = Modifier.reportBounds { second = it },
                        )
                    }
                }
            }.use { scene ->
                scene.frames(3)
                scene.tap(first.center)
                // Real time, and only when a gap is wanted. See the note above on
                // why the zero case renders nothing at all.
                if (gapMillis > 0) scene.renderUntil(timeoutMillis = gapMillis) { false }
                scene.tap(second.center)
                scene.frames(2)
            }
            assertTrue(on && also, "one of the two taps never landed")
            return felt
        }

        val together = tapped(gapMillis = 0)
        assertEquals(
            listOf(FeedbackIntent.Tap), together,
            "two boxes ticked in the same instant fired ${together.summary()}. A " +
                "hand does not feel components, and two pulses that close together " +
                "are one pulse to it — which is the same argument the interval came " +
                "from in the first place.",
        )
        // Comfortably past the floor, so a slow frame either side cannot eat the
        // margin. This is real time, not frame time: the floor runs on a wall
        // clock precisely so that it is measuring the hand rather than the render.
        val apart = tapped(gapMillis = 200)
        assertEquals(
            listOf(FeedbackIntent.Tap, FeedbackIntent.Tap), apart,
            "two boxes ticked a fifth of a second apart fired ${apart.summary()}. " +
                "The floor is there to thin a stream, not to make the second control " +
                "in a form inert.",
        )
    }

    /**
     * A knob ticks per step it is turned past, and says so once when it runs into
     * its end — the slider's rule, round.
     */
    @Test
    fun aKnobTicksPerStepTurnedAndSaysSoAtItsEnd() {
        val felt = mutableListOf<FeedbackIntent>()
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero
        Scene(width = 600, height = 600, reduceMotion = true) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    Knob(
                        value = value,
                        onValueChange = { value = it },
                        steps = 9,
                        size = 200.dp,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            val centre = bounds.center
            // Right from the middle, 260px of a 400px travel: 0.5 to 1 is five
            // steps, and then the wall.
            scene.press(centre)
            scene.frame()
            repeat(40) { i ->
                scene.move(centre + Offset(260f * (i + 1) / 40, 0f))
                scene.frame()
                Thread.sleep(30)
            }
            scene.frames(4)
            scene.release(centre + Offset(260f, 0f))
            scene.frames(6)
        }
        val ticks = felt.count { it == FeedbackIntent.Tick }
        assertTrue(ticks in 4..6, "turning 0.5 to the end of ten steps ticked $ticks times (${felt.summary()})")
        assertEquals(
            listOf(FeedbackIntent.DragThreshold),
            felt.filter { it != FeedbackIntent.Tick },
            "running a knob into its end should report once and nothing else did: ${felt.summary()}",
        )
    }

    @Test
    fun aRangeSliderTapFiresNothing() {
        val felt = mutableListOf<FeedbackIntent>()
        var range by mutableStateOf(2f..8f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    RangeSlider(
                        value = range,
                        onValueChange = { range = it },
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            scene.tap(bounds.alongX(0.5f))
            scene.frames(4)
        }

        assertEquals(
            emptyList(), felt,
            "tapping a stepped range slider fired ${felt.summary()}. A press that " +
                "never moved emits from `onEnd` on this control, so it used to " +
                "buzz once on touch and once on release for a gesture that " +
                "crossed nothing.",
        )
    }

    @Test
    fun aRangeSliderTicksForEveryStepAHandleIsDraggedAcross() {
        // The reporter's words were "both normal and range", and only the range
        // slider's *tap* was covered. A component whose drag is untested is a
        // component whose drag can be broken without anything going red, which
        // is the fault this round is mostly about.
        val felt = mutableListOf<FeedbackIntent>()
        var range by mutableStateOf(0f..10f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    RangeSlider(
                        value = range,
                        onValueChange = { range = it },
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            // The start handle, from its own position rightwards. Paced for the
            // same reason as the slider above: this counts ticks, and the rate
            // limit runs on a wall clock.
            scene.drag(
                bounds.alongX(0.02f),
                bounds.alongX(0.62f),
                steps = 30,
                paceMillis = 20,
            )
            scene.frames(4)
        }

        assertTrue(
            range.start > 0f,
            "the drag never moved the start handle, so this proves nothing " +
                "about its haptics",
        )
        val ticks = felt.count { it == FeedbackIntent.Tick }
        assertTrue(
            ticks >= 3,
            "dragging a range handle across six steps produced only $ticks " +
                "ticks (${felt.summary()})",
        )
        assertTrue(
            felt.none { it != FeedbackIntent.Tick },
            "the drag fired something other than detents: ${felt.summary()}",
        )
    }

    @Test
    fun aSegmentedControlTicksOncePerSegmentCrossed() {
        val felt = mutableListOf<FeedbackIntent>()
        var selected by mutableStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 700, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    SegmentedControl(
                        options = listOf("Depart", "Arrive", "Both", "Neither"),
                        selected = selected,
                        onSelectedChange = { selected = it },
                        modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            // Paced, because this counts ticks and `DetentTicker` now has a
            // rate limit on a wall clock — see `Scene.drag`. Forty steps 12ms
            // apart is a half-second swipe, which is a speed a thumb makes.
            scene.drag(bounds.alongX(0.05f), bounds.alongX(0.95f), steps = 40, paceMillis = 12)
            scene.frames(4)
        }

        val ticks = felt.count { it == FeedbackIntent.Tick }
        assertTrue(
            ticks in 3..5,
            "a drag across four segments produced $ticks ticks (${felt.summary()}). " +
                "Three boundaries were crossed, so three is the answer; anything " +
                "near forty is one per frame and anything near zero is silence.",
        )
        assertTrue(
            felt.none { it != FeedbackIntent.Tick },
            "the drag fired something besides its detents: ${felt.summary()}. The " +
                "settle at the end went with the audit — the thumb arriving is " +
                "the report, and it is one the user is looking at.",
        )
    }

    @Test
    fun tabsTickPerStepAndNothingElse() {
        val felt = mutableListOf<FeedbackIntent>()
        var tab by mutableStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 700, height = 300) {
            Recording(felt) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .tabSwipe(selected = tab, count = 4, onSelectedChange = { tab = it })
                        .reportBounds { bounds = it }
                )
            }
        }.use { scene ->
            scene.frames(3)
            // Paced for the same reason as the segmented control above.
            scene.drag(bounds.alongX(0.95f), bounds.alongX(0.05f), steps = 30, paceMillis = 16)
            scene.frames(4)
        }

        assertTrue(tab > 0, "the swipe did not change tab at all")
        assertTrue(
            felt.count { it == FeedbackIntent.Tick } >= tab,
            "only ${felt.count { it == FeedbackIntent.Tick }} ticks for $tab " +
                "steps: ${felt.summary()}",
        )
        assertTrue(
            felt.none { it != FeedbackIntent.Tick },
            "a tab swipe fired ${felt.summary()}. Stepping past a tab is a detent " +
                "crossed; arriving at one is a page the user is now looking at.",
        )
    }

    // -----------------------------------------------------------------------
    // Thresholds, long presses and warnings
    // -----------------------------------------------------------------------

    @Test
    fun aSwipedRowFiresOnlyAtThePointOfNoReturn() {
        val felt = mutableListOf<FeedbackIntent>()
        var bounds = Rect.Zero

        Scene(width = 700, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    SwipeActions(
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                            .reportBounds { bounds = it },
                        end = listOf(
                            SwipeAction(
                                label = "Delete",
                                icon = Tabler.Outline.Trash,
                                onAction = {},
                                background = Color.Red,
                                isFullSwipeAction = true,
                            ),
                        ),
                    ) {
                        Box(Modifier.fillMaxWidth().height(72.dp).background(Color.White))
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(bounds.alongX(0.95f), bounds.alongX(0.02f), steps = 30, release = false)
            scene.frames(6)
        }

        assertEquals(
            listOf(FeedbackIntent.DragThreshold), felt,
            "a full swipe fired ${felt.summary()}. It used to fire four different " +
                "things across one gesture — a tick per `actionWidth` uncovered, " +
                "the threshold, a `Confirm` when the action ran and a settle when " +
                "the row came back — and the report was that it goes way too " +
                "crazy. `actionWidth` is not a detent: nothing snaps there. What " +
                "is left is the one edge the user cannot see coming.",
        )
    }

    /**
     * Backing off the point of no return is felt too — it undoes something — and a
     * row moved in code is felt not at all.
     */
    @Test
    fun aSwipedRowSaysSoBackingOffTheLineAndNothingWhenMovedInCode() {
        val backedOff = mutableListOf<FeedbackIntent>()
        var bounds = Rect.Zero

        Scene(width = 700, height = 200) {
            Recording(backedOff) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    SwipeActions(
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                            .reportBounds { bounds = it },
                        end = listOf(
                            SwipeAction("Delete", Tabler.Outline.Trash, {}, Color.Red, isFullSwipeAction = true),
                        ),
                    ) {
                        Box(Modifier.fillMaxWidth().height(72.dp).background(Color.White))
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            val out = bounds.alongX(0.1f)
            scene.drag(bounds.alongX(0.95f), out, steps = 30, release = false)
            // Back to just past the actions, well short of the line.
            val back = bounds.alongX(0.7f)
            repeat(20) { step ->
                val t = (step + 1) / 20f
                scene.move(Offset(out.x + (back.x - out.x) * t, out.y))
                scene.frame()
            }
            scene.frames(4)
            scene.release(back)
            scene.frames(30)
        }
        assertEquals(
            listOf(FeedbackIntent.DragThreshold, FeedbackIntent.DragThreshold), backedOff,
            "over the point of no return and back fired ${backedOff.summary()}. The " +
                "crossing out is one report and the crossing back, which undoes it, is " +
                "the other.",
        )

        val inCode = mutableListOf<FeedbackIntent>()
        Scene(width = 700, height = 200) {
            Recording(inCode) {
                val state = rememberSwipeActionsState()
                LaunchedEffect(Unit) {
                    state.animateTo(SwipeValue.End)
                    state.reset()
                }
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    SwipeActions(
                        state = state,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        end = listOf(
                            SwipeAction("Delete", Tabler.Outline.Trash, {}, Color.Red, isFullSwipeAction = true),
                        ),
                    ) {
                        Box(Modifier.fillMaxWidth().height(72.dp).background(Color.White))
                    }
                }
            }
        }.use { scene -> scene.frames(60) }
        assertEquals(
            emptyList(), inCode,
            "a row opened and closed in code fired ${inCode.summary()}. Nobody's hand " +
                "was on it.",
        )
    }

    @Test
    fun aReorderedRowClicksPerPositionAndTicksLighterOnDrop() {
        val felt = mutableListOf<FeedbackIntent>()
        val rows = mutableStateListOf("Perth", "Daglish", "Subiaco", "West Leederville")
        var bounds = Rect.Zero

        Scene(width = 500, height = 500) {
            Recording(felt) {
                val listState = rememberLazyListState()
                val reorder = rememberReorderableState(listState) { from, to ->
                    rows.add(to, rows.removeAt(from))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(Color.White),
                ) {
                    itemsIndexed(rows) { index, name ->
                        ReorderableItem(state = reorder, index = index, itemCount = rows.size) {
                            ListItem(
                                modifier = if (index == 0) {
                                    Modifier.reportBounds { bounds = it }
                                } else {
                                    Modifier
                                }
                            ) { +name }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            // Long press, then down past two rows, then let go.
            scene.press(bounds.center)
            // Held on the *wall* clock, not on a frame count.
            //
            // Compose's long-press timeout is a `delay`, and a `delay` in this
            // harness is real time — `Scene` advances 16ms of frame time per
            // render and nothing ties the two together. Forty-five frames is
            // 720ms of frame time and was however long forty-five renders
            // happened to take, which on a warm JVM in the middle of a full run
            // is under the 500ms the press has to survive. It passed on its own
            // and failed inside the suite, which is the signature of exactly
            // that. Most of a second, so a slow frame either side cannot eat
            // the margin.
            scene.renderUntil(timeoutMillis = 900L) { false }
            scene.move(bounds.center + Offset(0f, bounds.height * 2.2f))
            scene.frames(6)
            scene.release(bounds.center + Offset(0f, bounds.height * 2.2f))
            scene.frames(4)
        }

        assertTrue(
            rows.first() != "Perth",
            "the row never moved, so this measured nothing: ${felt.summary()}",
        )
        assertTrue(
            felt.first() == FeedbackIntent.LongPress,
            "the long press that hands the row over fired ${felt.firstOrNull()}",
        )
        assertTrue(
            felt.count { it == FeedbackIntent.Selection } >= 1,
            "a row that changed position reported ${felt.summary()} — the " +
                "position change is the one thing here the user is not watching, " +
                "because they are watching the row in their hand.",
        )
        assertEquals(
            FeedbackIntent.Tick, felt.last(),
            "the drop reported ${felt.lastOrNull()}. A drop is lighter than the " +
                "reorders it follows — `SegmentFrequentTick` against their " +
                "`SegmentTick` — because the news already happened, once per gap " +
                "the row crossed.",
        )
    }

    @Test
    fun aDestructiveAlertWarnsOnceAndOthersDoNot() {
        fun opened(destructive: Boolean, hapticWarning: Boolean? = null): List<FeedbackIntent> {
            val felt = mutableListOf<FeedbackIntent>()
            var visible by mutableStateOf(false)
            Scene(width = 500, height = 400) {
                Recording(felt) {
                    OverlayHost {
                        Box(Modifier.fillMaxSize().background(Color.White))
                        if (hapticWarning == null) {
                            AlertDialog(
                                visible = visible,
                                onDismissRequest = {},
                                confirmLabel = "Delete",
                                onConfirm = {},
                                destructive = destructive,
                            ) { title { +"Delete favourite?" } }
                        } else {
                            AlertDialog(
                                visible = visible,
                                onDismissRequest = {},
                                confirmLabel = "Delete",
                                onConfirm = {},
                                destructive = destructive,
                                hapticWarning = hapticWarning,
                            ) { title { +"Delete favourite?" } }
                        }
                    }
                }
            }.use { scene ->
                scene.frames(3)
                visible = true
                scene.frames(8)
            }
            return felt
        }

        assertEquals(
            listOf(FeedbackIntent.Warn), opened(destructive = true),
            "a destructive alert opened without warning. It is the one haptic in " +
                "the library that fires for something that has *not* happened, " +
                "and it is the reason the rest could go: feedback is worth having " +
                "where it says something the eye is not being told, and \"this " +
                "cannot be undone\" is that.",
        )
        assertEquals(
            emptyList(), opened(destructive = false),
            "a \"Save changes?\" buzzed. `hapticWarning` defaults from " +
                "`destructive`, and a non-destructive alert has nothing to warn " +
                "about however it is set.",
        )
        assertEquals(
            emptyList(), opened(destructive = true, hapticWarning = false),
            "`hapticWarning = false` did not switch it off. It is opt-out on the " +
                "dialogs that want it, for a destructive confirmation that opens " +
                "often enough for the warning to become wallpaper.",
        )
        assertEquals(
            emptyList(), opened(destructive = false, hapticWarning = true),
            "a non-destructive alert warned when asked to. There is nothing there " +
                "to warn about.",
        )
    }

    /**
     * A carousel dragged across pages ticks; one moved in code says nothing.
     *
     * Both halves, because the half that matters is the silent one. A page is a
     * detent in the strictest sense — the card snaps to it and rests there — but
     * `scrollToPage` is what the accessibility actions, the indicator's dots and
     * any autoplay call, and a carousel that buzzes when a dot is clicked is
     * buzzing for something the reader is already watching.
     */
    @Test
    fun aCarouselTicksForPagesDraggedAcrossAndNotForOnesScrolledToInCode() {
        val dragged = mutableListOf<FeedbackIntent>()
        var bounds = Rect.Zero
        lateinit var state: CarouselState

        Scene(width = 400, height = 300) {
            Recording(dragged) {
                state = rememberCarouselState(pageCount = { 5 })
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Carousel(
                        state = state,
                        contentDescription = "Stop photos",
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                            .reportBounds { bounds = it },
                    ) { page ->
                        Box(Modifier.fillMaxWidth().height(200.dp).background(Color.Gray)) {
                            io.kontour.ui.foundation.Text("Page $page")
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            // Slowly enough that the ticker's 80ms floor is not what is being
            // measured — see `DetentTicker.MinimumTickInterval`.
            scene.drag(
                from = bounds.alongX(0.9f),
                to = bounds.alongX(0.05f),
                steps = 24,
                paceMillis = 12,
            )
            scene.frames(20)
        }

        assertTrue(
            dragged.isNotEmpty() && dragged.all { it == FeedbackIntent.Tick },
            "dragging a carousel across pages fired ${dragged.summary()}. A page " +
                "is a detent: the card snaps to it and rests there, and the eye " +
                "is on the card rather than on a counter.",
        )

        val inCode = mutableListOf<FeedbackIntent>()
        var target by mutableStateOf(0)
        Scene(width = 400, height = 300) {
            Recording(inCode) {
                val carousel = rememberCarouselState(pageCount = { 5 })
                androidx.compose.runtime.LaunchedEffect(target) {
                    carousel.scrollToPage(target)
                }
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Carousel(
                        state = carousel,
                        contentDescription = "Stop photos",
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    ) { page ->
                        Box(Modifier.fillMaxWidth().height(200.dp).background(Color.Gray)) {
                            io.kontour.ui.foundation.Text("Page $page")
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            target = 3
            scene.frames(40)
        }

        assertEquals(
            emptyList(), inCode,
            "a carousel scrolled to a page in code fired ${inCode.summary()}. " +
                "`scrollToPage` is what the accessibility actions and the " +
                "indicator's dots call, and every one of those is something the " +
                "reader is watching happen.",
        )
    }

    /**
     * A day crossed under a finger is a detent, and a day tapped is not.
     *
     * The last of the grid's silences. Forty-two cells, a drag across a fortnight,
     * and nothing in the hand — where a day is a detent in the strictest sense: the
     * selection snaps to one and rests there, and a reader dragging a range is
     * watching the band rather than counting cells.
     *
     * Paced, like the carousel above, so what is measured is the crossings rather
     * than `DetentTicker`'s 80ms floor.
     *
     * The second half is the rule the audit settled on and the one a new call site
     * is most likely to break: **a tap onto a detent has not crossed one.** Tapping
     * a date already reports a `Tap` — a day cell is the smallest target the library
     * has — and a `Tick` on top of it would be the same news twice.
     */
    /**
     * A month paged by dwelling on its arrow mid-drag is a threshold passed, once:
     * what the drag is choosing from has just changed under the finger. The hold
     * before it rumbles faintly, so the hand knows the wait is counting.
     */
    @Test
    fun aMonthPagedMidDragIsOneThreshold() {
        val felt = mutableListOf<FeedbackIntent>()
        var start by mutableStateOf<LocalDate?>(null)
        var end by mutableStateOf<LocalDate?>(null)
        var bounds = Rect.Zero

        Scene(width = 700, height = 800) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    DateRangePicker(
                        start = start,
                        end = end,
                        onRangeSelected = { s, e -> start = s; end = e },
                        today = LocalDate(2026, 8, 1),
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(6)
            val size = bounds.width / 7f
            val gridTop = bounds.bottom - 6 * size
            fun cell(day: Int): Offset {
                val index = day - 1 + 5
                return Offset(
                    bounds.left + (index % 7 + 0.5f) * size,
                    gridTop + (index / 7 + 0.5f) * size,
                )
            }
            scene.drag(from = cell(28), to = cell(31), steps = 12, release = false, paceMillis = 12)
            // Onto the arrow in the blank after the 31st, and held past the dwell.
            val arrow = cell(31) + Offset(size, 0f)
            scene.move(arrow)
            scene.frames(60)
            scene.release(arrow)
            scene.frames(10)
        }

        assertEquals(
            1,
            felt.count { it == FeedbackIntent.DragThreshold },
            "paging by a dwell mid-drag fired ${felt.summary()} — one threshold for the month changing",
        )
        // And a faint rumble while it was held: several of the lightest pulses.
        assertTrue(
            felt.count { it == FeedbackIntent.Hold } >= 3,
            "holding past the last day fired ${felt.summary()} — a rumble of Hold pulses while the ring filled",
        )
    }

    @Test
    fun aDraggedRangeTicksPerDayAndATappedDateDoesNot() {
        val dragged = mutableListOf<FeedbackIntent>()
        var start by mutableStateOf<LocalDate?>(null)
        var end by mutableStateOf<LocalDate?>(null)
        var bounds = Rect.Zero

        Scene(width = 700, height = 800) {
            Recording(dragged) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    DateRangePicker(
                        start = start,
                        end = end,
                        onRangeSelected = { s, e -> start = s; end = e },
                        today = LocalDate(2026, 8, 1),
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(6)
            val size = bounds.width / 7f
            val gridTop = bounds.bottom - 6 * size
            fun cell(day: Int): Offset {
                val index = day - 1 + 5
                return Offset(
                    bounds.left + (index % 7 + 0.5f) * size,
                    gridTop + (index / 7 + 0.5f) * size,
                )
            }
            scene.drag(from = cell(10), to = cell(15), steps = 24, paceMillis = 12)
            scene.frames(20)
        }

        assertTrue(
            dragged.isNotEmpty() && dragged.all { it == FeedbackIntent.Tick },
            "dragging a range across five days fired ${dragged.summary()}. Each day " +
                "the finger crosses is a detent the selection rests on.",
        )

        val tapped = mutableListOf<FeedbackIntent>()
        var chosen by mutableStateOf<LocalDate?>(null)
        var tapBounds = Rect.Zero
        Scene(width = 700, height = 800) {
            Recording(tapped) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    DatePicker(
                        selected = chosen,
                        onSelectedChange = { chosen = it },
                        today = LocalDate(2026, 8, 1),
                        modifier = Modifier.reportBounds { tapBounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(6)
            val size = tapBounds.width / 7f
            val gridTop = tapBounds.bottom - 6 * size
            val index = 12 - 1 + 5
            scene.tap(
                Offset(
                    tapBounds.left + (index % 7 + 0.5f) * size,
                    gridTop + (index / 7 + 0.5f) * size,
                )
            )
            scene.frames(10)
        }

        assertEquals(
            listOf(FeedbackIntent.Tap),
            tapped,
            "tapping a date fired ${tapped.summary()}. A press on the smallest " +
                "target the library has reports that the control took it, once — a " +
                "tick as well would be a detent nothing crossed.",
        )
    }

    /**
     * A toast swiped past its dismiss point reports once, and only there.
     *
     * The same shape as the swipe row above and for the same reason: what
     * letting go will do has just changed, and the card slides under the finger
     * whether or not it is past the point of no return, so nothing on screen
     * says which.
     */
    @Test
    fun aSwipedToastFiresOnceAtItsDismissPoint() {
        val felt = mutableListOf<FeedbackIntent>()
        val host = ToastHostState()

        Scene(width = 400, height = 700) {
            Recording(felt) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White))
                    ToastHost(host)
                }
            }
        }.use { scene ->
            scene.frames(2)
            host.show("Trip removed")
            scene.frames(30)
            // Down and off the bottom edge, which is the way a bottom-anchored
            // toast leaves.
            scene.drag(
                from = Offset(200f, 620f),
                to = Offset(200f, 700f),
                steps = 24,
                release = false,
                paceMillis = 8,
            )
            scene.frames(4)
        }

        assertEquals(
            listOf(FeedbackIntent.DragThreshold), felt,
            "swiping a toast past its dismiss point fired ${felt.summary()}. One " +
                "report, at the one moment in the gesture that has a consequence " +
                "— and the threshold is derived from the release's own condition, " +
                "so the two cannot come to mean different things.",
        )
    }

    /** Installs a dispatcher that writes down what it is asked to do. */
    @Composable
    private fun Recording(into: MutableList<FeedbackIntent>, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalFeedback provides FeedbackDispatcher { into += it },
            content = content,
        )
    }

    private fun List<FeedbackIntent>.summary(): String =
        if (isEmpty()) "nothing at all" else groupingBy { it }.eachCount().toString()

}
