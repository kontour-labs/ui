# `CalendarMonth`

The reusable grid, with no opinion about how selection works.

<!--sample:CalendarMonthBasics-->
```kotlin
var selected by remember { mutableStateOf(LocalDate(2026, 6, 18)) }

// The grid on its own, with no header and no paging — for a screen that
// shows three months at once, or supplies its own navigation.
CalendarMonth(
    month = LocalDate(2026, 6, 1),
    isSelected = { it == selected },
    onSelectedChange = { selected = it },
    today = LocalDate(2026, 6, 12),
)
```

**It expresses selection as *predicates* rather than a value**, because that is
the only shape that serves single, range and multi-select without the grid
knowing which mode it is in.

Range endpoints get a rounded cap and the interior stays square, so a run reads
as continuous rather than as a row of separate pills.

**The grid grows with the room it is given.** A cell is a seventh of the width,
and past about 44dp the day numbers grow in proportion to it — with the row of
weekday initials above them growing at the same rate, so the header keeps looking
like a header for the numbers under it. Both stop at the same point, which is
also where the grid stops widening: past it the digits are as large as they will
get and more width only buys a larger empty circle around the same number.

**Dragging a range extends one band.** Only the end the finger is moving
animates, sweeping along the track out of the edge the range is arriving from;
everything behind it is drawn. A cell in the middle of a run is not an edge that
is moving.

> A day cell used to announce its selection from a decorative sibling, so a
> screen reader could read a date as selected when it was not. Found by the
> contract suite.

---

## Accessibility

Each day is a `selectable` node with `Role.Button` and a full-date
`stateDescription`, set on the node that receives the press rather than on the
decorative fill behind it.

The grid on its own has **no month header**, which is the point of the component
— and it means a screen has to supply one. Without it a reader arrives at "18"
with no way to tell which month they are in.

`onDragSelect` is a gesture with no keyboard equivalent, so a range that can only
be chosen by dragging cannot be chosen at all by some users. Provide the two
end dates as taps as well, which is what
[`DateRangePicker`](date-range-picker.md) does.
