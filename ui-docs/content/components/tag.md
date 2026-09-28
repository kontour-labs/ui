# `Tag`

**Takes an arbitrary background and derives its own text colour.** Transit feeds
supply route colours that are not drawn from any palette — a route can be pale
yellow or near-black. Passing `containerColour` resolves the label with
`contentColourFor()`, so it stays legible.

<!--sample:TagBasics-->
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
    Tag(tone = Tone.Success) { +"On time" }
    Tag(tone = Tone.Warning) { +"Delayed" }
    // Not a `Chip`: a tag is a label the reader cannot press. A status that
    // filters the list behind it is a `FilterChip`.
    Tag(tone = Tone.Neutral) { +"Platform 2" }
}
```

That is the whole reason the component exists rather than callers styling a
`Surface` themselves: the case they would get wrong is the one where the feed
hands them a colour nobody designed for.

**`onClick` makes a tag lead to what it labels** — a route number that opens the
route, a category that lists everything in it — and it stays a label to look at:
the same size and colours, with a press and a focus ring added.

```kotlin
Tag(onClick = { openRoute(960) }) { +"960" }
```

On a touch screen a clickable tag reserves the platform's touch target around
itself, as every control does, so it takes more room in a row than a plain one.

**Reach for a [`Chip`](chip.md) instead** when the thing is a choice or an action
among others. A tag is a label that may lead somewhere; a chip is a control.

---

## Accessibility

A plain tag is not interactive and carries no role. Its text is announced in
reading order like any other text. With an `onClick` it is a button, announced
with its text, takes focus with a ring, and answers a press as a button does —
with no haptic, since pressing something that leads elsewhere is not an outcome.
Disabled, it keeps its colours and dims its text.

`contentDescription` overrides that, and is for a tag whose visible text is an
abbreviation — "PLT 2" announced as "Platform 2". Where the visible text already
reads correctly, leave it null: a description that repeats the label is heard
twice.

`tone` is colour and nothing else. A status carried only by tone is a status some
users cannot read, so the word has to say it too — which is why these have words
in them.

At high contrast a tag gains an edge in its `tone`'s border colour — `outline`
for the neutral tone — so a tint that sits close to the page still has a
boundary. A tag given its own `containerColour` takes the edge of its `tone`
too.

Nothing about a tag moves; a clickable one's press is a fade at every setting and
does not shrink under reduced motion.
