# `TimePicker`

**Wheels rather than a clock dial.** In a transit app the value is almost always
being *adjusted* ("leave at 8:15 instead of 8:00"), and a wheel gets there in one
flick where a dial needs two precise drags.

<!--sample:TimePickerBasics-->
```kotlin
var departAt by remember { mutableStateOf(LocalTime(8, 15)) }

// Five-minute steps, because a timetable has no use for 08:17.
TimePicker(value = departAt, onValueChange = { departAt = it }, minuteStep = 5)
```

AM/PM appears as a third wheel on a 12-hour clock, which is a
[`DateTimeFormats`](date-time-formats.md) decision rather than a parameter.

---

## Accessibility

The three wheels are named — "Hour", "Minute", "AM or PM" — because a wheel with
no name announces a number and nothing about what the number is.

Each wheel reports its centred value as its state, so moving it says the new
value rather than only that something scrolled.

`minuteStep` is worth setting. At the default of 1 the minute wheel has sixty
stops, which is sixty announcements to cross by swipe.
