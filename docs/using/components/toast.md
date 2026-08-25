# `Toast`

*Also on this page: `ToastHost`.*

Confirmation of something the user just did. Raised through a
`ToastHostState`, drawn by a `ToastHost`.

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

← [Overlays](overlays.md) · [All components](../components.md)
