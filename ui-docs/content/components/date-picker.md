# `DatePicker`

Single date, with month paging.

<!--sample:DatePickerBasics-->
```kotlin
var travelDate by remember { mutableStateOf<LocalDate?>(null) }

DatePicker(
    value = travelDate,
    onValueChange = { travelDate = it },
    today = LocalDate(2026, 6, 12),
    // Timetables do not go back, so neither does the picker.
    isDateSelectable = { it >= LocalDate(2026, 6, 12) },
)
```

**It has a width it stops at.** `fillMaxWidth` means "as wide as the container",
and a desktop window is a container — so a picker given one used to fill it, at
which point it is not more legible, only larger. It caps itself at
`CalendarMonthDefaults.MaxWidth`, a phone's 308dp, with weeks a little shorter
than its days are wide, and centres nothing: put it where you want it, and give
it less width if you want it narrower. In a `Column`,
`Modifier.align(Alignment.CenterHorizontally)` puts it in the middle; anywhere
else, `Modifier.fillMaxWidth().wrapContentWidth()` does.

### The header is the way out of paging

**The arrows either side are there by default**: the library's own chevrons,
back and on in the reading direction, so a right-to-left layout swaps their
ends and they still point the way they go. They used to be the caller's to
pass, and a picker given none had no buttons at all — it paged by a swipe
nobody could see, and with a mouse not at all; reported, of one in a dialog,
as *"there's no left/right buttons"*. `previousIcon` and `nextIcon` take an
app's own glyphs to match its set, and null leaves a button out.

**The month and year sit in the middle of the calendar**, over the middle of
the grid, not in the middle of whatever the buttons either side leave — the end
has the today button as well as the next month's, so the two sides are not the
same width. Where a narrow screen has no room to centre it, it moves toward the
previous-month arrow just far enough to clear the buttons, and is never cut
short.

**Tapping the month and year opens two wheels** — the same drums
[`TimePicker`](time-picker.md) is made of — and turning one jumps the calendar.
Paging a month at a time is right for "next week" and hopeless for a birthday,
which is thirty-odd taps in one direction and no way to know when to stop.

```kotlin
DatePicker(
    value = birthday,
    onValueChange = { birthday = it },
    today = today,
    chooserIcon = Tabler.Outline.ChevronDown,
)
```

**`chooserIcon` is the switch**, not just the glyph. Pass one and the header
becomes a `Role.Button` with a focus ring and a press state; leave it out and the
header is the title it always was. Unlike the arrows it is off by default,
because a popover needs an `OverlayHost` and throws without one, and a calendar
turns up in tests, previews and pages that have none. An affordance you did not
ask for must not be why your picker will not render.

The wheels drive the calendar **live**, so the grid behind the popover is already
on the month the drum is showing. A wheel that only committed on dismiss would be
a form field, and this is a way of looking around.

**The year drum reaches 120 years either side of wherever the calendar started**,
frozen at that point rather than recomputed — a range that followed the visible
year would shift the list under the finger by exactly as far as the finger had
moved. It widens if you page outside it, so the drum always has a row for the
month on screen.

A jump is navigation, not selection. Landing on a month whose days are all
outside `isDateSelectable` shows a grid of disabled days, which says more than
refusing to go there would.

**A small calendar is the way back to today.** Whenever `today` is known, the
header has a button with a calendar glyph. Pressing it brings the calendar to
today's month if it is somewhere else and then **flashes today's date** — a colour
from the theme pulsing in and out of the day's circle, twice — so it always does
something, and it answers "where is today" on a month where the day is easy to
miss. The flash stays inside the circle, so on a range picker it does not spill
into the band when today is in the range. The library
draws that one glyph itself, so the button is there with nothing supplied;
`todayIcon` swaps in an app's own to match its icon set, and `todayIcon = null`
leaves it out. The same goes for [`DateRangePicker`](date-range-picker.md). A
[`CalendarMonth`](calendar-month.md) on its own has no header: the month it shows
is the caller's to change.

**A swipe pages the month, and everything else moves the same way.** The months
sit side by side on one strip. Drag the grid sideways and they follow the finger;
let go past a third of the way, or with a flick, and it goes on to the next month
at the finger's speed. Short of that it comes back. Right to left it runs the
other way. A drag that sets off up or down is the page's, so a picker in a
scrolling page still scrolls.

The arrows, the today button, a sideways scroll and a `jumpTo` from your code all
move that same strip. A new move takes over a running one at the speed it had, so
pressing an arrow three times is one motion across three months. A jump further
than a month does not travel through every month between: the month it goes to
comes in beside the one in view.

A swipe that catches the last one before it has come to rest counts from the
month that one was going to, so quick swipes go on month after month. Each month
a swipe commits to is felt as a snap **when the swipe lets go**, however quickly
the next one follows. Arrows have their own press feedback.

The month and year above the grid cross-fade with the months, each as visible as
its month is in view, so the title is already the new month when the months land.
The title's width moves between the two months' widths as they do, so the
chooser's arrow beside it travels with it instead of jumping once they land.

**A sideways scroll pages the month** — a trackpad's two-finger swipe, a mouse's
tilt wheel, or shift and the wheel — which is how everything else pages on a
desktop. One swipe is one month, however long its momentum runs; a vertical scroll
is left to the page around the calendar. Right to left, the months run the other
way and so does the scroll.

### It needs its whole month

**Every month is six weeks tall**, with blank weeks at the end of a shorter one.
Months are four, five or six weeks, and a picker as tall as each changed height by
a row when it paged — which, in a scrolling page, moved the page: reported from a
phone as a picker that "randomly scrolls up sometimes when changing month". A
[`CalendarMonth`](calendar-month.md) on its own is still as tall as its weeks.

So it has nowhere to put its sixth row in a window shorter than about 400dp — a
phone turned sideways is 360. **Put it somewhere that scrolls.**

---

## Accessibility

The month header is a **polite live region**, so paging announces the new month
rather than leaving the user to work out that thirty-one buttons changed
underneath them. The arrows carry "Previous month" and "Next month".

It is also a `Role.Button` labelled "Choose month and year", which is the
keyboard and screen-reader route to the wheels — and the reason the jump is not
an arrow-only affordance. The wheels inside are `WheelPicker`s, each in a box
named "Month" and "Year".

The grid itself is [`CalendarMonth`](calendar-month.md), and that is where each
day's semantics live: a `selectable` node with `Role.Button` and
`stateDescription = formats.dateFull(date)` — "Thursday 18 June 2026" — set on the
node that is actually pressed. It was previously on the decorative box that draws
the highlight, a sibling a screen reader reaches separately if at all, so the
thing a user landed on said "18, button" and nothing about which date that was.

`isDateSelectable` disables a day rather than hiding it, so the shape of the
month stays readable and an unavailable date is announced as unavailable.

At high contrast the previous, next and today buttons, and the month title when
it opens the chooser, gain the edge every ghost button takes at this tier, and the grid draws today's ring and the focus ring
thicker. The wheels take the tier as [`WheelPicker`](wheel-picker.md) does.

Under reduced motion a new month fades in where the old one was rather than
sliding a third of the calendar's width, and the chooser fades in place rather
than scaling up. A swipe leaves the months where they are, and a swipe past the
line fades the next one in, as the arrows do. The today button's flash pulses a colour in the day at
every setting, which is not movement.
