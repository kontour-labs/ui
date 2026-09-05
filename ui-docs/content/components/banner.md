# `Banner` / `AnimatedBanner`

An inline message, four severities. `AnimatedBanner` is the same thing that
animates its own appearance and dismissal, for a banner whose presence is driven
by state.

<!--sample:BannerBasics-->
```kotlin
var showing by remember { mutableStateOf(true) }

if (showing) {
    Banner(tone = BannerTone.Warning, onDismissRequest = { showing = false }) {
        title { +"Track work this weekend" }
        message { +"Buses replace trains between Perth and Bayswater until Monday." }
        action {
            Button(onClick = { plan() }, variant = ButtonVariant.Ghost, size = ButtonSize.Small) {
                +"Plan around it"
            }
        }
    }
}
```

The leading icon and the dismiss are both centred against the banner, so the two
things flanking the text agree with each other. A tone icon says *this is a
warning*, which is a fact about the whole message rather than about its first
line — the same reason the dismiss does not sit up in the corner of a three-line
banner.

**`Banner` vs `Toast`.** A banner is about the screen you are on; a toast is
about something you just did. A banner that appears in response to a tap is easy
to miss, because the user is looking at their finger.

`Danger` banners announce assertively and everything else politely —
interrupting for a routine notice trains people to ignore the interruption.

---

## Accessibility

A banner is a **live region** — `Assertive` for `BannerTone.Danger`, `Polite`
otherwise. It announces itself when it appears, because it is about the state of
the screen the user is on and they need to know before they act on it.

That is the whole difference from a [`Toast`](toast.md), which is about something
just done, and from a [`Callout`](callout.md), which is prose and announces
nothing.

The dismiss button carries `dismissLabel`. A banner with an action should keep
that action available elsewhere too — a banner the user dismissed by accident
should not take a capability with it.
