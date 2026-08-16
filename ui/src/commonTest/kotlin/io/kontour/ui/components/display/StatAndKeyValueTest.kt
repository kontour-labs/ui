package io.kontour.ui.components.display

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test

/**
 * Both of these are read, not operated, so the only thing that can break is what
 * they say.
 *
 * A number and its label as two nodes make a screen-reader user assemble the
 * sentence themselves, and the pairing is the entire content — "Platform" then,
 * a beat later, "2". Each merges into one announcement instead, and these are
 * what say so.
 */
@OptIn(ExperimentalTestApi::class)
class StatAndKeyValueTest {

    /**
     * A stat announces label-then-value, in the order a person would say it.
     *
     * Visually the value is on top, because a dashboard is scanned by its
     * numbers. Spoken, that order is backwards: "4 min" before anything has said
     * what is four minutes away. Reverting `spoken()` to source order fails this.
     */
    @Test
    fun aStatAnnouncesItsLabelBeforeItsValue() = runComposeUiTest {
        setContent {
            KontourTheme {
                Stat(Modifier.testTag("stat")) {
                    value("4 min")
                    +"Next departure"
                    supporting("Platform 2")
                }
            }
        }

        onNodeWithTag("stat").assert(
            hasContentDescription("Next departure. 4 min. Platform 2"),
        )
    }

    /**
     * The parts are merged, so a screen reader stops on one node rather than
     * walking a number and then finding out what it was.
     *
     * Asserted through the *merged* tree's children: merging collapses the value
     * and label into the parent, so it has none. Reverting `mergeDescendants`
     * puts both back and this counts them.
     *
     * An earlier version of this asserted on `onAllNodesWithText("4 min")`,
     * which passes either way — the merged parent carries its children's text
     * too. A test that cannot fail is worse than no test.
     */
    @Test
    fun aStatIsOneNodeRatherThanThree() = runComposeUiTest {
        setContent {
            KontourTheme {
                Stat(Modifier.testTag("stat")) {
                    value("4 min")
                    +"Next departure"
                }
            }
        }

        onNodeWithTag("stat").onChildren().assertCountEquals(0)
    }

    /**
     * `announcement` wins over the assembled form.
     *
     * For a value a reader mangles: "1.2k" is said as "one point two kay", and
     * the caller is the only one who knows it means one thousand two hundred.
     */
    @Test
    fun anExplicitAnnouncementReplacesTheAssembledOne() = runComposeUiTest {
        setContent {
            KontourTheme {
                Stat(Modifier.testTag("stat")) {
                    value("1.2k")
                    +"Riders today"
                    announcement("Riders today, one thousand two hundred")
                }
            }
        }

        onNodeWithTag("stat").assert(
            hasContentDescription("Riders today, one thousand two hundred"),
        )
    }

    /** Each row of a key-value list is one node, reading "label, value". */
    @Test
    fun eachKeyValueRowAnnouncesAsAPair() = runComposeUiTest {
        setContent {
            KontourTheme {
                KeyValueList {
                    row("Operator", "Transperth")
                    row("Platform", "2")
                }
            }
        }

        onNodeWithContentDescription("Operator, Transperth").assertExists()
        onNodeWithContentDescription("Platform, 2").assertExists()
    }

    /**
     * A row whose value is not text says nothing unless told to.
     *
     * A tick icon in an "Accessible" row would otherwise announce as "Accessible"
     * and stop, which reads as a row with its value missing. `announcement` is
     * how the caller supplies the half the component cannot see — and leaving it
     * out is a row that falls back to its parts rather than lying.
     */
    @Test
    fun aNonTextValueAnnouncesWhatItWasGiven() = runComposeUiTest {
        setContent {
            KontourTheme {
                KeyValueList {
                    row("Accessible", announcement = "yes") { +"✓" }
                }
            }
        }

        onNodeWithContentDescription("Accessible, yes").assertExists()
    }
}
