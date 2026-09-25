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

**The band flows ahead of the finger, and always touches the handle.** Between
two days it is part way to the next one: leaning across, it fills the day the
handle is leaving, right up under it; leaning down toward the next week, it
starts to fill the rest of this week and the start of the next, up to the day
the finger is heading for — both at once, and more of each the nearer the finger
gets. Backing up empties them out the same way. So the accent is always against
the start or the top of the handle being dragged, and it never jumps a week at a
time. Let go between two days and it settles on the range that was chosen.

**The drag has detents.** Days are places the selection rests, so each one crossed
is a tick in the hand, and the moving end of the band *leans* toward the finger
between days rather than sitting exactly on a boundary — the same fraction of the
overshoot a range slider's thumb follows a finger by, which is near enough to read
as a pull and far enough from the next day never to be mistaken for it. The band
behind it fills instantly rather than fading in, so what is coloured is what the
finger has passed: a hundred milliseconds per cell reads as the band catching up
rather than as the finger drawing it.

Dragging is never the *only* way to reach a range. Both ends can be tapped, which
is what makes the gesture safe to offer: see the accessibility note below.

**The month header opens two wheels**, as it does on
[`DatePicker`](date-picker.md#the-header-is-the-way-out-of-paging) and for the
same reason — a range six months out is six taps away otherwise, in each
direction.

## Dragging across months

**A drag carries on across a month border.** Page with the header's arrows using
another finger while the first is still dragging, and the drag goes on in the new
month from wherever the finger is — the range still anchored where it started, in
the month now out of sight.

**Or page without a second finger.** As the handle nears the first or last days of
the month, a small arrow appears beside the edge day, pointing at the month before
or after. Hold the handle on it and a ring fills round it; when the ring is full
the month pages, with a tick in the hand, and the drag carries on. The arrow sits
in the blank beside the 1st or the last day. A month that starts on the first day
of the week, or ends on the last, has no blank there, so its arrow hangs just past
the edge of the grid, level with that day — push the handle past the edge in that
row to reach it.

**One dwell, one page.** Two months can start on the same weekday — February and
March often do — and then the arrow is in the same place in both. Having paged,
the arrow is spent until the handle leaves it: move off and back on to page again.
Leave before the ring fills and nothing happens.

The arrows are for a finger already busy. They are not buttons, carry no
semantics and cannot be focused; the header's arrows are the way to page for
everything else.

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
