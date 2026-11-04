# `TagGroup`

**Tags joined into one: a route and where it goes, a version and its state.**
Each part takes what a [`Tag`](tag.md) takes, a tone or its own colour with the
text colour derived from it, and an `onClick` if it leads somewhere. The group
does the shaping: only the outside corners round, and where two parts meet they
are cut square across or, with `join = TagJoin.Diagonal`, on a slant.

<!--sample:TagGroupBasics-->
```kotlin
// A line and where it goes: one tag in two colours, cut on a slant.
TagGroup(join = TagJoin.Diagonal) {
    item(containerColour = routeColour) { +"T1" }
    item { +"Fremantle" }
}
```

**`spacing` stands the parts apart.** At its default, 0, the parts are one tag
split into pieces. With a little room, `TagGroupDefaults.Gap`, they are separate
tags standing together, with tight corners where they face each other, as a
[`ButtonGroup`](button-group.md)'s buttons have. Either join works either way: a
diagonal group stood apart has slanted channels between its parts.

<!--sample:TagGroupApart-->
```kotlin
// Separate tags standing together, with tight corners where they meet.
TagGroup(spacing = TagGroupDefaults.Gap) {
    item(tone = Tone.Accent) { +"Bus" }
    item { +"Route 960" }
    item(tone = Tone.Success) { +"Live" }
}
```

**Two parts of one colour keep a hairline between them**, however small
`spacing` is: flush, they would be one wider tag with no sign of where one part
ends. Give the parts different tones, or colours, for a tag that reads as one
shape.

It is a **builder**, as `ButtonGroup` is, because each part's shape depends on
how many there are and where it sits, which is not known until they have all been
declared. The builder collects rather than composes, so read a `@Composable`
value before the group rather than inside it.

**Reach for a row of `Tag`s instead** when the labels only happen to sit
together. A group says the labels belong to each other: a line and its terminus,
a platform and its side, a key and its value.

---

## Accessibility

A group of plain labels is **one announcement**, its parts read in order: "T1,
Fremantle". A group with a part that can be pressed, or with a part that has its
own `contentDescription`, is read as separate items instead, each as its own
`Tag` would be, inside an `isTraversalGroup` so they are read together. A merged
announcement can't carry a button, and a description on one part would be read
in place of every other part's text.

A part with an `onClick` is a button, announced with its text, with a focus ring
that follows its cut. The group reserves `minTouchTarget` once, in height, rather
than each part reserving its own and pushing its neighbours apart. `enabled` on
the group disables every part that can be pressed.

At high contrast each part takes the edge its tone takes on a `Tag`, so a part
whose colour sits close to the page still has a boundary. Right to left, the
first part is on the right and a diagonal join leans the other way, as the text
does. Nothing about a group moves, and a part's press is a fade at every
setting, so reduced motion changes nothing here.
