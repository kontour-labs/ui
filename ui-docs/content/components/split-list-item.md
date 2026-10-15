# `SplitListItem`

**Two rows of a list, side by side, as one row.** Each half is a
[`ListItem`](list-item.md) of its own — its own press, its own selection, its
own focus — and the two are joined as the rows of a group are: a hairline
between them, and the corners that face each other cut square. In a
[`ListSection`](list-section.md) it is one row among the others, and takes its
outside corners from `position` as they do.

<!--sample:SplitListItemBasics-->
```kotlin
var saved by remember { mutableStateOf(false) }

// A stop, and beside it the one thing to do with it: two rows joined into
// one, each its own control, the square as wide as the row is tall.
SplitListItem(split = ListItemSplit.AutoSquare) {
    start(onClick = { /* open the stop */ }) {
        leading { +Tabler.Outline.Train }
        +"Perth Underground"
        supporting { +"Platform 2 · Joondalup line" }
    }
    end(
        onClick = { saved = !saved },
        selected = saved,
        role = Role.Checkbox,
        contentDescription = "Save Perth Underground",
    ) {
        leading { +Tabler.Outline.Star }
    }
}
```

**`split` says how the width is shared**, from a few presets or a ratio of
your own:

| `split` | The start half | The end half | For |
|---|---|---|---|
| `ListItemSplit.Halves` | half | half | two of a kind: Wi-Fi and Bluetooth, arrivals and departures |
| `ListItemSplit.AutoSquare` | the rest | a square | a row and the one action that belongs to it: a contact and its call, a stop and its star |
| `ListItemSplit.SquareAuto` | a square | the rest | the same, the action first |
| `ListItemSplit.TwoToOne` | two thirds | one third | a thing and a short fact about it: a route and its next service |
| `ListItemSplit.OneToTwo` | one third | two thirds | the same, the other way round |
| `ListItemSplit.ratio(3f, 1f)` | three quarters | a quarter | anything else |

**A square is as wide as the row is tall**, and grows with it: a start half
that wraps to two lines makes a taller row and a wider square, and the words
beside it make room. It holds what it is given in its middle, which is for a
glyph or a word or two — a square of nothing but a glyph needs a
`contentDescription`, or a screen reader has nothing to call it.

**Both halves are always as tall as the taller one**, so the seam runs the
full height of the row whatever either half holds.

Each half takes what a `ListItem` takes for its own press and selection —
`onClick`, `enabled`, `selected`, `role` — and a `ListItem`'s builder for what
it says: `+` is the label, and `leading`, `supporting`, `overline` and
`trailing` read as they do on any row. A half with no `onClick` is not a
control, and its words are content, as a row's are.

In a [`ListGroup`](list-item.md), a split row goes in through the builder's
own escape hatch, which hands it its position:

```kotlin
ListGroup {
    item("Notifications") { … }
    item { position ->
        SplitListItem(split = ListItemSplit.Halves, position = position) {
            start(onClick = ::openWifi) { +"Wi-Fi" }
            end(onClick = ::openBluetooth) { +"Bluetooth" }
        }
    }
}
```

---

## Accessibility

A split row is two stops, not one: each half is its own node, named by its
words and announced with its own role and state, in reading order — the start
half first, which is the right-hand one in a language read right to left. A
half that is only a glyph says nothing on its own; give it a
`contentDescription`.

Each half takes the touch target, focus ring and press feedback a
[`ListItem`](list-item.md) has, and its own: a press on one never shows on the
other.

At high contrast each half takes the edge a row takes, so the seam between them
stays visible, and a selected half is outlined in the accent as a selected row
is. Nothing in a split row moves on its own, so reduced motion changes nothing
here.
