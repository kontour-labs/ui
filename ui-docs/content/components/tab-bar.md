# `TabBar` / `Tab`

![TabBar, with the first tab selected](../../../ui-catalog/screenshots/components/tab-selected-light.png)

<!--sample:TabBarBasics-->
```kotlin
var selected by remember { mutableStateOf("departures") }

TabBar {
    Tab(
        selected = selected == "departures",
        onClick = { selected = "departures" },
        key = "departures",
    ) {
        +"Departures"
    }
    Tab(
        selected = selected == "arrivals",
        onClick = { selected = "arrivals" },
        key = "arrivals",
    ) {
        +"Arrivals"
    }
}
```

**`TabBar` is not app navigation.** Tabs stay within one screen — the stop you
are looking at, seen three ways. A tab bar used for destinations leaves the user
with no back stack and no sense of where they are.

Its indicator is one bar that *slides*, for the same reason
[`SegmentedControl`](segmented-control.md)'s does: the row reads as a
single control with a moving part.

**Reach for `SegmentedControl` instead** when you are switching a *value* rather
than a view. Tabs announce `Role.Tab`; segments announce `Role.RadioButton`, and
a screen reader user acts on that difference.

### When the labels do not fit

A `TabBar` divides its width evenly between its tabs and lets each label
ellipsise. It does not hand the width out in composition order, which starved the
last tab: three tabs and an overflow button on a 360dp phone rendered "Alerts" as
a single "A" with its badge shaved to a red sliver. A badge always keeps its full
size — the label is what gives way.

Set `scrollable = true` where the labels matter more than seeing them all at
once. It is off by default because a scrolling row hides options past the edge
and gives the user no way to know how many there are.

### Swiping between tabs

`Modifier.tabSwipe` goes on the **content**, not on the bar — nobody swipes a tab
bar, they swipe the thing it is describing.

```kotlin
TabBar {
    Tab(selected = tab == 0, onClick = { tab = 0 }, key = 0) { +"Departures" }
    Tab(selected = tab == 1, onClick = { tab = 1 }, key = 1) { +"Route map" }
}
Box(Modifier.tabSwipe(selected = tab, count = 2, onSelectedChange = { tab = it })) {
    when (tab) { 0 -> Departures(); else -> RouteMap() }
}
```

It commits every quarter of the pane's width *while the finger is down*, so the
indicator travels with the drag instead of appearing at the far end once it is
over, and a long drag steps through several tabs. Each of those steps is a
**tick** — a detent crossed — and the **selection** fires once, when the finger
lifts. It used to spend a selection on every step, which made a three-tab drag
feel like three decisions rather than one.

It also does not steal from what it wraps. Being an ancestor of the content is
the whole mechanism: a child gets the main pointer pass first, so a carousel or a
scrolling row inside the tab keeps its own drags and the swipe picks up only what
nothing inside wanted.

---

← [Navigation](navigation.md) · [All components](../components.md)
