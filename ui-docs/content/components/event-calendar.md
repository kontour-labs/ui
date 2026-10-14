# `EventCalendar`

*Also on this page: `EventCalendarState`, `rememberEventCalendarState`, `CalendarEvent`, `CalendarView`, `EventPresentation`, `CalendarDaysStyle`, `CalendarSwitcher`, `EventCalendarDefaults`.*

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

`EventCalendar` is a calendar for showing what is on: a day's, three days' or a
week's hours, a month of day boxes, a year of small months, or a schedule of
the days with something on. It was asked for as
*"a proper calendar component … not like a date picker, but more of a calendar
that has boxes for each day of the month, designed to show events"*.

[`CalendarMonth`](calendar-month.md) and [`DatePicker`](date-picker.md) are
for choosing a date. This is for reading a schedule.

## The events

An event is a `CalendarEvent`: an `id`, a `title`, when it starts and ends, a
`tone` to colour it, and optionally a `location`, a supporting line and an
`icon`.

**`icon` leads its title** — a plane for a flight, a person for a lunch — in each
of its shapes: before the title in a block or a bar, in the ink on it, and in a
line in place of its tone's dot, in the dot's colour. Where a day or a block is
too narrow for the icon and four letters of the title, the icon goes, as the dot
does. It is decorative: a screen reader hears the title. It is a field of the
event rather than a `+` in a slot, because an event is data the app keeps and
hands over, not a composable it writes.

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

`CalendarView` is `Day`, `ThreeDays`, `Week`, `Month`, `Year` or `Schedule`, held by the
`EventCalendarState` from `rememberEventCalendarState(initialDate,
initialView)`. Three days start on the day the view was switched to, and step
three at a time. The week view shows one week, or several side by side —
`state.showWeeks(2)` for a fortnight, up to six, or `initialWeekCount` — and
steps by as many.

`daysStyle` lays out the three-day and week views' days. The day view always has
its hours: a single day condensed is a list, and its hours say more about it.

**`showTimedEvents = false` shows only all-day events and those a day or
longer** — a plan of trips and leave rather than of meetings. An event that runs
overnight but less than a day is a timed one, and is left out. With nothing for
the hours, the day, three-day and week views stack their events as `Condensed`
does, the day view too.

| `CalendarDaysStyle` | What it draws |
|---|---|
| `Hours` | The hours, with the all-day events in a strip above them, three rows at most and a "+N" for the rest. The default. |
| `Condensed` | No hours: each day's events stacked down the days in a list that scrolls — all-day ones as bars across their days, timed ones as their title over their time — packed the way a month's week is but with no limit. A day's empty space opens it. A line runs down each day's start edge, so a week swiped in beside another has its line between them. With `showWeeks(2)`, a fortnight's plan at a glance. |

| View | What it draws |
|---|---|
| `Day`, `ThreeDays`, `Week` | The hours, an hour `hourHeight` tall, one scroll shared by the week so a time is level across it. All-day events and anything a day or longer sit in a strip above the hours. Overlapping events share a day's column side by side, and run on across the lanes beside them wherever those are free. One that starts below the title of an event it overlaps — about twenty minutes on, at the default height — is laid over it instead, a little in from its start edge and ringed in the surface's colour, as Google's calendar does, so a busy afternoon in a narrow week is still readable; where they overlap, a press is the later one's; every event is drawn as tall as its time, so an hour is the same height whatever is in it and a quarter-hour's edge is on the quarter: at the default 64dp an hour, a quarter-hour is 16dp, with its title on one line in the small type. A title wraps over as many lines as its event is tall, a long word broken where it must be, and a title, time or place cut short on its last line fades out. An event with no length is drawn a step tall. Each event is a box in its tone, edged as a banner is. A line marks the time now, on today. The hours sit in a rounded box with their labels beside it — `9 am` on a twelve-hour clock, the clock's own `09:00` on a twenty-four-hour one, each on one line, the column widening if a font loaded late sets them wider — and open scrolled to `initialScrollTime`, eight in the morning by default. |
| `Month` | In a rounded box, six weeks of day boxes, every row the same height so the weeks sit under each other, scrolling up and down through the weeks before and after — see **Moving about**. The days of the months either side of the one most on screen are quieter. Two fingers spread or pinched zoom it, between three weeks to the box and six — Ctrl, or Cmd, and the wheel a week at a time — keeping the week under them still, and settle on a whole number of weeks. Zoomed in, with rows tall enough, each event has more lines: a timed one its title, then its time, then where it is; a bar its title and where it is. All-day and several-day events are bars across their days; others are a line with their tone's dot, their time and their title, in a type a little smaller than the small label's. Where a day is too narrow for all three, the time goes first and then the dot, so the title keeps the room, and a title cut short fades out rather than ending in "…". A box holds as many as fit and a "+N" for the rest, as a row of its own the size of the events above it, which opens that day's list; an event in the list can be carried out of it onto any day of the month, and the list closes once it is put down. |
| `Schedule` | The days with something on, one under another — each day's weekday and number, then its events, all-day and longer ones first, each with its tone's dot or its icon, its title, and "All day" or its times and where (a timed event over several days is its part of each: from its start, all day, until its end) — under each month's name, scrolled through. Today is always there, saying "Nothing planned" when it has nothing on. The day at the top is the one the title and the state are about; the arrows go to the start of the month before or after, and a day's number opens it. `visibleDates` is the month before the one in hand and the two after it. |
| `Year` | Small months, as many to a row as fit, with each busy day marked in the tone of its first event, scrolling up and down through the years — each year's months under its number — as the month scrolls through its weeks. The year most on screen is the one the title and the state are about; the header's arrows go to the same month a year before or after. Each month is drawn whole, so every month on screen is there at once. |

An event that runs on past what a view shows — a bar into the day or the week before or after, or off the page, a block in the hours past midnight — is square on that side, and runs right to the edge there, through the line between the days or the weeks, so its pieces meet and read as one event. Where it really ends it is rounded and stops short of the line, so a rounded end always means the event ends there.

Today's date is ringed the way the date picker rings it, in every view, so a
calendar and a picker on the same screen agree about what today looks like.

**Moving about.** The header steps back and on, goes to today and switches
the view. In the day and week views a sideways swipe steps too, the same way
the date picker pages its months. The month scrolls up and down instead,
through the weeks either side. A finger's swipe or fling settles with a week at
the top; a mouse's wheel or a trackpad scrolls the weeks freely and leaves them
where it stops, as a desktop's calendars do. The month
most on screen is the one the title and the state are about, and the header's
arrows scroll to the first week of the month before or after. A day's number, in the month and over a week's columns, opens that
day. In the year, a month's name opens the month and a day opens the day. An
app can do the same with `state.show(date, view)`, `state.step(n)` and
`state.showToday()`.

**Weeks start on the reader's first day of the week**, from their locale,
unless `rememberEventCalendarState` is given another.

## What it reports

| Callback | When |
|---|---|
| `onEventClick` | An event pressed. Null and events are not buttons. |
| `onDateClick` | A day pressed in the month — anywhere in its box, its number included, away from its events — or Enter on it, *instead of* opening its day view: to start a new event there, say, or to open the day yourself as well. Without it, a pressed day opens its day view, wherever on it the press was. |
| `onTimeClick` | An empty time pressed in the day or week view, to the quarter-hour before it. |

## Editing

Give the calendar the callbacks for the edits you take and it offers them; it
still changes nothing itself. It shows each edit while it is in hand, and when
the edit is put down it reports where the event would go. Change your `events`
to keep the result, or leave them as they are to refuse it.

| Callback | The edit |
|---|---|
| `onCreateRequest(start, end)` | A new event. Drawn out on empty time in the day or week view — dragged with a mouse, or held and dragged with a finger — from the step the pointer took hold of to where it was let go, at least one step long. Or chosen as **New event** from the menu a finger held still opens, or a right-click: an hour where it was held in the day or week view, or the whole day held in the month, midnight to midnight. |
| `onEventMove(event, start, end)` | An event carried somewhere else, keeping its length. In the hours it moves by time, and to another day by moving across; near the top or bottom of the hours they scroll. It stays on the day it is over: carried to the top or the foot of the hours it stops at midnight rather than spilling into the day before or after, and so do the arrow keys and a screen reader's move earlier and later. An event that already runs past midnight keeps its start on the day. A bar, or a line in a month, moves by whole days and keeps its time. In the day and week views, held at either side of the days, a carry pages on to the day or week there and goes on, so an event can be carried into next week. In a month, carried near the top or the bottom, the weeks scroll with it, as the hours do, so an event can be carried into next month. In the day and week views, as it comes within about 72dp of a side, the date range picker's arrow shows against that side, level with a mouse's pointer or just above a finger, and following it, the same size in every view; its ring fills over the hold that pages on, felt as the picker's is, a rumble that builds and then the page as one threshold. |
| `onEventResize(event, start, end)` | An event's start or end dragged by its edge, never shorter than one step. In the hours an edge keeps to its own day: an end stops at midnight and a start at the day's first minute, so a resize never runs an event on into the next day. An event that crosses midnight has its start on its first day's piece and its end on its last. A bar, all day or a day or longer, has its ends at its sides, and they move a whole day at a time, never shorter than its first day. An end cut off by the edge of the week or the month has no handle there. With a mouse the edges are always there, with the resize cursor. On a touchscreen, choose **Adjust time** from the event's menu to give its edges handles, drawn across its edges; a press anywhere else takes them away. A handle is taken from up to 20dp inside the event or 16dp outside it, not only from the grip, and keeps its distance from the finger rather than jumping to it. On an event too short for two such zones — under 40dp — a press anywhere on or beside it takes the edge the finger sets off toward: up for the start, down for the end, as a range slider's thumbs on top of each other are told apart. From the moment an event in the hours is held, its menu open, and for as long as it is carried or its handles are out, it is widened to its whole day, over any it shares its time with, and every other event fades back, the same in the day and week views; it eases out and back over about a fifth of a second, and the copy a carry draws opens out with it. |
| `onEventDelete(event)` | **Delete**, from the event's menu, the Delete key on a focused event, or a screen reader's action. Take it out of `events` to delete it. |
| `eventMenu` | What a finger held on an event, or a right-click on it, offers. By default **Adjust time** and **Delete**, each where it applies. Null for no menu. See below. |
| `emptyMenu` | What a finger held on empty time, or a right-click there, offers, told the time it would be for. By default **New event**. Null for no menu. |

Every edit moves in steps of `snap`, a quarter-hour by default, and each step
is felt as a tick. A step must be a whole number of minutes that divides a day.
On a touchscreen an edit starts with a long press, so a quick swipe still
scrolls the hours or pages the period. A held finger can wander a little
without becoming a swipe, and a scroll that stops with the finger still down
does not turn into a hold. With a mouse an edit starts as soon as the pointer
moves, so swiping a mouse across an event moves the event instead of paging.

**A held finger opens a menu.** On empty time it offers **New event**, and on
an event **Adjust time** (on a block or a bar, not a month's line) and **Delete**, whichever
the callbacks you gave allow. Let go without moving and the menu stays to be
chosen from; move instead and the menu closes, and the hold carries on as the
drag it always was — a new event drawn out, or the event carried. A right-click
opens the same menus for a mouse. A finger held just beside one of a month's
events, which are too short to land on reliably, takes that event.

**The menus are yours to build.** `eventMenu` and `emptyMenu` are built as any
[menu](../dsls.md) is, with `item`, `divider` and `section`, and the calendar's
own actions are calls in them — `adjustTime()` and `delete()` for an event,
`newEvent()` for empty time — each drawn only where it applies, so a menu can
keep them, drop them, or put its own beside them:

```kotlin
EventCalendar(
    state = calendar,
    events = events,
    onEventDelete = { remove(it) },
    eventMenu = { event ->
        item("Share", leadingIcon = Tabler.Outline.Share) { share(event) }
        divider()
        adjustTime()
        delete()
    },
    emptyMenu = { start, end ->
        item("Block out") { blockOut(start, end) }
    },
)
```

A menu of your own opens on any event, one that cannot be moved included, and
on any empty time; the default opens only where one of its actions applies.
Null turns a menu off: a hold on an event then only takes it up.

While anything is carried, a lifted copy shows where it would land, and moves
as a date range's end does: it sits on its step, leaning a little toward the
pointer in the same frame the pointer moves, and as the pointer crosses into
the next step, day or week it hops there — a tenth of a second, with no
overshoot, from wherever it was. It never trails the pointer within a step,
and never goes back the way it came.

An event the app only shows, such as a holiday from a subscribed calendar,
says so with `editable = false` and can't be moved or resized, though it can
still be pressed.

## Your own pieces

`header` replaces the default `EventCalendarDefaults.Header`, or is null for
none — an app with its own top bar drives the state from there instead.
**Too narrow for every view side by side — six on a phone — the header offers
them in a menu by itself**, rather than stack segments in a column. A menu opens
in an `OverlayHost`; a calendar without one keeps its segments.
`EventCalendarDefaults.Header(state, switcher = CalendarSwitcher.Dropdown)` offers
the views as a button naming the one on show, which opens a menu of them with that
one ticked — narrow enough to sit beside the arrows on a phone — in place of the
segmented control.

**`views` on the calendar says which views it has.** Leave a view out and the
switcher does not offer it, and nothing else reaches it: a day opened in a
calendar without its day view — its number pressed, a day's heading, a "+N" —
opens the nearest view there is, three days or the week, and
`state.show(CalendarView.Day)` does the same. A state in a view the calendar does
not have is moved to the nearest one it does before it is drawn.
`Header(state, views = …)` limits the switcher further, to some of those.

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
quarter-hour at the default hour height is 16dp, and growing it would cover
the next event — so a screen reader reaches each event as a node and a
keyboard by Tab from its day, rather than relying on the box's size.

An event's actions include **Delete** when the calendar has `onEventDelete`,
and the Delete key on a focused event does the same.

A month's box is read as its date and how many events it has. Its "+N" is
read as "3 more events". In the year, each month is one stop, read as its
month and year and how many of its days are busy, and opened as a button; its
days are reached in the month it opens. The header's title is read out
whenever the period changes, whichever way it changed. The weekday names over the columns are
hidden from screen readers, since each day already says its own date. Dates,
times, weekday and month names all come from `Theme.formats`, in the reader's
language, and every word comes from the theme's strings.

At high contrast the grid's lines take the stronger outline, an event's tinted
box takes the tier's edge so it reads as a box rather than a wash, and muted
text is drawn at full ink.

**The grid is one keyboard stop, with a cursor in it.** In a month the
cursor is a day: the left and right arrows move it a day (mirrored for
right-to-left reading), up and down a week, Home and End to the week's first
and last day, and Page Up and Page Down a month. Enter or Space opens the day —
`onDateClick`, or the day's view where there is none. Ctrl, or Cmd, with plus
or minus zooms the month a week at a time, about the cursor's day, and a
screen reader has the same as the grid's Zoom in and Zoom out actions. In a day or a week the
cursor is a slot of time `snap` long: up and down move it a step, Page Up and
Page Down an hour, Home and End to the day's first and last slot, and left
and right a day. Enter or Space presses the time — `onTimeClick`, or, where
there is none, `onCreateRequest` for an hour from it. The cursor's day is the
state's `date`, so moving it off either end of the period pages, and the
header's title follows; the hours scroll to keep the slot in view. Each move
is read out to a screen reader.

Tab from the grid goes on to the cursor's day's events, and no others, then
out of the calendar: a month is otherwise every date and every event in it
before the next thing on the page. The day boxes and their numbers are still
pressed by a pointer and reached by a screen reader, but are not stops of
their own; the "+N" of the cursor's day is.

**Every edit can be made without a pointer.** A focused event is picked up
with Space. The up and down arrows then move it a step at a time, left and
right move it a day (mirrored for right-to-left reading), Shift with up or down
changes its end within its day, Enter or Space puts it down, and Escape puts it
back. A month's line or bar, and a bar in the all-day strip, is carried by whole
days, as a pointer carries it: in a month up and down move it a week and keep
its time, and in the strip they do nothing. On a bar, Shift with any arrow changes its end by a day. A screen
reader is offered the same edits as actions on the event: move earlier, move
later, move to the day before or after, make longer and make shorter (by a day,
on a bar), each for
the edits the app takes. After each edit, a screen reader is told where the
event now is.

While an event is carried, the copy left behind is faded and the place it
would land is drawn as a lifted copy in its own tone with a stronger edge. In
a month and in the all-day strip, that copy is a bar the event's length whose
ends spring from day to day as the carry passes each one, a tick for each
day, as a date range's end does. A carry changes day only once the pointer is
well into the next one, so a finger resting on the line between two days
does not flick the event back and forth.

Under reduced motion the swipe and the header's arrows change the period in
place, without sliding, a month's arrows jump to the month rather than
scrolling there, and a month let go after a pinch settles on its week at once.
