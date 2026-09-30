# `NavBar`

*Also on this page: `NavBarScope`, `NavBarStyle`, `NavBarDefaults`.*

The primary navigation on a phone, **at the bottom of the screen**. Each
destination is a circle, and one marker travels between them.

<!--sample:NavBarBasics-->
```kotlin
var here by remember { mutableStateOf("map") }

// Docked to the bottom edge rather than floating over the page, with a word
// under each icon for an app whose icons are not obvious.
NavBar(style = NavBarStyle.Docked, showLabels = true) {
    item(here == "map", { here = "map" }, key = "map", icon = Tabler.Outline.Map) { +"Map" }
    item(here == "plan", { here = "plan" }, key = "plan", icon = Tabler.Outline.Calendar) { +"Plan" }
    item(here == "me", { here = "me" }, key = "me", icon = Tabler.Outline.User) { +"Me" }
}
```

The content is a [`NavScope`](nav-scope.md) with one addition: `slot`, for anything
else that belongs inside the bar — a search, a button.
A slot at `weight = 0f` takes its content's width; above zero it takes that share
of what is left, which is what a search wants — up to `NavBarDefaults.SlotMaxWidth`,
320dp. Without the cap a search on a wide window took all of the room the
destinations left, and a floating bar ran from one edge of the window to the other
around three circles; with it, the bar hugs what it holds on anything wider than a
phone.

### Two styles

| | |
|---|---|
| `Floating` | A capsule inset from every edge, over the page. The default. The page runs under it, and on a wide window it hugs its destinations in the middle. |
| `Docked` | One surface along the bottom edge, the window's width. A firmer footing when the page behind is a list rather than a map. |

A third, free-standing circles with nothing behind them, is gone: a circle
casting its own shadow over a photograph read as a sticker rather than as
navigation, and every other surface in the library has a ground.

`showLabels` is off by default. A word under every icon is a row of words, and the
few destinations of a bar are ones its user already knows. A hidden label is still
announced.

`NavBarDefaults.arrangementFor(spans)` spreads the circles across a phone's width
and gathers them in the middle of anything wider.

### A search in the middle of it

<!--sample:NavBarCentreSearch-->
```kotlin
var here by remember { mutableStateOf("home") }
val search = rememberNavSearchState()

// The search in the middle, two destinations either side: the thing people
// came to do between the places they might go.
NavBar {
    item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
    item(here == "map", { here = "map" }, key = "map", icon = Tabler.Outline.Map) { +"Map" }
    slot(weight = 1f) { NavSearch(state = search, searchIcon = Tabler.Outline.Search) }
    item(here == "plan", { here = "plan" }, key = "plan", icon = Tabler.Outline.Calendar) { +"Plan" }
    item(here == "me", { here = "me" }, key = "me", icon = Tabler.Outline.User) { +"Me" }
}
```

Two destinations either side of the thing people came to do. The search is a
[`NavSearch`](nav-search.md), a pill that opens a field over the screen rather than
raising the keyboard inside the bar. `NavigationSuiteScaffold` puts one there by
counting destinations: `searchIndex = 2`.

### A floating action beside it

<!--sample:NavBarAction-->
```kotlin
var here by remember { mutableStateOf("map") }

// Beside the capsule, not in it: a floating action button has a surface of
// its own, and on a phone the capsule narrows to make room for it.
NavBar(
    action = {
        FloatingActionButton(onClick = { newTrip() }, icon = Tabler.Outline.Plus, contentDescription = "New trip")
    },
) {
    item(here == "map", { here = "map" }, key = "map", icon = Tabler.Outline.Map) { +"Map" }
    item(here == "plan", { here = "plan" }, key = "plan", icon = Tabler.Outline.Calendar) { +"Plan" }
    item(here == "me", { here = "me" }, key = "me", icon = Tabler.Outline.User) { +"Me" }
}
```

A floating action button goes in `action`, not in a slot. A floating bar puts it
**beside** the capsule, a small gap after it, on the page and centred on the
capsule's height: a round button squeezed into a capsule of round destinations
reads as one more destination, and a 56dp button in a 64dp capsule has 4dp
round it where the circles have 12. On a phone the capsule narrows to make room; on anything wider the two
are centred together. It sits after the bar in reading order, so in a
right-to-left layout it is on the left.

Beside the capsule the action is on the page, with nothing behind it, so it has
to bring its own surface, which a floating action button does. A plain icon
button belongs in a `slot`.

A `Docked` bar has no beside: it puts the action inside, after everything else.

### A group in a bar

A `group` is a circle that opens a menu of its destinations above the bar. While
the selected destination is inside it, the group's circle carries the marker.

---

## Accessibility

Destinations are `Role.Tab` inside a `selectableGroup()`. A slot is outside the
count, so a search is not announced as "3 of 5" among four destinations. A group
is a `Role.Button` that opens a menu.

At high contrast the bar takes an edge. The travelling marker takes an accent outline, `selectionEdge()`,
because a tint alone is too faint at that tier, and the focus ring is drawn
thicker.

Under reduced motion the marker does not travel: it fades out and fades up on
the new destination. The selected icon's slight growth is a short tween with no
overshoot, a press is a tonal wash at every setting, and a group's menu fades in
rather than growing out of the bar.
