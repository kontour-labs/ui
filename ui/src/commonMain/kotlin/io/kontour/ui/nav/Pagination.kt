package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/**
 * Moves between numbered pages of results.
 *
 * ```kotlin
 * Pagination(page = page, pageCount = totalPages, onPageChange = { page = it })
 * ```
 *
 * For a table or a result set the user needs to move *around* — jumping to page
 * seven and back to page two. Where the user only ever wants more of the same
 * list, use [io.kontour.ui.components.list.LoadMore]: pagination makes people
 * navigate a list they were reading.
 *
 * Long ranges collapse to first, last, and a window around the current page, so
 * the control does not grow with the result set. Every number is a real button
 * with its own announcement, and the current one reports as selected rather than
 * relying on colour — colour alone would fail WCAG 1.4.1.
 *
 * @param window How many pages to show either side of the current one.
 */
@Composable
fun Pagination(
    page: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    window: Int = 1,
    previousLabel: String = "Previous page",
    nextLabel: String = "Next page",
) {
    if (pageCount <= 1) return

    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            icon = SystemIcons.ChevronBack,
            contentDescription = previousLabel,
            onClick = { onPageChange(page - 1) },
            enabled = page > 0,
        )

        for (slot in paginationSlots(page, pageCount, window)) {
            when (slot) {
                is PaginationSlot.Page -> PageButton(
                    number = slot.index,
                    selected = slot.index == page,
                    onClick = { onPageChange(slot.index) },
                )

                PaginationSlot.Gap -> Text(
                    text = "…",
                    modifier = Modifier
                        .padding(horizontal = Theme.spacing.xxs)
                        // Announcing "ellipsis" between two page numbers tells a
                        // screen-reader user nothing they can act on.
                        .semantics { contentDescription = "" },
                    style = Theme.typography.bodyMedium,
                    color = Theme.colors.contentSubtle,
                )
            }
        }

        IconButton(
            icon = SystemIcons.ChevronForward,
            contentDescription = nextLabel,
            onClick = { onPageChange(page + 1) },
            enabled = page < pageCount - 1,
        )
    }
}

@Composable
private fun PageButton(number: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = Theme.colors
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.small

    Box(
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "Page ${number + 1}"
            }
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
            .clip(shape)
            .background(
                if (selected) colors.accent.container else androidx.compose.ui.graphics.Color.Transparent,
                shape,
            )
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = kontourIndication(shape, pressScale = 1f),
                role = Role.Button,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${number + 1}",
            style = Theme.typography.bodyMedium,
            color = if (selected) colors.accent.onContainer else colors.contentMuted,
        )
    }
}

/** What goes in one position of a [Pagination] row. */
sealed interface PaginationSlot {
    data class Page(val index: Int) : PaginationSlot
    data object Gap : PaginationSlot
}

/**
 * Which page numbers to show for a range of [pageCount], centred on [page].
 *
 * Pure, and tested, because this is fiddly in exactly the way that produces a
 * control which is right in the middle of a range and wrong at both ends — and
 * "page 1 of 40" is the first thing anyone sees.
 *
 * Always shows the first and last page, plus [window] either side of the
 * current, with a gap where numbers were dropped. Two refinements keep it from
 * hiding pages it had room for:
 *
 * - **A range that already fits is shown whole.** The collapsed form is at most
 *   `2 × window + 5` slots wide, so anything shorter than that can be listed in
 *   full — "1 2 … 5" is exactly as wide as "1 2 3 4 5" and shows two fewer
 *   pages.
 * - **A gap standing in for a single page is replaced by that page**, for the
 *   same reason one step down.
 */
fun paginationSlots(page: Int, pageCount: Int, window: Int = 1): List<PaginationSlot> {
    if (pageCount <= 0) return emptyList()

    val widestCollapsed = 2 * window + 5
    if (pageCount <= widestCollapsed) {
        return List(pageCount) { PaginationSlot.Page(it) }
    }

    val shown = buildSet {
        add(0)
        add(pageCount - 1)
        for (i in (page - window)..(page + window)) {
            if (i in 0 until pageCount) add(i)
        }
    }.sorted()

    val slots = mutableListOf<PaginationSlot>()
    var previous: Int? = null
    for (index in shown) {
        when {
            previous == null -> Unit
            index == previous + 2 -> slots.add(PaginationSlot.Page(index - 1))
            index > previous + 1 -> slots.add(PaginationSlot.Gap)
        }
        slots.add(PaginationSlot.Page(index))
        previous = index
    }
    return slots
}
