# `ReorderableItem`

![ReorderableItem, at rest](../../../ui-catalog/screenshots/components/reorderableitem-light.png)
![ReorderableItem, lifted mid-drag](../../../ui-catalog/screenshots/components/reorderableitem-dragging-light.png)

Drag to reorder, with `rememberReorderableState`.

The lift — shadow, scale, offset — is the whole of the feedback, and none of it
exists at rest. `ReorderableState.start(index)`, `drag(delta)` and `stop()` are
public so a drag can be begun without one: for a keyboard affordance, for a test,
and for the second picture above.

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

---

← [Collections](collections.md) · [All components](../components.md)
