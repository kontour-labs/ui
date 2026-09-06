# `Scrollbar`

A position indicator you can also drag, and hidden from the accessibility tree
since it conveys nothing the list does not already.

<!--sample:ScrollbarBasics-->
```kotlin
val scroll = rememberScrollState()

Box {
    Column(Modifier.fillMaxWidth().verticalScroll(scroll)) { Screen() }
    // It hides itself unless the pointer can hover, so it costs a touch user
    // nothing. `alwaysVisible` is for a pane where the scroll is the point.
    Scrollbar(state = scroll, modifier = Modifier.align(Alignment.CenterEnd))
}
```

**Its visibility follows input modality, not platform.** Under an input that can
hover it is drawn; under touch it is **not drawn at all**, and the component
returns before laying anything out. A permanent scrollbar on a touchscreen takes
space from the screens with least of it, and a finger has better ways to scroll.
On desktop and web the opposite holds: a long list with no scrollbar reads as
broken. Pass `alwaysVisible = true` to override.

Hovering it thickens the thumb and takes it to full opacity, so it is a target
before you have to aim at it.

**Dragging it scrolls the container**, which it used to refuse on the grounds
that a 6dp target is not draggable and widening it would make it compete with the
content. Both halves were about the wrong device: it is only ever drawn where
there is a pointer, 6dp is what every scrollbar on such a machine measures, and
the drag is on the whole bar rather than on the thumb, so there is nothing to
widen. It scrolls raw rather than flinging — a scrollbar is a position control,
and letting go of one should leave the list where the thumb is.

The full modality table is in
[`accessibility.md`](../accessibility.md#input-modality).

---

## Accessibility

The scrollbar **clears its own semantics** entirely. It is a read-out of a
position that the scrolling container already reports, and a second node
announcing the same thing is noise.

Scrolling itself is reachable through the container. The scrollbar is a pointer
affordance, and it hides unless the input can hover, which is why it costs a
touch user nothing.

`alwaysVisible` makes it permanent. That is a visual decision — it does not make
anything reachable that was not.
