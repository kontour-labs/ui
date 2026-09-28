package io.kontour.ui.overlay

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.ui.components.display.Kbd

/**
 * The shorthand for a menu's contents.
 *
 * ```kotlin
 * DropdownMenu(visible = open, onDismissRequest = { open = false }) {
 *     section("This stop")
 *     item("Share", leadingIcon = Tabler.Outline.Share, shortcut = "⌘S") { share(stop) }
 *     item("Copy stop ID", leadingIcon = Tabler.Outline.Copy) { copy(stop.id) }
 *     divider()
 *     item("Remove favourite", leadingIcon = Tabler.Outline.Trash, destructive = true) {
 *         remove(stop)
 *     }
 * }
 * ```
 *
 * `MenuScope` extends `ColumnScope`, so [MenuItem] and anything else still work
 * inside it — reach past the shorthand the moment you need a `modifier`, which
 * no shorthand takes. That rule, and the rest of the vocabulary, are in
 * `ui-docs/content/dsls.md`.
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
        leadingIcon: ImageVector? = null,
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
     * Two to four actions side by side, an icon over a word each, with a hairline
     * under them — the row across the top of an iOS menu, for the actions used
     * most. Put it first. Each closes the menu as it runs.
     *
     * ```kotlin
     * actionRow {
     *     action("Copy", Tabler.Outline.Copy) { copy(stop) }
     *     action("Share", Tabler.Outline.Share) { share(stop) }
     *     action("Save", Tabler.Outline.Star) { save(stop) }
     * }
     * ```
     *
     * @param filled Sets each action on a ground of its own. Off by default: the
     *   icons and their words stand on the menu, and the press shows where they are.
     */
    @Composable
    fun actionRow(filled: Boolean = false, content: MenuActionRowScope.() -> Unit)

    /**
     * A nested menu. On touch it opens in place, over its own row, with the row
     * kept as its heading to go back; with a mouse or the keyboard it opens beside
     * the row.
     *
     * Two levels is a category; three is a filing system, and by then a flat
     * list with [section] headings is easier to scan.
     */
    @Composable
    fun submenu(
        label: String,
        enabled: Boolean = true,
        leadingIcon: ImageVector? = null,
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
        leadingIcon: ImageVector?,
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
            if (leadingIcon != null) leading { +leadingIcon }
            // One region holding both, icon then key cap: a second `trailing`
            // replaces the first, which is how an item given both used to lose
            // its icon. The region sits in the row, so the two sit side by side.
            if (trailingIcon != null || shortcut != null) {
                trailing {
                    if (trailingIcon != null) +trailingIcon
                    // A key cap, not muted text. `⌘S` set in the row's own type
                    // reads as part of the label; the border is what says it is
                    // a key.
                    if (shortcut != null) Kbd { +shortcut }
                }
            }
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
    override fun actionRow(filled: Boolean, content: MenuActionRowScope.() -> Unit) {
        val dismiss = onDismissRequest
        MenuActionCells(MenuActionRowScope().apply(content).actions, Modifier, filled) { action ->
            // Close first, as `item` does.
            dismiss()
            action.onClick()
        }
        MenuDivider()
    }

    @Composable
    override fun submenu(
        label: String,
        enabled: Boolean,
        leadingIcon: ImageVector?,
        content: @Composable MenuScope.() -> Unit,
    ) {
        // The nested menu closes the *whole* stack, not just itself: picking an
        // action is done with the menu, and leaving the parent open over the
        // result is the same hanging-menu bug one level up.
        val dismiss = onDismissRequest
        SubMenu(
            label = {
                +label
                if (leadingIcon != null) leading { +leadingIcon }
            },
            enabled = enabled,
        ) {
            MenuScopeImpl(this, dismiss).content()
        }
    }
}

/**
 * The actions of a [MenuScope.actionRow] or a [MenuActionRow], collected in order.
 *
 * Two to four: one is an `item`, and past four each word has too little of the
 * menu's width to be read.
 */
@LayoutScopeMarker
@Stable
class MenuActionRowScope internal constructor() {
    internal val actions = mutableListOf<MenuAction>()

    /**
     * One action: [icon] over [label], which is also what it is called to a screen
     * reader.
     *
     * @param destructive Draws it in the danger tone.
     */
    fun action(
        label: String,
        icon: ImageVector,
        enabled: Boolean = true,
        destructive: Boolean = false,
        onClick: () -> Unit,
    ) {
        actions += MenuAction(label, icon, enabled, destructive, onClick)
    }
}

internal class MenuAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val destructive: Boolean,
    val onClick: () -> Unit,
)
