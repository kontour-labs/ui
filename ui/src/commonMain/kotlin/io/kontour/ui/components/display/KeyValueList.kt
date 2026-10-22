package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
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

    SubcomposeLayout(if (compact) modifier else modifier.fillMaxWidth()) { constraints ->
        val floor = labelWidth.roundToPx()
        val gapPx = gap.roundToPx()
        // As wide as its widest label and widest value side by side: what a
        // compact list takes, and a list in a row that scrolls, or anything
        // else with no width to give; laid out at an unbounded width, it threw.
        fun natural(): Int {
            val labels = subcompose("natural labels") { rows.forEach { row -> label(row, Measured) } }
                .maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
            val values = subcompose("natural values") { rows.forEach { row -> value(row, Measured) } }
                .maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
            return maxOf(labels, floor) + gapPx + maxOf(values, minValue.roundToPx())
        }
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
        val stacked = available < floor + gapPx + minValue.roundToPx()
        /*
         * **One label column for every row**: as wide as the widest label, from
         * `labelWidth` up to what leaves a value its room. It was a floor each
         * row took for itself, so a label longer than the floor pushed its own
         * value out and the values went ragged down the list — the two columns
         * held only while every label fitted, as the demo's did. Measured here,
         * so nobody has to set `labelWidth` for their longest label to get them.
         */
        val column = if (stacked) {
            0
        } else {
            val widest = maxOf(floor, available - gapPx - minValue.roundToPx())
            val natural = subcompose("labels") { rows.forEach { row -> label(row, Measured) } }
                .maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
            natural.coerceIn(floor, widest)
        }
        val columnDp = column.toDp()
        val list = subcompose("rows") {
            Column(Modifier.fillMaxWidth()) {
                rows.forEachIndexed { index, row ->
                    if (showDividers && index > 0) HorizontalDivider()

                    val rowModifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            row.spoken?.let { contentDescription = it.say(labelledValue) }
                        }
                        .padding(vertical = if (compact) spacing.xxs / 2 else spacing.xs)

                    if (stacked) {
                        Column(
                            modifier = rowModifier,
                            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                        ) {
                            label(row, Modifier.fillMaxWidth())
                            value(row, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            modifier = rowModifier,
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalAlignment = Alignment.Top,
                        ) {
                            label(row, Modifier.width(columnDp))
                            value(row, Modifier.weight(1f))
                        }
                    }
                }
            }
        }.map { it.measure(constraints.copy(minWidth = 0, maxWidth = available, minHeight = 0)) }
        val height = list.sumOf { it.height }
        layout(available, constraints.constrainHeight(height)) {
            var y = 0
            list.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

/**
 * A copy composed only to be measured, never placed: nothing a screen reader
 * should find, or it reads every label twice.
 */
private val Measured = Modifier.clearAndSetSemantics { }

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
