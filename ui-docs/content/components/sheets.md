# Sheets

Panels that come in from an edge and rest at positions you name.

| | For | Instead of |
|---|---|---|
| [`BottomSheet`](bottom-sheet.md) | Content that shares the screen with what is behind it | A `ModalBottomSheet`, when the map underneath is still the point |
| [`ModalBottomSheet`](modal-bottom-sheet.md) | A task that owns the screen until it is done | A `Dialog`, on a phone, where a sheet is easier to reach |
| [`SideSheet`](side-sheet.md) | A rail beside a page that stays usable — filters, an inspector | A `ModalSideSheet`, when the page beside it is still the point |
| [`ModalSideSheet`](modal-side-sheet.md) | Filters and detail over the content on a wide window, until they are done | A `ModalNavRail`, which is for destinations |
| [`SheetHeader`](sheet-header.md) | The title row every sheet needs | Rebuilding the title, actions and handle per sheet |
| [`DragHandle`](drag-handle.md) | The grab bar at the top of a sheet | — |

`DragHandle` is a row affordance as much as a sheet one, and it is
[drawn rather than draggable](#draghandle-is-drawn-not-draggable) — the sheet
under it owns the gesture.

Everything below is the model the six of them share: where a sheet is allowed
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
        SheetHeader(modifier = Modifier.sheetPeekAnchor()) { +"Perth Underground" }
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

**And any of them can reach the top of the window.** A sheet at `Full`, or at
`Expanded` with content taller than the window, has its top edge on the window's,
under the status bar, with its top corners the display's own. Its drag handle and
header are padded below the status bar; the sheet itself is not moved down. It
used to stop 12dp short, with a strip of page showing above it. A floating sheet
that does not become an edge sheet keeps its margin at the top as everywhere else.

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
    SheetHeader(modifier = Modifier.sheetPeekAnchor()) { +"Perth Underground" }
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

## An empty detent list

`detents` must not be empty, and this is worth its own heading because the model
above actively recommends filtering: which positions apply depends on what is in
the sheet, and `all.filter { … }` can match nothing.

Both routes to an empty list now refuse with the same message. Passing one to
`rememberSheetState` used to fail with `NoSuchElementException: List is empty` —
because `initialDetent` defaults to `detents.first()`, and a default argument is
evaluated at the call site, before the friendlier check inside `SheetState` could
run. Assigning one to `state.detents` afterwards used to succeed, and took the
frame down some frames later inside the drag handle, where the header reads
`detents.last()`.

Keep `SheetDetent.Hidden` in the list if nothing else applies.

## Nested scrolling

A `LazyColumn` inside a sheet works without ceremony:

- Dragging **down** scrolls the list until it reaches its top, then moves the
  sheet.
- Dragging **up** moves the sheet until it is expanded, then scrolls the list.

That handoff is why a sheet takes its content as a slot rather than being a
modifier. Without it, a sheet with a list inside is either undraggable or
unscrollable, depending on which modifier won.

**Inside a draggable sheet, the sheet owns the vertical rubber band.** A list and
the sheet it is in share one axis, and only one of them can answer a finger that
has run out of list — so a scrollable in a sheet is given no vertical overscroll
of its own, and the pixels it declines reach the sheet. Sideways is untouched: a
sheet has no opinion about horizontal scroll, so a carousel in one keeps whatever
bounce the platform gives it.

This was reported from an iPhone, as two symptoms of one cause — a flick that had
to be thrown harder than on Android, and a downward drag that *sometimes* pulled
the list's content down instead of moving the sheet. Each platform installs a
different overscroll effect on a scrollable, and that effect wraps the whole
scroll: one with a band already open takes the finger *before* the sheet is
offered anything, and it is handed the release as well, where it can keep velocity
for its own spring. Desktop has no such effect at all, which is why desktop was
the behaviour that read as correct. None of the platforms' rubber-band curves
changed; what changed is whose gesture a declined vertical drag is.

A sheet with `draggable = false` is the exception, and for the reason the
nested-scroll connection has the same exception: nothing would answer a list that
has reached its top, and a list that stops dead is a boundary the finger cannot
feel.

**One flick closes it.** A release lands where the throw would have carried
the sheet had it kept going for half a second — what iOS projects a release by —
at the nearest detent to that, so a hard throw goes past a middle detent and
closes the sheet in one, where it used to take one flick per detent. A throw of
the sheet itself, by its handle or its surface, counts from 125dp/s and always
moves it at least one detent the way it was thrown.

**A finger rolling off the glass is not a throw.** A release only counts as one
if the finger's last run in one direction was at least 14dp, a little past touch
slop. A finger that leaves with a few pixels of travel at a few hundred pixels a
second would otherwise move a full-height sheet a detent. The run starts again
where the finger turns, so a roll-off after pulling a sheet up against its top
is judged on the roll-off alone.

**A sheet being pulled when the finger lets go owns the release**, as a sheet on
an iPhone does. If the content has handed the drag to the sheet, the release
belongs to the sheet: it carries on at the finger's speed, and it moves at least
one detent the way it was thrown. That holds even when the gesture scrolled the
list up to its top first. A release used to stop the sheet dead for a few frames
and then ease it away from rest. A pull that followed a scroll was also read as
the list's, and sprang back.

**A flick is read off the finger when the platform loses it.** On iOS, Compose's
velocity tracker reports no speed at all for a thumb laid on the sheet, rested a
moment, and flicked — it takes the rest for the finger stopping — and the same for
a throw across a slow frame. So when a release reports exactly nothing but the
finger was still moving as it lifted, the sheet reads the throw off the last tenth
of a second of the finger's own path. A finger that stopped before lifting still
settles by position.

**A flick from the content has to be firmer.** Below 300dp/s, a release settles
by position. A fling the list spent on itself does not move the sheet at all: a
reader who scrolled the list and let go was moving the list, and its leftovers
are not a request. The floor was 500dp/s, and closing a sheet was reported as
taking too much effort; both numbers are for tuning on a device.

**A sheet thrown away gives the page back as it goes.** The scrim stops taking
taps, and the page is readable again, from the moment the sheet is let go on its
way out, not once it has landed. If the sheet is caught again, or put back
because `onDismissRequest` declined, it holds the page again.

A sheet that may not be dismissed by hand is never flicked shut, however hard it
is thrown: its lowest resting place is the floor a drag already stops at.

**Content taller than the window scrolls rather than being cropped.** The sheet
measures its content at the room it actually has, so a long `Column` wrapped in
`verticalScroll` has a real viewport and the part below the fold is reachable.
Content that fits is unaffected — `SheetDetent.Expanded` still means "as tall as
the content".

**The room is the tallest the sheet can be.** A sheet whose detents are all fixed
sizes — a bar and `Half`, say — measures its content at the tallest of them rather
than at the window, so a list in a sheet that never goes above half height still
reaches its last row. It used to be measured a window tall with only half of that
ever on screen.

This used to be the other way round: the content was measured at an unbounded
height and then placed in the room the sheet had, so anything past the window's
bottom edge simply was not drawn — and adding a `verticalScroll` to fix it threw,
because Compose refuses a scroller measured at an infinite height. If you carried
a workaround for that — a fixed `heightIn` on the content, say — you no longer
need it.

**The gesture bar is handed to the content rather than reserved from it.** The
content lambda takes a `PaddingValues` carrying the bottom inset — the gesture
bar, or the keyboard when it is up — and the sheet applies none of it:

```kotlin
BottomSheet(sheet) { safeArea ->
    LazyColumn(contentPadding = safeArea) { … }        // scrolls through the bar
    Column(Modifier.padding(safeArea)) { … }           // sits above it
}
```

The sheet used to pad its content column itself, which is the first of those two
for everybody — so a list inside ended its viewport a gesture bar above the
window and its last row stopped short of the band rather than travelling through
it and coming to rest clear. Reported as content being cut off at the safe zone.
Only the caller knows whether the number belongs outside a scroller or inside
one, and applying it to the wrong one clips the scroll; `Scaffold` has handed its
own padding over for the same reason since it was written.

The other three sides are still applied for you, and a floating sheet hands out
zero — its margin has already cleared the inset. **Content written before this
existed now draws to the bottom of the window**, which is the behaviour change:
read the value and put it where it belongs.

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
button that contradicts it. The tap, the drag and the spring back are all
checked.
