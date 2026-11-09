# `TagGroup`

**Tags joined into one: a route and where it goes, a version and its state.**
Each part takes what a [`Tag`](tag.md) takes, a tone or its own colour with the
text colour derived from it, and an `onClick` if it leads somewhere. The group
does the shaping: only the outside corners round, and where two parts meet they
are cut square across, on a slant with `join = TagJoin.Diagonal`, or to a point
with `join = TagJoin.Flow`.

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
[`ButtonGroup`](button-group.md)'s buttons have. Any join works either way: a
diagonal group stood apart has slanted channels between its parts, and a flowing
one has pointed ones.

<!--sample:TagGroupApart-->
```kotlin
// Separate tags standing together, with tight corners where they meet.
TagGroup(spacing = TagGroupDefaults.Gap) {
    item(tone = Tone.Accent) { +"Bus" }
    item { +"Route 960" }
    item(tone = Tone.Success) { +"Live" }
}
```

**`TagJoin.Flow` makes each join an arrow**, one part coming to a point in a
notch cut into the next: a journey, a process, a version made from another.
`flow` says which way the arrows point, `TagFlow.Forward`, the default, from the
first part to the last, or `TagFlow.Backward`, from the last to the first. Both
are in the reading direction, so a forward flow points left in a right-to-left
language, the way its text runs.

<!--sample:TagGroupFlow-->
```kotlin
// Each part an arrow into the next: where something is in its process.
TagGroup(join = TagJoin.Flow) {
    item { +"Draft" }
    item(tone = Tone.Info) { +"Review" }
    item(tone = Tone.Success) { +"Published" }
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
first part is on the right, a diagonal join leans the other way and a flow's
arrows point the other way, as the text does.

**A flow's arrows are drawn, not read.** A screen reader reads the parts in the
order they are declared, whichever way `flow` points, so declare them in the
order they make sense spoken. When the direction is the point, say it: a
backward flow from `v2` to `v1` can give `v1` the `contentDescription` "made
from v1". Nothing about a group moves, and a part's press is a fade at every
setting, so reduced motion changes nothing here.

**Too narrow for all of it, every part keeps a share.** At large type or in a
narrow column, the room is shared out: a part that fits keeps its width, and the
longer ones are cut to an even share and end in an ellipsis. No part is pushed
out of sight while a screen reader still reads it.
