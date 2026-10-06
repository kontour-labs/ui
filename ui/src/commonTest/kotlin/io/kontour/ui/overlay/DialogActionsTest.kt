package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.text.TextField
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A dialog's buttons, from `actions`: laid out as an alert dialog's are, and kept
 * on screen while the content scrolls.
 *
 * Reported from an app: *"Form dialogs have no standard Cancel/Save button row,
 * so I built a small `DialogButtons`."* And one written into the content scrolls
 * with it — a form taller than a landscape phone had its Save past the fold.
 */
@OptIn(ExperimentalTestApi::class)
class DialogActionsTest {

    private fun ComposeUiTest.bounds(text: String): Rect =
        onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    /**
     * Where a node's foot is, unclipped. `boundsInRoot` stops at the scroller's
     * edge, so a button scrolled out of the window reads as inside it.
     */
    private fun ComposeUiTest.foot(text: String): Float =
        onNodeWithText(text).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }

    @Test
    fun aTallFormKeepsItsSaveOnScreen() = runComposeUiTest {
        var saved = 0
        var windowHeight = 0
        setContent {
            KontourTheme(reduceMotion = true) {
                Box(Modifier.size(360.dp, 400.dp).onSizeChanged { windowHeight = it.height }) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Dialog(
                            visible = true,
                            onDismissRequest = {},
                            title = { +"Edit stop" },
                            actions = {
                                cancel()
                                confirm("Save") { saved++ }
                            },
                        ) {
                            repeat(20) { Text("Field $it", Modifier.fillMaxWidth().height(48.dp)) }
                        }
                    }
                }
            }
        }
        waitForIdle()
        val save = foot("Save")
        assertTrue(
            save <= windowHeight,
            "twenty fields in a 400dp window: Save should be on screen, and its foot is at $save of $windowHeight",
        )
        assertTrue(foot("Field 19") > windowHeight, "the form itself should be taller than the window, or this proves nothing")
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals(1, saved)
    }

    @Test
    fun anAlertsAnswersStayOnScreenUnderALongMessage() = runComposeUiTest {
        var windowHeight = 0
        setContent {
            KontourTheme(reduceMotion = true) {
                Box(Modifier.size(360.dp, 320.dp).onSizeChanged { windowHeight = it.height }) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        AlertDialog(
                            visible = true,
                            onDismissRequest = {},
                            confirmLabel = "Delete",
                            onConfirm = {},
                        ) {
                            +"Delete this journey?"
                            message { +List(40) { "Every stop and every saved time goes with it." }.joinToString(" ") }
                        }
                    }
                }
            }
        }
        waitForIdle()
        assertTrue(foot("Delete") <= windowHeight, "the confirm should stay on screen under a long message: its foot is at ${foot("Delete")} of $windowHeight")
        assertTrue(foot("Cancel") <= windowHeight, "and so should the cancel")
    }

    @Test
    fun cancelAsksToDismiss() = runComposeUiTest {
        var dismissed = 0
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Dialog(visible = true, onDismissRequest = { dismissed++ }, actions = { cancel() }) { Text("Body") }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun aConfirmNotYetEnabledCannotBePressed() = runComposeUiTest {
        var saved = 0
        var ready by mutableStateOf(false)
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Dialog(
                        visible = true,
                        onDismissRequest = {},
                        actions = { confirm("Save", enabled = ready) { saved++ } },
                    ) { Text("Body") }
                }
            }
        }
        waitForIdle()
        val node = onNodeWithText("Save", useUnmergedTree = false).fetchSemanticsNode()
        assertTrue(SemanticsProperties.Disabled in node.config, "a confirm not yet enabled should say so")
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals(0, saved)

        ready = true
        waitForIdle()
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals(1, saved, "enabled while the dialog is up, it should take the press")
    }

    /**
     * The type-to-confirm recipe on the alert dialog's page: a `Dialog` with a
     * field and a confirm that waits for it.
     *
     * Reported from an app: *"`AlertDialog` can't hold any extra input … That
     * rules out 'type the account's email to delete it' and 'reason for
     * disabling'."* An alert stays a title, a message and its answers; the
     * question that needs more is this.
     */
    @Test
    fun aTypeToConfirmWaitsForTheMatch() = runComposeUiTest {
        var deleted = 0
        val typed = TextFieldState()
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Dialog(
                        visible = true,
                        onDismissRequest = {},
                        title = { +"Delete this account?" },
                        actions = {
                            cancel()
                            confirm("Delete account", destructive = true, enabled = typed.text.trim() == "sam@example.com") {
                                deleted++
                            }
                        },
                    ) {
                        TextField(typed, label = "Email")
                    }
                }
            }
        }
        waitForIdle()
        typed.setTextAndPlaceCursorAtEnd("sam@example")
        waitForIdle()
        onNodeWithText("Delete account").performClick()
        waitForIdle()
        assertEquals(0, deleted, "half an address should not delete anything")

        typed.setTextAndPlaceCursorAtEnd("sam@example.com ")
        waitForIdle()
        onNodeWithText("Delete account").performClick()
        waitForIdle()
        assertEquals(1, deleted, "the whole address, trimmed, should")
    }

    /** Placed by what they are: written confirm first, the cancel is still on the leading side. */
    @Test
    fun theRowPlacesByRoleWhateverTheOrder() {
        for (direction in LayoutDirection.entries) runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            Dialog(
                                visible = true,
                                onDismissRequest = {},
                                actions = {
                                    confirm("Save") {}
                                    cancel()
                                },
                            ) { Text("Body") }
                        }
                    }
                }
            }
            waitForIdle()
            val save = bounds("Save")
            val cancel = bounds("Cancel")
            assertEquals(save.top, cancel.top, "$direction: two answers share a row")
            if (direction == LayoutDirection.Ltr) {
                assertTrue(cancel.right < save.left, "left to right, the cancel is to the left of the confirm")
            } else {
                assertTrue(save.right < cancel.left, "right to left, the cancel is to the right of the confirm")
            }
        }
    }

    @Test
    fun threeAnswersPutTheCancelUnderneath() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Dialog(
                        visible = true,
                        onDismissRequest = {},
                        actions = {
                            cancel()
                            neutral("Don't save") {}
                            confirm("Save") {}
                        },
                    ) { Text("Body") }
                }
            }
        }
        waitForIdle()
        val neutral = bounds("Don't save")
        val save = bounds("Save")
        val cancel = bounds("Cancel")
        assertEquals(neutral.top, save.top, "the two answers share the top row")
        assertTrue(cancel.top > save.bottom, "and the cancel takes a line of its own beneath them")
    }

    /** At twice the type size "Don't save" wraps in its half; "Save" stands as tall beside it. */
    @Test
    fun theAnswersInARowAreOneHeight() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    Box(Modifier.width(400.dp).height(800.dp)) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            Dialog(
                                visible = true,
                                onDismissRequest = {},
                                actions = {
                                    neutral("Don't save") {}
                                    confirm("Save") {}
                                    cancel()
                                },
                            ) { Text("Body") }
                        }
                    }
                }
            }
        }
        waitForIdle()
        val neutral = bounds("Don't save")
        val save = bounds("Save")
        assertTrue(neutral.height > bounds("Cancel").height, "the neutral's label should wrap at this size, or this proves nothing: $neutral")
        assertEquals(neutral.top, save.top)
        assertEquals(neutral.height, save.height, "a short button beside a tall one")
    }

    @Test
    fun aLabelCanBeReadFromTheTheme() = runComposeUiTest {
        var expected = ""
        setContent {
            KontourTheme(reduceMotion = true) {
                expected = Theme.strings.confirm
                OverlayHost(Modifier.fillMaxSize()) {
                    // The point is that this compiles: the builder is composable,
                    // so a label from the theme or a string resource is written
                    // where it is used.
                    Dialog(visible = true, onDismissRequest = {}, actions = { confirm(Theme.strings.confirm) {} }) { Text("Body") }
                }
            }
        }
        waitForIdle()
        onNodeWithText(expected).fetchSemanticsNode()
    }

    /**
     * A destructive confirm warns as the dialog arrives, as a destructive alert
     * does — once, and an alert still only once.
     */
    @Test
    fun aDestructiveConfirmWarnsOnce() {
        for (alert in listOf(false, true)) runComposeUiTest {
            val felt = mutableListOf<FeedbackIntent>()
            var visible by mutableStateOf(true)
            setContent {
                KontourTheme(reduceMotion = true) {
                    CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            if (alert) {
                                AlertDialog(
                                    visible = visible,
                                    onDismissRequest = { visible = false },
                                    confirmLabel = "Delete",
                                    onConfirm = {},
                                    destructive = true,
                                ) { +"Delete this account?" }
                            } else {
                                Dialog(
                                    visible = visible,
                                    onDismissRequest = { visible = false },
                                    actions = {
                                        cancel()
                                        confirm("Delete account", destructive = true) {}
                                    },
                                ) { Text("Type the address to delete it") }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val what = if (alert) "an alert" else "a dialog"
            assertEquals(1, felt.count { it == FeedbackIntent.Warn }, "$what arriving with a destructive answer: $felt")
            mainClock.advanceTimeBy(300)
            waitForIdle()
            assertEquals(1, felt.count { it == FeedbackIntent.Warn }, "$what staying up warns no more: $felt")

            visible = false
            waitForIdle()
            visible = true
            waitForIdle()
            assertEquals(2, felt.count { it == FeedbackIntent.Warn }, "$what asked again warns again: $felt")
        }
    }

    /** `hapticWarning = false` keeps a destructive confirm quiet, as on `AlertDialog`. */
    @Test
    fun aConfirmAskedNotToWarnIsSilent() = runComposeUiTest {
        val felt = mutableListOf<FeedbackIntent>()
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Dialog(
                            visible = true,
                            onDismissRequest = {},
                            actions = {
                                cancel()
                                confirm("Delete", destructive = true, hapticWarning = false) {}
                            },
                        ) { Text("Body") }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(emptyList(), felt.filter { it == FeedbackIntent.Warn })
    }

    /** A button of the caller's own, written in the row, is drawn and takes a press. */
    @Test
    fun aButtonOfYourOwnIsPlacedToo() = runComposeUiTest {
        var helped = 0
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Dialog(
                        visible = true,
                        onDismissRequest = {},
                        actions = {
                            Button(onClick = { helped++ }) { +"Help" }
                            cancel()
                            confirm("Save") {}
                        },
                    ) { Text("Body") }
                }
            }
        }
        waitForIdle()
        val help = bounds("Help")
        val save = bounds("Save")
        assertTrue(help.width > 0f && help.height > 0f, "it should be drawn: $help")
        assertEquals(save.top, help.top, "in the top row with the answers")
        assertTrue(help.right <= bounds("Cancel").left, "and leading it, so the confirm keeps the trailing edge")
        onNodeWithText("Help").performClick()
        waitForIdle()
        assertEquals(1, helped)
    }

    @Test
    fun aConfirmBeingActedOnTakesNoSecondPress() = runComposeUiTest {
        var saved = 0
        var loading = ""
        setContent {
            KontourTheme(reduceMotion = true) {
                loading = Theme.strings.loading
                OverlayHost(Modifier.fillMaxSize()) {
                    Dialog(
                        visible = true,
                        onDismissRequest = {},
                        actions = { confirm("Save", loading = true, onClick = { saved++ }) },
                    ) { Text("Body") }
                }
            }
        }
        waitForIdle()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, loading)).performClick()
        waitForIdle()
        assertEquals(0, saved, "a confirm already being acted on should not take another press")
    }

    /** The shares are a weighted `Row`'s, to the pixel, at every width a dialog takes. */
    @Test
    fun theSharesAreARowsShares() = runComposeUiTest {
        var width by mutableStateOf(200.dp)
        var count by mutableStateOf(2)
        setContent {
            KontourTheme(reduceMotion = true) {
                Row(Modifier.width(width).testTag("row"), horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                    repeat(count) { Box(Modifier.weight(1f).height(10.dp).testTag("cell$it")) }
                }
            }
        }
        for (n in 2..3) for (w in 200..420 step 7) {
            count = n
            width = w.dp
            waitForIdle()
            val row = onNodeWithTag("row").fetchSemanticsNode().size.width
            val gap = (onNodeWithTag("cell1").fetchSemanticsNode().positionInRoot.x -
                onNodeWithTag("cell0").fetchSemanticsNode().let { it.positionInRoot.x + it.size.width }).toInt()
            val cells = IntArray(n) { onNodeWithTag("cell$it").fetchSemanticsNode().size.width }
            assertContentEquals(cells, equalShares(row - gap * (n - 1), n), "$n buttons in ${w}dp")
        }
    }

    @Test
    fun sharesCarryTheirRemainderFromTheFirst() {
        assertContentEquals(intArrayOf(50, 51), equalShares(101, 2))
        assertContentEquals(intArrayOf(34, 33, 33), equalShares(100, 3))
        assertContentEquals(intArrayOf(32, 33, 33), equalShares(98, 3))
        assertContentEquals(IntArray(0), equalShares(100, 0))
    }
}
