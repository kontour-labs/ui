package io.kontour.ui.components.selection

import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.kontourSizing
import kotlin.test.assertTrue
import io.kontour.ui.theme.Theme
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.InputModality
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A rating is two components sharing a name, and the read-only one is the one
 * that gets built wrong.
 *
 * Most ratings on any screen are averages — nothing to press. Shipping those as
 * five silent radio buttons gives a screen-reader user five things to activate
 * that do nothing, which is the defect `EverythingRespondsTest` exists to catch
 * and would catch here.
 */
@OptIn(ExperimentalTestApi::class)
class RatingTest {

    /**
     * Read-only means no click action anywhere in the subtree.
     *
     * Not "the callback is null so nothing happens" — the node must not offer
     * the action at all. Reverting the `onValueChange == null` branch to always
     * build the selectable row fails this with five click actions.
     */
    @Test
    fun aReadOnlyRatingOffersNothingToPress() = runComposeUiTest {
        setContent {
            KontourTheme {
                Rating(value = 4.3f, onValueChange = null, contentDescription = "Average rating")
            }
        }

        onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    /** And it says the score, as one node. */
    @Test
    fun aReadOnlyRatingAnnouncesItsScore() = runComposeUiTest {
        setContent {
            KontourTheme {
                Rating(
                    value = 4.3f,
                    onValueChange = null,
                    contentDescription = "Average rating",
                    modifier = Modifier.testTag("rating"),
                )
            }
        }

        onNodeWithTag("rating").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "4.3 out of 5"),
        )
    }

    /**
     * Tapping a mark scores what it says it scores.
     *
     * Ordinary behaviour rather than a guard: an off-by-one shifts the
     * announcement and the emitted value *together*, so this passes either way.
     * [tappingTheLastMarkScoresTheMaximum] is the one that catches it — verified
     * by reverting, which is how the overclaim in this comment was found.
     */
    @Test
    fun tappingTheThirdMarkScoresThree() = runComposeUiTest {
        var score = 0f
        setContent {
            KontourTheme {
                Rating(
                    value = score,
                    contentDescription = "Your rating",
                    onValueChange = { score = it },
                )
            }
        }

        onNodeWithContentDescription("3 out of 5").performClick()
        assertEquals(3f, score)
    }

    /**
     * The last mark scores the maximum.
     *
     * This is the off-by-one guard. The marks are built from a zero-based index
     * and the score is one-based; shift it and the top of the scale disappears
     * — the highest mark announces "4 out of 5" and there is no way to score
     * five at all. Reverting `index + 1` fails here and nowhere else.
     */
    @Test
    fun tappingTheLastMarkScoresTheMaximum() = runComposeUiTest {
        var score = 0f
        setContent {
            KontourTheme {
                Rating(
                    value = score,
                    contentDescription = "Your rating",
                    onValueChange = { score = it },
                )
            }
        }

        onNodeWithContentDescription("5 out of 5").performClick()
        assertEquals(5f, score)
    }

    /**
     * A score can be taken back: a tap on the score already given clears it —
     * asked for from a phone, where a rating had no way back to nothing.
     */
    @Test
    fun tappingTheCurrentScoreClearsIt() = runComposeUiTest {
        var score by mutableStateOf(3f)
        setContent {
            KontourTheme(reduceMotion = true) {
                Rating(value = score, contentDescription = "Your rating", onValueChange = { score = it })
            }
        }
        onNodeWithContentDescription("3 out of 5").performClick()
        waitForIdle()
        assertEquals(0f, score)
    }

    /** Not decided on the press: a drag that starts on the score is a drag. */
    @Test
    fun aDragThatStartsOnTheScoreDoesNotClearIt() = runComposeUiTest {
        var score by mutableStateOf(3f)
        setContent {
            KontourTheme(reduceMotion = true) {
                Rating(value = score, contentDescription = "Your rating", onValueChange = { score = it })
            }
        }
        val third = onNodeWithContentDescription("3 out of 5").fetchSemanticsNode().boundsInRoot
        val fourth = onNodeWithContentDescription("4 out of 5").fetchSemanticsNode().boundsInRoot
        onRoot().performTouchInput {
            down(third.center)
            moveTo(Offset((third.center.x + fourth.center.x) / 2f, third.center.y))
            moveTo(fourth.center)
            up()
        }
        waitForIdle()
        assertEquals(4f, score)
    }

    /**
     * One mark is the score, as one radio button is the choice: at three, the
     * third star says it is selected and the first two do not. They all did,
     * so a screen reader announced the first star as selected and activating
     * it changed the score to one.
     */
    @Test
    fun onlyTheScoredMarkIsSelected() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                Rating(value = 3f, contentDescription = "Your rating", onValueChange = {})
            }
        }
        val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        onNodeWithContentDescription("3 out of 5").assert(selected)
        for (other in listOf(1, 2, 4, 5)) {
            onNodeWithContentDescription("$other out of 5").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        }
    }

    @Test
    fun activatingTheSelectedMarkFromTheKeyboardClearsIt() = runComposeUiTest {
        var score by mutableStateOf(3f)
        setContent {
            KontourTheme(reduceMotion = true) {
                Rating(value = score, contentDescription = "Your rating", onValueChange = { score = it })
            }
        }
        onNodeWithContentDescription("3 out of 5").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(0f, score)
    }

    @Test
    fun withoutAllowZeroTheScoreStays() = runComposeUiTest {
        var score by mutableStateOf(3f)
        setContent {
            KontourTheme(reduceMotion = true) {
                Rating(value = score, contentDescription = "Your rating", onValueChange = { score = it }, allowZero = false)
            }
        }
        onNodeWithContentDescription("3 out of 5").performClick()
        onNodeWithContentDescription("3 out of 5").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(3f, score)
    }

    /**
     * An interactive rating has one target per mark.
     *
     * Five, not one and not six. A single target for the whole row would mean
     * the score depends on where inside it you tapped, which is a slider
     * pretending to be a rating.
     */
    @Test
    fun anInteractiveRatingHasOneTargetPerMark() = runComposeUiTest {
        setContent {
            KontourTheme {
                Rating(
                    value = 2f,
                    contentDescription = "Your rating",
                    onValueChange = {},
                    count = 5,
                )
            }
        }

        onAllNodes(hasClickAction()).assertCountEquals(5)
    }

    /**
     * On a touch screen the stars sit [RatingDefaults.Gap] apart, and the row
     * is still a whole target tall.
     *
     * *"it's always bugged me how much gap there is between the stars by
     * default"*: each 24dp star reserved a 48dp target of its own, so on a
     * phone there was 28dp of air between one and the next. The row takes every
     * press itself, so it owns the target now and the stars sit together.
     */
    @Test
    fun onATouchScreenTheStarsSitAGapApart() = runComposeUiTest {
        var star = 0f
        var gap = 0f
        var target = 0f
        setContent {
            // A phone's target: the JVM's own is 24dp, which a 24dp star clears.
            KontourTheme(sizing = kontourSizing(ContrastLevel.Standard).copy(minTouchTarget = 48.dp)) {
                CompositionLocalProvider(LocalInputModality provides InputModality.Touch) {
                    with(LocalDensity.current) {
                        star = Theme.sizing.iconLarge.toPx()
                        gap = RatingDefaults.Gap.toPx()
                        target = Theme.sizing.minTouchTarget.toPx()
                    }
                    Rating(
                        value = 2f,
                        contentDescription = "Your rating",
                        onValueChange = {},
                        modifier = Modifier.testTag("rating"),
                    )
                }
            }
        }
        val marks = onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
        val pitch = marks[1].center.x - marks[0].center.x
        assertTrue(pitch <= star + gap + 1f, "the stars are ${pitch - star}px apart, against a ${gap}px gap")
        val row = onNodeWithTag("rating").fetchSemanticsNode().boundsInRoot
        assertTrue(row.height >= target - 0.5f, "the row is ${row.height}px tall, less than a ${target}px target")
    }

    /**
     * Right to left, a tap on a mark scores that mark.
     *
     * The pointer's mark was mirrored twice: the marks' positions are measured
     * where the row placed them, so the nearest one is already the right
     * index, and mirroring it again swapped the ends. And "off the start of the
     * row" was measured against the left-hand star's edge, so a press on any
     * other star cleared the score. The right-most star — the first in Arabic —
     * scored nothing, and the left-most scored one.
     */
    @Test
    fun rightToLeftATapScoresTheMarkTapped() = runComposeUiTest {
        var score by mutableStateOf(0f)
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Rating(value = score, contentDescription = "Your rating", onValueChange = { score = it })
                }
            }
        }
        onNodeWithContentDescription("1 out of 5").performClick()
        waitForIdle()
        assertEquals(1f, score, "the first star, at the right-hand end")
        onNodeWithContentDescription("5 out of 5").performClick()
        waitForIdle()
        assertEquals(5f, score, "the last star, at the left-hand end")
        onNodeWithContentDescription("3 out of 5").performClick()
        waitForIdle()
        assertEquals(3f, score, "the middle star")
    }

    /** And a drag follows the finger, and the half is the side the star fills from. */
    @Test
    fun rightToLeftADragAndAHalfLandWhereTheFingerIs() = runComposeUiTest {
        var score by mutableStateOf(1f)
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Rating(
                        value = score,
                        contentDescription = "Your rating",
                        onValueChange = { score = it },
                        allowHalf = true,
                    )
                }
            }
        }
        val second = onNodeWithContentDescription("2 out of 5").fetchSemanticsNode().boundsInRoot
        val fourth = onNodeWithContentDescription("4 out of 5").fetchSemanticsNode().boundsInRoot
        // Leftwards in RTL is up the scale. To the far (left) side of the
        // fourth star, which is its end in reading order: a whole four.
        onRoot().performTouchInput {
            down(second.center)
            moveTo(Offset((second.center.x + fourth.center.x) / 2f, second.center.y))
            moveTo(Offset(fourth.left + fourth.width * 0.2f, fourth.center.y))
            up()
        }
        waitForIdle()
        assertEquals(4f, score, "a drag from the second star to the left side of the fourth")

        // The right side of the third star is its start in Arabic, where a half
        // star is lit: three and a half would be the other side.
        val third = onNodeWithContentDescription("3 out of 5").fetchSemanticsNode().boundsInRoot
        onRoot().performTouchInput { click(Offset(third.right - third.width * 0.2f, third.center.y)) }
        waitForIdle()
        assertEquals(2.5f, score, "a tap on the start half of the third star")
    }
}
