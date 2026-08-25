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

> A day cell used to announce its selection from a decorative sibling, so a
> screen reader could read a date as selected when it was not. Found by the
> contract suite.

---

← [Date and time](date-time.md) · [All components](../components.md)
