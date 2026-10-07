# `Scrollbar`

*Also on this page: `Modifier.scrollbar`.*

A position indicator you can also drag, and hidden from the accessibility tree
since it conveys nothing the list does not already.

<!--sample:ScrollbarBasics-->
```kotlin
val scroll = rememberScrollState()
// On the scroller itself, before its scroll. Under touch it appears while
// the content moves and fades after; with a mouse or a keyboard it is there
// all along. Either way it can be dragged while it is there.
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

**A lazy list's length is from every row it has measured**, not only the rows on
screen. A lazy list knows the size of what it has laid out and nothing else, so
the rows not yet seen are taken at the average of all those that have been. The
estimate improves as the list is scrolled, and once every row has been seen it
is the list's true length. Over a list of short headers and tall cards the thumb
keeps its length, where it used to grow over the headers and shrink over the
cards.

**The thumb never jumps.** It moves only as the content moves, and by the share
of the way left that the content covered, so it reaches the end of its track
exactly as the content reaches its end, and the top as the content reaches its
top, however wrong the estimate of the length in between was. When the estimate
changes, as rows not seen before come into view, its length eases to the new
one over a moment rather than stepping to it. While a finger or the mouse holds
the thumb, it stays under the pointer, its length holds still, and taking it to
the end of its track takes the content to its very end.

A list with an [`IndexScroller`](index-scroller.md) down its edge has no
scrollbar: the index is its position control, and the bar draws nothing while
the index is there.

**How it shows follows the input, not the platform.**

| | Touch, stylus | Mouse, keyboard |
|---|---|---|
| At rest | not drawn | drawn, half-faded |
| While scrolling | drawn, fading out 600ms after it stops | drawn |
| Dragged | by its thumb, while it is drawn | anywhere along it |

Under touch it is an indicator, as every phone's is: thinner, up while the
content moves to say where it is and how much there is, and gone once it has
settled. **While it is drawn, a finger on its thumb takes hold of it** — the
thumb thickens under the finger to say so, and a list still moving stops, as it
would for a finger on the list. Only the thumb and a fingertip either side of
it: the rest of the strip at the edge of a list is still the list's, for a tap
on a row or a scroll, and so is all of it while the bar is not drawn. It used to
take no touches at all, so a bar on a phone could be seen and not moved —
*"if the scrollbar is visible (even on mobile), then dragging it should do what
you expect"*. With a mouse or a keyboard it is the bar every other window on the
machine has, there all the time: a long list with none reads as broken.
`alwaysVisible = true` draws it at rest under touch too, for a scroller whose
length is the point.

Hovering it thickens the thumb and takes it to full opacity, so it is a target
before you have to aim at it.

**Dragging it scrolls the container**, at the rate the track's length says:
take hold of it and the content follows rather than jumping. It scrolls raw
rather than flinging — a scrollbar is a position control, and letting go of one
should leave the content where the thumb is.

The full modality table is in
[`accessibility.md`](../accessibility.md#input-modality).

---

## Accessibility

The scrollbar **clears its own semantics** entirely. It is a read-out of a
position that the scrolling container already reports, and a second node
announcing the same thing is noise.

Scrolling itself is reachable through the container. The scrollbar is a
shortcut for a hand: under touch it takes only a finger on a thumb that is
drawn, so it costs a touch user nothing they had.

`alwaysVisible` makes it permanent. That is a visual decision — it does not make
anything reachable that was not.

At high contrast an idle thumb is drawn at full strength instead of half-faded,
so the thumb a reader has to find is never the faint one. Its width is the
scrollbar's own and does not change with the tier.

Reduced motion changes nothing here: the thumb follows the scroll or the drag
the reader is making, which is never touched, and its widening under the pointer
or the finger and its fading in and out under touch are the same short tweens at every
setting — a change of opacity, not a movement.
