# `IconToggleButton`

Unchecked, this is pixel-for-pixel an [`IconButton`](icon-button.md) — not close
to one, the same drawing. The accent ground it takes when checked is the entire
visible difference between the two components.

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

## Accessibility

`Role.Checkbox` with a `stateDescription`, so what is announced is "Favourite,
on" rather than a button giving no clue which way it is set. That is the whole
difference from [`IconButton`](icon-button.md), which is `Role.Button` and has no
state.

`stateDescription` is overridable for the cases where on and off are the wrong
words — "muted" and "unmuted" read better, and "playing" and "paused" better
still.

`contentDescription` names the control and must not change with the state: a
button called "Mute" that becomes "Unmute" is two controls to anyone navigating
by name.
