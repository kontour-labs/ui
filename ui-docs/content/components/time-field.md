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

## Accessibility

The label is the field's **accessible name**, set on the control with
`contentDescription` while the visible label is drawn with
`clearAndSetSemantics {}` — the same arrangement `FieldScaffold` uses for every
text field, and for the same reason: Compose has no `labelledBy`, so a label
drawn above a control is an unrelated node however close it is on screen.

The formatted time is the field's `stateDescription`, so what is announced is
"Leave at, 08:15, button" rather than a control whose name is its own value.

It is a button, not an editable field, and deliberately: typing a time into a
text field is how you get 25:61. Pressing it opens a
[`TimePicker`](time-picker.md).
