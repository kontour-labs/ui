package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import io.kontour.ui.components.list.ListItemDefaults

/**
 * The shorthand for a group of rows.
 *
 * ```kotlin
 * ListGroup {
 *     item("Perth Underground", supporting = "Platform 2 · Joondalup line") { open(it) }
 *     item("Elizabeth Quay", supporting = "Stand C · Route 950") { open(it) }
 *     item("McIver", supporting = "Platform 1 · Midland line") { open(it) }
 * }
 * ```
 *
 * ### What it is actually for
 *
 * The corners. A group of rows is one object with rows in it, which means the
 * first row rounds its top, the last rounds its bottom and the ones between are
 * square — and by hand that is index arithmetic at every call site:
 *
 * ```kotlin
 * val positions = listPositions(stops.size)      // the bit people forget
 * stops.forEachIndexed { index, stop ->
 *     ListItem(label = stop.name, position = positions[index], …)
 * }
 * ```
 *
 * Get it wrong and you get a group with two rounded rows in the middle of it,
 * which reads as a rendering fault rather than as a mistake. The builder knows
 * how many rows there are because it counted them, so there is nothing to pass
 * and nothing to get wrong.
 *
 * ### Not composable, on purpose
 *
 * The builder lambda is plain Kotlin — it *collects* rows rather than emitting
 * them, because the position of the first row depends on how many come after it.
 * It still runs inside composition, so `if (signedIn) item(…)` works and reading
 * state in the condition recomposes the group.
 *
 * What you cannot do is read `Theme` or call a composable directly in the
 * builder. Use [row] for that: it takes the whole `@Composable` and hands you
 * the position.
 */
@Stable
class ListGroupScope internal constructor() {

    internal val rows = mutableListOf<@Composable (ListItemPosition) -> Unit>()

    /**
     * One row.
     *
     * No `modifier`, like every shorthand in the library — a row that needs one
     * has outgrown this, and [ListItem] is one line away through [row].
     */
    fun item(
        label: String,
        supporting: String? = null,
        overline: String? = null,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        selected: Boolean = false,
        role: Role = Role.Button,
        trailing: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)? = null,
    ) {
        rows += { position ->
            ListItem(
                enabled = enabled,
                onClick = onClick,
                selected = selected,
                role = role,
                position = position,
            ) {
                +label
                if (overline != null) overline { +overline }
                if (supporting != null) supporting { +supporting }
                if (icon != null) leading { +icon }
                if (trailing != null) trailing { trailing() }
            }
        }
    }

    /** One settings row — an icon, a label and the value it is currently set to. */
    fun setting(
        label: String,
        value: String? = null,
        supporting: String? = null,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
    ) {
        rows += { position ->
            SettingRow(
                enabled = enabled,
                onClick = onClick,
                position = position,
            ) {
                +label
                if (supporting != null) supporting { +supporting }
                if (icon != null) leading { +icon }
                if (value != null) trailing { settingValue(value) }
            }
        }
    }

    /**
     * A row of your own, given where it sits.
     *
     * The escape hatch, and the reason the shorthands can stay small. Anything
     * the library draws with a `position` parameter belongs here.
     *
     * ```kotlin
     * ListGroup {
     *     item("Notifications") { … }
     *     row { position ->
     *         ListItem(label = "Sign out", position = position, onClick = ::signOut)
     *     }
     * }
     * ```
     */
    fun row(content: @Composable (ListItemPosition) -> Unit) {
        rows += content
    }
}

/**
 * Renders a group of rows with their corners already worked out.
 *
 * For a group short enough to build eagerly — a settings screen, a handful of
 * favourites. Use [listGroup] inside a `LazyColumn` when the list is long enough
 * that composing all of it would be wasteful.
 *
 * @param spacing The gap between rows. The default is the hairline that makes a
 *   group read as one object; widen it and the rows read as separate cards.
 */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    spacing: Dp = ListItemDefaults.Spacing,
    content: ListGroupScope.() -> Unit,
) {
    val scope = ListGroupScope().apply(content)
    val rows = scope.rows

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        rows.forEachIndexed { index, row -> row(ListItemPosition.of(index, rows.size)) }
    }
}

/**
 * The same group, inside a `LazyColumn`.
 *
 * ```kotlin
 * LazyColumn(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
 *     item { SectionHeader("Saved places") }
 *     listGroup(savedPlaces, key = { it.id }) { place ->
 *         item(place.name, supporting = place.detail) { open(place) }
 *     }
 * }
 * ```
 *
 * The spacing between rows is the `LazyColumn`'s own `verticalArrangement`, not
 * something this can set — a lazy list arranges its children and there is no
 * container here to do it instead.
 *
 * ### It builds every element's rows up front
 *
 * Not the *content* — that stays lazy, and a row scrolled off screen is not
 * composed. But the builder runs for every element immediately, because the
 * first row cannot know it is first until something has counted the rest.
 *
 * That allocates one lambda per row, which is the right trade for the lists this
 * is for and the wrong one for a list of thousands. A group of thousands of rows
 * with rounded ends is not a thing anyone wants to look at, so the constraint and
 * the design agree.
 */
fun <T> LazyListScope.listGroup(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    content: ListGroupScope.(T) -> Unit,
) {
    val perElement = items.map { element -> ListGroupScope().apply { content(element) }.rows }
    val total = perElement.sumOf { it.size }

    var emitted = 0
    items.forEachIndexed { index, element ->
        val rows = perElement[index]
        // Captured now, not read later: `emitted` keeps moving after this item
        // is declared, and a lambda that read it would give every row the last
        // element's offset.
        val start = emitted
        emitted += rows.size

        item(key = key?.invoke(element), contentType = "listGroupRow") {
            rows.forEachIndexed { offset, row ->
                row(ListItemPosition.of(start + offset, total))
            }
        }
    }
}
