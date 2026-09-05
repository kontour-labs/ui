# `Button`

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

| Variant | For |
|---|---|
| `Primary` | The one action the screen exists for. At most one per screen. Solid, near-black |
| `Accent` | The place the product should show up as itself — "Get started", "Plan a trip". Solid, in the accent tone |
| `Secondary` | A real alternative to the primary action. Outlined |
| `Tertiary` | Supporting actions that should not compete. Filled with a soft ground |
| `Ghost` | Lowest weight — toolbar actions, inline "edit". No ground until hovered |
| `Destructive` | Deletes, cancels a booking, ends a trip |
| `DestructiveGhost` | A destructive action that should not shout — inside a menu or row |

`Primary` and `Accent` are **alternatives to each other, not companions**. Two
solid buttons on one screen is still one too many.

Five sizes, `XSmall` to `XLarge`, drawn from `Theme.sizing.controlHeight*` so a
row of mixed buttons, inputs and selects lines up without per-call-site padding.
`Medium` is the default; `Large`/`XLarge` are for a screen's single main call to
action.

**A button is a capsule**, at every one of those sizes. It reads
`Theme.shapes.control`, the shape every pressable thing in the library shares —
so a `Button` beside an `IconButton` beside a `Chip` is one row of one corner.
A fixed radius could not manage that: at the old 14dp an `XSmall` was nearly a
pill already and an `XLarge` was nearly square, so the component disagreed with
itself across its own size scale. Override `Theme.shapes.control` to change every
action at once; pass `shape` to change one.

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
`contentDescription` is required. A component that announces nothing fails
the build, which is what replaced the guarantee the old `label: String`
parameter gave.

`loading = true` sets `stateDescription = loadingLabel` on the button and clears
the semantics of the spinner inside it, so a screen reader hears "Save this trip,
saving" rather than the label followed by an unnamed progress indicator. The
button reports itself disabled while it runs rather than merely ignoring the
press.

`ButtonVariant.Destructive` changes the colour and nothing that is announced.
Where the consequence matters, put it in the label: "Delete trip", not "Delete".
