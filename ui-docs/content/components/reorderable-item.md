# `ReorderableItem`

Drag to reorder, with `rememberReorderableState`.

The lift — shadow, scale, offset — is the whole of the feedback, and none of it
exists at rest. `ReorderableState.start(index)`, `drag(delta)` and `stop()` are
public so a drag can be begun without one: for a keyboard affordance, and for a
test that needs a row already lifted.

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

LazyColumn(state = listState) {
    itemsIndexed(stops, key = { _, stop -> stop.name }) { index, stop ->
        ReorderableItem(state = reorder, index = index, itemCount = stops.size) {
            ListItem { +stop.name }
        }
    }
}
```

**A `handleIcon` changes the gesture, not just the picture.** With one, the row
is dragged from the grip and the drag starts the moment the grip is pressed.
Without one the whole row is draggable, and on touch that has to wait for a long
press — because a row that moved as soon as a finger touched it could never be
scrolled past. `handleSide` puts the grip at either end; the trailing end by
default, since the leading end is usually where a row's own icon or avatar is.

The grip is decoration. Move up and move down are the accessible route either
way, so the glyph carries no description and adding one would only put a third,
drag-shaped path in front of a screen reader.

---

## Accessibility

Reordering is exposed as `CustomAccessibilityAction`s — move up and move down —
on each row, wired to the same state the drag uses. A list that could only be
reordered by dragging could not be reordered at all by a screen-reader user, and
this is what the actions exist for.

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
