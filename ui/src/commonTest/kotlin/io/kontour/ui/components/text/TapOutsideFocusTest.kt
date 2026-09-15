package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import io.kontour.ui.components.action.Button
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pressing the page puts the keyboard away.
 *
 * Reported from a phone and reproduced here on the first try, so it was never a
 * platform quirk: a focused field kept its caret, its focus ring and its
 * keyboard wherever else you pressed. Compose does not do this for you — a field
 * takes focus and holds it until something asks for it back, and empty page is
 * not something.
 *
 * The rule lives on [OverlayHost] rather than on the field, because a field
 * cannot see a press that lands somewhere else. What it does is narrow: a press
 * that reaches the root **unconsumed** clears focus, and nothing else does.
 * These tests are the four consequences of that sentence.
 *
 * ### Two ways this test failed before it found anything
 *
 * Worth leaving in the file, because both look like the defect. `Focused` is not
 * on the frame the `testTag` lands on, it is on the editable node inside — so
 * asserting on the tag reported an unfocused field that was focused. And the
 * frame's *centre* is not over the input either, because the label sits above
 * it, so clicking the tag clicked the label. Both produce a red test on the
 * first line rather than the one under examination.
 */
@OptIn(ExperimentalTestApi::class)
class TapOutsideFocusTest {

    @Test
    fun pressingThePageTakesFocusOffAField() {
        runComposeUiTest {
            setContent {
                Harness {
                    Column {
                        Field()
                        Spacer(Modifier.testTag(Page).fillMaxWidth().height(240.dp))
                    }
                }
            }

            onNode(hasSetTextAction()).performClick()
            onNode(hasSetTextAction()).assertIsFocused()

            onNodeWithTag(Page).performClick()
            onNode(hasSetTextAction()).assertIsNotFocused()
        }
    }

    /**
     * A press something else wanted is not a press "outside".
     *
     * The button's own `clickable` consumes the release, which is exactly the
     * signal this rule reads, so the rule costs no special case anywhere and
     * the press is not swallowed on its way.
     *
     * **Only the press is asserted, not the focus afterwards**, and the first
     * draft of this test got that wrong. A desktop click focuses the control it
     * lands on and a touch does not, so "the field keeps its focus" is a claim
     * about the harness rather than about this rule — it failed here for a
     * reason that had nothing to do with the code under test. What belongs to
     * this rule is that it stays out of the way.
     */
    @Test
    fun aButtonStillRunsWhileThisRuleIsWatching() {
        var pressed = 0
        runComposeUiTest {
            setContent {
                Harness {
                    Column {
                        Field()
                        Button(onClick = { pressed++ }, modifier = Modifier.testTag(Page)) {
                            +"Save"
                        }
                    }
                }
            }

            onNode(hasSetTextAction()).performClick()
            onNodeWithTag(Page).performClick()

            assertEquals(
                1,
                pressed,
                "the button did not run — the focus rule sits on the root and " +
                    "must observe presses without consuming them",
            )
        }
    }

    /**
     * Scrolling past a focused field leaves it focused.
     *
     * A list that dismissed the keyboard on every flick would be its own report.
     * It falls out of the rule rather than being special-cased:
     * `waitForUpOrCancellation` returns null once a drag claims the pointer, so
     * there is no release to judge.
     */
    @Test
    fun scrollingDoesNotDropTheKeyboard() {
        runComposeUiTest {
            setContent {
                Harness {
                    Column(Modifier.testTag(Page).verticalScroll(rememberScrollState())) {
                        Field()
                        Spacer(Modifier.fillMaxWidth().height(2000.dp))
                    }
                }
            }

            onNode(hasSetTextAction()).performClick()
            onNode(hasSetTextAction()).assertIsFocused()

            onNodeWithTag(Page).performTouchInput { swipeUp() }
            onNode(hasSetTextAction()).assertIsFocused()
        }
    }

    /** A screen that means to hold the keyboard up can say so. */
    @Test
    fun theRuleCanBeTurnedOff() {
        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    OverlayHost(clearFocusOnTap = false) {
                        Column {
                            Field()
                            Spacer(Modifier.testTag(Page).fillMaxWidth().height(240.dp))
                        }
                    }
                }
            }

            onNode(hasSetTextAction()).performClick()
            onNodeWithTag(Page).performClick()
            onNode(hasSetTextAction()).assertIsFocused()
        }
    }

    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        KontourTheme(reduceMotion = true) {
            OverlayHost { content() }
        }
    }

    @Composable
    private fun Field() {
        TextField(state = rememberTextFieldState(), label = "Name")
    }

    private companion object {
        const val Page = "page"
    }
}
