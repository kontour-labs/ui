# Navigation

| | For | Instead of |
|---|---|---|
| [`NavigationSuiteScaffold`](#navigationsuitescaffold) | **Start here.** Destinations, placed by window size | Picking a surface by hand |
| [`NavItem`](#navitem) | One destination, declared once | Three copies, one per surface |
| [`NavBar`](#the-three-surfaces) | Compact windows — bottom of the screen | — |
| [`NavRail`](#the-three-surfaces) | Medium windows — leading edge | — |
| [`NavDrawer`](#the-three-surfaces) | Expanded windows, and nested groups | `NavRail`, for a flat set |
| [`TopBar`](#topbar) | A title and its actions | Anything holding destinations |
| [`TabBar`](#tabbar) | Views of *one* screen | `SegmentedControl`, when switching a value |
| [`Breadcrumbs`](#breadcrumbs) | Where you are in a hierarchy | — |
| [`Pagination`](#pagination) | Numbered pages | `LoadMore`, in an app |

Routes and the back stack are not in `:ui` — they are in `:core:navigation`.
This page is about the surfaces that draw them.

---

## `NavigationSuiteScaffold`

Picks the surface from the window size and places it, so a screen declares its
destinations once and never arranges them.

| Window | Surface | Placement |
|---|---|---|
| Compact (< 600dp) | `NavBar` | **Bottom of the screen**, over the content |
| Medium (< 840dp) | `NavRail` | **Leading edge**, beside the content |
| Expanded and up | `NavDrawer` | **Leading edge**, labels always shown |

**This is an app, not a website.** Destinations live at the bottom on a phone
because that is where a thumb reaches, and move to the leading edge on a wide
window because a horizontal bar there eats the dimension there is least of. A
[`TopBar`](#topbar) in this system is a title and its actions — never a place to
put destinations.

**The bar overlays the content** rather than sitting below it, matching the
floating toolbar the app uses over its map. The scaffold hands your content the
padding to inset by, the same way a map insets its controls by a sheet's
`visibleHeight`.

## `NavItem`

One destination — icon, label, optional badge and content description —
rendered by whichever surface fits. It is what removes the second and third copy
of the same three destinations.

---

## The three surfaces

![NavBarItem](../../../../../app/ui-catalog/screenshots/components/navbaritem-light.png)
![NavRailItem](../../../../../app/ui-catalog/screenshots/components/navrailitem-light.png)
![NavDrawerItem](../../../../../app/ui-catalog/screenshots/components/navdraweritem-light.png)

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
vertical fade from transparent to the page colour behind the whole row.

`showLabels` is off by default. A word under every icon is a row of words, and
the destinations of an app this size are the four or five its user already
knows. Turn it on for an app whose icons are not obvious.

![NavDrawerSection](../../../../../app/ui-catalog/screenshots/components/navdrawersection-light.png)
![NavDrawerGroup](../../../../../app/ui-catalog/screenshots/components/navdrawergroup-light.png)

`NavDrawerSection` separates a titled run of destinations; `NavDrawerGroup`
nests them behind a disclosure. The group's expansion is hoisted, so the app can
open the group containing the current page — which is nearly always right and
not something the component can know.

`ModalNavDrawer` is the same drawer rendered into the `OverlayHost`, for a
narrow window where it slides over the content instead of sitting beside it.

### The rail expands

```kotlin
NavRail(
    items = destinations,
    selected = current,
    expanded = railOpen,
    onExpandedChange = { railOpen = it },
)
```

Pass `onExpandedChange` to get the toggle; leave it null for a rail fixed at
whatever `expanded` says. Expanding grows the rail to `NavDrawerDefaults.Width`
and moves the labels beside the icons — the two line up so the switch does not
read as a jump.

A collapsed *expandable* rail shows icons only. Stacking the label and then
moving it beside the icon would pop mid-animation; keeping the icon still and
sliding the label out from behind it is the same treatment
[`ExtendedFloatingActionButton`](actions.md#extendedfloatingactionbutton) uses.

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
[`SegmentedControl`](selection.md#segmentedcontrol) cannot drift apart.

Under reduced motion the marker snaps and cross-fades rather than travelling: a
bar sliding the width of the screen is exactly the translation the preference
exists to stop.

**The selected destination grows and its pill springs in**, ported from the
app's `ToolbarButton`. It is a control people tap dozens of times a session.

A badge is placed against the *glyph*, not the touch target — a 48dp circle
around a 24dp icon would otherwise put the dot floating in empty space well
clear of the thing it annotates.

---

## `TopBar`

Title and actions. Three styles from `TopBarStyle`: `Small`, `Centred` and
`Large`.

```kotlin
TopBar(
    title = stop.name,
    style = TopBarStyle.Large,
    collapseProgress = collapseProgress(listState),
)
```

`Large` collapses to `Small` as the content scrolls. The bar does not own the
scroll state, so the caller passes the progress — `collapseProgress(listState)`
computes it from a `LazyListState`, and there is a raw
`collapseProgress(scrolled, distance)` for anything else.

## `TabBar`

![Tab](../../../../../app/ui-catalog/screenshots/components/tab-light.png)

**`TabBar` is not app navigation.** Tabs stay within one screen — the stop you
are looking at, seen three ways. A tab bar used for destinations leaves the user
with no back stack and no sense of where they are.

Its indicator is one bar that *slides*, for the same reason
[`SegmentedControl`](selection.md#segmentedcontrol)'s does: the row reads as a
single control with a moving part.

**Reach for `SegmentedControl` instead** when you are switching a *value* rather
than a view. Tabs announce `Role.Tab`; segments announce `Role.RadioButton`, and
a screen reader user acts on that difference.

## `Breadcrumbs`

Where you are in a hierarchy, and the way back up. No caller in Anyways today —
it is here for the admin panel.

## `Pagination`

**It collapses only when collapsing saves room.** A range short enough to list
in full is listed in full — "1 2 … 5" is exactly as wide as "1 2 3 4 5" and
shows two fewer pages — and a gap standing in for a single page is replaced by
that page.

`paginationSlots()` is pure and tested, because the failure mode is a control
that is right in the middle of a range and wrong at both ends, and "page 1 of
40" is the first thing anyone sees.

**Reach for [`LoadMore`](collections.md#loadmore) instead** in the app. Numbered
pages are a web pattern; a phone list pages by scrolling.
