# `Modifier.pointerCursor`

*Also on this page: `Cursor`.*

**Named for what the pointer is over, not for what it looks like.** A component
asks for `Cursor.ResizeColumn` because it is a divider between two columns, and
each platform draws whatever it has for that: an east-west arrow on a desktop, a
horizontal double arrow on an Android tablet with a mouse. The component does not
have to know which, and the list of ten is the part that grows.

Every clickable component in the library already sets one, so this is for what
you build yourself — a splitter, a canvas, a card with its own gesture.

<!--sample:PointerCursors-->
```kotlin
var listWidth by remember { mutableStateOf(280.dp) }
val density = LocalDensity.current

Row(Modifier.fillMaxWidth().height(200.dp)) {
    Box(Modifier.width(listWidth).fillMaxHeight()) { Screen() }
    // Named for what the pointer is over, not for a picture. A desktop
    // draws a sideways double arrow here, an Android tablet its own, and a
    // platform with neither falls back to the hand rather than to nothing.
    Box(
        Modifier
            .width(8.dp)
            .fillMaxHeight()
            .pointerCursor(Cursor.ResizeColumn)
            .draggable(
                state = rememberDraggableState { delta ->
                    listWidth += with(density) { delta.toDp() }
                },
                orientation = Orientation.Horizontal,
            ),
    )
    Box(Modifier.weight(1f).fillMaxHeight()) { Screen() }
}
```

---

## The ten

| `Cursor` | For | Used by |
|---|---|---|
| `Default` | The arrow. A disabled control, or a child keeping an enclosing hand out | Every disabled control |
| `Pointer` | Anything that answers a click. What `pointerCursor()` gives with no argument | Every clickable component |
| `Text` | Text that can be typed into or selected | `TextField` |
| `Crosshair` | Picking a point rather than a thing | — |
| `ResizeColumn` | A divider dragged sideways | The splitter in `ListDetailPaneScaffold` |
| `ResizeRow` | A divider dragged up and down | A sheet's `DragHandle` |
| `Grab` | Something that can be picked up and moved | `Scrollbar`, a `ReorderableItem`'s grip |
| `Grabbing` | The same thing, while it is held | Both of those, mid-drag |
| `NotAllowed` | Refused here — a drop target that will not take what is over it | — |
| `Progress` | Working in the background, with the pointer still usable | — |

`Crosshair`, `NotAllowed` and `Progress` are there for what you build: nothing in
the library needs them, and each is the other half of a question the rest raise.

---

## What each platform draws

| | Desktop | Android | Web | iOS |
|---|---|---|---|---|
| `ResizeColumn` | east-west arrow | horizontal double arrow | `col-resize` | — |
| `ResizeRow` | north-south arrow | vertical double arrow | `row-resize` | — |
| `Grab` | *hand* | grab | `grab` | — |
| `Grabbing` | four-way move | grabbing | `grabbing` | — |
| `NotAllowed` | *arrow* | no-drop | `not-allowed` | — |
| `Progress` | *arrow* | *arrow* | `progress` | — |

The other four are the same on every platform. Italics are the nearest of those
four, standing in where a platform has nothing that means the same thing.

**The web draws every one**, by its CSS keyword, through Compose's
`PointerIcon.fromKeyword`. That is marked experimental in Compose, and the library
takes the opt-in in one file so an app does not have to.

**Anything dragged falls back to the hand, never the arrow**, where a platform
has no shape for it — the desktop's grab. The hand is imprecise — it says *click*,
and these are dragged — but it says the thing does something, and it is what all
four showed before they had shapes of their own.

**`Progress` is never the busy cursor.** The ones AWT and Android offer mean *the
application has stopped responding*, which is an untrue thing to say about a
400ms spinner, so `Progress` shows the arrow on both. iOS has no cursor backend at
all, and a pointer there keeps the system's own shape.

---

## Disabled shows the arrow

Pass the component's own `enabled` through, and a disabled control sets the arrow
— sets it, rather than setting nothing. Nothing is not neutral: Compose shows the
cursor of the deepest element under the pointer that has one, so a disabled button
that asked for none, inside a clickable card, showed the card's hand and promised
a click it would not answer.

It is the arrow and not `NotAllowed` on purpose. A Save button waiting for a form
to be finished is inert, not forbidden, and a no-entry sign on it tells the reader
they have done something wrong.

---

## While something is held

A cursor belongs to a *drag*, not to whatever the pointer happens to be over — on
every desktop's own drag-and-drop it stays put until the drop. Compose has no such
capture: a held pointer that leaves the element that set the cursor gets the arrow
back. So a lifted `ReorderableItem` puts `Grabbing` over its whole row and lets it
win over the row's content: the row follows the pointer up and down, but nothing
stops the pointer wandering sideways off a 24dp grip.

Something you build that is dragged by a small handle has the same problem, and a
worse one if its drag waits for a finger's slop under a mouse — the handle then
trails the pointer by 18dp on a desktop and is left behind at once. Two things
keep it away: detect the drag with Compose's own `draggable` or
`detectDragGestures`, or with the pointer-aware `awaitVerticalPointerSlopOrCancellation`,
all of which use a mouse's much smaller slop and so keep the handle under the
pointer; and set the cursor on the handle from the start, since Compose decides
which elements a held pointer reports to at the moment it goes down.

---

## Accessibility

A cursor is a hover affordance for a mouse, and nothing else. It is not announced,
it does not exist on a touchscreen, and a keyboard user never sees it — so it can
never be the only thing that says what an element does. A splitter still needs its
`setProgress` action for a screen reader, which `ListDetailPaneScaffold`'s has; a
refusal still needs to be said in words, not only shown as `NotAllowed`.

The modifier returns its receiver unchanged when the input cannot hover, so it
costs a touch user nothing.
