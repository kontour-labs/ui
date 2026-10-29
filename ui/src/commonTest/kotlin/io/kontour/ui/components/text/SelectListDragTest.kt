package io.kontour.ui.components.text

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.down
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A drag that starts on a select's open list is the list's, and moves nothing
 * outside it; a drag that starts on the field is the page's, as on any field.
 *
 * The page here holds its `OverlayHost` rather than sitting beside it, as a
 * card, a pane or a stage on a documentation page does. That is what put the
 * page in the list's way: the list's scroller offered whatever it did not use
 * — all of it, for a list that fits — to the scrollers it is inside, and its
 * overlay is inside the page. With the host at the root of the window the
 * page is beside the list rather than round it, and nothing could reach it.
 */
@OptIn(ExperimentalTestApi::class)
class SelectListDragTest {

    private var page: ScrollState? = null
    private lateinit var state: SelectState
    private var picked: String? = null
    private val overscroll = OverscrollRecord()

    @Test
    fun aDragOnAComboboxListThatFitsMovesNothing() = runComposeUiTest {
        show(Short, Kind.Combobox)
        row("Tram").performTouchInput { swipe(center, center + Offset(0f, -300f), 300) }
        waitForIdle()
        assertEquals(0, page!!.value, "a drag on a combobox's list scrolled the page under it")
        assertTrue(state.expanded, "a drag on the list closed it")

        // And a tap after it is still a tap.
        row("Ferry").performTouchInput { click() }
        waitForIdle()
        assertEquals("Ferry", picked, "a tap on a row after a drag on the list did not pick it")
    }

    @Test
    fun aDragOnASelectListThatFitsMovesNothing() = runComposeUiTest {
        show(Short, Kind.Select)
        row("Tram").performTouchInput { swipe(center, center + Offset(0f, 300f), 300) }
        waitForIdle()
        assertEquals(0, page!!.value, "a drag down a select's list scrolled the page under it")
        row("Tram").performTouchInput { swipe(center, center + Offset(0f, -300f), 300) }
        waitForIdle()
        assertEquals(0, page!!.value, "a drag up a select's list scrolled the page under it")
        assertTrue(state.expanded)
    }

    @Test
    fun aDragPastTheEndOfALongListIsTheListsOverscroll() = runComposeUiTest {
        show(Long, Kind.Combobox)
        dragPastTheEnd(Long.last())
    }

    /** The same, for a list short enough to be composed whole, which scrolls in a column. */
    @Test
    fun aDragPastTheEndOfAColumnIsTheListsOverscroll() = runComposeUiTest {
        show(Middling, Kind.Select)
        dragPastTheEnd(Middling.last())
    }

    /**
     * A list pulled past its end by a finger that is then held still, and
     * lifted, lets its stretch go.
     *
     * Reported from an Android phone and an iPhone for 0.5.1: pulled up past
     * the end of a menu's list, the finger stopped above the menu and lifted
     * once it was perfectly still, the list stayed stretched on Android and
     * floating on iOS. The list's drag flung it only at a speed above nothing,
     * and the fling is what tells the overscroll the finger has gone.
     */
    @Test
    fun aStretchHeldStillAndLetGoIsReleased() = runComposeUiTest {
        show(Long, Kind.Select)
        row("Stop 3").performTouchInput {
            down(center)
            // Past the end of the list, and on up out of the menu.
            repeat(40) { moveBy(Offset(0f, -60f)) }
        }
        waitForIdle()
        assertTrue(overscroll.stretch < 0f, "the drag never stretched the list past its end (${overscroll.stretch}px)")
        // Perfectly still, then lifted: a release at no speed.
        onRoot().performTouchInput {
            advanceEventTime(300)
            up()
        }
        waitForIdle()
        assertEquals(0f, overscroll.stretch, "the list stayed stretched after the finger lifted")
        assertTrue(overscroll.flings > 0, "the list's overscroll was never told the finger had lifted")
    }

    /** A flick goes on after the finger lifts: the list keeps its fling. */
    @Test
    fun aFlickOnTheListFlingsIt() = runComposeUiTest {
        show(Long, Kind.Select)
        row("Stop 3").performTouchInput { swipe(center, center + Offset(0f, -120f), 400) }
        waitForIdle()
        val dragged = firstShownStop()
        runOnIdle { state.collapse() }
        waitForIdle()
        runOnIdle { state.expand() }
        waitForIdle()
        row("Stop 3").performTouchInput { swipe(center, center + Offset(0f, -120f), 40) }
        waitForIdle()
        val flicked = firstShownStop()
        assertTrue(flicked > dragged, "a flick went no further than a slow drag of the same length: $flicked, $dragged")
        assertEquals(0, page!!.value, "a flick on the list scrolled the page under it")
    }

    private fun ComposeUiTest.dragPastTheEnd(last: String) {
        row("Stop 3").performTouchInput { swipe(center, center + Offset(0f, -2000f), 400) }
        waitForIdle()
        assertEquals(0, page!!.value, "a drag past the end of the list scrolled the page under it")
        assertTrue(rowShowing(last), "the list did not scroll to its end under a long drag")
        assertTrue(
            overscroll.offered < 0f,
            "the list's overscroll was offered nothing for a drag past its end (${overscroll.offered}px): " +
                "what the list could not use went somewhere else",
        )
    }

    /** The number of the first stop wholly in view. */
    private fun ComposeUiTest.firstShownStop(): Int =
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f && it.boundsInRoot.height >= it.size.height - 1 }
            .minBy { it.boundsInRoot.top }
            .config[SemanticsProperties.Text].joinToString { it.text }
            .removePrefix("Stop ").toInt()

    @Test
    fun theWheelOverAListThatFitsMovesNothing() = runComposeUiTest {
        show(Short, Kind.Select)
        // A mouse arriving makes the rows a pointer's, shorter than a finger's,
        // so the wheel is aimed at where the row is once they have settled.
        row("Tram").performMouseInput { moveTo(center) }
        waitForIdle()
        row("Tram").performMouseInput {
            moveTo(center)
            scroll(6f)
        }
        waitForIdle()
        assertEquals(0, page!!.value, "the wheel over a select's list scrolled the page under it")
        assertTrue(state.expanded, "the wheel over the list closed it")
    }

    /** The control: a drag on the field is the page's, as on any other field. */
    @Test
    fun aDragOnTheComboboxFieldStillScrollsThePage() = runComposeUiTest {
        show(Short, Kind.Combobox)
        onNodeWithTag(Field).performTouchInput { swipe(center, center + Offset(0f, -300f), 300) }
        waitForIdle()
        assertTrue(page!!.value > 0, "a drag on the combobox's field no longer scrolls the page")
    }

    private enum class Kind { Select, Combobox }

    private fun ComposeUiTest.show(options: List<String>, kind: Kind) {
        setContent {
            KontourTheme(reduceMotion = true) {
                val scroll = rememberScrollState()
                page = scroll
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    CompositionLocalProvider(LocalOverscrollFactory provides OverscrollRecording(overscroll)) {
                        OverlayHost(Modifier.fillMaxWidth().height(700.dp)) {
                            Box(Modifier.padding(24.dp)) { Field(options, kind) }
                        }
                    }
                    Box(Modifier.height(3000.dp))
                }
            }
        }
        waitForIdle()
        runOnIdle { state.expand() }
        waitForIdle()
    }

    @Composable
    private fun Field(options: List<String>, kind: Kind) {
        var value by remember { mutableStateOf<String?>(null) }
        state = rememberSelectState()
        val pick = { option: String ->
            value = option
            picked = option
        }
        val modifier = Modifier.width(320.dp).testTag(Field)
        when (kind) {
            Kind.Select -> Select(value, options, pick, label = "Mode", state = state, modifier = modifier)
            Kind.Combobox -> Combobox(value, options, pick, label = "Mode", state = state, modifier = modifier)
        }
    }

    /** An option's row in the open list, not the field's own text. */
    private fun ComposeUiTest.row(label: String) =
        onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    private fun ComposeUiTest.rowShowing(label: String): Boolean =
        onAllNodes(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .fetchSemanticsNodes().any { it.boundsInRoot.height > 0f }

    /**
     * What the list's overscroll was offered, and took: all of it, as a stretch
     * does. And the stretch, held until a fling lets it go, as a platform's is.
     */
    private class OverscrollRecord {
        var offered = 0f
        var stretch = 0f
        var flings = 0
    }

    private class RecordingOverscroll(private val record: OverscrollRecord) : OverscrollEffect {
        override val isInProgress: Boolean get() = record.stretch != 0f
        override val node: DelegatableNode = object : Modifier.Node() {}

        override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
            val used = performScroll(delta)
            record.offered += (delta - used).y
            record.stretch += (delta - used).y
            return delta
        }

        override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
            performFling(velocity)
            record.flings++
            record.stretch = 0f
        }
    }

    private data class OverscrollRecording(private val record: OverscrollRecord) : OverscrollFactory {
        override fun createOverscrollEffect(): OverscrollEffect = RecordingOverscroll(record)
    }

    private companion object {
        const val Field = "field"
        val Short = listOf("Bus", "Train", "Tram", "Ferry")
        val Long = List(40) { "Stop ${it + 1}" }

        /** Too many to fit, too few to be composed lazily. */
        val Middling = List(25) { "Stop ${it + 1}" }
    }
}
