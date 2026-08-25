# `ContextMenuArea`

Wraps content so a secondary click or a long press opens a menu at the pointer.

<!--sample:ContextMenuAreaBasics-->
```kotlin
ContextMenuArea(
    menu = {
        MenuItem(onClick = { report() }) { +"Report a problem" }
        MenuItem(onClick = { suggest() }) { +"Suggest a correction" }
    },
) {
    Text("Perth Underground")
}
```

**A context menu must never be the only route to an action.** Anything in one
needs a visible path too — a toolbar button, an item in a
[`DropdownMenu`](dropdown-menu.md), a swipe action. A context menu is a shortcut
for people who already know the action exists.

It draws nothing of its own until something asks for it, which is why it is one
of the two components deliberately absent from `componentRegistry`: a card of it
would be a card of whatever is inside it.

---

## Accessibility

The gesture is right-click on a pointer and long-press on touch — and a gesture
is not an affordance. Anything reachable *only* through this menu is unreachable
for someone who can do neither, so a context menu must duplicate actions that
exist elsewhere rather than be the only place they live.

The menu itself is a [`DropdownMenu`](dropdown-menu.md) and behaves as one once
open: focus, arrow keys, typeahead, Escape.

---

← [Overlays](overlays.md) · [All components](../components.md)
