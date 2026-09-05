# `NavigationSuiteScaffold`

Picks the surface from the window size and places it, so a screen declares its
destinations once and never arranges them.

<!--sample:NavigationSuiteScaffoldBasics-->
```kotlin
var selected by remember { mutableStateOf(0) }
val items = remember {
    listOf(
        NavItem("Home", Tabler.Outline.Home, onClick = { selected = 0 }),
        NavItem("Saved", Tabler.Outline.Star, onClick = { selected = 1 }),
        NavItem("Alerts", Tabler.Outline.Bell, badge = 3, onClick = { selected = 2 }),
    )
}

// A bar on a phone, a rail on a tablet, a drawer on a desktop — chosen from
// the window size class, so the screen below says nothing about which.
NavigationSuiteScaffold(items = items, selectedIndex = selected) { contentPadding ->
    Screen()
}
```

| Window | Surface | Placement |
|---|---|---|
| Compact (< 600dp) | `NavBar` | **Bottom of the screen**, over the content |
| Medium (< 840dp) | `NavRail` | **Leading edge**, icons only, beside the content |
| Expanded and up | `NavDrawer` | **Leading edge**, labels always shown |

**This is an app, not a website.** Destinations live at the bottom on a phone
because that is where a thumb reaches, and move to the leading edge on a wide
window because a horizontal bar there eats the dimension there is least of. A
[`TopBar`](top-bar.md) in this system is a title and its actions — never a place to
put destinations.

**The bar overlays the content** rather than sitting below it, matching the
floating toolbar the app uses over its map. The scaffold hands your content the
padding to inset by, the same way a map insets its controls by a sheet's
`visibleHeight`.

---

## Accessibility

The surface changes with the window and the destinations do not, so what is
announced is the same on a phone, a tablet and a desktop. That is the property
worth protecting: a user who has learned the order of their destinations should
not have to relearn it because they rotated the device.

Each surface applies `selectableGroup()` to its items, so a screen reader
announces "2 of 4" rather than four unrelated buttons.

`search` and `action` sit **outside** that group deliberately. Inside it, a
search field would be counted as a destination — "1 of 5" for four destinations —
which is the bug the separation exists to prevent.
