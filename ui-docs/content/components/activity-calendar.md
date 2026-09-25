# `ActivityCalendar`

*Also on this page: `ActivityLevels`, `ActivityCalendarColours`.*

<!--sample:ActivityCalendarBasics-->
```kotlin
var picked by remember { mutableStateOf<LocalDate?>(null) }

// A year to today, a shade a day. Point at a day, long-press it or reach it
// with the arrow keys to see its count; tap it to pick it.
ActivityCalendar(
    activity = tripsByDay,
    end = LocalDate(2026, 6, 5),
    today = LocalDate(2026, 6, 5),
    selected = picked,
    onDayClick = { picked = it },
)
```
`ActivityCalendar` shows a year of something at a glance, like GitHub's
contribution graph. Each cell is a day, each column a week, and each cell is
shaded by how much happened that day. Month names run along the top, Mon, Wed
and Fri down the side, and a legend from "Less" to "More" sits underneath.
Turn all three off for a quiet strip of cells on a profile:

```kotlin
ActivityCalendar(
    activity = days,
    end = today,
    monthLabels = false,
    weekdayLabels = false,
    legend = false,
    colours = ActivityCalendarDefaults.colours(full = Theme.colours.info.solid),
)
```

## The range

The calendar is `weeks` columns, 53 by default, so it holds every day of the
last year in whole weeks. **The last column holds `end` and stops at it**, so it
is as long as the week has been so far. `start` leaves out the days before it,
for an account a few months old. Each column starts on `firstDayOfWeek`, which
comes from the reader's locale through `formats`.

The library never reads the clock, so `end` and `today` are yours to pass. Most
screens pass the same date for both. `today` gets an inner ring.

## Shades

`levels` decides how a day's count becomes a shade:

- **`ActivityLevels.Quantiles`**, the default, is GitHub's rule. The days with
  any activity are split into equal-sized groups, one per shade. A busy year and
  a quiet one both use every shade, and the shades say which days were busier
  than the others, not by how much.
- **`ActivityLevels.Thresholds(listOf(1, 3, 6, 9))`** starts each shade at a
  fixed count. Use it for a count that means something on its own: ten thousand
  steps is a good day whatever the rest of the year was like.

`ActivityCalendarDefaults.colours()` blends four shades from `empty` to `full`.
The first shade starts a little way along, so a day with one of something is
plainly not a day with none. Pass `full` for a colour of your own, such as
GitHub's green, and `levels` for more or fewer shades. For shades that don't
blend, build `ActivityCalendarColours` with a list of your own, where the first
colour is "nothing".

## Size

Cells fit the width available, between 8dp and 16dp. **A width that would need
smaller cells scrolls sideways instead**, and it opens on the most recent week,
which is the one anybody looks at first. `cellSize` fixes the size, and
`cellGap` and `cellShape` set the spacing and the corners. The default shape is
a small squircle, the library's own corner. Pass `Theme.shapes.pill` for dots.

## Picking and reading a day

With `onDayClick`, tapping a day picks it, and `selected` draws a ring round the
picked day. Every day can show its count in a tooltip, however it is reached:

| Input | Shows the count | Picks |
|---|---|---|
| Mouse | Resting on a cell, then from cell to cell as it moves | A click |
| Touch | A long press | A tap |
| Keyboard | The cursor's day | Enter or Space |

On the keyboard the calendar is one stop in the Tab order. Up and Down move a
day, Left and Right a week (mirrored right to left), and Home and End go to the
first and last day. The cursor scrolls itself into view. `describe` says what a
day's count means, in its tooltip and to a screen reader. By default it is
"3 activities on Friday, 5 June 2026", from the theme's strings.

## Not a `CalendarMonth`

`CalendarMonth` is a month for choosing a date: big targets, one month at a
time, dates you move between. This calendar is a year for reading: small cells,
every day at once, and a pattern you see before you look at any single day.
Picking a day here is for showing that day's detail somewhere else.

---

## Accessibility

**A screen reader moves through the calendar a week at a time.** Each week is
one node, named by the day it starts ("Week of 1 Jun 2026"), followed by what
happened on each day that had anything. A quiet week says so once. The weeks are
read in the order they happened, whichever side the grid starts on. A week
holding the picked day announces as selected, and when the calendar has
`onDayClick`, each week carries an action per day to pick it. A node per day
would be 371 stops to swipe through a year.

The shades are never the only way to read a count. The tooltip and the week
nodes both say it in words, so a reader who can't tell the shades apart still
gets every number. The legend is hidden from screen readers for the same
reason: it explains the colours, and the words don't need it.

The month and weekday names come from `DateTimeFormats` and are English, like
the rest of its names. "Less", "More", the day's count and "Week of" come from
the theme's strings and can be translated.
