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

← [Display and content](display.md) · [All components](../components.md)
