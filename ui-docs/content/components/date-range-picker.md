# `DateRangePicker`

Start and end, with a continuous run between them.

<!--sample:DateRangePickerBasics-->
```kotlin
var start by remember { mutableStateOf<LocalDate?>(null) }
var end by remember { mutableStateOf<LocalDate?>(null) }

// `end` arrives null on the first tap and filled on the second, so the
// caller can show a half-picked range rather than waiting for both.
DateRangePicker(
    start = start,
    end = end,
    onRangeSelected = { from, to -> start = from; end = to },
    today = LocalDate(2026, 6, 12),
)
```

**It follows the rule users expect without being told**: the first tap sets the
start and clears any end, the second sets the end, and tapping before the
current start *restarts* the range there rather than producing a backwards one.

**A range can also be dragged out in one gesture**, in either direction —
dragging from the 20th back to the 16th selects the 16th to the 20th. The band
extends as one strip behind the finger and only its moving end animates; it is
drawn the whole way rather than appearing when the finger lifts.

**The band follows the handle, and always touches it.** Across a row it runs to
the finger, which is always under the leaning handle, so the day being left fills
right up behind it. Down the page it does not follow the finger at all: a thumb
sagging a few pixels below a day's middle is not a decision, and flooding the rest
of the week with colour for it made small, unintentional movements look like big
ones. When the handle snaps to another week, the band *flows* there — the rest of
this week and the start of the next filling together, up to the new day — and
backing up a week empties them out the same way. Let go between two days and it
settles on the range that was chosen.**The drag has detents.** Days are places the selection rests, so each one crossed
is a tick in the hand, and the moving end of the band *leans* toward the finger
between days rather than sitting exactly on a boundary — the same fraction of the
overshoot a range slider's thumb follows a finger by, which is near enough to read
as a pull and far enough from the next day never to be mistaken for it. The band
behind it fills instantly rather than fading in, so what is coloured is what the
finger has passed: a hundred milliseconds per cell reads as the band catching up
rather than as the finger drawing it.

Dragging is never the *only* way to reach a range. Both ends can be tapped, which
is what makes the gesture safe to offer: see the accessibility note below.

**A sideways scroll pages the month**, and the header's calendar glyph brings it
back to today and flashes the day, as on
[`DatePicker`](date-picker.md#the-header-is-the-way-out-of-paging).

**The month header opens two wheels**, as it does on
[`DatePicker`](date-picker.md#the-header-is-the-way-out-of-paging) and for the
same reason — a range six months out is six taps away otherwise, in each
direction.

## Dragging across months

**A drag carries on across a month border.** Page with the header's arrows using
another finger while the first is still dragging, and the drag goes on in the new
month from wherever the finger is — the range still anchored where it started, in
the month now out of sight.

**Or page without a second finger.** As the finger nears the month's first or
last day, a small arrow fades in inside that day, on the side the other month is,
in a little ring of its own — more of it the nearer the finger, from any
direction, so coming at the 1st from the row below shows it as much as coming
along the row. Push the handle *past* the day — before the 1st, or after the last —
and hold it there: the arrow's ring fills, with a faint rumble in the hand while
it does, and when it is full the month pages with a tick and the drag carries on.
Only a handle that went over the arrow on its way past the day counts — arriving
in the blanks from above or below is not asking for another month, and resting on
the arrow without going past is choosing that day. Once the ring is filling it
keeps going however much further past the finger goes, and the arrow stays shown
while it does. The arrow is inside the day's own box, so a month that
starts on the first day of the week, or ends on the last, has it the same as any
other: off the edge of the grid counts as past the day.

**One dwell, one page.** Two months can start on the same weekday — February and
March often do — and then the day is in the same place in both. Having paged, the
arrow is spent until the handle leaves it: move off and back again to page again.
Let go of it before the ring fills and nothing happens.

The arrow is for a finger already busy. It is not a button, carries no semantics
and cannot be focused; the header's arrows are the way to page for everything
else.

A multi-month scrolling calendar, showing several months at once, is
[not yet built](../components.md#not-yet-built).

---

## Accessibility

Everything on [`DatePicker`](date-picker.md) applies to the grid.

The range is the addition, and it is announced through each day's own state
rather than as a separate summary — so a user reviewing the selection hears it by
moving across the days. Where the range matters as a whole, put it in the prose
beside the picker: "18 to 22 June" as `Text` is more use than a fourteenth
announcement inside the grid.

`onRangeSelected` fires with a null end on the first tap. Show the half-picked
range rather than waiting for both, or a screen reader user gets no confirmation
that the first tap did anything.
