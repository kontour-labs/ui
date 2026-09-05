# Sheets

Panels that come in from an edge and rest at positions you name.

| | For | Instead of |
|---|---|---|
| [`BottomSheet`](bottom-sheet.md) | Content that shares the screen with what is behind it | A `ModalBottomSheet`, when the map underneath is still the point |
| [`ModalBottomSheet`](modal-bottom-sheet.md) | A task that owns the screen until it is done | A `Dialog`, on a phone, where a sheet is easier to reach |
| [`SideSheet`](side-sheet.md) | Filters and detail beside the content on a wide window | A `ModalNavDrawer`, which is for destinations |
| [`SheetHeader`](sheet-header.md) | The title row every sheet needs | Rebuilding the title, actions and handle per sheet |
| [`DragHandle`](drag-handle.md) | The grab bar at the top of a sheet | — |

`DragHandle` is a row affordance as much as a sheet one, and it is
[drawn rather than draggable](#draghandle-is-drawn-not-draggable) — the sheet
under it owns the gesture.

Everything below is the model the five of them share: where a sheet is allowed
to stop, and why those positions are values rather than an enum.

---

## The model

```kotlin
val sheet = rememberSheetState(
    detents = listOf(
        SheetDetent.Hidden,
        SheetDetent.peek(140.dp),
        SheetDetent.Half,
        SheetDetent.Expanded,
    ),
    initialDetent = SheetDetent.Hidden,
)

Box(Modifier.fillMaxSize()) {
    Map(contentPadding = PaddingValues(bottom = with(density) { sheet.visibleHeight.toDp() }))

    BottomSheet(sheet) {
        SheetHeader("Perth Underground", modifier = Modifier.sheetPeekAnchor())
        LazyColumn { … }
    }
}
```

Built on foundation's `AnchoredDraggableState`, which already handles the drag,
the fling and the settle. What `SheetState` adds is the part that is actually
specific to sheets: turning detents into anchor positions as the container and
content resize, and handing scroll off between the sheet and whatever scrolls
inside it.

### Detents are values, not an enum

```kotlin
class SheetDetent(
    val id: String,
    val resolve: Density.(containerHeight: Float, sheetHeight: Float) -> Float,
)
```

A detent says **how much of the sheet is visible**, not where its top edge is.
That is how the positions are actually thought about — "showing 360dp", "showing
just the header", "showing all of it" — and `SheetState` converts to offsets
internally.

| | |
|---|---|
| `SheetDetent.Hidden` | Off screen |
| `SheetDetent.Expanded` | As tall as its *content*, or the container — whichever is smaller |
| `SheetDetent.Half` | Half the container |
| `SheetDetent.peek(fallback)` | As tall as whatever the content marked with `Modifier.sheetPeekAnchor()` |
| `SheetDetent.height(id, dp)` | A fixed height |
| `SheetDetent.fraction(id, f)` | A fraction of the container |

Being values rather than an enum is what lets a screen define its own. The map's
stop sheet rests at the height of its own header; the trip planner rests at
360dp. Neither is a case the library could have enumerated in advance.

**Identity is the `id`, not the lambda.** Two `peek(140.dp)` calls make two
objects with two different resolvers, and if they were not equal, a recomposition
that rebuilt the detent list would re-anchor and snap the sheet shut on every
frame.

**`Expanded` is the content's height, not the window's.** A sheet with three
rows in it should be three rows tall, not an empty full-screen panel with three
rows at the top.

### The detent list is reassignable

```kotlin
val sheet = rememberSheetState(
    detents = when (selection) {
        is Location -> listOf(SheetDetent.Hidden, peek)          // nothing to expand into
        is Itinerary -> listOf(SheetDetent.Hidden, half, expanded) // no peek; the header is the content
        else -> listOf(SheetDetent.Hidden, peek, half, expanded)
    },
)
```

Which detents apply depends on what is in the sheet. Filtering a list beats
defining a second sheet — and it is what the Android app already does today with
a `forEach` and two `return@forEach` guards.

When the list changes, the sheet keeps its position if that detent survives, and
otherwise falls to the *nearest surviving* one. Falling to the first in the list
would slam a half-open sheet shut every time its content changed underneath it.

Detents that resolve to the same offset are dropped, keeping the first. Two
anchors at one position make `settledValue` ambiguous and the sheet flickers
between two names for the same place — which happens easily, since content that
is exactly half the container makes `Half` and `Expanded` collide.

### `peek` is the one that matters

A sheet over a map has to rest showing exactly its header: enough to identify
what is selected, not enough to cover the thing it is about. A fixed peek height
cannot do that, because a stop header and a trip header are different heights and
both change again at 200% type.

```kotlin
BottomSheet(sheet) {
    SheetHeader("Perth Underground", modifier = Modifier.sheetPeekAnchor())
    LazyColumn { … }
}
```

The measurement is **the anchor's bottom edge relative to the top of the
sheet**, not the anchor's own height — anything above it counts too. The drag
handle is what makes the difference: a peek set to the header's height alone
shows the bottom of the sheet up to that height, which is the header minus the
handle, and the last line of the header is cut off.

It is also derived from two separately-stored measurements rather than computed
where the anchor reports. `onGloballyPositioned` fires children-first, so the
anchor reports before the sheet it is inside — and it only fires again when a
position actually changes, so computing the difference at the anchor's callback
finds the sheet's top still unset and never gets a second chance. That is how the
peek silently stayed at its fallback in the first version.

---

## The components

| | |
|---|---|
| `BottomSheet` | Non-modal, in the layout, over the content behind it |
| `ModalBottomSheet` | Renders into the `OverlayHost`; dims and blocks |
| `SideSheet` | Slides in from an edge, for wide windows |
| `SheetHeader` | Title, supporting line, actions, close |
| `DragHandle` | The grab bar, and the sheet's accessibility actions |

**`BottomSheet` is non-modal by design.** Nothing behind it is dimmed or
blocked, which is the whole point over a map: the user pans the map with the
sheet resting at its peek detent, and `sheet.visibleHeight` is what the map
insets its controls by so they stay above it.

**`ModalBottomSheet` takes over.** It goes through the overlay stack, so it
shares a scrim with dialogs and menus, back closes it, and dragging it shut calls
`onDismissRequest` — `visible` and the sheet cannot disagree about whether it is
open.

**`SideSheet` is the wide-window shape.** A bottom sheet on a desktop window is a
short letterbox across something very wide. It slides rather than dragging
through detents: a side sheet has one useful position, and a horizontal drag on a
wide screen usually meant something else.

### `DragHandle` is drawn, not draggable

The whole sheet is already draggable. A handle that is the *only* draggable part
makes a 4dp-tall target the user has to hit.

It does carry the sheet's accessibility actions, though, since a drag is not a
gesture a screen reader can perform: expand and collapse appear as actions on the
handle, which is why it is not marked decorative. It widens slightly on hover —
the one hint a pointer user gets that a sheet is draggable at all.

---

## Nested scrolling

A `LazyColumn` inside a sheet works without ceremony:

- Dragging **down** scrolls the list until it reaches its top, then moves the
  sheet.
- Dragging **up** moves the sheet until it is expanded, then scrolls the list.

That handoff is why a sheet takes its content as a slot rather than being a
modifier. Without it, a sheet with a list inside is either undraggable or
unscrollable, depending on which modifier won.

---

## Opening a sheet before it has been laid out

```kotlin
LaunchedEffect(Unit) { sheet.animateTo(SheetDetent.Half) }
```

This is the normal case — a screen that arrives with its sheet already open — and
it runs before the first layout, so there are no anchors to move to yet.
`SheetState` holds the request and applies it when anchors arrive, so callers do
not have to wait for a measurement they should not have to know about.

---

## Testing

Two suites, because sheets fail in two different ways: the arithmetic of where a
detent lands, and where the sheet is actually drawn. They can disagree, and when
they do the state is the half that looks right — both bugs found while building
sheets were of exactly that kind.

See [`building/testing.md`](../../../docs/building/testing.md#how-sheets-are-tested).

## Not draggable

`draggable = false` removes the drag gesture **and** the drag handle. A handle is
the only part of a sheet that says "pull me", so one that does nothing is a lie
about what the sheet will do.

It takes the nested-scroll connection with it. That connection exists to hand a
list's overscroll to the sheet, so leaving it behind would mean a flick at the
top of the content still closed a sheet that cannot be dragged.

The scrim follows the sheet's visible height, so a sheet that cannot be dragged
also cannot fade its scrim halfway — there is no halfway for it to be at.

`dismissible = false` is the other half, and it is a different question:
`draggable` is about whether the sheet *moves*, this is about whether the user can
get *out*. It closes every route — the tap outside, the back gesture, and a drag
to the bottom, which springs back instead of closing. A sheet can be
undismissable and still be dragged freely between its detents.

It was called `dismissOnOutside` and covered only the tap, which was the wrong
name for a surface with three ways out of it. The same parameter, under the same
name, is now on `Dialog`, `AlertDialog`, `SideSheet` and `CommandPalette` — the
palette had never had one.

Pair it with `onClose = null` on the `SheetHeader`, or the sheet grows a close
button that contradicts it. `SheetDraggableTest` covers the tap, the drag and the
spring back.
