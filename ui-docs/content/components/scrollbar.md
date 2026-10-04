# `Scrollbar`

*Also on this page: `Modifier.scrollbar`.*

A position indicator you can also drag, and hidden from the accessibility tree
since it conveys nothing the list does not already.

<!--sample:ScrollbarBasics-->
```kotlin
val scroll = rememberScrollState()
// On the scroller itself, before its scroll. Under touch it appears while
// the content moves and fades after; with a mouse or a keyboard it is there
// all along, and can be dragged.
Column(Modifier.fillMaxWidth().scrollbar(scroll).verticalScroll(scroll)) { Screen() }

// Any scroller foundation ships — a lazy list, a grid, a pager — across or
// down: a row's runs along its foot.
val row = rememberLazyListState()
LazyRow(Modifier.scrollbar(row, Orientation.Horizontal), state = row) {
    items(stops) { Text(it.name) }
}
```

**Put it on the scroller.** `Modifier.scrollbar(state)` goes on the scrolling
container itself — a `Column` before its `verticalScroll`, a `LazyColumn`, a
`LazyVerticalGrid`, a `LazyRow` with `Orientation.Horizontal` — and draws along
its trailing edge, or its foot, with nothing to wrap it in and nothing to align.
Before the `verticalScroll` or `horizontalScroll` in the chain, not after, or it
scrolls away with the content; and before `fadingEdges`, or the fade takes it
too. `Scrollbar(state)` is the same bar as a component of its own, for a bar that
is not on the scroller's edge.

It reads any scroller foundation ships — `ScrollState`, lazy lists, grids and
staggered grids, pagers — from the position they report; a grid's used to draw
nothing at all.

**How it shows follows the input, not the platform.**

| | Touch, stylus | Mouse, keyboard |
|---|---|---|
| At rest | not drawn | drawn, half-faded |
| While scrolling | drawn, fading out 600ms after it stops | drawn |
| Dragged | no — the scroller's own | yes |

Under touch it is an indicator, as every phone's is: thinner, up while the
content moves to say where it is and how much there is, and gone once it has
settled. It takes no touches, so a strip at the edge of a list is still the
list's. With a mouse or a keyboard it is the bar every other window on the
machine has, there all the time: a long list with none reads as broken.
`alwaysVisible = true` draws it at rest under touch too, for a scroller whose
length is the point.

Hovering it thickens the thumb and takes it to full opacity, so it is a target
before you have to aim at it.

**Dragging it scrolls the container**, at the rate the track's length says:
grab it anywhere and the content follows rather than jumping. It scrolls raw
rather than flinging — a scrollbar is a position control, and letting go of one
should leave the content where the thumb is.

The full modality table is in
[`accessibility.md`](../accessibility.md#input-modality).

---

## Accessibility

The scrollbar **clears its own semantics** entirely. It is a read-out of a
position that the scrolling container already reports, and a second node
announcing the same thing is noise.

Scrolling itself is reachable through the container. The scrollbar is a pointer
affordance: under touch it only indicates, and takes no touches, so it costs a
touch user nothing.

`alwaysVisible` makes it permanent. That is a visual decision — it does not make
anything reachable that was not.

At high contrast an idle thumb is drawn at full strength instead of half-faded,
so the thumb a reader has to find is never the faint one. Its width is the
scrollbar's own and does not change with the tier.

Reduced motion changes nothing here: the thumb follows the scroll or the drag
the reader is making, which is never touched, and its widening under the pointer
and its fading in and out under touch are the same short tweens at every
setting — a change of opacity, not a movement.
