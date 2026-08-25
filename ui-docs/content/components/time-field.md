# `TimeField`

The tappable field that opens a `TimePicker`. It is a field, like
[`Select`](select.md) — same frame, same label, same error slot.

<!--sample:TimeFieldBasics-->
```kotlin
var departAt by remember { mutableStateOf(LocalTime(8, 15)) }

// A read-only field that opens a picker — `onClick`, not `onValueChange`.
// Typing a time into a text field is how you get 25:61.
TimeField(value = departAt, onClick = { start() }, label = "Leave at")
```

---

← [Date and time](date-time.md) · [All components](../components.md)
