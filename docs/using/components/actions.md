# Actions

Things the user presses to make something happen.

| | For | Instead of |
|---|---|---|
| [`Button`](#button) | An action with a name | An `IconButton`, when there is room for the word |
| [`IconButton`](#iconbutton) | An action with no room for a name | A `Button`, whenever there is room |
| [`IconToggleButton`](#icontogglebutton) | An icon that is on or off — favourite, mute | A `Switch`, when the state deserves a label |
| [`FloatingActionButton`](#floatingactionbutton) | The one action a whole screen exists for | A `Button`, for anything else |
| [`Spinner`](#spinner) | Work is happening, duration unknown | `LinearProgress`, when you know the fraction |

---

## `Button`

```kotlin
Button(onClick = ::plan) {
    +"Plan a trip"
}

Button(
    onClick = ::delete,
    variant = ButtonVariant.Destructive,
    size = ButtonSize.Small,
) {
    +Tabler.Outline.Trash
    +"Delete"
}
```

The content is a `RowContentScope`, so `+"text"` and `+icon` are the whole
vocabulary — see [`dsls.md`](../dsls.md). There is no `label: String`: a button
whose content is a slot can hold a label with a badge, a two-line label, or a
row with a trailing chevron, and none of those needed a new parameter.

### Seven variants, chosen by importance rather than appearance

| Variant | For | |
|---|---|---|
| `Primary` | The one action the screen exists for. At most one per screen. Solid, near-black | ![](../../../../../app/ui-catalog/screenshots/components/button-primary-light.png) |
| `Accent` | The place the product should show up as itself — "Get started", "Plan a trip". Solid, in the accent tone | ![](../../../../../app/ui-catalog/screenshots/components/button-accent-light.png) |
| `Secondary` | A real alternative to the primary action. Outlined | ![](../../../../../app/ui-catalog/screenshots/components/button-secondary-light.png) |
| `Tertiary` | Supporting actions that should not compete. Filled with a soft ground | ![](../../../../../app/ui-catalog/screenshots/components/button-tertiary-light.png) |
| `Ghost` | Lowest weight — toolbar actions, inline "edit". No ground until hovered | ![](../../../../../app/ui-catalog/screenshots/components/button-ghost-light.png) |
| `Destructive` | Deletes, cancels a booking, ends a trip | ![](../../../../../app/ui-catalog/screenshots/components/button-destructive-light.png) |
| `DestructiveGhost` | A destructive action that should not shout — inside a menu or row | ![](../../../../../app/ui-catalog/screenshots/components/button-destructiveghost-light.png) |

`Primary` and `Accent` are **alternatives to each other, not companions**. Two
solid buttons on one screen is still one too many.

Five sizes, `XSmall` to `XLarge`, drawn from `Theme.sizing.controlHeight*` so a
row of mixed buttons, inputs and selects lines up without per-call-site padding.
`Medium` is the default; `Large`/`XLarge` are for a screen's single main call to
action.

### States

**Loading** swaps the label for a spinner *without changing the button's width*,
so a row of buttons does not reflow when one is pressed. The button also blocks
input and announces itself as busy while loading — a screen-reader user is not
left tapping a control that already took their input.

**Disabled** styling is shared across variants on purpose: a disabled outlined
button and a disabled solid one both mean "not available right now", and should
not look like two different controls.

### Accessibility

The content slot is the accessible name, so a button whose content is only an
icon has no name — that is what `IconButton` is for, and why its
`contentDescription` is required. `ComponentContractTest` fails a registered
component that announces nothing, which is what replaced the guarantee the old
`label: String` parameter gave.

---

## `IconButton`

![IconButton](../../../../../app/ui-catalog/screenshots/components/iconbutton-light.png)

```kotlin
IconButton(Tabler.Outline.X, contentDescription = "Close", onClick = ::dismiss)

IconButton(
    icon = Tabler.Outline.ChevronDown,
    contentDescription = "Expand",
    onClick = ::toggle,
    rotation = if (expanded) 180f else 0f,
)
```

`contentDescription` is **required and non-null**. There is no visible text to
fall back on, so an icon button without one is a control a screen-reader user
cannot identify. If the icon is genuinely decorative, it is not a button.

`rotation` animates, which is what makes a disclosure chevron read as the same
arrow turning rather than two different glyphs swapping.

It defaults to `ButtonVariant.Ghost` and `Theme.shapes.pill`, because an icon
button is nearly always a low-weight action inside something else. Both are
parameters when it is not.

## `IconToggleButton`

![IconToggleButton](../../../../../app/ui-catalog/screenshots/components/icontogglebutton-light.png)

```kotlin
IconToggleButton(
    icon = Tabler.Outline.Star,
    contentDescription = "Favourite",
    checked = isFavourite,
    onCheckedChange = viewModel::setFavourite,
)
```

Announces `Role.Checkbox`, not `Role.Switch`. It reads as one — a star that is
on or off — but a switch describes the sliding control, and a screen reader that
says "switch" for a star describes a widget that is not on screen. The contract
suite found this component announcing `Role.Switch` from a wrapper `Box` around
an `IconButton` that announced `Role.Button`: a switch containing a button, and
the wrong role either way.

**Reach for `SelectionRow` with a `Switch` instead** when the thing being toggled
deserves a written label. An icon toggle is for a dense row of actions where the
glyph is the whole affordance.

---

## `FloatingActionButton`

![FloatingActionButton](../../../../../app/ui-catalog/screenshots/components/floatingactionbutton-light.png)

```kotlin
FloatingActionButton(Tabler.Outline.Plus, "Add favourite", onClick = ::add)
```

One per screen. A second FAB is two competing "the" actions.

## `ExtendedFloatingActionButton`

![ExtendedFloatingActionButton](../../../../../app/ui-catalog/screenshots/components/extendedfloatingactionbutton-light.png)

```kotlin
ExtendedFloatingActionButton(
    icon = Tabler.Outline.Navigation,
    label = "Start",
    contentDescription = "Start trip to Perth Station",
    expanded = !listState.isScrollingDown,
    onClick = ::start,
)
```

It animates its *width* when collapsing rather than cross-fading between two
components, so the icon stays put and the label slides out from behind it.
Cross-fading makes the icon appear to jump sideways. `NavRail` uses the same
treatment when it expands.

`contentDescription` is separate from `label` because the label may be terse
where the announcement should not be — "Start" on screen, "Start trip to Perth
Station" for a screen reader.

---

## `Spinner`

An indeterminate activity indicator. The arc sweeps *and* breathes — its length
grows and shrinks as it rotates, so the tail chases the head. Under reduced
motion the breathing stops and the arc holds a constant length.

```kotlin
Spinner(contentDescription = "Loading departures")
```

`contentDescription` defaults to `null`, which is right when the spinner sits
inside something that already announces itself as busy — a loading `Button`
does, so its spinner is silent. Pass one when the spinner is the only thing
saying work is happening.

**Reach for `LinearProgress` or `ProgressRing` instead** when the fraction is
known. A spinner says "wait"; a progress bar says how long, and people wait
longer when they can see the end. Both are in
[display.md](display.md#progress).
