package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackFloor
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.LocalFeedbackFloor
import io.kontour.ui.locale.localeFormatsFor
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * An A–Z rail beside a list of trees, with no D, Q or X among them and a long
 * run of numbered ones under `#`.
 *
 * Asked for as an A–Z index scroller. What is held: a press goes to its
 * letter's section, or the next with something in it; a drag ticks once a
 * letter and shows the letter under the finger beside it; a short rail folds
 * into dots and still reaches every letter; the keyboard and a screen reader
 * move it a section at a time; disabled, it does nothing.
 */
@OptIn(ExperimentalTestApi::class)
class IndexScrollerTest {

    private val names = listOf(
        "Alder", "Ash", "Birch", "Cedar", "Elm", "Fir", "Gum", "Hazel", "Ironbark", "Jarrah", "Karri", "Larch",
        "Maple", "Nutmeg", "Oak", "Pine", "Rowan", "Spruce", "Teak", "Upas", "Vine", "Willow", "Yew", "Zelkova",
    ) + List(20) { "${it + 1} Gum" }

    private val indexed = names.indexedBy(IndexAlphabet.Latin, localeFormatsFor(Locale("en"))) { it }
    private val felt = mutableListOf<FeedbackIntent>()
    private lateinit var list: LazyListState

    private val labels = IndexAlphabet.Latin.labels + "#"

    private fun section(label: String) = indexed.sections.first { it.label == label }

    private fun ComposeUiTest.show(
        height: Dp = 640.dp,
        rtl: Boolean = false,
        enabled: Boolean = true,
    ) {
        // A clock that moves a second on every tick, so the rate floor never
        // drops one and the count is the crossings.
        val clock = TestTimeSource()
        val floor = FeedbackFloor(clock)
        setContent {
            list = rememberLazyListState()
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            KontourTheme {
                CompositionLocalProvider(
                    LocalFeedback provides FeedbackDispatcher { felt += it; clock += 1.seconds },
                    LocalFeedbackFloor provides floor,
                ) {
                    OverlayHost {
                        Box(Modifier.size(360.dp, height).testTag("frame")) {
                            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                                indexed.groups.forEach { group ->
                                    item { Text("Under ${group.label}", Modifier.height(40.dp)) }
                                    items(group.items) { Text(it, Modifier.height(40.dp)) }
                                }
                            }
                            IndexScroller(
                                state = list,
                                sections = indexed.sections,
                                modifier = Modifier.align(Alignment.CenterEnd).testTag("index"),
                                enabled = enabled,
                                alphabet = IndexAlphabet.Latin,
                            )
                        }
                    }
                }
            }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.rail(): Rect = onNodeWithTag("index").fetchSemanticsNode().boundsInRoot

    /** Where label [i] is on a rail that shows every label: its middle, within a padding's error. */
    private fun ComposeUiTest.labelY(i: Int): Float = rail().height * (i + 0.5f) / labels.size

    private fun ComposeUiTest.tap(label: String) {
        val y = labelY(labels.indexOf(label))
        onNodeWithTag("index").performTouchInput {
            down(Offset(centerX, y))
            up()
        }
        waitForIdle()
    }

    @Test
    fun aPressOnALetterJumpsToItsSection() = runComposeUiTest {
        show()
        tap("M")
        assertEquals(section("M").itemIndex, list.firstVisibleItemIndex)
        tap("C")
        assertEquals(section("C").itemIndex, list.firstVisibleItemIndex)
    }

    @Test
    fun aLetterWithNothingUnderItGoesToTheNextThatHasSomething() = runComposeUiTest {
        show()
        tap("D")
        assertEquals(section("E").itemIndex, list.firstVisibleItemIndex)
        tap("Q")
        assertEquals(section("R").itemIndex, list.firstVisibleItemIndex)
    }

    @Test
    fun aDragTicksOnceALetterAndShowsTheLetterBesideTheFinger() = runComposeUiTest {
        show()
        val from = labelY(0)
        val to = labelY(labels.indexOf("M"))
        onNodeWithTag("index").performTouchInput {
            down(Offset(centerX, from))
            for (step in 1..120) moveTo(Offset(centerX, from + (to - from) * step / 120f))
        }
        waitForIdle()
        assertEquals(12, felt.count { it == FeedbackIntent.Tick }, "A to M is twelve letters crossed: $felt")
        assertEquals(section("M").itemIndex, list.firstVisibleItemIndex)
        onNodeWithText("M").assertExistsCompat("no bubble saying M beside the finger")

        onNodeWithTag("index").performTouchInput { up() }
        waitForIdle()
        onNodeWithText("M").assertDoesNotExist()
        assertEquals(12, felt.count { it == FeedbackIntent.Tick }, "lifting the finger ticked")
    }

    @Test
    fun aShortRailFoldsIntoDotsAndStillReachesEveryLetter() = runComposeUiTest {
        show(height = 200.dp)
        val rail = rail()
        assertTrue(rail.height <= 200f * density.density + 0.5f, "the rail is ${rail.height}px in a 200dp box")
        onNodeWithTag("index").performTouchInput {
            down(Offset(centerX, 1f))
            for (step in 1..300) moveTo(Offset(centerX, 1f + (height - 2f) * step / 300f))
        }
        waitForIdle()
        assertEquals(26, felt.count { it == FeedbackIntent.Tick }, "a run down a folded rail did not pass every letter")
        onNodeWithText("#").assertExistsCompat("at the foot of the rail the bubble does not say #")
        onNodeWithTag("index").performTouchInput { up() }
    }

    @Test
    fun theKeyboardMovesASectionAtATime() = runComposeUiTest {
        show()
        onNodeWithTag("index").requestFocus()
        onNodeWithTag("index").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(section("B").itemIndex, list.firstVisibleItemIndex)
        onNodeWithTag("index").performKeyInput { pressKey(Key.MoveEnd) }
        waitForIdle()
        assertEquals(section("#").itemIndex, list.firstVisibleItemIndex)
        onNodeWithTag("index").performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        assertEquals(section("Z").itemIndex, list.firstVisibleItemIndex)
        onNodeWithTag("index").performKeyInput { pressKey(Key.MoveHome) }
        waitForIdle()
        assertEquals(0, list.firstVisibleItemIndex)
    }

    @Test
    fun aScreenReaderHearsTheSectionAndMovesBySections() = runComposeUiTest {
        show()
        val node = { onNodeWithTag("index").fetchSemanticsNode().config }
        assertEquals(listOf("Section index"), node()[SemanticsProperties.ContentDescription])
        assertEquals("A", node()[SemanticsProperties.StateDescription])
        val actions = node()[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Next section"), actions, "at the first section")

        onNodeWithTag("index").performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        waitForIdle()
        assertEquals(indexed.sections[5].itemIndex, list.firstVisibleItemIndex)
        assertEquals(indexed.sections[5].label, node()[SemanticsProperties.StateDescription])
        assertEquals(5f, node()[SemanticsProperties.ProgressBarRangeInfo].current)

        node()[SemanticsActions.CustomActions].first { it.label == "Next section" }.action()
        waitForIdle()
        assertEquals(indexed.sections[6].itemIndex, list.firstVisibleItemIndex)

        // `#` is said as a word.
        onNodeWithTag("index").performSemanticsAction(SemanticsActions.SetProgress) { it(indexed.sections.lastIndex.toFloat()) }
        waitForIdle()
        assertEquals("Other", node()[SemanticsProperties.StateDescription])
    }

    @Test
    fun disabledItDoesNothing() = runComposeUiTest {
        show(enabled = false)
        tap("M")
        assertEquals(0, list.firstVisibleItemIndex, "a disabled rail moved the list")
        assertTrue(felt.isEmpty(), "a disabled rail ticked")
        val config = onNodeWithTag("index").fetchSemanticsNode().config
        assertTrue(SemanticsProperties.Disabled in config)
        assertFalse(SemanticsActions.SetProgress in config, "a disabled rail can still be moved by a screen reader")
        assertFalse(SemanticsActions.CustomActions in config)
    }

    @Test
    fun theBubbleIsOnTheListsSideOfTheRailInEitherDirection() {
        for (rtl in listOf(false, true)) runComposeUiTest {
            show(rtl = rtl)
            val rail = rail()
            val frame = onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
            val strip = 20f * density.density
            val y = labelY(labels.indexOf("M"))
            onNodeWithTag("index").performTouchInput { down(Offset(centerX, y)) }
            waitForIdle()
            val bubble = onNodeWithText("M").fetchSemanticsNode().boundsInRoot
            if (rtl) {
                assertEquals(frame.left, rail.left, 0.5f, "right to left, the rail is not at the list's left edge")
                assertTrue(bubble.left >= rail.left + strip - 1f, "right to left, the bubble ($bubble) is over the labels ($rail)")
            } else {
                assertEquals(frame.right, rail.right, 0.5f, "the rail is not at the list's right edge")
                assertTrue(bubble.right <= rail.right - strip + 1f, "the bubble ($bubble) is over the labels ($rail)")
            }
            onNodeWithTag("index").performTouchInput { up() }
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat(message: String) {
        try {
            assertExists()
        } catch (e: AssertionError) {
            throw AssertionError(message, e)
        }
    }
}
