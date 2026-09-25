# `Timeline`

*Also on this page: `TimelineItem`, `HorizontalTimeline`, `TimelineColours`.*

<!--sample:TimelineBasics-->
```kotlin
Timeline {
    TimelineItem {
        Text("Perth Underground", style = Theme.typography.titleSmall)
        Text("08:14 · Platform 2", style = Theme.typography.bodySmall)
    }
    TimelineItem {
        Text("Elizabeth Quay", style = Theme.typography.titleSmall)
        Text("08:21 · Platform 1", style = Theme.typography.bodySmall)
    }
    // The last item draws no connector below it, because there is nothing
    // for it to connect to.
    TimelineItem(filled = false, connector = ConnectorStyle.None) {
        Text("Perth Busport", style = Theme.typography.titleSmall)
        Text("08:29 · Stand 24", style = Theme.typography.bodySmall)
    }
}
```
`Timeline` and `TimelineItem` — a vertical sequence, which in this app is the
journey itinerary.

**The connector is drawn to the full height of its row**, using
`IntrinsicSize.Min`. A fixed-height connector leaves gaps against tall rows and
overshoots short ones, which is what makes most hand-rolled timelines look
assembled rather than built.

**`connector` is the weight of the leg, not a decoration.** `Solid` is a ride,
`Dashed` a walk, `Dotted` a wait — a transfer window, an estimate nobody has
committed to — and `None` ends the rail. A dot is as wide as the connector, so a
4dp train segment and a 2dp walk get dots in proportion rather than a fixed one.

**Every style reaches the bottom of its row, and getting there took two goes.**
The dots and the dashes were a dash pattern over the same line the solid style
draws, and a dash pattern is walked from the start of the path and abandoned
wherever the path runs out — so the remainder of the gutter was blank and the rail
appeared to come apart above the next node. The dashes lost up to one gap; the
dots lost a whole dot, because a partial dot is nothing, and lost one *even when
the pitch divided the row exactly*, since a zero-length dash that falls on the
path's own endpoint is not drawn at all. Both are now spaced to the run they have
rather than to a multiple of the stroke: the dots are placed, one on each end,
with the pitch stretched by under half a diameter to make a whole number of them
fit, and the dashes keep their length and give the adjustment to the gap.

`loading = true` puts a spinner where the node's dot would be, for the step a
timeline is waiting on — a train with no platform yet, a payment being taken. The
connector below it is unchanged, because the itinerary is not in doubt; one step
of it is. The spinner stands on the same line as every other node rather than in
the middle of its row, so the rail does not bend around the step that is still
going, **and it is drawn at `connectorWidth`** so it is the same weight as the
hollow dot it replaces. `Spinner` otherwise derives its stroke from its size,
which at a 12dp node is 1.5dp against the ring's 2dp: a step going into progress
got visibly thinner and the rail around it did not.

## Progress

`progress` says how far along a timeline the journey is, counted in items:
`0f` at the first, `1f` at the second, `1.5f` halfway along the leg between the
second and the third. **Everything up to there takes the progress colour, and
everything after it the rail's**, so a glance says what is behind and what is
to come.

- **At an item**, its node gets a halo that pulses: it swells and fades, and
  swells again, while the journey is there.
- **Between two**, the leg it is on is coloured as far as the journey has got,
  and a band travels along the rest of it towards the next item, the same band
  a `StepProgress` step shows while it is working.

Under reduced motion the halo holds still and the band is not drawn, and the
coloured part of the leg still says how far along it is. An item's own
`nodeColour` or `connectorColour` keeps its colour whatever the progress; leave
them unspecified to take `colours`, which are the same `TimelineColours` a
`TimelineList` takes. Items count in the order they are laid out, so one
wrapped in a `Box` or added between two others takes its place in the count.

## Across the page

<!--sample:HorizontalTimelineBasics-->
```kotlin
// The same items, laid across: each node at its item's start, the content
// under it, and the connector running on to the next.
HorizontalTimeline {
    TimelineItem {
        Text("Ordered", style = Theme.typography.titleSmall)
        Text("Mon 3", style = Theme.typography.bodySmall)
    }
    TimelineItem {
        Text("Packed", style = Theme.typography.titleSmall)
        Text("Tue 4", style = Theme.typography.bodySmall)
    }
    TimelineItem(connector = ConnectorStyle.Dashed) {
        Text("On its way", style = Theme.typography.titleSmall)
        Text("Wed 5", style = Theme.typography.bodySmall)
    }
    TimelineItem(filled = false, connector = ConnectorStyle.None) {
        Text("Delivered", style = Theme.typography.titleSmall)
        Text("Thu 6, expected", style = Theme.typography.bodySmall)
    }
}
```
`HorizontalTimeline` lays the same `TimelineItem`s across the page, for a handful
of stages read at a glance: an order's progress, a short trip. Each item puts its
node at its start with the content under it, and its connector runs to the
item's end edge, where the next node begins. The connectors are the same three
styles, and every one of them reaches the next node here too.

Each item is as wide as its content, up to about 200dp, past which its text
wraps. **`equalWidths = true`** makes every item as wide as the widest, or an
even share of the width when that is more. Use it when the spacing between
stages should not depend on how long their names are. When the items are wider
than the screen, the timeline scrolls sideways, and `scrollState` lets you bring
the current stage into view. Right to left, the first stage is at the right.

It is not a `Row`, and its items get no `RowScope`: they are measured inside a
scroller, where a `weight` would be asked to share an unbounded width.
`equalWidths` is the way to ask for even spacing. An item across is two parts,
its node on the rail and its content under it, and **an item's `modifier` is its
content's**: a click or a width applies to the words, not the rail.

`progress` works across as it does down, with the first stage moved in far
enough that its pulse is not cut off at the edge.

---

## Accessibility

The timeline is a `Column` and its items are read top to bottom, which is the
order they mean. A `HorizontalTimeline` is read start to end, the same order. Nothing here adds a role: a journey is a sequence of content,
not a control.

The connectors and nodes are drawn, not announced, so the *text* has to carry the
sequence. "08:14 Perth Underground" then "08:21 Elizabeth Quay" reads as an
itinerary; two rows saying only the platform do not.

Where a step is complete or pending and that matters, put it in the words rather
than in `filled` — the node's fill is colour and shape, and neither is announced.

The same goes for `loading`. A spinner is a picture of waiting and says nothing
to a screen reader, so a row that is only a spinner is a row with no state at
all: write "Walking — 4 min" or "Finding a platform" in the item's own text.
`progress` is drawn too, so say where the journey is in words as well: "Arrived",
"Next stop: Elizabeth Quay".
