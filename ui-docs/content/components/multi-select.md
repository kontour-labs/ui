# `MultiSelect`

Picks any number; the menu stays open while toggling, because closing after each
choice makes selecting four things take four taps plus four reopenings.

<!--sample:MultiSelectBasics-->
```kotlin
val modes = remember { listOf("Train", "Bus", "Ferry", "Tram") }
var chosen by remember { mutableStateOf(setOf("Train", "Bus")) }

// The closed field summarises rather than listing everything: three fit,
// and beyond that it says how many. `summary` overrides that.
MultiSelect(
    value = chosen,
    options = modes,
    onValueChange = { chosen = it },
    label = "Modes",
)
```

**Reach for a [`ChipGroup`](chip.md) of
`FilterChip`s instead** when the options fit on screen. Chips show the current
selection without being opened, which is most of what a filter bar is for.

---

## Accessibility

Everything on [`Select`](select.md), except the one thing that makes it a
different component: its options report **`Role.Checkbox`**, because any number
of them can be on.

They did not until Round 16. `MenuItem` had a single notion of "selected" and
reported `Role.RadioButton` for it, so a screen reader told every user of this
component that choosing a second mode of transport would clear the first —
which is the opposite of what it does. `MenuItem(multiple = true)` — in
`Menu.kt`, which is where the menu rows and their roles actually live — is the
distinction, and it changes what is announced and nothing that is drawn.

The closed field announces `summary`, which by default lists up to three and then
says how many. That is a deliberate ceiling: a field that read out nine selected
modes every time it took focus would be unusable.

Where the exact set matters, show it as chips beside the field rather than
lengthening the summary — chips can be reviewed one at a time and removed
individually.

---

← [Text editing](text-editing.md) · [All components](../components.md)
