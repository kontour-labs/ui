# `NavBar` / `NavRail` / `NavDrawer`

*Also on this page: `NavBarItem`, `NavRailItem`, `NavDrawerItem`, `NavDrawerSection`, `NavDrawerGroup` — the rows each surface draws.*

Each surface draws the selected indicator its own way — a filled circle in the
bar, a pill behind the icon in the rail, a pill across the whole row in the
drawer — and that indicator is the only thing distinguishing a destination you
are on from one you are not.

`NavBar` and `NavRail` take a `List<NavItem>`. **A drawer takes a slot, not a
list**: a drawer is where destinations stop being a flat set of three, and a
list model would be a tree wearing a list's shape.

### Why the bar has no bar

`NavBar` is free-standing circles floating over whatever the screen is showing.
What makes a row of destinations read as navigation is the **travelling
circle**, not the container they sit in — and over a map or a photo a container
is a strip of the content you cannot see. The marker moves between
destinations; that is the whole of the chrome.

Each circle carries its own elevation, which is enough to separate it from a
map. Over a photo or a promotional banner it is not, and `backdrop` adds a
vertical fade from transparent to the page colour behind the whole row. The fade
is *drawn*, not laid out — it reaches 128dp up the screen without the bar
measuring a pixel taller, because the scaffold hands that measurement to your
content as padding.

**The bar is as tall as the target it reserves, and no taller.** A destination
draws a 40dp circle inside the 48dp `Theme.sizing.minTouchTarget` reserves around
it, so the air above and below the circles is the touch target's own rather than
padding added on top of it. On a phone that is 48dp of bar, plus whatever the
gesture bar underneath asks for.

`showLabels` is off by default. A word under every icon is a row of words, and
the destinations of an app this size are the four or five its user already
knows. Turn it on for an app whose icons are not obvious.

### Three styles, and one axis

`style` decides what — if anything — the circles sit on. It is one enum rather
than two because the bar has been here before: it used to be three container
styles and two item styles, nine combinations of which the product wanted one.
Each of the nine looked defensible alone, which is how a matrix gets built. **A
second axis is the thing to refuse**, so the item's own presentation is derived
from the style rather than chosen beside it.

| | |
|---|---|
| `Free` | Free-standing circles over the page, nothing behind them. The default. Each circle carries its own elevation, so the bar works over a map with no surface separating it from one. |
| `Docked` | One surface spanning the window, on the bottom edge. What to reach for when the content behind is a list rather than a map — a surface meeting the window's edge is a firmer footing than shapes floating over a scroll. |
| `Floating` | A capsule inset from every edge. `Docked`'s footing without its commitment: the page runs under it, so it suits content that should be seen to continue past the bar. |

`NavBarDefaults.arrangementFor` reads the same value, so the style moves the
items as well as the surface — a docked bar spreads them across the window, a
floating capsule packs them.

### A search field in the middle of it

<!--sample:NavBarCentreSearch-->
```kotlin
var current by remember { mutableStateOf(0) }
val search = rememberNavSearchState()

val destinations = listOf(
    NavItem(label = "Home", icon = Tabler.Outline.Home, onClick = { current = 0 }),
    NavItem(label = "Map", icon = Tabler.Outline.Map, onClick = { current = 1 }),
    NavItem(label = "Plan", icon = Tabler.Outline.Calendar, onClick = { current = 2 }),
    NavItem(label = "Profile", icon = Tabler.Outline.User, onClick = { current = 3 }),
)

NavBar(
    items = destinations,
    selectedIndex = current,
    search = { NavSearch(state = search) },
    searchIndex = destinations.size / 2,
)
```

`searchIndex` decides where among the destinations the `search` slot goes;
`items.size / 2` is the middle, with two either side. `null` — the default —
puts it after them all.

**Tapping the pill expands it into an overlay, not into a taller bar.** A
navigation bar clears `WindowInsets.bottomEdges`, whose own documentation says
why: *"A navigation bar holds no text field and should stay where it is while
the user types."* A bar riding up on the keyboard would contradict the inset it
asks for. So the pill stays put and the expanded field goes into the
`OverlayHost`, which brings the scrim, back and escape, and trapped focus with
it.

`NavExpandPlacement` says where it lands. `AboveKeyboard` keeps the field near
the thumb that opened it and stacks results upward; `Top` puts it where a
browser puts one and reads the results downward. Both are built because the
answer is a question about your app rather than about the component.

**The pill sits on `surface` with the destinations' own shadow**, not in the
`surfaceSunken` well a text field uses. It is a raised thing on the page beside
other raised things; a sunken control on a sunken page has no edge at all.
`containerColour` and `contentColour` are there for an app that wants otherwise.

### The same shape without a search in it

`NavExpandingSlot` is the pill-and-panel underneath
`NavSearch`, and it knows nothing about searching. A filter, an account
switcher, a "where to?" prompt and a compose box are all the same thing: a small
control in the bar that needs the whole screen once it is in use.

```kotlin
var open by remember { mutableStateOf(false) }

NavExpandingSlot(
    expanded = open,
    onExpandedChange = { open = it },
    expandedContent = { FilterList(onPick = { open = false }) },
) {
    +Tabler.Outline.Filter
    +"Filters"
}
```

### Slot content that knows how much room it has

`LocalNavExpansion` tells whatever is in a `header`, `action`, `footer` or
`search` slot how wide the surface around it currently is — `expanded` for
whether there is room for a word, `progress` for interpolating across a rail's
animation.

```kotlin
NavRail(items, selectedIndex = current, header = {
    val room = LocalNavExpansion.current
    Row { Avatar(user); if (room.expanded) Text(user.name) }
})
```

This is what makes `NavSearch` a pill in a bar and a field in a drawer with no
parameter at the call site, and it is deliberately not about search: a profile
row that gains a name, a button that drops its label, a chip that becomes an
icon are the same question. Before it, the only way to answer it was to thread
your own copy of the rail's `expanded` flag down by hand — and that flag is the
*target*, not the animated width, which is the mistake the rail itself spent a
round unlearning.

`NavigationSuiteScaffold` passes `search` and `searchIndex` through to the bar,
and to the bar only — a rail and a drawer have a leading edge and room to spare,
and how a wide window searches is a screen's decision.

`NavDrawerSection` separates a titled run of destinations; `NavDrawerGroup`
nests them behind a disclosure. The group's expansion is hoisted, so the app can
open the group containing the current page — which is nearly always right and
not something the component can know.

`ModalNavDrawer` is the same drawer rendered into the `OverlayHost`, for a
narrow window where it slides over the content instead of sitting beside it.

**A drawer opens showing the page you are on.** `revealSelected` scrolls the
selected destination into view as the drawer arrives, which matters most on a
phone: a sidebar of twenty destinations opens showing the first five, and the
row that says where you are is routinely below the fold. It costs nothing on a
list that already fits, and it snaps rather than scrolling — the row should be
in place when the drawer gets there, not slide into it afterwards. Turn it off
for a drawer whose list is short enough that the movement is the only thing
you would notice.

### The rail expands

<!--sample:NavRailExpanding-->
```kotlin
var current by remember { mutableStateOf(0) }
var railOpen by remember { mutableStateOf(false) }

val destinations = listOf(
    NavItem(label = "Plan", icon = Tabler.Outline.Map, onClick = { current = 0 }),
    NavItem(label = "Saved", icon = Tabler.Outline.Star, onClick = { current = 1 }),
)

NavRail(
    items = destinations,
    selectedIndex = current,
    expanded = railOpen,
    onExpandedChange = { railOpen = it },
)
```

Pass `onExpandedChange` to get the toggle; leave it null for a rail fixed at
whatever `expanded` says. Expanding grows the rail to `NavDrawerDefaults.Width`.

**A rail shows icons only until it is wide enough for a word beside them**, and
then fades the labels in. That is the whole of what changes: a destination is an
icon beside a label at *every* width, in a glyph box that is always the same
size, so the growing rail reveals the labels rather than rearranging around
them and **the icons do not move at all**. The toggle, the header and any action
share the destinations' leading edge, so the column lines up with itself at both
widths.

It used to be three changes at three different moments — the destinations
swapped a stacked column for an inline row on the first frame, the rail's own
alignment flipped from centred to leading at the halfway mark, and the labels
appeared with it. Collapsing was worse: the layout flipped back while the rail
was still 240dp wide, so every icon jumped to the centre of that and slid home.
`NavRailStillnessTest` measures the selected icon's leading edge across the whole
animation, in both directions, and fails if it moves by more than a pixel.

A drawer puts its icons at the same x as the rail it replaces, so the swap the
window size class makes at 840dp — which is not animated, and cannot be — has
nothing to give away.

### How selection is shown

A single marker **travels** to the current destination — an underline beneath it
in the bar and tab bar, a bar down the leading edge in the rail and drawer. The
movement is what carries the meaning, so the accent tint on the icon and label is
a *second* cue rather than the only one.

That matters beyond taste. Selection signalled by colour alone fails WCAG 1.4.1,
and it is what a colour-blind user has nothing to go on. Two independent cues
also serve two different people: the shape reads without colour vision, the tint
reads without sharp edges.

All four surfaces use `foundation/SelectionIndicator.kt` — one mechanism, so the
bar, the rail, the drawer, the tab bar and
[`SegmentedControl`](segmented-control.md) cannot drift apart.

Under reduced motion the marker snaps and cross-fades rather than travelling: a
bar sliding the width of the screen is exactly the translation the preference
exists to stop.

**The selected destination grows and its pill springs in**, ported from the
app's `ToolbarButton`. It is a control people tap dozens of times a session.

A badge is placed against the *glyph*, not the touch target — a 48dp circle
around a 24dp icon would otherwise put the dot floating in empty space well
clear of the thing it annotates.

### `NavRail`, expanded

Four things about the expanded form, all fixed together because they were one
mistake seen from four angles: the rail grew and its contents did not follow.

- **It lines up.** The column is leading-aligned once it has grown. Centred is
  right for an 88dp column of glyphs and wrong for a 280dp one — the destinations
  lay their contents out from the leading edge, so a centred header, toggle and
  action sat in the middle of the rail while everything else started at its edge.
- **It grows rather than jumping.** The contents switch from stacked to inline
  when the *animated width* passes halfway, not when the flag flips. They used to
  change on the first frame, so labels appeared beside icons in an 88dp rail and
  were clipped until it caught up.
- **The pill has room.** `IndicatorSizing.Inset` takes a value per axis now. Inset
  on both, the marker behind a 48dp destination came out 40dp high with the label
  hard against its edge.
- **The toggle stays put and rotates.** One chevron turned round, not two swapped
  for each other — the same thing the select chevron does. A control that changes
  has nothing to say about what it just did.

### `NavBar`, search and the trailing action

`searchIndex` puts the search slot *between* two destinations rather than after
all of them; `items.size / 2` is the middle. It is an index rather than a builder
because `NavigationSuiteScaffold` hands the bar, the rail and the drawer one
`List<NavItem>` and expects all three to show the same list.

With `showLabels` the items grow a word taller, and the trailing action used to
be centred in the row — which put a FAB half a label below the icons it sits
beside. It is aligned to the icons now.

---

## Accessibility

All three apply `selectableGroup()` to their destinations, so a screen reader
announces "2 of 4" rather than four unrelated buttons — and `NavBar`'s search
field and action sit *outside* that group, because inside it a search field is
counted as a destination.

Items are `Role.Tab`. `NavRail`'s expand control carries its own
`contentDescription` and a `stateDescription` of "Expanded" / "Collapsed", so the
rail's width is a state and not a mystery.

`NavDrawer` sets `paneTitle`. All three take `NavItem`s, so a description written
once is right on every surface.
