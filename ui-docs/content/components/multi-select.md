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

← [Text editing](text-editing.md) · [All components](../components.md)
