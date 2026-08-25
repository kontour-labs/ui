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

← [Navigation](navigation.md) · [All components](../components.md)
