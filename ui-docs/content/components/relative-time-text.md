# `RelativeTimeText`

**It re-renders when what it shows changes** — the moment "in 2 min" becomes
"in 1 min", and no more often — rather than on a fixed timer that is either
wasteful or stale. The time left is read off the clock each time, so a phone
that slept or an app that was in the background comes back to the right
number.

<!--sample:RelativeTimeTextBasics-->
```kotlin
// A duration from now, not an instant: it reads the clock from there on,
// so it stays right however long the app was away.
RelativeTimeText(until = 4.minutes)
```

**It rounds down.** Telling someone their bus is 2 minutes away when it is 90
seconds away is the error that makes them miss it. The time left is counted in
whole seconds first, rounded up, as a countdown is shown: `until = 4.minutes`
reads "in 4 min" as it appears, not "in 3 min" for the milliseconds it took to
get on screen.

It is a polite live region, so a screen reader announces the change without
interrupting whatever is being read.

---

## Accessibility

Marked as a **polite live region**, so "4 min" becoming "3 min" is announced when
there is a gap rather than interrupting. That is what makes a departure board
usable without sight and what makes it maddening if fifteen of them are on screen
at once.

`announce = false` turns it off. Use it for every row but the one the user is
following — a list of live times all announcing themselves is a list nobody can
listen to.

`until` is a `Duration`, so the caller says when the moment is without handing
over a clock: the moment is fixed as `until` from when it was composed, and
what is announced is what is left of it on the system's clock.

At high contrast it is text in the colour you pass, or the one it inherits, from
the enhanced scheme; nothing else changes.

By default nothing moves, so reduced motion has nothing to change. With
`animateValue = true` the number rolls over through
[`AnimatedCounter`](animated-counter.md), and under reduced motion it is
replaced where it stands instead.
