# `Modifier.atmosphere`

A soft glow behind a hero or an onboarding screen, in the theme's own colours.

<!--sample:AtmosphereBasics-->
```kotlin
// Behind a hero, not behind every screen: a wash under each list is one
// nobody notices and everybody pays for.
Box(Modifier.fillMaxWidth().height(320.dp).atmosphere()) {
    Hero()
}
```

Two radial washes, one from the accent off the top corner and one from the brand
colour off the bottom corner, drawn behind whatever the node holds. They sit off
the corners rather than in the middle because a glow centred behind content puts
its brightest point exactly where the text is.

**It is opt-in, and never part of `Surface`.** It is for one or two screens that
introduce something — a landing page, the first run of an app. Put it behind
every list and nobody notices it, while every screen pays to draw it.

**It follows the theme.** The washes are the scheme's `accent.solid` and `brand`
at low strength, so they change with the scheme and in dark mode without a
second image. Pass `primary` and `secondary` for other colours, and `intensity`
to scale both: past `1f` the glow starts to compete with what is in front of it.

It draws behind the node's content, so give the node a size — a `Box` with no
height has nothing to glow in.

---

## Accessibility

The glow is decoration. It adds no semantics, and nothing is announced.

Text in front of it keeps most of its contrast: the washes are faint, and
strongest off the corners, away from where a hero's text sits. Check the
brightest point if you raise `intensity` or pass stronger colours.

At high contrast the washes are drawn from the enhanced scheme's accent and the
brand at the same low strength. Nothing else changes, and nothing is needed, as
the page's own colours carry the contrast.

Under reduced motion nothing changes: the glow does not move.
