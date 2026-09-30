# `DisclosureListItem`

**A row that opens onto more of itself.** Pressing the header grows the row
downward to show what it holds — an answer, a note, the settings of the one
item — inside the same card, lined up under the header's words. Nothing new
appears beneath it.

<!--sample:DisclosureListItemBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

// The row grows to show the answer inside itself: one card, nothing new
// appearing underneath it.
DisclosureListItem(
    expanded = open,
    onExpandedChange = { open = it },
    header = {
        leading { +Tabler.Outline.HelpCircle }
        +"Why is my train late?"
    },
) {
    Text("Trains give way to freight between Midland and Bellevue. A delay there reaches every stop after it.")
}
```

**Not an [`ExpandingListItem`](expanding-list-item.md)**, which opens onto
*rows*: each child is a list row of its own, in the group's seam, and the header
becomes the first of a run. Reach for that when what is inside is more places to
go — platforms, stops, files. Reach for this when it is more of the same thing
and should read as part of it.

| | `DisclosureListItem` | `ExpandingListItem` |
|---|---|---|
| What opens | anything, inside the row | more rows, under it |
| The card | one, growing | the header's, then one per row |
| In a group | one row, whatever it holds | a run of rows |

**Not an [`Accordion`](accordion.md)** either: an accordion is a section of a
page with a header of its own; this is a row of a list, and sits in a
[`ListSection`](list-section.md) with its neighbours, taking its corners from
`position`.

`header` takes a [`ListItem`](list-item.md)'s builder: `+` is the title, and
`supporting`, `overline` and `leading` read as they do on any row. Its
`trailing` is the chevron, which turns as the row opens; `chevron = null` leaves
none, for a header that says in words that it opens. `content` is set in the
row's muted body style and starts where the title does, past a leading icon.

---

## Accessibility

The header is one `Role.Button` named by its words, with a state of expanded or
collapsed, as a disclosure is. What opens is read after it, in order; nothing in
it is hidden from a screen reader while it is shown, and nothing of it is there
while it is shut.

It answers a press with the light tap every disclosure in the library makes.

At high contrast the whole card takes one edge, header and body together, and
the press wash is stronger. Under reduced motion the body takes its room at once
and fades in, and the chevron turns without overshooting.
