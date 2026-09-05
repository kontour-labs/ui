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

Dragging is never the *only* way to reach a range. Both ends can be tapped, which
is what makes the gesture safe to offer: see the accessibility note below.

A multi-month scrolling calendar, for ranges that cross a month boundary
comfortably, is [not yet built](../components.md#not-yet-built).

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
