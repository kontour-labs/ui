package io.kontour.ui.components.list

import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import io.kontour.ui.foundation.ContentScope

/**
 * The regions of a [ListItem], filled by name.
 *
 * ```kotlin
 * ListItem(onClick = { open(stop) }) {
 *     +stop.name
 *     supporting { +stop.detail }
 *     leading { +Tabler.Outline.Bus }
 *     trailing { Switch(saved, onCheckedChange = ::save) }
 * }
 * ```
 *
 * A bare `+` fills the label, because that is the one every row has and the one
 * written every time. The rest are named.
 *
 * **This one collects rather than emits.** A row is four regions in two
 * containers, so content cannot run where it is written — the label belongs
 * inside a `Column` inside a `Row`, next to a leading slot declared after it.
 * The builder records what goes where and [ListItem] composes the regions in
 * the order the layout wants.
 *
 * No slot takes a `modifier`; a region that needs one has outgrown the
 * shorthand, and the slot takes a composable. Both rules, and the two kinds of
 * scope behind them, are in `ui-docs/content/dsls.md`.
 */
@LayoutScopeMarker
@Stable
class ListItemScope internal constructor() {

    internal var label: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var supporting: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var overline: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var leading: (@Composable ContentScope.() -> Unit)? = null
        private set
    internal var trailing: (@Composable ContentScope.() -> Unit)? = null
        private set

    /** The row's name. What it announces, and the one region every row has. */
    operator fun String.unaryPlus() {
        val text = this
        label = { +text }
    }

    /** The row's name, carrying its own spans. */
    operator fun AnnotatedString.unaryPlus() {
        val text = this
        label = { +text }
    }

    /** The label, when it is more than text. */
    fun label(content: @Composable ContentScope.() -> Unit) {
        label = content
    }

    /** The second line: an address, a status, a time. Muted and smaller. */
    fun supporting(content: @Composable ContentScope.() -> Unit) {
        supporting = content
    }

    /** A line *above* the label — a category, a route number. */
    fun overline(content: @Composable ContentScope.() -> Unit) {
        overline = content
    }

    /**
     * The leading edge: an icon, an avatar, a checkbox.
     *
     * `+icon` here draws muted and at [io.kontour.ui.theme.Sizing.iconLarge],
     * which is what the old `leadingIcon` parameter did — an icon at the start of
     * a row is scenery for the label, not a second thing to read.
     */
    fun leading(content: @Composable ContentScope.() -> Unit) {
        leading = content
    }

    /** The trailing edge: a switch, a chevron, a value, a badge. */
    fun trailing(content: @Composable ContentScope.() -> Unit) {
        trailing = content
    }

    /** True once anything has claimed the label. */
    internal val hasLabel: Boolean get() = label != null

    /** Two-line rows are taller. Known only after the builder has run. */
    internal val isMultiLine: Boolean get() = supporting != null || overline != null
}

/** Runs [content] and hands back what it claimed. */
internal fun listItemSlots(content: ListItemScope.() -> Unit): ListItemScope =
    ListItemScope().apply(content)
