package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ScrollbarDefaults
import io.kontour.ui.components.list.scrollbar
import io.kontour.ui.foundation.LocalTextStyle
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Theme

/**
 * The options of a select's menu, one row each, for the menu to compose as it
 * shows them rather than all at once.
 *
 * Built only while the menu is open: a select of two hundred countries has no
 * business naming them all each time its closed field recomposes.
 *
 * @param keys One per row, saveable, and unique: the row's place in the whole
 *   list of options, which a filtered list keeps.
 * @param labels What each row says, which the menu's width is measured from —
 *   or null for a row a slot draws, which says nothing about how wide it is.
 * @param furniture Which rows carry the same things beside their words, an icon
 *   or a tick: the widest words in each of these is the widest row of it.
 * @param names What each row is called, which typing in the menu finds it by:
 *   its option's label, whether its words or a slot draw it.
 * @param enabled Whether each row can be chosen, which the keys pass over if not.
 * @param chosen The row the select holds, which the menu opens at, or -1.
 */
internal class MenuRows(
    val keys: List<Any>,
    val labels: List<String?>,
    val furniture: List<Int>,
    val names: List<String> = labels.map { it.orEmpty() },
    val enabled: List<Boolean> = List(keys.size) { true },
    val chosen: Int = -1,
    val row: @Composable (index: Int) -> Unit,
) {
    val count: Int get() = keys.size

    /** Row [index], told what it is called and whether it is the one held. */
    @Composable
    fun Item(index: Int) {
        CompositionLocalProvider(
            LocalMenuRowName provides names[index],
            LocalMenuRowChosen provides (index == chosen),
        ) {
            row(index)
        }
    }
}

/**
 * How many options a menu composes all at once. Past this it composes the ones
 * it shows.
 *
 * A menu shows eleven or so rows at a time under a pointer and eight under a
 * finger, and a lazy list composes those and a few beyond, each a little more
 * dearly than a column does. Thirty is about where all of them in a column
 * costs more than what is shown in a lazy list does, measured opening a select
 * on the desktop; it also keeps every menu a reader is likely to meet short of
 * it, drawn exactly as it always was.
 */
internal const val LazyMenuFrom: Int = 30

/**
 * A long list of options in a menu's panel, composed as it is shown.
 *
 * ### Its width
 *
 * A menu is as wide as its widest row, between the field's width and
 * [MenuDefaults.MaxWidth], and a list that composes only what it shows cannot
 * ask its rows. It must not find out as it goes, either: a panel that widened as
 * it was scrolled, under the reader's finger, is worse than either answer.
 *
 * So the width is decided as it opens, and decided the way the column decides
 * it. Under a field at least as wide as a menu goes there is nothing to decide:
 * the field is the width. Otherwise the words of every option are set at once,
 * one line each, in one paragraph per kind of row — one with an icon, one with
 * a tick — which is a fraction of the cost of composing the rows; the row with
 * the widest words of each kind is composed, never placed, never heard, and its
 * width is the panel's. Exact, for a row of words: the column asks the same rows
 * the same question. Rows a slot draws say nothing about their width until they
 * are composed, and their list opens as wide as a menu goes, which is never
 * narrower than the column would have been, and cuts nothing short.
 */
@Composable
internal fun LazyMenuRows(rows: MenuRows, shape: Shape, anchorWidth: Dp) {
    val list = rememberLazyListState()
    // The keys reach the rows this list has not composed through it.
    val navigator = LocalMenuNavigator.current
    SideEffect { navigator?.lazy = LazyMenuList(list, rows) }
    val opening = remember(navigator) { LazyOpening(list, navigator) }
    opening.chosen = rows.chosen
    opening.top = with(LocalDensity.current) { Theme.spacing.xxs.roundToPx() }
    opening.row = with(LocalDensity.current) { TypicalRow.roundToPx() }
    val overscroll = rememberOverscrollEffect()
    val fling = ScrollDefaults.flingBehavior()
    val inset = Theme.spacing.xxs
    // The style a row sets its words in: see `MenuItem`.
    val style = LocalTextStyle.current.merge(Theme.typography.bodyMedium)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val decided = anchorWidth != Dp.Unspecified && anchorWidth >= MenuDefaults.MaxWidth
    val widest = remember(rows.labels, rows.furniture, decided, style, measurer) {
        if (decided) emptyList() else widestRows(rows.labels, rows.furniture, measurer, style)
    }
    val policy = remember(widest == null, rows.count) {
        LazyMenuMeasure(drawn = widest == null, count = rows.count, opening = opening)
    }
    Layout(
        content = {
            Column(Modifier.padding(horizontal = inset)) {
                CompositionLocalProvider(LocalMenuRowSizing provides true) {
                    widest?.forEach { index -> key(rows.keys[index]) { rows.Item(index) } }
                }
            }
            LazyColumn(
                modifier = Modifier
                    // As the column's: see `MenuSurface`.
                    .scrollbar(list, cornerInset = ScrollbarDefaults.cornerInset(shape))
                    .keepsItsScroll(list, overscroll, fling)
                    .onPlaced { navigator?.opened = true },
                state = list,
                contentPadding = PaddingValues(inset),
                flingBehavior = fling,
                overscrollEffect = overscroll,
            ) {
                items(rows.count, key = { rows.keys[it] }) { index ->
                    CompositionLocalProvider(LocalMenuRowPlace provides index) { rows.Item(index) }
                }
            }
        },
        measurePolicy = policy,
    )
}

/**
 * The row with the widest words among each kind of row, or null where a slot
 * draws any of them.
 *
 * One paragraph per kind, a line to a row, set without wrapping: one layout of
 * a few hundred short lines rather than a few hundred layouts.
 */
private fun widestRows(
    labels: List<String?>,
    furniture: List<Int>,
    measurer: TextMeasurer,
    style: TextStyle,
): List<Int>? {
    if (labels.any { it == null }) return null
    return labels.indices.groupBy { furniture[it] }.values.map { kind ->
        // A row is one line, so a break in its words is a space to it.
        val text = kind.joinToString("\n") { labels[it]!!.replace('\n', ' ') }
        val layout = measurer.measure(text, style, softWrap = false, maxLines = Int.MAX_VALUE)
        // A label holding a line separator of its own would put the lines and
        // the rows out of step; measured as slot rows are, it is still never cut.
        if (layout.lineCount != kind.size) return null
        kind[(0 until layout.lineCount).maxBy { layout.getLineRight(it) - layout.getLineLeft(it) }]
    }
}

/**
 * Lays the list out at the width it is given, and answers the panel's questions
 * about its size without asking the list, which cannot answer them.
 *
 * Its width is the widest row's: the sizing rows', or as wide as a menu goes
 * where a slot draws them ([drawn]). Its height, asked only to decide which side
 * of its field it has room on, is at least [count] rows of [ShortestRow], which
 * is all that question needs: a list this long fills a menu either way.
 */
private class LazyMenuMeasure(
    private val drawn: Boolean,
    private val count: Int,
    private val opening: LazyOpening,
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        if (constraints.hasBoundedHeight) opening.open(constraints.maxHeight)
        val list = measurables[1].measure(constraints)
        return layout(list.width, list.height) { list.placeRelative(0, 0) }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        if (drawn) MenuDefaults.MaxWidth.roundToPx() else measurables[0].maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        maxIntrinsicWidth(measurables, height)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        count * ShortestRow.roundToPx()

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        maxIntrinsicHeight(measurables, width)
}

/** Shorter than any menu row: a line of text and its padding. */
private val ShortestRow = 24.dp

/** A row of words under a pointer, about: what the list centres the option it opens at by. */
private val TypicalRow = 36.dp

/**
 * Where a long list opens: with the option its select holds in the middle of it,
 * set before the list is first laid out, so the first frame shows the value and
 * the top of the list is never composed on the way to it.
 *
 * Asked as the list is measured, when its height is known, and until it is first
 * placed; after that the list is the reader's to scroll.
 */
private class LazyOpening(private val state: LazyListState, private val navigator: MenuNavigator?) {
    var chosen: Int = -1
    var top: Int = 0
    var row: Int = 0
    private var askedFor = -1

    fun open(viewport: Int) {
        if (navigator?.opened != false || chosen < 0 || viewport == askedFor) return
        askedFor = viewport
        // Below the middle by a negative offset: the list lays out the rows above.
        state.requestScrollToItem(chosen, -(viewport / 2 - top - row / 2).coerceAtLeast(0))
    }
}
