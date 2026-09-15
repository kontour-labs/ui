# `Redacted` / `Modifier.redacted`

<!--sample:RedactionBasics-->
```kotlin
Redacted(departures == null) {
    ListItem {
        +(departures?.first()?.name ?: "Perth Underground")
        supporting { +(departures?.first()?.detail ?: "08:14 · Platform 2") }
        leading { +Tabler.Outline.Train }
    }
}
```

Draws everything inside as a placeholder in the shape of the real thing. The
real layout composes, with real spacing and real line breaks, and the **ink** is
replaced — text becomes one bar per line, icons become a rounded square of their
own size.

**Reach for this rather than [`Skeleton`](skeleton.md) whenever you have a
layout to redact.** A `Skeleton` is a box you draw *instead of* your content,
and the cost only shows up later: the placeholder is a second, parallel drawing
of a layout you already have. It starts matching and drifts, because nothing
makes the two agree — a row grows a third line and its skeleton does not, and
nobody notices until someone photographs a loading state. Redaction has nothing
to keep in step. `Skeleton` is still the right answer when there is no content
to redact yet, which is a real case: a list with no rows in it at all.

**Taking it back is a nested `Redacted(false)`**, which is what SwiftUI spells
`unredacted()`. For the part that must still read while the rest loads — a
price, an error, a countdown.

```kotlin
Redacted {
    Column {
        Text("Loading")
        Redacted(enabled = false) { Text("Tap to retry") }
    }
}
```

`Modifier.redacted()` is the same thing for one node that is neither `Text` nor
`Icon` — an image, a chart, a custom drawing. It defaults to the surrounding
block, so inside `Redacted { }` it takes no argument; outside one it does
nothing until you pass `true`, which is the shape for a single element that
loads on its own. The node still measures and lays out exactly as it would have,
which is the point: a placeholder the size of the real thing is a layout that
does not jump when the data lands.

**Text's bars come from the real text layout**, which is why they live in `Text`
rather than in the modifier. A modifier sees a node's size and nothing else, so
the best it could do over a paragraph is one rectangle the shape of the whole
block — and lines have different lengths, the last one is short, and a centred
heading's bars are centred too. That difference is the tell that gives a
hand-drawn skeleton away.

---

## Accessibility

**Redacted content leaves the accessibility tree entirely**, and stops taking
input. Both halves matter and neither is visible in a screenshot: a placeholder
that still announces its text is a screen reader reading out data the user has
not been given, and one that still takes a press is a button whose label cannot
be read.

Presses are consumed on the **Initial** pointer pass, which is what makes the
second half true rather than merely intended — a `clickable` wrapping the
redacted node is a parent in the modifier chain, and on the Main pass the click
would already have been arbitrated.

That leaves the container to say what is happening. Put the loading
announcement there; a dozen unlabelled bars is noise, and silence is worse.
