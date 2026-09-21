# `AnimatedCounter`

<!--sample:AnimatedCounterBasics-->
```kotlin
AnimatedCounter(value = minutesAway, format = { "$it min" })
```

For a figure that changes while the user is looking at it — minutes to the next
bus, an unread count, a fare as options are added. A number that simply swaps is
one the eye can miss entirely; one that rolls says *this changed* without a
highlight or a flash that has to be undone a moment later.

**Only the digits that changed move.** "14 min" to "13 min" rolls one column; the
`1` does not move and neither does " min". That is the difference between this
and a cross-fade of two strings: a cross-fade says the *value* changed, and this
says which part of it did. Digits roll **up** when the number grows and **down**
when it shrinks, so counting down to a departure looks like a departure board.

**The row does not twitch.** Every digit cell is the width of the widest digit in
the current font, measured once — the theme's face draws `1` at 23px and `0` at
42px, so a counter laid out naturally would change width as it counts and drag
whatever is beside it along. Non-digits keep their natural width, since they do
not change.

The cells are a dozen separate nodes, so the row carries the whole formatted
string as its own description and the cells are cleared — otherwise a screen
reader announces "one", "four", "space", "m", "i", "n".

---

**`warnBefore` makes the counter flinch before a number falls.** Off by default,
and only ever on a **decrease**: a number going up is good news and arrives as
fast as it likes, while a number going down is a seat gone, a balance spent, a
minute lost — and the report was that it happens with no warning at all.

The counter cannot see the future, so it makes one. A drop holds the old number
and shakes the digits that are about to change for `warnBefore`, and then rolls.
What a reader gets is a moment of "something is about to change" before it does;
what it costs is that the drawn number lags the hoisted `value` for exactly that
long. That is the trade, and it is why this is opt-in rather than something every
counter in an app quietly starts doing.

**`warnBefore` is the shake, not a hold with a shake somewhere inside it.** They
were two separate lengths, and both ways of arranging them were reported. A fixed
450ms tremor at the front of a 1.5-second warning shook and then stood perfectly
still for a second before the number moved; moving the tremor to the back put the
same second of silence in front of it, so the drop was announced by nothing
happening. There is no third place to put a pause inside a warning, because the
pause was the fault.

So there is no lead-in and no tail: the shake starts on the frame the fall is
noticed and its last leg lands on centre as the roll begins. **About 450ms is the
length to ask for**, which is two there-and-backs at the tremor's natural rhythm.
Longer is honest rather than clever — ask for two seconds and you get two seconds
of shaking, which stops reading as an announcement somewhere around the third
cycle. The rhythm is held near constant and the number of cycles follows the
duration, so a longer warning is *more* shaking rather than slower shaking.

**Only the digits that are going to move shake.** That was the other half of the
report. The tremor used to be a `translationX` on the whole row — one object
saying something about itself, which sounds right and tells the reader only that
*something* is changing, where the useful half of the message is *which* part of
the figure is about to go.

The positions are compared **right-aligned**, which matters on the transition
that is easiest to get wrong: 1000 falling to 999 changes length, so matching
index for index from the left marks all four positions as moving when what has
actually happened is that three digits changed and a leading 1 went away.

**Every digit about to move trembles the same way.** Neighbouring cells used to
shake in opposite phase, on the argument that two adjacent changing digits in phase
read as the whole number sliding — the thing the per-digit tremor replaced. That
argument does not survive the right-aligned comparison above: a slide is the *whole*
figure travelling, and the digits that are not about to change sit still, so 1200
falling to 1199 shakes two columns while two hold their ground. On the one
transition where every digit does change — 200 to 199 — a 1.5dp shake at 90ms is
not a slide either, because a slide is vertical and this is not.

It was also never quite the alternation it claimed to be: the parity ran over the
character index, and a group separator takes a slot without drawing one, so
1000 → 999 gave the four digits a reader sees the signs `+ + − +`.

A second drop landing mid-warning restarts nothing — the wiggle carries on and
the roll, when it comes, goes to wherever the value has reached — so a value
falling every second does not queue a second of warning per step.

The wiggle is an `Animatable` driven between the drop and the roll, not an
infinite transition: an infinite one runs for as long as it is composed, so
every counter in an app would carry a perpetual animation to be ready for a
warning most of them never give.

---

## Accessibility

The announced value is `contentDescription`, defaulting to the text, set on the
whole control with every animating digit cleared. Without that a screen reader
would read a column of digits mid-flight, which is neither the old value nor the
new one.

Pass `contentDescription` where the digits are not the sentence — "4 minutes"
rather than "4".

It is not a live region: it does not announce itself when it changes. Where the
change is the point — a departure time counting down — that is
[`RelativeTimeText`](relative-time-text.md), which is.

The description follows **what is drawn**, including while a fall is being held.
Announcing the pending number instead would spare a screen reader the delay, and
would mean a sighted user of one hears a number they cannot see. The delay is
the cost of the warning and it is paid in every channel.
