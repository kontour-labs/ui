# `SelectionIndicatorBox`

*Also on this page: `Modifier.selectionIndicatorItem`.*

The travelling pill behind a selected destination — the thing that slides from
one nav item to the next rather than appearing on it.

<!--sample:SelectionIndicatorBasics-->
```kotlin
var selected by remember { mutableStateOf(0) }
val tabs = listOf("Departures", "Route map", "Alerts")

// The pill is the bar's, not the tab's: `key` is what it animates between,
// so it travels rather than appearing on one and vanishing from another.
TabBar(modifier = Modifier.fillMaxWidth()) {
    tabs.forEachIndexed { index, label ->
        Tab(selected = selected == index, onClick = { selected = index }, key = index) {
            +label
        }
    }
}
```

One indicator, drawn by the container, moved between children. That is the whole
design: an indicator drawn *by each item* cannot animate between two of them,
because the one leaving and the one arriving are different composables and
neither can see the other. The box owns it, each item marks itself with
`Modifier.selectionIndicatorItem(key)`, and the box interpolates between the
bounds it has been told about.

Used by `NavBar`, `NavRail`, `NavDrawer` and `TabBar`, which is why they agree
about how selection looks — see
[how selection is shown](nav-surfaces.md#how-selection-is-shown).

---

## Accessibility

The travelling pill is drawn by the bar, not by the tab, and it carries no
semantics of its own — which is right. Selection is reported by each tab through
its `selected` state, so a screen reader hears "selected" on the tab rather than
being told about an indicator that has no meaning to it.

That separation is also why the animation is safe: with reduced motion the pill
stops travelling and appears in place, and nothing about what is announced
changes.

---

← [Foundation](foundation.md) · [All components](../components.md)
