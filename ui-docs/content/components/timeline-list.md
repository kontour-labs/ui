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

**`icon` puts a small icon inside a stop's node**: how the stop is left, a
transfer, the end of the trip. The node grows to `TimelineDefaults.IconNodeSize`
to hold it, and only that one: the stops without keep their dot. On a solid node
the icon takes whichever of light or dark reads on it, so a route colour straight
from a feed stays legible; inside a ring it takes the ring's colour. A `loading`
stop shows its spinner and no icon.

<!--sample:TimelineIcons-->
```kotlin
// An icon inside a node grows it to hold one; the stops without keep their dot.
TimelineList {
    item("Perth Station", trailing = "08:12", icon = Tabler.Outline.Train)
    item("Walk 4 min", connector = ConnectorStyle.Dashed, filled = false, icon = Tabler.Outline.Walk)
    item("Elizabeth Quay", trailing = "08:21")
    item("Perth Busport", trailing = "08:29", icon = Tabler.Outline.Flag2)
}
```

## Node groups

**`nodeGroup(count)` is one row standing for several stops**: the stretch of a
route nobody needs to see stop by stop, folded away until it is pressed open.
Its node is a few nodes bunched along the rail, overlapping — up to three,
however many stops it stands for — the last whole and each before it cut back
where the next covers it, so it reads the same on the page or on grouped rows.
They are rings unless `filled` says otherwise.

<!--sample:TimelineNodeGroupBasics-->
```kotlin
// Each group is one row for the stops it folds away: one node to progress,
// one tap to open them.
TimelineList(progress = 2f) {
    item("Hospital Av", trailing = "11:34 pm")
    nodeGroup("Show 10 past stops", count = 10, connector = ConnectorStyle.Dotted, onClick = onShowPast)
    item("Elizabeth Quay Bus Station", supporting = "Stand A6", trailing = "11:47 pm")
    nodeGroup("Show 38 upcoming stops", count = 38, connector = ConnectorStyle.Dotted, onClick = onShowUpcoming)
    item("Morley", supporting = "Stand 10", trailing = "12:19 am")
}
```

To the rail a group is a stop: its `connector` is the leg after it, and the
legs either side clear the whole stack. To `progress` it is one stop too — put
a journey that is somewhere among the stops it folds at the group's row, and
the halo goes round the whole stack. Opening it is the caller's: replace the
group with its stops, and the progress with the count to the stop the journey
is at.

**The node sits on the middle of the label's first line.** An `overline` above
the label, such as a route number, does not move it, and neither does a second
line below it.

## Progress

`progress` is how far along the journey is, counted in stops. `0f` is at the
first stop, `1f` at the second, and `1.5f` halfway along the leg between the
second and the third. The rail and the nodes up to that point take the progress
colour. The nodes not reached yet take the rail's colour. A node given its own
`nodeColour` keeps it either way.

- **At a stop**, its node gets a halo that pulses: it swells and fades, and
  swells again, while the journey is there.
- **Between two**, the leg turns faint and a band sweeps its whole length, from
  one stop to the next and again, the same band a `StepProgress` step shows
  while it is working. It shows whatever colour the leg is, and crosses from
  one row to the next without a jump, in a `LazyColumn` too.

`progressMotion = TimelineMotion.Arrows` shows that leg with arrows instead of
a band: drawn in full as far as the journey has got, with arrows marching along
the rest towards the next stop. Each row fits a whole number of arrows to its
half of the leg, so they cross from one row to the next without a jump too.

Under reduced motion the halo holds still and there is no band: the leg is
coloured as far as the journey has got instead. The
colours are a `TimelineColours`, the same as a `Timeline`'s, and
`containerColour` is the grouped rows' ground.

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

The rail, the nodes and their icons, a node group's stack, the progress colour
and the ring round the current stop are drawn, not announced. A group's row has
to say how many stops it stands for in its words — "Show 10 past stops". The words have to carry the journey. Write "Departed
08:12", "Next stop", or "Walk 4 min, in progress" in the rows themselves. A
`loading` stop's spinner says nothing to a screen reader, so its row should say
what is being waited for.

At [high contrast](../accessibility.md#what-high-actually-changes), on a
travelled list, the stops not yet reached are hollow and the halo round the
current stop is a solid ring, so reached and not reached differ in shape as well
as colour. The rail and the rings take their weight from `borderWidthStrong`,
which is thicker at this tier; grouped rows take a list row's edge, and a
selected stop an accent outline round its tint.

Under reduced motion the pulse off the current stop and the band running along
the leg ahead both stop, and the rail is drawn still, filled as far as
`progress` says. Arrows, if asked for, stand still where they are. A
`loading` stop's spinner keeps turning, with its breathing stilled.
