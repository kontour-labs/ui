# `Timeline`

*Also on this page: `TimelineItem`.*

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
    TimelineItem(filled = false) {
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

---

## Accessibility

The timeline is a `Column` and its items are read top to bottom, which is the
order they mean. Nothing here adds a role: a journey is a sequence of content,
not a control.

The connectors and nodes are drawn, not announced, so the *text* has to carry the
sequence. "08:14 Perth Underground" then "08:21 Elizabeth Quay" reads as an
itinerary; two rows saying only the platform do not.

Where a step is complete or pending and that matters, put it in the words rather
than in `filled` — the node's fill is colour and shape, and neither is announced.

The same goes for `loading`. A spinner is a picture of waiting and says nothing
to a screen reader, so a row that is only a spinner is a row with no state at
all: write "Walking — 4 min" or "Finding a platform" in the item's own text.
