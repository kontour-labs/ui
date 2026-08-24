package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.DatePicker
import io.kontour.ui.components.datetime.TimePicker
import io.kontour.ui.overlay.AlertDialog
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.kontourSizing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.fail

/**
 * The things that own the screen still fit when the screen is short.
 *
 * Every canvas in this project is tall. The goldens run to 8000px, the render
 * gallery gives each specimen its own card, and the phone pages are 2400px of
 * scroll. Nothing has ever been asked what happens in a **landscape phone** —
 * 360dp of height, which is less than a date picker wants and about half what a
 * time picker's three wheels want.
 *
 * That matters most for the components a user cannot scroll past: a sheet, a
 * dialog, a picker inside one. A calendar taller than the window with nothing to
 * scroll is a calendar whose last week cannot be reached, and there is no
 * picture of it in this repository that would show that, because there is no
 * canvas short enough to take one.
 *
 * ### Measured as content that does not survive the squeeze
 *
 * Two instruments were wrong before this one, and both were wrong in a way worth
 * writing down.
 *
 * Reading the page's ink and asking whether the last rows were still background
 * is right for a picker on a page and wrong for anything with a **scrim**: a
 * dialog dims the whole window by design, so its ink reaches every row and the
 * check called it an overflow at every size.
 *
 * Reading the component's **bounds** is worse, because it passes. A `DatePicker`
 * inside a `fillMaxSize` box is handed the window's height as its maximum, so
 * its bounds are exactly the window however much content it has — and a render
 * of that same picker at 360dp shows March cut off mid-row with the last week
 * gone. The bounds were clamped; the content was clipped; the assertion saw
 * nothing.
 *
 * So the component is measured twice: once in a window taller than anything
 * needs, which is what it *wants*, and once in the short one. Wanting more than
 * the window has is fine if it can be scrolled to — that is what scrolling is
 * for — and a defect if it cannot, because the part past the fold is then simply
 * unreachable.
 */
class ShortWindowTest {

    @Test
    fun whatOwnsTheScreenFitsALandscapePhone() {
        val failures = mutableListOf<String>()

        for (case in Cases) {
            for ((label, size) in Windows) {
                val (w, h) = size
                val complaint = try {
                    val roomy = render(case, w, TallEnough)
                    val cramped = render(case, w, h)
                    // Laid out shorter than it wants *and* not scrollable means
                    // the difference went nowhere a finger can reach it. A
                    // scroller reports the same height in both, because it takes
                    // the room it is given and moves its content inside it.
                    val lost = (roomy.height - cramped.height) / Density
                    if (lost > Slack && !case.scrolls) {
                        "loses ${lost.toInt()}dp in a ${h}dp window — it wants " +
                            "${(roomy.height / Density).toInt()}dp, is given " +
                            "${(cramped.height / Density).toInt()}, and has no way to " +
                            "reach the rest"
                    } else {
                        null
                    }
                } catch (error: Throwable) {
                    "threw ${error::class.simpleName}: ${error.message?.lineSequence()?.firstOrNull()}"
                }
                if (complaint != null) failures += "${case.name} in $label — $complaint"
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} components did not fit a short window:\n" +
                    failures.joinToString("\n") { "  · $it" }
            )
        }
    }

    /**
     * Where the component was laid out, in root pixels.
     *
     */
    private fun render(case: Case, width: Int, height: Int): Rect {
        var box = Rect.Zero
        Scene(width = width * Density, height = height * Density, density = Density.toFloat()) {
            KontourTheme(
                reduceMotion = true,
                sizing = kontourSizing(ContrastLevel.Standard).copy(minTouchTarget = 48.dp),
            ) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        case.content(Modifier.reportBounds { box = it })
                    }
                }
            }
        }.use { it.frames(Frames) }
        return box
    }

    /**
     * @param scrolls True for something that is *supposed* to fill the window and
     *   move its content inside it — a sheet is the shape of the window by
     *   definition, and calling that a loss would be asking it to be short.
     */
    private class Case(
        val name: String,
        val scrolls: Boolean = false,
        val content: @Composable (Modifier) -> Unit,
    )

    private val Cases = listOf(
        // In a dialog, which is where a picker lives. A picker dropped straight
        // onto a page that does not scroll loses its last rows and always will —
        // that is a fact about a month grid, stated on `DatePicker` itself, not a
        // bug the component can fix from the inside.
        Case("DatePicker in a Dialog", scrolls = true) { probe ->
            var day by mutableStateOf<LocalDate?>(LocalDate(2026, 3, 14))
            Dialog(visible = true, onDismissRequest = {}, modifier = probe) {
                DatePicker(
                    selected = day,
                    onSelectedChange = { day = it },
                    today = LocalDate(2026, 3, 10),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        Case("TimePicker in a Dialog", scrolls = true) { probe ->
            var time by mutableStateOf(LocalTime(9, 30))
            Dialog(visible = true, onDismissRequest = {}, modifier = probe) {
                TimePicker(
                    value = time,
                    onValueChange = { time = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        Case("AlertDialog") { probe ->
            AlertDialog(
                visible = true,
                modifier = probe,
                onDismissRequest = {},
                confirmLabel = "Leave",
                onConfirm = {},
                cancelLabel = "Stay",
            ) {
                +"Leave this journey?"
                supporting {
                    +(
                        "Your planned route will not be saved, and the stops you " +
                            "added along the way will be forgotten. This cannot be undone."
                        )
                }
            }
        },
        Case("BottomSheet", scrolls = true) { probe ->
            val sheet = rememberSheetState(initialDetent = SheetDetent.Expanded)
            BottomSheet(state = sheet, modifier = probe.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(200.dp).background(Color(0xFFEEEEEE)))
            }
        },
    )

    /**
     * The canary: a column too tall for the window, with nothing to scroll it.
     *
     * A sweep that goes green on its first run has told you nothing, and this one
     * had two wrong instruments before it had a right one. This case must always
     * fail — so if it ever stops, the measurement has gone blind again rather
     * than the library having got better.
     */
    @Test
    fun theMeasurementCanStillSeeAnOverflow() {
        val roomy = render(TooTall, 640, TallEnough)
        val cramped = render(TooTall, 640, 360)
        val lost = (roomy.height - cramped.height) / Density
        if (lost <= Slack) {
            fail(
                "a 900dp column in a 360dp window reported losing ${lost}dp. The " +
                    "measurement is no longer able to see an overflow, which is " +
                    "how both of its predecessors failed."
            )
        }
    }

    private val TooTall = Case("a column taller than the window") { probe ->
        Box(probe.fillMaxWidth().height(900.dp).background(Color(0xFFDDDDDD)))
    }

    private companion object {
        const val Density = 2

        /** A phone on its side, and a small one on its side. */
        val Windows = listOf(
            "800×360" to (800 to 360),
            "640×360" to (640 to 360),
        )

        const val Frames = 12

        /** A pixel of rounding, and nothing more. */
        const val Slack = 2f

        /** Taller than anything here wants, so the first render is the true height. */
        const val TallEnough = 1600
    }
}
