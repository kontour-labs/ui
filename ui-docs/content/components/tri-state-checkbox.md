# `TriStateCheckbox`

![TriStateCheckbox](../../../ui-catalog/screenshots/components/tristatecheckbox-light.png)

<!--sample:TriStateCheckboxBasics-->
```kotlin
var routes by remember { mutableStateOf(listOf(true, false, false)) }

val state = when {
    routes.all { it } -> ToggleableState.On
    routes.none { it } -> ToggleableState.Off
    else -> ToggleableState.Indeterminate
}

TriStateCheckbox(
    state = state,
    onClick = { routes = List(routes.size) { state != ToggleableState.On } },
)
```

`ToggleableState.Indeterminate` draws a dash, for a parent whose children are
partly selected. Clicking an indeterminate checkbox should select everything,
not clear it — that is the caller's decision, and the common wrong answer.

---

## Accessibility

`Role.Checkbox` with a three-valued `toggleableState`, so the indeterminate state
is announced as indeterminate rather than as unchecked. A parent row that looks
half-ticked and reports "not checked" is worse than no parent row.

The cycle order is on → off → indeterminate only if you write it that way.
Prefer two states for the user's press — on and off — and let indeterminate be
something the *children* produce, which is what it means.

The label still belongs on the row. Put it in a
[`SelectionRow`](selection-row.md).

---

← [Selection](selection.md) · [All components](../components.md)
