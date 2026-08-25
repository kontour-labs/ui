# `RadioButton`

![RadioButton, unselected](../../../ui-catalog/screenshots/components/radiobutton-light.png)
![RadioButton, selected](../../../ui-catalog/screenshots/components/radiobutton-selected-light.png)

The control on its own. You almost never want this directly — see below.

<!--sample:RadioButtonBasics-->
```kotlin
var mode by remember { mutableStateOf("Train") }

// The row carries the click and the role; the button is passed `null` so it
// is not a second target announcing the same thing. A bare `RadioButton` is
// for a table cell or a custom row — everywhere else, use `RadioGroup`.
SelectionRow(
    selected = mode == "Train",
    onSelectedChange = { mode = "Train" },
    role = Role.RadioButton,
) {
    +"Train"
    leading { RadioButton(selected = mode == "Train", onClick = null) }
}
```

---

## Accessibility

A bare `RadioButton` is not a group, and a group is what a screen reader needs:
[`RadioGroup`](radio-group.md) applies `selectableGroup()`, which is what makes
"option 2 of 5" possible. A column of radio buttons without it is five unrelated
controls that happen to be near each other.

Pass `onClick = null` when the row around it carries the click — otherwise there
are two targets announcing the same thing, and the smaller one is the button.
[`SelectionRow`](selection-row.md) with `role = Role.RadioButton` is the shape
that gets this right.

Under `Role.RadioButton` selection is one-way: a radio is turned on by pressing
it and off by pressing another, so pressing the selected one does nothing rather
than clearing it.

---

← [Selection](selection.md) · [All components](../components.md)
