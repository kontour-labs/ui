# `WindowSizeClass`

What size of window the content is in, in buckets rather than pixels.

*Also on this page: `WindowWidthClass`, `WindowHeightClass`.*

`WindowWidthClass` is `Compact` under 600dp, `Medium` under 840, `Expanded`
under 1200 and `Large` above it. Reading `windowSizeClass.width >= Medium` at a
call site works and is what most code does — but `hasRoomBeside` and
`hasRoomForTwoPanes` say the same thing in terms of the decision being made, and
a threshold written as a comparison is a threshold that drifts when somebody
moves it.

**It is the size of the container, not the device.** `WindowSizeClassProvider`
measures with `BoxWithConstraints`, so nesting one inside a 360dp box reports
`Compact` on a desktop — which is how the catalog draws three device frames side
by side and gets three different navigation surfaces.

---

← [Adaptive](adaptive.md) · [All components](../components.md)
