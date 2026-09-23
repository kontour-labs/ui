# `WindowSizeClass`

*Also on this page: `WindowWidthClass`, `WindowHeightClass`, `WindowSizeClassProvider`, `WindowAdaptiveInfo`.*

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
`Compact` on a desktop — which is how the catalog's adaptive demos sit in a frame
whose edge you drag, and change shape at the breakpoints as you do.

**The dp values are outside `equals`.** `widthDp` and `heightDp` are there to
read, but two classes in the same buckets are equal whatever their exact size, so
a window resized within one class does not recompose everything under it. Read
the buckets to decide a layout; measure the box yourself if you need its pixels.

**`windowAdaptiveInfo` adds the input to the size**, because the decisions are
rarely about one alone. `isPrecise` is true where there is a pointer to aim with,
and it governs what is cheap for a mouse and awkward for a thumb — the pane
scaffold's resize handle is 12dp under a mouse and a full touch target without
one. It does not govern *whether* there are two panes: the input is learned from
the first pointer event and assumed to be touch until then, so a layout that
waited on it would open every desktop window on one pane and reflow at the first
mouse movement.

---

## Accessibility

Nothing here is announced, and that is the point worth stating: the size class
decides *layout*, and layout must not change what is reachable. A control that
exists on a desktop and vanishes on a phone is a control some users can never
reach, whatever the reason for hiding it.

The class describes the **container**, not the device. A pane 380dp wide inside a
1400dp window is `Compact`, and a layout that asked the window would put a
two-column grid into it — which at 200% text is a column of single words.
