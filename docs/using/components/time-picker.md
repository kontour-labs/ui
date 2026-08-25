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

← [Date and time](date-time.md) · [All components](../components.md)
