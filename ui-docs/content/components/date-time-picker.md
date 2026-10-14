# `DateTimePicker`

**A [`DatePicker`](date-picker.md) and a [`TimePicker`](time-picker.md), as one
value.** The month grid with its header, paging, chooser and marks, and the hour
and minute wheels on the reader's own clock — each exactly as it is on its own.
A day tapped keeps the time, and a wheel turned keeps the day.

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

The calendar's parameters — `today`, `isDateSelectable`, `decorationFor`, the icons,
`navigation` and `firstDayOfWeek` — mean what they mean on the `DatePicker`, and
`minuteStep` what it means on the `TimePicker`. The clock, 12-hour or 24, comes
from [`LocaleFormats`](locale-formats.md) as the time picker's does.

**Now** is under the wheels, as on the time picker: the wheels scroll to the
time now, and the day becomes today, the calendar paging to it, or tomorrow
when the time rounds past midnight. Where `isDateSelectable` refuses tomorrow,
the wheels stop at the last step of today instead — 23:55 by fives. `now`
reads the clock, `DateTimePickerDefaults.currentDateTime()` by default; `null`
hides it.

**Each change, and then the settled one.** `onValueChange` hears every change:
a day tapped, and each row a wheel turns past. `onValueChangeFinished` hears a
change once it has settled — a day as soon as it is tapped, a wheel once it
comes to rest — and is where a save or a request belongs.

**Side by side, one over the other, or one at a time.** Given 560dp or more
across, the calendar and the wheels sit next to each other. Narrower — a phone —
the wheels go under the calendar where the picker may be 600dp tall: the height
it is given, or in a page that scrolls, the window's less 160dp for the bars
round the page. That is any phone but a small one; the two come to about 566dp.
Shorter than that, a segmented control above them says the chosen date and the
chosen time, and shows the calendar or the wheels as one or the other is
picked. One at a time, the space is the calendar's whichever is showing, so
swapping moves nothing below it. It still needs its whole month, so put it
somewhere that scrolls, as a date picker does.

`isDateSelectable` turns days away, not times. A time earlier today than now is
the caller's to refuse, since only the caller knows whether now is the floor.

---

## Accessibility

It is the two pickers, and reads as them. On a small phone the segmented
control is read first, saying the chosen date and time, and the half not showing
says nothing until it is chosen. The month header is a live region
that announces each month paged to, each day says its full date when pressed and
whether it can be chosen, and the wheels are named "Hour", "Minute" and, on a
12-hour clock, "AM or PM", each reporting its centred value as its state.

`minuteStep` is worth setting here as on the time picker: at 1 the minute wheel
has sixty stops to swipe across.

At high contrast and under reduced motion each half does what it does on its
own — see [`DatePicker`](date-picker.md#accessibility) and
[`TimePicker`](time-picker.md#accessibility).
