# `PageIndicator`

**The current dot widens** rather than only changing colour. Colour alone fails
WCAG 1.4.1, and eight pixels of tinted circle is the hardest place in the system
to see a tint difference.

<!--sample:PageIndicatorBasics-->
```kotlin
val carousel = rememberCarouselState { 5 }
val scope = rememberCoroutineScope()

// Given `onPageSelect` the dots become the control as well as the readout,
// which is what the default style is sized for: every dot keeps its own
// footprint and its own touch target.
PageIndicator(
    state = carousel,
    onPageSelect = { page -> scope.launch { carousel.scrollToPage(page) } },
)
```

`onPageSelect = null` makes the dots decorative *and* hides them from the
accessibility tree — the carousel already says "3 of 5", and a screen reader
walking five unlabelled dots after it is noise. Pass a handler and each dot
becomes a `Role.RadioButton` naming the page it goes to.

---

## Accessibility

With `onPageSelect` the dots become a `selectableGroup` of `Role.RadioButton`
nodes, each described by `label(page, count)` — `Theme.strings.pageOfCount` by
default, so "Page 2 of 5" rather than a dot with no name. Each gets a full
`minimumTouchTarget`, which is why the dots sit further apart than they look.

Without `onPageSelect` it is a read-out and carries nothing: the
[`Carousel`](carousel.md) it belongs to is what announces the page.

`PageIndicatorStyle.Worm` animates the pill between dots and changes nothing
about what is announced. Under reduced motion it stops travelling.

---

← [Display and content](display.md) · [All components](../components.md)
