package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The search pill in a bar becomes the field it opens: a capsule leaves the pill
 * where it sits and ends where the field is, and going back it returns into the
 * pill, the panel staying until it has landed. It used to appear over the page
 * with nothing joining it to what had been tapped.
 *
 * Measured on the capsule itself, through the rectangles it is drawn in.
 */
@OptIn(ExperimentalTestApi::class)
class NavSearchMorphTest {

    @Test
    fun overTheKeyboardThePillGrowsIntoTheField() = morphs(NavExpandPlacement.AboveKeyboard)

    @Test
    fun atTheTopThePillGrowsIntoTheField() = morphs(NavExpandPlacement.Top)

    private fun morphs(placement: NavExpandPlacement) = runComposeUiTest {
        val drawn = mutableListOf<Rect>()
        val state = bar(placement, reduceMotion = false) { drawn += it }
        val pill = onAllNodesWithText("Search").fetchSemanticsNodes().first().boundsInRoot

        mainClock.autoAdvance = false
        state.expand()
        val opening = frames { drawn }
        // The editable text inside the field; the capsule ends on the field
        // round it, so it should hold this and share its middle.
        val text = onAllNodes(hasSetTextAction()).fetchSemanticsNodes().single().boundsInRoot

        assertTrue(opening.isNotEmpty(), "$placement: nothing joined the pill to the field")
        assertTrue(opening.first().near(pill), "$placement: the capsule started at ${opening.first()}, the pill is at $pill")
        val end = opening.last()
        assertTrue(
            end.contains(text.topLeft) && end.contains(text.bottomRight) && (end.center - text.center).getDistance() <= 2f,
            "$placement: the capsule ended at $end, not round the field's text at $text",
        )
        assertTrue(
            opening.any { !it.near(pill) && !it.near(end) },
            "$placement: the capsule jumped from the pill to the field without travelling",
        )

        drawn.clear()
        state.collapse()
        mainClock.advanceTimeByFrame()
        assertTrue(
            onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty(),
            "$placement: the panel went at once instead of going back into the pill",
        )
        val closing = frames { drawn }
        assertTrue(closing.isNotEmpty(), "$placement: nothing took the field back to the pill")
        assertTrue(closing.last().near(pill), "$placement: the capsule ended at ${closing.last()} going back, the pill is at $pill")
        // The host's own leaving, once the capsule is home.
        repeat(15) { mainClock.advanceTimeByFrame() }
        assertTrue(
            onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty(),
            "$placement: the panel was still there once it had landed",
        )
        mainClock.autoAdvance = true
    }

    /** Under reduced motion the field fades in where it is: nothing travels. */
    @Test
    fun underReducedMotionItFadesInPlace() = runComposeUiTest {
        val drawn = mutableListOf<Rect>()
        val state = bar(NavExpandPlacement.AboveKeyboard, reduceMotion = true) { drawn += it }
        mainClock.autoAdvance = false
        state.expand()
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertTrue(onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty(), "the field should be there at once")
        frames { drawn }
        assertTrue(drawn.isEmpty(), "under reduced motion a capsule travelled: $drawn")
        mainClock.autoAdvance = true
    }

    /** Runs frames until the capsule has not been drawn for a while, and hands back what it drew. */
    private fun ComposeUiTest.frames(drawn: () -> List<Rect>): List<Rect> {
        var quiet = 0
        var seen = 0
        repeat(60) {
            mainClock.advanceTimeByFrame()
            if (drawn().size == seen) quiet++ else quiet = 0
            seen = drawn().size
            if (quiet > 10) return drawn().toList()
        }
        return drawn().toList()
    }

    private fun Rect.near(other: Rect): Boolean =
        abs(left - other.left) <= 2f && abs(top - other.top) <= 2f &&
            abs(right - other.right) <= 2f && abs(bottom - other.bottom) <= 2f

    private fun ComposeUiTest.bar(
        placement: NavExpandPlacement,
        reduceMotion: Boolean,
        probe: (Rect) -> Unit,
    ): NavSearchState {
        lateinit var state: NavSearchState
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = reduceMotion) {
                CompositionLocalProvider(LocalNavMorphProbe provides probe) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.White)) {
                            state = rememberNavSearchState()
                            NavBar {
                                item(true, {}, key = "nearby", icon = SystemIcons.Check) { +"Nearby" }
                                slot(weight = 1f) { NavSearch(state = state, placement = placement) }
                                item(false, {}, key = "routes", icon = SystemIcons.Close) { +"Routes" }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        return state
    }
}
