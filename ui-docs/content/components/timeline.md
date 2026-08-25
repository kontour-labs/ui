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

← [Display and content](display.md) · [All components](../components.md)
