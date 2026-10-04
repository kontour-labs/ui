# `Dialog`

A decision that must be made before anything else can happen. Centred, over a
dimmed scrim, and it takes the focus with it.

<!--sample:DialogBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

Button(onClick = { open = true }) { +"Rename favourite" }

val name = rememberTextFieldState("Perth Underground")
Dialog(
    visible = open,
    onDismissRequest = { open = false },
    title = { +"Rename favourite" },
    // Under the content, and kept on screen while it scrolls. Placed by role:
    // the confirm on the trailing edge, the cancel beside it.
    actions = {
        cancel()
        confirm("Save", enabled = name.text.isNotBlank()) {
            rename(name.text.toString())
            open = false
        }
    },
) {
    Text(
        "Give it a name you will recognise on the home screen.",
        style = Theme.typography.bodySmall,
        colour = Theme.colours.contentMuted,
    )
    TextField(name, label = "Name")
}
```

Reach for a [`Popover`](popover.md) when the rest of the screen is still usable —
a dialog says *stop*, and saying it about something optional is how people learn
to dismiss dialogs without reading them. For the ordinary confirm-or-cancel
shape, [`AlertDialog`](alert-dialog.md) already lays the buttons out.

**`title` is a heading above the content**, `title = { +"Rename favourite" }`, set
in `titleLarge` as an [`AlertDialog`](alert-dialog.md)'s is and announced as a
heading. The content under it is any column.

**`actions` is the row of buttons along the foot**: `cancel()`, `confirm(…)` and,
rarely, `neutral(…)`, each at most once. It is the row an
[`AlertDialog`](alert-dialog.md) draws, laid out the same way: the confirm on the
trailing edge and the cancel beside it as equal halves, or with a third answer,
the two answers together and the cancel on a line of its own beneath them. The
row places them by what they are; the order of the calls is the order a screen
reader and the Tab key take them in. `cancel()` calls `onDismissRequest`, as a tap
outside or back does. `confirm` takes `enabled` for a form that is not done yet,
`destructive` for an answer that cannot be undone, and `loading` while the answer
is being acted on. The builder is composable, so a label can come from
`Theme.strings` or a string resource where it is written.

The row sits **under** the content, not in it: a form taller than the window
scrolls, and its Save stays on screen. A button written into the content scrolls
away with it.

**A dialog scrolls its own content**, and the edge it scrolls under fades while
there is more past it. It is centred in the window with nowhere else to go, so
content taller than the window has no other way out. Without the
scroller it did not merely spill: the content was measured unbounded and placed
in a box the window had clamped, so it **overlapped itself** — a `DatePicker` in
a dialog on a landscape phone drew the last two weeks of the month in the same
cells, "23" over "30", the rest cut through the middle of the digits.

`windowInsets` defaults to every edge including the keyboard, because a dialog is
centred rather than pinned and a confirmation with a text field in it is common.
Only the part of an inset over the overlay's container counts, so a dialog in an
app that has already made room for the keyboard is not pushed up by it again.

See [the overlay guide](../overlays.md#the-stack) for how it stacks with menus
and sheets, and what back does.

---

## Accessibility

The panel is marked `dialog()`, which is what tells a screen reader that the
thing that just appeared is modal and that the content behind it is no longer
the subject.

Dismissal has three routes and they are meant to stay three: the scrim's
labelled `onClick` action, the platform back gesture, and whatever button the
content provides. `dismissible = false` removes the first two — correct for a
confirmation that must be answered, and a reason to make sure the content offers
its own way out. Back still reaches the dialog then, and does nothing: the
dialog does not move, and back does not go on to pop the screen behind a
question that is still open. On Android a dialog that may be dismissed goes back
by its own exit, following the gesture; on iOS back never closes a dialog.

`dismissLabel` is what the scrim announces. The default is `Theme.strings.dismiss`;
override it when "dismiss" is ambiguous about what is being dismissed.

At [high contrast](../accessibility.md#what-high-actually-changes) the panel
takes an edge in the scheme's `outline`, since a raised panel is otherwise
barely distinct from the page at this tier, and the scrim behind it is darker.

Under [reduced motion](../tokens.md#motion) the dialog fades in where it will
rest instead of settling down onto the screen from slightly larger, and it
arrives on a short tween rather than a spring.
