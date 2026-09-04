# `PullToRefresh`

Pull at the top of a list to reload. The indicator appears past a threshold, so
a list that is merely over-scrolled does not fire a request.

<!--sample:PullToRefreshBasics-->
```kotlin
PullToRefresh(refreshing = refreshing, onRefresh = { refresh() }) {
    LazyColumn {
        items(stops) { stop ->
            ListItem { +stop.name }
        }
    }
}
```

---

## Accessibility

The indicator is a **polite live region** whose description tracks the gesture —
pull, release, refreshing — so a state that is otherwise conveyed only by a
rotating arrow is spoken.

But it is still a gesture, and a gesture is not an affordance: **pull to refresh
must never be the only way to refresh.** Put the same action in a menu or a
toolbar. This is the single most common place a screen becomes unrefreshable
without a swipe.

`pullLabel`, `releaseLabel` and `refreshingLabel` are the three things that
region says, and they default from `Theme.strings`. Override them where "refresh"
is not the word — a timetable is *reloaded*, a trip is *replanned*.
