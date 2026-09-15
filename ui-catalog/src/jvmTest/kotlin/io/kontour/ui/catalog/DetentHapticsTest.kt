package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.datetime.WheelPicker
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.CarouselState
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.rememberReorderableState
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

/**
 * What the library is allowed to buzz for.
 *
 * The policy, in one sentence: **a haptic reports something the user could not
 * otherwise tell, and there are four of those.** A detent crossed under a
 * finger, a threshold passed that changes what letting go will do, a long press
 * reaching the point where it becomes a gesture, and a destructive question
 * arriving. Everything else — pressing a button, tapping a switch, choosing a
 * date, tapping a tab, opening a menu, landing on a step you tapped — is the
 * user doing something they are watching happen, and a buzz adds nothing to it.
 *
 * The switch is the one component on both sides of that line, and it is the
 * only site added since the audit: tapping it is silent, and dragging it past
 * its midpoint reports, because the drag commits there and nothing on screen
 * says so first.
 *
 * The previous policy was "make it tactile", and fifty-seven call sites took
 * that literally. Every `clickable` in the library fired, every `toggleable`
 * fired, a stepped slider fired on the press *and* on the release, a swipe row
 * fired four different intents across one gesture, and a wheel picker fired the
 * moment it was composed. The report was that it had become "a bit ridiculous",
 * which is the correct reading of a component set that vibrates when you look at
 * it.
 *
 * ### Why "no intent" is stronger than "no haptic"
 *
 * These record [FeedbackIntent]s, not platform haptics. A component that
 * performs no intent is silent at *every* [io.kontour.ui.interaction.HapticsLevel]
 * and under every consumer's replacement dispatcher, because there is nothing
 * for a level or a mapping to let through. Asserting an empty list is therefore
 * the whole claim, and it needs no `Full`/`Essential`/`Off` sweep to make it.
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
     * A switch is two gestures and only one of them is worth reporting.
     *
     * It used to be on the silent list whole, on the argument that "a toggle is
     * a decision the user made and can see the result of". That is still true
     * of a *tap*, and it was true of a drag too while the drag reported on
     * release — nothing changed until the finger lifted, so there was no moment
     * to announce.
     *
     * The drag commits at the midpoint now. Crossing it changes what letting go
     * will do, with nothing on screen having said so first, which is the
     * `DragThreshold` row of the policy table word for word. So the switch is
     * the one component on both lists, and this asserts both halves — the tap
     * silent, the drag exactly one fire, no more.
     */
    @Test
    fun aSwitchIsSilentTappedAndReportsTheCrossingOnceDragged() {
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
            emptyList(), tapped,
            "tapping a switch fired ${tapped.summary()}. It was named in the " +
                "original report: a toggle you press is a decision you made and " +
                "can see the result of.",
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

    @Test
    fun aRatingDragFiresNothing() {
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
            scene.drag(bounds.alongX(0.05f), bounds.alongX(0.95f), steps = 24)
            scene.frames(3)
        }

        assertTrue(score > 0f, "the drag never reached the rating")
        assertEquals(
            emptyList(), felt,
            "dragging across a rating fired ${felt.summary()}. There are no real " +
                "detents here — the marks are five drawings on one continuous " +
                "track, and the eye is on them the whole way across.",
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
            // `Scene.drag`. Forty steps 20ms apart is a deliberate 800ms drag,
            // which crosses its ten detents about 80ms apart. Unpaced the same
            // drag takes almost no real time at all and the limit swallows half
            // of them, which is a true statement about a gesture no hand makes.
            it.drag(bounds.alongX(0.02f), bounds.alongX(0.98f), steps = 40, paceMillis = 20)
            it.frames(4)
        }

        val ticks = dragged.count { it == FeedbackIntent.Tick }
        assertTrue(
            ticks in 8..12,
            "dragging across ten steps produced $ticks ticks " +
                "(${dragged.summary()}). Ten boundaries were crossed.",
        )
        assertTrue(
            dragged.none { it != FeedbackIntent.Tick },
            "the drag fired something other than detents: ${dragged.summary()}",
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
