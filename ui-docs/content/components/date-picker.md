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

← [Date and time](date-time.md) · [All components](../components.md)
