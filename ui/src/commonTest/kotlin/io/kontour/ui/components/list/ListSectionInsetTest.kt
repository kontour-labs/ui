package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.ChipGroup
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private const val Title = "Planning"
private const val Footer = "Used to work out how long the walks between stops take."
private const val Paragraph = "Trips are planned at the speed you walk."
private const val First = "Walking speed"
private const val Last = "Home stop"
private const val Section = "section"
private const val Chip = "chip"

/**
 * Where a section's title and footer start: in from the edge over rows, flush
 * over anything else.
 *
 * A section's words used to start 16dp in whatever it held, which is where a
 * row's content starts and nowhere else. Over a paragraph, a field or a row of
 * buttons — which is how a good many sections are actually used — the title
 * hung 16dp in from a column of nothing, and read as a mistake.
 *
 * The section reads the inset from its content, and the content is what these
 * vary. Each test asserts on the left edge of the title's heading node and the
 * footer's text, against the section's own left edge, because that is the
 * whole of the claim: which column the words start in.
 */
@OptIn(ExperimentalTestApi::class)
class ListSectionInsetTest {

    @Test
    fun overRowsTheTitleAndFooterStartWhereTheRowsWordsDo() = runComposeUiTest {
        var inset = 0.dp
        show {
            inset = ListItemDefaults.ContentInset
            ListSection(sectionModifier, title = { +Title }, footer = { +Footer }) {
                ListItem(position = GroupPosition.First) { +First }
                ListItem(position = GroupPosition.Last) { +Last }
            }
        }

        val label = onNodeWithText(First, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertNear(label.left, start(Title), "the title starts at the row's label, as it always has")
        assertNear(section().left + inset, start(Title), "the title is the rows' inset in")
        assertNear(section().left + inset, start(Footer), "the footer is the rows' inset in")
    }

    /** A filter sheet's section, as the catalog's has it: chips under a title. */
    @Test
    fun overChipsTheTitleIsFlushWithThem() = runComposeUiTest {
        show {
            ListSection(sectionModifier, title = { +Title }) {
                ChipGroup {
                    FilterChip(selected = true, onSelectedChange = {}, modifier = Modifier.testTag(Chip)) { +"Trains" }
                }
            }
        }
        val chip = onNodeWithTag(Chip, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertNear(chip.left, start(Title), "the title starts where the chips under it do")
    }

    @Test
    fun overAParagraphTheTitleAndFooterAreFlushWithIt() = runComposeUiTest {
        show {
            ListSection(sectionModifier, title = { +Title }, footer = { +Footer }) {
                Text(Paragraph)
            }
        }

        assertNear(start(Paragraph), start(Title), "the title starts where the paragraph under it does")
        assertNear(start(Paragraph), start(Footer), "the footer starts where the paragraph over it does")
        assertNear(section().left, start(Title), "a paragraph has no inset for the title to share")
    }

    /**
     * The title follows what is first and the footer what is last, separately —
     * a control over a run of rows, as a settings screen's "Appearance" has.
     */
    @Test
    fun eachEndFollowsWhatIsBesideIt() = runComposeUiTest {
        var inset = 0.dp
        show {
            inset = ListItemDefaults.ContentInset
            ListSection(sectionModifier, title = { +Title }, footer = { +Footer }) {
                Text(Paragraph)
                ListItem(position = GroupPosition.Only) { +Last }
            }
        }

        assertNear(section().left, start(Title), "a paragraph is first, so the title is flush")
        assertNear(section().left + inset, start(Footer), "a row is last, so the footer is in")
    }

    /** A row inside a card with padding of its own: the card is what is first. */
    @Test
    fun aRowInsideAPaddedBoxIsNotARow() = runComposeUiTest {
        show {
            ListSection(sectionModifier, title = { +Title }, footer = { +Footer }) {
                Box(Modifier.padding(12.dp)) {
                    ListItem(position = GroupPosition.Only) { +First }
                }
            }
        }

        assertNear(section().left, start(Title), "the box is first, and it is not a row")
        assertNear(section().left, start(Footer), "the box is last, and it is not a row")
    }

    /**
     * An open disclosure row is still a row, all of it: its body is part of the
     * card. Were only the header row read, opening the last row of a section
     * would throw its footer out to the edge.
     */
    @Test
    fun anOpenDisclosureRowIsStillARow() = runComposeUiTest {
        var inset = 0.dp
        show {
            inset = ListItemDefaults.ContentInset
            ListSection(sectionModifier, title = { +Title }, footer = { +Footer }) {
                DisclosureListItem(
                    expanded = true,
                    onExpandedChange = {},
                    header = { +First },
                ) {
                    Text(Paragraph)
                }
            }
        }

        assertNear(section().left + inset, start(Title), "the open row is first")
        assertNear(section().left + inset, start(Footer), "the open row is last, body and all")
    }

    @Test
    fun aGivenInsetIsTakenWhateverTheContent() = runComposeUiTest {
        show {
            ListSection(sectionModifier, title = { +Title }, footer = { +Footer }, textInset = 24.dp) {
                Text(Paragraph)
            }
        }

        assertNear(section().left + 24.dp, start(Title), "textInset places the title")
        assertNear(section().left + 24.dp, start(Footer), "textInset places the footer")
    }

    /** The inset is from the start edge, which right to left is the right. */
    @Test
    fun rightToLeftTheInsetIsFromTheRight() = runComposeUiTest {
        var inset = 0.dp
        show(LayoutDirection.Rtl) {
            inset = ListItemDefaults.ContentInset
            ListSection(sectionModifier, title = { +Title }) {
                ListItem(position = GroupPosition.Only) { +First }
            }
            ListSection(Modifier.width(320.dp).testTag("flush"), title = { +Footer }) {
                Text(Paragraph)
            }
        }

        val title = onNodeWithText(Title).getUnclippedBoundsInRoot()
        assertNear(section().right - inset, title.right, "over a row the title is in from the right")
        val flush = onNodeWithTag("flush").getUnclippedBoundsInRoot()
        val other = onNodeWithText(Footer).getUnclippedBoundsInRoot()
        assertNear(flush.right, other.right, "over a paragraph the title is flush with the right")
    }

    private val sectionModifier = Modifier.width(320.dp).testTag(Section)

    private fun ComposeUiTest.show(
        direction: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit,
    ) {
        setContent {
            KontourTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box(Modifier.fillMaxSize()) {
                        Column { content() }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.section(): DpRect = onNodeWithTag(Section).getUnclippedBoundsInRoot()

    private fun ComposeUiTest.start(text: String): Dp = onNodeWithText(text).getUnclippedBoundsInRoot().left

    private fun assertNear(expected: Dp, actual: Dp, what: String) {
        assertTrue(
            abs(expected.value - actual.value) < 1f,
            "$what: expected ${expected.value}dp, found ${actual.value}dp.",
        )
    }
}
