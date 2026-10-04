# `LoadMore`

The paging row at the end of a list — idle, loading, failed with a retry, or
exhausted. Four states rather than a spinner, because "there is no more" and
"loading more" look identical if you only draw the second.

<!--sample:LoadMoreBasics-->
```kotlin
var status by remember { mutableStateOf(LoadMoreStatus.Idle) }

// One component for all four states — idle, loading, failed and the end of
// the list. The failure is the one that gets skipped when a screen rolls its
// own, and it is the one a user on a train actually meets.
LoadMore(
    status = status,
    onLoadMore = { status = LoadMoreStatus.Loading },
    errorMessage = "Couldn't load more departures",
    endLabel = "That's everything",
)
```

**It asks when it is on screen.** Idle, it loads once any of it is in the
window, so it works at the foot of a plain scrolling `Column` as well as at the
end of a `LazyColumn`. It used to ask whenever it was composed, and a `Column`
composes everything at once, so it chained through every page before anyone
scrolled. A foot still on screen after a short page asks again, until the window
is full.

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

At high contrast the retry button, a ghost, gains an outline in its label's
colour, as every ghost button does at that tier. The message, the end label and
the spinner take the enhanced scheme's colours.

Under reduced motion the spinner keeps turning with its breathing stilled, and
that is the only change. The states swap without a transition at every setting,
and the retry button's press is a tonal wash, never a shrink.
