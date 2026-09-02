package io.kontour.ui.contract

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.CurrentLocation
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Stack
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonGroup
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.ExtendedFloatingActionButton
import io.kontour.ui.components.action.FabMenu
import io.kontour.ui.components.action.FabMenuLayout
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.action.SplitButton
import io.kontour.ui.components.action.IconToggleButton
import io.kontour.ui.components.datetime.RelativeTimeText
import io.kontour.ui.components.display.Accordion
import io.kontour.ui.components.display.AnimatedCounter
import io.kontour.ui.components.display.AnimatedBanner
import io.kontour.ui.components.display.BannerTone
import io.kontour.ui.components.action.Toolbar
import io.kontour.ui.components.action.ToolbarDivider
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.Kbd
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.PageIndicatorStyle
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ExpandingListItem
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.list.SwipeToDismiss
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.components.list.rememberSwipeActionsState
import io.kontour.ui.components.list.SettingRow
import io.kontour.ui.components.list.settingValue
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.Chip
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.components.selection.InputChip
import io.kontour.ui.components.selection.RadioButton
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Rating
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
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.marquee
import io.kontour.ui.theme.Theme
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
 * One extra picture of a component, in a state it cannot be caught in at rest.
 *
 * The per-component renders were all resting, and for a good half of the library
 * that is the wrong picture: `SwipeToDismiss` and `ReorderableItem` at rest are
 * both a plain row, so their images were the same row twice under two names, and
 * neither showed the gesture the component exists for. A `Tab` at rest is an
 * unselected tab, which is the one state a reader does not need to see, and
 * `IconButton` and `IconToggleButton` were byte-for-byte the same file.
 *
 * A state supplies its own arrangement rather than a script the harness runs.
 * That sounds like more work per state and is less: driving the component means
 * hoisting its state object and launching one call in a `LaunchedEffect`, which
 * is exactly what a caller would write, and the screenshot theme already runs
 * with `reduceMotion` so the animation lands on the first frame.
 *
 * ### Which components get one
 *
 * The ones whose defining state is not the resting one. That is a narrower set
 * than "every state a component has": `TriStateCheckbox` has three and gets
 * none, because the one that distinguishes it from `Checkbox` — indeterminate —
 * is what its resting specimen already draws. A second picture that a reader
 * could have predicted from the first is a file to keep current for nothing.
 *
 * @param name Becomes the slug: `<component>-<name>-{light,dark}.png`. Named
 *   after the *parameter* that differs from the resting render — `checked`,
 *   `selected`, `expanded` — so the slug says what was changed rather than
 *   describing how it looks. The two gesture states are the exception, since
 *   neither is reached by a parameter. The resting render keeps the bare
 *   `<component>-{light,dark}.png` it always had, so no documentation link moves.
 * @param height A taller card, in dp, where the state needs one — a revealed
 *   swipe row does not, an expanded accordion does.
 */
class RenderState(
    val name: String,
    val height: Int? = null,
    val content: @Composable (modifier: Modifier) -> Unit,
)

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
 *   worth defaulting to. See [ControlLocator].
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
    val control: ControlLocator? = null,
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
    /**
     * The narrowest this component can be drawn correctly, in dp.
     *
     * Null for almost everything: a component squeezed below what it asked for
     * is expected to cope, and `WidthSweepTest` holds it to that. The exception
     * is a component whose parts have *individually* irreducible sizes — a
     * `Stepper` is two 48dp touch targets with a value between them, so at 48dp
     * there is no arrangement of it that is right, only ones that are wrong in
     * different ways: shrink the buttons and they stop being reachable, drop one
     * and the control silently loses half of what it does.
     *
     * Declared here rather than exempted by name inside the sweep, because a
     * list of names in a test is how a defect becomes a permanent exemption.
     * This is a fact about the component; it belongs next to the component,
     * where whoever widens a `Stepper`'s parts has to change it.
     */
    val minWidth: Int? = null,
    /**
     * Extra renders beyond the resting one — see [RenderState].
     *
     * Empty for most components, because most of them look like themselves
     * standing still. The ones that do not are the ones whose whole point is
     * something they do.
     */
    val states: List<RenderState> = emptyList(),
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
 * Where a component's operable node is, said without naming a test API.
 *
 * The registry used to hold a `SemanticsMatcher` here, which tied the whole list
 * to `compose.uiTest` and so to a test source set. That was fine while the
 * contract suite was the only reader; it is not fine now that the documentation
 * site renders the same specimens, because it would ship a test framework in a
 * bundle a browser has to download.
 *
 * So the spec says *what kind of node* and `ComponentContractTest` turns that
 * into a matcher. Four kinds cover the library, and each is a decision worth
 * writing down rather than a matcher expression to be read back later.
 */
sealed interface ControlLocator {

    /** The clickable node inside the specimen. An `Accordion`'s header. */
    data object Clickable : ControlLocator

    /**
     * A text input, **in either state**.
     *
     * Deliberately not "has a set-text action": foundation withdraws that action
     * when a field is disabled, so a matcher built on it finds nothing in
     * precisely the case the disabled rule exists to check — and an assertion
     * that cannot find its node reports as a failure that looks like the rule,
     * not like the matcher.
     */
    data object TextInput : ControlLocator

    /**
     * The chosen one of a set of selectable children, **in either state**.
     *
     * Not the clickable one, for the same reason [TextInput] is not the
     * set-text one. `Selected` is set either way, and matching the selected one
     * keeps it to a single node where "clickable" would match every segment.
     */
    data object SelectedOption : ControlLocator

    /**
     * The clickable node carrying this text.
     *
     * For a component that is two controls: a `SplitButton` is a labelled half
     * and a chevron half, and the rules about role and disabled belong to the
     * half that carries the default action.
     */
    data class ClickableLabelled(val text: String) : ControlLocator
}

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
        ComponentSpec(
            name = "IconToggleButton",
            role = Role.Checkbox,
            states = listOf(
                // Unchecked, this draws an icon in a hit target and nothing
                // else — which is `IconButton`, and the two renders really were
                // the same bytes. The checked ground is the entire difference
                // between the two components.
                RenderState("checked") { modifier ->
                    IconToggleButton(
                        icon = Tabler.Outline.Star,
                        contentDescription = "Favourite",
                        checked = true,
                        onCheckedChange = {},
                        modifier = modifier,
                    )
                },
            ),
        ) { modifier, enabled, onClick ->
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
                contentDescription = "Add stop",
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            ) { +"Add stop" }
        }
    )

    add(
        // Shut, this is a `FloatingActionButton` and nothing else — which is the
        // point of the component and the wrong picture of it. The three extra
        // renders are the three layouts, because "which arrangement do I want"
        // is the only question a reader has about this one and no amount of
        // prose answers it as fast as seeing them side by side.
        ComponentSpec(
            name = "FabMenu",
            role = Role.Button,
            accessibleName = "Add",
            states = FabMenuLayout.entries.map { layout ->
                // In the corner, because that is the only place the picture is
                // true: the component picks which way to open from where it
                // finds itself, so a specimen centred in its card opens
                // downward into nothing and shows an arrangement no real screen
                // would produce.
                RenderState(layout.name.lowercase(), height = 300) { modifier ->
                    Box(modifier.fillMaxSize()) {
                        FabMenuSpecimen(
                            expanded = true,
                            layout = layout,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            },
        ) { modifier, enabled, onClick ->
            FabMenuSpecimen(
                expanded = false,
                layout = FabMenuLayout.Vertical,
                modifier = modifier,
                enabled = enabled,
                onExpandedChange = { onClick() },
            )
        }
    )

    // --- Selection -------------------------------------------------------
    add(
        // The three toggles all rest in the off state, which is the state that
        // looks like an empty box. The tick, the dot and the moved knob are the
        // whole of what each one draws.
        ComponentSpec(
            name = "Checkbox",
            role = Role.Checkbox,
            namedByContext = true,
            states = listOf(
                RenderState("checked") { modifier ->
                    Checkbox(checked = true, onCheckedChange = {}, modifier = modifier)
                },
            ),
        ) { modifier, enabled, onClick ->
            Checkbox(
                checked = false,
                onCheckedChange = { onClick() },
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec(
            name = "RadioButton",
            role = Role.RadioButton,
            namedByContext = true,
            states = listOf(
                RenderState("selected") { modifier ->
                    RadioButton(selected = true, onClick = {}, modifier = modifier)
                },
            ),
        ) { modifier, enabled, onClick ->
            RadioButton(
                selected = false,
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec(
            name = "Switch",
            role = Role.Switch,
            namedByContext = true,
            states = listOf(
                RenderState("checked") { modifier ->
                    Switch(checked = true, onCheckedChange = {}, modifier = modifier)
                },
            ),
        ) { modifier, enabled, onClick ->
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
        ComponentSpec(
            name = "FilterChip",
            role = Role.Checkbox,
            states = listOf(
                // Unselected it is a `Chip` — an outlined pill. Selected it
                // swaps the outline for the accent container and takes the
                // accent for its text, which is the whole difference and is
                // worth showing precisely because it is only colour. No tick:
                // `selectedIcon` defaults to null, and this render flips
                // `selected` and nothing else.
                RenderState("selected") { modifier ->
                    FilterChip(selected = true, onClick = {}, modifier = modifier) { +"Bus" }
                },
            ),
        ) { modifier, enabled, onClick ->
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
        ComponentSpec(
            name = "SelectionRow",
            role = Role.Checkbox,
            states = listOf(
                // The row and the control it carries move together — that they
                // agree is the thing worth photographing, since a row wired to a
                // checkbox that does not follow it is the defect this component
                // exists to prevent.
                RenderState("selected") { modifier ->
                    SelectionRow(
                        selected = true,
                        onSelectedChange = {},
                        modifier = modifier,
                        role = Role.Checkbox,
                    ) {
                        +"Notify me about delays"
                        trailing { Checkbox(checked = true, onCheckedChange = null) }
                    }
                },
            ),
        ) { modifier, enabled, onClick ->
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
            // Two 48dp targets and a value cell between them. Measured at 144dp
            // with the default `valueWidth`; below that something has to give
            // and every candidate is worse than overflowing.
            minWidth = 144,
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
                item("Operator", "Transperth")
                item("Platform", "2")
                item("Fare", "$3.20")
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
                item(onClick = onActivate, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
                item(onClick = onActivate, contentDescription = "Recentre", icon = Tabler.Outline.CurrentLocation)
                item(onClick = onActivate, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
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
                    item(onClick = onActivate, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
                    item(onClick = onActivate, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
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

    add(
        // The interactive one. Each mark is a `Role.RadioButton` inside a
        // `selectableGroup`, so the roles and targets belong to the marks; the
        // row is the group. `RatingTest` covers the read-only half, which is a
        // different component wearing the same name.
        ComponentSpec(
            "Rating",
            role = null,
            activatedByClick = false,
            expectsMinimumTarget = false,
            underContract = false,
        ) { modifier, enabled, onActivate ->
            Rating(
                value = 3f,
                contentDescription = "Your rating",
                modifier = modifier,
                enabled = enabled,
                onValueChange = { onActivate() },
            )
        }
    )

    add(
        // Its own node is a scrollable group carrying custom actions, not a
        // control. `CarouselTest` covers the routes; this exists for the render
        // and for the layout rules.
        ComponentSpec(
            "Carousel",
            role = null,
            activatedByClick = false,
            expectsMinimumTarget = false,
            underContract = false,
            renderHeight = 160,
            states = listOf(
                // The worm, which the default render cannot show: the dots
                // style and the worm style draw the same thing on page one and
                // differ only in what happens between two pages.
                // Caught halfway between two pages, because that is the only
                // position where a worm looks like anything: at rest it is a
                // dot, and the stretch is the whole of what it adds.
                RenderState("worm", height = 60) { modifier ->
                    val carousel = rememberCarouselState { 4 }
                    LaunchedEffect(Unit) {
                        val pitch = snapshotFlow {
                            carousel.listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size
                        }.filterNotNull().first()
                        carousel.listState.scrollToItem(0, pitch / 2)
                    }
                    Column(modifier.fillMaxWidth()) {
                        // Off the bottom of the frame: the pages exist so the
                        // list has something to be halfway through, and the
                        // render is cropped to its ink anyway.
                        Carousel(
                            state = carousel,
                            contentDescription = "Pages",
                            modifier = Modifier.fillMaxWidth().height(1.dp),
                        ) { Box(Modifier.fillMaxWidth().height(1.dp)) }
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PageIndicator(carousel, style = PageIndicatorStyle.Worm)
                        }
                    }
                },
            ),
        ) { modifier, enabled, _ ->
            val carousel = rememberCarouselState { 3 }
            Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Carousel(
                    state = carousel,
                    contentDescription = "Stop photos",
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                ) { page ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        shape = Theme.shapes.small,
                        color = Theme.colors.surfaceSunken,
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Photo ${page + 1}")
                        }
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PageIndicator(carousel)
                }
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
            control = ControlLocator.TextInput,
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
            control = ControlLocator.TextInput,
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
            control = ControlLocator.Clickable,
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

    add(
        // Drawn at rest, which for a counter is a number. The roll is what it is
        // *for* and a still cannot show it — `AnimatedCounterTest` is where that
        // claim is checked, and a second render mid-roll would be a picture of
        // one arbitrary frame.
        ComponentSpec(
            name = "AnimatedCounter",
            role = null,
            underContract = false,
        ) { modifier, _, _ ->
            AnimatedCounter(value = 14, modifier = modifier, format = { "$it min" })
        }
    )

    add(
        // Given less room than its text needs, because that is the only state in
        // which the modifier does anything at all.
        ComponentSpec(
            name = "Modifier.marquee",
            role = null,
            underContract = false,
        ) { modifier, _, _ ->
            Box(modifier.width(140.dp)) {
                Text(
                    text = "Elizabeth Quay Bus Station",
                    maxLines = 1,
                    modifier = Modifier.marquee(),
                )
            }
        }
    )

    // --- Disclosure ------------------------------------------------------
    add(
        ComponentSpec(
            name = "Accordion",
            role = Role.Button,
            control = ControlLocator.Clickable,
            states = listOf(
                // Collapsed, the panel is the half of this component that does
                // not exist yet — so the resting render is a header row with a
                // chevron on it, and the chevron is the only hint that anything
                // is under there.
                RenderState("expanded", height = 340) { modifier ->
                    Accordion(
                        expanded = true,
                        onExpandedChange = {},
                        header = { +"Accessibility" },
                        modifier = modifier,
                    ) {
                        Text("Step-free access at all platforms.")
                    }
                },
            ),
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

    add(
        ComponentSpec(
            name = "SplitButton",
            role = Role.Button,
            // Two buttons in one control, so the outermost node is the pair and
            // the rules about role and disabled belong to the half that carries
            // the default action.
            control = ControlLocator.ClickableLabelled("Save"),
            accessibleName = "Save",
            states = listOf(
                RenderState("expanded", height = 260) { modifier ->
                    SplitButtonSpecimen(expanded = true, modifier = modifier)
                },
            ),
        ) { modifier, enabled, onClick ->
            SplitButtonSpecimen(
                expanded = false,
                modifier = modifier,
                enabled = enabled,
                onClick = onClick,
            )
        }
    )

    add(
        // The near-twin of `Accordion`, and the pair is why both are here: an
        // accordion is a header over a *panel*, this is a row over more *rows*.
        // Two renders of the same collapsed row would be the duplicate the
        // `RenderState` KDoc warns about, so the state that earns its picture is
        // the open one — where the seams are the whole difference.
        ComponentSpec(
            name = "ExpandingListItem",
            role = Role.Button,
            control = ControlLocator.Clickable,
            states = listOf(
                RenderState("expanded", height = 340) { modifier ->
                    ExpandingListItem(
                        expanded = true,
                        onExpandedChange = {},
                        header = { +"Perth Underground" },
                        chevron = Tabler.Outline.ChevronDown,
                        modifier = modifier,
                    ) {
                        item("Platform 1")
                        item("Platform 2")
                    }
                },
            ),
        ) { modifier, enabled, onClick ->
            ExpandingListItem(
                expanded = false,
                onExpandedChange = { onClick() },
                header = { +"Perth Underground" },
                chevron = Tabler.Outline.ChevronDown,
                modifier = modifier,
                enabled = enabled,
            ) {
                item("Platform 1")
                item("Platform 2")
            }
        }
    )

    // --- Navigation ------------------------------------------------------
    add(
        // Every navigation item rests unselected, and unselected is the state
        // with no indicator in it. The pill — its shape, its colour, where it
        // sits relative to icon and label — is what distinguishes these three
        // from one another and from a plain row, and none of it was on show.
        ComponentSpec(
            name = "NavBarItem",
            role = Role.Tab,
            states = listOf(
                RenderState("selected") { modifier ->
                    NavBarItem(
                        item = NavItem(label = "Plan", icon = Tabler.Outline.Star, onClick = {}),
                        selected = true,
                        modifier = modifier,
                    )
                },
            ),
        ) { modifier, enabled, onClick ->
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
        ComponentSpec(
            name = "NavDrawerItem",
            role = Role.Tab,
            states = listOf(
                RenderState("selected") { modifier ->
                    NavDrawerItem(
                        selected = true,
                        onClick = {},
                        key = "drawer-item",
                        modifier = modifier,
                    ) { +"Favourites" }
                },
            ),
        ) { modifier, enabled, onClick ->
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
        ComponentSpec(
            name = "NavRailItem",
            role = Role.Tab,
            states = listOf(
                RenderState("selected") { modifier ->
                    NavRailItem(
                        item = NavItem(label = "Map", icon = Tabler.Outline.Star, onClick = {}),
                        selected = true,
                        modifier = modifier,
                    )
                },
            ),
        ) { modifier, enabled, onClick ->
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
            control = ControlLocator.SelectedOption,
        ) { modifier, enabled, onClick ->
            SegmentedControl(
                options = listOf("Bus", "Train"),
                selected = 0,
                onSelectedChange = { onClick() },
                modifier = modifier,
                enabled = enabled,
            )
        }
    )

    add(
        ComponentSpec(
            name = "Tab",
            role = Role.Tab,
            states = listOf(
                // Two tabs rather than one, because the underline is drawn by
                // the *bar* and sized to the selected tab — a bar holding one
                // tab cannot show that, and showing it is the point. This is
                // the one state where the specimen is deliberately not the
                // resting one with a parameter flipped.
                RenderState("selected") { modifier ->
                    TabBar {
                        Tab(selected = true, onClick = {}, key = "departures", modifier = modifier) {
                            +"Departures"
                        }
                        Tab(selected = false, onClick = {}, key = "arrivals") { +"Arrivals" }
                    }
                },
            ),
        ) { modifier, enabled, onClick ->
            // A `Tab` only exists inside a `TabBar` — it reports its width to the
            // bar so the indicator can follow it. The tag still goes on the tab,
            // so what is asserted is the tab and not its container.
            TabBar {
                Tab(
                    selected = false,
                    onClick = onClick,
                    key = "departures",
                    modifier = modifier,
                    enabled = enabled,
                ) { +"Departures" }
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
            control = ControlLocator.SelectedOption,
        ) { modifier, enabled, onClick ->
            RadioGroup(
                options = listOf("Bus", "Train"),
                selected = "Bus",
                onSelectedChange = { onClick() },
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
            Kbd(modifier = modifier) { +"⌘K" }
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
        ComponentSpec(
            name = "SwipeToDismiss",
            role = null,
            underContract = false,
            states = listOf(
                // At rest this is a list row and nothing else, so the resting
                // render was a picture of `ListItem` under another name — which
                // is exactly the complaint that started this. The component is
                // the reveal.
                RenderState("revealed") { modifier ->
                    // `initialValue` rather than `animateTo`, and the first
                    // render is why: `animateTo` animates, a screenshot is one
                    // moment, and six frames of spring got the row about a
                    // tenth of the way open — a picture of a red sliver, which
                    // is the same nothing the resting render showed.
                    val swipe = rememberSwipeActionsState(initialValue = SwipeValue.End)
                    SwipeToDismiss(
                        onDismissRequest = {},
                        label = "Remove",
                        icon = Tabler.Outline.Star,
                        modifier = modifier,
                        state = swipe,
                    ) {
                        ListItem {
                            +"Elizabeth Quay Station"
                            supporting { +"Fremantle Line · Platform 2" }
                        }
                    }
                },
            ),
        ) { modifier, _, _ ->
            // A two-line row rather than one, in both renders, and for the
            // revealed one's sake: an action panel shows its label only where
            // there is room for one, and a single-line row has 48dp where icon
            // plus label wants 59. The resting render matches it so the pair
            // differ by the reveal and nothing else.
            SwipeToDismiss(
                onDismissRequest = {},
                label = "Remove",
                icon = Tabler.Outline.Star,
                modifier = modifier,
            ) {
                ListItem {
                    +"Elizabeth Quay Station"
                    supporting { +"Fremantle Line · Platform 2" }
                }
            }
        }
    )

    add(
        ComponentSpec(
            name = "ReorderableItem",
            role = null,
            underContract = false,
            states = listOf(
                // Lifted: the shadow, the scale and the offset that say "this row
                // is in your hand". None of it is visible at rest, and until
                // `ReorderableState.start` went public there was no way to reach
                // it from outside a gesture.
                RenderState("dragging") { modifier ->
                    val listState = rememberLazyListState()
                    val reorder = rememberReorderableState(listState) { _, _ -> }
                    LaunchedEffect(Unit) { reorder.start(0) }
                    // In a lazy list, because that is the only place a
                    // reorderable row means anything: it reads its position out
                    // of a `LazyListState`, and it animates its neighbours
                    // through `LazyItemScope`.
                    LazyColumn(state = listState, modifier = modifier) {
                        item {
                            ReorderableItem(state = reorder, index = 0, itemCount = 3) {
                                ListItem { +"Perth Underground" }
                            }
                        }
                    }
                },
            ),
        ) { modifier, _, _ ->
            val listState = rememberLazyListState()
            val reorder = rememberReorderableState(listState) { _, _ -> }
            LazyColumn(state = listState, modifier = modifier) {
                item {
                    ReorderableItem(state = reorder, index = 0, itemCount = 3) {
                        ListItem { +"Perth Underground" }
                    }
                }
            }
        }
    )

    add(
        ComponentSpec("NavDrawerSection", role = null, underContract = false) { modifier, _, _ ->
            NavDrawerSection(title = { +"Saved" }, modifier = modifier) {
                item(label = "Nearby", selected = false, onClick = {})
            }
        }
    )

    add(
        ComponentSpec("NavDrawerGroup", role = null, underContract = false) { modifier, _, _ ->
            NavDrawerGroup(
                label = { +"Lines" },
                expanded = true,
                onExpandedChange = {},
                modifier = modifier,
            ) {
                item(label = "Joondalup", selected = false, onClick = {})
            }
        }
    )
}


/**
 * The `FabMenu` every one of its specimens uses.
 *
 * Four call sites want the same three actions — the resting one and one per
 * layout — and a menu whose contents differed between them would make the three
 * renders incomparable in exactly the dimension they exist to compare.
 */
@Composable
private fun FabMenuSpecimen(
    expanded: Boolean,
    layout: FabMenuLayout,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    FabMenu(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        icon = Tabler.Outline.Plus,
        contentDescription = "Add",
        modifier = modifier,
        enabled = enabled,
        layout = layout,
    ) {
        item(Tabler.Outline.Star, "Save stop") {}
        item(Tabler.Outline.CurrentLocation, "Nearby") {}
        item(Tabler.Outline.Stack, "Routes") {}
    }
}


/**
 * The `SplitButton` both of its specimens use.
 *
 * Same label and same menu in the resting and expanded renders, so the pair
 * differs only in the thing being shown.
 */
@Composable
private fun SplitButtonSpecimen(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    SplitButton(
        onClick = onClick,
        expanded = expanded,
        onExpandedChange = {},
        menuContentDescription = "Other save options",
        modifier = modifier,
        enabled = enabled,
        menu = {
            item("Save and close", onClick = {})
            item("Save a copy", onClick = {})
        },
    ) {
        +"Save"
    }
}
