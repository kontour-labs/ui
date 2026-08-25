# `CommandPalette`

Every action in the app, by name, from the keyboard.

<!--sample:CommandPaletteBasics-->
```kotlin
var open by remember { mutableStateOf(false) }
val commands = remember {
    listOf(
        Command("plan", "Plan a trip", onRun = { plan() }, shortcut = "P"),
        Command("saved", "Saved trips", onRun = { nearby() }, keywords = listOf("favourites")),
        Command("offline", "Download for offline", onRun = { save() }, enabled = false),
    )
}

CommandPalette(visible = open, onDismissRequest = { open = false }, commands = commands)
```

Commands carry `keywords` as well as a label, so "prefs" finds Settings, and a
`shortcut` string that is shown rather than bound — the binding lives with the
action, and a palette that claimed to own it would be a second source of truth.

**The keyboard is the point.** It opens on a chord, filters as you type, moves
with the arrow keys and runs on Enter, without the pointer being touched — see
[the overlay guide](../overlays.md#the-keyboard-is-the-point).

---

## Accessibility

The field takes focus when the palette opens, so a keyboard user types straight
into it. Up and down move the selection, Enter runs it, Escape closes — through
`onPreviewKeyEvent`, before the field's own editing handlers.

Being keyboard-first is the point of the component, which makes the same warning
as the context menu load-bearing: **a command reachable only from the palette is
unreachable on a phone**, where there is no keyboard shortcut to open it. The
palette is an accelerator over actions that already exist.

---

← [Overlays](overlays.md) · [All components](../components.md)
