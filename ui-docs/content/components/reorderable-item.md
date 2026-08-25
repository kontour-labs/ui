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

← [Collections](collections.md) · [All components](../components.md)
