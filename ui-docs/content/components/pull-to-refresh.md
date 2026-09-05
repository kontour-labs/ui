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

**It works with a mouse.** A `LazyColumn` answers a finger and a wheel and
nothing else — desktops do not drag lists — so a gesture built on nested scroll
alone did not exist on desktop or the web. Dragging the list down now pulls the
indicator there too, and only when the list is already at its top.

**A wheel is not a pull.** Spinning a wheel at the top of a list is a request to
read what is above; there is nothing above, so nothing happens. The distinction
is a held pointer rather than the kind of scroll: a pull is a sustained gesture —
press, drag, decide, let go — and a notch is not.

**The ring stops at an arc.** It grows and turns with the finger up to the
length the spinner opens at, and the spinner takes over from there, so the moment
the gesture commits is a change of meaning rather than a change of picture. It
used to close the circle and then empty.

**Letting it back slowly does not scroll the page.** Past the threshold the pull
resists, so the indicator moves less than the finger does; the way back closes at
full rate and consumes all of it, and the list underneath stays where it is until
the indicator is fully in.

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
