# `PullToRefresh`

Pull at the top of a list to reload. The indicator appears past a threshold, so
a list that is merely over-scrolled does not fire a request.

<!--sample:PullToRefreshBasics-->
```kotlin
PullToRefresh(refreshing = refreshing, onRefresh = { refresh() }) {
    LazyColumn {
        listSection(stops) { stop, position ->
            ListItem(position = position) { +stop.name }
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

**The ring stops at three quarters, and the spinner contracts out of it.** The
arc grows and turns with the finger to about 270° at the threshold, and the
spinner then opens *at that length and that angle* and shortens into its own over
a beat — so the moment the gesture commits is a change of meaning rather than a
change of picture. It used to close the circle and then empty; more recently it
matched the length but not the position, so an arc that had been dragged most of
the way round the dial jumped back to three o'clock in one frame.

The three quarters is what the ceiling used to cost. It was pinned to the
spinner's own opening length — a hair over half a circle — purely so the swap
would not be a step, which made a gesture taken all the way to its threshold look
like it had barely committed. Handing the length *to* the spinner keeps the swap
smooth without holding the arc back.

**The indicator reaches full size before the finger is done.** Its scale and its
opacity ramp over the first 40% of the pull rather than the whole of it. While
they tracked the whole pull they multiplied with the sweep — the arc's apparent
length grew as the *square* of the pull — and a half pull drew a short arc at
half size and half opacity, which measured as no ink at all. It still grows in
from nothing, so a stray one-pixel drag does not flash a control onto the screen;
it just finishes early and leaves the rest of the gesture to the arc.

**The circle never overlaps the list, and is never cut off by the top edge.** It
is centred in the gap the list has vacated, so a 40dp circle in a gap shorter than
that hangs over both ends at once — over the content below and above the container's
own edge, where a clipping ancestor shaves it flat. There is no position that fixes
it, because a 40dp circle does not fit in a 20dp gap, so the circle is *sized* to
the gap: its drawn diameter is the smaller of 40dp and the gap, and it grows out of
the top edge as the list comes away from it. Once the gap is 40dp or more the bound
does nothing at all and the indicator is the size it has always been.

**Letting it back slowly does not scroll the page.** Past the threshold the pull
resists, so the indicator moves less than the finger does; the way back closes at
full rate and consumes all of it, and the list underneath stays where it is until
the indicator is fully in.

## Under a top bar

What it holds is what a pull brings down, and the indicator comes in at its top
edge. So put it round the list alone, below the page's `TopBar`, not round the
page: round the page, the bar comes down with the pull and the indicator appears
above it. With a collapsing bar, put `Modifier.topBarCollapse(collapse)` on the
list, inside the refresh, so a pull at the top opens the bar before it starts a
refresh:

```kotlin
Column {
    TopBar(style = TopBarStyle.Large, collapseState = collapse) { +"Departures" }
    PullToRefresh(refreshing, onRefresh, Modifier.weight(1f)) {
        LazyColumn(Modifier.topBarCollapse(collapse)) { … }
    }
}
```

**Under a bar drawn over the list** — a `Hero` following its list, with
`rememberTopBarCollapseState(list)` — the list starts below the bar, and so does
the gap a pull opens. Give `indicatorPadding` the top the list's content has,
and the indicator comes in there rather than under the bar:

```kotlin
Box {
    PullToRefresh(refreshing, onRefresh, indicatorPadding = PaddingValues(top = collapse.openHeight)) {
        LazyColumn(state = list, contentPadding = PaddingValues(top = collapse.openHeight)) { … }
    }
    TopBar(style = TopBarStyle.Hero, collapseState = collapse, image = { … }) { +"Departures" }
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

At high contrast the disc takes an edge, since a raised disc over the list is
1.00:1 at that tier. The arc drawn while pulling is half a line heavier than a
strong border, so it thickens with the tier.

Under reduced motion the list still follows the finger, and the arc fills with
the pull without turning. Once refreshing, the spinner keeps turning with its
breathing stilled. The list's step to the refreshing position after you let go,
and its return to rest, happen at once: no finger is moving it then.
