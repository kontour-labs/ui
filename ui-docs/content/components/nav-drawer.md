# `ModalNavDrawer`

*Also on this page: `NavSearch`, `NavExpandingSlot`.*

The navigation drawer as a modal: over the content, behind a scrim, dismissed by
back or by a tap outside.

<!--sample:NavDrawerBasics-->
```kotlin
var open by remember { mutableStateOf(false) }
var selected by remember { mutableStateOf("Home") }
var routesOpen by remember { mutableStateOf(true) }

// The same `NavDrawerScope` content the permanent `NavDrawer` takes, over
// the screen instead of beside it — a tree rather than a flat list, because
// a drawer is where destinations stop being three of them.
ModalNavDrawer(visible = open, onDismissRequest = { open = false }) {
    item("Home", icon = Tabler.Outline.Home, selected = selected == "Home") {
        selected = "Home"
        open = false
    }
    section("Saved") {
        item("Stops", selected = selected == "Stops") { selected = "Stops"; open = false }
        group("Routes", expanded = routesOpen, onExpandedChange = { routesOpen = it }) {
            item("950", selected = selected == "950") { selected = "950"; open = false }
            item("998", selected = selected == "998") { selected = "998"; open = false }
        }
    }
    divider()
    item("Alerts", icon = Tabler.Outline.Bell, badge = 3, selected = selected == "Alerts") {
        selected = "Alerts"
        open = false
    }
}
```

The permanent `NavDrawer` is on [nav surfaces](nav-surfaces.md) with the bar and
the rail; this is the same `NavDrawerScope` content shown a different way, for a
window with no room to keep it open. Both take a scope rather than a
`List<NavItem>`, because a drawer is where destinations stop being a flat set of
three and start being sections and groups — a tree wearing a list's shape is
still a tree.

`NavSearch` is the search field a bar or a rail hosts, and `NavExpandingSlot` is
the mechanism behind it: a slot that grows into the surface it sits in rather
than opening a separate overlay, so the search field a reader types into is the
same one they pressed.

---

## Swap the destination when the drawer has gone, not when it is tapped

The obvious lambda — set the destination, close the drawer — puts both in one
snapshot, so **the frame that starts the exit animation is also the frame that
composes a whole destination for the first time**. On a phone that reads as a
tap that did nothing, followed half a second later by a drawer already partway
out. Reported exactly that way. Measured, with the page swap and the drawer's
exit each on their own:

```
  arm          f1     f2     f3     f4     f5     f6     f7     f8
  both       66.4   32.5   18.9   20.3   27.7   27.0   20.7   20.3
  drawer     13.0   13.2   12.0   13.2   12.8   14.3   14.2   14.2
```

66.4ms against 13.0 for the exit on its own — and it is not only the first
frame. The new destination goes on composing for the whole of the animation, so
the exit janks for its full length as well as starting late.

Hold the choice and apply it when the drawer has finished leaving. The overlay
host keeps an entry composed for the whole of its exit, so `onDispose` inside the
drawer's content is that moment, measured rather than timed:

```kotlin
var pending by remember { mutableStateOf<String?>(null) }

ModalNavDrawer(visible = open, onDismissRequest = { open = false }) {
    DisposableEffect(Unit) {
        onDispose { pending?.let { selected = it }; pending = null }
    }
    item("Home", selected = (pending ?: selected) == "Home") {
        pending = "Home"
        open = false
    }
}
```

Nothing is lost by waiting: the drawer covers most of a phone on its way out, so
the page behind it is not being read. Reading `pending` first is what keeps the
answer immediate — the marker travels to the row the moment it is pressed, and
only the page waits.

A destination cheap enough to compose in a frame does not need this. One built
from a `LazyColumn` of cards is not that.

---

## Accessibility

`ModalNavDrawer` sets `paneTitle` (`Theme.strings.navigation` by default), so a
screen reader says what the region that just appeared is. The scrim carries a
labelled dismiss action and the platform back gesture closes it.

Items are `Role.Tab` inside a `selectableGroup()`, so the drawer announces
position as well as name. `group` is a disclosure — its own state is announced,
and the items inside it are not reachable while it is collapsed, which is why a
collapsed group should never hold the only route to something.

`section` is a label for a run of items, not a control. It does not take focus.
