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
