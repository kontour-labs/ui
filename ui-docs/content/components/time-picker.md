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

A day period appears as a third wheel on a 12-hour clock — before the time
where the locale writes it there, as Korean and Japanese do — which is a
[`LocaleFormats`](locale-formats.md) decision rather than a parameter. So is
what sits between the hour and the minute (`.` in Finnish), and the digits.

`onValueChange` follows every row a wheel passes; `onValueChangeFinished` comes
once a wheel has come to rest, which is where a save or a request belongs. A
`value` between two of `minuteStep`'s rows is put on the row before it, in the
same hour, and reported: 9:58 by fives opens on 9:55.

**Now, under the wheels.** A small button that scrolls the wheels to the time
now, rounded to the nearest `minuteStep`. They turn through the rows to get
there rather than jumping, so the reader sees where the time went, and
`onValueChangeFinished` comes once they arrive. `now` is the clock it reads,
`TimePickerDefaults.currentTime()` by default; pass `null` for no button on a
picker whose time has nothing to do with the present.

**The wheels go round, as a clock does.** Past :59 comes :00 and past 23 comes
0, with no end to reach: 23:30 to half past midnight is one row on, not
twenty-three back. On a 12-hour clock the day period turns with the hour, so
11 PM turned on is 12 AM rather than noon, as an iOS picker's does. Only the
wheel goes round: the minutes passing :00 leave the hour as it is.
`infinite = false` gives each wheel its ends back, as they were before 0.5.1.

---

## Accessibility

The three wheels are named — "Hour", "Minute", "AM or PM" — because a wheel with
no name announces a number and nothing about what the number is.

Each wheel reports its centred value as its state, so moving it says the new
value rather than only that something scrolled.

Each wheel is a stop for the keyboard, hour then minute then AM or PM, and turns
from it as a [`WheelPicker`](wheel-picker.md) does: Up and Down a row, round
the end of the clock as a finger takes it, Page Up and Page Down a face, Home
and End the first and last rows, and typing to a row — "45" on the minute
wheel. A screen reader can turn each one too, as an adjustable control.

`minuteStep` is worth setting. At the default of 1 the minute wheel has sixty
stops, which is sixty announcements to cross by swipe.

At high contrast the band behind each wheel's chosen row takes the tier's edge,
and the rows either side stay legible: they fade to no less than 0.6 of the ink,
where the standard tier takes them down to 0.2.

Under reduced motion the wheels still turn under the finger and settle on the
nearest row when it lets go, because a drag is never touched. A value set from
code puts the drum straight on it at every setting, and a tap on a row above or
below the band puts it there at once.
