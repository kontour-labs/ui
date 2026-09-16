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

The counter cannot see the future, so it makes one. A drop is *held* for
`warnBefore`, the digits about to change shake at the end of that, and only then
does the roll happen. What a reader gets is a couple of seconds of "something is about to
change" before it does; what it costs is that the drawn number lags the hoisted
`value` for exactly that long. That is the trade, and it is why this is opt-in
rather than something every counter in an app quietly starts doing.

**The shake sits at the end of the hold.** It used to sit at the start, which
with a 1.5-second warning meant 450ms of tremor followed by a full second of a
perfectly still number and only then the roll — reported as the wiggle stopping
for a moment before the counter ticks down, which is exactly what it was doing. A
warning is only a warning if it is next to the thing it warns of, so the number
is held still first and the tremor runs out onto the roll.

The two are one timeline and the length of the tremor is derived from the two
constants that make it, rather than written down a second time: the wait before
it is the hold *minus* the tremor, and two hand-written numbers that drift apart
would put the silence straight back. A `warnBefore` shorter than the tremor
simply starts it at once and lets the roll cut it off.

**Only the digits that are going to move shake, and they shake for a fixed
length.** Both were reported. The tremor used to be a `translationX` on the whole
row — one object saying something about itself, which sounds right and tells the
reader only that *something* is changing, where the useful half of the message is
*which* part of the figure is about to go. And it used to run for exactly as long
as `warnBefore`, because one number controlled the hold and the shake together:
a warning long enough to read was a tremor long enough to look like a fault. It
is two there-and-backs and a settle now — 450ms at a 90ms leg — whatever the hold
is.

The positions are compared **right-aligned**, which matters on the transition
that is easiest to get wrong: 1000 falling to 999 changes length, so matching
index for index from the left marks all four positions as moving when what has
actually happened is that three digits changed and a leading 1 went away.

Neighbouring cells shake in opposite phase. Without that, two adjacent changing
digits read as the whole number sliding — which is the thing this replaced.

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
