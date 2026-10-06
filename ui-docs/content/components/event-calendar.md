# `EventCalendar`

*Also on this page: `EventCalendarState`, `rememberEventCalendarState`, `CalendarEvent`, `CalendarView`, `EventPresentation`, `EventCalendarDefaults`.*

<!--sample:EventCalendarBasics-->
```kotlin
// The app keeps the events; the calendar shows them and says what was pressed.
val calendar = rememberEventCalendarState()
val events = listOf(
    CalendarEvent(id = 1, title = "Stand-up", start = LocalDateTime(2026, 10, 14, 9, 0), end = LocalDateTime(2026, 10, 14, 9, 15)),
    CalendarEvent(id = 2, title = "Perth trip", startDate = LocalDate(2026, 10, 13), endDate = LocalDate(2026, 10, 16), tone = Tone.Warning),
)
EventCalendar(
    state = calendar,
    events = events,
    onEventClick = { open(it.title) },
    modifier = Modifier.fillMaxSize(),
)
```

`EventCalendar` is a calendar for showing what is on: a day's or a week's
hours, a month of day boxes, or a year of small months. It was asked for as
*"a proper calendar component … not like a date picker, but more of a calendar
that has boxes for each day of the month, designed to show events"*.

[`CalendarMonth`](calendar-month.md) and [`DatePicker`](date-picker.md) are
for choosing a date. This is for reading a schedule.

## The events

An event is a `CalendarEvent`: an `id`, a `title`, when it starts and ends, a
`tone` to colour it, and optionally a `location` and a supporting line.

- **Times are wall-clock times** (`LocalDateTime`): the time on the reader's
  clock, not an instant. The day and week views always draw 24 hours a day,
  whatever the clocks did that night, and a meeting at ten is drawn at ten. If
  your app keeps instants, convert each one once with
  `instant.toLocalDateTime(TimeZone.currentSystemDefault())` as you hand it
  over.
- **An event runs up to its end, not including it.** One ending at ten and one
  starting at ten don't overlap. The all-day constructor takes the days as
  people say them — `CalendarEvent(id, "Perth trip", startDate = 13 Oct,
  endDate = 16 Oct)` covers the 13th to the 16th.
- **An event ending before it starts is refused** with a message naming the
  field, rather than drawn somewhere odd.

**The app keeps the events.** The calendar never changes `events`; it draws
them and reports what was pressed. Either load what
`state.visibleDates` covers as the reader moves about, or hand over everything.
A few hundred events are no trouble, and the ones the current view doesn't
show cost nothing.

## The views

`CalendarView` is `Day`, `Week`, `Month` or `Year`, held by the
`EventCalendarState` from `rememberEventCalendarState(initialDate,
initialView)`.

| View | What it draws |
|---|---|
| `Day`, `Week` | The hours, an hour `hourHeight` tall, one scroll shared by the week so a time is level across it. All-day events and anything a day or longer sit in a strip above the hours. Overlapping events share a day's column side by side, and the shortest event is still drawn tall enough for its title, nudging the next one aside rather than overlapping it. A line marks the time now, on today. The hours open scrolled to `initialScrollTime`, eight in the morning by default. |
| `Month` | A box for every day of every week the month touches, including the days from the months either side, which are quieter. All-day and several-day events are bars across their days; others are a line with their time. A box holds as many as fit and a "+N" for the rest, which opens that day's list. |
| `Year` | Twelve small months, as many to a row as fit, with each busy day marked in the tone of its first event. |

**Moving about.** The header steps back and on, goes to today and switches
the view. A sideways swipe steps too, the same way the date picker pages its
months. A day's number, in the month and over a week's columns, opens that
day. In the year, a month's name opens the month and a day opens the day. An
app can do the same with `state.show(date, view)`, `state.step(n)` and
`state.showToday()`.

**Weeks start on the reader's first day of the week**, from their locale,
unless `rememberEventCalendarState` is given another.

## What it reports

| Callback | When |
|---|---|
| `onEventClick` | An event pressed. Null and events are not buttons. |
| `onDateClick` | A day's box pressed in the month, away from its number and its events — to start a new event there, say. |
| `onTimeClick` | An empty time pressed in the day or week view, to the quarter-hour before it. |

## Your own pieces

`header` replaces the default `EventCalendarDefaults.Header`, or is null for
none — an app with its own top bar drives the state from there instead.
`EventCalendarDefaults.Header(state, views = …)` can also limit the switcher to
the views you offer.

`eventContent` draws the inside of an event. It is told the event and its
`EventPresentation`: a `Block` in a day's hours, a `Bar` across days, or a
`Line` in a month's box. The calendar keeps the event's box itself — its place,
its colour, its press and what a screen reader says — so a custom inside can't
break any of them.

`now` is the time on the reader's clock, which marks today and draws the line
across the hours. By default it is the system's time, updated each minute.
Pass a fixed time for a screenshot, or null for a calendar that isn't about
the present.

---

## Accessibility

**Every event is its own node**, read as its title, its day, its time (or "All
day") and its place, as a list in the reader's own way of writing one. Short
events are drawn smaller than the 48dp target a control usually gets — a
quarter-hour at the default hour height is 12dp, and growing it would cover
the next event — so a screen reader reaches each event as a node and a
keyboard by Tab, rather than relying on the box's size.

A month's box is read as its date and how many events it has. Its "+N" is
read as "3 more events". The header's title is read out whenever the period
changes, whichever way it changed. The weekday names over the columns are
hidden from screen readers, since each day already says its own date. Dates,
times, weekday and month names all come from `Theme.formats`, in the reader's
language, and every word comes from the theme's strings.

At high contrast the grid's lines take the stronger outline, an event's tinted
box takes the tier's edge so it reads as a box rather than a wash, and muted
text is drawn at full ink.

Under reduced motion the swipe and the header's arrows change the period in
place, without sliding.
