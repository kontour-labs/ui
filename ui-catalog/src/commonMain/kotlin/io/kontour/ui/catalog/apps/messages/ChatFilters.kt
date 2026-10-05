package io.kontour.ui.catalog.apps.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Check
import io.kontour.icons.tabler.outline.ChevronDown
import io.kontour.ui.adaptive.windowSizeClass
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.TextButton
import io.kontour.ui.components.datetime.DateRangePicker
import io.kontour.ui.components.datetime.rememberCalendarNavigationState
import io.kontour.ui.components.selection.ChipGroup
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalDate
import androidx.compose.ui.Alignment

/**
 * What the chat list is narrowed to.
 *
 * People and groups are two chips rather than one choice of three, because
 * that is how the chips read: neither on, or both, is every chat; one on is
 * only that kind. The dates are the range a chat's *last* message falls in —
 * "who was I talking to last week" — with [to] null while only the first day
 * of a range has been picked, which then stands for that one day.
 */
@Immutable
internal data class ChatFilter(
    val unreadOnly: Boolean = false,
    val people: Boolean = false,
    val groups: Boolean = false,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    /** Whether anything is narrowed at all, for the dot on the Filter action. */
    val active: Boolean get() = unreadOnly || people || groups || from != null

    fun matches(chat: Chat, last: Message?): Boolean {
        if (unreadOnly && chat.unread == 0) return false
        if (people != groups) {
            val wanted = if (people) ChatKind.Person else ChatKind.Group
            if (chat.kind != wanted) return false
        }
        if (from != null) {
            val day = last?.date ?: return false
            if (day < from || day > (to ?: from)) return false
        }
        return true
    }
}

/**
 * The filters, in whichever sheet suits the window.
 *
 * Beside the list on anything with room — a side sheet over the end of the
 * window, where the list stays in view and a chip changed in the sheet is
 * seen taking effect — and from the bottom on a phone, where a side sheet
 * would cover the list it is filtering anyway and the thumb is at the bottom.
 */
@Composable
internal fun ChatFiltersSheet(visible: Boolean, onDismissRequest: () -> Unit, state: MessagesState) {
    if (windowSizeClass.width.hasRoomBeside) {
        ModalSideSheet(visible = visible, onDismissRequest = onDismissRequest, paneTitle = "Filters") {
            FiltersContent(state, onDone = onDismissRequest, bottom = 0.dp)
        }
    } else {
        ModalBottomSheet(visible = visible, onDismissRequest = onDismissRequest, paneTitle = "Filters") { safeArea ->
            FiltersContent(state, onDone = onDismissRequest, bottom = safeArea.calculateBottomPadding())
        }
    }
}

/**
 * The sheet's inside: what to show, a range of days, and a button that says
 * how many chats it will leave.
 *
 * Every change applies at once — the button closes the sheet, it does not
 * commit anything — so the count on it is the count behind it, and closing the
 * sheet any other way keeps what was chosen.
 */
@Composable
private fun ColumnScope.FiltersContent(state: MessagesState, onDone: () -> Unit, bottom: Dp) {
    val filter = state.chatFilter
    val chats = state.chats
    val shown = chats.count { filter.matches(it, state.last(it.id)) }
    val activeDays = state.activeDays
    val marker = Theme.colours.accent.solid

    // The close is passed rather than left to the header to find: a side sheet
    // has no drag to close it by, and a cross is the way out anyone looks for.
    SheetHeader(
        onClose = onDone,
        actions = {
            TextButton(onClick = { state.chatFilter = ChatFilter() }, enabled = filter.active) { +"Clear" }
        },
    ) {
        +"Filters"
        supporting { +"$shown of ${chats.size} chats" }
    }
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(start = Theme.spacing.md, end = Theme.spacing.md, bottom = Theme.spacing.lg + bottom),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        Label("Show")
        ChipGroup {
            // The count is part of the chip's own words, not a badge beside it:
            // it is what the chip would find, read with its name.
            FilterChip(
                selected = filter.unreadOnly,
                onSelectedChange = { state.chatFilter = filter.copy(unreadOnly = it) },
                selectedIcon = Tabler.Outline.Check,
            ) { +"Unread only · ${chats.count { it.unread > 0 }}" }
            FilterChip(
                selected = filter.people,
                onSelectedChange = { state.chatFilter = filter.copy(people = it) },
                selectedIcon = Tabler.Outline.Check,
            ) { +"People · ${chats.count { it.kind == ChatKind.Person }}" }
            FilterChip(
                selected = filter.groups,
                onSelectedChange = { state.chatFilter = filter.copy(groups = it) },
                selectedIcon = Tabler.Outline.Check,
            ) { +"Groups · ${chats.count { it.kind == ChatKind.Group }}" }
        }
        Label("Last message between")
        DateRangePicker(
            start = filter.from,
            end = filter.to,
            onRangeChange = { from, to -> state.chatFilter = filter.copy(from = from, to = to) },
            today = Today,
            // In the middle of a sheet wider than the picker.
            modifier = Modifier.align(Alignment.CenterHorizontally),
            // Nobody has written tomorrow's messages yet.
            isDateSelectable = { it <= Today },
            // A dot under each day somebody said something, so the range is
            // picked from the days that have anything in them.
            markerFor = { day -> marker.takeIf { day in activeDays } },
            chooserIcon = Tabler.Outline.ChevronDown,
            // September, where most of the conversations are, rather than the
            // month of today with two days in it.
            navigation = rememberCalendarNavigationState(OpeningMonth),
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = Theme.spacing.xs)) {
            +if (shown == 1) "Show 1 chat" else "Show $shown chats"
        }
    }
}

/** A small heading inside the sheet, where a `SectionHeader`'s own inset would double the column's. */
@Composable
private fun Label(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = Theme.spacing.xs),
        style = Theme.typography.labelMedium,
        colour = Theme.colours.contentMuted,
    )
}

/** The month the date picker opens on. */
private val OpeningMonth = LocalDate(2026, 9, 1)
