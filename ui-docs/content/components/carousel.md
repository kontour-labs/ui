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
- and `PageIndicator` becomes a real target the moment you give it
  `onPageSelect` — one full-height band that sends a tap to the nearest dot, plus
  a named `Role.RadioButton` per page for a screen reader.

A carousel with a decorative indicator and no arrows is operable by exactly one
input method, and the app has four.

### Page styles

**`CarouselStyle.Slide` is the default**: the pages are a strip and the strip
moves under the viewport, which is what a finger expects and what a carousel has
always been.

**`CarouselStyle.Hero` is two boxes side by side, trading width.** The page you
are on is most of the frame; the next one is a narrow box beside it with a gap
between them, and a swipe hands the width from one to the other. Material's hero
carousel, and the arrangement a row of pictures wants — what the eye follows is a
photograph getting bigger, with the next one already there to say the row
continues.

```kotlin
Carousel(
    carousel,
    contentDescription = "Stop photos",
    style = CarouselStyle.Hero,
    peek = 96.dp,
) { page -> Image(photos[page]) }
```

`peek` is how much of the next page shows and `pageSpacing` is the gap between
the two boxes — the one place in a carousel's layout where that spacing is
something a reader can see. **A third of the frame is the ceiling worth designing
to**: past that the "next" box is competing with the page you are looking at, and
a hero carousel with two heroes in it is a two-column list. Nothing clamps it, so
an over-wide peek costs the hero its width.

**`peek = 0.dp` is one page at a time.** The frame holds the hero and nothing
else, and the gap opens between the two boxes only while a swipe is in flight —
there being nothing for it to separate at rest.

`parallax` is how much of the strip's travel the *content* of the page being left
behind keeps. Its box is pinned to the frame's start and closes over it, so at `0`
the picture holds still and is taken away; at `1` it travels with the strip and
slides out under a shrinking window; `0.2`–`0.3` is a drift behind the closing
edge. It is **ignored under reduced motion** — boxes trading width is the style,
and a picture drifting underneath the one closing over it is the embellishment on
top. There is nothing to scale on the page arriving: its box is already at the
strip's position, so its content travels with it.

Reach for it when each page is **one picture**. Avoid it for pages with structure
— a form, a list — because a page is measured once at the hero's width and masked
down to whatever its box currently is, so its content is **cropped rather than
reflowed**. That is the trade, and it is deliberate: a page that re-laid-out
sixty times a second under a finger is what a carousel of text would cost.

The last page grows to the whole frame rather than to a hero's width, because
there is no page after it to fill the gap and the peek.

Everything else is the same as a slide — the same drag, the same snap, the same
accessibility actions and the same announcement. It changes where the pixels go
and nothing else.

### Indicator styles

**The default, `PageIndicatorStyle.Pill`, does both halves.** A pill sits over the
current dot at rest, and stretches from the dot it is leaving to the one it is
arriving at while a page travels, then contracts onto it.

The travel is the only thing that shows the *middle* of a swipe: the pill is at
its longest exactly halfway between two pages. That needs a fractional page
position, which is what `CarouselState.pagePosition` is for — `currentPage` is
the right answer for anything that has to *name* a page, and this is for anything
that has to draw the space between two.

`PageIndicatorStyle.Worm` is the same travel with the pill contracting to a
*dot*, so at rest it looks like an indicator with no current page at all — which
it also *is*, to a screen reader: under a worm every dot is drawn inactive and
none reports as selected. `PageIndicatorStyle.Dots` is the other half on its own:
the current dot widens and nothing travels, which is right where the indicator is
never the subject of a gesture.

`Pill`'s layout **is** `Dots`': the row widens the current dot, and the pill at
rest is that dot's own box. So the gaps are the one gap all the way along and
switching between the two moves nothing. `Worm` is narrower because it widens no
dot, which is the same fact as it saying nothing about the current page at rest.

The choice between them is about the travel, not about the targets. The strip is
one target under every style — see [page-indicator](page-indicator.md).

---

## What it refuses

The `pageCount` lambda must not return a negative number. Zero is fine and
common — it is what a collection reports before it has loaded, and an empty
carousel draws nothing, which is correct.

Negative is different: it is nearly always a subtraction against something that
has not arrived, and it used to reach the reader as `NegativeArraySizeException`
from inside [`PageIndicator`](page-indicator.md), which sizes an array with the
count. It now throws where the lambda is read, naming the carousel and the
parameter.

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
