package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

private const val Title = "Appearance"
private const val Description = "How the app looks on this device"
private const val First = "Theme"
private const val Last = "Text size"
private const val Footer = "Always dark keeps the screen dark even when the system is light."

/**
 * A section's footer comes **after** its rows, and its description before them.
 *
 * `list-section.md` says exactly that — "`footer` is announced after the rows,
 * in reading order" — and nothing held it to it. The slot has been there since
 * it was added and was never demoed, sampled or tested, which is how a claim on
 * a page ends up resting on somebody having read the code once.
 *
 * ### Order on screen, because that is what a reader gets
 *
 * Compose's default traversal order for a `Column` is its layout order, so the
 * top-to-bottom position *is* the reading order here — no `traversalIndex`
 * anywhere in `ListSection`, which is the point. Asserting on the bounds says
 * the same thing as asserting on the semantics tree and says it in the terms the
 * page uses.
 *
 * The description is checked in the same breath because the pair is the claim:
 * two slots of muted small text, one above the rows and one below, and a footer
 * that drifted above them would look like a second description rather than like
 * a bug.
 */
@OptIn(ExperimentalTestApi::class)
class ListSectionFooterTest {

    @Test
    fun theFooterIsBelowTheRowsAndTheDescriptionAboveThem() = runComposeUiTest {
        setContent {
            KontourTheme {
                Box(Modifier.fillMaxSize()) {
                    ListSection(
                        modifier = Modifier.width(320.dp),
                        title = { +Title },
                        supporting = { +Description },
                        footer = { +Footer },
                    ) {
                        ListItem(position = GroupPosition.First) { +First }
                        ListItem(position = GroupPosition.Last) { +Last }
                    }
                }
            }
        }

        val description = onNodeWithText(Description).getUnclippedBoundsInRoot()
        val first = onNodeWithText(First).getUnclippedBoundsInRoot()
        val last = onNodeWithText(Last).getUnclippedBoundsInRoot()
        val footer = onNodeWithText(Footer).getUnclippedBoundsInRoot()

        assertTrue(
            description.bottom <= first.top,
            "the description sits at ${description.top}..${description.bottom} and " +
                "the first row starts at ${first.top}. `description` is what the " +
                "group *is* and belongs above the rows it describes.",
        )
        assertTrue(
            footer.top >= last.bottom,
            "the footer sits at ${footer.top}..${footer.bottom} and the last row " +
                "ends at ${last.bottom}. `footer` is what the setting *does* — " +
                "the sentence a settings screen puts below a switch to explain " +
                "the consequence of it — and above the rows it reads as a second " +
                "description of the group instead.",
        )
    }

    /**
     * And it is genuinely optional: nothing is drawn, and nothing takes room.
     *
     * A null slot that still laid out an empty `Text` would cost a line's height
     * for nothing, which is the defect `SectionHeader` already had and fixed for
     * its title.
     */
    @Test
    fun aSectionWithNoFooterIsShorterThanOneWithOne() = runComposeUiTest {
        setContent {
            KontourTheme {
                Box(Modifier.fillMaxSize()) {
                    ListSection(modifier = Modifier.width(320.dp), title = { +Title }) {
                        ListItem(position = GroupPosition.Only) { +First }
                    }
                }
            }
        }

        val title = onNodeWithText(Title).getUnclippedBoundsInRoot()
        val row = onNodeWithText(First).getUnclippedBoundsInRoot()
        assertTrue(
            row.top >= title.bottom,
            "the only row starts at ${row.top} against a title ending at " +
                "${title.bottom}, so something is being drawn between them that " +
                "was not asked for.",
        )
    }
}
