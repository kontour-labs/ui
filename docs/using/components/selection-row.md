# `SelectionRow`

![SelectionRow, unselected](../../../ui-catalog/screenshots/components/selectionrow-light.png)
![SelectionRow, selected](../../../ui-catalog/screenshots/components/selectionrow-selected-light.png)

<!--sample:SelectionRowBasics-->
```kotlin
var notifyOnDelay by remember { mutableStateOf(false) }

SelectionRow(
    selected = notifyOnDelay,
    onSelectedChange = { notifyOnDelay = it },
    role = Role.Checkbox,
) {
    +"Notify me about delays"
    supporting { +"Only for favourited routes" }
    // The row owns the interaction; the control is here to show state.
    trailing { Checkbox(notifyOnDelay, onCheckedChange = null) }
}
```

**This is the form almost every checkbox, radio and switch should take.** The
nested control takes `onCheckedChange = null` — the row owns the interaction,
the control is there to show state.

It takes [`ListItem`](list-item.md)'s builder rather than one of its
own, because a selection row is a list row that happens to toggle. Which slot
the control goes in *is* its position — there is no `controlPosition`, and
`leading` suits a list of options being picked from where `trailing` suits a
settings list.

`role` is required rather than inferred, because the row cannot see which
control you nested in it.

---

← [Selection](selection.md) · [All components](../components.md)
