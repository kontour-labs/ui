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

## Accessibility

This decides what is *announced*, not only what is drawn. `is24Hour`, `dayFirst`
and `firstDayOfWeek` feed the full-date `stateDescription` every day cell carries
and the value every time control reports.

Provide it once near the root. A screen that sets it locally gets a calendar
announcing dates in one order and a field announcing them in another, which is
worse than either.

It is not a locale. Wire it from the platform's own settings where you have them
— guessing 24-hour from a language tag is how a user who prefers one clock gets
the other.
