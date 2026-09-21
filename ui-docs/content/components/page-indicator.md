# `PageIndicator`

**The current page is drawn wider** rather than only tinted. Colour alone fails
WCAG 1.4.1, and eight pixels of tinted circle is the hardest place in the system
to see a tint difference.

<!--sample:PageIndicatorBasics-->
```kotlin
val carousel = rememberCarouselState { 5 }
val scope = rememberCoroutineScope()

// Given `onPageSelect` the dots become the control as well as the readout.
// The strip takes the tap and sends it to the nearest dot, so the indicator
// stays the width of its own ink rather than 48dp per page.
PageIndicator(
    state = carousel,
    onPageSelect = { page -> scope.launch { carousel.scrollToPage(page) } },
)
```

`onPageSelect = null` makes the dots decorative *and* hides them from the
accessibility tree — the carousel already says "3 of 5", and a screen reader
walking five unlabelled dots after it is noise. Pass a handler and each dot
becomes a `Role.RadioButton` naming the page it goes to.

The default style rests like a widened dot and travels like a worm; the three
styles and what separates them are under
[carousel § indicator styles](carousel.md#indicator-styles).

---

## Accessibility

With `onPageSelect` the dots become a `selectableGroup` of `Role.RadioButton`
nodes, each described by `label(page, count)` — `Theme.strings.pageOfCount` by
default, so "Page 2 of 5" rather than a dot with no name.

**The strip is one touch target, not one per dot.** That is the second version of
this. Each dot used to carry its own `minimumTouchTarget()`, and on Android that
reserves 48dp of row apiece — so five 8dp dots with 6dp between them, 64dp of ink,
were laid out across 264dp and sat nearly four times further from each other than
they looked. It was reported from a phone as the indicator feeling too spread out,
and this page had the symptom written down as if it were a feature.

So the duty moved up one level. The row reserves the minimum target once, as a
full-height band the width of the dots, and a tap goes to whichever dot centre is
nearest the finger. Nothing inside the band is dead — the 6dp gaps between the old
48dp boxes selected nothing at all, which no one could see and everyone could feel.

**What that trades.** A slice of the band is one dot's pitch wide, 14dp, where
WCAG 2.5.8 asks for 24dp; the old layout cleared that and this does not. Two
things make it the better arrangement anyway, and both are worth stating rather
than implying:

- the exact route is unaffected. Each dot's `onClick` lives in the semantics tree,
  which is what TalkBack and VoiceOver activate — a focused node, not a rectangle
  — and it costs no layout at all.
- the slice is taller and contiguous where the target was square and isolated. An
  8dp dot inside a 48dp box was never what anyone aimed at; a 14 × 48dp column
  under the dot you can see is closer to what the gesture actually is.

Where a 24dp target for each page is the requirement rather than the goal, pass
`previousIcon` and `nextIcon`: those are `IconButton`s and carry full targets, and
they are the arrangement to reach for on a page a mouse or a switch device is
driving.

Without `onPageSelect` it is a read-out and carries nothing, including the band:
the [`Carousel`](carousel.md) it belongs to is what announces the page.

The travelling styles change nothing about what is announced. Under reduced
motion the *dot widening* stops animating, as everything else in the system does;
the pill follows the page position, so it follows a finger for as long as there is
one on the screen.
