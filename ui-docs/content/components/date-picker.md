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

**It has a width it stops at.** `fillMaxWidth` means "as wide as the container",
and a desktop window is a container — so a picker given one used to fill it, at
which point it is not more legible, only larger. It caps itself at
`CalendarMonthDefaults.MaxWidth` and centres nothing: put it where you want it,
and give it less width if you want it narrower.

### It needs its whole month

A month grid is up to six rows of dates plus a header, and it has nowhere to put
the sixth row in a window shorter than about 400dp — a phone turned sideways is
360. **Put it somewhere that scrolls.**

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
