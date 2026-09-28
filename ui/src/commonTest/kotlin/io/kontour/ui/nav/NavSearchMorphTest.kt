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
        val state = bar(placement, reduceMotion = false) { drawn += it.capsule }
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

    /**
     * The pill's icon and its word go with the capsule: they leave from where the
     * pill drew them and land where the field draws its own.
     *
     * Asked for from a phone: "can we make the 'Search' text and icon animate in
     * there too?" The capsule travelled bare and the field's icon and placeholder
     * faded in at the far end, so the word the reader tapped vanished with the
     * pill and a second copy appeared somewhere else.
     */
    @Test
    fun theIconAndTheWordTravelFromThePillIntoTheField() = runComposeUiTest {
        val drawn = mutableListOf<NavMorphDrawn>()
        val state = bar(NavExpandPlacement.AboveKeyboard, reduceMotion = false, icon = true) { drawn += it }
        val pill = onAllNodesWithText("Search").fetchSemanticsNodes().first().boundsInRoot
        val pillWord = onAllNodesWithText("Search", useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot

        mainClock.autoAdvance = false
        state.expand()
        val opening = frames { drawn }.filter { it.icon != null && it.word != null }
        val fieldWord = onAllNodesWithText("Search", useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .single { !it.near(pillWord) }
        mainClock.autoAdvance = true

        assertTrue(opening.size > 2, "the capsule carried nothing: ${drawn.take(3)}")
        val first = opening.first()
        val last = opening.last()
        assertTrue(first.word!!.near(pillWord), "the word left from ${first.word}, the pill's is at $pillWord")
        assertTrue(last.word!!.near(fieldWord), "the word landed at ${last.word}, the field's placeholder is at $fieldWord")
        assertTrue(
            opening.any { !it.word!!.near(pillWord) && !it.word!!.near(fieldWord) },
            "the word jumped from the pill to the field without travelling",
        )

        // The icon: in the pill ahead of its word at the start, in the field ahead
        // of its placeholder at the end, and level with each.
        val startIcon = first.icon!!
        val endIcon = last.icon!!
        assertTrue(
            pill.contains(startIcon.center) && startIcon.right <= pillWord.left && abs(startIcon.center.y - pillWord.center.y) <= 1f,
            "the icon left from $startIcon, not beside the pill's word at $pillWord",
        )
        assertTrue(
            last.capsule.contains(endIcon.center) && endIcon.right <= fieldWord.left && abs(endIcon.center.y - fieldWord.center.y) <= 1f,
            "the icon landed at $endIcon, not beside the field's placeholder at $fieldWord",
        )
    }

    /**
     * Nothing in the panel starts to show until the capsule has landed, and the
     * capsule stays until the panel is opaque over it.
     *
     * The two halves of what makes the hand-over invisible: the parts the capsule
     * carries are put down exactly where the field draws its own, so the field
     * fading in over them changes nothing — but only if it fades in over parts
     * that have stopped, and only if they are still there underneath until it is
     * done.
     */
    @Test
    fun thePanelShowsOnlyOnceTheCapsuleHasLandedAndTheCapsuleStaysUntilItIsOpaque() {
        var progress = 0f
        val morph = NavMorph { progress }
        for (step in 0..200) {
            progress = step / 200f
            if (morph.geometry < 1f) {
                assertTrue(morph.content == 0f, "at $progress the capsule was still travelling and the panel was ${morph.content} in")
            }
            if (morph.content < 1f) {
                assertTrue(morph.capsule == 1f, "at $progress the panel was ${morph.content} in and the capsule already ${morph.capsule}")
            }
        }
        progress = 1f
        assertTrue(morph.content == 1f && morph.capsule == 0f, "open, the panel should be in and the capsule gone")
    }

    /** Under reduced motion the field fades in where it is: nothing travels. */
    @Test
    fun underReducedMotionItFadesInPlace() = runComposeUiTest {
        val drawn = mutableListOf<Rect>()
        val state = bar(NavExpandPlacement.AboveKeyboard, reduceMotion = true) { drawn += it.capsule }
        mainClock.autoAdvance = false
        state.expand()
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertTrue(onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty(), "the field should be there at once")
        frames { drawn }
        assertTrue(drawn.isEmpty(), "under reduced motion a capsule travelled: $drawn")
        mainClock.autoAdvance = true
    }

    /** Runs frames until the capsule has not been drawn for a while, and hands back what it drew. */
    private fun <T> ComposeUiTest.frames(drawn: () -> List<T>): List<T> {
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
        icon: Boolean = false,
        probe: (NavMorphDrawn) -> Unit,
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
                                slot(weight = 1f) {
                                    NavSearch(
                                        state = state,
                                        placement = placement,
                                        searchIcon = if (icon) SystemIcons.Check else null,
                                    )
                                }
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
