# `Dialog`

A decision that must be made before anything else can happen. Centred, over a
dimmed scrim, and it takes the focus with it.

<!--sample:DialogBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

Button(onClick = { open = true }) { +"Rename favourite" }

Dialog(visible = open, onDismissRequest = { open = false }) {
    Text("Rename favourite", style = Theme.typography.titleMedium)
    Text(
        "Give it a name you will recognise on the home screen.",
        style = Theme.typography.bodySmall,
        colour = Theme.colours.contentMuted,
    )
    Button(onClick = { open = false }, modifier = Modifier.fillMaxWidth()) { +"Save" }
}
```

Reach for a [`Popover`](popover.md) when the rest of the screen is still usable —
a dialog says *stop*, and saying it about something optional is how people learn
to dismiss dialogs without reading them. For the ordinary confirm-or-cancel
shape, [`AlertDialog`](alert-dialog.md) already lays the buttons out.

**A dialog scrolls its own content.** It is centred in the window with nowhere
else to go, so content taller than the window has no other way out. Without the
scroller it did not merely spill: the content was measured unbounded and placed
in a box the window had clamped, so it **overlapped itself** — a `DatePicker` in
a dialog on a landscape phone drew the last two weeks of the month in the same
cells, "23" over "30", the rest cut through the middle of the digits.

`windowInsets` defaults to every edge including the keyboard, because a dialog is
centred rather than pinned and a confirmation with a text field in it is common.

See [the overlay guide](../overlays.md#the-stack) for how it stacks with menus
and sheets, and what back does.

---

## Accessibility

The panel is marked `dialog()`, which is what tells a screen reader that the
thing that just appeared is modal and that the content behind it is no longer
the subject.

Dismissal has three routes and they are meant to stay three: the scrim's
labelled `onClick` action, the platform back gesture, and whatever button the
content provides. `dismissOnOutside = false` removes the first — correct for a
confirmation that must be answered, and a reason to make sure the content offers
its own way out.

`dismissLabel` is what the scrim announces. The default is `Theme.strings.dismiss`;
override it when "dismiss" is ambiguous about what is being dismissed.

---

← [Overlays](overlays.md) · [All components](../components.md)
