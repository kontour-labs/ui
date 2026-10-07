package io.kontour.ui.catalog

import io.kontour.ui.sheet.SheetPartPin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetPresentation
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sheet with a part pinned to its bottom moves with the finger, and no further.
 *
 * Reported as *"when pinned is enabled, the sheet races ahead of my finger"*. A
 * floating sheet that morphs into an edge sheet hands its content the bottom
 * safe area as it morphs, and the pinned part sits above that safe area, so the
 * part's block grew as the sheet rose. the collapsed detent was sized from the
 * block as it was *now*, so its anchor moved on every frame of a drag, and each
 * move of the anchors snapped the sheet to its target: dragged down from full
 * height it jumped ahead to Half and on, four times as far as the finger, and a
 * drag on the list did not move it at all.
 *
 * Read from the sheet's offset against the finger, over a window with a bottom
 * inset, as a phone has. The edge sheet, whose safe area never changes, is the
 * control.
 */
class SheetFooterDragTest {

    private companion object {
        const val Density = 2f
        const val Width = 390
        const val Height = 760

        /** What the finger travels before the sheet takes the drag, with room to spare. */
        const val Slop = 40f
        const val Pull = 200f
    }

    @Composable
    private fun Harness(presentation: SheetPresentation, start: SheetDetent, onState: (SheetState) -> Unit) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            OverlayHost(Modifier.fillMaxSize()) {
                val state = rememberSheetState(
                    detents = listOf(SheetDetent.parts(), SheetDetent.Half, SheetDetent.Expanded),
                    initialDetent = SheetDetent.parts(),
                )
                onState(state)
                LaunchedEffect(start) { state.animateTo(start) }
                val query = rememberTextFieldState()
                BottomSheet(
                    state = state,
                    presentation = presentation,
                    windowInsets = WindowInsets(top = 47.dp, bottom = 34.dp),
                ) { padding: PaddingValues ->
                    part(pin = SheetPartPin.Bottom) { SearchField(query, placeholder = "Search stops") }
                    LazyColumn(contentPadding = padding) {
                        items(40) { ListItem(onClick = {}) { +"Stop $it" } }
                    }
                }
            }
        }
    }

    /** How far the sheet moved for a [Pull] from [start], grabbed [below] its top edge. */
    private fun moved(presentation: SheetPresentation, start: SheetDetent, below: Float, down: Boolean): Float {
        var state: SheetState? = null
        return Scene(width = Width * 2, height = Height * 2, density = Density, reduceMotion = true) {
            Harness(presentation, start) { state = it }
        }.use { scene ->
            scene.frames(40)
            val sheet = state!!
            val from = sheet.offset
            val grab = Offset(Width.toFloat(), from + below)
            val to = grab + Offset(0f, if (down) Pull else -Pull)
            scene.drag(grab, to, steps = 20, release = false)
            val travelled = sheet.offset - from
            scene.release(to)
            if (down) travelled else -travelled
        }
    }

    private fun assertFollows(presentation: SheetPresentation, what: String, moved: Float) {
        assertTrue(
            moved <= Pull + 1f && moved >= Pull - Slop,
            "${presentation.name}, $what: the sheet moved ${moved}px for a ${Pull}px drag — " +
                if (moved > Pull) "it ran ahead of the finger" else "it did not follow the finger",
        )
    }

    @Test
    fun draggedDownFromFullHeightTheSheetFollowsTheFinger() {
        for (presentation in listOf(SheetPresentation.Floating, SheetPresentation.Edge)) {
            assertFollows(presentation, "down from Expanded, by the handle", moved(presentation, SheetDetent.Expanded, below = 12f, down = true))
            assertFollows(presentation, "down from Expanded, by the list", moved(presentation, SheetDetent.Expanded, below = 300f, down = true))
        }
    }

    @Test
    fun draggedUpFromHalfByTheListTheSheetFollowsTheFinger() {
        for (presentation in listOf(SheetPresentation.Floating, SheetPresentation.Edge)) {
            assertFollows(presentation, "up from Half, by the list", moved(presentation, SheetDetent.Half, below = 240f, down = false))
        }
    }
}
