# `SettingRow`

The settings-screen shape: an icon, a label, and the current value on the right.
`onClick` is optional — a row that only displays a value does not need one.

<!--sample:SettingRowBasics-->
```kotlin
SettingRow(onClick = { save() }) {
    +"Live vehicle positions"
    supporting { +"Uses more data" }
}
```

**`SettingRow` is `clickable`, not `toggleable`.** It is a row you tap to open
something, which may happen to carry a switch. That is why a control inside it
must publish its own state — see
[`Checkbox`](checkbox.md).

**Reach for [`SelectionRow`](selection-row.md) instead** when tapping
the row *is* the toggle. `SettingRow` opens a screen; `SelectionRow` flips a
value.

---

## Accessibility

A `SettingRow` is a [`ListItem`](list-item.md) with settings defaults, so the
same rule governs it: the **row** carries the click and the role, and the control
inside it takes `onCheckedChange = null`. Two targets for one setting is the
defect this shape exists to prevent, and the switch is the smaller of the two.

`supporting` is part of the row's announcement, not a footnote — put the
consequence there ("Uses more data") rather than in a tooltip.

`position` is visual only; it rounds the corners and announces nothing. Grouping
is expressed by [`ListSection`](list-section.md).
