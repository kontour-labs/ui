# `SearchField`

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

## Accessibility

The clear button carries `clearLabel`, which it needs — it is an icon inside a
field and has no other name.

`onQuery` is debounced and `onSearch` is not, and that split matters more than it
looks: filtering a list on every keystroke re-announces the results under a
screen reader as fast as the user can type. Debounce is what makes the
announcement settle.

Where the field filters a list, the list's own count should be announced when it
settles — the field cannot do it, because it does not know what is being
filtered.
