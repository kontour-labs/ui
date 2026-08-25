# `WindowSizeClass`

*Also on this page: `WindowWidthClass`, `WindowHeightClass`, `WindowSizeClassProvider`.*

What size of window the content is in, in buckets rather than pixels.

<!--sample:WindowSizeClassBasics-->
```kotlin
// The class of the *container*, not of the device: a pane 380dp wide inside
// a 1400dp window is Compact, and a layout that asked the window would put
// a two-column grid in it.
if (windowSizeClass.width.hasRoomBeside) {
    Row { Screen() }
} else {
    Column { Screen() }
}

// Provide a fresh one wherever a subtree gets its own width.
WindowSizeClassProvider {
    if (windowSizeClass.width == WindowWidthClass.Compact) Screen() else Screen()
}
```

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
