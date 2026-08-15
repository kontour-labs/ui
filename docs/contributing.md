# Adding a component

Read this once before your first component. After that the checklist at the
bottom is enough.

---

## Where it goes

```
app/ui/src/commonMain/kotlin/io/kontour/ui/
  components/
    action/       buttons, FABs, links
    selection/    checkboxes, switches, sliders, chips
    datetime/     pickers, calendars
    text/         text fields
    display/      cards, badges, avatars, progress, banners
    collection/   list items, tables, swipe actions
  nav/            bars, rails, drawers, tabs
  overlay/        dialogs, menus, tooltips, toasts
  sheet/          bottom and side sheets
```

One file per component, named after it. A component and its `Defaults` object
live together; a family that shares internals (all the chip variants, say) may
share a file.

---

## The shape of a component

```kotlin
/**
 * One sentence saying what it is.
 *
 * A paragraph on when to reach for this rather than its neighbours — that is
 * the part a reader actually needs, and the part they cannot get from the
 * signature.
 *
 * ```
 * Button(onClick = ::submit, variant = ButtonVariant.Primary) {
 *     Text("Save")
 * }
 * ```
 *
 * @param enabled When false, the component is non-interactive and drawn in
 *   [ColorScheme.contentDisabled]. It stays in the accessibility tree.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
)
```

Parameter order is Material's, because it is a good convention and everyone
already knows it:

1. required behaviour (`onClick`, `value`/`onValueChange`, `checked`)
2. `modifier` — **the first optional parameter, always**
3. `enabled` — **directly after `modifier`, always**
4. content (`label`, `supporting`, `leadingIcon`)
5. appearance (`variant`, `size`, `colors`, `shape`)
6. `windowInsets`, where the component sits at the window's edge
7. `interactionSource` — after every non-slot parameter
8. slots — trailing lambda last

Rules 2, 3 and 7 are checked by `./gradlew :ui:checkApiConventions`, which
`check` depends on. They were established in one sweep and would otherwise drift
apart again one component at a time, which is exactly how they got out of order
the first time.

`enabled` sits directly after `modifier` with no exceptions. An earlier draft let
it follow an optional `onClick` instead — which reads better on the three
components where it applies, and misfired on the first component with two
callbacks. A rule with an exception is a rule nobody can apply without looking it
up.

### The words

One name per idea, across the whole library. A second name for the same thing is
how an API stops feeling like one library, and it is the thing a reader notices
first.

| Word | Means | Not |
|---|---|---|
| `label` | The short name of a control — a button, chip, tab, field, menu item, row | `text`, `headline`, `title` |
| `title` | The heading of a *region* — a dialog, a sheet, a section, a top bar | `label` |
| `message` | The prose under a title | `body`, `description` |
| `supporting` | Secondary text under a label | `supportingText`, `subtitle` |
| `placeholder` | What a field shows while empty | `hint` |
| `text` | A body of text in a text-rendering primitive — `Text`, `Tooltip` | anything with a shorter name |
| `leading` / `trailing` | A composable slot at one end | |
| `leadingIcon` / `trailingIcon` | An `ImageVector` at one end — the same slot, without the ceremony | |
| `onDismissRequest` | *You* own `visible`; the component is asking to be hidden | `onDismiss` |
| `onDismiss` | The *host* owns visibility; it has already gone and is telling you | |
| `onClose` / `onDismissSupporting` | A close *control* was pressed. What it closes is something else — `SheetHeader`'s button closes the sheet, not the header — and `null` means "no button", not "not dismissible" | |
| `enabled` | This component's own state | `isEnabled` |
| `windowInsets` | What this component keeps its content clear of | |

A predicate is named for what it decides, never `isEnabled` — `DatePicker` takes
`isDateSelectable`, because `enabled` already means "is the whole picker usable".

The banned column is enforced by `checkApiConventions` too.

### If it gets a shorthand

Some components also carry a scope of small builder functions — see
[`dsls.md`](dsls.md). Three rules, and they are what stops a shorthand turning
into a second, worse copy of the component:

1. **The scope extends the receiver the content lambda already had.** `MenuScope`
   is a `ColumnScope`, so every existing body still compiles and the component is
   always in reach. A shorthand that shadowed the component would be a wall
   rather than a convenience.
2. **No `modifier` parameter, and the action lambda goes last.** A row that needs
   a modifier has outgrown the shorthand. The trailing lambda is the one place a
   builder is ordered differently from a component, and it is deliberate: a
   builder is read as a list of things, not as a call.
3. **It has to delete a mistake, not just characters.** `item` closes the menu;
   `ListGroup` counts the rows; `group` carries the indent. A shorthand that only
   saves typing is a second API to keep in sync for no return.

**Defaults live in a `<Component>Defaults` object**, never inline in the
signature, so a caller can reference and override one value:

```kotlin
object ButtonDefaults {
    @Composable
    fun colors(variant: ButtonVariant): ButtonColors = …
}
```

---

## The rules

These are asserted by the shared contract suite
(`ui/src/commonTest/…/contract/`) over every component in
`componentRegistry`. A component that skips one fails CI.

They are not hypothetical. The suite failed on its first run and found six real
bugs that had survived every screenshot review — see [Registering a
component](#registering-a-component) below.

**1. `modifier: Modifier = Modifier`, first optional parameter.** Applied to the
outermost layout node, before anything the component adds itself, so a caller's
`padding` sits outside the component's own.

**2. Accept an `interactionSource`, honour `enabled`.** Nullable, defaulting to
`null` — create one internally when not supplied, so callers who do not care do
not allocate. A disabled component takes no input, draws in disabled colours,
and stays in the accessibility tree marked disabled.

Note the second half of rule 2, which is the one that gets dropped. It is
tempting to write `if (enabled) Modifier.clickable(…) else Modifier` — the
callback cannot fire, so the component *looks* correct. But a node with no
`clickable` has no role and no disabled flag: a screen reader announces the
control as plain text, and the user has no way to tell it is unavailable rather
than broken. Keep the modifier and pass `enabled = false` to it; foundation does
the blocking and the reporting together.

**3. Correct semantics.** A `Role`, and a `stateDescription` wherever state is
not implied by the role. `contentDescription` is a *required* parameter on
anything icon-only. Withhold the *action* too, not just the flag — a disabled
control that still exposes `setProgress` or `onClick` to semantics can be
operated by assistive tech while looking inert.

**4. A visible label names the control.** Compose has no `labelledBy`: a label
drawn above a field is an unrelated node however close it is on screen. Set it
as the control's `contentDescription`, or place it inside a node that merges
into the control. `FieldScaffold` does this for every form control — the label
is drawn with `clearAndSetSemantics {}` and the control carries the name — so a
new field that goes through the scaffold gets it for free, and one that does not
must do it itself.

**5. `Modifier.minimumTouchTarget()` on anything interactive.** Before any
`size` modifier, so it wraps the visual size rather than being overridden by it.
Key it on *being a control*, not on being enabled, or the component changes
height when it greys out and the layout around it jumps.

**6. Tokens only.** No `Color(0xFF…)`, no `12.dp` radius, no `220` duration.
`Theme.colors`, `Theme.shapes`, `Theme.motion`. If the value you need does not
exist, add a token — do not inline it "just this once".

---

## Interaction

Use the shared mechanisms rather than rolling your own:

```kotlin
val interactions = interactionSource ?: remember { MutableInteractionSource() }

Box(
    Modifier
        .minimumTouchTarget()
        .focusRing(interactions, shape)      // before clip
        .clip(shape)
        .background(colors.container, shape)
        .clickable(
            interactionSource = interactions,
            indication = kontourIndication(shape),
            enabled = enabled,
            onClick = onClick,
        )
)
```

`kontourIndication` handles press, hover and drag, and already knows about
reduced motion and whether the pointer can hover. Pass `pressScale = 1f` for
anything large — a list row or a sheet should not flinch when touched; a button
should.

For haptics, ask for an *intent*, not a constant:

```kotlin
Theme.feedback.perform(FeedbackIntent.Selection)
```

---

## Tests

Every component needs, in `commonTest`:

- **Registration in the contract suite**, which covers the rules above.
- **Behaviour tests** for anything the contract suite cannot know: that a slider
  clamps, that a text field's output transformation masks correctly, that a
  sheet settles on the detent it was sent to.
- **A catalog entry** covering every state — default, hovered, pressed, focused,
  disabled, loading, error, long content, empty content.

Run them with:

```sh
./gradlew :ui:jvmTest
```

### Registering a component

One entry in `componentRegistry`, and all six rules apply to it:

```kotlin
add(
    ComponentSpec("Chip", Role.Button) { modifier, enabled, onClick ->
        Chip(label = "Bus", onClick = onClick, modifier = modifier, enabled = enabled)
    }
)
```

The defaults assume the common case: the tagged node is the control, a tap
operates it, and it owns its own touch target. Four parameters relax that where
a component genuinely differs:

| | |
|---|---|
| `expectsMinimumTarget = false` | The component sits *inside* something that owns the target — a control in a `SelectionRow` — or sizes itself from content, like a text field |
| `activatedByClick = false` | A tap does not operate it: a slider is dragged, a field is typed into. Skips the callback half of rule 2, keeps the rest |
| `control = hasClickAction()` | The tagged node is a container and the control is inside it. An `Accordion` is a header plus a panel, and merging the panel into the header would swallow the whole disclosed body into its announcement |
| `accessibleName = "Origin"` | Rendered here with a visible label, so rule 4 applies |

Reach for these only when the component really is that shape. Every one of them
narrows what is checked, and the six bugs the suite found on its first run were
all in components that looked fine:

- `ListItem` and `SettingRow` dropped their `clickable` entirely when disabled,
  so a disabled row announced as plain text with no role and no disabled state.
- `IconToggleButton` put its toggle semantics on a `Box` wrapping an
  `IconButton`, producing a node tree that read as a switch containing a button
  — and announced `Role.Switch` for what is a tick, not a setting.
- A disabled `Slider` still exposed `setProgress`, so assistive tech could move
  a control that looked inert.
- No text field or select carried its label as an accessible name.

None of these are visible in a screenshot. That is the argument for a registry
over per-component tests: nobody writes a test for the component they think is
fine.

Screenshot goldens are generated from the catalog entry, so adding the entry is
what gets you visual regression coverage. Review the generated PNG before
committing it — a golden nobody looked at pins whatever bug was present when it
was recorded.

---

## Documentation

- **KDoc on every public declaration**, with a usage snippet on the component
  itself. Say when to use it rather than restating the signature.
- **A row in the relevant page** under `docs/app/design-system/`.
- If the component introduces a concept — a new state holder, a new overlay
  layer — it gets a section explaining the concept, not just the API.

---

## Checklist

```
[ ] modifier: Modifier = Modifier, first optional parameter
[ ] interactionSource accepted; enabled honoured
[ ] semantics: Role, stateDescription, contentDescription where icon-only
[ ] Modifier.minimumTouchTarget() on the interactive area
[ ] tokens only — no literal colours, radii or durations
[ ] kontourIndication for press/hover; focusRing before clip
[ ] defaults in a <Component>Defaults object
[ ] registered in the contract suite
[ ] behaviour tests for what the contract suite cannot know
[ ] catalog entry covering every state
[ ] screenshot golden reviewed, not just accepted
[ ] KDoc with a usage snippet and a "when to use this" paragraph
[ ] documented in docs/app/design-system/
[ ] checked at 200% font scale and in RTL
```
