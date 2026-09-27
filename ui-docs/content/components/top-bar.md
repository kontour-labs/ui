# `TopBar`

Title and actions. Three styles from `TopBarStyle`: `Small`, `Centred` and
`Large`.

<!--sample:TopBarCollapsing-->
```kotlin
val listState = rememberLazyListState()

// The bar does not own the scroll state, so the caller hands it the
// progress. `collapseProgress` computes it from a `LazyListState`.
TopBar(
    style = TopBarStyle.Large,
    collapseProgress = collapseProgress(listState),
) {
    +"Perth Underground"
    supporting { +"Platform 2" }
}

LazyColumn(state = listState) {
    stopRows()
}
```

`Large` collapses to `Small` as the content scrolls. The bar does not own the
scroll state, so the caller passes the progress — `collapseProgress(listState)`
computes it from a `LazyListState`, and there is a raw
`collapseProgress(scrolled, distance)` for anything else.

---

## Accessibility

The title is marked `heading()`, so a screen reader can jump straight to it —
which is how a user finds out what screen they are on after navigating.

The back button carries `backLabel`. Action icons need their own
`contentDescription`; the bar cannot invent one.

`windowInsets` defaults to the top edge, so the bar is not under the status bar
on an iOS PWA. `collapseProgress` animates the title and changes nothing about
what is announced.

At high contrast the bar takes the enhanced scheme's colours and adds no edge of
its own — it is the page's ground by default — and the rule `showDivider` draws
under it thickens with the tier. The back button is a ghost
[`IconButton`](icon-button.md), so it gains a ring there.

Reduced motion leaves the large title's collapse as it is: it is driven by the
scroll position rather than by a clock, so it moves only as the content under it
does, and the title slides a few pixels as it fades. The back button keeps its
press wash and loses its shrink.
