package io.kontour.ui.catalog.apps

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.Messages

/**
 * An app inside the gallery: a whole screen of the library's components doing
 * a job together, with its own navigation, rather than one component on a card.
 *
 * Asked for as *"a few more demos … that show off a bit more of the navigation
 * stuff, with a heap of our components in it. It should almost launch a
 * 'mini-app' within the app that allows users to navigate between a heap of
 * pages."* A card can show what a component does; only a screen shows whether
 * the components belong together — a hero bar collapsing over a list that
 * refreshes, a sheet over a page over a bar, back going to the innermost thing
 * that can take it.
 *
 * Each takes the whole window while it runs (see [AppTakeover]) and leaves by
 * back at its root, by its own leave button, or by Escape.
 *
 * @property components What a reader will find inside, for the Apps page's
 *   card: the components this app is the best demo of, not every one it uses.
 */
internal enum class MiniApp(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val components: List<String>,
) {
    Transit(
        title = "Transit",
        summary = "Departures, stops and trips for a city's trains and buses. A hero bar over the " +
            "departures, an A–Z of stops, a ticket that covers the bar, and a planner in a sheet of parts.",
        icon = Tabler.Outline.Bus,
        components = listOf(
            "TopBar (Hero)", "NavBar", "PullToRefresh", "SwipeActions", "IndexScroller", "Timeline",
            "Table", "Gauge", "ActivityCalendar", "Coachmark", "BottomSheet", "WheelSelect",
        ),
    ),
    Messages(
        title = "Messages & Notes",
        summary = "Conversations beside their list on a wide window and over it on a phone, notes " +
            "that reorder and edit in place, and a command palette on ⌘K.",
        icon = Tabler.Outline.Messages,
        components = listOf(
            "ChatBubble", "List-detail", "SideSheet", "DateRangePicker", "Popover", "DropdownMenu",
            "ReorderableItem", "SelectableText", "TextArea", "EmptyState", "Kbd", "Badge",
        ),
    ),
}

/**
 * Starts an app, from anywhere in the gallery. Provided by `Catalog`; nothing
 * outside the gallery has one, so it does nothing there.
 */
internal val LocalOpenApp = staticCompositionLocalOf<(MiniApp) -> Unit> { {} }
