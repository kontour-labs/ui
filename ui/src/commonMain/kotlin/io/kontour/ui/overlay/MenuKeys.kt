package io.kontour.ui.overlay

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One place in a menu the keys can move to: a row, or an action row as a whole.
 *
 * Kept by the row while it is composed and handed to its surface's
 * [MenuNavigator], which reads it only as a key is pressed.
 */
internal class MenuStop {
    val requester = FocusRequester()

    /** Its place in a long list of options, or -1 in a menu composed all at once. */
    var place: Int = -1

    /** The words type-ahead finds it by, or null for a stop with none. */
    var name: String? = null

    var enabled: Boolean = true

    /** Whether it is the option its select holds, which the list opens at. */
    var chosen: Boolean = false

    /** Whether the focus is on it, or for an action row in it. */
    var focused: Boolean = false

    var coordinates: LayoutCoordinates? = null
}

/**
 * The stops of one surface of a menu — the menu itself, or a submenu open over
 * it — and how to reach those it has not composed.
 */
internal class MenuNavigator {
    /** The stops composed now, in the order they were composed. */
    val stops = mutableListOf<MenuStop>()

    /** The rows of a long list, composed as they are shown, or null for a column. */
    var lazy: LazyMenuList? = null

    /** The column's scroll, for how many rows a page is. */
    var scroll: ScrollState? = null

    /**
     * Whether its list has been placed: until then it is put where it opens,
     * and after that it is the reader's to scroll. Once for the menu, so a
     * combobox's list narrowed from long to short does not open again.
     */
    var opened: Boolean = false
}

/** A long list's options, and the list showing them. */
internal class LazyMenuList(val state: LazyListState, val rows: MenuRows)

/**
 * A stop on the surface this is drawn on, kept in its navigator while it is
 * composed; or null outside a menu, where there is no surface to move about.
 */
@Composable
internal fun rememberMenuStop(): MenuStop? {
    val navigator = LocalMenuNavigator.current ?: return null
    val stop = remember(navigator) { MenuStop() }
    DisposableEffect(navigator, stop) {
        navigator.stops += stop
        onDispose { navigator.stops -= stop }
    }
    return stop
}

/**
 * Ties [stop] to the focus target after it: its focus, whether it has it, and
 * where it is, which orders it among the surface's other stops.
 */
internal fun Modifier.menuStop(stop: MenuStop): Modifier = this
    .focusRequester(stop.requester)
    .onFocusChanged { stop.focused = it.hasFocus }
    .onGloballyPositioned { stop.coordinates = it }

/** The [MenuNavigator] of the surface a row is drawn on, or null outside a menu. */
internal val LocalMenuNavigator = staticCompositionLocalOf<MenuNavigator?> { null }

/** What a select's option is called, for type-ahead, whatever its row draws. */
internal val LocalMenuRowName = compositionLocalOf<String?> { null }

/** Whether a select's row is the option the select holds. */
internal val LocalMenuRowChosen = compositionLocalOf { false }

/**
 * A way into an open menu from outside it: a combobox's field, on Down.
 *
 * [onARow] is whether the focus is on one of the menu's rows — put there by the
 * keyboard, as a pointer leaves it on the panel or outside the menu — which a
 * select reads as it closes, to know whether to take the focus back.
 */
internal class MenuEntrance {
    var enter: (() -> Unit)? = null
    var onARow: Boolean = false
}

/**
 * How long type-ahead waits for the next letter of a word before the next
 * letter starts a new one.
 *
 * Platform menus wait about a second: a Mac's for about that, Windows' list
 * boxes for twice the double-click time. Three-quarters of one is long enough
 * for two letters typed at an ordinary pace and short enough that a second try
 * after a miss is not read as more of the first.
 */
internal val TypeAheadPause: Duration = 750.milliseconds

/** How many frames a stop that is not yet shown is waited for before the keys give up on it. */
private const val StopFrames = 10

/**
 * The keys of one open menu: the arrows, Home and End, a page at a time, and
 * typing. One per menu panel, whichever of its surfaces is on top.
 *
 * ### Which keys, and why each does what it does
 *
 * **Up and Down** move to the row before or after, past those that cannot be
 * chosen — a disabled row, a heading, a rule — and **go round** from the last
 * row to the first and back. A menu on the Mac goes round, a select's list there
 * is the same menu, and WAI-ARIA's menu pattern goes round; Windows' list boxes
 * stop at the ends. Going round is the one of those that never leaves a key
 * doing nothing, and one rule for every menu in the library, short or long,
 * since they share this panel. Home and End are the way to an end.
 *
 * **Home and End** go to the first and last rows that can be chosen; **Page Up
 * and Page Down** a list's height less a row back or on, stopping at the ends.
 *
 * **Typing** goes to the next row whose words begin with what was typed, as
 * every platform's menus do. Letters typed within [TypeAheadPause] of each other
 * are one word, so "ma" finds *Madagascar* rather than an *M* and then an *A*;
 * the same letter again goes on to the next row beginning with it. A space is a
 * letter while a word is under way and picks the row otherwise. A combobox's
 * list hands typing to its field instead: see [typeInto].
 *
 * From nothing focused — a menu opened with a pointer — the first arrow goes to
 * the option a select holds; without one, Down goes to the first row and Up to
 * the last.
 */
internal class MenuKeys {
    lateinit var scope: CoroutineScope

    /**
     * Folds a row's words and what is typed alike, so `zu` finds *Zürich*: the
     * reader's locale's own folding, set by the panel.
     */
    var fold: (String) -> String = { it }

    /** Where typing goes instead of type-ahead: a combobox's field. */
    var typeInto: ((String) -> Unit)? = null

    /**
     * Makes the input the keyboard's, so the row a letter went to shows its
     * ring. The arrows do that as they are pressed; a letter is typing to the
     * tracker, which draws no rings for typing.
     */
    var showFocus: () -> Unit = {}

    private var typed = ""
    private var forget: Job? = null

    /** A move still waiting for its row to be shown, which the next key carries on from. */
    private var pending: Job? = null
    private var pendingIndex = -1

    fun handle(event: KeyEvent, navigator: MenuNavigator): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false
        val stops = stopsOf(navigator)
        return when (event.key) {
            Key.DirectionDown -> stops.size > 0 && go(stops, step(stops, 1))
            Key.DirectionUp -> stops.size > 0 && go(stops, step(stops, -1))
            Key.MoveHome -> stops.size > 0 && go(stops, stops.nearest(0, 1))
            Key.MoveEnd -> stops.size > 0 && go(stops, stops.nearest(stops.size - 1, -1))
            Key.PageDown -> stops.size > 0 && go(stops, page(stops, 1))
            Key.PageUp -> stops.size > 0 && go(stops, page(stops, -1))
            else -> {
                val letter = letterOf(event) ?: return false
                val into = typeInto
                when {
                    into != null -> {
                        into(letter.toString())
                        true
                    }
                    // A space starts no word: it picks the row.
                    letter == ' ' && typed.isEmpty() -> false
                    else -> {
                        typeAhead(letter)
                        val found = find(stops, typed)
                        if (found >= 0) {
                            showFocus()
                            go(stops, found)
                        }
                        // Kept even when nothing matches: there is nothing else for a letter to do here.
                        true
                    }
                }
            }
        }
    }

    /**
     * Into the menu from outside it or as it opens: to the option a select
     * holds, or the first row that can be chosen.
     */
    fun enter(navigator: MenuNavigator) {
        pending?.cancel()
        pendingIndex = -1
        pending = scope.launch {
            repeat(StopFrames) {
                val stops = stopsOf(navigator)
                if (stops.size > 0) {
                    val target = stops.chosen.takeIf { it >= 0 && stops.enabled(it) } ?: stops.nearest(0, 1)
                    if (target < 0) return@launch
                    if (stops.focusNow(target)) return@launch
                    stops.bringIntoView(target)
                }
                withFrameNanos { }
            }
        }
    }

    private fun typeAhead(letter: Char) {
        typed += letter
        forget?.cancel()
        forget = scope.launch {
            delay(TypeAheadPause)
            typed = ""
        }
    }

    /** The row [by] rows on from the focused one, round the ends; or where to start. */
    private fun step(stops: Stops, by: Int): Int {
        val from = current(stops)
        if (from < 0) {
            val chosen = stops.chosen
            return if (chosen >= 0 && stops.enabled(chosen)) chosen else stops.nearest(if (by > 0) 0 else stops.size - 1, by)
        }
        for (n in 1..stops.size) {
            val index = (from + by * n).mod(stops.size)
            if (stops.enabled(index)) return index
        }
        return -1
    }

    /** A list's height less a row from the focused row, stopping at the ends. */
    private fun page(stops: Stops, direction: Int): Int {
        val from = current(stops)
        if (from < 0) return step(stops, direction)
        val target = (from + direction * stops.page()).coerceIn(0, stops.size - 1)
        return stops.nearest(target, direction).takeIf { it >= 0 } ?: stops.nearest(target, -direction)
    }

    /**
     * The next row beginning with [word]. A word of one letter, or of one letter
     * over and over, looks from the row after the focused one, so the same letter
     * again goes on; a longer word looks from the focused row, which it may
     * still describe.
     */
    private fun find(stops: Stops, word: String): Int {
        val repeated = word.all { it == word[0] }
        val prefix = fold(if (repeated) word.substring(0, 1) else word)
        val from = current(stops).let { if (it < 0) 0 else if (repeated) it + 1 else it }
        for (n in 0 until stops.size) {
            val index = (from + n).mod(stops.size)
            val name = stops.name(index) ?: continue
            if (stops.enabled(index) && fold(name).startsWith(prefix)) return index
        }
        return -1
    }

    private fun current(stops: Stops): Int =
        if (pending?.isActive == true && pendingIndex >= 0) pendingIndex else stops.focused

    private fun go(stops: Stops, index: Int): Boolean {
        if (index < 0) return true
        pending?.cancel()
        pendingIndex = -1
        if (stops.focusNow(index)) return true
        // Not shown yet: brought into view, and focused once it is composed.
        pendingIndex = index
        pending = scope.launch {
            stops.bringIntoView(index)
            repeat(StopFrames) {
                withFrameNanos { }
                if (stops.focusNow(index)) return@launch
            }
        }
        return true
    }

    private fun stopsOf(navigator: MenuNavigator): Stops =
        navigator.lazy?.let { LazyStops(navigator, it) } ?: ColumnStops(navigator)
}

/** A key's letter, or null for a key that types none. */
private fun letterOf(event: KeyEvent): Char? {
    if (event.key == Key.Spacebar) return ' '
    val code = event.utf16CodePoint
    if (code <= 0 || code > Char.MAX_VALUE.code) return null
    val letter = code.toChar()
    return letter.takeUnless { it.isISOControl() || it.isSurrogate() }
}

/** The stops of a surface in the order they are drawn, composed or not. */
private interface Stops {
    val size: Int
    fun enabled(index: Int): Boolean
    fun name(index: Int): String?

    /** The focused stop, or -1. */
    val focused: Int

    /** The option the select holds, or -1. */
    val chosen: Int

    /** How many rows a page is. */
    fun page(): Int

    /** Focuses the stop if it is composed and shown, and says whether it did. */
    fun focusNow(index: Int): Boolean

    /** Scrolls the stop into view, for [focusNow] to find it composed. */
    suspend fun bringIntoView(index: Int)

    /** The first stop that can be chosen from [index] on in [direction], or -1. */
    fun nearest(index: Int, direction: Int): Int {
        var at = index
        while (at in 0 until size) {
            if (enabled(at)) return at
            at += direction
        }
        return -1
    }
}

/**
 * A menu composed all at once: every stop is there, in the order of where it
 * sits. A focused row brings itself into view in the column's scroll.
 */
private class ColumnStops(navigator: MenuNavigator) : Stops {
    private val scroll = navigator.scroll
    private val stops = navigator.stops
        .filter { it.coordinates?.isAttached == true }
        .sortedBy { it.coordinates!!.positionInRoot().y }

    override val size: Int get() = stops.size
    override fun enabled(index: Int): Boolean = stops[index].enabled
    override fun name(index: Int): String? = stops[index].name
    override val focused: Int get() = stops.indexOfFirst { it.focused }
    override val chosen: Int get() = stops.indexOfFirst { it.chosen }

    override fun page(): Int {
        val viewport = scroll?.viewportSize ?: 0
        val row = stops.map { it.coordinates!!.size.height }.average()
        if (viewport <= 0 || row <= 0.0) return stops.size
        return (viewport / row).toInt().minus(1).coerceAtLeast(1)
    }

    override fun focusNow(index: Int): Boolean = runCatching { stops[index].requester.requestFocus() }.getOrDefault(false)

    override suspend fun bringIntoView(index: Int) = Unit
}

/**
 * A long list, composed as it is shown: the stops are its rows, all of them, and
 * only those on screen are there to focus. One that is not is scrolled to — to
 * the top of the list going back, to the foot going on — and focused when it is
 * composed, which brings the rest of it into view.
 */
private class LazyStops(private val navigator: MenuNavigator, list: LazyMenuList) : Stops {
    private val state = list.state
    private val rows = list.rows

    override val size: Int get() = rows.count
    override fun enabled(index: Int): Boolean = rows.enabled[index]
    override fun name(index: Int): String? = rows.names[index]
    override val focused: Int get() = navigator.stops.firstOrNull { it.focused }?.place ?: -1
    override val chosen: Int get() = rows.chosen

    override fun page(): Int {
        val info = state.layoutInfo
        val whole = info.visibleItemsInfo.count {
            it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset
        }
        return (whole - 1).coerceAtLeast(1)
    }

    override fun focusNow(index: Int): Boolean {
        if (state.layoutInfo.visibleItemsInfo.none { it.index == index }) return false
        val stop = navigator.stops.firstOrNull { it.place == index && it.coordinates?.isAttached == true } ?: return false
        return runCatching { stop.requester.requestFocus() }.getOrDefault(false)
    }

    override suspend fun bringIntoView(index: Int) {
        val shown = state.layoutInfo.visibleItemsInfo
        // Not laid out yet: the list is about to show the place it opens at.
        if (shown.isEmpty() || shown.any { it.index == index }) return
        val first = shown.first().index
        if (index < first) {
            state.scrollToItem(index)
        } else {
            state.scrollToItem((index - page()).coerceAtLeast(0))
        }
    }
}

/**
 * Lays a menu's column out top to bottom, as [Arrangement.Top] does, and on its
 * first layout scrolls it so that the option its select holds is in the middle
 * of the list — before it is first drawn, so a select opens at its value rather
 * than at the top and then jumping to it.
 *
 * In the arrangement because that is the one place the column says where each of
 * its rows goes before it places them. The scroller measures the column, and
 * reads its offset only as it places it, after.
 *
 * [rows] is how many of the column's children, at its end, are a select's
 * options, and [chosen] which of those the select holds, or -1.
 */
internal class OpensAtChoice(
    private val scroll: ScrollState,
    private val navigator: MenuNavigator,
) : Arrangement.Vertical {
    var rows: Int = 0
    var chosen: Int = -1

    /** The list's height, which the column, measured in a scroller, is not told. */
    var viewport: Int = 0

    /** The column's padding above its first row. */
    var top: Int = 0

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        with(Arrangement.Top) { arrange(totalSize, sizes, outPositions) }
        if (navigator.opened || chosen < 0 || viewport <= 0) return
        val child = sizes.size - rows + chosen
        if (child !in sizes.indices) return
        val middle = top + outPositions[child] + sizes[child] / 2
        val target = (middle - viewport / 2).coerceAtLeast(0)
        // The scroller has not been told how far it can go yet on the first
        // layout, and clamps the offset to that as it places the column.
        scroll.dispatchRawDelta((target - scroll.value).toFloat())
    }
}
