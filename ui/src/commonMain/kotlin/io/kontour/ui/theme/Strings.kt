package io.kontour.ui.theme

import androidx.compose.runtime.Immutable
import io.kontour.ui.components.selection.ColourChannel
import io.kontour.ui.components.selection.ColourFormat
import io.kontour.ui.locale.Quantity

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
 * ### Plurals and word order
 *
 * A phrase with a number in it takes a [Quantity], not an `Int`: the number
 * already written in the reader's digits, and the plural form it takes in their
 * language. So a translation puts the number wherever its grammar wants it and
 * picks the right word for it:
 *
 * ```kotlin
 * Strings(
 *     peopleCount = { n -> n.select(one = "${n.formatted} Person", other = "${n.formatted} Personen") },
 *     pageOfCount = { page, count -> "Seite ${page.formatted} von ${count.formatted}" },
 * )
 * ```
 *
 * ### Where translations come from
 *
 * The library ships English and nothing else, and has no resource system of its
 * own: an app already has one. [from] reads every field from whichever it uses —
 * Android resources, Compose Multiplatform resources, moko-resources, a map —
 * through a [StringLookup], and `docs/export-strings.py` writes the English as
 * an Android `strings.xml`, plurals included, for a translator to start from.
 * `localisation.md` has the recipe for each.
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
    /** A menu's dismiss affordance, for assistive technology. */
    val closeMenu: String = "Close menu",

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
    /** One page's button in a pagination row: "Page 3". `page` counts from one. */
    val pageNumber: (page: Quantity) -> String = { page -> "Page ${page.formatted}" },
    /**
     * Where a carousel is, said as its position rather than as a page — the
     * carousel's own state, beside [pageOfCount] for its indicator: "2 of 5".
     * `position` counts from one.
     */
    val itemOfCount: (position: Quantity, count: Quantity) -> String =
        { position, count -> "${position.formatted} of ${count.formatted}" },
    /** A page indicator's position: "Page 2 of 5". `page` counts from one. */
    val pageOfCount: (page: Quantity, count: Quantity) -> String =
        { page, count -> "Page ${page.formatted} of ${count.formatted}" },

    // Reordering
    val moveUp: String = "Move up",
    val moveDown: String = "Move down",

    // Values
    val decrease: String = "Decrease",
    val increase: String = "Increase",
    val rangeStart: String = "Range start",
    val rangeEnd: String = "Range end",
    /** The action that opens a control's value for typing — a knob's readout. */
    val editValue: String = "Edit value",
    /** A score against its scale: "4.5 out of 5". `value` is already written out. */
    val outOf: (value: String, count: Quantity) -> String = { value, count -> "$value out of ${count.formatted}" },
    /** A progress bar that knows neither how far it is nor how far it has to go. */
    val inProgress: String = "In progress",
    /** A step progress bar's position; `working` while the step is still under way. */
    val stepOfCount: (step: Quantity, count: Quantity, working: Boolean) -> String = { step, count, working ->
        if (working) "Step ${step.formatted} of ${count.formatted}, in progress" else "Step ${step.formatted} of ${count.formatted}"
    },
    /** A badge with no count, marking something as new. */
    val new: String = "New",
    /** A badge's count, as it is read out: "3 new". */
    val newCount: (count: Quantity) -> String = { count -> "${count.formatted} new" },
    /** A badge's count past its cap, as it is read out: the cap shows as "99+". */
    val moreThan: (count: Quantity) -> String = { count -> "More than ${count.formatted}" },
    /** A count past its cap, as it is drawn: "99+". */
    val cappedCount: (cap: Quantity) -> String = { cap -> "${cap.formatted}+" },
    /** How many more there are than are shown — an avatar group's overflow: "+3". */
    val moreCount: (count: Quantity) -> String = { count -> "+${count.formatted}" },
    /** How many options are chosen, once there are too many to list: "5 selected". */
    val selectedCount: (count: Quantity) -> String = { count -> "${count.formatted} selected" },
    /** A group of people, as a screen reader names an avatar group: "4 people". */
    val peopleCount: (count: Quantity) -> String =
        { count -> count.select(one = "${count.formatted} person", other = "${count.formatted} people") },
    /** What an avatar with a name and no letters in it shows. */
    val unknownInitials: String = "?",
    /** A tour's position, drawn small in its corner: "2 / 5". */
    val tourPosition: (position: Quantity, count: Quantity) -> String =
        { position, count -> "${position.formatted} / ${count.formatted}" },
    /**
     * Separate sentences joined into one announcement — a tooltip's title and its
     * message, a statistic's label and value. "Perth. 12 services".
     */
    val sentences: (List<String>) -> String = { parts -> parts.joinToString(". ") },

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

    // Colour picker
    /** The saturation-and-value square. */
    val colourArea: String = "Saturation and brightness",
    val colourHue: String = "Hue",
    val colourOpacity: String = "Opacity",
    /** The notation the colour is written in — hex, RGB, HSV, HSL. */
    val colourFormat: String = "Colour format",
    /** Each notation's name, on its button: "Hex", "RGB", "HSV", "HSL". */
    val colourFormatName: (ColourFormat) -> String = { format ->
        when (format) {
            ColourFormat.Hex -> "Hex"
            ColourFormat.Rgb -> "RGB"
            ColourFormat.Hsv -> "HSV"
            ColourFormat.Hsl -> "HSL"
        }
    },
    /** A colour channel's field label: "R", "H", "A". */
    val colourChannel: (ColourChannel) -> String = { channel ->
        when (channel) {
            ColourChannel.Red -> "R"
            ColourChannel.Green -> "G"
            ColourChannel.Blue -> "B"
            ColourChannel.Hue -> "H"
            ColourChannel.Saturation -> "S"
            ColourChannel.Value -> "V"
            ColourChannel.Lightness -> "L"
            ColourChannel.Alpha -> "A"
        }
    },
    /** And its name, as a screen reader says it: "Red", "Hue", "Opacity". */
    val colourChannelName: (ColourChannel) -> String = { channel ->
        when (channel) {
            ColourChannel.Red -> "Red"
            ColourChannel.Green -> "Green"
            ColourChannel.Blue -> "Blue"
            ColourChannel.Hue -> "Hue"
            ColourChannel.Saturation -> "Saturation"
            ColourChannel.Value -> "Brightness"
            ColourChannel.Lightness -> "Lightness"
            ColourChannel.Alpha -> "Opacity"
        }
    },
    /** The colour picker's two modes: the continuous square and the swatches. */
    val colourSpectrum: String = "Spectrum",
    val colourPalette: String = "Palette",

    // Dates and times
    val hour: String = "Hour",
    val minute: String = "Minute",
    /** The morning-or-afternoon wheel of a 12-hour time picker. */
    val dayPeriod: String = "AM or PM",
    val month: String = "Month",
    val year: String = "Year",
    val previousMonth: String = "Previous month",
    val nextMonth: String = "Next month",
    /** Moves a calendar back to the month with today in it. */
    val returnToToday: String = "Return to today",
    /** The click label on a calendar's title, which opens the month-and-year wheels. */
    val chooseMonthAndYear: String = "Choose month and year",

    // Layout
    /** The divider between two panes a person can drag. */
    val resizePanes: String = "Resize panes",

    // Navigation
    val navigation: String = "Navigation",
    val closeNavigation: String = "Close navigation",
    val expandNavigation: String = "Expand navigation",
    val collapseNavigation: String = "Collapse navigation",

    // Tables
    /** A sorted column's header, sorted smallest first. */
    val sortedAscending: String = "Sorted ascending",
    /** And largest first. */
    val sortedDescending: String = "Sorted descending",
    /** A row's checkbox, where the row has nothing better to be called. */
    val selectRow: String = "Select row",

    // Activity calendar
    /** The quiet end of an activity calendar's legend. */
    val activityLess: String = "Less",
    /** Its busy end. */
    val activityMore: String = "More",
    /**
     * One day of an activity calendar, as its tooltip and a screen reader say it
     * — a format, like [pageOfCount], because it needs the count and the date.
     */
    val activityOnDay: (count: Quantity, date: String) -> String = { count, date ->
        if (count.value == 0L) {
            "No activity on $date"
        } else {
            count.select(one = "${count.formatted} activity on $date", other = "${count.formatted} activities on $date")
        }
    },
    /** A week of an activity calendar, named by the day it starts. */
    val weekOf: (date: String) -> String = { date -> "Week of $date" },

    // Index scroller
    /** The rail of letters beside a long list, as a screen reader names it. */
    val sectionIndex: String = "Section index",
    /** The rail's last label — `#` — said aloud: whatever no letter took. */
    val otherSection: String = "Other",
    /** Moving the rail on by one section. */
    val nextSection: String = "Next section",
    /** And back. */
    val previousSection: String = "Previous section",
) {
    companion object {
        /**
         * Every field from [lookup], and [fallback]'s wherever it has nothing.
         *
         * The bridge to whatever an app already translates with. Each field is
         * asked for by its name — `dismiss`, `pageOfCount` — and a phrase with a
         * number in it asks for the plural form its [Quantity] takes, with
         * `%1$s`, `%2$s` for its arguments in the order the field's own
         * parameters list them:
         *
         * ```kotlin
         * // Android resources, as `docs/export-strings.py` writes them.
         * val strings = Strings.from(object : StringLookup {
         *     override fun text(key: String) = resources.string("kontour_$key")
         *     override fun plural(key: String, quantity: Quantity) = resources.plural("kontour_$key", quantity.value)
         * })
         * ```
         *
         * Four keys are not field names: `stepOfCountInProgress` for a step
         * still under way, `activityOnDayNone` for a day with nothing on it,
         * `sentenceSeparator` for what joins sentences, and
         * `colourFormatName_Hex`, `colourChannel_Red` and their siblings for the
         * words an enum is shown as.
         */
        fun from(lookup: StringLookup, fallback: Strings = Strings()): Strings = Strings(
        dismiss = lookup.text("dismiss") ?: fallback.dismiss,
        close = lookup.text("close") ?: fallback.close,
        back = lookup.text("back") ?: fallback.back,
        cancel = lookup.text("cancel") ?: fallback.cancel,
        confirm = lookup.text("confirm") ?: fallback.confirm,
        gotIt = lookup.text("gotIt") ?: fallback.gotIt,
        expandSheet = lookup.text("expandSheet") ?: fallback.expandSheet,
        collapseSheet = lookup.text("collapseSheet") ?: fallback.collapseSheet,
        closeMenu = lookup.text("closeMenu") ?: fallback.closeMenu,
        loading = lookup.text("loading") ?: fallback.loading,
        loadingMore = lookup.text("loadingMore") ?: fallback.loadingMore,
        loadMoreFailed = lookup.text("loadMoreFailed") ?: fallback.loadMoreFailed,
        retry = lookup.text("retry") ?: fallback.retry,
        refreshing = lookup.text("refreshing") ?: fallback.refreshing,
        pullToRefresh = lookup.text("pullToRefresh") ?: fallback.pullToRefresh,
        releaseToRefresh = lookup.text("releaseToRefresh") ?: fallback.releaseToRefresh,
        expanded = lookup.text("expanded") ?: fallback.expanded,
        collapsed = lookup.text("collapsed") ?: fallback.collapsed,
        previous = lookup.text("previous") ?: fallback.previous,
        next = lookup.text("next") ?: fallback.next,
        previousPage = lookup.text("previousPage") ?: fallback.previousPage,
        nextPage = lookup.text("nextPage") ?: fallback.nextPage,
        goToPage = lookup.text("goToPage") ?: fallback.goToPage,
        goToPageConfirm = lookup.text("goToPageConfirm") ?: fallback.goToPageConfirm,
        pageNumber = { page -> lookup.phrase("pageNumber", page, page.formatted) ?: fallback.pageNumber(page) },
        itemOfCount = { position, count -> lookup.phrase("itemOfCount", count, position.formatted, count.formatted) ?: fallback.itemOfCount(position, count) },
        pageOfCount = { page, count -> lookup.phrase("pageOfCount", count, page.formatted, count.formatted) ?: fallback.pageOfCount(page, count) },
        moveUp = lookup.text("moveUp") ?: fallback.moveUp,
        moveDown = lookup.text("moveDown") ?: fallback.moveDown,
        decrease = lookup.text("decrease") ?: fallback.decrease,
        increase = lookup.text("increase") ?: fallback.increase,
        rangeStart = lookup.text("rangeStart") ?: fallback.rangeStart,
        rangeEnd = lookup.text("rangeEnd") ?: fallback.rangeEnd,
        editValue = lookup.text("editValue") ?: fallback.editValue,
        outOf = { value, count -> lookup.phrase("outOf", count, value, count.formatted) ?: fallback.outOf(value, count) },
        inProgress = lookup.text("inProgress") ?: fallback.inProgress,
        stepOfCount = { step, count, working ->
            lookup.phrase(if (working) "stepOfCountInProgress" else "stepOfCount", count, step.formatted, count.formatted)
                ?: fallback.stepOfCount(step, count, working)
        },
        new = lookup.text("new") ?: fallback.new,
        newCount = { count -> lookup.phrase("newCount", count, count.formatted) ?: fallback.newCount(count) },
        moreThan = { count -> lookup.phrase("moreThan", count, count.formatted) ?: fallback.moreThan(count) },
        cappedCount = { cap -> lookup.phrase("cappedCount", cap, cap.formatted) ?: fallback.cappedCount(cap) },
        moreCount = { count -> lookup.phrase("moreCount", count, count.formatted) ?: fallback.moreCount(count) },
        selectedCount = { count -> lookup.phrase("selectedCount", count, count.formatted) ?: fallback.selectedCount(count) },
        peopleCount = { count -> lookup.phrase("peopleCount", count, count.formatted) ?: fallback.peopleCount(count) },
        unknownInitials = lookup.text("unknownInitials") ?: fallback.unknownInitials,
        tourPosition = { position, count -> lookup.phrase("tourPosition", count, position.formatted, count.formatted) ?: fallback.tourPosition(position, count) },
        sentences = lookup.text("sentenceSeparator")?.let { separator -> { parts: List<String> -> parts.joinToString(separator) } } ?: fallback.sentences,
        search = lookup.text("search") ?: fallback.search,
        clearSearch = lookup.text("clearSearch") ?: fallback.clearSearch,
        select = lookup.text("select") ?: fallback.select,
        noMatches = lookup.text("noMatches") ?: fallback.noMatches,
        showPassword = lookup.text("showPassword") ?: fallback.showPassword,
        hidePassword = lookup.text("hidePassword") ?: fallback.hidePassword,
        commandPalettePlaceholder = lookup.text("commandPalettePlaceholder") ?: fallback.commandPalettePlaceholder,
        noMatchingCommands = lookup.text("noMatchingCommands") ?: fallback.noMatchingCommands,
        copy = lookup.text("copy") ?: fallback.copy,
        cut = lookup.text("cut") ?: fallback.cut,
        paste = lookup.text("paste") ?: fallback.paste,
        selectAll = lookup.text("selectAll") ?: fallback.selectAll,
        colourArea = lookup.text("colourArea") ?: fallback.colourArea,
        colourHue = lookup.text("colourHue") ?: fallback.colourHue,
        colourOpacity = lookup.text("colourOpacity") ?: fallback.colourOpacity,
        colourFormat = lookup.text("colourFormat") ?: fallback.colourFormat,
        colourFormatName = { format -> lookup.text("colourFormatName_${format.name}") ?: fallback.colourFormatName(format) },
        colourChannel = { channel -> lookup.text("colourChannel_${channel.name}") ?: fallback.colourChannel(channel) },
        colourChannelName = { channel -> lookup.text("colourChannelName_${channel.name}") ?: fallback.colourChannelName(channel) },
        colourSpectrum = lookup.text("colourSpectrum") ?: fallback.colourSpectrum,
        colourPalette = lookup.text("colourPalette") ?: fallback.colourPalette,
        hour = lookup.text("hour") ?: fallback.hour,
        minute = lookup.text("minute") ?: fallback.minute,
        dayPeriod = lookup.text("dayPeriod") ?: fallback.dayPeriod,
        month = lookup.text("month") ?: fallback.month,
        year = lookup.text("year") ?: fallback.year,
        previousMonth = lookup.text("previousMonth") ?: fallback.previousMonth,
        nextMonth = lookup.text("nextMonth") ?: fallback.nextMonth,
        returnToToday = lookup.text("returnToToday") ?: fallback.returnToToday,
        chooseMonthAndYear = lookup.text("chooseMonthAndYear") ?: fallback.chooseMonthAndYear,
        resizePanes = lookup.text("resizePanes") ?: fallback.resizePanes,
        navigation = lookup.text("navigation") ?: fallback.navigation,
        closeNavigation = lookup.text("closeNavigation") ?: fallback.closeNavigation,
        expandNavigation = lookup.text("expandNavigation") ?: fallback.expandNavigation,
        collapseNavigation = lookup.text("collapseNavigation") ?: fallback.collapseNavigation,
        sortedAscending = lookup.text("sortedAscending") ?: fallback.sortedAscending,
        sortedDescending = lookup.text("sortedDescending") ?: fallback.sortedDescending,
        selectRow = lookup.text("selectRow") ?: fallback.selectRow,
        activityLess = lookup.text("activityLess") ?: fallback.activityLess,
        activityMore = lookup.text("activityMore") ?: fallback.activityMore,
        activityOnDay = { count, date ->
            (if (count.value == 0L) lookup.phrase("activityOnDayNone", count, date) else null)
                ?: lookup.phrase("activityOnDay", count, count.formatted, date)
                ?: fallback.activityOnDay(count, date)
        },
        weekOf = { date -> lookup.phrase("weekOf", null, date) ?: fallback.weekOf(date) },
        sectionIndex = lookup.text("sectionIndex") ?: fallback.sectionIndex,
        otherSection = lookup.text("otherSection") ?: fallback.otherSection,
        nextSection = lookup.text("nextSection") ?: fallback.nextSection,
        previousSection = lookup.text("previousSection") ?: fallback.previousSection,
        )
    }
}

/**
 * Where [Strings.from] gets its words: an app's own resources, through whatever
 * reads them — Android's, Compose Multiplatform's, moko's, a map in a test.
 */
fun interface StringLookup {
    /** The translation of [key], or `null` to keep the English. */
    fun text(key: String): String?

    /**
     * The translation of [key] in the form [quantity] takes, or `null` to use
     * [text]. Android's `getQuantityString` and Compose's `pluralStringResource`
     * choose by the number itself — `quantity.value` — and a lookup keyed by
     * category has `quantity.category`.
     */
    fun plural(key: String, quantity: Quantity): String? = null
}

/** [key]'s translation, in [quantity]'s plural form where it has one, filled with [arguments]. */
private fun StringLookup.phrase(key: String, quantity: Quantity?, vararg arguments: String): String? =
    (quantity?.let { plural(key, it) } ?: text(key))?.let { fillArguments(it, arguments) }

/**
 * `%1$s`, `%2$s` — and plain `%s` and `%d`, taken in order — replaced by
 * [arguments]; `%%` is a percent sign. The subset of `String.format` a
 * translated resource uses, without the platform formatter that would
 * re-write the numbers in its own locale's digits.
 */
internal fun fillArguments(template: String, arguments: Array<out String>): String = buildString {
    var next = 0
    var index = 0
    while (index < template.length) {
        val char = template[index]
        if (char != '%' || index + 1 >= template.length) {
            append(char)
            index++
            continue
        }
        val following = template[index + 1]
        when {
            following == '%' -> {
                append('%')
                index += 2
            }
            following == 's' || following == 'd' -> {
                append(arguments.getOrElse(next++) { "" })
                index += 2
            }
            following.isDigit() -> {
                var end = index + 1
                while (end < template.length && template[end].isDigit()) end++
                if (end + 1 < template.length && template[end] == '$' && (template[end + 1] == 's' || template[end + 1] == 'd')) {
                    val position = template.substring(index + 1, end).toInt() - 1
                    append(arguments.getOrElse(position) { "" })
                    index = end + 2
                } else {
                    append(char)
                    index++
                }
            }
            else -> {
                append(char)
                index++
            }
        }
    }
}

/**
 * These strings with every word passed through [transform] — the phrases' too,
 * after they are filled in. What a pseudo-locale is made of.
 */
internal fun Strings.mapped(transform: (String) -> String): Strings = Strings(
    dismiss = transform(dismiss),
    close = transform(close),
    back = transform(back),
    cancel = transform(cancel),
    confirm = transform(confirm),
    gotIt = transform(gotIt),
    expandSheet = transform(expandSheet),
    collapseSheet = transform(collapseSheet),
    closeMenu = transform(closeMenu),
    loading = transform(loading),
    loadingMore = transform(loadingMore),
    loadMoreFailed = transform(loadMoreFailed),
    retry = transform(retry),
    refreshing = transform(refreshing),
    pullToRefresh = transform(pullToRefresh),
    releaseToRefresh = transform(releaseToRefresh),
    expanded = transform(expanded),
    collapsed = transform(collapsed),
    previous = transform(previous),
    next = transform(next),
    previousPage = transform(previousPage),
    nextPage = transform(nextPage),
    goToPage = transform(goToPage),
    goToPageConfirm = transform(goToPageConfirm),
    pageNumber = { a0 -> transform(pageNumber(a0)) },
    itemOfCount = { a0, a1 -> transform(itemOfCount(a0, a1)) },
    pageOfCount = { a0, a1 -> transform(pageOfCount(a0, a1)) },
    moveUp = transform(moveUp),
    moveDown = transform(moveDown),
    decrease = transform(decrease),
    increase = transform(increase),
    rangeStart = transform(rangeStart),
    rangeEnd = transform(rangeEnd),
    editValue = transform(editValue),
    outOf = { a0, a1 -> transform(outOf(a0, a1)) },
    inProgress = transform(inProgress),
    stepOfCount = { a0, a1, a2 -> transform(stepOfCount(a0, a1, a2)) },
    new = transform(new),
    newCount = { a0 -> transform(newCount(a0)) },
    moreThan = { a0 -> transform(moreThan(a0)) },
    cappedCount = { a0 -> transform(cappedCount(a0)) },
    moreCount = { a0 -> transform(moreCount(a0)) },
    selectedCount = { a0 -> transform(selectedCount(a0)) },
    peopleCount = { a0 -> transform(peopleCount(a0)) },
    unknownInitials = transform(unknownInitials),
    tourPosition = { a0, a1 -> transform(tourPosition(a0, a1)) },
    sentences = { parts -> transform(sentences(parts)) },
    search = transform(search),
    clearSearch = transform(clearSearch),
    select = transform(select),
    noMatches = transform(noMatches),
    showPassword = transform(showPassword),
    hidePassword = transform(hidePassword),
    commandPalettePlaceholder = transform(commandPalettePlaceholder),
    noMatchingCommands = transform(noMatchingCommands),
    copy = transform(copy),
    cut = transform(cut),
    paste = transform(paste),
    selectAll = transform(selectAll),
    colourArea = transform(colourArea),
    colourHue = transform(colourHue),
    colourOpacity = transform(colourOpacity),
    colourFormat = transform(colourFormat),
    colourFormatName = { a0 -> transform(colourFormatName(a0)) },
    colourChannel = { a0 -> transform(colourChannel(a0)) },
    colourChannelName = { a0 -> transform(colourChannelName(a0)) },
    colourSpectrum = transform(colourSpectrum),
    colourPalette = transform(colourPalette),
    hour = transform(hour),
    minute = transform(minute),
    dayPeriod = transform(dayPeriod),
    month = transform(month),
    year = transform(year),
    previousMonth = transform(previousMonth),
    nextMonth = transform(nextMonth),
    returnToToday = transform(returnToToday),
    chooseMonthAndYear = transform(chooseMonthAndYear),
    resizePanes = transform(resizePanes),
    navigation = transform(navigation),
    closeNavigation = transform(closeNavigation),
    expandNavigation = transform(expandNavigation),
    collapseNavigation = transform(collapseNavigation),
    sortedAscending = transform(sortedAscending),
    sortedDescending = transform(sortedDescending),
    selectRow = transform(selectRow),
    activityLess = transform(activityLess),
    activityMore = transform(activityMore),
    activityOnDay = { a0, a1 -> transform(activityOnDay(a0, a1)) },
    weekOf = { a0 -> transform(weekOf(a0)) },
    sectionIndex = transform(sectionIndex),
    otherSection = transform(otherSection),
    nextSection = transform(nextSection),
    previousSection = transform(previousSection),
)
