# `Toast`

*Also on this page: `ToastHost`, `ToastCountdown`.*

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
                tone = Tone.Danger,
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

**At most four at once** — one card and three pills behind it, tapering. Past
that the stack is taller than the thing it is reporting on and the ones at the
back are a stripe of colour rather than a message, so `ToastDefaults.MaxVisible`
caps it and the rest wait their turn.

**Nothing behind the front card is bigger than it.** The ones behind take its
shape, stepped back: a pill behind an "OK" is held to the card's width, and a
card pushed back by a newer, shorter toast takes the new one's size the moment
it arrives rather than shrinking down past it. Only the front one is its own
size, and a toast that comes back to the front grows back to it.

**Every toast runs its own clock whether or not it is on screen.** A toast queued
behind the visible ones is counting down from the moment it was shown, so a burst
of confirmations clears in one round of the timer rather than in as many rounds
as there are toasts. One that runs out while it is still waiting for room never
appears at all — a confirmation of something the user did ten seconds ago is not
worth showing late. How long each stays, and why they appear at the top on a
phone, is in [the overlay guide](../overlays.md#toasts).

**Reaching the front tops the clock up to a floor.** Counting while waiting is
right, but a toast that arrives at the front with two hundred milliseconds left
was never actually read — it flashed. So on being promoted its remainder is
raised to `ToastDefaults.PromotedFloor` of its own duration, three fifths, which
scales with whatever the caller asked for and keeps the extra time
`DisplayDurationWithAction` exists to give. It is a **floor and not a restart**: a
restart would put a stack of four back to taking four full durations to clear,
which is the queue this host was rewritten to stop being, and a toast promoted
with plenty of time left is untouched either way.

**A countdown shows the time left**, with `ToastHost(countdown = …)`:
`ToastCountdown.Wipe` washes the front toast's background in its own ink from
the start edge and draws it back as the time runs out, and `ToastCountdown.Bar`
draws a thin bar along its foot that shortens the same way. Both stand still
whenever the clock does — a finger holding the stack, or a mouse's pointer
resting on the toast, which stops every toast's clock as a finger does — and a
pinned toast has none. `ToastCountdown.None`, the default, draws nothing.

```kotlin
ToastHost(toasts, countdown = ToastCountdown.Bar)
```

**A pill behind the card shrinks and fades away; the front card slides.** The
card leaves toward the edge it arrived from, which is legible because it is the
only thing there. A pill has no such direction — sliding that way takes it under
the cards in front, so the last frame it is drawn on is a full-size one and the
next frame it is simply gone.

**A card swiped away shrinks where the finger left it.** Letting go past the
dismiss point used to send it off the way a toast that times out goes, from
wherever the finger had put it — and a card dragged sideways was cut off at the
edge of where it rests on the first frame of that fade, so most of it just went.
It now leaves as a pill does: smaller toward its own centre first, then fading,
at the place it was let go.

Both leave on the **exit** easing rather than the house one. That is not a
detail: `Motion.standard` is a hard ease-out, three quarters done one frame in,
and on a departure it puts a nine-frame tween into the first two. The shrink was
running perfectly and finishing before anyone could see it — reported, twice, as
a pill with no animation at all.

**Clearing one by hand buys the others time.** Every toast counts down wherever
it sits in the stack, which is deliberate; the consequence is that working
through the ones in front spends the time of the one behind them, so reaching for
a toast deep in a stack is what takes it away. A dismissal by hand — a swipe, the
close control, the action — now tops the rest back up by
`ToastDefaults.ClearedGrace` of their own duration, capped at a full lifetime.
Expiring does not — routing both through one path would have every expiry extend
every other toast, and a stack that never empties — and nor does the app's own
`ToastHostState.dismiss(id)`: a toast taken down because its work finished is
not the reader working through the stack.

**Every direction sends a toast away except the one aimed back into the screen.**
Push it toward the edge it is anchored to, or sideways either way, and it goes;
push a bottom toast *up*, or a top one *down*, and it resists and springs back.
That single rule is what a 45-degree cone around the anchored edge plus a cone
around each side add up to — three cones that meet, leaving one quarter over.

**The refused quarter is a rubber band**, easing up to `ToastDefaults.RubberBand`
of the card's own height and never quite arriving, and **the pills travel with
it**. Both were reported together: the card resisted by a flat third of every
delta, which converges to a fixed point within two or three events and then is a
wall, and the pills it is the front of stayed where they were while it moved.
Sideways is not banded — a way out must not feel like a refusal.

**A toast owns its drag.** Once a finger on one has travelled a pixel, that
gesture is the toast's and no scroller underneath can take it back. The cost is
the one `horizontalDragOwning` names: a page cannot be scrolled by a finger that
started on a toast. For a small pill that is on screen for two and a half seconds
and has two ways out of the way, that is the right trade — and without it a
sideways swipe inside a vertical list is a race decided by the angle of the first
few pixels.

**`alignment` puts the stack in a corner.** A toast is capped at
`ToastDefaults.MaxWidth`, so on a phone it spans the screen less its margins and
`alignment` changes nothing. On a desktop window the default is a card in the
middle of the bottom edge — as far as it can be from the pointer and from the
corner where desktop platforms have taught people to look for a notification.
`OverlayAlignment.End` puts it there.

```kotlin
ToastHost(toasts, alignment = OverlayAlignment.End)
```

It is a second parameter rather than four more values of `ToastPosition`, because
only the *edge* decides which way the stack recedes, slides in and is swiped away.
A top-right position would have to answer whether it recedes up or right, and every
answer to that is arbitrary; the edge and the corner along it are two questions.

**A bottom stack stands above the navigation bar.** A `NavBar` along the foot of
the window tells the host where it is, and the stack stands a little above its
top — or above the keyboard and the gesture bar, whichever is higher; the two are
never added. A page that covers the bar, or a bar that goes, brings the stack back
down, moving rather than jumping. A bar drawn partway down a page does not count.

**8dp off its edge.** A toast stands 8dp off the window's top or foot, inside its
insets, and 8dp above a navigation bar. It was the 16dp ring each card is drawn
in — the room its shadow takes — and above a bar `sm` more, 28dp of page between
a toast and the bar, reported as *"a bit too large"*. The ring is still there;
the stack is moved toward its edge so that the ring reaches past it.

---

## Accessibility

A toast announces itself. The live region is **`Assertive` for
`Tone.Danger`** and `Polite` for everything else — a failure interrupts,
a confirmation waits for a gap.

It never takes focus, which is deliberate: a toast that stole focus would move
the reader away from what they were doing to tell them the thing they did
worked. The consequence is the rule that governs the component — **an action in
a toast must not be the only way to do it.** Four seconds is not a decision
window for someone reading at their own pace.

`showDismiss` adds a close button with `dismissLabel` on it, which is worth turning
on wherever a toast can carry an action.

At high contrast the rims round the pills stacked behind the front toast are
drawn thicker and in full ink rather than faded, and the action and close
buttons, which are ghosts, gain an outline in the toast's text colour.

Under reduced motion a toast arrives and leaves by fading where it rests, the
front card and the pills behind it alike, and a swipe still follows the finger;
a card swiped away fades where it was let go, without the shrink.
When one leaves, the cards behind it still step forward into its place, over a
short tween with no overshoot — a few pixels of the stack's resting depth — and
a toast whose message changes takes its new size at once.
