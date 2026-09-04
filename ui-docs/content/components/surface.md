# `Surface`

*Also on this page: `ProvideContentColour`.*

Background, shape, border and shadow in one — and the thing that sets
`LocalContentColour` for everything inside it.

<!--sample:SurfaceBasics-->
```kotlin
// No colour argument on either child: the surface set `LocalContentColour`
// from its own background, and both resolve against it.
Surface(colour = Theme.colours.primary, shape = Theme.shapes.medium, shadow = Theme.elevation.low) {
    Column(Modifier.padding(Theme.spacing.md)) {
        Text("Perth Underground")
        Icon(Tabler.Outline.Star, contentDescription = null)
    }
}
```

That last part is the point. A surface says what it is, and
[`Text`](text.md) and [`Icon`](icon.md) resolve against it, so a component
dropped onto a dark card is legible without any of its children being told where
they are. Setting a background with `Modifier.background` instead skips that
step, and is how content ends up dark-on-dark.

`shadow` comes from `Theme.elevation` rather than a `Dp`, because elevation is a
role — the same nominal height reads differently on a light scheme and a dark
one, where a shadow has almost nothing to fall on and a lighter surface does the
work instead.

---

## Accessibility

A surface sets `LocalContentColour` from the ground it paints, which is the
mechanism that keeps contrast correct without any call site asking for it: text
and icons inside resolve against the background they are actually on.

That is why `colour` and `contentColour` travel together, and why passing a literal
`colour` without its matching `contentColour` is the way to produce unreadable text
in one theme and not the other. The token pairs are asserted for contrast by a
test over every combination — see
[accessibility](../accessibility.md).
