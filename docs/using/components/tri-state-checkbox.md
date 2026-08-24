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

← [Selection](selection.md) · [All components](../components.md)
