# `ExpandingListItem`

<!--sample:ExpandingListItemBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

// One group: the expanding row takes its place in it like any other.
ListGroup {
    item(label = "Elizabeth Quay", supporting = "3 routes")
    item { position ->
        ExpandingListItem(
            expanded = open,
            onExpandedChange = { open = it },
            position = position,
            chevron = Tabler.Outline.ChevronDown,
            header = {
                leading { +Tabler.Outline.Bus }
                +"Perth Underground"
                supporting { +"4 platforms" }
            },
        ) {
            item(label = "Platform 1", supporting = "Mandurah line")
            item(label = "Platform 2", supporting = "Joondalup line")
        }
    }
}
```

A row that opens onto more rows. The header takes a `ListItemScope`, so it is
written exactly like any other row; the body takes the **same `ListGroupScope`**
a [`ListGroup`](list-item.md) does, so what comes out is rows rather than a panel.

**The seams are the whole of it.** Shut, the header is whatever `position` says
it is. Open, it becomes the *first* of a longer run: its bottom corners square
off, the children take the middle, and the last child rounds off the bottom. The
group reads as one object unfolding rather than as a card appearing under a pill.
That is why the children come from a scope rather than being emitted by the
caller — a caller writing bare `ListItem`s would have to compute those positions
itself, which is the thing this exists to prevent.

**Reach for [`Accordion`](accordion.md) instead** when what opens is
arbitrary content. The two are near-twins and the difference is what comes out:

| | `ExpandingListItem` | `Accordion` |
|---|---|---|
| Header | A `ListItem` | Its own header, in a `ListItem`'s shape |
| Body | More rows | Anything |
| In a `ListGroup` | Continues the run | Sits in it as a foreign object |

An empty body draws no chevron and does not respond to a tap: a disclosure
control that opens onto nothing is a promise the row cannot keep.

---

## Accessibility

`Role.Button` with a `stateDescription` of `expandedLabel` / `collapsedLabel`
whenever the row can open, so the row announces whether it is expanded rather
than looking identical to one that cannot open at all.

A row that does not open reports no state, which is what distinguishes the two —
so do not give every row the parameter "for consistency".

Collapsed content is not composed and is not in the tree. The same rule as
[`Accordion`](accordion.md): nothing essential inside one.

At high contrast the header, a [`ListItem`](list-item.md), takes the tier's
edge, as the rows under it do when they are `ListItem`s too, and a selected one
is outlined in the accent as well. Both are drawn at the tier's thicker line.

Under reduced motion the rows take their room at once and fade in, and fade out
before the room closes, rather than pushing what is below them; the header's
corners change over `fast`.
The chevron turns without its overshoot. A row has no press-shrink at either
setting.
