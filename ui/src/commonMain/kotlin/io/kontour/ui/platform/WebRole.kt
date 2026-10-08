package io.kontour.ui.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState

/**
 * What a control is, as a browser's accessibility tree names it.
 *
 * Reported: *"toggleable rows show up as buttons in the browser's accessibility
 * tree, and so do tabs, even though `TabBar` declares them as tabs."* Compose
 * 1.12 builds that tree from the semantics, and names anything that can be
 * clicked a button, after and over the role the semantics give it, with no
 * checked or selected state at all. Compose 1.13 does it properly; until the
 * library is on it, [webRole] puts back what the control said.
 */
internal enum class WebRole(val aria: String, val stateAttribute: String) {
    Checkbox("checkbox", "aria-checked"),
    Switch("switch", "aria-checked"),
    Radio("radio", "aria-checked"),
    Tab("tab", "aria-selected"),
    MenuItemCheckbox("menuitemcheckbox", "aria-checked"),
    MenuItemRadio("menuitemradio", "aria-checked"),
}

/** The value of [WebRole.stateAttribute] for [state]. */
internal fun ariaState(state: ToggleableState): String = when (state) {
    ToggleableState.On -> "true"
    ToggleableState.Off -> "false"
    ToggleableState.Indeterminate -> "mixed"
}

/**
 * On the node a control's role and state are on, beside its `toggleable` or
 * `selectable`: in a browser, its element in the accessibility tree is given
 * [role] and [state] again whenever Compose writes `button` over them. Nothing
 * anywhere else.
 */
internal expect fun Modifier.webRole(role: WebRole, state: ToggleableState): Modifier

/**
 * [webRole] for a control whose role is Compose's: a checkbox, a switch, a
 * radio button or a tab. Nothing for any other role, whose name a browser
 * already has right or has no better one for.
 */
internal fun Modifier.webRole(role: Role, on: Boolean): Modifier = when (role) {
    Role.Checkbox -> webRole(WebRole.Checkbox, on)
    Role.Switch -> webRole(WebRole.Switch, on)
    Role.RadioButton -> webRole(WebRole.Radio, on)
    Role.Tab -> webRole(WebRole.Tab, on)
    else -> this
}

/** [webRole] for a control that is on or off, chosen or not. */
internal fun Modifier.webRole(role: WebRole, on: Boolean): Modifier =
    webRole(role, if (on) ToggleableState.On else ToggleableState.Off)
