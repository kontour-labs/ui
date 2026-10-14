# `NavigationSuiteScaffold`

*Also on this page: `NavigationSuiteType`, `NavigationSuiteState`, `navigationSuiteTypeFor`.*

Picks the surface from the window size and places it, so a screen writes its
destinations once, as a [`NavScope`](nav-scope.md), and never arranges them.

<!--sample:NavigationSuiteScaffoldBasics-->
```kotlin
var here by remember { mutableStateOf("home") }
val suite = rememberNavigationSuiteState()

// A bar on a phone, a rail on a tablet, a sidebar on a desktop — chosen from
// the window size class, so the screen below says nothing about which.
NavigationSuiteScaffold(
    destinations = {
        item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
        item(here == "saved", { here = "saved" }, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
        item(
            selected = here == "alerts",
            onClick = { here = "alerts" },
            key = "alerts",
            icon = Tabler.Outline.Bell,
            badge = { Badge(count = 3) },
        ) {
            +"Alerts"
        }
    },
    state = suite,
    railExpandable = true,
) { contentPadding ->
    Screen()
}
```

| Window | Surface | Placement |
|---|---|---|
| Compact (< 600dp) | `NavBar` | **Bottom of the screen**, over the content |
| Medium (< 840dp) | `NavRail`, collapsed | **Leading edge**, beside the content; `railExpandable` gives it a toggle |
| Expanded and up | `NavRail`, expanded | **Leading edge**, labels always shown |

**This is an app, not a website.** Destinations live at the bottom on a phone
because that is where a thumb reaches, and move to the leading edge on a wide
window because a bar there eats the dimension there is least of. A
[`TopBar`](top-bar.md) is a title and its actions, never a place for destinations.

**The bar overlays the content** rather than sitting below it; the scaffold hands
your content the padding to inset by — and to anything inside it, a page a
navigation host shows, through `navigationSuitePadding()`. With Navigation 3,
[`rememberNavigationSuiteSceneStrategy`](../navigation3.md#pages-inside-the-nav-bar-and-pages-over-it)
puts the scaffold round one back stack, with pages over it as well. A rail sits beside the content and hands it
none. The bar is measured before the content is laid out, so the padding is the
bar's real height from the first frame — margins, labels, type size and the inset
under it — and not a guess that the content then jumps clear of.

**The content is the same composable whichever surface it is beside.** A phone
turned on its side, a foldable opened or a window dragged past 600dp swaps the
bar for a rail, and the screen inside carries on: what it remembers, its saved
state and its scroll position are all still there. Only the navigation is
rebuilt.

### The modal rail

<!--sample:NavigationSuiteModalRail-->
```kotlin
var here by remember { mutableStateOf("home") }
val suite = rememberNavigationSuiteState()

// No navigation on screen until the app asks for it: a top bar's menu
// button calls `suite.showModal()`.
NavigationSuiteScaffold(
    destinations = {
        item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
        item(here == "saved", { here = "saved" }, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
    },
    state = suite,
    type = NavigationSuiteType.ModalRail,
) {
    IconButton(icon = Tabler.Outline.Home, contentDescription = "Destinations", onClick = suite::showModal)
}
```

`NavigationSuiteType.ModalRail` is never chosen automatically. It is for a phone
whose destinations are too many or too nested for a bar: no navigation on screen
until a menu button calls `state.showModal()`. `NavigationSuiteState` also holds
whether the rail is expanded, so it persists while the window stays a rail.

`barStyle` reaches the bar; `search` goes in the bar
after `searchIndex` destinations, counted — `null`, or a number at or past the
count, puts it at the end; `action` goes beside a floating bar, after everything
in a docked one, and at the foot of a rail — see
[`NavBar`](nav-bar.md#a-floating-action-beside-it); `header` goes above a rail's
destinations.

---

## Accessibility

The surface changes with the window and the destinations do not, so what is
announced is the same on a phone, a tablet and a desktop. Each surface groups its
destinations, so a screen reader announces "2 of 4"; a search and an action sit
outside that count.

At [high contrast](../accessibility.md#what-high-actually-changes) the bar, the
rail and the modal rail each take an edge in the scheme's `outline`, and the
current destination's indicator takes an accent outline round its tint, as on
[`NavBar`](nav-bar.md) and [`NavRail`](nav-rail.md).

Under [reduced motion](../tokens.md#motion) the indicator fades up on the
destination chosen instead of travelling to it, a rail growing into a sidebar
takes its width at once, and the modal rail fades where it rests instead of
sliding in from the edge.
