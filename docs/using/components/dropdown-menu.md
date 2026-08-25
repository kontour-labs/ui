# `DropdownMenu`

*Also on this page: `AnchoredDropdownMenu`, `MenuItem`, `MenuDivider`, `MenuSectionHeader`, `SubMenu`.*

A list of actions off a control. The items are a DSL rather than children:
`item`, `divider`, `section` and `submenu`, so the menu owns the roles, the
keyboard traversal and the shortcut column instead of each caller rebuilding
them.

<!--sample:DropdownMenuBasics-->
```kotlin
var open by remember { mutableStateOf(false) }
var order by remember { mutableStateOf(0) }

Box {
    IconButton(
        icon = Tabler.Outline.Dots,
        contentDescription = "More",
        onClick = { open = !open },
    )
    DropdownMenu(visible = open, onDismissRequest = { open = false }) {
        section("This stop")
        item("Share", icon = Tabler.Outline.Share, shortcut = "⌘S", onClick = { open = false })
        item("Copy stop ID", icon = Tabler.Outline.Copy, onClick = { open = false })
        item("Set a reminder", enabled = false, onClick = {})
        divider()
        section("Sort departures by")
        item("Departure time", selected = order == 0, onClick = { order = 0 })
        item("Journey length", selected = order == 1, onClick = { order = 1 })
        divider()
        item(
            "Remove favourite",
            icon = Tabler.Outline.Trash,
            destructive = true,
            onClick = { open = false },
        )
    }
}
```

An item can be `selected`, which turns the menu into a single-choice list —
useful for a sort order, where a separate radio group would be a second control
for one decision.

**Nested menus are worth being sparing with.** Two levels is a category; three is
a filing system, and by then a flat list with section headers is easier to scan.

For the same list on a right-click or a long press, see
[`ContextMenuArea`](context-menu-area.md). For content that is not a list of
actions, [`Popover`](popover.md).

---

← [Overlays](overlays.md) · [All components](../components.md)
