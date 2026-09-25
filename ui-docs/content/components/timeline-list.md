# `TimelineList`

*Also on this page: `TimelineListScope`, `timelineList`.*

<!--sample:TimelineListBasics-->
```kotlin
// One and a half stops along: the first leg is travelled, the walk is half
// done, and the rows are list rows — each one opens its stop.
TimelineList(progress = 1.5f) {
    item(onClick = { openStop("Perth Station") }) {
        +"Perth Station"
        supporting { +"Platform 3" }
        trailing { +"08:12" }
    }
    // The connector is the leg after the stop: this one is the walk.
    item("Walk 4 min", connector = ConnectorStyle.Dashed, filled = false)
    item("Elizabeth Quay", supporting = "Stand C", trailing = "08:21") {
        openStop("Elizabeth Quay")
    }
}
```
`TimelineList` is a stop list: `Timeline`'s rail beside list rows. Use it for a
trip's stops, a delivery's scans, or a day's appointments, where each step is
something to open and has a value at the end of its line. `Timeline` takes any
content beside its rail and has no rows. The rows here are `ListItem`s, so they
press, select and read like every other list in the library. They take the same
slots: a bare `+` for the label, then `supporting`, `overline`, `leading` and
`trailing` by name.

**`style` chooses the rows.** `Plain`, the default, draws clear rows on the page
with the rail running beside them. `Grouped` draws sunken rows grouped as one
object, the way `ListGroup` does, with the rail running through the rows and
across the hairline seams between them. Grouped corners are worked out from
where each row sits, so there is nothing to pass.

## The rail

**A stop's `connector` is the leg after it**, as a `TimelineItem`'s is: `Solid`
for a ride, `Dashed` for a walk, `Dotted` for a wait. The last stop's leg goes
nowhere. The list's `leadOut` says what leaves the last stop, and `leadIn` what
arrives at the first. Use them for a list that is a window onto a longer
journey. `connectorColour` and `connectorWidth` style one leg, and `nodeColour`
one stop, the way a transit app brands each route.

Each row draws half of the leg above its node and half of the leg below. The
rail is unbroken whatever the rows' heights, and a list built lazily draws
exactly the same as one built all at once. The dots and dashes are spaced so the
two halves meet with one whole gap between them, rather than two marks touching.

**The node sits on the middle of the label's first line.** An `overline` above
the label, such as a route number, does not move it, and neither does a second
line below it.

## Progress

`progress` is how far along the journey is, counted in stops. `0f` is at the
first stop, `1f` at the second, and `1.5f` halfway along the leg between the
second and the third. The rail and the nodes up to that point take the progress
colour. The nodes not reached yet take the rail's colour. The stop the journey
is at, if it is at one, gets a soft ring. A node given its own `nodeColour`
keeps it either way.

Each leg changes colour where one row hands it to the next. That is the middle
of the leg when the rows are the same height, and near the middle when they are
not. A row cannot know how tall the next one is, which is also what lets the
list be lazy.

## In a `LazyColumn`

`timelineList(items, key)` is the same list inside a `LazyColumn`. Like
`listGroup`, its builder runs for every element up front, because a row cannot
draw the top half of a leg until it knows the stop before it. The rows
themselves stay lazy. **Leave the column's `verticalArrangement` alone**: the
rail is unbroken because the rows touch, and spacing between them leaves gaps in
it. Grouped rows keep their gap inside themselves, and the rail crosses it.

## Not a `Timeline`

`Timeline` takes arbitrary content beside a rail: a map, a ticket, two lines of
text. Use it when the steps are something to read. Use `TimelineList` when each
step is a row to tap, with a trailing time and a selected state. They share
their connectors, nodes and spacing, so one of each on the same screen looks
like one family.

---

## Accessibility

Every stop is a `ListItem`: one merged node carrying its label, second line and
trailing value. When it has an `onClick` it is a button, or whatever its `role`
says, and a selected stop announces as selected. A disabled list disables every
row, and the rows stay in the reading order.

The rail, the nodes, the progress colour and the ring round the current stop are
drawn, not announced. The words have to carry the journey. Write "Departed
08:12", "Next stop", or "Walk 4 min, in progress" in the rows themselves. A
`loading` stop's spinner says nothing to a screen reader, so its row should say
what is being waited for.
