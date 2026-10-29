# `DateTimePicker`

**A [`DatePicker`](date-picker.md) and a [`TimePicker`](time-picker.md), as one
value.** The month grid with its header, paging, chooser and marks, and the hour
and minute wheels on the reader's own clock — each as it is on its own, but for
the wheels' width. A day tapped keeps the time, and a wheel turned keeps the day.

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
`minuteStep` and `infinite` what they mean on the `TimePicker`. The clock,
12-hour or 24, comes from [`LocaleFormats`](locale-formats.md) as the time
picker's does.

The hour and minute wheels go round, as the time picker's do, unless
`infinite = false`. The day stays as it is when the hours pass midnight: it is
the calendar's to change, and a wheel that moved it would page the calendar
under the reader's other hand.

**Now** is under the wheels, as on the time picker, wherever the wheels are —
beside the calendar or in the popover described below: the wheels scroll to the
time now, and the day becomes today, the calendar paging to it, or tomorrow
when the time rounds past midnight. Where `isDateSelectable` refuses tomorrow,
the wheels stop at the last step of today instead — 23:55 by fives. `now`
reads the clock, `DateTimePickerDefaults.currentDateTime()` by default; `null`
hides it.

**Each change, and then the settled one.** `onValueChange` hears every change:
a day tapped, and each row a wheel turns past. `onValueChangeFinished` hears a
change once it has settled — a day as soon as it is tapped, a wheel once it
comes to rest — and is where a save or a request belongs.

**Side by side, or the calendar with a row for the time.** Wherever the
calendar, at 40dp a day, a small gap and the wheels fit across, the two sit next
to each other, centred together, the calendar taking whatever is left up to its
widest. The wheels are measured rather than assumed, so the room needed depends
on the clock and the type size:

| Clock | Side by side from, default type | From, larger type (1.3×) | Narrower |
| --- | --- | --- | --- |
| 24-hour | 390dp | 406dp | Calendar, and a time row under it |
| 12-hour, with am/pm | 453dp | 476dp | Calendar, and a time row under it |

So given a large phone's whole width — 393dp and up — a 24-hour clock shows the
wheels beside the calendar. Narrower, as on any phone with a 12-hour clock, it
is what an iOS inline picker is: the calendar across the width, up to its
widest, and under it one row, **Time** at its start and the chosen time in a
pill at its end, lined up with the calendar's edges. The row is one control
high, about 44dp, so the date and the time both show at once at any width the
calendar does.

Pressing the pill opens the wheels — the same wheels, with **Now** — in a small
popover anchored to it, and tints the pill while they are open. They set the
time as they turn, as beside the calendar, and a tap outside, back or Escape
puts them away. The popover is a [`Popover`](popover.md), so it needs an
[`OverlayHost`](overlay-host.md) to open into, as a menu does. It comes and
goes as any popover does, scaling from its pill as it fades, and under reduced
motion only fading.

Only the width the picker is given decides, never its height, so nothing
changes arrangement as a page scrolls or a sheet is dragged, and the calendar
keeps its month through a change of arrangement. The wheels never go under the
calendar: that takes up far more height than it saves. It still needs its whole
month, so put it somewhere that scrolls, as a date picker does.

The wheels here are as wide as their widest number, with the same room either
side as the day period's wheel has, rather than the time picker's 72dp: it is
what lets the two fit side by side on a phone.

`isDateSelectable` turns days away, not times. A time earlier today than now is
the caller's to refuse, since only the caller knows whether now is the floor.

---

## Accessibility

It is the two pickers, and reads as them. Where the time is a row under the
calendar, its pill is a button named "Time" that says the time as its state —
"Time, 8:15 pm, button" — and can be expanded and collapsed. Opened from the
keyboard, the focus goes to the hour wheel in the popover, which the arrows and
typing turn as a [`TimePicker`](time-picker.md)'s do, and when it closes the
focus comes back to the pill. The month header is a live region
that announces each month paged to, each day says its full date when pressed and
whether it can be chosen, and the wheels are named "Hour", "Minute" and, on a
12-hour clock, "AM or PM", each reporting its centred value as its state.

`minuteStep` is worth setting here as on the time picker: at 1 the minute wheel
has sixty stops to swipe across.

At high contrast and under reduced motion each half does what it does on its
own — see [`DatePicker`](date-picker.md#accessibility) and
[`TimePicker`](time-picker.md#accessibility).
