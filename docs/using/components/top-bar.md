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

← [Navigation](navigation.md) · [All components](../components.md)
