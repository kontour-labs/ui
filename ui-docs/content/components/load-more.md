# `LoadMore`

The paging row at the end of a list — idle, loading, failed with a retry, or
exhausted. Four states rather than a spinner, because "there is no more" and
"loading more" look identical if you only draw the second.

<!--sample:LoadMoreBasics-->
```kotlin
var state by remember { mutableStateOf(LoadMoreState.Idle) }

// One component for all four states — idle, loading, failed and the end of
// the list. The failure is the one that gets skipped when a screen rolls its
// own, and it is the one a user on a train actually meets.
LoadMore(
    state = state,
    onLoadMore = { state = LoadMoreState.Loading },
    errorLabel = "Couldn't load more departures",
    endLabel = "That's everything",
)
```

---

← [Collections](collections.md) · [All components](../components.md)
