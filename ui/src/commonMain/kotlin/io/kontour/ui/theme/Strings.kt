package io.kontour.ui.theme

import androidx.compose.runtime.Immutable

/**
 * Every word this library puts on screen that the caller did not supply.
 *
 * Forty-seven of these used to be English literals in parameter defaults —
 * `dismissLabel: String = "Dismiss"`, `pullLabel: String = "Pull to refresh"`.
 * That is fine for one app that ships in one language and wrong for a library,
 * because the only way to change one was to pass it at every call site, and
 * missing a call site is invisible until somebody reads the screen in Welsh.
 *
 * They are a token group now, like [Spacing] or [Motion]:
 *
 * ```kotlin
 * KontourTheme(strings = Strings(dismiss = "Schließen", back = "Zurück")) {
 *     AppRoot()
 * }
 * ```
 *
 * Each component still takes the parameter, defaulted from here — so a one-off
 * override at a call site works exactly as it did, and an app-wide one is a
 * single argument instead of a sweep.
 *
 * ### One field per idea, not per parameter
 *
 * `SheetHeader`, `SideSheet` and `ModalBottomSheet` all say "Close", and all
 * three read [close]. Translating a library where the same word appears three
 * times under three names is how a translation ends up saying three different
 * things.
 *
 * ### Not a localisation system
 *
 * There is no plural handling, no gender, no locale lookup, and no resource
 * bundle. This makes the strings *reachable*; resourcing them properly is a
 * separate piece of work with its own tooling question. What it buys today is
 * that no English is welded into a component, and an app can supply its own set
 * from whatever mechanism it already has.
 */
@Immutable
data class Strings(
    // Overlays and sheets
    /** A dismissible surface's close affordance — banners, dialogs. */
    val dismiss: String = "Dismiss",
    /** A close *control*, where what it closes is the surface around it. */
    val close: String = "Close",
    val back: String = "Back",
    val cancel: String = "Cancel",
    val confirm: String = "Confirm",
    /** Acknowledges a coach mark. Deliberately warmer than "Dismiss". */
    val gotIt: String = "Got it",
    val expandSheet: String = "Expand sheet",
    val collapseSheet: String = "Collapse sheet",

    // Loading, failure and retry
    val loading: String = "Loading",
    val loadingMore: String = "Loading more",
    val loadMoreFailed: String = "Couldn't load more",
    val retry: String = "Try again",
    val refreshing: String = "Refreshing",
    val pullToRefresh: String = "Pull to refresh",
    val releaseToRefresh: String = "Release to refresh",

    // Disclosure
    val expanded: String = "Expanded",
    val collapsed: String = "Collapsed",

    // Moving through things
    val previous: String = "Previous",
    val next: String = "Next",
    val previousPage: String = "Previous page",
    val nextPage: String = "Next page",

    /** The ellipsis in a pagination row, when it can be tapped to jump. */
    val goToPage: String = "Go to page",

    /** Confirms the page typed into that box. */
    val goToPageConfirm: String = "Go",
    /**
     * A page indicator's position, which needs the numbers rather than a
     * constant — the one place here that is a format instead of a word.
     */
    val pageOfCount: (page: Int, count: Int) -> String =
        { page, count -> "Page ${page + 1} of $count" },

    // Reordering
    val moveUp: String = "Move up",
    val moveDown: String = "Move down",

    // Values
    val decrease: String = "Decrease",
    val increase: String = "Increase",
    val rangeStart: String = "Range start",
    val rangeEnd: String = "Range end",

    // Fields and pickers
    val search: String = "Search",
    val clearSearch: String = "Clear search",
    val select: String = "Select…",
    val noMatches: String = "No matches",
    val showPassword: String = "Show password",
    val hidePassword: String = "Hide password",
    val commandPalettePlaceholder: String = "Type a command",
    val noMatchingCommands: String = "No matching commands",

    // Text selection
    val copy: String = "Copy",
    val cut: String = "Cut",
    val paste: String = "Paste",
    val selectAll: String = "Select all",

    // Navigation
    val navigation: String = "Navigation",
    val closeNavigation: String = "Close navigation",
    val expandNavigation: String = "Expand navigation",
    val collapseNavigation: String = "Collapse navigation",
)
