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

← [Date and time](date-time.md) · [All components](../components.md)
