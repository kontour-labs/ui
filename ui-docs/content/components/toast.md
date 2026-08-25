# `Toast`

*Also on this page: `ToastHost`.*

Confirmation of something the user just did. Raised through a
`ToastHostState`, drawn by a `ToastHost`.

<!--sample:ToastBasics-->
```kotlin
val toasts = rememberToastHostState()
val scope = rememberCoroutineScope()

// One host, high in the tree, beside the content it floats over.
Box(Modifier.fillMaxSize()) {
    Screen()
    ToastHost(toasts)
}

Button(onClick = { scope.launch { toasts.show("Saved for offline") } }) { +"Save" }

Button(
    onClick = {
        scope.launch {
            toasts.show(
                "Couldn't reach the timetable service",
                tone = ToastTone.Danger,
                actionLabel = "Retry",
                onAction = { refresh() },
            )
        }
    },
) { +"Refresh" }
```

**A toast is for feedback on an action; a [`Banner`](banner.md) is about the
screen.** A banner that appears in response to a tap is easy to miss, because the
user is looking at their finger. A toast that carries important information is
missed by anyone who looked away.

**Never put the only copy of something important in a toast**, and never put a
control in one that is not also available elsewhere. An action that vanishes
after a few seconds is unusable for anyone who reads slowly.

**At most three at once.** Past that the stack is taller than the thing it is
reporting on and the ones at the back are a stripe of colour rather than a
message, so `ToastDefaults.MaxVisible` caps it and the rest wait their turn while
their own timers run. How long each stays, and why they appear at the top on a
phone, is in [the overlay guide](../overlays.md#toasts).

---

## Accessibility

A toast announces itself. The live region is **`Assertive` for
`ToastTone.Danger`** and `Polite` for everything else — a failure interrupts,
a confirmation waits for a gap.

It never takes focus, which is deliberate: a toast that stole focus would move
the reader away from what they were doing to tell them the thing they did
worked. The consequence is the rule that governs the component — **an action in
a toast must not be the only way to do it.** Four seconds is not a decision
window for someone reading at their own pace.

`showClose` adds a close button with `closeLabel` on it, which is worth turning
on wherever a toast can carry an action.

---

← [Overlays](overlays.md) · [All components](../components.md)
