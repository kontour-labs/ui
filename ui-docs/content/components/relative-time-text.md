# `RelativeTimeText`

![RelativeTimeText](../../../ui-catalog/screenshots/components/relativetimetext-light.png)

**It re-renders at the resolution it is displaying** — every second under a
minute, every twenty above — rather than on a fixed timer that is either
wasteful or stale.

<!--sample:RelativeTimeTextBasics-->
```kotlin
// A duration, not an instant: the caller owns the clock, so this is
// testable without freezing time and does not need a time source of its own.
RelativeTimeText(until = 4.minutes)
```

**It rounds down.** Telling someone their bus is 2 minutes away when it is 90
seconds away is the error that makes them miss it.

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

`until` is a `Duration`, so the caller owns the clock: what is announced is
derived from a value under test rather than from a time source inside the
component.

---

← [Date and time](date-time.md) · [All components](../components.md)
