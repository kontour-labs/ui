# `DropdownMenu`

*Also on this page: `AnchoredDropdownMenu`, `MenuItem`, `MenuDivider`, `MenuSectionHeader`, `SubMenu`, `MenuActionRow`.*

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
        item("Share", leadingIcon = Tabler.Outline.Share, shortcut = "⌘S", onClick = { open = false })
        item("Copy stop ID", leadingIcon = Tabler.Outline.Copy, onClick = { open = false })
        item("Set a reminder", enabled = false, onClick = {})
        divider()
        section("Sort departures by")
        item("Departure time", selected = order == 0, onClick = { order = 0 })
        item("Journey length", selected = order == 1, onClick = { order = 1 })
        divider()
        item(
            "Remove favourite",
            leadingIcon = Tabler.Outline.Trash,
            destructive = true,
            onClick = { open = false },
        )
    }
}
```

An item can be `selected`, which turns the menu into a single-choice list —
useful for a sort order, where a separate radio group would be a second control
for one decision.

**A row of actions across the top** — the two to four used most, an icon over a
word each, as an iOS menu has them — is `actionRow`:

```kotlin
DropdownMenu(visible = open, onDismissRequest = { open = false }) {
    actionRow {
        action("Share", Tabler.Outline.Share) { share(stop) }
        action("Copy", Tabler.Outline.Copy) { copy(stop.id) }
        action("Save", Tabler.Outline.Star) { save(stop) }
    }
    item("Report a problem") { report(stop) }
}
```

Each is a button that closes the menu as it runs, and a hairline follows the
row. Put it first. The actions stand on the menu itself, and a press shows where
each one is; `actionRow(filled = true)` sets each on a sunken ground of its own
instead. Outside the builder, `MenuActionRow { }` is the same row, and leaves
closing to its actions.

**On touch a submenu opens in place.** A tap on its row opens it over the row,
growing from the row's height, with the row kept on top and its chevron turning
to point down: tap it to go back, or tap the menu around it. The menu under it
dims and draws back a little, and a screen reader stops reaching it. Going back,
the submenu folds into its row and fades as it arrives, so what is left is the
row itself; and the menu under it dims by a shade drawn over its rows, inside
its shape, so its shadow stays round. Submenus nest the same way. Near
the bottom of a menu too short to hold it, the submenu is moved up to fit. With a
mouse or the keyboard a submenu opens beside its row instead, on hover or on
Right — which is where a pointer is going, and where a phone has no room. Both
are asked for from a phone; the in-place one is how iOS nests its menus.

**Nested menus are worth being sparing with.** Two levels is a category; three is
a filing system, and by then a flat list with section headers is easier to scan.

**A drag on a menu is the menu's.** It scrolls a menu that scrolls, with its
stretch or bounce at the ends, and does nothing to one that fits; it never moves
what is under the menu. Nor does the wheel. That mattered where an
`OverlayHost` sits in a page that scrolls — a card or a pane with a host of its
own — and the page took whatever the menu's list did not use.

For the same list on a right-click or a long press, see
[`ContextMenuArea`](context-menu-area.md). For content that is not a list of
actions, [`Popover`](popover.md).

---

## Accessibility

**From the keyboard.** Opened from the keyboard, the menu puts the focus on its
first row that can be chosen, and the keys are the menu's — the page underneath
never sees them:

| Key | Does |
|---|---|
| Down, Up | The next or previous row, passing over disabled rows, headings and rules. Past the last row Down goes round to the first, and Up the other way. |
| Home, End | The first or last row that can be chosen. |
| Page Down, Page Up | A list's height, less a row, on or back; stopping at the ends. |
| Letters | The next row whose words begin with what was typed. |
| Enter, Space | Picks the row. |
| Right | Opens a submenu, with the focus on its first row. |
| Left | Closes the submenu, with the focus back on its row. |
| Escape | Closes a submenu, with the focus back on its row; then the menu. |
| Tab | Walks the rows in order, as before. |

Right and Left are the other way round right to left. Left and Right walk the
action row, which Up and Down pass through as one row.

**The arrows go round** at the ends, as a menu on the Mac does and as WAI-ARIA's
menu pattern does; Windows' list boxes stop. Going round is the one of those that
never leaves a key doing nothing, and it is one rule for every menu in the
library, a select's list included. Home and End are the way to an end.

**Typing** matches the start of a row's words, ignoring case and accents. Letters
typed within three-quarters of a second of each other are one word, so "pa" goes
to *Paste* rather than to a *P* and then an *A*; after a pause the next letter
starts again. The same letter again goes on to the next row beginning with it. A
space is a letter while a word is under way, and picks the row otherwise. A row
whose label is drawn with `label { }` rather than `+"…"` has no words to be found
by.

**Opened with a mouse**, the menu holds the focus without showing it: no row is
focused and no ring is drawn until a key is pressed. The first arrow then goes to
the first row (Up to the last), and a letter to the row it begins, with the ring
on it. On touch the menu takes no focus. A submenu a pointer opens by resting on
its row leaves the keys with the menu the row is in.

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

A divider is the menu's own text colour at a low alpha rather than a line
token, so it stands off the raised panel by as much as a list's divider stands
off a list, in light and dark, and on a theme's own panel colour.

At [high contrast](../accessibility.md#what-high-actually-changes) the panel's
border goes from the faint `outlineSubtle`, and the dividers from that wash, to
the near-ink `outline`, and both thicken with the tier, as does the focus ring. Where a long
menu shows a scrollbar, it stays at full ink rather than fading when idle.

At high contrast the action row's cells take the tier's edge, filled or not, as a
ghost button does.

Under reduced motion the menu, and any submenu, fades in where it opens instead
of growing out of its anchor; a submenu opening in place fades in over its row,
and the menu under it dims without drawing back. A row never shrinks when pressed, at any setting:
the tonal wash is its whole press.
