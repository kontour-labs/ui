# `ModalNavDrawer`

The navigation drawer as a modal: over the content, behind a scrim, dismissed by
back or by a tap outside.

*Also on this page: `NavSearch`, `NavExpandingSlot`.*

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

← [Navigation](navigation.md) · [All components](../components.md)
