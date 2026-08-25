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

← [Selection](selection.md) · [All components](../components.md)
