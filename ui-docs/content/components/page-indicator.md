# `PageIndicator`

**The current page is drawn wider** rather than only tinted. Colour alone fails
WCAG 1.4.1, and eight pixels of tinted circle is the hardest place in the system
to see a tint difference.

<!--sample:PageIndicatorBasics-->
```kotlin
val carousel = rememberCarouselState { 5 }
val scope = rememberCoroutineScope()

// Given `onPageClick` the dots become the control as well as the readout.
// The strip takes the tap and sends it to the nearest dot, so the indicator
// stays the width of its own ink rather than 48dp per page.
PageIndicator(
    state = carousel,
    onPageClick = { page -> scope.launch { carousel.animateScrollToPage(page) } },
)
```

`onPageClick = null` makes the dots decorative *and* hides them from the
accessibility tree — the carousel already says "3 of 5", and a screen reader
walking five unlabelled dots after it is noise. Pass a handler and each dot
becomes a `Role.RadioButton` naming the page it goes to.

The default style rests like a widened dot and travels like a worm; the three
styles and what separates them are under
[carousel § indicator styles](carousel.md#indicator-styles).

### Dragged along, page by page

With `onPageClick`, a press that moves along the dots **scrubs**: the page under
the finger is chosen as it passes, each one once, and a pill comes up round the
dots for as long as the drag lasts, so the strip shows itself as the thing being
dragged. Past the last dot is the last page, and before the first,
the first. A tap is still a tap on the nearest dot.

Only along the strip. A press that moves across it first is a scroll of whatever
the dots sit in, and is left for that. Each page passed under the finger is a
`Snap`, as a segmented control's segments are; the carousel moving because of it
says nothing, as any page reached in code does.

The pill is `surfaceSunken`, the well a segmented control's track and a filled
text field are, in the theme's `pill` shape: a squircle, round at its ends round
the round dots inside them, however a theme caps its capsules. It has an edge at
high contrast, and under reduced motion it fades in without growing.

### Vertical

`orientation = Orientation.Vertical` stacks the dots top to bottom, for a pager
that turns vertically or the edge of a full-screen story. Everything that runs
along the row runs down the column instead: the current dot grows taller rather
than wider, the pill travels down, a tap goes to the nearest dot by height, a
drag scrubs up and down, and
`previousIcon` and `nextIcon` go above and below — pass glyphs that point up and
down. The first page is at the top in either layout direction.

**The gap between dots is 8dp**, up from six. Six was set while every dot reserved
48dp of row and the gap that was drawn was not the gap anyone saw; once the strip
stopped doing that, six read as cramped — which it had been all along.

---

## Accessibility

With `onPageClick` the dots become a `selectableGroup` of `Role.RadioButton`
nodes, each described by `label(page, count)` — `Theme.strings.pageOfCount` by
default, so "Page 2 of 5" rather than a dot with no name.

**The strip is one touch target, not one per dot.** That is the second version of
this. Each dot used to carry its own `minimumTouchTarget()`, and on Android that
reserves 48dp of row apiece — so five dots showing 52dp of ink were laid out
across 264dp and sat five times further from each other than they looked. It was
reported from a phone as the indicator feeling too spread out, and this page had
the symptom written down as if it were a feature.

So the duty moved up one level. The row reserves the minimum target once, as a
full-height band the width of the dots, and a tap goes to whichever dot centre is
nearest the finger. Nothing inside the band is dead — the gaps between the old
48dp boxes selected nothing at all, which no one could see and everyone could feel.

**What that trades.** A slice of the band is one dot's pitch wide — 16dp between
two plain dots, rising to 22dp for the widened one — where WCAG 2.5.8 asks for
24dp; the old layout cleared that and this does not. Two things make it the better arrangement anyway,
and both are worth stating rather than implying:

- the exact route is unaffected. Each dot's `onClick` lives in the semantics tree,
  which is what TalkBack and VoiceOver activate — a focused node, not a rectangle
  — and it costs no layout at all.
- the slice is taller and contiguous where the target was square and isolated. An
  8dp dot inside a 48dp box was never what anyone aimed at; a 16 × 48dp column
  under the dot you can see is closer to what the gesture actually is.

Where a 24dp target for each page is the requirement rather than the goal, pass
`previousIcon` and `nextIcon`: those are `IconButton`s and carry full targets, and
they are the arrangement to reach for on a page a mouse or a switch device is
driving.

Without `onPageClick` it is a read-out and carries nothing, including the band:
the [`Carousel`](carousel.md) it belongs to is what announces the page.

The travelling styles change nothing about what is announced. Under reduced motion
the *dot widening* is a short tween with no overshoot rather than a spring. The
pill follows the page position, so it follows a finger for as long as there is one
on the screen, and a page changed through `CarouselState.animateScrollToPage`
arrives at once, with the pill already on it.

At high contrast a `Worm` rests as `Pill` does, with the current dot widened,
because a row of identical dots says where you are by colour alone. The dots
that are not current are `inactive` by default, the tier's colour for the off
half of a pair, and the step buttons, which are ghosts, gain an outline.
