# `CommandPalette`

Every action in the app, by name, from the keyboard.

<!--sample:CommandPaletteBasics-->
```kotlin
var open by remember { mutableStateOf(false) }
val commands = remember {
    listOf(
        Command("plan", "Plan a trip", onAction = { plan() }, shortcut = "P"),
        Command("saved", "Saved trips", onAction = { nearby() }, keywords = listOf("favourites")),
        Command("offline", "Download for offline", onAction = { save() }, enabled = false),
    )
}

CommandPalette(visible = open, onDismissRequest = { open = false }, commands = commands)
```

Commands carry `keywords` as well as a label, so "prefs" finds Settings, and a
`shortcut` string that is shown rather than bound — the binding lives with the
action, and a palette that claimed to own it would be a second source of truth.

It hangs near the top of the window rather than in the middle, so the list grows
down as it filters instead of the panel jumping up, and keeps clear of the
window's insets and, by `Theme.spacing.md`, its sides: on a phone it is the
width of the screen less a margin, and wider up it stops at `width`.
**The closest first.** Typing filters the commands with `matches` and orders
what is left by `similarity`, closest first: "set" puts *Set theme* (the label
starts with it) ahead of *Sync settings* (a word in it does), ahead of
*Preferences* (its keyword *settings* does), ahead of *Reset zoom* (the query is
inside a word). The highlight is on the top row, so Enter runs the closest.
Commands that answer as closely keep their order in `commands`, and with
nothing typed the list is in that order.

Past `maxHeight` the results scroll, and fade out at whichever end there are
more of them ([`fadingEdges`](modifier-fading-edges.md)), so a row cut off at the panel's
foot does not read as the last.

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

At high contrast each result row and the search field take an outline, and the
highlighted row takes an accent one as well: the `selectionEdge()` a selected
[`ListItem`](list-item.md) has. The panel takes an edge too, as a
[`Dialog`](dialog.md) on the same dimmed layer does.

Under reduced motion the palette fades in where it rests instead of settling
from a slightly larger scale, and arrowing past the last visible row brings the
highlight into view at once rather than scrolling to it.
