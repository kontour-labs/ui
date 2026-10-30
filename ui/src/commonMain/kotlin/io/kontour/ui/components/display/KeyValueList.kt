package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutIdParentData
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.theme.Theme

/**
 * Label-and-value rows: the details of one thing, read rather than operated.
 *
 * ```kotlin
 * KeyValueList {
 *     item("Operator", "Transperth")
 *     item("Platform", "2")
 *     item("Fare", "$3.20")
 *     item("Accessible") { +Tabler.Outline.Check }
 * }
 * ```
 *
 * ### Not a [io.kontour.ui.components.selection.SelectionRow]
 *
 * They draw almost the same row, and the difference is the whole reason both
 * exist. A `SelectionRow` is a **control**: it is pressed, announces a role,
 * has a touch target and changes or opens something. A `KeyValueList` row is
 * **text**: no role, no target, nothing to press.
 *
 * Using a setting row for facts gives a screen-reader user a list of buttons
 * that do nothing, which is worse than the visual duplication it saves. If a row
 * here needs to become tappable, it is not one of these — move it out into a
 * `SelectionRow` and leave the rest.
 *
 * ### Each row announces as one thing
 *
 * "Platform" and "2" as separate nodes make the reader hold the label while
 * waiting for the value, and the pairing is the entire content. So each row
 * merges, and reads as "Platform, 2".
 *
 * **Reach for a [Stat] instead** when one figure is the headline. A stat shouts
 * one number; this lists several without ranking them.
 *
 * @param labelWidth The least room the labels get. Every row shares one label
 *   column — as wide as the widest label, from this up to what leaves the values
 *   [KeyValueListDefaults.MinValueWidth] — so the values line up down the list
 *   whatever the labels are: a ragged value column is the thing that makes a
 *   details panel look untidy. A longer label than that wraps, to two lines.
 * @param showDividers A hairline between rows. Off by default: the label column
 *   already aligns them, and a rule per row is a lot of lines for a short list.
 * @param compact For a small container of its own — a tooltip, a popover: the
 *   rows take the text style and colour around them rather than a page's, sit
 *   closer, and the list is as wide as its rows rather than its container.
 *   A chart's tooltip lists its series this way, each with its swatch.
 */
@Composable
inline fun KeyValueList(
    modifier: Modifier = Modifier,
    labelWidth: Dp = KeyValueListDefaults.LabelWidth,
    showDividers: Boolean = false,
    compact: Boolean = false,
    content: @Composable KeyValueScope.() -> Unit,
) {
    // Inline, as `ListGroup` is and for its reason: the builder runs in the
    // caller's restart scope, so a value read in it is never collected into a
    // list nothing draws. See `ListGroupScope`.
    KeyValueRows(KeyValueScope().apply { content() }, modifier, labelWidth, showDividers, compact)
}

/**
 * The call an app built against 0.4.5's inline [KeyValueList] has in it, kept
 * so that it still links.
 */
@PublishedApi
@Composable
internal fun KeyValueRows(scope: KeyValueScope, modifier: Modifier, labelWidth: Dp, showDividers: Boolean) {
    KeyValueRows(scope, modifier, labelWidth, showDividers, compact = false)
}

/** The rows [KeyValueList] collected, laid out. */
@PublishedApi
@Composable
internal fun KeyValueRows(scope: KeyValueScope, modifier: Modifier, labelWidth: Dp, showDividers: Boolean, compact: Boolean) {
    val rows = scope.rows
    val gap = Theme.spacing.sm
    // A compact list is as wide as its rows, so a value always has the room
    // its words take, and there is no column too narrow to be worth having.
    val minValue = if (compact) 0.dp else KeyValueListDefaults.MinValueWidth
    val labelledValue = Theme.strings.labelledValue
    val muted = Theme.colours.contentMuted
    val body = Theme.typography.bodyMedium
    val spacing = Theme.spacing

    val label: @Composable (KeyValueRow, Modifier) -> Unit = { row, slot ->
        if (compact) {
            // A swatch or an icon beside the words, centred on them.
            Row(slot, horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                ContentSlot(maxLines = 2, content = row.label)
            }
        } else {
            ProvideContentColour(muted) {
                ProvideTextStyle(body) {
                    Row(slot) { ContentSlot(maxLines = 2, content = row.label) }
                }
            }
        }
    }
    val value: @Composable (KeyValueRow, Modifier) -> Unit = { row, slot ->
        val cell: @Composable () -> Unit = {
            Row(slot) {
                ContentSlot(
                    iconSize = Theme.sizing.iconSmall,
                    maxLines = 4,
                    content = row.value,
                )
            }
        }
        if (compact) cell() else ProvideTextStyle(body, cell)
    }

    val stackGap = spacing.xxs
    val rowPadding = if (compact) spacing.xxs / 2 else spacing.xs
    val columns = remember { KeyValueColumns() }
    val rowPolicy = remember(columns) { KeyValueRowPolicy(columns) }

    /*
     * **Plain layouts, nothing composed while measuring.** This was a
     * `SubcomposeLayout`, composing every label twice a measure — once to find
     * the widest, once to lay the rows out — and the rows themselves inside the
     * measure. Nothing here needs that: the label column is the widest label's
     * width, which a layout can ask its own children for, and the rows are then
     * laid out with it. Composed while measuring, the rows broke a list read
     * early: a `ListSection` asks its body for `ListRowTop` and `ListRowBottom`
     * as it measures, which places the body there and then, and a body that
     * creates its nodes during measure is not ready to be placed. Reported
     * against 0.4.5, a `KeyValueList` in a `ListSection`; fixed for 0.5.1.
     *
     * Each row is still a node of its own, so a screen reader still hears it as
     * one: "Platform, 2". The list asks a row for its label's width and its
     * value's width through the row's intrinsics — see [KeyValueRowPolicy] —
     * once each a measure, and tells it the column before measuring it.
     */
    Layout(
        modifier = if (compact) modifier else modifier.fillMaxWidth(),
        content = {
            rows.forEachIndexed { index, row ->
                if (showDividers && index > 0) HorizontalDivider()
                Layout(
                    modifier = Modifier
                        .layoutId(KeyValueRowId)
                        .semantics(mergeDescendants = true) {
                            row.spoken?.let { contentDescription = it.say(labelledValue) }
                        }
                        .padding(vertical = rowPadding),
                    content = {
                        label(row, Modifier)
                        value(row, Modifier)
                    },
                    measurePolicy = rowPolicy,
                )
            }
        },
        measurePolicy = remember(columns, labelWidth, minValue, gap, stackGap, compact) {
            KeyValueListPolicy(columns, labelWidth, minValue, gap, stackGap, compact)
        },
    )
}

/**
 * What the list decided this measure, for its rows: the label column's width,
 * whether the pairs stack, and the gaps. Written by [KeyValueListPolicy] before
 * it measures a row, read by [KeyValueRowPolicy] as the row measures. Not
 * state: it changes only inside one measure pass, which is what reads it.
 */
private class KeyValueColumns {
    var column = 0
    var stacked = false
    var gap = 0
    var stackGap = 0
}

/** A row, as against a divider between two. */
private object KeyValueRowId

/** Whether this is a row, as against a divider: by its id, which a measurable and an intrinsic one both carry. */
private val IntrinsicMeasurable.isRow: Boolean
    get() = (parentData as? LayoutIdParentData)?.layoutId == KeyValueRowId

/** The list: the column for every row, then the rows and dividers top to bottom. */
private class KeyValueListPolicy(
    private val columns: KeyValueColumns,
    private val labelWidth: Dp,
    private val minValue: Dp,
    private val gap: Dp,
    private val stackGap: Dp,
    private val compact: Boolean,
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val floor = labelWidth.roundToPx()
        val gapPx = gap.roundToPx()
        val minValuePx = minValue.roundToPx()
        val rows = measurables.filter { it.isRow }
        // Each asked once a measure, and only if it is needed.
        val widestLabel by lazy(LazyThreadSafetyMode.NONE) { rows.maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0 }
        val widestValue by lazy(LazyThreadSafetyMode.NONE) { rows.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0 }
        // As wide as its widest label and widest value side by side: what a
        // compact list takes, and a list in a row that scrolls, or anything
        // else with no width to give; laid out at an unbounded width, it threw.
        fun natural() = maxOf(widestLabel, floor) + gapPx + maxOf(widestValue, minValuePx)
        val available = when {
            !constraints.hasBoundedWidth -> natural()
            compact -> minOf(constraints.maxWidth, natural())
            else -> constraints.maxWidth
        }
        /*
         * Two columns, or one above the other.
         *
         * The label column used to be `widthIn(min = labelWidth)` beside a value
         * that had no weight at all, and the arithmetic of that is unkind:
         * `labelWidth` is 108dp, so anything narrower than about 120dp gave the
         * label the entire row and the value whatever was left, which was
         * nothing. A `Text` measured at nothing still draws its first character,
         * so the value came out as a single column of letters standing **outside
         * the component**, one glyph wide — "T" over "r" over "a" over "n".
         *
         * Below the point where a value column would be worth having, the pair
         * stacks instead. That is what a key and a value do on a narrow screen,
         * and it is the only arrangement where both are readable.
         */
        val stacked = available < floor + gapPx + minValuePx
        /*
         * **One label column for every row**: as wide as the widest label, from
         * `labelWidth` up to what leaves a value its room. It was a floor each
         * row took for itself, so a label longer than the floor pushed its own
         * value out and the values went ragged down the list — the two columns
         * held only while every label fitted, as the demo's did. Measured here,
         * so nobody has to set `labelWidth` for their longest label to get them.
         */
        columns.stacked = stacked
        columns.gap = gapPx
        columns.stackGap = stackGap.roundToPx()
        columns.column = if (stacked) 0 else widestLabel.coerceIn(floor, maxOf(floor, available - gapPx - minValuePx))
        val placeables = measurables.map { it.measure(Constraints(minWidth = 0, maxWidth = available)) }
        val height = placeables.sumOf { it.height }
        return layout(available, constraints.constrainHeight(height)) {
            var y = 0
            placeables.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }

    // What a parent asking how wide the list would be gets: its natural width,
    // which a `SubcomposeLayout` could not answer at all.
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        natural(measurables)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        natural(measurables)

    private fun IntrinsicMeasureScope.natural(measurables: List<IntrinsicMeasurable>): Int {
        val rows = measurables.filter { it.isRow }
        val labels = rows.maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0
        val values = rows.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        return maxOf(labels, labelWidth.roundToPx()) + gap.roundToPx() + maxOf(values, minValue.roundToPx())
    }
}

/**
 * One row: its label at the column's width and its value in the rest, top
 * aligned, or the label above the value when the list stacks — both at the
 * width the list gives it.
 *
 * **Its intrinsic widths are its parts', for the list.** Its min intrinsic
 * width is its label's widest, and its max its value's: the two things the list
 * needs to find its column and its natural width, from a node it can ask. A row
 * is never laid out by anything but [KeyValueListPolicy], so nothing else reads
 * them. Its intrinsic heights are its own, at the column the list last chose.
 */
private class KeyValueRowPolicy(private val columns: KeyValueColumns) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val (label, value) = measurables
        val width = constraints.maxWidth
        if (columns.stacked) {
            val above = label.measure(Constraints(maxWidth = width))
            val below = value.measure(Constraints(maxWidth = width))
            val height = above.height + columns.stackGap + below.height
            return layout(width, constraints.constrainHeight(height)) {
                above.placeRelative(0, 0)
                below.placeRelative(0, above.height + columns.stackGap)
            }
        }
        val column = columns.column.coerceAtMost(width)
        val rest = (width - column - columns.gap).coerceAtLeast(0)
        val start = label.measure(Constraints.fixedWidth(column).copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val end = value.measure(Constraints.fixedWidth(rest).copy(minHeight = 0, maxHeight = Constraints.Infinity))
        return layout(width, constraints.constrainHeight(maxOf(start.height, end.height))) {
            start.placeRelative(0, 0)
            end.placeRelative(column + columns.gap, 0)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables[0].maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables[1].maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        heightAt(measurables, width) { w -> minIntrinsicHeight(w) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        heightAt(measurables, width) { w -> maxIntrinsicHeight(w) }

    private inline fun heightAt(measurables: List<IntrinsicMeasurable>, width: Int, of: IntrinsicMeasurable.(Int) -> Int): Int {
        val (label, value) = measurables
        if (columns.stacked) return label.of(width) + columns.stackGap + value.of(width)
        val column = columns.column.coerceAtMost(width)
        return maxOf(label.of(column), value.of((width - column - columns.gap).coerceAtLeast(0)))
    }
}

/** One collected row. */
internal class KeyValueRow(
    val label: @Composable ContentScope.() -> Unit,
    val value: @Composable ContentScope.() -> Unit,
    val spoken: Spoken?,
)

/**
 * What a row says, kept in its parts until composition, where the reader's
 * `Strings.labelledValue` joins them: "Operator, Transperth" in English, and
 * whatever a translation's grammar makes of a label and its value elsewhere.
 * A row with only an [announcement][KeyValueScope.item] says that alone.
 */
internal class Spoken(val label: String?, val value: String) {
    fun say(labelledValue: (String, String) -> String): String = if (label == null) value else labelledValue(label, value)
}

/**
 * Collects the rows.
 *
 * A builder rather than emitting in place, matching [io.kontour.ui.components.list.ListGroupScope]: the label
 * column is one width shared by every row, and the widest label is not known
 * until they have all been declared.
 *
 * The builder is composable, so a value from `Theme.formats` is read where the
 * row is written — `item("Updated", Theme.formats.date(updated))` — and
 * [KeyValueList] is `inline` so that this is safe, for the reason argued on
 * [io.kontour.ui.components.list.ListGroupScope].
 */
@LayoutScopeMarker
@Stable
class KeyValueScope @PublishedApi internal constructor() {
    internal val rows = mutableListOf<KeyValueRow>()

    /** The common case: two strings. */
    fun item(label: String, value: String) {
        rows += KeyValueRow(
            label = { +label },
            value = { +value },
            spoken = Spoken(label, value),
        )
    }

    /**
     * A value that is not text — a [io.kontour.ui.components.display.Tag], an
     * icon, a `RelativeTimeText` that ticks.
     *
     * Pass [announcement] whenever the content does not speak for itself. A tick
     * icon in an "Accessible" row announces as "Accessible" and nothing else,
     * which reads as a row with a missing value.
     */
    fun item(
        label: String,
        announcement: String? = null,
        value: @Composable ContentScope.() -> Unit,
    ) {
        rows += KeyValueRow(
            label = { +label },
            value = value,
            spoken = announcement?.let { Spoken(label, it) },
        )
    }

    /**
     * Both regions as content, for a label that is more than a word.
     *
     * The label column is as wide as the widest label, so a label is asked how
     * wide it would be: one with a lazy row or `BoxWithConstraints` inside it,
     * which cannot say, throws.
     */
    fun item(
        label: @Composable ContentScope.() -> Unit,
        announcement: String? = null,
        value: @Composable ContentScope.() -> Unit,
    ) {
        rows += KeyValueRow(label = label, value = value, spoken = announcement?.let { Spoken(null, it) })
    }
}

/** What a [KeyValueList] takes by default. */
object KeyValueListDefaults {
    /**
     * The label column's floor.
     *
     * Wide enough for "Operator" and "Accessible" at the body size, which is
     * where transit detail labels sit. It is a minimum rather than a fixed
     * width so a longer label pushes the column rather than wrapping to two
     * lines while the rest of the row sits empty.
     */
    val LabelWidth: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.keyValueLabelWidth

    /**
     * The narrowest a value column is worth having.
     *
     * Below this the row stacks — label above value — rather than squeezing the
     * value into a strip. Roughly "Transperth" at the body size: enough for a
     * short answer on one line, which is what most of these are.
     */
    val MinValueWidth: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.keyValueMinValueWidth
}
