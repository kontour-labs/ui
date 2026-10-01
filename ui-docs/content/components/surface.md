# `Surface`

*Also on this page: `ProvideContentColour`.*

Background, shape, border and shadow in one — and the thing that sets
`LocalContentColour` for everything inside it.

<!--sample:SurfaceBasics-->
```kotlin
// No colour argument on either child: the surface set `LocalContentColour`
// from its own background, and both resolve against it.
Surface(containerColour = Theme.colours.primary, shape = Theme.shapes.medium, shadow = Theme.elevation.low) {
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

**A tap on a surface stays on it.** Whatever is drawn beneath a surface does
not get the taps that land on it, within its shape: a card over a map, a toolbar
over a canvas or a tooltip over the page does not pass a tap on its blank part
to whatever is underneath. It takes nothing for itself, so its own controls and
gestures work as before, and a clickable container *round* it still gets a tap
on it: a card in a clickable row presses the row. This is Material's rule for
its surface too.

The bars — `TopBar`, `NavBar`, `NavRail`, `TabBar` and `Toolbar` — go one step
further, because they are chrome: a press on one that nothing in it took goes
nowhere, not even to a clickable container round it. A drag that starts on a bar
still scrolls the page round it.

`shadow` comes from `Theme.elevation` rather than a `Dp`, because elevation is a
role — the same nominal height reads differently on a light scheme and a dark
one, where a shadow has almost nothing to fall on and a lighter surface does the
work instead.

---

## Accessibility

A surface sets `LocalContentColour` from the ground it paints, which is the
mechanism that keeps contrast correct without any call site asking for it: text
and icons inside resolve against the background they are actually on.

That is why `containerColour` and `contentColour` travel together, and why passing a literal
`containerColour` without its matching `contentColour` is the way to produce unreadable text
in one theme and not the other. The token pairs are asserted for contrast by a
test over every combination — see
[accessibility](../accessibility.md).

At high contrast a `Surface` takes the enhanced scheme's grounds and the content
colours paired with them, and nothing else: it adds no edge of its own, and its
shadow is the same at both tiers. A ground that sits close to the page —
`surfaceRaised` measures 1.00:1 against it in the enhanced light scheme — needs
`border = contrastEdge()`, which is how the library's own cards, sheets and
dialogs get theirs.

Nothing about a surface moves, so reduced motion changes nothing.
