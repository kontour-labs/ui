# `AlertDialog`

*Also on this page: `ConfirmHost`.*

A [`Dialog`](dialog.md) with the buttons already arranged: a confirm, a dismiss,
and optionally a third answer.

<!--sample:AlertDialogBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

AlertDialog(
    visible = open,
    onDismissRequest = { open = false },
    confirmLabel = "Remove",
    onConfirm = { remove("Perth Underground"); open = false },
    destructive = true,
) {
    +"Remove this favourite?"
    message {
        +"Perth Underground will be taken off your home screen. You can add it back any time."
    }
}
```

`destructive` colours the confirm button rather than moving it, so the shape of
the dialog does not change between "Save?" and "Delete?" — only the weight of the
answer does.

**Three answers do not fit in three thirds.** Three equal columns put each label
in a third of a dialog, which is where a button starts ellipsising its own verb.
The cancel answer takes a line of its own instead — see
[the overlay guide](../overlays.md#alert-dialogs-with-three-answers).

**The buttons stay on screen.** They sit under the message rather than at the
end of it, so a long message on a short window scrolls and the answers stay in
reach. They are the row a `Dialog` draws from its
[`actions`](dialog.md), which is where to go for the same buttons under a body
of your own.

Awaiting an answer from a coroutine rather than hoisting a `visible` flag is what
`ConfirmHostState.confirm()` is for.

---

## Accessibility

Everything on [`Dialog`](dialog.md) applies, and the buttons are the addition.

The order is fixed by the component rather than by the caller — confirm at the
trailing end of the row and, when there are three answers, cancel on a line of
its own beneath the other two — so every alert in the app reads the same way,
and a user who has learned that the trailing button is the one that acts does
not have to relearn it per screen. `destructive = true` colours the confirm
button and does not change the order: a destructive action that moved position
would be a destructive action pressed by muscle memory.

The `StateScope` content is the accessible description. Give it a message that
says what will happen, not "Are you sure?" — which is the sentence a screen
reader user hears with no title on screen to give it a subject.

At [high contrast](../accessibility.md#what-high-actually-changes) the panel
takes the edge a `Dialog`'s does, and the cancel button, a ghost, gains an edge
in its label's colour, so every answer is drawn as a button. The neutral
button's outline and every focus ring are thicker.

Under [reduced motion](../tokens.md#motion) the dialog fades in where it will
rest, as a `Dialog` does, and a pressed button does not shrink; the tonal wash
remains.
