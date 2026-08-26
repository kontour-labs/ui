package io.kontour.ui.components.selection

import androidx.compose.ui.Modifier
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
                Rating(value = 4.3f, contentDescription = "Average rating")
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
}
