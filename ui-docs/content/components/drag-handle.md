# `DragHandle`

![DragHandle](../../../ui-catalog/screenshots/components/draghandle-light.png)

The grab bar at the top of a sheet.

<!--sample:DragHandleBasics-->
```kotlin
val sheet = rememberSheetState(
    detents = listOf(SheetDetent.peek(140.dp), SheetDetent.Expanded),
    initialDetent = SheetDetent.peek(140.dp),
)

// Every sheet draws one already. Pass `dragHandle` only to replace it, or
// `null` to take it away — a sheet with `draggable = false` should not
// advertise a gesture it does not have.
BottomSheet(state = sheet, dragHandle = { DragHandle(state = sheet) }) {
    SheetHeader(modifier = Modifier.sheetPeekAnchor()) { +"Perth Underground" }
    Departures()
}
```

**It is drawn, not draggable.** The whole sheet is already draggable, and a
handle that is the *only* draggable part makes a 4dp-tall target the user has to
hit.

It does carry the sheet's accessibility actions, though, since a drag is not a
gesture a screen reader can perform: expand and collapse appear as actions on the
handle, which is why it is not marked decorative. It widens slightly on hover —
the one hint a pointer user gets that a sheet is draggable at all.

`BottomSheet` and `ModalBottomSheet` draw one by default and take a `dragHandle`
slot to replace or remove it. [Sheets](../sheets.md) is where the detents, the
nested scrolling and the rest of the sheet story live.

---

← [Collections](collections.md) · [All components](../components.md)
