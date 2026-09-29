# `Modifier.revealOnScroll` / `Modifier.parallax` / `Modifier.edgeVignette`

*Also on this page: `RevealVariant`, `ScrollEffectDefaults`.*

Three treatments for a long page that is read by scrolling: content that arrives
as it comes into view, a layer that drifts against the scroll, and edges that
fade into the ground.

<!--sample:ScrollEffectsBasics-->
```kotlin
val scroll = rememberScrollState()
Box(
    // A soft edge only where there is more page past it: at the top once
    // it has scrolled, at the bottom until the end arrives.
    Modifier.edgeVignette(
        topFade = (scroll.value / 120f).coerceIn(0f, 1f),
        bottomFade = ((scroll.maxValue - scroll.value) / 120f).coerceIn(0f, 1f),
    ),
) {
    Column(Modifier.verticalScroll(scroll)) {
        // Drifts against the scroll, from how far it is from the middle of
        // the window.
        Hero(Modifier.parallax())
        // Each arrives once, the first time a tenth of it is showing; the
        // second a beat behind the first.
        Card(Modifier.revealOnScroll()) { Text("Plan a trip") }
        Card(Modifier.revealOnScroll(delay = 80.milliseconds)) { Text("Keep it for later") }
    }
}
```

They are for a landing page, an onboarding story or an article, not for a list
of departures. On a screen someone uses every day, content that fades in is
content they have to wait for.

## `Modifier.revealOnScroll`

```
Card(Modifier.revealOnScroll(RevealVariant.FadeUp, delay = 80.milliseconds)) { … }
```

Fades the content in, and moves it the last `distance` into place, the first
time a tenth of it is showing. Once only: something that plays again every time
it scrolls back into view turns a page into a slot machine. The threshold is a
share of the element rather than all of it, so something taller than the window
still arrives.

`RevealVariant` says where it comes from: `FadeUp` from below, `FadeScale` from
slightly smaller, `FadeStart` and `FadeEnd` from the start or end of the line,
which swap sides in a right-to-left layout. A `delay` staggers a row of them.

**Never on the first screenful.** What is visible when the page opens should be
there when it opens, and on a slow device the fade lands after the reader has
already looked.

It measures against the window, not against the nearest scrolling container:
Compose can say where a node is on screen without being handed a scroll state,
and that is the question this asks. Inside a small scrolling box, an element
scrolled out of the box but still within the window counts as shown.

## `Modifier.parallax`

```
Hero(Modifier.parallax(strength = 0.08f))
```

Moves the content by a share of its distance from the middle of the window, so
it drifts both ways around the midpoint rather than only one way, and eases
after the scroll rather than tracking it exactly, which is what makes it read as
depth rather than as a second scroll speed. `0.08` is about as much as is
pleasant; past `0.2` the layer visibly comes loose from the page.

It moves the drawing, not the layout, so put it on a layer inside a clipped
frame when the edges must not show — a picture larger than its window, for
instance.

## `Modifier.edgeVignette`

```
Modifier.edgeVignette(topFade = (scroll.value / 120f).coerceIn(0f, 1f), bottomFade = …)
```

Fades the page into the ground at its top and bottom edges, drawn over the
content in `colour`, which has to be the colour behind it. It takes the two
strengths directly, from a scroll you are already tracking: the usual choice is
the distance scrolled, and the distance left, over 120 pixels.

For a scrolling list, [`Modifier.fadingEdges`](modifier-fading-edges.md) is
simpler: it takes the list's own state and fades each edge only while there is
more past it.

---

## Accessibility

None of the three adds or removes semantics. A screen reader reads the content
whether or not it has been revealed, in the order it is laid out.

At high contrast nothing changes. `edgeVignette` fades content into the ground,
so keep controls out of the faded band: a button half-faded at the bottom of the
window is harder to see at every tier.

Under reduced motion `revealOnScroll` and `parallax` do nothing at all: the
content is simply there, in its place. Parallax especially is one of the few
effects that reliably makes people feel ill, so this is not a nicety.
`edgeVignette` is unchanged, since it does not move.
