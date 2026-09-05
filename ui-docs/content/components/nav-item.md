# `NavItem`

One destination — icon, label, optional badge and content description —
rendered by whichever surface fits. It is what removes the second and third copy
of the same three destinations.

<!--sample:NavItemBasics-->
```kotlin
var selected by remember { mutableStateOf(0) }

// One list, described once. `NavBar`, `NavRail`, `NavDrawer` and
// `NavigationSuiteScaffold` all take it, so changing surface at a
// breakpoint is not a second copy of the destinations.
val items = remember {
    listOf(
        NavItem("Home", Tabler.Outline.Home, onClick = { selected = 0 }),
        NavItem("Saved", Tabler.Outline.Star, onClick = { selected = 1 }),
        NavItem("Alerts", Tabler.Outline.Bell, badge = 3, onClick = { selected = 2 }),
    )
}

NavBar(items = items, selectedIndex = selected)
```

---

## Accessibility

`NavItem` is data, not a composable, and it carries the two things every surface
needs to announce it: `label`, which is the destination's name, and
`contentDescription`, for the cases where the label is an abbreviation and the
spoken form should not be.

`badge` is a count. It is announced with the item rather than as a separate node,
so "Alerts, 3" is one destination rather than two things next to each other.

Because one list feeds `NavBar`, `NavRail`, `NavDrawer` and
`NavigationSuiteScaffold`, a description written once is right on every surface —
which is most of the reason the type exists.
