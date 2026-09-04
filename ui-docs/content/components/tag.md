# `Tag`

**Takes an arbitrary background and derives its own text colour.** Transit feeds
supply route colours that are not drawn from any palette — a route can be pale
yellow or near-black. Passing `colour` resolves the label with
`contentColourFor()`, so it stays legible.

<!--sample:TagBasics-->
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
    Tag(tone = TagTone.Success) { +"On time" }
    Tag(tone = TagTone.Warning) { +"Delayed" }
    // Not a `Chip`: a tag is a label the reader cannot press. A status that
    // filters the list behind it is a `FilterChip`.
    Tag(tone = TagTone.Neutral) { +"Platform 2" }
}
```

That is the whole reason the component exists rather than callers styling a
`Surface` themselves: the case they would get wrong is the one where the feed
hands them a colour nobody designed for.

**Reach for a [`Chip`](chip.md) instead** when the
thing is pressable. A tag is a label; a chip is a control.

---

## Accessibility

A tag is not interactive and carries no role. Its text is announced in reading
order like any other text.

`contentDescription` overrides that, and is for a tag whose visible text is an
abbreviation — "PLT 2" announced as "Platform 2". Where the visible text already
reads correctly, leave it null: a description that repeats the label is heard
twice.

`tone` is colour and nothing else. A status carried only by tone is a status some
users cannot read, so the word has to say it too — which is why these have words
in them.
