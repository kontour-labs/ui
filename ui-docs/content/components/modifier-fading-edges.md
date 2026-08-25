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

## Accessibility

Purely visual. The fade says there is more content in that direction, and it
appears only on the side that has any — which is information a sighted user gets
and nobody else does.

So it is not a substitute for the scroll container's own semantics, and it must
not be the only signal that a list continues. Where the fact matters, the list
should say how many items it has.

It draws with a blend mode over the content, and it adds no node to the
accessibility tree at all.

---

← [Collections](collections.md) · [All components](../components.md)
