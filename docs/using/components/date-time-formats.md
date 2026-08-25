# `DateTimeFormats`

Carries the two preferences users actually notice — 12/24-hour and day-first —
because "05/06" is two different days depending on the answer. Also the
first-day-of-week.

<!--sample:DateTimeFormatsBasics-->
```kotlin
// Provided once, near the root. Every date and time component below reads
// it, so 12-hour clocks and a Sunday week start are one decision rather
// than a parameter on nine call sites.
CompositionLocalProvider(
    LocalDateTimeFormats provides DateTimeFormats(
        is24Hour = false,
        dayFirst = false,
        firstDayOfWeek = DayOfWeek.SUNDAY,
    ),
) {
    Screen()
}
```

Provided once at the root through `LocalDateTimeFormats`.

---

← [Date and time](date-time.md) · [All components](../components.md)
