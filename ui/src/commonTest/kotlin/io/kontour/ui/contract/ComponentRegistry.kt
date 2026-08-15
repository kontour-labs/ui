package io.kontour.ui.contract

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.ExtendedFloatingActionButton
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.action.IconToggleButton
import io.kontour.ui.components.display.Accordion
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.SettingRow
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.Chip
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.components.selection.InputChip
import io.kontour.ui.components.selection.RadioButton
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.components.selection.TriStateCheckbox
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.components.text.Select
import io.kontour.ui.components.text.TextField
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.NavBarItem
import io.kontour.ui.nav.NavDrawerItem
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavRailItem
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar

/**
 * One interactive component, as the contract suite sees it.
 *
 * @param name What a failure reports. Worth the duplication with the function
 *   name: a failing assertion that says `Button (Destructive)` is far more
 *   useful than one that says `entry 14`.
 * @param role What the component must announce itself as. `null` for one that
 *   deliberately declares none.
 * @param expectsMinimumTarget False only where the component is *inside*
 *   something else that owns the target — a control in a `SelectionRow` is not
 *   independently tappable, and requiring a 48dp box round it would push the
 *   row apart for nothing. Also false for anything that sizes itself from its
 *   content, like a text field, where the minimum is met by the content box
 *   rather than imposed.
 * @param activatedByClick False for a control a tap does not operate — a slider
 *   is dragged, a text field is typed into. Those skip the *callback* half of the
 *   disabled check, since there is no callback a click could fire, but they still
 *   have to report themselves as disabled.
 * @param control Where the operable node is, when it is **not** the outermost
 *   one. Some components are a container around a control: an `Accordion` is a
 *   header plus a panel, and merging the panel into the header would swallow the
 *   whole disclosed body into the header's announcement. The role and disabled
 *   rules apply to the control; the modifier, target and layout rules still apply
 *   to the outside. `null` means the two are the same node, which is the case
 *   worth defaulting to.
 * @param accessibleName The text a screen reader must be able to read off this
 *   control, for a component rendered here **with a visible label**. This is the
 *   rule that catches a label sitting beside a control rather than naming it —
 *   Compose has no `labelledBy`, so two adjacent nodes stay two unrelated things
 *   however close together they are drawn. `null` where the component is rendered
 *   without a label to check.
 * @param content Renders it, applying [modifier] to the outermost node and
 *   calling [onActivate] when operated.
 */
class ComponentSpec(
    val name: String,
    val role: Role?,
    val expectsMinimumTarget: Boolean = true,
    val activatedByClick: Boolean = true,
    val control: SemanticsMatcher? = null,
    val accessibleName: String? = null,
    val content: @Composable (modifier: Modifier, enabled: Boolean, onActivate: () -> Unit) -> Unit,
)

/**
 * Matches a text input **in either state**.
 *
 * Deliberately not `hasSetTextAction()`: foundation withdraws the set-text action
 * when a field is disabled, so a matcher built on it finds nothing in precisely
 * the case the disabled rule exists to check — and an assertion that cannot find
 * its node reports as a failure that looks like the rule, not like the matcher.
 */
private val isTextInput = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

/**
 * Matches the chosen one of a set of selectable children, **in either state**.
 *
 * Not `hasClickAction()`, for the same reason [isTextInput] is not
 * `hasSetTextAction()`: foundation withdraws the click action when a control is
 * disabled, so that matcher finds nothing in exactly the case the disabled rule
 * is about. `Selected` is set either way, and matching the selected one keeps it
 * to a single node where `hasClickAction()` would match every segment.
 */
private val isSelectedOption = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

/**
 * Every interactive component in the system.
 *
 * The contract suite runs the same five assertions over all of them, so the
 * rules in `contributing.md` are enforced rather than remembered. **Adding a
 * component means adding a line here** — a component absent from this list is a
 * component nothing checks.
 *
 * Non-interactive components are deliberately absent: the five rules are about
 * being operable, and asserting a `Role` on a `Text` would be asserting a bug.
 */
val componentRegistry: List<ComponentSpec> = buildList {
    // --- Actions ---------------------------------------------------------
    for (variant in ButtonVariant.entries) {
        add(
            ComponentSpec("Button ($variant)", Role.Button) { modifier, enabled, onClick ->
                Button(
                    onClick = onClick,
                    modifier = modifier,
                    enabled = enabled,
                    variant = variant,
                ) {
                    +"Label"
                }
            }
        )
    }

    add(
        ComponentSpec("IconButton", Role.Button) { modifier, enabled, onClick ->
            IconButton(
                icon = Tabler.Outline.Star,
                contentDescription = "Favourite",
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("IconToggleButton", Role.Checkbox) { modifier, enabled, onClick ->
            IconToggleButton(
                icon = Tabler.Outline.Star,
                contentDescription = "Favourite",
                checked = false,
                onCheckedChange = { onClick() },
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("FloatingActionButton", Role.Button) { modifier, enabled, onClick ->
            FloatingActionButton(
                icon = Tabler.Outline.Star,
                contentDescription = "Add",
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("ExtendedFloatingActionButton", Role.Button) { modifier, enabled, onClick ->
            ExtendedFloatingActionButton(
                icon = Tabler.Outline.Star,
                label = "Add stop",
                contentDescription = "Add stop",
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    // --- Selection -------------------------------------------------------
    add(
        ComponentSpec("Checkbox", Role.Checkbox) { modifier, enabled, onClick ->
            Checkbox(
                checked = false,
                onCheckedChange = { onClick() },
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("RadioButton", Role.RadioButton) { modifier, enabled, onClick ->
            RadioButton(
                selected = false,
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("Switch", Role.Switch) { modifier, enabled, onClick ->
            Switch(
                checked = false,
                onCheckedChange = { onClick() },
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("Chip", Role.Button) { modifier, enabled, onClick ->
            Chip(onClick = onClick, modifier = modifier, enabled = enabled) { +"Bus" }
        }
    )

    add(
        ComponentSpec("FilterChip", Role.Checkbox) { modifier, enabled, onClick ->
            FilterChip(
                selected = false,
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            ) {
                +"Bus"
            }
        }
    )

    add(
        ComponentSpec("InputChip", Role.Button) { modifier, enabled, onClick ->
            InputChip(
                onRemove = {},
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                removeLabel = "Remove Perth",
            ) {
                +"Perth"
            }
        }
    )

    add(
        ComponentSpec("TriStateCheckbox", Role.Checkbox) { modifier, enabled, onClick ->
            TriStateCheckbox(
                state = ToggleableState.Indeterminate,
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("SelectionRow", Role.Checkbox) { modifier, enabled, onClick ->
            SelectionRow(
                label = "Notify me about delays",
                selected = false,
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                role = Role.Checkbox,
                control = { Checkbox(checked = false, onCheckedChange = null) },
            )
        }
    )

    add(
        // Dragged, not tapped. The other four rules still apply, and the disabled
        // one still applies in its "says so" half.
        ComponentSpec("Slider", role = null, activatedByClick = false) { modifier, enabled, _ ->
            Slider(
                value = 0.5f,
                onValueChange = {},
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    // --- Text ------------------------------------------------------------
    // The tagged node is the scaffold — label, frame, helper text. The control is
    // the input inside it, and the helper text deliberately stays its own node so
    // an error can be read separately from the value.
    add(
        ComponentSpec(
            name = "TextField",
            role = null,
            expectsMinimumTarget = false,
            activatedByClick = false,
            control = isTextInput,
            accessibleName = "Origin",
        ) { modifier, enabled, _ ->
            TextField(
                state = rememberTextFieldState("Perth"),
                modifier = modifier,
                enabled = enabled,
                label = "Origin",
            )
        }
    )

    add(
        ComponentSpec(
            name = "SearchField",
            role = null,
            expectsMinimumTarget = false,
            activatedByClick = false,
            control = isTextInput,
        ) { modifier, enabled, _ ->
            SearchField(
                state = rememberTextFieldState(),
                modifier = modifier,
                enabled = enabled,
                placeholder = "Search stops",
            )
        }
    )

    add(
        ComponentSpec(
            name = "Select",
            role = Role.DropdownList,
            expectsMinimumTarget = false,
            control = hasClickAction(),
            accessibleName = "When",
        ) { modifier, enabled, onClick ->
            Select(
                value = "Now",
                options = listOf("Now", "Later"),
                onValueChange = { onClick() },
                modifier = modifier,
                enabled = enabled,
                label = "When",
            )
        }
    )

    // --- Disclosure ------------------------------------------------------
    add(
        ComponentSpec(
            name = "Accordion",
            role = Role.Button,
            control = hasClickAction(),
        ) { modifier, enabled, onClick ->
            Accordion(
                title = "Accessibility",
                expanded = false,
                onExpandedChange = { onClick() },
                modifier = modifier,
                enabled = enabled,
            ) {
                Text("Step-free access at all platforms.")
            }
        }
    )

    // --- Navigation ------------------------------------------------------
    add(
        ComponentSpec("NavBarItem", Role.Tab) { modifier, enabled, onClick ->
            NavBarItem(
                item = NavItem(
                    label = "Plan",
                    icon = Tabler.Outline.Star,
                    onClick = onClick,
                    enabled = enabled,
                ),
                selected = false,
                modifier = modifier,
            )
        }
    )

    add(
        ComponentSpec("NavDrawerItem", Role.Tab) { modifier, enabled, onClick ->
            NavDrawerItem(
                label = "Favourites",
                selected = false,
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("NavRailItem", Role.Tab) { modifier, enabled, onClick ->
            NavRailItem(
                item = NavItem(
                    label = "Map",
                    icon = Tabler.Outline.Star,
                    onClick = onClick,
                    enabled = enabled,
                ),
                selected = false,
                modifier = modifier,
            )
        }
    )

    add(
        // The tagged node is the track; each segment inside it is the control.
        ComponentSpec(
            name = "SegmentedControl",
            role = Role.RadioButton,
            control = isSelectedOption,
        ) { modifier, enabled, onClick ->
            SegmentedControl(
                options = listOf("Bus", "Train"),
                selectedIndex = 0,
                onSelect = { onClick() },
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("Tab", Role.Tab) { modifier, enabled, onClick ->
            // A `Tab` only exists inside a `TabBar` — it reports its width to the
            // bar so the indicator can follow it. The tag still goes on the tab,
            // so what is asserted is the tab and not its container.
            TabBar {
                Tab(
                    label = "Departures",
                    selected = false,
                    onClick = onClick,
                    modifier = modifier,
                    enabled = enabled,
                )
            }
        }
    )

    // --- Collections -----------------------------------------------------
    add(
        ComponentSpec("ListItem", Role.Button) { modifier, enabled, onClick ->
            ListItem(
                label = "Perth Underground",
                supporting = "Platform 2",
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("SettingRow", Role.Button) { modifier, enabled, onClick ->
            SettingRow(
                label = "Theme",
                value = "Match system",
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            )
        }
    )
}
