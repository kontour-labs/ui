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

## Accessibility

The menu takes focus when it opens. Arrow keys move between items, typing jumps
to a matching one, and Escape closes — all through `onPreviewKeyEvent`, so the
page underneath never sees them.

It does **not** return focus to the anchor on close, which is worth knowing:
after dismissing a menu from the keyboard, focus is wherever the composition left
it. Where the anchor matters — a toolbar the user was working through — hold a
`FocusRequester` on it and request focus in the `onDismissRequest`.

`item(selected = true)` reports **`Role.RadioButton`**, not a checked button:
"one of these" is the thing a sort order is, and a menu of buttons all reporting
"button" tells a screen reader user nothing about which one is in force. An
unselected item is `Role.Button`.

Items are collected by a builder rather than composed as children, which is why
the menu can own the roles and the keyboard traversal instead of each caller
rebuilding them.
