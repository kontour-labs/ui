package io.kontour.ui.nav

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.ui.foundation.HorizontalDivider

/**
 * The shorthand for a drawer's destinations.
 *
 * ```kotlin
 * NavDrawer(header = { AppMark() }) {
 *     destination("Home", Tabler.Outline.Home, selected = tab == Tab.Home) { go(Tab.Home) }
 *     destination("Map", Tabler.Outline.Map, selected = tab == Tab.Map) { go(Tab.Map) }
 *
 *     section("Saved") {
 *         destination("Favourites", Tabler.Outline.Star, badge = 3) { go(Tab.Favourites) }
 *         group("Routes", expanded = routesOpen, onExpandedChange = { routesOpen = it }) {
 *             destination("950", selected = route == "950") { go("950") }
 *             destination("998", selected = route == "998") { go("998") }
 *         }
 *     }
 * }
 * ```
 *
 * ### It does not replace anything
 *
 * `NavDrawerScope` **extends** `ColumnScope`, which is what a drawer's content
 * lambda already was, so [NavDrawerItem], [NavDrawerSection], [NavDrawerGroup]
 * and anything of your own still work inside it unchanged.
 *
 * As everywhere in the library, the shorthands take no `modifier`. A destination
 * that needs one has outgrown the shorthand, and the component is in scope.
 *
 * ### The nesting is the point
 *
 * A destination inside a [group] inside a [section] is still a destination: it
 * keeps its full touch target, it reports its selection to the travelling pill
 * through a composition local rather than through its parent, and the indent is
 * the only thing nesting changes. Writing that by hand means threading
 * `nestLevel` down every branch, and the branch that forgets is a row that lines
 * up with the wrong column.
 */
@Stable
interface NavDrawerScope : ColumnScope {

    /**
     * One destination.
     *
     * @param badge A count drawn at the trailing edge. Announced as part of the
     *   row, so it is not a colour-only cue.
     * @param contentDescription What a screen reader says instead of [label],
     *   when a single word does not say where the row goes.
     */
    @Composable
    fun destination(
        label: String,
        icon: ImageVector? = null,
        selected: Boolean = false,
        enabled: Boolean = true,
        badge: Int? = null,
        contentDescription: String? = null,
        onClick: () -> Unit,
    )

    /** A titled run of destinations. */
    @Composable
    fun section(label: String, content: @Composable NavDrawerScope.() -> Unit)

    /** A run of destinations that collapses. */
    @Composable
    fun group(
        label: String,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        icon: ImageVector? = null,
        content: @Composable NavDrawerScope.() -> Unit,
    )

    /** A hairline between runs of destinations. */
    @Composable
    fun divider()
}

/**
 * Wraps a [ColumnScope] as a [NavDrawerScope] at a given nesting depth.
 *
 * [nestLevel] is carried by the scope rather than passed by the caller, which is
 * the whole reason the nested shorthands are worth having.
 */
internal class NavDrawerScopeImpl(
    column: ColumnScope,
    private val nestLevel: Int = 0,
) : NavDrawerScope, ColumnScope by column {

    @Composable
    override fun destination(
        label: String,
        icon: ImageVector?,
        selected: Boolean,
        enabled: Boolean,
        badge: Int?,
        contentDescription: String?,
        onClick: () -> Unit,
    ) {
        NavDrawerItem(
            label = label,
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            icon = icon,
            badge = badge,
            contentDescription = contentDescription,
            nestLevel = nestLevel,
        )
    }

    @Composable
    override fun section(label: String, content: @Composable NavDrawerScope.() -> Unit) {
        val level = nestLevel
        NavDrawerSection(label = label) {
            NavDrawerScopeImpl(this, level).content()
        }
    }

    @Composable
    override fun group(
        label: String,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        icon: ImageVector?,
        content: @Composable NavDrawerScope.() -> Unit,
    ) {
        val level = nestLevel
        NavDrawerGroup(
            label = label,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            icon = icon,
        ) {
            // One deeper. The rows inside a group indent under its chevron, and
            // this is the only place that number is decided.
            NavDrawerScopeImpl(this, level + 1).content()
        }
    }

    @Composable
    override fun divider() {
        HorizontalDivider()
    }
}
