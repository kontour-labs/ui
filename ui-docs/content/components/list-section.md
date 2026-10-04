# `ListSection` / `SectionHeader`

A titled group. `SectionHeader` carries `heading()` semantics, so a screen reader
can jump between sections rather than walking every row.

<!--sample:ListSectionBasics-->
```kotlin
// The section owns the rounding: `position` tells each row whether it is the
// first, the last, both or neither, so a group reads as one card rather than
// as a stack of separate ones.
ListSection(
    title = { +"Appearance" },
    supporting = { +"How the app looks on this device" },
    // Above the rows is what the group *is*; below them is what it *does*.
    footer = { +"Always dark keeps the screen dark even when the system is light." },
) {
    SelectionRow(onClick = { save() }, position = GroupPosition.First) {
        +"Theme"
        supporting { +"Match system" }
    }
    SelectionRow(onClick = { save() }, position = GroupPosition.Last) {
        +"Text size"
        supporting { +"Default" }
    }
}
```

**Rows, not fields.** The `spacing` between a section's children is the hairline
that makes a group of rows read as one object, and it is the gap fields get too.
Form fields are separate controls rather than one card, so give them the space a
form has: `ListSection(title = { +"Contact" }, spacing = Theme.spacing.md) { TextField(…); TextField(…) }`.
The heading and the footer keep their own padding either way.

## In a lazy list: `listSection`

A `LazyColumn` arranges every item one way, and a sectioned list needs two gaps:
the hairline between a group's rows and the space before the next heading.
`listSection` draws both, with the heading, each row's `position` and the footer,
so leave the column's `verticalArrangement` alone.

<!--sample:ListSectionLazy-->
```kotlin
// Sections in a lazy list. Each draws its heading, its rows a hairline
// apart with their corners worked out, and the space before the next —
// so the `LazyColumn` sets no `verticalArrangement` of its own.
LazyColumn {
    listSection(favourites, key = { it.name }, sectionKey = "favourites", title = { +"Favourites" }) { stop, position ->
        ListItem(onClick = { save() }, position = position) { +stop.name }
    }
    listSection(
        nearby,
        key = { it.name },
        sectionKey = "nearby",
        title = { +"Nearby" },
        footer = { +"Within a ten-minute walk." },
    ) { stop, position ->
        ListItem(onClick = { save() }, position = position) {
            +stop.name
            supporting { +"${stop.routes} routes" }
        }
    }
}
```

Each section adds one item for its header, one for each row and one for its
footer, which is what [`indexedBy`](index-scroller.md) counts for an
`IndexScroller`: a section per letter, without a footer, is where the rail's
letters land. An empty section draws nothing. Its rows move rather than jump when
a filter adds or takes some away; give `key` so the moves follow each row, and
`sectionKey` for a section that comes and goes as a whole. A `key` is saved with
the list, as any lazy item's is, so on Android it has to be something a `Bundle`
holds — a string or a number is right. A `sectionKey` can be anything whose
`toString()` tells the sections apart: the section makes its own string keys from
it.

---

## Accessibility

`title` is marked `heading()`, which is what makes a settings screen navigable:
a screen reader can jump between sections instead of walking every row.

`spacing` and `position` are visual grouping. The heading is the semantic
grouping, and they are not the same thing — a run of rounded rows with no heading
looks like a group and is not one.

`footer` is announced after the rows, in reading order. Put the caveat there
rather than in the last row's `supporting`, where it reads as being about that
row.

At high contrast the title, supporting line and footer take the enhanced
scheme's muted colours. The section draws no line of its own; each
[`ListItem`](list-item.md) in it takes the edge a row takes at this tier.

Nothing in a `ListSection` moves. A `listSection`'s rows slide to their new
places when the list changes under them; under reduced motion they appear and go
in place, with the fade and without the slide.
