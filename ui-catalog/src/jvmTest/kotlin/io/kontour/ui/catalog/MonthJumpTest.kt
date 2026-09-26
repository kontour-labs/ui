package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import io.kontour.ui.components.datetime.CalendarNavigationState
import io.kontour.ui.components.datetime.DatePicker
import io.kontour.ui.components.datetime.rememberCalendarNavigationState
import io.kontour.ui.overlay.OverlayHost
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The month header opens two wheels, and turning one moves the calendar.
 *
 * Paging a month at a time is right for "next week" and hopeless for a birthday.
 * The header was a bare `Text`, so a date twelve years back was twelve years of
 * tapping; given a `chooserIcon` it is a button, and behind it are the same two
 * drums `TimePicker` is made of. The icon is the switch and not only the glyph —
 * a popover needs an `OverlayHost`, which a calendar drawn inline may not have —
 * so this passes one, as the component's own docs do.
 *
 * ### What this has to prove that a screenshot cannot
 *
 * Three things, and each of them was a way for this to be built and not work:
 *
 * - the header is **reachable** — a `Role.Button` with a press, not a title that
 *   happens to have a popover declared beside it;
 * - the popover **survives the gesture**. A panel that opened and then closed on
 *   the first frame of the drag looks exactly like a failed tap, and the plan for
 *   this expected to have to turn `dismissOnScroll` off to stop that. It did not:
 *   see `MonthAndYearButton`'s own note;
 * - the drum drives `jumpTo` **live**, so the grid behind the popover is already
 *   showing the month the wheel is on rather than waiting for a dismissal.
 *
 * The state is read through a hoisted [CalendarNavigationState] rather than off
 * the pixels, because what the header and the grid agree on is the point: a
 * picker that moved its title and not its month would pass a screenshot.
 */
class MonthJumpTest {

    @Test
    fun theHeaderOpensTheWheelsAndTurningOneMovesTheMonth() {
        lateinit var navigation: CalendarNavigationState
        var header = Rect.Zero

        Scene(width = 420, height = 760) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    navigation = rememberCalendarNavigationState(Start)
                    DatePicker(
                        value = Start,
                        onValueChange = {},
                        today = Start,
                        navigation = navigation,
                        // The paging icons are what put the title in the middle:
                        // the header is a `SpaceBetween` row, so with nothing
                        // either side of it the title sits at the start. This is
                        // the arrangement the component is documented with, and
                        // the first version of this test aimed at the centre of a
                        // header that had no reason to have anything there.
                        previousIcon = Tabler.Outline.ChevronLeft,
                        nextIcon = Tabler.Outline.ChevronRight,
                        chooserIcon = Tabler.Outline.ChevronDown,
                        modifier = Modifier.fillMaxSize().reportBounds { header = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(4)
            assertEquals(
                Start.month,
                navigation.visibleMonth.month,
                "the calendar did not start where it was told",
            )

            // The header row is the full width of the picker and sits at its top,
            // so a tap at the middle of the first touch target's worth of it is
            // the title and nothing else. The paging buttons are at the ends.
            val title = Offset(header.center.x, header.top + HeaderCentre)
            scene.tap(title)
            scene.frames(8)

            // The month wheel is the left half of the panel, and the panel is
            // centred under the header. Dragged upward, which on a drum is
            // *forward* through the list: later months, and the year is not
            // touched.
            val wheel = Offset(header.center.x - WheelOffset, header.top + PanelCentre)
            scene.drag(
                from = wheel,
                to = Offset(wheel.x, wheel.y - RowsToTurn),
                steps = 18,
                pointer = PointerType.Touch,
            )
            scene.frames(30)
        }

        val moved = navigation.visibleMonth
        assertTrue(
            moved != LocalDate(Start.year, Start.month, 1),
            "the calendar is still on ${moved.month} ${moved.year}. Either the " +
                "header did not open the popover, or the drum turned and is not " +
                "wired to `jumpTo` — both of which were tried against this and " +
                "both of which it catches",
        )
        assertEquals(
            Start.year,
            moved.year,
            "turning the month wheel moved the year as well, to ${moved.year} — the " +
                "two drums are meant to be independent",
        )
    }

    /**
     * With the wheels up, a month change **cuts**. With them down, it slides.
     *
     * The two arms are one claim: the transition is switched off by the chooser
     * and not by having been deleted. Reported as the date picker going to mush
     * while a drum was turning, and the cause is in `AnimatedContent`'s contract
     * rather than in the transition's length — every target it has not finished
     * *leaving* stays composed, and a drum flicked through a year hands it a new
     * one every couple of frames. Ten month grids at forty-two `DayCell`s each,
     * every cell holding two colour animations and a scale.
     *
     * Read off `hasInvalidations` rather than off the pixels, because "is
     * anything still animating" is exactly the question and a screenshot of a
     * grid mid-slide is a screenshot of a grid.
     */
    @Test
    fun theGridCutsWhileTheWheelsAreUpAndSlidesWhenTheyAreNot() {
        lateinit var navigation: CalendarNavigationState
        var header = Rect.Zero

        Scene(width = 420, height = 760) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    navigation = rememberCalendarNavigationState(Start)
                    DatePicker(
                        value = Start,
                        onValueChange = {},
                        today = Start,
                        navigation = navigation,
                        previousIcon = Tabler.Outline.ChevronLeft,
                        nextIcon = Tabler.Outline.ChevronRight,
                        chooserIcon = Tabler.Outline.ChevronDown,
                        modifier = Modifier.fillMaxSize().reportBounds { header = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(4)

            // Closed: the month slides, which is what the component is for.
            navigation.step(1)
            scene.frames(2)
            assertTrue(
                scene.stillAnimating(),
                "a month change with the chooser closed did not animate at all — " +
                    "this arm is here so the other one cannot pass by the " +
                    "transition having been deleted",
            )
            scene.renderUntil(2_000) { !scene.stillAnimating() }

            scene.tap(Offset(header.center.x, header.top + HeaderCentre))
            scene.frames(8)
            val quiet = scene.renderUntil(2_000) { !scene.stillAnimating() }
            assertTrue(quiet != null, "the popover never came to rest")

            // Open: the same change, cut.
            navigation.step(1)
            scene.frames(3)
            assertTrue(
                !scene.stillAnimating(),
                "the month grid is still animating with the wheels up. Every month " +
                    "a turning drum passes through is then a grid of forty-two " +
                    "animating cells kept alive until its exit finishes",
            )
        }
    }

    private companion object {
        val Start = LocalDate(2026, 6, 12)

        /**
         * Down the picker to the middle of the header row, in scene pixels.
         *
         * The row is the title's own touch target plus the frame's vertical
         * padding, and at the scene's density of 2 its middle is here. Read off a
         * rendered frame rather than derived, because a title's height is a font
         * metric and this test is not about typography.
         */
        const val HeaderCentre = 52f

        /**
         * Down the picker to the selection band of the popover's wheels.
         *
         * The panel opens below the header — `Popover`'s own gap and padding down
         * from it — and a five-row drum at 40dp a row puts its centre band here.
         * Also read off a frame: the point of aiming at the band rather than at
         * the panel's top is that a drag has to start *on the drum*.
         */
        const val PanelCentre = 328f

        /**
         * Left of centre, onto the month drum rather than the year one.
         *
         * The two wheels split the panel's width, so anything inside the left
         * half is the month. Far enough from the middle that the gap between them
         * is not where the finger lands.
         */
        const val WheelOffset = 87f

        /**
         * Far enough to cross at least one row and stay well inside the list.
         *
         * A row is 40dp, so 80px at this density, and touch slop eats the first
         * eighteen-odd — which is why this is several rows rather than one. Which
         * month it lands on is not the claim; that it moved and took the year with
         * it is.
         */
        const val RowsToTurn = 200f

    }
}