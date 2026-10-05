# Drag and drop

Anything can be carried, and anything can take it: a card from one column of a
board to another, a stop into a trip, a file onto a bin. Three parts, and a type
between them:

- **`DragType<T>`** names what is carried. A source and a target meet only on the
  same type, so a card cannot be dropped into a list of stops however it is held.
- **`Modifier.dragSource`** makes an element carry a payload, and
  **`Modifier.dropTarget`** with **`rememberDropTargetState`** takes one.
- **`rememberDropListState`**, **`Modifier.dropList`** and **`dragItems`** make a
  lazy list both: its items can be carried — within it or to another list — and
  it takes drops at a position, opening room where a card would land.

The thing being carried is drawn by the nearest
[`OverlayHost`](overlays.md), above everything in it, the overlays included, so a
card can be carried out of a sheet and onto the page. Without a host a source
cannot be dragged, and says so once in the log.

## Between lists

<!--sample:KanbanColumns-->
```kotlin
val todo = remember { mutableStateListOf(Task(1, "Timetable"), Task(2, "Fares")) }
val done = remember { mutableStateListOf(Task(3, "Maps")) }
val lists = remember { mutableMapOf<DropListState<*>, MutableList<Task>>() }

@Composable
fun Lane(title: String, tasks: MutableList<Task>, modifier: Modifier) {
    val scroll = rememberLazyListState()
    // One call per drop: what, where, and where it came from. The lists
    // are yours, and nothing in them moves until this moves it.
    val lane = rememberDropListState(
        listState = scroll,
        type = TaskType,
        items = tasks,
        key = { it.id },
        label = title,
        itemLabel = { it.title },
        onDrop = { drop ->
            lists.getValue(drop.fromList!!).removeAt(drop.fromIndex!!)
            tasks.add(drop.index, drop.payload)
        },
    )
    lists[lane] = tasks
    LazyColumn(state = scroll, modifier = modifier.fillMaxHeight().dropList(lane)) {
        dragItems(lane) { _, task ->
            ListItem {
                +task.title
                trailing { Icon(Tabler.Outline.GripVertical, null, Modifier.dragHandle()) }
            }
        }
    }
}

Row {
    Lane("To do", todo, Modifier.weight(1f))
    Lane("Done", done, Modifier.weight(1f))
}
```

**One call per drop, when it lands.** `onDrop` gets a `DropEvent`: the payload,
the `index` it ends at, and `fromList` and `fromIndex` for where it came from.
The `index` is counted **as if the card had already left its own list**, so a
move within one list is `removeAt(fromIndex)` then `add(index, …)`, and a move
between two is the remove from one and the add to the other. Nothing moves in the
caller's lists before then, which keeps cancelling, and undo, simple: a drag that
is called off has changed nothing. A card let go in its own place is not a drop,
and calls nothing.

**The list makes room as it goes.** The card's own place folds away as it is
picked up and a gap opens where it would land, measured from the card's centre;
the neighbours slide aside as the gap moves. A place, once taken, sticks: the
card has to pass the middle of the item beyond it, where that item now is, to
take the next one, so a hand held still does not make the list twitch.

**A press held on a card picks it up, or a mouse's drag.** A finger's drag is a
scroll until it has held still for a moment, as a reorderable row's is: the hold
forgives about a fingertip's wander. A press on the item's `dragHandle()` picks
it up at once, as does a mouse anywhere on it.

**A card held near the end of a list scrolls it**, faster the deeper into the
last 64dp it is, and building up over the first moments there, so passing through on the way somewhere does not throw the list.
A card picked up beside an edge does not scroll its list until it has been
somewhere else first. Another scrolling thing — the row of columns a board sits
in — takes `Modifier.dragAutoScroll`, and the innermost scrolls first.

## Any target

<!--sample:DropTargetTrash-->
```kotlin
val tasks = remember { mutableStateListOf(Task(1, "Timetable"), Task(2, "Fares")) }

// Anything can be carried, and anything can take it: a card here, a bin
// there, and the type between them.
val bin = rememberDropTargetState(TaskType, label = "Bin", onDrop = { tasks.remove(it) })
Row {
    for (task in tasks) {
        Card(Modifier.dragSource(TaskType, task, label = task.title)) { Text(task.title) }
    }
    Box(Modifier.dropTarget(bin)) {
        // Its status says when something it would take is over it.
        val over = bin.status == DropStatus.Over
        Icon(Tabler.Outline.Trash, bin.label, tint = if (over) Theme.colours.danger.solid else LocalContentColour.current)
    }
}
```

A target's `status` says where it stands: `Available` while something it would
take is carried elsewhere, `Over` while it is over it, `Refused` when `accepts`
turns it away, and `Idle` otherwise. Draw from it — a bin that opens, a column
that tints — so a reader can see where a card can go before they let go of it.
Let go over a refusal, or over nothing, and the card goes home.

Inside another target the innermost takes the drop; in an overlay it outranks the
page under it; and the page behind a dimmed modal takes nothing, as it takes no
taps.

## Accessibility

**Every move has a way without a pointer.** A screen reader is offered each move
as a named action on the item — *Move to Done*, and *Move up* and *Move down*
within its own list — which happens at once, with nothing carried. They are on
the item's `dragHandle()` when it has one, which merges into the row a reader
lands on, and on the item itself when it has none. A keyboard
picks a card up with Space or Enter on its handle, or Alt and an arrow on the card
itself; the arrows along the list move it a place, the arrows across move it to
the next target, Home and End to either end; Enter puts it down and Escape puts it
back. The keys are the host's while a card is carried, wherever the focus was, and
a card put down by keys takes the focus back in its new place.

**Each step is said**, through a polite live region the host keeps: "Picked up
Fares", "Done, 3 of 4", "Fares dropped in Done", "Fares put back". The words are
`Theme.strings` — `dragPickedUp`, `dragPosition`, `dragDropped`, `dragCancelled`,
`dragRefused`, `moveTo` and `pickUp` — so they translate with the rest.

**It is felt.** A hold that picks something up is a `LongPress`, each new place a
`Snap`, a landing `GestureEnd` and a refusal `Reject` — see the haptics policy in
[theming](theming.md#physical-feedback). A row put straight back has landed
nowhere and plays nothing.

**The cursor says it.** A grip shows an open hand, a carried card a closed one,
and a card over a target that will not take it `NotAllowed`. At high contrast the
carried card has an edge, so it stands off whatever it is over. Under reduced
motion the card does not fly to its place or home, and the gap opens at once.

## Reorderable lists

`ReorderableItem` is still the simpler thing for a list whose rows only move
within it, and it reorders live under the finger rather than at the drop. For
anything that crosses from one list to another, use a `dropList`.
