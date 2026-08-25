# `DropdownMenu`

A list of actions off a control. The items are a DSL rather than children:
`item`, `divider`, `section` and `submenu`, so the menu owns the roles, the
keyboard traversal and the shortcut column instead of each caller rebuilding
them.

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
