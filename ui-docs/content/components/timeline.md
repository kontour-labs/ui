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
