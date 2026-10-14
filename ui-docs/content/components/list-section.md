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

## Not only rows

A section holds whatever goes in it: a paragraph, a field, a chart, a card, a row
of buttons. Its words line up with what they sit beside. Over rows, the title,
its supporting line and the footer start 16dp in
(`ListItemDefaults.ContentInset`), where a row's own content starts, so the words
make one column down the section. Over anything else they start at the edge,
where that content does — a title 16dp in from a paragraph at the edge looks
like a mistake.

<!--sample:ListSectionOtherContent-->
```kotlin
var units by remember { mutableStateOf(0) }

// Not rows: a paragraph and a control. The title and footer start where
// they do, at the edge, rather than in where a row's words would be.
ListSection(
    title = { +"Planning" },
    footer = { +"Used to work out how long the walks between stops take." },
    // Separate things rather than one card, so the room a form has.
    spacing = Theme.spacing.md,
) {
    Text("Trips are planned at the speed you walk, in the units you read distances in.")
    SegmentedControl(
        options = listOf("Kilometres", "Miles"),
        selectedIndex = units,
        onSelectedIndexChange = { units = it },
        modifier = Modifier.fillMaxWidth(),
    )
}
```

The title follows whatever is **first** in the section and the footer whatever
is **last**, so a control above a run of rows has its title at the edge and its
footer in. A `ListItem` is a row, and so is anything built on one —
`SelectionRow`, `DisclosureListItem`, `ExpandingListItem`, a row in
`SwipeActions`. A row inside a padded card is not, because the card is what is
first. The section measures this rather than guessing from the types: each row
reports its top and bottom to the layout, and the section asks whether its
content starts and ends with one.

Where that is not the reading you want, `textInset` sets it outright:
`textInset = 0.dp` is flush whatever is inside, and
`textInset = ListItemDefaults.ContentInset` lines the words up with rows that are
not there — over a card whose content starts 16dp in, say.

**The gap is yours to set.** The `spacing` between a section's children is the
hairline that makes a group of rows read as one object, and it is the gap
anything else gets too. A paragraph, a field or a control is a separate thing
rather than part of one card, so give it the space a form has:
`spacing = Theme.spacing.md`, as above. The same `spacing` is the gap under the
heading and above the footer, which keep their own padding besides — so a
form's wider gap opens there too.

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
