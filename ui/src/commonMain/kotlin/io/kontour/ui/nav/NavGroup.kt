package io.kontour.ui.nav

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.ui.overlay.MenuDivider
import io.kontour.ui.overlay.MenuItem
import io.kontour.ui.overlay.MenuScope
import io.kontour.ui.overlay.MenuSectionHeader
import io.kontour.ui.overlay.SubMenu

/**
 * A group's destinations as a menu: what a group opens in a bar or a collapsed
 * rail, where there is no room to disclose them in place.
 *
 * Choosing one closes the menu first and navigates second, as every menu item
 * does — an action that navigates takes the composition with it, and a close
 * queued behind it never runs.
 */
internal class NavMenuScope(
    private val menu: MenuScope,
    private val dismiss: () -> Unit,
) : NavScope {

    @Composable
    override fun item(
        selected: Boolean,
        onClick: () -> Unit,
        key: Any,
        icon: ImageVector?,
        modifier: Modifier,
        enabled: Boolean,
        selectedIcon: ImageVector?,
        contentDescription: String?,
        interactionSource: MutableInteractionSource?,
        badge: (@Composable () -> Unit)?,
        label: NavLabelScope.() -> Unit,
    ) {
        emitter().let { if (it !== this) return it.item(selected, onClick, key, icon, modifier, enabled, selectedIcon, contentDescription, interactionSource, badge, label) }
        val text = navLabel(label)
        MenuItem(
            onClick = {
                dismiss()
                onClick()
            },
            modifier = modifier,
            enabled = enabled,
            selected = selected,
            interactionSource = interactionSource,
        ) {
            +text
            val glyph = if (selected) selectedIcon ?: icon else icon
            if (glyph != null) leading { +glyph }
            if (badge != null) trailing { badge() }
        }
    }

    @Composable
    override fun section(title: String, modifier: Modifier, content: @Composable NavScope.() -> Unit) {
        emitter().let { if (it !== this) return it.section(title, modifier, content) }
        MenuSectionHeader(modifier) { +title }
        content()
    }

    @Composable
    override fun group(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        key: Any,
        icon: ImageVector?,
        header: NavLabelScope.() -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        interactionSource: MutableInteractionSource?,
        badge: (@Composable () -> Unit)?,
        content: @Composable NavScope.() -> Unit,
    ) {
        emitter().let { if (it !== this) return it.group(expanded, onExpandedChange, key, icon, header, modifier, enabled, interactionSource, badge, content) }
        val text = navLabel(header)
        val close = dismiss
        SubMenu(
            label = {
                +text
                if (icon != null) leading { +icon }
            },
            modifier = modifier,
            enabled = enabled,
            interactionSource = interactionSource,
        ) {
            NavMenuScope(this, close).emit(content)
        }
    }

    @Composable
    override fun divider(modifier: Modifier) {
        emitter().let { if (it !== this) return it.divider(modifier) }
        MenuDivider(modifier)
    }
}

/**
 * A group's destinations composed for what they say and nothing else: each one
 * reports whether it is selected, so the group can carry the marker while they
 * are hidden. Draws nothing.
 */
internal class NavProbeScope(private val selection: NavGroupSelection) : NavScope {

    @Composable
    override fun item(
        selected: Boolean,
        onClick: () -> Unit,
        key: Any,
        icon: ImageVector?,
        modifier: Modifier,
        enabled: Boolean,
        selectedIcon: ImageVector?,
        contentDescription: String?,
        interactionSource: MutableInteractionSource?,
        badge: (@Composable () -> Unit)?,
        label: NavLabelScope.() -> Unit,
    ) {
        emitter().let { if (it !== this) return it.item(selected, onClick, key, icon, modifier, enabled, selectedIcon, contentDescription, interactionSource, badge, label) }
        selection.Report(key, selected)
    }

    @Composable
    override fun section(title: String, modifier: Modifier, content: @Composable NavScope.() -> Unit) {
        emitter().let { if (it !== this) return it.section(title, modifier, content) }
        content()
    }

    @Composable
    override fun group(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        key: Any,
        icon: ImageVector?,
        header: NavLabelScope.() -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        interactionSource: MutableInteractionSource?,
        badge: (@Composable () -> Unit)?,
        content: @Composable NavScope.() -> Unit,
    ) {
        emitter().let { if (it !== this) return it.group(expanded, onExpandedChange, key, icon, header, modifier, enabled, interactionSource, badge, content) }
        content()
    }

    @Composable
    override fun divider(modifier: Modifier) {
        emitter().let { if (it !== this) return it.divider(modifier) }
    }
}

/** Remembers a group's selection and, while its destinations are hidden, probes them. */
@Composable
internal fun rememberGroupSelection(
    hidden: Boolean,
    content: @Composable NavScope.() -> Unit,
): NavGroupSelection {
    val selection = remember { NavGroupSelection() }
    if (hidden) NavProbeScope(selection).emit(content)
    return selection
}
