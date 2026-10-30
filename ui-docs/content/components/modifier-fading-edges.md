# `Modifier.fadingEdges`

**It erases rather than painting over.** `BlendMode.DstOut` in a layer of its
own, not a gradient of the background colour — the shortcut version fails the
moment anything is behind the list, which over a map is always. The layer
covers only the fade strips, and only while there is something past that edge;
the list between them is drawn as it would be with no fade at all, so a long
list costs a slow phone no more to scroll for having one.

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

At high contrast there is no fade: nothing that is text fades at that tier, and
the list's own edge and its scrollbar still say there is more.

The fade follows the scroll position and has no animation of its own, so
reduced motion changes nothing about it.
