# Actions

Things the user presses to make something happen.

| | For | Instead of |
|---|---|---|
| [`Button`](#button) | An action with a name | An `IconButton`, when there is room for the word |
| [`IconButton`](#iconbutton) | An action with no room for a name | A `Button`, whenever there is room |
| [`IconToggleButton`](#icontogglebutton) | An icon that is on or off — favourite, mute | A `Switch`, when the state deserves a label |
| [`FloatingActionButton`](#floatingactionbutton) | The one action a whole screen exists for | A `Button`, for anything else |
| [`FabMenu`](#fabmenu) | Several actions behind the one FAB | A `Toolbar`, when they belong on the chrome |
| [`SplitButton`](#splitbutton) | One usual action, with variants a tap away | A `Button` + menu, when there is no *usual* one |
| [`ButtonGroup`](#buttongroup) | Related actions that read as one control | `SegmentedControl`, when one is *selected* |
| [`Toolbar`](#toolbar) | A floating surface of actions over other content | `TopBar`, when it is the screen's own chrome |
| [`Spinner`](#spinner) | Work is happening, duration unknown | `LinearProgress`, when you know the fraction |

---

## `Button`

<!--sample:ButtonBasics-->
```kotlin
Button(onClick = { plan() }) {
    +"Plan a trip"
}

Button(
    onClick = { delete() },
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
| `Primary` | The one action the screen exists for. At most one per screen. Solid, near-black | ![](../../../ui-catalog/screenshots/components/button-primary-light.png) |
| `Accent` | The place the product should show up as itself — "Get started", "Plan a trip". Solid, in the accent tone | ![](../../../ui-catalog/screenshots/components/button-accent-light.png) |
| `Secondary` | A real alternative to the primary action. Outlined | ![](../../../ui-catalog/screenshots/components/button-secondary-light.png) |
| `Tertiary` | Supporting actions that should not compete. Filled with a soft ground | ![](../../../ui-catalog/screenshots/components/button-tertiary-light.png) |
| `Ghost` | Lowest weight — toolbar actions, inline "edit". No ground until hovered | ![](../../../ui-catalog/screenshots/components/button-ghost-light.png) |
| `Destructive` | Deletes, cancels a booking, ends a trip | ![](../../../ui-catalog/screenshots/components/button-destructive-light.png) |
| `DestructiveGhost` | A destructive action that should not shout — inside a menu or row | ![](../../../ui-catalog/screenshots/components/button-destructiveghost-light.png) |

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

<!--sample:ButtonLoading-->
```kotlin
var saving by remember { mutableStateOf(false) }

Button(onClick = { saving = true }, loading = saving) {
    +"Save this trip"
}
```

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

![IconButton](../../../ui-catalog/screenshots/components/iconbutton-light.png)

<!--sample:IconButtonBasics-->
```kotlin
IconButton(Tabler.Outline.X, contentDescription = "Close", onClick = { dismiss() })
```

`contentDescription` is **required and non-null**. There is no visible text to
fall back on, so an icon button without one is a control a screen-reader user
cannot identify. If the icon is genuinely decorative, it is not a button.

`rotation` animates, which is what makes a disclosure chevron read as the same
arrow turning rather than two different glyphs swapping.

<!--sample:IconButtonRotation-->
```kotlin
var expanded by remember { mutableStateOf(false) }

IconButton(
    icon = Tabler.Outline.ChevronDown,
    contentDescription = if (expanded) "Collapse" else "Expand",
    onClick = { expanded = !expanded },
    rotation = if (expanded) 180f else 0f,
)
```

It defaults to `ButtonVariant.Ghost` and `Theme.shapes.pill`, because an icon
button is nearly always a low-weight action inside something else. Both are
parameters when it is not.

## `IconToggleButton`

![IconToggleButton, unchecked](../../../ui-catalog/screenshots/components/icontogglebutton-light.png)
![IconToggleButton, checked](../../../ui-catalog/screenshots/components/icontogglebutton-checked-light.png)

The first of those is pixel-for-pixel an [`IconButton`](#iconbutton), and that is
not a mistake in the picture: unchecked, it *is* one. The accent ground in the
second is the entire visible difference between the two components.

<!--sample:IconToggleButtonBasics-->
```kotlin
var favourite by remember { mutableStateOf(false) }

IconToggleButton(
    icon = Tabler.Outline.Star,
    contentDescription = "Favourite",
    checked = favourite,
    onCheckedChange = { favourite = it },
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

![FloatingActionButton](../../../ui-catalog/screenshots/components/floatingactionbutton-light.png)

<!--sample:FloatingActionButtonBasics-->
```kotlin
FloatingActionButton(Tabler.Outline.Plus, "Add favourite", onClick = { add() })
```

One per screen. A second FAB is two competing "the" actions.

## `ExtendedFloatingActionButton`

![ExtendedFloatingActionButton](../../../ui-catalog/screenshots/components/extendedfloatingactionbutton-light.png)

<!--sample:ExtendedFloatingActionButtonCollapsing-->
```kotlin
ExtendedFloatingActionButton(
    icon = Tabler.Outline.Navigation,
    contentDescription = "Start trip to Perth Station",
    expanded = listState.firstVisibleItemIndex == 0,
    onClick = { start() },
) {
    +"Start"
}
```

It animates its *width* when collapsing rather than cross-fading between two
components, so the icon stays put and the label slides out from behind it.
Cross-fading makes the icon appear to jump sideways. `NavRail` uses the same
treatment when it expands.

`contentDescription` is separate from `label` because the label may be terse
where the announcement should not be — "Start" on screen, "Start trip to Perth
Station" for a screen reader.

## `FabMenu`

![FabMenu](../../../ui-catalog/screenshots/components/fabmenu-vertical-light.png)

<!--sample:FabMenuBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

FabMenu(
    expanded = open,
    onExpandedChange = { open = it },
    icon = Tabler.Outline.Plus,
    contentDescription = "Add",
) {
    item(Tabler.Outline.Star, "Save stop") { save(); open = false }
    item(Tabler.Outline.CurrentLocation, "Nearby") { nearby(); open = false }
    item(Tabler.Outline.Navigation, "Directions") { start(); open = false }
}
```

The anchor **is** a `FloatingActionButton` — same `FabSize`, same shape, same
press scale — so a screen that already has a FAB gains a menu by changing the
call rather than by swapping the component for a lookalike. The plus rotates 45°
into a cross as it opens; pass `expandedIcon` when the resting icon is something
a rotation does not usefully transform.

**Three layouts, and none of them takes a direction.**

| | | |
|---|---|---|
| `Vertical` | ![vertical](../../../ui-catalog/screenshots/components/fabmenu-vertical-light.png) | The default. Labelled, because a column has room. |
| `Horizontal` | ![horizontal](../../../ui-catalog/screenshots/components/fabmenu-horizontal-light.png) | A row beside the button. |
| `Fan` | ![fan](../../../ui-catalog/screenshots/components/fabmenu-fan-light.png) | An arc. Icons only — a diagonal leaves a label nowhere to go. |

All three pick which way to open from where the button finds itself in the
window: bottom-right opens up and to the left, top-left opens down and to the
right, and nothing has to be told which corner it is in. Where the room runs out
the spacing **compresses** rather than clamping — clamping each item to the
window independently puts every item past the wall on the same point, and three
actions become one pile with two of them unreachable.

**The items render into the [`OverlayHost`](../overlays.md)**, anchored to the
FAB, for the reason a menu does: a FAB sits in a corner, and items expanding out
of a corner leave whatever box put it there. The FAB itself stays put, behind a
transparent scrim — so tapping it again closes the menu without a second handler,
the same bargain `DropdownMenu` strikes with its trigger. Pass
`scrim = ScrimStyle.Dimmed` where the actions deserve the whole screen.

<!--sample:FabMenuFan-->
```kotlin
var open by remember { mutableStateOf(false) }

FabMenu(
    expanded = open,
    onExpandedChange = { open = it },
    icon = Tabler.Outline.Stack,
    contentDescription = "Map layers",
    layout = FabMenuLayout.Fan,
    expandedIcon = Tabler.Outline.X,
    scrim = ScrimStyle.Dimmed,
) {
    item(Tabler.Outline.Bus, "Buses") { openLayers() }
    item(Tabler.Outline.Train, "Trains") { openLayers() }
    item(Tabler.Outline.Bike, "Bike paths") { openLayers() }
}
```

The items leave **one after another**, nearest first, and gather back into the
button furthest-first — which is what makes it read as one thing unfolding
rather than five things appearing. Under `reduceMotion` the stagger is dropped
entirely: a sequence is still movement, and it drags the eye across the screen
exactly as that preference asks it not to.

Each item is a real button with its own touch target, so the 48dp minimum
applies to every one of them rather than to the menu as a whole.

> Items default to `surfaceRaised` with a **hairline border**, and the border is
> not decoration. In the light scheme `background`, `surface` and `surfaceRaised`
> are all the same white, so a light FAB without it is a white circle on a white
> page held together by its shadow alone — legible over a map, and not much else.
> It is the same hairline `OverlaySurface` puts round every menu and popover.
> Pass `itemBorder = null` on a menu that only ever floats over photography.

## `SplitButton`

![SplitButton](../../../ui-catalog/screenshots/components/splitbutton-expanded-light.png)

<!--sample:SplitButtonBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

SplitButton(
    onClick = { save() },
    expanded = open,
    onExpandedChange = { open = it },
    menuContentDescription = "Other save options",
    menu = {
        item("Save and close", onClick = { saveAndClose() })
        item("Save a copy", onClick = { saveCopy() })
    },
) {
    +"Save"
}
```

The left half runs the **default** action immediately; the right half opens the
rest. That division is the whole component, and it is what separates it from a
`Button` that opens a menu: here the common case costs one tap and never shows a
list.

The two halves sit flush with a hairline between them and only the outside
corners round — the same `ButtonGroupPosition.shape` a
[`ButtonGroup`](#buttongroup) uses, because it is the same idea: separate targets
that read as one control. The pair owns the touch target between them for the
reason `ButtonGroup` does, so the reserved slack does not land in the seam and
turn a 1dp join into a 9dp gap.

**Reach for a plain `Button` and a `DropdownMenu`** when there is no default. A
split button whose main half also opens the menu is a wide chevron. And reach for
[`ButtonGroup`](#buttongroup) when the alternatives are *equal* — three ways of
doing a thing, none of them the usual one — since a split button claims one of
them is the answer.

`menuContentDescription` is required and separate from the label: the chevron
half has no text of its own, and a screen reader that reads "Save, Save" for the
two halves has described neither.

---

## `ButtonGroup`

![ButtonGroup](../../../ui-catalog/screenshots/components/buttongroup-light.png)

<!--sample:ButtonGroupBasics-->
```kotlin
ButtonGroup {
    item(onClick = { zoomOut() }, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
    item(
        onClick = { recentre() },
        contentDescription = "Recentre",
        icon = Tabler.Outline.CurrentLocation,
    )
    item(onClick = { zoomIn() }, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
}
```

The buttons sit flush and only the outside corners round — the same treatment
[`ListItemPosition`](collections.md#listitem) gives a group of rows. That is the
whole visual idea: three separate buttons say "three things", one joined group
says "one thing, three ways".

It is a **builder**, not a row of `Button`s, because each button's shape depends
on how many there are — which is not known until they have all been declared.
Note that the builder collects rather than composes, so a `@Composable` helper
cannot be called inside it; hoist the value first.

### Not a `SegmentedControl`

They look almost identical and mean opposite things:

| | `ButtonGroup` | [`SegmentedControl`](selection.md#segmentedcontrol) |
|---|---|---|
| Each item is | an action | an option |
| Something is selected | no | always exactly one |
| Role | `Button` | `RadioButton` |
| Pressing one | does something | changes a value |

A segmented control with no selection is broken; a button group with a selection
is a segmented control wearing the wrong clothes. **If the row answers a
question, it is a segmented control.**

### Not `TopBar`'s `actions`

That slot holds the *screen's* actions, separated from each other. This is a
cluster that belongs together — zoom in and zoom out — and the joining is what
says so. A [`TopBar`](navigation.md#topbar) can hold one of these in its slot.

Any single action can be disabled without the others, which is what lets a
cluster grey a button rather than hide it. A cluster that changes width as you
use it is worse than one with a greyed button in it.

## `Toolbar`

![Toolbar](../../../ui-catalog/screenshots/components/toolbar-light.png)

<!--sample:ToolbarBasics-->
```kotlin
Toolbar {
    ButtonGroup {
        item(
            onClick = { zoomOut() },
            contentDescription = "Zoom out",
            icon = Tabler.Outline.Minus,
        )
        item(onClick = { zoomIn() }, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
    }
    ToolbarDivider()
    IconButton(Tabler.Outline.Stack, "Map layers", onClick = { openLayers() })
}
```

A floating surface holding actions, over content it does not belong to — the
controls over the map.

**Deliberately thin**: a `Surface` and a `Row`. It earns its place the way
[`Card`](display.md#card) does, by fixing the elevation, shape, padding and
traversal semantics in one place so a second toolbar does not grow a second set
of numbers.

**Its corners are concentric with what it holds.** `ToolbarDefaults.Shape` is
one step up the shape scale from the controls inside — the scale climbs in 4dp
steps and the content padding is 4dp, so `medium` around `small` puts the two
curves exactly parallel. It was a pill until a `ButtonGroup`'s corners were
found poking through it and being sheared flat. If your toolbar holds nothing
but circular `IconButton`s, `Theme.shapes.pill` is the concentric choice there
and worth passing.

**It is not a [`TopBar`](navigation.md#topbar).** A top bar is *part of* the
screen — it holds the title and sits at the top. A toolbar floats **over**
content that is not its own, which is why it has a shadow and rounded corners
and a top bar has neither. If it is the screen's chrome, it is a top bar.

For a translucent one over a live map, use `GlassSurface` and read the note in
[adaptive](adaptive.md#there-is-no-portable-backdrop-blur) — there is no
portable backdrop blur.

---

## `Spinner`

An indeterminate activity indicator. The arc sweeps *and* breathes — its length
grows and shrinks as it rotates, so the tail chases the head. Under reduced
motion the breathing stops and the arc holds a constant length.

<!--sample:SpinnerBasics-->
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
