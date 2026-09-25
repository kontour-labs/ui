# `Table`

*Also on this page: `TableScope`, `TableState`, `TableSort`.*

<!--sample:TableBasics-->
```kotlin
var sort by remember { mutableStateOf<TableSort?>(null) }
val rows = remember(sort) {
    // The table says what the reader asked for; the caller does the sorting.
    val by = compareBy<Service> { if (sort?.column == "Route") it.route else it.time }
    services.sortedWith(if (sort?.direction == SortDirection.Descending) by.reversed() else by)
}

Table(
    items = rows,
    modifier = Modifier.height(240.dp),
    stickyColumns = 1,
    striped = true,
    sort = sort,
    onSortChange = { sort = it },
    onRowClick = { openService(it) },
) {
    column("Route", width = 72.dp) { +it.route }
    column("Destination", weight = 1f) { +it.destination }
    column("Departs", align = TableAlign.End) { +it.time }
}
```
`Table` is for rows of records you compare across columns: departures by route,
platform and time, or trips by date, distance and fare. Each item is a row, and
each `column` says what its cell shows for an item. **The header stays put while
the rows scroll under it**, lazily, so a table of thousands composes only the
rows on screen. A footer row, if any column has one, stays at the bottom.

## Sizing columns

Each column is sized one of three ways:

- **`width`** is fixed: a route number, a status icon.
- **`weight`** takes a share of the room left once the others are sized, the way
  a `Row`'s weights share it. The table fills its width exactly when it can. A
  weighted column never goes narrower than its title, or than `minWidth`.
- **Neither** fits the column to its title and to the cells of its first 50
  rows. The width is remembered and only ever grows, so a column doesn't jump as
  rows scroll in or the items are re-sorted. A column whose contents vary more
  than its first rows show should be given a `width`.

`numeric = true` sets a column in tabular figures, so its digits line up down
the rows, and against its end edge. `align` places any column's cells, and
`maxLines` lets a cell wrap before it is cut short.

## Scrolling across

When the columns are wider than the table, **they scroll across together**:
header, rows and footer as one. A drag anywhere moves every row, and a fling on
one is not stopped by another. The first `stickyColumns` stay pinned at the
start, so a row keeps its name however far across it is read, and a rule
appears at their edge once something has slid under it. Pinned columns never
cover more than half the table. On a screen too narrow for that, the last of
them scroll too.

`TableState` holds both scroll positions: down through its `listState`, and
across through `horizontalOffset` and `scrollHorizontallyTo`. `rememberTableState`
keeps them across recreation.

## Sorting is yours

A tap on a sortable header calls `onSortChange` with the sort it asks for: the
same column the other way round, or a new column ascending. The header shows an
arrow for `sort`. **The table sorts nothing itself.** Sort the items and pass
them back. That is where a sort belongs when the rows come from a query or
arrive a page at a time. `sortable = false` leaves one column's header alone,
and without `onSortChange` no header is sortable.

## Picking rows

`selection` says how rows are picked, and `selected` holds the picked rows'
keys:

- **`None`** does not pick rows, but rows in `selected` are still tinted, to
  mark a current one.
- **`Single`**: a tap picks the row.
- **`Multiple`** adds a checkbox column at the start, pinned when it fits. A tap
  on a row toggles it, and the header's checkbox shows none, some or all of the
  rows. From "some", the header checkbox picks the rest.

`onRowClick` opens a row in any mode. With `Multiple`, the row then opens and
its checkbox picks, and each is its own control.

## Rules, stripes and the outline

`lines` draws `Rows` (a hairline between rows, the default), `Grid` (between
columns too) or `None`. The header and footer are always ruled off. `striped`
puts every other row on a quiet ground for reading across a wide row. Stripes
and the selected tint run under the pinned columns and the scrolling ones
alike. `outlined` draws a border round the whole table, with a card's corners.

## Not a `KeyValueList`

A `KeyValueList` is one record's fields, a label beside each value. A table is
many records' fields, with the labels said once, in the header. If there is only
one row, use a `KeyValueList`. If each row is one thing to open with a line of
detail, use `ListItem`s.

---

## Accessibility

The table announces itself as a collection of its rows (header and footer
counted) and columns. **Each header is a heading**, so a screen reader can move
through the columns by their titles. A sorted header says "Sorted ascending" or
"Sorted descending", and a sortable one is a button.

**Each row is one node**: its cells read in column order, with its place in the
table. A row that opens is a button, one that is picked singly says whether it
is selected, and one with a checkbox is a checkbox. Where the cells mean little
without their headers, such as "4", "8:12", "$3.30", pass `rowAnnouncement` to
say the row in words: "Route 950 to Elizabeth Quay, departs 8:12".

The table scrolls across for a screen reader's scroll actions as well as for a
drag, and rows take keyboard focus in turn, with a focus ring inside the row so
the next row does not cover it. A disabled table keeps its rows' roles and says
they are disabled, and it still scrolls.
