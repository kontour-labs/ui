# `TextButton`

*Also on this page: `TextIconButton`, `linkedText`.*

A button that is only its label, at the size of the text around it.

<!--sample:TextButtonBasics-->
```kotlin
TextButton(onClick = { forgot() }) {
    +"Forgot your password?"
}
```

`ButtonVariant.Ghost` is the nearest thing a [`Button`](button.md) has, and it is
still a button: a fixed height, horizontal padding, a shape, and a 48dp target
that takes up room. That is right under a form and wrong at the end of a
sentence, where what is wanted is a word you can press. This draws the label and
nothing else — **the target is reserved without being painted**, so it is 48dp to
a finger and the height of a line to the layout.

**It is not underlined, and that is a difference from an inline link rather than
an inconsistency with it.** WCAG 1.4.1 asks that a link *inside a block of text*
be told apart by more than its colour, because there is text either side of it to
be confused with. A standalone label has no neighbours, and an underline there
reads as a mistake — which is why every platform's own "Forgot password?" is a
coloured word without one.

Press and hover are a tint behind the word. The scale every filled control uses
is unavailable to something with no container to scale, and a second colour on
the glyphs alone is not enough to notice at a caption's size.

---

## `TextIconButton` — a glyph the size of the text beside it

<!--sample:TextIconButtonBasics-->
```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    Text("Perth Underground", style = Theme.typography.titleSmall)
    TextIconButton(
        icon = Tabler.Outline.InfoCircle,
        contentDescription = "About this stop",
        onClick = { explain() },
    )
}
```

[`IconButton`](icon-button.md) is the same idea at a control's scale — a
`ButtonSize`, a shape, a variant, and a 40dp box it paints. Next to a line of
text that box is taller than the line, so a heading with one in it grows and the
baselines stop lining up. This takes its size from `LocalTextStyle`, so it
matches whatever it is standing beside, **including at 200% font scale** where a
fixed icon size would be the one thing on the line that did not grow.

It still needs a `contentDescription`: an icon with no text beside it is the only
thing saying what the button does.

---

## `linkedText` — a link *inside* a sentence

A button cannot wrap with the words either side of it, cannot be read out in the
order it is written, and is announced as a button in a list of buttons rather
than as a link in the flow of the text. So a link in a paragraph is not a
`TextButton` placed next to one — it is a real `LinkAnnotation`, and
`linkedText` is how you build one.

<!--sample:InlineLink-->
```kotlin
Text(
    linkedText {
        +"Services are suspended between Perth and Midland. "
        link("See replacement buses") { replacements() }
    }
)
```

The text wraps around it, a screen reader lists it with the page's other links,
and the platform's own link focus and hit-testing apply.

**Why this exists rather than `buildAnnotatedString` at the call site.**
`LinkAnnotation.Clickable` compares by tag, styles **and**
`linkInteractionListener` — and the listener *by identity*. A lambda written at
the call site is a new object on every composition, so the `AnnotatedString` is
never equal to its previous self and `BasicText` re-runs full text layout every
time. Text shaping is the most expensive thing the renderer does; this
documentation site found it the hard way across 320 link-bearing spans. So the
listener here is one remembered object per call site, shared by every link in the
string, which finds the right handler by the tag it was given rather than by
capturing it — and the handlers are still replaced on every composition, so they
close over whatever they like. The string comes out value-equal to the one before
it, and no caller has to know any of this.

`LinkDefaults.styles()` is accent-coloured and underlined, with the accent's own
container tint behind the words on hover and press. Pass your own for a link that
has to sit on a coloured ground.

---

## Accessibility

Both buttons are `Role.Button` and both reserve the full 48dp target without
painting it, so a caption-sized affordance is still reachable by a thumb. The
reserved slack overlaps its neighbours rather than pushing them apart, which is
what lets one sit inside a line of text at all.

`linkedText` produces link semantics rather than button semantics. That is the
whole reason to prefer it in prose: a screen reader's link rotor is how a reader
finds where a paragraph can take them, and a button is not in it.

A `TextButton` whose label is the only thing naming it needs that label to say
where it goes — "See replacement buses", not "See more". This is the control
most likely to be read out of context, because it is the one most likely to be
one of several on a screen.
