# `SearchField`

![SearchField](../../../ui-catalog/screenshots/components/searchfield-light.png)

A debounced query callback and an animated clear button. The debounce is the
point: a field that fires per keystroke into a network call produces a request
per letter and renders the results out of order.

<!--sample:SearchFieldBasics-->
```kotlin
val query = rememberTextFieldState()

// `onQuery` is debounced and `onSearch` is not: the first is for filtering
// a list as the user types, the second for the action key. Wiring a network
// call to every keystroke is the bug this split exists to prevent.
SearchField(
    state = query,
    placeholder = "Search stops",
    onQuery = { openStop(it) },
    onSearch = { openStop(it) },
)
```

**Reach for `SearchField` over `Combobox`** when the answer is free text and the
suggestions are a convenience. Reach for `Combobox` when the value must be one
of the options.

---

← [Text editing](text-editing.md) · [All components](../components.md)
