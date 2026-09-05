# `Carousel`

**Draggable with a pointer**, not only scrollable with a finger. A carousel is a
stack of cards, and pulling one aside is the only gesture anybody tries; on
desktop that used to leave the indicator dots as the sole route.

<!--sample:CarouselWithIndicator-->
```kotlin
val scope = rememberCoroutineScope()
val carousel = rememberCarouselState { photos.size }

Carousel(carousel, contentDescription = "Stop photos") { page ->
    Text(photos[page])
}
PageIndicator(carousel, onPageSelect = { scope.launch { carousel.scrollToPage(it) } })
```

It snaps. A carousel that stops between two pages is showing neither, and the
indicator under it is then lying whatever it says.

**A quarter of a page turns it.** Compose's own snapping asks which page is
nearest, which is a list's question — with items smaller than the viewport,
nearest is the one you are mostly looking at. A carousel's page *is* the
viewport, so nearest meant dragging a full-width card past its own middle before
the gesture took, and anything less slid all the way back having done nothing.
`CarouselDefaults.SnapThreshold` is a quarter, counted from wherever the gesture
began rather than from a fixed edge: a third of the way along is a third forward
from one page or two thirds back from the next, and which it is decides which way
a quarter counts.

That number is also what a **trackpad** inherits. A two-finger sideways push
already settles on its own — the platform runs the snap once the gesture goes
quiet — but a push moves a little at a time, so against a half-page threshold it
landed back where it started every time, which reads as a carousel refusing to
move rather than as one snapping to the wrong end.

`currentPage` is derived from the scroll **offset**, not from
`firstVisibleItemIndex`. That index changes the instant a single pixel of the
next page appears, so an indicator driven by it flips forward at the very start
of a drag and then sits there while the user is still looking at the previous
page.

### The swipe is a shortcut, not the route

The house rule from [collections](collections.md#gestures-are-shortcuts-never-routes)
applies here too, and a carousel is the easiest place to forget it. A drag is
invisible, has no keyboard equivalent, and is unreachable for anyone who cannot
make a sustained one. So:

- the carousel carries **previous** and **next** as custom accessibility
  actions, the same way `SwipeActions` and `ReorderableItem` do;
- it announces "3 of 5" as its state;
- and `PageIndicator` becomes a set of real targets the moment you give it
  `onPageSelect`.

A carousel with a decorative indicator and no arrows is operable by exactly one
input method, and the app has four.

### Indicator styles

`PageIndicator(style = PageIndicatorStyle.Worm)` replaces the widening dot with a
single pill that stretches from the dot it is leaving to the one it is arriving
at, then contracts.

It reads as one thing travelling rather than one dot going out and another coming
on, and it is the only style that shows the *middle* of a swipe: the pill is at
its longest exactly halfway between two pages. That needs a fractional page
position, which is what `CarouselState.pagePosition` is for — `currentPage` is
the right answer for anything that has to *name* a page, and this is for anything
that has to draw the space between two.

Under a worm every dot is a track, so `Dots` remains the better choice when the
indicator is also the control: there each dot keeps its own footprint and its own
target.

---

## Accessibility

`contentDescription` is required, and it names the **set** — "Stop photos" —
because a carousel is one control containing several pages rather than several
controls.

The current page is the carousel's `stateDescription`, and it exposes previous
and next as `CustomAccessibilityAction`s, so paging does not depend on a swipe.
That is the whole reason a carousel can be used without a pointer.

A carousel hides content by default. Anything essential inside one is essential
content behind a gesture, so give it a [`PageIndicator`](page-indicator.md) with
`onPageSelect` — or reconsider the carousel.
