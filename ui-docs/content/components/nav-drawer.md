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

## Accessibility

`ModalNavDrawer` sets `paneTitle` (`Theme.strings.navigation` by default), so a
screen reader says what the region that just appeared is. The scrim carries a
labelled dismiss action and the platform back gesture closes it.

Items are `Role.Tab` inside a `selectableGroup()`, so the drawer announces
position as well as name. `group` is a disclosure — its own state is announced,
and the items inside it are not reachable while it is collapsed, which is why a
collapsed group should never hold the only route to something.

`section` is a label for a run of items, not a control. It does not take focus.
