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

**A phone's calendar wherever it is.** A cell is a seventh of the width up to
44dp (`CalendarMonthDefaults.ReferenceCell`), the size the type scale is right
for, and the grid stops widening there, at 308dp: given a desktop window it is
the size it is on a phone, and its digits and weekday initials stay on the type
scale. A week is as tall as a day is wide, up to 40dp, so six of them stack no taller
than they need to; a chosen day is a circle as tall as its week.

**Dragging a range extends one band.** Only the end the finger is moving
travels, leaning toward the finger between days; everything behind it is drawn.
While the finger is down the grid draws the band itself, so it runs right up to
the leaning end and flows to another week when the end snaps there, rather than
changing a day at a time — see [`DateRangePicker`](date-range-picker.md). Right to left, the
columns and the band mirror with the layout.

A month drawn on its own drags within itself. Inside a `DateRangePicker` the drag
is held above the pager, so it carries on when the month changes.

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

At high contrast today's ring and the focus ring are drawn thicker, and during a
range drag the arrow rings at the month's edges are thicker too, and solid
rather than faint where one sits on the range's cap. A range's middle days are
marked by the accent tint and their label colour alone; they gain no edge at
this tier.

Under reduced motion a chosen day's fill grows in over a short tween without its
bounce, and colours fade as they do at every setting. A cap dragged across the
grid follows the finger at every setting. Changing the month is the host's job,
and in [`DatePicker`](date-picker.md) it becomes a fade.
