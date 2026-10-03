package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.theme.Theme

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
 * [GroupPosition] is index arithmetic at the call site, and getting it wrong
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
 * `MenuScope` and `NavScope` do take `@Composable` builders, and the
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

    internal val rows = mutableListOf<@Composable (GroupPosition) -> Unit>()

    /**
     * One row.
     *
     * No `modifier`, like every shorthand in the library — a row that needs one
     * has outgrown this, and [ListItem] is one line away through [item].
     */
    fun item(
        label: String,
        enabled: Boolean = true,
        supporting: String? = null,
        overline: String? = null,
        icon: ImageVector? = null,
        selected: Boolean = false,
        role: Role = Role.Button,
        trailing: (@Composable ContentScope.() -> Unit)? = null,
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
                if (trailing != null) this.trailing(trailing)
            }
        }
    }

    /**
     * One settings row — an icon, a label and the value it is currently set to —
     * as a [SelectionRow] that opens the choice: pressed, it goes to where the
     * value is picked.
     */
    fun setting(
        label: String,
        value: String? = null,
        supporting: String? = null,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
    ) {
        rows += { position ->
            SelectionRow(
                onClick = onClick,
                enabled = enabled,
                position = position,
            ) {
                +label
                if (supporting != null) supporting { +supporting }
                if (icon != null) leading { +icon }
                if (value != null) trailing { +value }
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
    fun item(content: @Composable (GroupPosition) -> Unit) {
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
        rows.forEachIndexed { index, row -> row(GroupPosition.of(index, rows.size)) }
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
                row(GroupPosition.of(start + offset, total))
            }
        }
    }
}

/**
 * A titled group of rows inside a `LazyColumn`: [ListSection], lazily.
 *
 * ```kotlin
 * LazyColumn(state = list) {
 *     indexed.groups.forEach { group ->
 *         listSection(group.items, key = { it.id }, title = { +group.label }) { stop, position ->
 *             ListItem(onClick = { open(stop) }, position = position) { +stop.name }
 *         }
 *     }
 * }
 * ```
 *
 * The section draws everything a grouped list needs between its rows, so the
 * call site has none of it to get wrong:
 *
 * - **Each row's [GroupPosition]**, handed to [content] with the item, so the
 *   group's outer corners round and the seams between its rows stay square.
 * - **The hairline between rows**, [spacing], the section's own. A lazy list
 *   arranges every item one way, and a sectioned list needs two gaps — the
 *   hairline inside a group and the space before the next header — so leave
 *   the `LazyColumn`'s `verticalArrangement` alone; it would be added to both.
 * - **The space before the section.** A [title] is a [SectionHeader], whose own
 *   top padding is the gap after the section above. A section without one
 *   starts the same distance below it, so two groups never run together and
 *   read as one with the wrong corners.
 * - **A [footer]** under the rows, as [ListSection] draws it.
 * - **Rows that move rather than jump** when a filter adds or takes some away:
 *   each item is placed with `animateItem`, which wants the item's root, and
 *   the section wraps every row in a box of its own to space it, so only the
 *   section can put it there. Placement does not move under reduced motion; the
 *   fades stay. Give [key] for the moves to follow each row.
 *
 * An empty section draws nothing, its header and footer included.
 *
 * ### The items it adds
 *
 * One for the header, when there is a [title] or an [action]; one for each of
 * [items]; one for the [footer], when there is one. That is what
 * [indexedBy] counts for an [IndexScroller] — a header and then the rows — so a
 * section per group, without a footer, is where the rail's letters land.
 *
 * @param key A stable key for each row, as `LazyColumn`'s `items` takes.
 * @param sectionKey A key for the section's header and footer, for a section
 *   that comes and goes — a "Pinned" that empties — to animate as one. Any value
 *   unique to the section; it is wrapped, so it cannot collide with a row's.
 * @param spacing The gap between the rows, and under the header. Defaults to
 *   `ListItemDefaults.Spacing`, the hairline [ListGroup] uses.
 * @param content One row: the item, and where it sits in the group.
 */
fun <T> LazyListScope.listSection(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    sectionKey: Any? = null,
    title: (@Composable ContentScope.() -> Unit)? = null,
    supporting: (@Composable ContentScope.() -> Unit)? = null,
    footer: (@Composable ContentScope.() -> Unit)? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    spacing: Dp = Dp.Unspecified,
    content: @Composable (item: T, position: GroupPosition) -> Unit,
) {
    if (items.isEmpty()) return
    val header = title != null || action != null

    if (header) {
        item(key = sectionKey?.let { SectionPart(it, SectionPart.Header) }, contentType = "listSectionHeader") {
            SectionHeader(
                modifier = sectionItem(),
                supporting = supporting,
                action = action,
                title = title ?: {},
            )
        }
    }
    val rowKey = key?.let { k -> { index: Int -> k(items[index]) } }
    items(items.size, key = rowKey, contentType = { "listSectionRow" }) { index ->
        // Under the header, or after the row above, the hairline; first in a
        // section with no header, the space a header would have given it.
        val above = when {
            index > 0 || header -> spacing.orListSpacing()
            else -> Theme.spacing.md
        }
        Box(sectionItem().fillMaxWidth().padding(top = above)) {
            content(items[index], GroupPosition.of(index, items.size))
        }
    }
    if (footer != null) {
        item(key = sectionKey?.let { SectionPart(it, SectionPart.Footer) }, contentType = "listSectionFooter") {
            SectionFooter(sectionItem().padding(top = spacing.orListSpacing()), content = footer)
        }
    }
}

/** A section's header or footer key, kept apart from any row's. */
private data class SectionPart(val section: Any, val part: Int) {
    companion object {
        const val Header = 0
        const val Footer = 1
    }
}

@Composable
private fun Dp.orListSpacing(): Dp = if (isSpecified) this else ListItemDefaults.Spacing

/** Placed with `animateItem`, without the slide under reduced motion. */
@Composable
private fun LazyItemScope.sectionItem(): Modifier =
    if (Theme.motion.reduceMotion) Modifier.animateItem(placementSpec = null) else Modifier.animateItem()
