# `Modifier.fadingEdges`

**It erases rather than painting over.** `BlendMode.DstOut` in an offscreen
layer, not a gradient of the background colour — the shortcut version fails the
moment anything is behind the list, which over a map is always.

<!--sample:FadingEdgesBasics-->
```kotlin
val scroll = rememberScrollState()

// A hard edge at the top of a scrolling list reads as the end of the
// content. The fade says there is more, and it appears only on the side
// that has any.
Column(
    Modifier
        .fadingEdges(scroll, orientation = Orientation.Vertical)
        .verticalScroll(scroll),
) {
    Text("Departures")
}
```

---

← [Collections](collections.md) · [All components](../components.md)
