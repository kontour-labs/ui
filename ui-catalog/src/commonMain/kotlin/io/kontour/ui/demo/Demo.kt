package io.kontour.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.adaptive.windowSizeClass
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.CardVariant
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.components.text.Select
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * One hand-written, interactive demonstration of a component.
 *
 * ### Why this is not `componentRegistry`
 *
 * That list is deliberately **stateless**, and has to be. `Checkbox`'s specimen
 * is `checked = false` with the change routed into an `onActivate` the contract
 * suite observes; `Slider`'s is `onValueChange = {}` outright. Seven assertions,
 * four sweeps and 148 screenshot goldens all depend on a specimen rendering the
 * same frame every time, so making those specimens respond would break the thing
 * they exist for.
 *
 * The documentation site nevertheless mounted them and passed `{}` as the
 * callback, so a reader got a checkbox that could not be ticked and a slider
 * that could not be dragged. This is the other half: real hoisted state, written
 * by hand, for somebody who wants to press the thing.
 *
 * Two lists, then, and that is the right number — one is the answer to "does
 * this component keep its contract", the other to "what is this component
 * like". Neither question is served by the other's specimen.
 *
 * @param slug The page this belongs to — a file stem under the component pages.
 * @param knobs The controls above the demo. Empty where the variants are not
 *   the story: a component whose whole point is a gesture has nothing useful to
 *   put in a dropdown.
 */
@Immutable
class ComponentDemo(
    val slug: String,
    val knobs: List<Knob<*>> = emptyList(),
    val content: @Composable DemoScope.() -> Unit,
)

/**
 * What a demo is handed: its knob values, and somewhere to report a press.
 *
 * [echo] is the answer to the problem `LocalCatalogEcho` solves in the gallery —
 * a `Button` has no state to watch, so `onClick = {}` looks alive while doing
 * nothing, and this project has twice found a real defect hiding behind exactly
 * that. The gallery raises a toast. A documentation page cannot: a toast is
 * gone in four seconds and invisible in a screenshot, and the render sweep is
 * how these get reviewed. So the last action is written under the demo and
 * stays there.
 */
@Stable
class DemoScope internal constructor(
    private val knobs: Knobs,
    private val lastAction: MutableState<String?>,
) {
    /** The current setting of [knob]. */
    operator fun <T> get(knob: Knob<T>): T = knobs[knob]

    /** Records that a callback fired, for a control with nothing else to show. */
    fun echo(what: String) {
        lastAction.value = what
    }
}

/**
 * One control above a demo.
 *
 * Two kinds cover the library, which is the whole design: a props framework that
 * can express every parameter of every component would be a bigger thing than
 * the components, and the parameters worth putting a knob on are nearly always
 * "which variant", "which size" and "is it disabled".
 *
 * A knob is its own key. Declare it as a file-private top-level `val` and read
 * it back with `knobs[variant]` — there is no string to mistype and no map to
 * keep in step, and the compiler knows the type of what comes back.
 */
sealed class Knob<T>(val label: String, val initial: T) {

    /**
     * A choice between several values, usually an enum's `entries`.
     *
     * Taking the options from `ButtonVariant.entries` rather than listing them
     * is the anti-drift device that costs nothing: an eighth variant grows the
     * demo by itself, and a removed one stops compiling here.
     */
    class Choice<T>(
        label: String,
        val options: List<T>,
        initial: T = options.first(),
        val name: (T) -> String = { it.toString() },
    ) : Knob<T>(label, initial)

    /** On or off — `enabled`, `loading`, `selected`. */
    class Flag(label: String, initial: Boolean = false) : Knob<Boolean>(label, initial)
}

/** What every knob is set to, right now. */
@Stable
class Knobs internal constructor(
    private val values: Map<Knob<*>, MutableState<Any?>>,
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(knob: Knob<T>): T = values.getValue(knob).value as T

    internal fun set(knob: Knob<*>, value: Any?) {
        values.getValue(knob).value = value
    }
}

@Composable
fun rememberKnobs(knobs: List<Knob<*>>): Knobs = remember(knobs) {
    Knobs(knobs.associateWith { mutableStateOf<Any?>(it.initial) })
}

/**
 * A demo, with its controls above it.
 *
 * Public so the site and the gallery draw the same thing rather than two things
 * that are supposed to look alike.
 */
@Composable
fun DemoCard(demo: ComponentDemo, modifier: Modifier = Modifier) {
    val knobs = rememberKnobs(demo.knobs)
    val lastAction = remember(demo) { mutableStateOf<String?>(null) }
    val scope = remember(knobs, lastAction) { DemoScope(knobs, lastAction) }

    Card(variant = CardVariant.Outlined, modifier = modifier.fillMaxWidth()) {
        if (demo.knobs.isNotEmpty()) {
            KnobRow(demo.knobs, knobs)
            // A rule between the controls and the thing they control.
            //
            // Without it `Switch`'s card is an "Enabled" switch above a "Show
            // live vehicles" switch and the two read as one list of settings —
            // which is the one reading that makes the demo unintelligible,
            // since half of it is the demo and half is the apparatus.
            HorizontalDivider(Modifier.padding(top = Theme.spacing.md))
        }
        Column(Modifier.fillMaxWidth().padding(top = Theme.spacing.md)) {
            demo.content(scope)
        }
        lastAction.value?.let { action ->
            Text(
                text = action,
                style = Theme.typography.monoLabel,
                color = Theme.colors.contentMuted,
                modifier = Modifier.padding(top = Theme.spacing.md),
            )
        }
    }
}

/**
 * A demo's content with some knobs pinned, for a test that sweeps them.
 *
 * The card is skipped deliberately: a sweep over every setting of every knob is
 * asking whether the *component* draws in that state, and rendering the knob row
 * seventy times over would make every one of those renders pass on the strength
 * of the controls alone.
 */
@Composable
internal fun DemoContentForTest(demo: ComponentDemo, overrides: Map<Knob<*>, Any?>) {
    val scope = remember(demo, overrides) {
        val values = demo.knobs.associateWith { knob ->
            mutableStateOf(if (knob in overrides) overrides[knob] else knob.initial)
        }
        DemoScope(Knobs(values), mutableStateOf(null))
    }
    demo.content(scope)
}

/**
 * The controls, wrapped.
 *
 * `FlowRow` rather than `Row` for the reason `ShowcaseLayout`'s KDoc sets out at
 * length: a `Row` gives the first child everything it asks for and leaves the
 * rest slivers, and a `Switch` measured at zero width used to throw from inside
 * draw. Four knobs will not fit across a phone, and wrapping is the honest fix.
 */
@Composable
private fun KnobRow(knobs: List<Knob<*>>, values: Knobs) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        knobs.forEach { knob ->
            when (knob) {
                is Knob.Flag -> FlagKnob(knob, values)
                is Knob.Choice<*> -> ChoiceKnob(knob, values)
            }
        }
    }
}

@Composable
private fun FlagKnob(knob: Knob.Flag, values: Knobs) {
    val checked = values[knob]
    SelectionRow(
        selected = checked,
        onSelectedChange = { values.set(knob, it) },
        role = Role.Switch,
        modifier = Modifier.widthIn(max = KnobWidth),
    ) {
        +knob.label
        trailing { Switch(checked = checked, onCheckedChange = null) }
    }
}

/**
 * A short choice is a segmented control; a long one is a select.
 *
 * The threshold is about where a segmented control stops being readable rather
 * than about a number of options in the abstract — seven `ButtonVariant`s side
 * by side are seven unreadable slivers even on a desktop. On a phone everything
 * becomes a select, because the widest control that fits is one row.
 */
@Composable
private fun <T> ChoiceKnob(knob: Knob.Choice<T>, values: Knobs) {
    val selected = values[knob]
    val labels = remember(knob) { knob.options.map(knob.name) }
    val compact = windowSizeClass.width == WindowWidthClass.Compact

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
        Text(
            text = knob.label,
            style = Theme.typography.labelSmall,
            color = Theme.colors.contentMuted,
        )
        if (labels.size <= SegmentedLimit && !compact) {
            SegmentedControl(
                options = labels,
                selected = knob.options.indexOf(selected).coerceAtLeast(0),
                onSelectedChange = { values.set(knob, knob.options[it]) },
            )
        } else {
            Select(
                value = selected,
                options = knob.options,
                onValueChange = { values.set(knob, it) },
                optionLabel = knob.name,
                modifier = Modifier.widthIn(max = KnobWidth),
            )
        }
    }
}

/** Beyond this a segmented control's options stop being readable. */
private const val SegmentedLimit = 4

private val KnobWidth = 260.dp
