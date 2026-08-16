package io.kontour.ui.overlay

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.ui.components.display.Kbd

/**
 * The shorthand for a menu's contents.
 *
 * ```kotlin
 * DropdownMenu(visible = open, onDismissRequest = { open = false }) {
 *     section("This stop")
 *     item("Share", icon = Tabler.Outline.Share, shortcut = "⌘S") { share(stop) }
 *     item("Copy stop ID", icon = Tabler.Outline.Copy) { copy(stop.id) }
 *     divider()
 *     item("Remove favourite", icon = Tabler.Outline.Trash, destructive = true) {
 *         remove(stop)
 *     }
 * }
 * ```
 *
 * `MenuScope` extends `ColumnScope`, so [MenuItem] and anything else still work
 * inside it — reach past the shorthand the moment you need a `modifier`, which
 * no shorthand takes. That rule, and the rest of the vocabulary, are in
 * `docs/app/design-system/using/dsls.md`.
 *
 * **What it is actually for is not brevity.** [item] closes the menu after
 * running its action, which the component cannot do for you — `MenuItem` has
 * never been told how. Written out by hand every call site is
 * `onClick = { open = false; share(stop) }`, and the one that forgets leaves a
 * menu hanging over the screen it just navigated away from. That is the bug
 * this exists to delete.
 */
@Stable
@LayoutScopeMarker
interface MenuScope : ColumnScope {

    /**
     * One action.
     *
     * @param shortcut A keyboard accelerator, drawn muted at the trailing edge.
     *   Display only — binding the key is the screen's job.
     * @param selected Ticks the row. For a menu that shows which of a set is on,
     *   like a sort order.
     * @param destructive Draws in the danger tone. For anything the user cannot
     *   undo.
     * @param closeOnClick Set false for a row that toggles something the user is
     *   likely to toggle again — a set of filters, say.
     */
    @Composable
    fun item(
        label: String,
        enabled: Boolean = true,
        icon: ImageVector? = null,
        trailingIcon: ImageVector? = null,
        shortcut: String? = null,
        selected: Boolean = false,
        destructive: Boolean = false,
        closeOnClick: Boolean = true,
        onClick: () -> Unit,
    )

    /** A hairline between groups of actions. */
    @Composable
    fun divider()

    /** A heading over the rows that follow. */
    @Composable
    fun section(title: String)

    /**
     * A nested menu, which opens beside this row.
     *
     * Two levels is a category; three is a filing system, and by then a flat
     * list with [section] headings is easier to scan.
     */
    @Composable
    fun submenu(
        label: String,
        enabled: Boolean = true,
        icon: ImageVector? = null,
        content: @Composable MenuScope.() -> Unit,
    )
}

/**
 * Wraps a [ColumnScope] as a [MenuScope] that dismisses through [onDismissRequest].
 *
 * `by column` rather than a fresh implementation: `ColumnScope`'s members are
 * `Modifier.weight` and `Modifier.align`, and a menu has no business changing
 * what those mean.
 */
internal class MenuScopeImpl(
    column: ColumnScope,
    private val onDismissRequest: () -> Unit,
) : MenuScope, ColumnScope by column {

    @Composable
    override fun item(
        label: String,
        enabled: Boolean,
        icon: ImageVector?,
        trailingIcon: ImageVector?,
        shortcut: String?,
        selected: Boolean,
        destructive: Boolean,
        closeOnClick: Boolean,
        onClick: () -> Unit,
    ) {
        MenuItem(
            onClick = {
                // Close first. An action that navigates takes the composition
                // with it, and a dismiss queued behind it never runs.
                if (closeOnClick) onDismissRequest()
                onClick()
            },
            enabled = enabled,
            selected = selected,
            destructive = destructive,
        ) {
            +label
            if (icon != null) leading { +icon }
            if (trailingIcon != null) trailing { +trailingIcon }
            // A key cap, not muted text. `⌘S` set in the row's own type reads
            // as part of the label; the border is what says it is a key.
            if (shortcut != null) trailing { Kbd { +shortcut } }
        }
    }

    @Composable
    override fun divider() {
        MenuDivider()
    }

    @Composable
    override fun section(title: String) {
        MenuSectionHeader { +title }
    }

    @Composable
    override fun submenu(
        label: String,
        enabled: Boolean,
        icon: ImageVector?,
        content: @Composable MenuScope.() -> Unit,
    ) {
        // The nested menu closes the *whole* stack, not just itself: picking an
        // action is done with the menu, and leaving the parent open over the
        // result is the same hanging-menu bug one level up.
        val dismiss = onDismissRequest
        SubMenu(label = { +label }, enabled = enabled, leadingIcon = icon) {
            MenuScopeImpl(this, dismiss).content()
        }
    }
}
