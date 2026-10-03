# `DateTimePicker`

**A [`DatePicker`](date-picker.md) with a [`TimePicker`](time-picker.md) under
it, as one value.** The month grid with its header, paging, chooser and marks,
and under it the hour and minute wheels on the reader's own clock — each exactly
as it is on its own. A day tapped keeps the time, and a wheel turned keeps the
day.

<!--sample:DateTimePickerBasics-->
```kotlin
var leaveAt by remember { mutableStateOf(LocalDateTime(LocalDate(2026, 6, 12), LocalTime(8, 15))) }

// One value, a day and a time: a day tapped keeps the time, and a wheel
// turned keeps the day.
DateTimePicker(
    value = leaveAt,
    onValueChange = { leaveAt = it },
    today = LocalDate(2026, 6, 12),
    isDateSelectable = { it >= LocalDate(2026, 6, 12) },
    minuteStep = 5,
)
```

The calendar's parameters — `today`, `isDateSelectable`, `markerFor`, the icons,
`navigation` and `firstDayOfWeek` — mean what they mean on the `DatePicker`, and
`minuteStep` what it means on the `TimePicker`. The clock, 12-hour or 24, comes
from [`LocaleFormats`](locale-formats.md) as the time picker's does.

**Each change, and then the settled one.** `onValueChange` hears every change:
a day tapped, and each row a wheel turns past. `onValueChangeFinished` hears a
change once it has settled — a day as soon as it is tapped, a wheel once it
comes to rest — and is where a save or a request belongs.

**It is as tall as both**, about 560dp at the default type size, so put it
somewhere that scrolls: a sheet, a dialog, or a page. The two are stacked and
centred, the calendar no wider than a phone's; side by side they would suit a
wide window and not fit a phone at all.

`isDateSelectable` turns days away, not times. A time earlier today than now is
the caller's to refuse, since only the caller knows whether now is the floor.

---

## Accessibility

It is the two pickers, and reads as them: the month header is a live region
that announces each month paged to, each day says its full date when pressed and
whether it can be chosen, and the wheels are named "Hour", "Minute" and, on a
12-hour clock, "AM or PM", each reporting its centred value as its state.

`minuteStep` is worth setting here as on the time picker: at 1 the minute wheel
has sixty stops to swipe across.

At high contrast and under reduced motion each half does what it does on its
own — see [`DatePicker`](date-picker.md#accessibility) and
[`TimePicker`](time-picker.md#accessibility).
