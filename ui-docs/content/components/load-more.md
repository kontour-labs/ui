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

## Accessibility

The block is a **polite live region**, so a state change at the foot of the list
is announced: the spinner's label while it loads, the error and its retry button
when it fails, and `endLabel` when there is nothing left.

The failure state is the one that matters most: without the announcement,
the retry button a reader needs appears silently below them.

The failure state is the one that gets skipped when a screen rolls its own, and
it is the one a user on a train actually meets. `onRetry` defaults to
`onLoadMore`, so a retry button exists without anybody wiring it.

`endLabel` is worth setting. "That's everything" is the answer to "have I reached
the bottom", which is otherwise unanswerable without sight.
