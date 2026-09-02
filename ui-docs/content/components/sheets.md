# Sheets

Panels that come in from an edge. The detent model — where a sheet can rest, and
why those positions are values rather than an enum — is in
[`../sheets.md`](../sheets.md); the pages below are the components.

| | For | Instead of |
|---|---|---|
| [`BottomSheet`](bottom-sheet.md) | Content that shares the screen with what is behind it | A `ModalBottomSheet`, when the map underneath is still the point |
| [`ModalBottomSheet`](modal-bottom-sheet.md) | A task that owns the screen until it is done | A `Dialog`, on a phone, where a sheet is easier to reach |
| [`SideSheet`](side-sheet.md) | Filters and detail beside the content on a wide window | A `ModalNavDrawer`, which is for destinations |
| [`SheetHeader`](sheet-header.md) | The title row every sheet needs | Rebuilding the title, actions and handle per sheet |
| [`DragHandle`](drag-handle.md) | The grab bar at the top of a sheet | — |

`DragHandle` is a row affordance as much as a sheet one, and it is
[drawn rather than draggable](../sheets.md#draghandle-is-drawn-not-draggable) —
the sheet under it owns the gesture.

---

← [All components](../components.md)
