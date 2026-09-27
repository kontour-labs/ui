# `ModalNavRail`

A [`NavRail`](nav-rail.md) over the content rather than beside it — the sidebar on
a phone, behind a menu button. Always at its widest, so its groups open in place.

<!--sample:ModalNavRailBasics-->
```kotlin
var open by remember { mutableStateOf(false) }
var here by remember { mutableStateOf("home") }

IconButton(icon = Tabler.Outline.Home, contentDescription = "Destinations", onClick = { open = true })

// Over the screen rather than beside it. Choosing a destination closes it, a
// frame after the page has changed behind it.
ModalNavRail(visible = open, onDismissRequest = { open = false }) {
    item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
    item(here == "saved", { here = "saved" }, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
    item(here == "alerts", { here = "alerts" }, key = "alerts", icon = Tabler.Outline.Bell) { +"Alerts" }
}
```

It renders through [`ModalSideSheet`](modal-side-sheet.md), from the leading edge
by default, so it stacks with dialogs, dims the page, closes on back and can be
dragged closed towards its edge. `glass` frosts the panel.

**Choosing a destination closes it — a frame later.** `dismissOnItemClick` calls
the destination's `onClick` first, while the rail is still open and still, and
closes it on the next frame. A destination's first frame is its most expensive,
and that order puts it where nothing is moving: measured on the catalog, the same
64ms landing inside the rail's exit read as the rail snapping shut, and holding the
choice until the rail had gone read as the page changing late. Turn it off for a
rail that should stay open while the reader works.

---

## Accessibility

It sets `paneTitle` (`Theme.strings.navigation` by default), so a screen reader
says what just appeared, and the scrim carries a labelled dismiss action.
Destinations are `Role.Tab` in a `selectableGroup()`; a group is a disclosure that
says whether it is open.
