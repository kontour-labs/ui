package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.ProvideContentColor
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
 * ### Not a [io.kontour.ui.components.list.SettingRow]
 *
 * They draw almost the same row, and the difference is the whole reason both
 * exist. A `SettingRow` is a **control**: it is `clickable`, announces
 * `Role.Button`, has a touch target and opens something. A `KeyValueList` row is
 * **text**: no role, no target, nothing to press.
 *
 * Using a setting row for facts gives a screen-reader user a list of buttons
 * that do nothing, which is worse than the visual duplication it saves. If a row
 * here needs to become tappable, it is not one of these — move it out into a
 * `SettingRow` and leave the rest.
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
 * @param labelWidth How much room the labels get. Fixed rather than intrinsic so
 *   the values line up down the column — a ragged value column is the thing that
 *   makes a details panel look untidy, and intrinsic width re-ragged it every
 *   time the content changed.
 * @param dividers A hairline between rows. Off by default: the label column
 *   already aligns them, and a rule per row is a lot of lines for a short list.
 */
@Composable
fun KeyValueList(
    modifier: Modifier = Modifier,
    labelWidth: Dp = KeyValueListDefaults.LabelWidth,
    dividers: Boolean = false,
    content: KeyValueScope.() -> Unit,
) {
    val rows = keyValueRows(content)

    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (dividers && index > 0) HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        row.spoken?.let { contentDescription = it }
                    }
                    .padding(vertical = Theme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                ProvideContentColor(Theme.colors.contentMuted) {
                    ProvideTextStyle(Theme.typography.bodyMedium) {
                        Row(Modifier.widthIn(min = labelWidth)) {
                            ContentSlot(maxLines = 2, content = row.label)
                        }
                    }
                }
                ProvideTextStyle(Theme.typography.bodyMedium) {
                    Row(Modifier.fillMaxWidth()) {
                        ContentSlot(
                            iconSize = Theme.sizing.iconSmall,
                            maxLines = 4,
                            content = row.value,
                        )
                    }
                }
            }
        }
    }
}

/** One collected row. */
internal class KeyValueRow(
    val label: @Composable ContentScope.() -> Unit,
    val value: @Composable ContentScope.() -> Unit,
    val spoken: String?,
)

/**
 * Collects the rows.
 *
 * A builder rather than emitting in place, matching [io.kontour.ui.components.list.ListGroupScope]: the label
 * column is one width shared by every row, and the widest label is not known
 * until they have all been declared.
 *
 * Plain Kotlin rather than `@Composable`, for the reason argued at length on
 * [io.kontour.ui.components.list.ListGroupScope] — a collecting scope with a
 * composable builder collects in
 * one recompose scope and is consumed in another.
 */
@LayoutScopeMarker
@Stable
class KeyValueScope internal constructor() {
    internal val rows = mutableListOf<KeyValueRow>()

    /** The common case: two strings. */
    fun item(label: String, value: String) {
        rows += KeyValueRow(
            label = { +label },
            value = { +value },
            spoken = "$label, $value",
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
            spoken = announcement?.let { "$label, $it" },
        )
    }

    /** Both regions as content, for a label that is more than a word. */
    fun item(
        label: @Composable ContentScope.() -> Unit,
        announcement: String? = null,
        value: @Composable ContentScope.() -> Unit,
    ) {
        rows += KeyValueRow(label = label, value = value, spoken = announcement)
    }
}

internal fun keyValueRows(content: KeyValueScope.() -> Unit): List<KeyValueRow> =
    KeyValueScope().apply(content).rows

object KeyValueListDefaults {
    /**
     * The label column's floor.
     *
     * Wide enough for "Operator" and "Accessible" at the body size, which is
     * where transit detail labels sit. It is a minimum rather than a fixed
     * width so a longer label pushes the column rather than wrapping to two
     * lines while the rest of the row sits empty.
     */
    val LabelWidth: Dp = 108.dp
}
