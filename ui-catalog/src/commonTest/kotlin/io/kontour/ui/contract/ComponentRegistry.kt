package io.kontour.ui.contract

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.CurrentLocation
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Stack
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonGroup
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.ExtendedFloatingActionButton
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.action.IconToggleButton
import io.kontour.ui.components.datetime.RelativeTimeText
import io.kontour.ui.components.display.Accordion
import io.kontour.ui.components.display.AnimatedBanner
import io.kontour.ui.components.display.BannerTone
import io.kontour.ui.components.action.Toolbar
import io.kontour.ui.components.action.ToolbarDivider
import io.kontour.ui.components.display.Kbd
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.list.SwipeToDismiss
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.components.list.SettingRow
import io.kontour.ui.components.list.settingValue
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.Chip
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.components.selection.InputChip
import io.kontour.ui.components.selection.RadioButton
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.RadioGroup
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Stepper
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.components.selection.TriStateCheckbox
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.components.text.Select
import io.kontour.ui.components.text.TextField
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.NavBarItem
import io.kontour.ui.nav.NavDrawerGroup
import io.kontour.ui.nav.NavDrawerItem
import io.kontour.ui.nav.NavDrawerSection
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavRailItem
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.sheet.DragHandle
import kotlin.time.Duration.Companion.minutes

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
    /**
     * True for a control that cannot name itself and is designed not to.
     *
     * A bare `Checkbox` has no text of its own — it is meant to sit in a
     * `SelectionRow`, or beside a label whose click target it shares, and take
     * its name from there. Material's does the same.
     *
     * An opt-out rather than an opt-in, so a component that *loses* its name —
     * which is now possible, since slots let a caller build a row with no label
     * in it — fails rather than passing quietly.
     */
    val namedByContext: Boolean = false,
    /**
     * False for a specimen that exists only to be **drawn**.
     *
     * The contract's seven assertions are about being operable: they ask for a
     * role, for a disabled state, for a touch target. A `Kbd` has none of those
     * and is not failing anything by not having them — it is a glyph in a box.
     * Such a specimen is here so that [componentRegistry] can also drive the
     * per-component renders, which want the whole library rather than the
     * quarter of it that is interactive.
     *
     * Default true, so a new *control* is under contract unless someone says
     * otherwise. Opting out is the thing that should have to be written down.
     */
    val underContract: Boolean = true,
    /**
     * A taller card for this specimen's render, in dp.
     *
     * Null for almost everything: one canvas for all of them is what makes a
     * page of these comparable. The exception earns it — `PullToRefresh`
     * mid-refresh floats its indicator a 72dp threshold below the top of its
     * content, so a faithful picture of it is 112dp of structure and the
     * standard card offers 88.
     */
    val renderHeight: Int? = null,
    val content: @Composable (modifier: Modifier, enabled: Boolean, onActivate: () -> Unit) -> Unit,
) {
    /**
     * A filename for this specimen's render.
     *
     * Derived rather than declared, so it cannot drift from [name]: `"Button
     * (Primary)"` becomes `button-primary`. A second field would be a second
     * thing to keep in step, and this one has exactly one correct value.
     */
    val slug: String = name.lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .trim('-')
        .replace(Regex("-+"), "-")
}

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
 * Every component in the system, as a specimen.
 *
 * **One list, two consumers.** `ComponentContractTest` runs seven assertions
 * over the entries under contract, so the rules in `contributing.md` are
 * enforced rather than remembered. `ComponentRenderTest` draws all of them, so
 * every component has a picture its documentation can show.
 *
 * **Adding a component means adding a line here** — one absent from this list is
 * a component nothing checks *and* nothing draws, which is one omission made
 * visible twice.
 *
 * A non-interactive component sets [ComponentSpec.underContract] to false and
 * joins the second consumer only. It is here to be drawn.
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
        ComponentSpec("Checkbox", Role.Checkbox, namedByContext = true) { modifier, enabled, onClick ->
            Checkbox(
                checked = false,
                onCheckedChange = { onClick() },
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("RadioButton", Role.RadioButton, namedByContext = true) { modifier, enabled, onClick ->
            RadioButton(
                selected = false,
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec("Switch", Role.Switch, namedByContext = true) { modifier, enabled, onClick ->
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
        ComponentSpec("TriStateCheckbox", Role.Checkbox, namedByContext = true) { modifier, enabled, onClick ->
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
                selected = false,
                onSelectedChange = { onClick() },
                modifier = modifier,
                enabled = enabled,
                role = Role.Checkbox,
            ) {
                +"Notify me about delays"
                trailing { Checkbox(checked = false, onCheckedChange = null) }
            }
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

    add(
        // Same as `Slider`, and for the same reason. Its own two thumbs are
        // separate semantic nodes inside it, which is what `RangeSliderTest`
        // covers; here it is one specimen with one outermost node.
        ComponentSpec("RangeSlider", role = null, activatedByClick = false) { modifier, enabled, _ ->
            RangeSlider(
                value = 0.25f..0.75f,
                onValueChange = {},
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        // The row is the component; its two buttons are the controls. It names
        // itself through `contentDescription`, and pressing it is pressing one
        // of the buttons rather than the row, so neither the click rule nor the
        // touch-target rule applies to the outermost node.
        ComponentSpec(
            "Stepper",
            role = null,
            activatedByClick = false,
            expectsMinimumTarget = false,
        ) { modifier, enabled, _ ->
            Stepper(
                value = 2,
                onValueChange = {},
                contentDescription = "Adults",
                modifier = modifier,
                enabled = enabled,
                range = 1..9,
            )
        }
    )

    add(
        // Read, not operated: no role, no touch target, nothing to press. It is
        // here for its render and for the one rule that does apply to it — that
        // it survives 200% type in RTL, which a number-over-label block is a
        // plausible way to break.
        ComponentSpec(
            "Stat",
            role = null,
            activatedByClick = false,
            expectsMinimumTarget = false,
            underContract = false,
        ) { modifier, _, _ ->
            Stat(modifier) {
                value("4 min")
                +"Next departure"
                supporting("Platform 2")
            }
        }
    )

    add(
        ComponentSpec(
            "KeyValueList",
            role = null,
            activatedByClick = false,
            expectsMinimumTarget = false,
            underContract = false,
            // Three rows do not fit the shared 120dp canvas, and a clipped
            // render is the worst kind: it draws enough ink to pass the blank
            // check while showing two thirds of the specimen.
            renderHeight = 160,
        ) { modifier, _, _ ->
            KeyValueList(modifier) {
                row("Operator", "Transperth")
                row("Platform", "2")
                row("Fare", "$3.20")
            }
        }
    )

    add(
        // The group is a container; its buttons are the controls. The outermost
        // node is a traversal group with no role and nothing to press, so the
        // click and touch-target rules apply to the buttons inside rather than
        // to it. `ButtonGroupTest` is what covers those.
        ComponentSpec(
            "ButtonGroup",
            role = null,
            activatedByClick = false,
            expectsMinimumTarget = false,
            underContract = false,
        ) { modifier, enabled, onActivate ->
            ButtonGroup(modifier, enabled = enabled) {
                action(onClick = onActivate, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
                action(onClick = onActivate, contentDescription = "Recentre", icon = Tabler.Outline.CurrentLocation)
                action(onClick = onActivate, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
            }
        }
    )

    add(
        ComponentSpec(
            "Toolbar",
            role = null,
            activatedByClick = false,
            expectsMinimumTarget = false,
            underContract = false,
        ) { modifier, enabled, onActivate ->
            Toolbar(modifier) {
                ButtonGroup(enabled = enabled) {
                    action(onClick = onActivate, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
                    action(onClick = onActivate, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
                }
                ToolbarDivider()
                IconButton(
                    icon = Tabler.Outline.Stack,
                    contentDescription = "Map layers",
                    onClick = onActivate,
                    enabled = enabled,
                )
            }
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
                expanded = false,
                onExpandedChange = { onClick() },
                header = { +"Accessibility" },
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
                selected = false,
                onClick = onClick,
                key = "drawer-item",
                modifier = modifier,
                enabled = enabled,
            ) { +"Favourites" }
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
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            ) {
                +"Perth Underground"
                supporting { +"Platform 2" }
            }
        }
    )

    add(
        ComponentSpec("SettingRow", Role.Button) { modifier, enabled, onClick ->
            SettingRow(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            ) {
                +"Theme"
                trailing { settingValue("Match system") }
            }
        }
    )

    add(
        // The one group in the library that is a *set* of controls. Tagged on
        // the column, asserted on the chosen radio inside it — same shape as
        // `SegmentedControl` above, and for the same reason: a column has no
        // role and no disabled state to report.
        ComponentSpec(
            name = "RadioGroup",
            role = Role.RadioButton,
            control = isSelectedOption,
        ) { modifier, enabled, onClick ->
            RadioGroup(
                options = listOf("Bus", "Train"),
                selected = "Bus",
                onSelect = { onClick() },
                modifier = modifier,
                enabled = enabled,
                label = { it },
            )
        }
    )

    // --- Drawn, not operated ---------------------------------------------
    //
    // Everything below is here so it has a picture. None of it is a control:
    // the contract's seven assertions ask for a role, a disabled state and a
    // touch target, and a key cap has none of those and is not failing anything
    // by not having them.
    //
    // Two components are still missing even from here, and deliberately.
    // `ConfirmHost` draws nothing until something asks it a question and
    // `ContextMenuArea` draws nothing until a right-click, so a card of either
    // would show only the child inside it. They need a specimen that captures
    // the *open* state, which is a bigger frame than this one.

    add(
        ComponentSpec("Kbd", role = null, underContract = false) { modifier, _, _ ->
            Kbd(key = "⌘K", modifier = modifier)
        }
    )

    add(
        ComponentSpec("DragHandle", role = null, underContract = false) { modifier, _, _ ->
            // No `LocalSheetState`, so it is the resting pill rather than the
            // interactive one — which is what a reader wants to see of it.
            DragHandle(modifier = modifier, state = null)
        }
    )

    add(
        ComponentSpec("RelativeTimeText", role = null, underContract = false) { modifier, _, _ ->
            RelativeTimeText(until = 4.minutes, modifier = modifier)
        }
    )

    add(
        ComponentSpec("Scrollbar", role = null, underContract = false) { modifier, _, _ ->
            // `alwaysVisible`, because a scrollbar hides itself unless the input
            // can hover and a render has no pointer at all. The list behind it
            // has to overflow or there is no thumb to draw.
            Box(modifier.height(96.dp)) {
                val scroll = rememberScrollState()
                Column(Modifier.verticalScroll(scroll)) {
                    repeat(12) { Text("Stop ${it + 1}") }
                }
                Scrollbar(
                    state = scroll,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    alwaysVisible = true,
                )
            }
        }
    )

    add(
        ComponentSpec("AnimatedBanner", role = null, underContract = false) { modifier, _, _ ->
            AnimatedBanner(visible = true, modifier = modifier, tone = BannerTone.Warning) {
                +"Services are running late."
            }
        }
    )

    add(
        ComponentSpec(
            name = "PullToRefresh",
            role = null,
            underContract = false,
            renderHeight = 200,
        ) { modifier, _, _ ->
            // Mid-refresh, over real content: at rest it is its own content and
            // nothing else, so a card of it would be a card of a list row — and
            // with nothing underneath, the indicator floats in space rather than
            // sitting above the thing it is refreshing.
            PullToRefresh(refreshing = true, onRefresh = {}, modifier = modifier) {
                // Taller than the 72dp threshold on purpose. With shorter
                // content the indicator lands *below* the list, which is what it
                // honestly does when it wraps something short — and is not what
                // it does in the full-height scrollable it is actually for.
                Column(Modifier.fillMaxWidth()) {
                    ListItem(position = ListItemPosition.First) { +"Perth Underground" }
                    ListItem(position = ListItemPosition.Middle) { +"Elizabeth Quay" }
                    ListItem(position = ListItemPosition.Last) { +"McIver" }
                }
            }
        }
    )

    add(
        ComponentSpec("SwipeToDismiss", role = null, underContract = false) { modifier, _, _ ->
            SwipeToDismiss(
                onDismissRequest = {},
                label = "Remove",
                icon = Tabler.Outline.Star,
                modifier = modifier,
            ) {
                ListItem { +"Perth Underground" }
            }
        }
    )

    add(
        ComponentSpec("ReorderableItem", role = null, underContract = false) { modifier, _, _ ->
            val listState = rememberLazyListState()
            val reorder = rememberReorderableState(listState) { _, _ -> }
            ReorderableItem(state = reorder, index = 0, modifier = modifier, itemCount = 3) {
                ListItem { +"Perth Underground" }
            }
        }
    )

    add(
        ComponentSpec("NavDrawerSection", role = null, underContract = false) { modifier, _, _ ->
            NavDrawerSection(label = "Saved", modifier = modifier) {
                destination(label = "Nearby", selected = false, onClick = {})
            }
        }
    )

    add(
        ComponentSpec("NavDrawerGroup", role = null, underContract = false) { modifier, _, _ ->
            NavDrawerGroup(
                label = "Lines",
                expanded = true,
                onExpandedChange = {},
                modifier = modifier,
            ) {
                destination(label = "Joondalup", selected = false, onClick = {})
            }
        }
    )
}
