# `TabBar` / `Tab`

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

Its indicator is one **pill** that *slides*, for the same reason
[`SegmentedControl`](segmented-control.md)'s thumb does: the row reads as a
single control with a moving part. It used to be a bar sliding along the bottom
edge with a full-width rule under it, which was the most Material thing in the
library and had no counterpart on any other platform. The mechanism is unchanged
— the same shared indicator box, the same anchor arithmetic — it just says the
same thing in the library's own vocabulary, which is already full of pills.

The hairline rule under the bar used to stay, on the grounds that a bar squeezed
narrow enough to clip its labels draws *nothing at all* without it. That was
true, and it is not any more — see below — so `showDivider` now defaults to
`false`, matching [`TopBar`](top-bar.md). A pill *and* a full-width rule is the
sliding-underline bar wearing a different hat, and it is the one thing this bar
had that no other navigation surface in the library does.

**Reach for `SegmentedControl` instead** when you are switching a *value* rather
than a view. Tabs announce `Role.Tab`; segments announce `Role.RadioButton`, and
a screen reader user acts on that difference.

### The marker is sized to the bar, not to the label

`TabBar` is `TabBarDefaults.Height` tall and each tab fills it, so the pill
is that height less one grid step of air above and below — 40dp in a 48dp bar,
on every platform.

That last clause is the point. The pill used to be inset from the tab's *own*
box, and a tab is only as tall as its label plus padding unless
`minimumTouchTarget()` grows it — which it does to `Sizing.minTouchTarget`:
**24dp on desktop, 44 on iOS and web, 48 on Android**. So the same bar drew a
24dp marker on one platform, a 32dp one on another and a 36dp one on a third,
inside a bar that is 48dp on all of them. On desktop that is a 97 × 24dp lozenge
— four times as wide as it is tall — floating in the middle of a bar twice its
height. Every other marker in the library is either a constant (the nav bar's
`Fixed(56, 32)`) or exactly the row it marks (the rail's and drawer's
`Inset(vertical = 0.dp)`); this was the only one whose proportions were a
platform guideline.

### A squeezed tab keeps its label

A tab's `md` of padding either side is what gives the marker room around the
label. Fixed, it is 32dp the label can never reclaim — so a bar squeezed to 48dp
gave the label 16dp, which is narrower than the ellipsis it truncates to, and
the tab drew *nothing at all*. The hairline rule was the only ink left, which is
what the argument for keeping it was really describing.

The padding now gives way, under one rule: **it never takes more than the label
keeps.** That needs no number of its own and stops applying at 64dp — twice the
padding — so every width an app actually draws is untouched, and a bar narrower
than any of them shows a truncated word instead of an empty strip.

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

## Accessibility

Tabs are `Role.Tab` inside a `selectableGroup()`, so a screen reader announces
"2 of 3" and which one is selected. The travelling pill is drawn by the bar and
carries no semantics — selection is reported by the tab.

The overflow menu button sits **outside** the group deliberately: inside it, it
would be counted as a tab, and the bar would announce four tabs when it has
three.

A tab bar changes what is below it. Where that content is a separate scrolling
region, give it a heading so a user who has switched tabs can find where they
have arrived.
