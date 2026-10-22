# `KeyValueList`

<!--sample:KeyValueListBasics-->
```kotlin
KeyValueList {
    item("Operator", "Transperth")
    item("Platform", "2")
    item("Fare", "$3.20")
    // A slot draws nothing a screen reader can read, so it says what to
    // announce instead.
    item("Accessible", announcement = "yes") { +Tabler.Outline.Check }
}
```

**Not a [`SelectionRow`](selection-row.md).** They draw almost the same
row, and the difference is the whole reason both exist:

| | `KeyValueList` row | `SelectionRow` |
|---|---|---|
| What it is | text | a control |
| Role | none | `Role.Button`, or the control's |
| Touch target | none | yes |
| Pressing it | nothing | opens or changes something |

Using a setting row for facts gives a screen-reader user a list of buttons that
do nothing, which is worse than the visual duplication it saves. If one row needs
to become tappable, it is not one of these — move it out and leave the rest.

**Narrow rows stack.** Below about 216dp — `labelWidth` plus a value column worth
having — each pair becomes label above value instead of side by side. The label
column had a floor and the value had no weight at all, so anything under about
120dp gave the label the whole row and left the value drawing its first character
outside the component, one glyph wide. The column is capped now, so a long label
pushes it out but can never starve the value.

**Each row announces as a pair**, "Platform, 2". Separate nodes make the reader
hold the label while waiting for the value, and the pairing is the entire
content. A row whose value is not text needs `announcement` — a tick icon in an
"Accessible" row otherwise announces as "Accessible" and stops, which reads as a
row with its value missing.

The builder is composable, so a value is formatted where it is written:
`item("Updated", Theme.formats.date(updated))`.

**Two columns, always.** Every row shares one label column, as wide as the
widest label — from `labelWidth` up to what leaves the values their room, a
longer label wrapping to two lines — so the values line up down the list
whatever the labels are, with nothing to set. A ragged value column is what makes
a details panel look untidy. `labelWidth` was a floor each row took for itself,
so one label longer than it pushed its own value out of line with the rest, and
the columns held only while every label was short.

**`compact` for a small container of its own** — a tooltip, a popover. The rows
take the text style and colour around them rather than a page's, sit closer,
and the list is only as wide as its rows. A chart's tooltip lists its series
this way, each with its swatch beside its name:
`KeyValueList(labelWidth = 0.dp, compact = true) { … }`.

---

## Accessibility

Each row merges into one `contentDescription` — `"$label, $value"` — so a screen
reader hears "Platform, 2" as one fact rather than two nodes it has to associate.

`announcement` overrides the spoken form for a value whose written form does not
read aloud: "8m" announced as "8 minutes", "PLT 2" as "Platform 2".

A row is **not** a control. It has no role, no touch target and no press — that
is [`SelectionRow`](selection-row.md), and the distinction is why a key-value list
does not appear as a list of buttons a user has to try.

At high contrast the rows take the enhanced scheme's colours, and the dividers
`showDividers` draws are thicker and in the near-ink `outline`. Nothing in a
key-value list moves, so reduced motion changes nothing.
