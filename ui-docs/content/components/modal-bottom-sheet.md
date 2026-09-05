# `ModalBottomSheet`

A task that owns the screen until it is finished. Scrim behind, dismissed by a
tap outside, by back, or by dragging it down.

<!--sample:ModalBottomSheetBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

Button(onClick = { open = true }) { +"Rename favourite" }

ModalBottomSheet(visible = open, onDismissRequest = { open = false }) {
    SheetHeader {
        +"Rename favourite"
        supporting { +"Perth Underground" }
    }
    Column(
        modifier = Modifier.padding(
            start = Theme.spacing.md,
            end = Theme.spacing.md,
            bottom = Theme.spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        Button(onClick = { save(); open = false }, modifier = Modifier.fillMaxWidth()) {
            +"Save"
        }
    }
}
```

On a phone this is usually the right answer where a desktop would use a
[`Dialog`](dialog.md): the controls arrive under the thumb rather than in the
middle of the screen, and the dismissal gesture is the one people already use.

Takes `visible` and `onDismissRequest` rather than a sheet state, because a modal
sheet has one meaningful position and the state object exists to describe
several. Reach for [`BottomSheet`](bottom-sheet.md) when what is behind the sheet
is still the point.

### `dismissible = false` closes the drag too

For the sheet that has to be answered rather than escaped — a required choice, a
form with unsaved changes. It shuts every route out the user has: the tap
outside, the back gesture, and the drag downward, which now **stretches and
springs back** rather than following the finger off the bottom of the window.

That last one matters more than it sounds. A sheet dragged all the way down
settles hidden, and a sheet that is hidden has no scrim to speak of — so the
screen behind it brightened as the user dragged, and then the sheet came back a
moment later, having gone nowhere it was allowed to go. Held at its lowest
detent, none of that happens: the sheet gives a little, the scrim stays where it
is, and letting go puts it back.

The app can still close it by setting `visible` to false, and should offer some
way to. This is about what the *user* can do on their own.

Not the same as `draggable = false`, which stops the sheet moving at all and
takes its handle with it. An undismissable sheet can still be dragged between its
detents.

---

## Accessibility

Everything on [`BottomSheet`](bottom-sheet.md) applies, and modality is the
difference.

`paneTitle` matters more here, because the content behind is blocked and the
sheet is the whole of what the user can now reach. The scrim carries a labelled
dismiss action (`dismissLabel`, `Theme.strings.close` by default), so tapping
away has an equivalent for someone who cannot tap away, and the platform back
gesture closes it.

`dismissOnOutside = false` removes the scrim route. Where you use it, the content
must offer its own way out.
