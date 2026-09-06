package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.WheelPicker
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Stepper
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.rememberSheetState
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Components given the arguments a real app produces on a bad day.
 *
 * Every sweep in this repository drives `ComponentSpec.content`, which is a
 * *fixed specimen* — one canned `Button`, one canned `Slider`, with hard-coded
 * literals inside it. The only axis they can vary from outside is the box the
 * specimen is drawn in, so between them they cover width, density and type size
 * and cannot vary a single argument. Nothing anywhere passed a component an
 * empty list, a zero count, an inverted range or a size of `0.dp`.
 *
 * Which is the axis those arguments actually come from. A count is
 * `seatsAvailable`, and it is zero on a full flight. A detent list is filtered,
 * and the filter can match nothing. A collection is still loading, so it is
 * empty for the first frame and not the second. None of these are contrived:
 * they are the ordinary states of screens the library is for.
 *
 * ### What passing means
 *
 * Two shapes, and the difference matters.
 *
 * [survives] — the input has a sensible reading, so the component renders it.
 * An empty carousel is a carousel with nothing in it, and drawing nothing is
 * correct.
 *
 * [refuses] — the input has no sensible reading, so the component throws
 * `IllegalArgumentException` at the call, in a message that names **both the
 * component and the parameter**. That is the good outcome. The bad one is the
 * same crash arriving later from inside `measure`, or from an accessibility
 * action, with a message about coercion.
 *
 * Both halves of that are load-bearing, and the second was learned here. The
 * first version of [refuses] only asked that the message mention the parameter,
 * and `Stepper(range = 0 until 0)` passed it — because the standard library's
 * own "Cannot coerce value to an empty range" contains the word "range". A
 * component's precondition has to be distinguishable from the internal failure
 * it was meant to replace, and naming itself is what does that.
 */
class DegenerateInputTest {

    // ---- sheets ----------------------------------------------------------

    /**
     * `rememberSheetState(detents = detents.filter { … })`, where the filter
     * matched nothing.
     *
     * `initialDetent` defaults to `detents.first()`, and a default argument is
     * evaluated at the call site — so this threw `NoSuchElementException` before
     * the friendly `require` inside `SheetState` ever ran. The KDoc actively
     * recommends filtering the list.
     */
    @Test
    fun aSheetWithNoDetentsSaysSo() = refuses("rememberSheetState(emptyList())", "SheetState", "detents") {
        rememberSheetState(detents = emptyList())
        Unit
    }

    /**
     * The same list, emptied after the sheet exists.
     *
     * `detents` is a public `var` and was assigned unconditionally, so this
     * succeeded silently and took the frame down later from inside `DragHandle`,
     * where `SheetHeader` calls `detents.last()`.
     */
    @Test
    fun emptyingASheetsDetentsSaysSo() = refuses("state.detents = emptyList()", "SheetState", "detents") {
        val state = rememberSheetState(detents = listOf(SheetDetent.Hidden, SheetDetent.Full))
        state.detents = emptyList()
    }

    // ---- counters --------------------------------------------------------

    /**
     * `Stepper(range = 0 until seatsAvailable)` on a full flight.
     *
     * `0 until 0` is an empty `IntRange`, and `coerceIn` throws on one — from
     * composition, where nothing can catch it. The most ordinary empty state a
     * counter has.
     */
    @Test
    fun aStepperOverAnEmptyRangeSaysSo() = refuses("Stepper(range = 0 until 0)", "Stepper", "range") {
        Stepper(value = 0, onValueChange = {}, contentDescription = "Bags", range = 0 until 0)
    }

    /**
     * A range that reaches the end of `Int`.
     *
     * The value cell is sized by measuring candidate strings at each digit
     * boundary, and the boundary walk is `boundary * 10 + 9` — which overflows
     * to a negative number somewhere past a billion and then climbs again, so
     * the loop condition never settles.
     */
    @Test
    fun aStepperOverTheWholeIntRangeTerminates() = survives("Stepper(range = 0..Int.MAX_VALUE)") {
        Stepper(
            value = 1,
            onValueChange = {},
            contentDescription = "Anything",
            range = 0..Int.MAX_VALUE,
        )
    }

    // ---- sliders ---------------------------------------------------------

    /**
     * An inverted `valueRange`, which the visible control cannot reach.
     *
     * The crash was in the `setProgress` **semantics action** — so no screenshot
     * could see it, no gesture test could reach it, and the first person to find
     * it would be using a screen reader.
     */
    @Test
    fun aSliderWithAnInvertedRangeSaysSo() = refuses("Slider(valueRange = 10f..0f)", "Slider", "valueRange") {
        Slider(value = 5f, onValueChange = {}, valueRange = 10f..0f, modifier = Modifier.width(200.dp))
    }

    @Test
    fun aRangeSliderWithAnInvertedRangeSaysSo() =
        refuses("RangeSlider(valueRange = 10f..0f)", "RangeSlider", "valueRange") {
            RangeSlider(
                value = 2f..4f,
                onValueChange = {},
                valueRange = 10f..0f,
                modifier = Modifier.width(200.dp),
            )
        }

    /** A range with no width at all is degenerate but readable: everything is at 0%. */
    @Test
    fun aSliderWithAnEmptyRangeDraws() = survives("Slider(valueRange = 1f..1f)") {
        Slider(value = 1f, onValueChange = {}, valueRange = 1f..1f, modifier = Modifier.width(200.dp))
    }

    // ---- collections that are still loading ------------------------------

    /**
     * `rememberCarouselState { photos.size }` on the frame before `photos` loads.
     */
    @Test
    fun anEmptyCarouselDraws() = survives("Carousel over zero pages") {
        val state = rememberCarouselState { 0 }
        Carousel(state = state, contentDescription = "Stop photos") { Text("page $it") }
    }

    /** The same lambda, subtracting a page count from something not yet known. */
    @Test
    fun aCarouselWithANegativePageCountSaysSo() = refuses("Carousel over -1 pages", "Carousel", "pageCount") {
        val state = rememberCarouselState { -1 }
        Carousel(state = state, contentDescription = "Stop photos") { Text("page $it") }
    }

    // ---- sizes the caller passes -----------------------------------------

    /**
     * A drum row with no height.
     *
     * The loud neighbour of a quiet bug: `visibleItems` has been rejecting even
     * numbers with a message since it was written, while `itemHeight` — divided
     * by in four places — accepted `0.dp` and produced NaN, which does not throw
     * and does not draw. A picker that is silently stuck is worse than one that
     * refuses.
     */
    @Test
    fun aWheelPickerWithNoRowHeightSaysSo() = refuses("WheelPicker(itemHeight = 0.dp)", "WheelPicker", "itemHeight") {
        WheelPicker(
            items = listOf("one", "two", "three"),
            selected = 0,
            onSelectedChange = {},
            label = { it },
            itemHeight = 0.dp,
        )
    }

    /** Empty items is a list that has not arrived, and drawing nothing is right. */
    @Test
    fun aWheelPickerWithNoItemsDraws() = survives("WheelPicker(items = emptyList())") {
        WheelPicker(
            items = emptyList<String>(),
            selected = 0,
            onSelectedChange = {},
            label = { it },
        )
    }

    // ---- the two shapes --------------------------------------------------

    private fun survives(what: String, content: @Composable () -> Unit) {
        try {
            Scene(width = 600, height = 400, density = 2f) {
                Box(Modifier.fillMaxSize()) { content() }
            }.use { it.advance(3) }
        } catch (error: Throwable) {
            fail(
                "$what threw ${error::class.simpleName}: ${error.message}\n\n" +
                    "This input has a sensible reading and the component should render it. " +
                    "It is the shape an app produces while a collection is loading or a " +
                    "count is zero, so it reaches users before anything else does.",
            )
        }
    }

    private fun refuses(
        what: String,
        component: String,
        parameter: String,
        content: @Composable () -> Unit,
    ) {
        val thrown = try {
            Scene(width = 600, height = 400, density = 2f) {
                Box(Modifier.fillMaxSize()) { content() }
            }.use { it.advance(3) }
            null
        } catch (error: Throwable) {
            error
        }

        if (thrown == null) {
            fail(
                "$what was accepted. It has no sensible reading, so accepting it means the " +
                    "failure arrives later — from measure, from a draw, or from an " +
                    "accessibility action — where there is no caller to blame and no " +
                    "message worth reading.",
            )
        }
        assertTrue(
            thrown is IllegalArgumentException,
            "$what threw ${thrown::class.simpleName}, not IllegalArgumentException: " +
                "${thrown.message}. A caller who passed bad input should get a precondition, " +
                "not the internal failure it happened to cause first.",
        )
        for (word in listOf(component, parameter)) {
            assertTrue(
                thrown.message?.contains(word, ignoreCase = true) == true,
                "$what threw with \"${thrown.message}\", which does not mention " +
                    "\"$word\". A precondition has to name the component and the " +
                    "parameter, or it is indistinguishable from the internal failure it " +
                    "was written to replace — the standard library's own message for an " +
                    "empty range says \"range\" too.",
            )
        }
    }
}
