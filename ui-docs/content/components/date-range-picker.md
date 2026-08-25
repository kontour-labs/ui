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

A multi-month scrolling calendar, for ranges that cross a month boundary
comfortably, is [not yet built](../components.md#not-yet-built).

---

← [Date and time](date-time.md) · [All components](../components.md)
