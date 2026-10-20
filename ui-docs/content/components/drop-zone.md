# `DropZone`

A place to put things down: a bin, an upload area, the end of a column. It is a
[drop target](../drag-and-drop.md) that shows where a drag stands with it, laid
out as an [`EmptyState`](empty-state.md) is: an icon, a title, a line on what
happens, and an action.

<!--sample:DropZoneArchive-->
```kotlin
val tasks = remember { mutableStateListOf(Task(1, "Timetable"), Task(2, "Fares")) }
val archived = remember { mutableStateListOf<Task>() }

// A drop target, drawn: dashed at rest, the accent while a task is
// carried, its ground once one is over it.
val archive = rememberDropTargetState(TaskType, label = "Archive", onDrop = { task ->
    tasks.remove(task)
    archived.add(task)
})
Column {
    for (task in tasks) {
        Card(Modifier.dragSource(TaskType, task, label = task.title)) { Text(task.title) }
    }
    DropZone(archive) {
        leading { +Tabler.Outline.Archive }
        +"Archive"
        message { +"Drop a task here to put it away." }
    }
}
```

**It shows four things**, from the target's `status`:

| Status | What it looks like |
|---|---|
| `Idle` | A dashed edge, and no ground of its own. |
| `Available` | Something it would take is being carried elsewhere: the dashed edge turns the accent. |
| `Over` | That thing is over it and would be taken: a solid accent edge on the accent's ground. |
| `Refused` | The thing is over it, but the state's `accepts` turns it away: the same in the danger colour. Let go, it goes home. A `dropList` that refuses makes no room for it either: a full column's items stay where they are. |

Off (`enabled = false`), it takes nothing and is drawn as a disabled control is.

**What it takes is its state's business.** Make one with
`rememberDropTargetState`, as for any other target: the type it takes, what it is
called, what to do with a drop, and what it turns away.

## Files from outside the app

For an upload area, make the state with the overload that takes no in-app type.
It takes files and text dragged in from the desktop, another window, or the
Files app, and nothing carried within the app:

<!--sample:DropZoneUpload-->
```kotlin
val types = FileTypes.of(".pdf", ".csv")

// Files from outside the app only: no in-app type, so nothing in the app
// can be carried here.
val upload = rememberDropTargetState(
    label = "Timetables",
    external = ExternalDrops.files(types) { files, _ -> attach(files) },
)
// Nobody drags with a screen reader or a keyboard, so the zone has a
// button that does the same thing.
val picker = rememberFilePicker(attach)
DropZone(upload) {
    leading { +Tabler.Outline.FileUpload }
    +"Drop timetables here"
    message { +"PDF or CSV" }
    action { Button(onClick = { picker.launch(types, multiple = true) }) { +"Choose files" } }
}
```

**Always pair it with a picker.** Nobody drags with a screen reader or a
keyboard, and on a phone there is usually nothing to drag from. The action is
where the same files come in another way.

Where a drag from outside the app can happen at all depends on the platform:
the desktop, a browser, and an iPad or Android tablet with another app beside
yours. The [drag and drop guide](../drag-and-drop.md) has what each platform
reports while a drag is under way.

---

## Accessibility

The zone is part of the page, not an announcement: its words are read where
they are, and nothing is said when it appears. A drop zone that takes things
carried within the app is reached from the thing to be moved: "Move to Archive"
is among its actions, from its target's label, and the move is announced as any
other is. A zone that takes files from outside the app has no such move, which
is why it needs its picker.

The status is not colour alone: the edge goes from dashed to solid as a thing
comes over it, and a refused thing goes home when let go. At rest the edge is
an outlined field's colour; at high contrast it is drawn at the strong width in
every state.

Under reduced motion nothing changes: nothing in it moves, and its colours
fade between states over the theme's fast duration, since a fade is not
movement.
