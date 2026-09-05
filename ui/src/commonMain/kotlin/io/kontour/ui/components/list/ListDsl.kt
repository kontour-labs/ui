package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.LayoutScopeMarker
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
 * **What it is actually for is the corners.** Written by hand, each row's
 * [ListItemPosition] is index arithmetic at the call site, and getting it wrong
 * gives a group with two rounded rows in the middle of it — which reads as a
 * rendering fault rather than as a mistake. The builder counted the rows, so
 * there is nothing to pass and nothing to get wrong.
 *
 * ### Not composable, on purpose
 *
 * [item] and [setting] do not draw a row where they are called; they add it to
 * a list that [ListGroup] draws afterwards. They have to, because the position
 * of the first row depends on how many come after it, and nothing can know that
 * until the builder has finished.
 *
 * The builder lambda is plain Kotlin. It still runs inside composition, so
 * `if (signedIn) item(…)` works and reading state in the condition recomposes
 * the group. What you cannot do is read `Theme` or call a composable directly
 * in the builder. Use [item] for that: it takes the whole `@Composable` and
 * hands you the position.
 *
 * ### Why it is not `@Composable`, which was tried
 *
 * `MenuScope` and `NavDrawerScope` do take `@Composable` builders, and the
 * asymmetry looks like drift. It is not. Those two *emit* — they are
 * `ColumnScope`s, and every item they declare draws where it is declared. A
 * collecting scope cannot follow them.
 *
 * Annotating this lambda `@Composable` makes it its own restartable group, and
 * the collection then happens in a different recompose scope from the
 * consumption. The catalog's grouped rows are wired to
 * `onClick = { current.value = index }` and read `current.value` back for
 * `selected`. With the annotation on: the tap fires, the state changes, the
 * builder's own scope restarts and appends three more rows to a plain
 * `mutableListOf` that nothing observes, and [ListGroup] — which read no
 * changed state itself — never re-runs. The rows keep drawing at their first
 * values. Three specimens went dead and the page still looked right.
 *
 * Making [rows] a snapshot list does not rescue it; it turns a stale group into
 * a group that clears and rebuilds against a lambda Compose is entitled to
 * skip. Emission and collection are the real fork, and collection is what the
 * position arithmetic needs.
 */
@Stable
@LayoutScopeMarker
class ListGroupScope internal constructor() {

    internal val rows = mutableListOf<@Composable (ListItemPosition) -> Unit>()

    /**
     * One row.
     *
     * No `modifier`, like every shorthand in the library — a row that needs one
     * has outgrown this, and [ListItem] is one line away through [item].
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
     * An overload of [item] rather than its own verb: a group holds items, and
     * whether one is written as a shorthand or as a composable of your own does
     * not change what it is.
     *
     * ```kotlin
     * ListGroup {
     *     item("Notifications") { … }
     *     item { position ->
     *         ListItem(position = position, onClick = ::signOut) { +"Sign out" }
     *     }
     * }
     * ```
     */
    fun item(content: @Composable (ListItemPosition) -> Unit) {
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
 * @param spacing The gap between rows, as a `Dp` rather than an `Arrangement`,
 *   unlike every other spacing parameter in the library.
 *
 *   The rows' facing corners are cut for a *fixed* gap —
 *   `ListItemDefaults.InnerCorner`. An `Arrangement` would let a caller write
 *   `SpaceBetween` on such a group, and the rows would come apart with the
 *   notches still in them.
 *
 *   The default is the hairline that makes a group read as one object; widen it
 *   and the rows read as separate cards.
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
 *     item { SectionHeader { +"Saved places" } }
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
