# `NavBar`

*Also on this page: `NavBarScope`, `NavBarStyle`, `NavBarDefaults`.*

The primary navigation on a phone, **at the bottom of the screen**. The
destinations share the bar in equal cells, and one marker, round the whole of the
current one, travels between them.

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

**Labels barely change the bar's height.** With them on, each destination's icon
is 20dp rather than 24, two points above its label, and the pair is centred in
the touch target, so a labelled bar is the height of an unlabelled one at the
default type and grows only as the type does. It used to hang its label under a
40dp glyph box: 82dp of floating bar against 64, reported as *"too much space
between the icon and the text, and it's making the navbar huge"*.

**The label and the badge fit inside the marker.** The marker round the current
destination has rounded ends, so it is narrower at the bottom, where the label
sits, than across its middle. A label is measured to that narrower width and
ends in an ellipsis where the marker's shape does. A badge sits on its icon's
top-trailing corner and moves inward as far as it has to so that it stays inside the
marker. It used to poke out over the bar.

With circles rather than cells (see below), `NavBarDefaults.arrangementFor(spans)`
spreads them across a phone's width and gathers them in the middle of anything
wider.

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

### Its shape, cells or circles

By default the bar is a tab bar: **cells that share it**, and **a marker round the
whole of the current destination**, glyph and label. The marker's shape follows
the bar's. In a floating bar it is a capsule inside the capsule, as far from the
bar's ends as from its top and bottom, so it is the bar's shape a size smaller,
with labels or without. It used to be the
theme's medium corner whatever the bar, a box inside a pill: *"our new defaults
look really terrible with labels enabled"*. A docked bar has no corners to follow,
and has the medium corner.

The other look is circles:

<!--sample:NavBarCircles-->
```kotlin
var here by remember { mutableStateOf("drive") }

// Circles rather than cells: each destination as wide as it needs, spread
// across a phone and gathered in the middle of anything wider, and the
// marker a circle behind the glyph.
NavBar(
    itemWidth = NavBarItemWidth.Natural,
    marker = NavBarDefaults.glyphMarker(),
) {
    item(here == "drive", { here = "drive" }, key = "drive", icon = Tabler.Outline.Car) { +"Drive" }
    item(here == "shop", { here = "shop" }, key = "shop", icon = Tabler.Outline.Basket) { +"Shop" }
    item(here == "you", { here = "you" }, key = "you", icon = Tabler.Outline.User) { +"You" }
}
```

Three parameters change how the bar is drawn without changing what it holds:

| | |
|---|---|
| `shape` | The bar's own outline. `null` is a capsule when floating and square when docked; `RoundedCornerShape(20.dp)` is a floating rounded rectangle. |
| `itemWidth` | `Equal`, the default: cells that share the bar, touching, up to `NavBarDefaults.EqualItemMaxWidth` each. Beside a search they are only as wide as the widest destination needs, and the search has the rest. Or `Natural`: each destination as wide as it needs, spread across a phone and gathered on anything wider. |
| `marker` | `NavBarDefaults.destinationMarker(shape, inset)`, the default: a highlight round the whole destination, in the bar's shape unless given one. Or `NavBarDefaults.glyphMarker(size, shape)`: the circle behind the glyph, in any size or shape. |

A destination marker wants cells to fill: on natural widths it only outlines each
word. With cells, the capsule's padding at the ends is the same as at its top and
bottom, so the highlight sits the same distance from every edge, and a slot or an
action beside the cells keeps the usual gap from them. A press washes the whole
cell rather than the glyph. A bar with a `shape` of its own gets the medium corner:
pass the marker's shape too to match it.

`NavigationSuiteScaffold` passes all three on as `barShape`, `barItemWidth` and
`barMarker`; a rail's marker stays its own.

### The marker reaches for a press

Press a destination other than the current one and, before the finger lifts, the
marker leans a little toward it and stretches from the edge it is leaving: a
promise of where it is going, not the trip. Let go and it goes. Slide off the
destination instead and the press is cancelled, and the marker settles back.
However far away the destination is, the lean is the same small nudge. A rail's,
a tab bar's and a segmented control's markers do the same.

### Dragging the marker

The marker can be dragged, as a segmented control's thumb can. Put a finger on
a destination and slide it sideways: the marker comes with the finger, leaning
and stretching toward it, and snaps from destination to destination with a tick at each, the
colours following. A drag that starts on another destination than the chosen one
ticks as the marker jumps to it, too. Let go on one and it is chosen — its `onClick` runs then,
once.

**By default the choice waits for the finger to lift.** A segmented control
changes its value at every boundary it crosses, because a value is cheap to
change back. A destination is a page: choosing each one the finger passed over
would build and throw away every screen on the way. A drag that comes back to
where it started, or that something else takes away, chooses nothing, and a tap
is still a tap.

**`markerDrag = NavBarMarkerDrag.SelectWhileDragging` chooses each destination
as the marker reaches it**, once, the way a segmented control does, and the lift
chooses nothing more. Use it for pages cheap enough to scrub across, where
seeing each one as the finger passes is the point. Asked for as an option:
*"when you drag the navbar selection indicator, it can switch tabs as you're
dragging"*.

Only sideways, and only from a destination: a slot — a search — keeps its own
gestures. `NavBarMarkerDrag.Off` turns it off. A rail's marker cannot be
dragged at all: a rail scrolls, and a drag along it could not be told from a
scroll.

### A group in a bar

A `group` is a circle that opens a menu of its destinations above the bar. While
the selected destination is inside it, the group's circle carries the marker.

---

## Accessibility

Destinations are `Role.Tab` inside a `selectableGroup()`. A slot is outside the
count, so a search is not announced as "3 of 5" among four destinations. A group
is a `Role.Button` that opens a menu.

Dragging the marker is a shortcut for a finger and adds nothing to the tree:
each destination is still its own tab, chosen by a screen reader's activation,
a switch or a key exactly as before.

At high contrast the bar takes an edge. The travelling marker takes an accent outline, `selectionEdge()`,
because a tint alone is too faint at that tier, and the focus ring is drawn
thicker.

Under reduced motion the marker does not travel: it fades out and fades up on
the new destination, and it does not lean toward a finger dragging it or a
destination being pressed. The selected icon's slight growth is a short tween with no
overshoot, a press is a tonal wash at every setting, and a group's menu fades in
rather than growing out of the bar.
