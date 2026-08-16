package io.kontour.ui.nav

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One destination in a navigation surface.
 *
 * Declared once and rendered by whichever surface the window has room for — a
 * [NavBar] on a phone, a [NavRail] on a tablet, a [NavDrawer] on a desktop. That
 * is the point of a shared model: the Android app today defines its three
 * destinations once and loops over them in both `MainToolbar` and
 * `MainNavigationRail`, and this makes that the supported shape rather than a
 * convention two functions happen to follow.
 *
 * @param selectedIcon A filled counterpart for the current destination. Optional,
 *   but worth supplying: a filled icon says "you are here" on its own, and the
 *   tonal pill behind it stops being the only signal.
 * @param badge A count to show over the icon, or null. `0` shows a dot rather
 *   than a number — "something happened" without a quantity.
 */
@Immutable
data class NavItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val selectedIcon: ImageVector? = null,
    val badge: Int? = null,
    val enabled: Boolean = true,
    /**
     * What a screen reader announces, if the visible label is not enough. A nav
     * item's label is usually a single word, and "Plan" alone does not say what
     * it plans.
     */
    val contentDescription: String? = null,
)

/** The icon to draw for this [NavItem], given whether it is the current destination. */
internal fun NavItem.iconFor(selected: Boolean): ImageVector =
    if (selected) selectedIcon ?: icon else icon
