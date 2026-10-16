# `ReorderableItem`

Drag to reorder, with `rememberReorderableState`.

For a row that has to leave its list — a card between a board's columns, a stop
into another trip — use [drag and drop](../drag-and-drop.md): `dragItems` in a
`dropList`. `ReorderableItem` stays the simpler thing for a list whose rows only
move within it, and it reorders live under the finger rather than at the drop.

The lift — shadow, scale, offset — is the whole of the feedback, and none of it
exists at rest. `ReorderableState.start(index)`, `drag(delta)` and `stop()` are
public so a drag can be begun without one: for a keyboard affordance, and for a
test that needs a row already lifted.

**The shadow belongs to the card, not to the row.** With a `handleIcon` those are
two different widths — the grip takes its own column and the content has what is
left — and the lift used to be cast by the row, so a picked-up row drew a shadow
tracing a rectangle wider than the card anybody could see, with an unpainted
strip beside the handle. Reported twice: once as the gap, and again after the gap
was filled with the surface colour, which on a light page is the same white as
the page, so the fix changed nothing you could see. The lift is measured from the
content's own box now: the shadow hugs the card, and the grip sits beside it on
the list rather than inside it. The **scale and the offset stay on the row**, so
the handle still travels and grows with what it is holding.

**Reordering happens live, under the finger.** `onMove` fires every time the
dragged row passes another, so the caller's list stays the source of truth
throughout and there is no pending order to reconcile on release.

<!--sample:ReorderableList-->
```kotlin
var stops by remember { mutableStateOf(initial) }
val listState = rememberLazyListState()
val reorder = rememberReorderableState(listState) { from, to ->
    stops = stops.toMutableList().apply { add(to, removeAt(from)) }
}

// One group: a list item's gap between the rows, each in its place.
LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing)) {
    itemsIndexed(stops, key = { _, stop -> stop.name }) { index, stop ->
        ReorderableItem(state = reorder, index = index, itemCount = stops.size) {
            ListItem(position = GroupPosition.of(index, stops.size)) { +stop.name }
        }
    }
}
```

**`index` is the row's index in the `LazyColumn`**, and it is what `onMove` is
given. In a list of rows and nothing else that is the row's index in your
collection. With a header item above the rows it is one more, and `onMove` takes
the header off both before it moves anything. An item that is not a
`ReorderableItem` — a header, a footer, a "load more" — is never where a drag
lands: a row carried over one stays where it was. It used to land there, and the
last row carried over a footer reported a move to one past the end, which the
`add(to, removeAt(from))` above throws on.

**A `handleIcon` adds a gesture rather than replacing one.** The grip picks the
row up the moment it is pressed, because a grip has no scroll to steal. **The
row stays draggable too**, on a long press, exactly as it is without a handle —
a row that moved as soon as a finger touched it could never be scrolled past,
and the hold is what keeps a row that is also a link safe.

It did not used to. Turning the grip on took the whole row's gesture away with
it, on the reasoning that a handle is what you asked for — which is not how any
platform's own editable list behaves, and not what a reader reaches for first.

`handleSide` puts the grip at either end; the trailing end by default, since the
leading end is usually where a row's own icon or avatar is.

**Handles animate in and out from their own side**, and the row's content narrows
with them. Turning them on used to be a layout jump — the content went from
filling the row to being a weighted sibling of a grip between two frames, so a
row of text reflowed with no warning. The grip expands out of the edge it lives
on, and because the width being animated is the grip's own, the card's remainder
is animated by the same curve rather than by a second one kept in step with it.
Moving `handleSide` across animates the old grip out and the new one in. Under
reduced motion the grip fades rather than growing out of the edge, and the rows
a reorder moves take their new places at once instead of sliding into the gap.

The grip is decoration. Move up and move down are the accessible route either
way, so the glyph carries no description and adding one would only put a third,
drag-shaped path in front of a screen reader.

---

## Accessibility

Reordering is exposed as `CustomAccessibilityAction`s — move up and move down —
on each row, wired to the same state the drag uses. A list that could only be
reordered by dragging could not be reordered at all by a screen-reader user, and
this is what the actions exist for. A row with `enabled = false` offers neither,
as it offers no drag.

`moveUpLabel` and `moveDownLabel` are what those actions announce. The defaults
are generic; where the rows are ordered stops or ranked preferences, saying so
reads better.

Long-press-to-drag is a gesture and stays one. The actions are the equivalent,
not a fallback.

**The hold tolerates a finger.** Compose's own long press gives up the moment
the pointer leaves `touchSlop`, which is the threshold for "this is a scroll" and
the wrong question to ask of a finger that has not gone anywhere yet — a
fingertip is a centimetre wide and rolls as it presses. The row allows a
fingertip's worth of wander during the hold instead, which is well inside what
any gesture that meant to scroll would have crossed by then. Reported from a
phone browser, where frames are slower and holding still is a skill.

High contrast changes nothing here: a lifted row is marked by its shadow and a
2% lift at both tiers, and the row it wraps takes the tier as it would anywhere.

Under [reduced motion](../tokens.md#motion) the rows a reorder displaces take
their new places at once instead of sliding, including after a move made through
the actions, and a grip turned on or off fades where it sits. The row under the
finger follows it at every setting.
