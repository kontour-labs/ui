package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.text.TextField
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.overlay.Popover
import io.kontour.ui.theme.Theme

/**
 * Moves between numbered pages of results.
 *
 * ```kotlin
 * Pagination(value = page, pageCount = totalPages, onValueChange = { page = it })
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
 * ### [window] is a ceiling, not a promise
 *
 * A collapsed range still has a width, and on a phone it is bigger than it
 * looks: every button reserves `Theme.sizing.minTouchTarget`, which is 48dp on
 * Android against the 24 a desktop asks for. `« 1 … 19 20 21 … 40 »` needs
 * about 410dp on that arithmetic and a 360dp phone offers roughly 310 — so it
 * ran off the right-hand edge, where the numbers are neither visible nor
 * reachable, and it did so in a golden that had been looked at, because the JVM
 * renders those same buttons at 24dp and everything fit.
 *
 * So the window narrows to what there is room for, down to nothing: first,
 * current and last, which is three buttons and always fits. Where there is
 * room — a desktop, a tablet, a horizontally scrolling parent that measures
 * this unbounded — [window] is honoured exactly as asked.
 *
 * @param window How many pages to show either side of the current one, if they
 *   fit. Fewer are shown when they do not.
 */
@Composable
fun Pagination(
    value: Int,
    pageCount: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    window: Int = 1,
    previousLabel: String = Theme.strings.previousPage,
    nextLabel: String = Theme.strings.nextPage,
    /**
     * Whether tapping an ellipsis opens a box to type a page number into.
     *
     * Off by default, because it changes what the ellipsis *is*: with this on it
     * is a control and gets a touch target, an outline when focused and a label
     * a screen reader will read out; with it off it is punctuation, announced as
     * nothing. Both are right, for different rows — a forty-page result set
     * wants it, a five-page one has no ellipsis to tap.
     *
     * The gap is the only sensible place for it. It is the part of the row that
     * stands for the pages you cannot see, which is exactly the set you would be
     * typing a number to reach.
     */
    allowJump: Boolean = false,
    jumpLabel: String = Theme.strings.goToPage,
    jumpConfirmLabel: String = Theme.strings.goToPageConfirm,
) {
    if (pageCount <= 1) return

    var openGap by remember { mutableStateOf(-1) }

    BoxWithConstraints(modifier) {
        val fitted = widestWindowThatFits(
            available = maxWidth,
            requested = window,
            value = value,
            pageCount = pageCount,
            button = maxOf(PageButtonSize, Theme.sizing.minTouchTarget),
            gap = Theme.spacing.xxs,
        )

        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                icon = SystemIcons.ChevronBack,
                contentDescription = previousLabel,
                onClick = { onValueChange(value - 1) },
                enabled = value > 0,
            )

            // Which gap has its box open, by position in the row. A single
            // boolean would open both of them: a long row has an ellipsis at each
            // end and they are different controls.
            var gapOrdinal = -1
            for (slot in paginationSlots(value, pageCount, fitted)) {
                when (slot) {
                    is PaginationSlot.Page -> PageButton(
                        number = slot.index,
                        selected = slot.index == value,
                        onClick = { onValueChange(slot.index) },
                    )

                    PaginationSlot.Gap -> if (allowJump) {
                        gapOrdinal++
                        JumpGap(
                            open = openGap == gapOrdinal,
                            onOpenChange = { open -> openGap = if (open) gapOrdinal else -1 },
                            pageCount = pageCount,
                            onValueChange = onValueChange,
                            label = jumpLabel,
                            confirmLabel = jumpConfirmLabel,
                        )
                    } else {
                        Text(
                            text = "…",
                            modifier = Modifier
                                .padding(horizontal = Theme.spacing.xxs)
                                // Announcing "ellipsis" between two page numbers
                                // tells a screen-reader user nothing they can act
                                // on. When it *is* a control, `JumpGap` labels it.
                                .semantics { contentDescription = "" },
                            style = Theme.typography.bodyMedium,
                            colour = Theme.colours.contentSubtle,
                        )
                    }
                }
            }

            IconButton(
                icon = SystemIcons.ChevronForward,
                contentDescription = nextLabel,
                onClick = { onValueChange(value + 1) },
                enabled = value < pageCount - 1,
            )
        }
    }
}

/**
 * An ellipsis you can tap to type a page number into.
 *
 * The pages it stands for are exactly the ones you cannot reach by tapping, so
 * it is the one part of the row where a number is worth typing.
 *
 * Typed one-based, because that is what the row shows and what
 * `contentDescription` announces — "Page 1" for index zero. Clamped rather than
 * rejected: somebody typing 99 into a forty-page set means the end, and an error
 * message for that is a lecture.
 */
@Composable
private fun JumpGap(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    pageCount: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    confirmLabel: String,
) {
    val typed = rememberTextFieldState()
    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.control

    fun commit() {
        typed.text.toString().trim().toIntOrNull()?.let { page ->
            onValueChange((page - 1).coerceIn(0, pageCount - 1))
        }
        typed.clearText()
        onOpenChange(false)
    }

    Box {
        Box(
            modifier = Modifier
                .semantics(mergeDescendants = true) { contentDescription = label }
                .minimumTouchTarget()
                .focusRing(interactions, shape)
                .clip(shape)
                .clickable(interactionSource = interactions, indication = null) {
                    onOpenChange(!open)
                }
                .padding(horizontal = Theme.spacing.xxs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "…",
                style = Theme.typography.bodyMedium,
                colour = Theme.colours.contentSubtle,
            )
        }

        Popover(visible = open, onDismissRequest = { onOpenChange(false) }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    state = typed,
                    label = null,
                    placeholder = "1\u2013$pageCount",
                    modifier = Modifier.width(JumpFieldWidth),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                    onKeyboardAction = { commit() },
                )
                Button(onClick = { commit() }, size = ButtonSize.Small) { +confirmLabel }
            }
        }
    }
}

/** Wide enough for four digits and the range hint behind them. */
private val JumpFieldWidth: Dp = 96.dp

/**
 * The largest window from [requested] down to zero whose row fits [available].
 *
 * Arithmetic rather than a measure pass, because the row's width is a sum of
 * known parts: every page and arrow is a [button] square, every gap is an
 * ellipsis with [GapWidth] to itself, and there is a [gap] between each pair.
 * The estimate is deliberately a little generous — erring towards a narrower
 * window costs one page number, while erring the other way puts the last page
 * off the edge of the screen, which is the failure this exists to stop.
 *
 * [available] is `Dp.Infinity` inside a horizontally scrolling parent, which is
 * the right answer there: nothing is being clipped, so nothing needs dropping.
 */
private fun widestWindowThatFits(
    available: Dp,
    requested: Int,
    value: Int,
    pageCount: Int,
    button: Dp,
    gap: Dp,
): Int {
    for (window in requested downTo 1) {
        val slots = paginationSlots(value, pageCount, window)
        val gaps = slots.count { it == PaginationSlot.Gap }
        // The two arrows are buttons the slot list does not know about.
        val buttons = slots.size - gaps + 2
        val width =
            button * buttons + GapWidth * gaps + gap * (buttons + gaps - 1)
        if (width <= available) return window
    }
    return 0
}

@Composable
private fun PageButton(number: Int, selected: Boolean, onClick: () -> Unit) {
    val colours = Theme.colours
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.control

    Box(
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "Page ${number + 1}"
            }
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .sizeIn(minWidth = PageButtonSize, minHeight = PageButtonSize)
            .clip(shape)
            .background(
                if (selected) colours.accent.container else androidx.compose.ui.graphics.Color.Transparent,
                shape,
            )
            .selectable(
                selected = selected,
                interactionSource = interactions,
                // A page number is a small button and nothing else answers the tap.
                indication = kontourIndication(
                    shape,
                    io.kontour.ui.components.action.ButtonDefaults.SmallPressScale,
                ),
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
            colour = if (selected) colours.accent.onContainer else colours.contentMuted,
        )
    }
}

/** The visible square of a page number, before any touch target around it. */
private val PageButtonSize = 36.dp

/** About what an ellipsis and its side padding come to at `bodyMedium`. */
private val GapWidth = 24.dp

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
