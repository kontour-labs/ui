# `AlertDialog`

*Also on this page: `ConfirmHost`.*

A [`Dialog`](dialog.md) with the buttons already arranged: a confirm, a dismiss,
and optionally a third answer.

`destructive` colours the confirm button rather than moving it, so the shape of
the dialog does not change between "Save?" and "Delete?" — only the weight of the
answer does.

**Three answers do not fit in three thirds.** Three equal columns put each label
in a third of a dialog, which is where a button starts ellipsising its own verb.
The neutral answer takes a line of its own instead — see
[the overlay guide](../overlays.md#alert-dialogs-with-three-answers).

Awaiting an answer from a coroutine rather than hoisting a `visible` flag is what
`ConfirmationController.confirm()` is for.

---

← [Overlays](overlays.md) · [All components](../components.md)
