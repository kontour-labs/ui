# `DatePicker`

Single date, with month paging.

<!--sample:DatePickerBasics-->
```kotlin
var travelDate by remember { mutableStateOf<LocalDate?>(null) }

DatePicker(
    selected = travelDate,
    onSelectedChange = { travelDate = it },
    today = LocalDate(2026, 6, 12),
    // Timetables do not go back, so neither does the picker.
    isDateSelectable = { it >= LocalDate(2026, 6, 12) },
)
```

---

## Accessibility

The month header is a **polite live region**, so paging announces the new month
rather than leaving the user to work out that thirty-one buttons changed
underneath them. The arrows carry "Previous month" and "Next month".

The grid itself is [`CalendarMonth`](calendar-month.md), and that is where each
day's semantics live: a `selectable` node with `Role.Button` and
`stateDescription = formats.dateFull(date)` — "Thursday 18 June 2026" — set on the
node that is actually pressed. It was previously on the decorative box that draws
the highlight, a sibling a screen reader reaches separately if at all, so the
thing a user landed on said "18, button" and nothing about which date that was.

`isDateSelectable` disables a day rather than hiding it, so the shape of the
month stays readable and an unavailable date is announced as unavailable.

---

← [Date and time](date-time.md) · [All components](../components.md)
