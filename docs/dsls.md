# The shorthands

Every component in the library takes parameters and slots. Some of them also
have a **shorthand** — a scope with a handful of small functions on it, for the
shape you write nine times out of ten.

```kotlin
DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
    section("This stop")
    item("Share", icon = Tabler.Outline.Share, shortcut = "⌘S") { share(stop) }
    item("Copy stop ID", icon = Tabler.Outline.Copy) { copy(stop.id) }
    divider()
    item("Remove favourite", icon = Tabler.Outline.Trash, destructive = true) {
        remove(stop)
    }
}
```

---

## They do not replace anything

Each scope **extends the receiver the content lambda already had**, so every
component still works inside it, unchanged:

| Scope | Extends | Used by |
|---|---|---|
| `MenuScope` | `ColumnScope` | `DropdownMenu`, `AnchoredDropdownMenu`, `SubMenu` |
| `NavDrawerScope` | `ColumnScope` | `NavDrawer`, `ModalNavDrawer`, `NavDrawerSection`, `NavDrawerGroup` |
| `ListGroupScope` | — | `ListGroup`, `LazyListScope.listGroup` |

That is why adopting them broke nothing: a menu body written against
`ColumnScope` before is a menu body written against `MenuScope` now, because
`MenuScope` *is* a `ColumnScope`. Mix them freely.

```kotlin
DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
    item("Share") { share(stop) }

    // Not in the shorthand — nothing here takes a modifier. Use the component.
    MenuItem("Pin", onClick = ::pin, modifier = Modifier.testTag("pin"))
}
```

**No shorthand takes a `modifier`.** That is the rule, and it is what keeps them
small: the moment a row needs one it has outgrown the shorthand, and the
component is right there in the same scope.

---

## What each one is actually for

Not brevity. Each shorthand deletes a specific mistake.

### `MenuScope` — the menu that stays open

`MenuItem` cannot close the menu it is in; it has never been told how. Written
by hand, every row is:

```kotlin
MenuItem("Share", onClick = { open = false; share(stop) })
```

and the one that forgets leaves a menu hanging over the screen it just navigated
away from. `item` does the dismiss for you, and closes *first* — an action that
navigates takes the composition with it, and a dismiss queued behind it never
runs.

`closeOnClick = false` for a row the user is likely to press again, like a filter
toggle. `submenu` closes the whole stack rather than just itself, for the same
reason one level up.

### `ListGroupScope` — the corners

A group of rows is one object with rows in it: the first rounds its top, the last
rounds its bottom, the rest are square. By hand that is index arithmetic at every
call site.

```kotlin
val positions = listPositions(stops.size)          // the bit people forget
stops.forEachIndexed { index, stop ->
    ListItem(label = stop.name, position = positions[index], …)
}
```

```kotlin
ListGroup {                                        // the same thing, counted for you
    stops.forEach { item(it.name, supporting = it.detail) { open(it) } }
}
```

Get it wrong and you get a group with two rounded rows in the middle of it, which
reads as a rendering fault rather than as a mistake.

Inside a `LazyColumn`, use `listGroup`:

```kotlin
LazyColumn(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
    item { SectionHeader("Saved places") }
    listGroup(savedPlaces, key = { it.id }) { place ->
        item(place.name, supporting = place.detail, icon = Tabler.Outline.Bus) {
            open(place)
        }
    }
}
```

The row *content* stays lazy — a row scrolled off screen is not composed — but
the builder runs for every element up front, because the first row cannot know it
is first until something has counted the rest. That is the right trade for the
lists this is for and the wrong one for a list of thousands; a group of thousands
of rows with rounded ends is not a thing anyone wants to look at, so the
constraint and the design agree.

### `NavDrawerScope` — the indent

A destination inside a `group` inside a `section` is still a destination. It
keeps its full touch target and reports its selection to the travelling pill
through a composition local rather than through its parent, so the only thing
nesting changes is the indent — and by hand that means threading `nestLevel`
down every branch.

```kotlin
NavDrawer(header = { AppMark() }) {
    destination("Home", Tabler.Outline.Home, selected = tab == Tab.Home) { go(Tab.Home) }

    section("Saved") {
        destination("Favourites", Tabler.Outline.Star, badge = 3) { go(Tab.Favourites) }
        group("Routes", expanded = open, onExpandedChange = { open = it }) {
            destination("950", selected = route == "950") { go("950") }
        }
    }
}
```

---

## The `ListGroupScope` builder is not composable

It **collects** rows rather than emitting them, because the position of the first
row depends on how many come after it. It still runs inside composition, so

```kotlin
ListGroup {
    item("Profile") { … }
    if (signedIn) item("Sign out") { … }      // fine — recomposes on the state read
}
```

works. What you cannot do is call a composable or read `Theme` in the builder
itself. Use `row` for that — it takes the whole `@Composable` and hands you the
position:

```kotlin
ListGroup {
    item("Notifications") { … }
    row { position ->
        ListItem(label = "Sign out", position = position, onClick = ::signOut)
    }
}
```

`row` is the escape hatch, and the reason the other shorthands can stay small.

---

## How they are tested

Two ways, because the two claims are different.

**"It renders the same thing."** Two catalog panels — the menu and the grouped
rows — were rewritten with the shorthands, and their screenshot goldens did not
move. That is not an assertion anybody wrote; it is the existing golden refusing
to change.

**"It does the thing a golden cannot see."** `DslBehaviourTest` covers the
positions each row is handed (including the one-row and two-row cases a
three-item example never exercises) and whether a menu closes itself. Both guards
were verified by reverting: reading the lazy builder's running offset late
instead of capturing it makes every row `Middle`, and the test says so.
